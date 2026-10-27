package com.music.streamx.playback.smart

import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import com.music.streamx.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.security.MessageDigest

/**
 * Advanced Automix's on-demand half: the ONNX Runtime engine, its JNI bridge
 * and the beat and vocal models, none of which ship in the APK.
 *
 * They arrive as one versioned package per ABI (see `packageAutomix` in
 * app/build.gradle.kts) and are installed under `files/automix/v<version>-<abi>`.
 * The engine is loaded from there by the app's own copy of
 * [ai.onnxruntime.OnnxRuntime], which honours `onnxruntime.native.path` on
 * Android, and only once [modelPath] is asked for a model — which nothing does
 * unless Automix is on and the package is installed. Until then analysis runs
 * on the DSP pass alone and transitions take the planner's plain-fade rung.
 *
 * Native code cannot be unloaded, so the package a process first loads stays
 * its package: a newer one installed afterwards takes over at the next start.
 */
object AutomixEngine {

    sealed interface State {
        /** No download address is configured, or no package exists for this phone's ABI. */
        data object Unavailable : State

        /** Not downloaded. [bytes] is the download's size once the manifest has been read. */
        data class Missing(val bytes: Long?) : State

        data class Downloading(val done: Long, val total: Long) : State

        data object Verifying : State

        data class Failed(val reason: String, val bytes: Long?) : State

        /** Installed. [restartPending] when a newer package waits for the next start. */
        data class Ready(val version: Int, val bytesOnDisk: Long, val restartPending: Boolean) : State
    }

    private const val TAG = "Automix"
    private const val ENGINE = "libonnxruntime.so"
    private const val BRIDGE = "libonnxruntime4j_jni.so"
    private const val DESCRIPTION = "package.json"

    /** Every file a package must carry, and the only names an archive may contain. */
    internal val REQUIRED = setOf(ENGINE, BRIDGE, BeatTracker.MODEL_ASSET, VocalTracker.MODEL_ASSET)

    private val _state = MutableStateFlow<State>(State.Missing(null))
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var root: File? = null
    private var job: Job? = null

    /** The installed package this process uses; fixed once [loaded]. */
    @Volatile private var active: File? = null

    /** The package the engine was loaded from, once it has been. */
    @Volatile private var loaded: File? = null

    /** The ABI this process runs as, if a package is built for it. */
    val abi: String? by lazy {
        val candidates = if (Process.is64Bit()) Build.SUPPORTED_64_BIT_ABIS else Build.SUPPORTED_32_BIT_ABIS
        candidates.firstOrNull { it in pins }
    }

    /** Engine and bridge SHA-256 per ABI, pinned at build time for the ONNX Runtime the app compiles against. */
    private val pins: Map<String, Pair<String, String>> by lazy {
        BuildConfig.AUTOMIX_ENGINE_PINS.split(';').mapNotNull { entry ->
            val parts = entry.split(':')
            if (parts.size == 3) parts[0] to (parts[1] to parts[2]) else null
        }.toMap()
    }

    val configured: Boolean get() = BuildConfig.AUTOMIX_BASE_URL.isNotBlank() && abi != null

    /** Ready to analyse: installed, and loadable by this process. */
    val isReady: Boolean get() = active != null

    /** Idempotent; every entry point calls it, since the service and the UI each get here first. */
    fun attach(context: Context) {
        synchronized(lock) {
            if (root != null) return
            val files = context.applicationContext.filesDir
            root = File(files, "automix")
            // The package is published as a release of a private repository, which
            // only answers with the same access token the plugin catalogue uses.
            val app = context.applicationContext
            AutomixDownloader.token = { com.music.streamx.data.extensions.RepositoryAccess.token(app) }
            // Where builds that bundled the models unpacked them; nothing reads those copies now.
            File(files, BeatTracker.MODEL_ASSET).delete()
            File(files, VocalTracker.MODEL_ASSET).delete()
            active = installed()
            sweep()
            publishIdle()
        }
    }

    /**
     * The on-disk path of [name], loading the engine from the active package on
     * first call. Throws when no package is installed: the trackers treat that,
     * like any other failure to open a session, as "no model".
     */
    fun modelPath(name: String): String = File(engine(), name).absolutePath

    /**
     * The active package, with ONNX Runtime pointed at it. Must run before
     * anything touches an ai.onnxruntime class: the loader reads its path once,
     * in OrtEnvironment's static initialiser, and a failed initialiser stays
     * failed for the life of the process.
     */
    private fun engine(): File {
        val dir = active ?: error("Advanced Automix is not downloaded")
        synchronized(lock) {
            if (loaded == null) {
                val (enginePin, bridgePin) = pins[abi] ?: error("No engine for this ABI")
                // Re-checked here rather than trusted from install time: this is the
                // one moment native code from writable storage enters the process.
                check(sha256(File(dir, ENGINE)) == enginePin) { "Engine does not match its pin" }
                check(sha256(File(dir, BRIDGE)) == bridgePin) { "Bridge does not match its pin" }
                System.setProperty("onnxruntime.native.path", dir.absolutePath)
                loaded = dir
                Log.i(TAG, "Engine loads from ${dir.name}")
            }
        }
        return dir
    }

    /**
     * Loads the engine and opens both models once, straight after the first
     * install, so "ready" in Settings means it actually ran on this phone rather
     * than that the files arrived. A package that fails here is removed again.
     */
    private fun probe() {
        engine()
        val environment = ai.onnxruntime.OrtEnvironment.getEnvironment()
        for (model in listOf(BeatTracker.MODEL_ASSET, VocalTracker.MODEL_ASSET)) {
            ai.onnxruntime.OrtSession.SessionOptions().use { options ->
                options.setIntraOpNumThreads(1)
                environment.createSession(modelPath(model), options).use { session ->
                    Log.i(TAG, "$model opens on ONNX Runtime ${ai.onnxruntime.OrtEnvironment.getEnvironment().version}: " +
                        "inputs ${session.inputNames}, outputs ${session.outputNames}")
                }
            }
        }
        // One real pass through each tracker, on a 120 BPM click track, for the log.
        fun clicks(rate: Double, seconds: Int) = FloatArray((rate * seconds).toInt()) { index ->
            val sinceBeat = index % (rate / 2).toInt()
            if (sinceBeat < rate / 100) kotlin.math.sin(sinceBeat * 0.3f) * 0.8f else 0f
        }
        val beats = BeatTracker({ modelPath(BeatTracker.MODEL_ASSET) }, { 1 })
        val grid = beats.track(clicks(MelSpectrogram.sampleRate, 20))
        beats.release()
        Log.i(TAG, "Beat model on a 120 BPM click track: bpm=${grid?.bpm} beats=${grid?.beats?.size} " +
            "downbeats=${grid?.downbeats?.size}")
        val vocals = VocalTracker({ modelPath(VocalTracker.MODEL_ASSET) }, { 1 })
        val clip = clicks(VocalSpectrogram.sampleRate, 5)
        val mask = vocals.track(clip, clip, VocalSpectrogram.sampleRate)
        vocals.release()
        Log.i(TAG, "Vocal model on the same clip: ${mask?.size} frames")
    }

    /** Reads the manifest for the download size, if nothing is known yet. Cheap to call repeatedly. */
    fun refreshSize() {
        val current = _state.value
        if (current !is State.Missing || current.bytes != null || !configured) return
        if (job?.isActive == true) return
        job = scope.launch {
            runCatching { AutomixDownloader.manifest(abi!!) }
                .onSuccess { entry -> if (_state.value is State.Missing) _state.value = State.Missing(entry.size) }
                .onFailure { Log.w(TAG, "Manifest unavailable", it) }
        }
    }

    /** Starts (or resumes) the download unless it is running or already installed. */
    fun download() {
        if (!configured) return
        synchronized(lock) {
            val busy = _state.value.let { it is State.Downloading || it is State.Verifying }
            if (job?.isActive == true && busy) return
            job?.cancel()
            val dir = root ?: return
            job = scope.launch {
                var size: Long? = null
                runCatching {
                    val entry = AutomixDownloader.manifest(abi!!)
                    size = entry.size
                    if (installed()?.let(::versionOf) == entry.version) return@runCatching
                    AutomixDownloader.install(dir, abi!!, entry, pins.getValue(abi!!)) { state -> _state.value = state }
                    synchronized(lock) {
                        if (loaded == null) active = installed()
                    }
                    if (loaded == null) {
                        _state.value = State.Verifying
                        runCatching(::probe).onFailure { failure ->
                            synchronized(lock) {
                                active?.deleteRecursively()
                                active = null
                            }
                            throw IllegalStateException("Engine would not start on this phone", failure)
                        }
                    }
                }.onSuccess {
                    synchronized(lock) { sweep() }
                    publishIdle()
                }.onFailure {
                    if (it is kotlinx.coroutines.CancellationException) return@onFailure
                    Log.w(TAG, "Download failed", it)
                    _state.value = State.Failed(it.message ?: it.javaClass.simpleName, size)
                }
            }
        }
    }

    /** Stops a running download. The partial file stays, so the next download resumes it. */
    fun cancel() {
        synchronized(lock) {
            val current = _state.value
            if (current !is State.Downloading && current !is State.Verifying) return
            job?.cancel()
            _state.value = State.Missing((current as? State.Downloading)?.total)
        }
    }

    /** Deletes every downloaded file. A loaded engine keeps running until the app restarts. */
    fun remove() {
        synchronized(lock) {
            job?.cancel()
            active = null
            root?.deleteRecursively()
            _state.value = State.Missing(null)
        }
        refreshSize()
    }

    private fun publishIdle() {
        val newest = installed()
        _state.value = when {
            newest != null -> State.Ready(
                version = versionOf(newest) ?: 0,
                bytesOnDisk = newest.walk().filter { it.isFile }.sumOf { it.length() },
                restartPending = loaded != null && newest != loaded,
            )
            !configured -> State.Unavailable
            else -> State.Missing(null)
        }
    }

    /** The newest complete package for this ABI and ONNX Runtime, if any. */
    private fun installed(): File? {
        val dir = root ?: return null
        val abi = abi ?: return null
        return dir.listFiles().orEmpty()
            .filter { it.isDirectory && it.name.matches(Regex("v\\d+-$abi")) }
            .filter { candidate -> REQUIRED.all { File(candidate, it).isFile } }
            .mapNotNull { candidate -> versionOf(candidate)?.let { candidate to it } }
            .maxByOrNull { it.second }?.first
    }

    private fun versionOf(dir: File): Int? = runCatching {
        val description = AutomixDownloader.Description.parse(File(dir, DESCRIPTION).readText())
        description.version.takeIf {
            description.abi == abi && description.onnxRuntime == BuildConfig.AUTOMIX_ONNX_RUNTIME
        }
    }.getOrNull()

    /** Drops installed packages nobody will use again. Partial downloads stay, to be resumed. */
    private fun sweep() {
        val keep = setOfNotNull(active, loaded, installed())
        root?.listFiles().orEmpty()
            .filter { it.isDirectory && it.name.startsWith("v") && it !in keep }
            .forEach { it.deleteRecursively() }
    }

    internal fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
