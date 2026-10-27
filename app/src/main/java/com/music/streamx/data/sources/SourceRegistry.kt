package com.music.streamx.data.sources

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import com.music.streamx.auth.EncryptedPrefs
import com.music.streamx.data.streamx.StreamXSession
import com.music.streamx.data.TrackLog
import com.music.streamx.data.settings.AppSettings
import com.music.streamx.data.settings.permits
import com.music.streamx.data.sources.addon.AddonClient
import com.music.streamx.data.sources.addon.AddonException
import com.music.streamx.data.sources.addon.DetectedFormat
import com.music.streamx.data.sources.addon.SourceFormats
import com.music.streamx.data.settings.AudioQuality
import com.music.streamx.playback.audio.LosslessOutput
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import java.util.UUID

/**
 * One configured source: which protocol, which index.
 *
 * Stored in encrypted prefs — [baseUrl] is a module index the user called
 * "for my private use", not something to leave sitting in plain-text
 * SharedPreferences on a device someone else might get into.
 */
@Serializable
data class SourceConfig(
    val id: String = UUID.randomUUID().toString(),
    val kind: SourceKind,
    /** What the user called it. Blank falls back to the server's host, or the kind's own label. */
    val label: String = "",
    val baseUrl: String = "",
    /** External built-in providers are opt-in; StreamX is the primary catalogue. */
    val enabled: Boolean = kind != SourceKind.JIOSAAVN && kind != SourceKind.YOUTUBE && kind != SourceKind.CLOUDSTREAM,
    /**
     * The addon's `allowDownloads`, as its manifest last said. Stored rather
     * than asked each time so the answer is there without a request — see
     * [checkValidLossless] for why that matters.
     */
    val allowDownloads: Boolean = true,
    /**
     * The addon's `checkValidLossless`, as its manifest last said. Has to be
     * stored: the promise is that a gated addon is not contacted at all while
     * no lossless output is connected, and asking it for its manifest would
     * already break that. Refreshed whenever the manifest is next read.
     */
    val checkValidLossless: Boolean = false,
    val shownInSources: Boolean = true,
    val extensionPackage: String = "",
    val extensionAuthority: String = "",
    val extensionCertificate: String = "",
) {
    /** Whether this source is shut out right now because it needs an output that isn't connected. */
    val awaitsLosslessOutput: Boolean
        get() = checkValidLossless && !LosslessOutput.capable

    /** The two manifest switches, without the parts of the config the user set. */
    internal fun withPolicyOf(other: SourceConfig) =
        copy(allowDownloads = other.allowDownloads, checkValidLossless = other.checkValidLossless)

    /** What the sources screen and the player show. Never blank. */
    val displayName: String
        get() = if (kind == SourceKind.CLOUDSTREAM) "CloudStream" else label.ifBlank {
            baseUrl.takeIf { it.isNotBlank() }
                ?.let { runCatching { Uri.parse(it).host }.getOrNull() }
                ?: kind.label
        }

    /** Whether this has enough filled in to be worth contacting at all. */
    val isComplete: Boolean
        get() = if (kind == SourceKind.APK_PLUGIN) extensionPackage.isNotBlank() && extensionAuthority.isNotBlank() && extensionCertificate.isNotBlank()
            else !kind.needsServer || baseUrl.isNotBlank()
}

/**
 * The user's sources, tried in their saved [playbackOrder].
 * Sources without a saved position follow the default order [SourceKind] declares.
 *
 * [SourceKind.YOUTUBE] is seeded on first run and cannot be deleted, only
 * disabled — it needs no configuration, so a "remove" would delete something
 * the user could not then re-create by typing anything in, it would just be a
 * switch that hides itself. YouTube and JioSaavn are optional and start off
 * on fresh installs. Existing provider choices are preserved on upgrades.
 */
object SourceRegistry {

    private const val TAG = "StreamX"

    private lateinit var prefs: SharedPreferences

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Every configured source, enabled or not. */
    val configs = MutableStateFlow<List<SourceConfig>>(emptyList())
    /** Source ids in the user’s saved playback order. */
    val playbackOrder = MutableStateFlow<List<String>>(emptyList())
    val catalogue = MutableStateFlow(SourceKind.STREAMX)
    /** APK catalogue selection is separate from the remembered built-in catalogue. */
    val selectedExtensionId = MutableStateFlow<String?>(null)
    fun selectExtension(configId: String?) {
        if (configId != null) require(configs.value.any { it.id == configId && feedEligible(it) })
        // Enabling audio fallback or changing a built-in search tab must not
        // silently replace the enabled Spotify catalogue with YouTube's feed.
        val selected = catalogueExtension(configs.value, configId)
        if (::prefs.isInitialized) prefs.edit().putString("selectedExtensionId", selected).apply()
        selectedExtensionId.value = selected
    }

    /**
     * Whether a plugin can serve the Home feed. Being in the playback walk
     * ([SourceConfig.enabled]) is not required: the feed and what plays are
     * separate choices, so Spotify's recommendations can be played through
     * Apple Music or YouTube.
     */
    internal fun feedEligible(config: SourceConfig) =
        config.kind == SourceKind.APK_PLUGIN && config.shownInSources && config.isComplete

    /**
     * Sets the Home feed to one plugin, without touching playback.
     *
     * Unlike [selectExtension], the choice is taken as given and persisted as
     * explicit, so the Spotify default in [catalogueExtension] never overrides it.
     */
    fun useFeedExtension(configId: String) {
        require(configs.value.any { it.id == configId && feedEligible(it) }) { "This plugin cannot provide a Home feed" }
        if (::prefs.isInitialized) prefs.edit().putString("selectedExtensionId", configId).apply()
        selectedExtensionId.value = configId
    }

    /**
     * Sets the Home feed to a built-in catalogue, without switching that source
     * on for playback. Persisted as an explicit "no plugin" (an empty id), so a
     * relaunch does not fall back to an installed Spotify plugin's feed.
     */
    fun useFeedCatalogue(kind: SourceKind) {
        require(kind in listOf(SourceKind.STREAMX, SourceKind.CLOUDSTREAM, SourceKind.YOUTUBE))
        if (::prefs.isInitialized) prefs.edit().putString("catalogue", kind.name).putString("selectedExtensionId", "").apply()
        selectedExtensionId.value = null
        catalogue.value = kind
    }

    /**
     * Plugins whose Home feed is shown too, each as its own labelled section
     * after the main feed's. Like the main feed, independent of playback.
     */
    val extraFeedIds = MutableStateFlow<List<String>>(emptyList())

    /** The switch for [extraFeedIds] as a whole; off keeps the picks but shows one feed. */
    val multiFeed = MutableStateFlow(false)

    fun setMultiFeed(on: Boolean) {
        multiFeed.value = on
        if (::prefs.isInitialized) prefs.edit().putBoolean(KEY_MULTI_FEED, on).apply()
    }

    /**
     * Whether a source can be an extra Home section: a plugin, or YouTube Music
     * (which needs no setup). StreamX and CloudStream stay main-feed only: their
     * clients follow [catalogue], so they cannot run beside another main feed.
     */
    fun extraFeedEligible(config: SourceConfig) = feedEligible(config) || config.kind == SourceKind.YOUTUBE

    fun setExtraFeed(configId: String, shown: Boolean) {
        if (shown) require(configs.value.any { it.id == configId && extraFeedEligible(it) }) { "This source cannot provide a Home feed" }
        val next = if (shown) (extraFeedIds.value - configId) + configId else extraFeedIds.value - configId
        if (next == extraFeedIds.value) return
        extraFeedIds.value = next
        if (::prefs.isInitialized) prefs.edit().putString(KEY_EXTRA_FEEDS, json.encodeToString(ListSerializer(String.serializer()), next)).apply()
    }

    /** Whether a plugin may serve feed pages: the main Home feed or one of the extra sections. */
    fun servesFeed(config: SourceConfig): Boolean =
        feedEligible(config) && (selectedExtensionId.value == config.id || multiFeed.value && config.id in extraFeedIds.value)

    /** Whether [config] is the Home feed that leads the page. */
    fun isMainFeed(config: SourceConfig): Boolean =
        if (config.kind == SourceKind.APK_PLUGIN) selectedExtensionId.value == config.id
        else selectedExtensionId.value == null && catalogue.value == config.kind

    /** Whether [config] is on Home at all: leading it, or as its own section. */
    fun isOnHome(config: SourceConfig): Boolean = isMainFeed(config) || activeExtraFeeds().any { it.id == config.id }

    private fun makeMainFeed(config: SourceConfig) =
        if (config.kind == SourceKind.APK_PLUGIN) useFeedExtension(config.id) else useFeedCatalogue(config.kind)

    /**
     * The one Home control: tick a source to add it, untick to remove it. The
     * first one ticked leads Home; each further one becomes its own section.
     * Unticking the lead promotes the next. The last one cannot be unticked,
     * so Home always has a feed. StreamX and CloudStream can only lead, so
     * ticking one makes it the lead and keeps the previous lead as a section
     * where it can be one.
     */
    fun toggleHomeFeed(configId: String) {
        val config = config(configId) ?: return
        val extras = activeExtraFeeds()
        when {
            isMainFeed(config) -> {
                val next = extras.firstOrNull { it.kind == SourceKind.APK_PLUGIN || it.kind == SourceKind.YOUTUBE } ?: return
                setExtraFeed(next.id, false)
                makeMainFeed(next)
            }
            extras.any { it.id == configId } -> setExtraFeed(configId, false)
            extraFeedEligible(config) -> { setMultiFeed(true); setExtraFeed(configId, true) }
            else -> {
                val previous = configs.value.firstOrNull(::isMainFeed)
                makeMainFeed(config)
                if (previous != null && extraFeedEligible(previous)) { setMultiFeed(true); setExtraFeed(previous.id, true) }
            }
        }
    }

    /** The extra Home sections to load now, in the order they were switched on. */
    fun activeExtraFeeds(): List<SourceConfig> {
        if (!multiFeed.value) return emptyList()
        val mainKind = catalogue.value.takeIf { selectedExtensionId.value == null }
        return extraFeedIds.value.mapNotNull(::config).filter {
            extraFeedEligible(it) && it.id != selectedExtensionId.value && it.kind != mainKind
        }
    }

    internal fun catalogueExtension(sources: List<SourceConfig>, requestedId: String?): String? {
        val available = sources.filter(::feedEligible)
        // An explicit pick may be out of playback; the automatic Spotify default may not.
        return available.firstOrNull { it.id == requestedId }?.id
            ?: available.firstOrNull { it.enabled && it.extensionPackage == "com.music.streamx.extensions.spotify" }?.id
    }

    internal fun permitsYouTubePlayback(sources: List<SourceConfig>): Boolean = sources.any {
        it.enabled && (it.kind == SourceKind.YOUTUBE ||
            it.kind == SourceKind.APK_PLUGIN && it.isComplete &&
            it.extensionPackage == "com.music.streamx.extensions.spotify")
    }

    fun setCatalogue(kind: SourceKind) {
        require(kind in listOf(SourceKind.STREAMX, SourceKind.CLOUDSTREAM, SourceKind.YOUTUBE))
        if (kind == SourceKind.CLOUDSTREAM) {
            selectedExtensionId.value = null
            prefs.edit().remove("selectedExtensionId").apply()
        } else selectExtension(null)
        configs.value.firstOrNull { it.kind == kind }?.let { if (!it.enabled) setEnabled(it.id, true) }
        prefs.edit().putString("catalogue", kind.name).apply()
        catalogue.value = kind
    }

    fun configureCloudStream() {
        val current = configs.value.firstOrNull { it.kind == SourceKind.CLOUDSTREAM }
        if (current == null) add(SourceConfig(id = "cloudstream", kind = SourceKind.CLOUDSTREAM, label = "CloudStream", baseUrl = "https://cloudstream.local", enabled = true))
        else update(current.copy(baseUrl = "https://cloudstream.local", enabled = true))
        setCatalogue(SourceKind.CLOUDSTREAM)
    }

    fun configureStreamX(endpoint: String, name: String = "") {
        val config = configs.value.first { it.kind == SourceKind.STREAMX }
        val saved = StreamXSession.servers.value.firstOrNull { it.endpoint == endpoint }
        val next = config.copy(
            id = saved?.id ?: if (config.baseUrl.isNotBlank() && config.baseUrl != endpoint) UUID.randomUUID().toString() else config.id,
            baseUrl = endpoint, label = "StreamX", enabled = true,
        )
        StreamXSession.rememberServer(next.id, endpoint, name)
        publish(configs.value.map { if (it.id == config.id) next else it })
        setCatalogue(SourceKind.STREAMX)
    }

    /**
     * Built instances, keyed by config id, rebuilt whenever [configs] changes.
     *
     * Held rather than constructed per call so that a source with any warmed
     * state — a module whose index has already been fetched — keeps it across
     * tracks instead of re-probing on every resolve.
     */
    private var instances: Map<String, MusicSource> = emptyMap()

    fun init(context: Context) {
        // Same repair and degradation as AuthStore; see EncryptedPrefs.
        prefs = EncryptedPrefs.open(context, "streamx_sources", "streamx_sources_plain")

        StreamXSession.init(context)
        catalogue.value = runCatching { SourceKind.valueOf(prefs.getString("catalogue", null) ?: "STREAMX") }
            .getOrDefault(SourceKind.STREAMX).takeIf { it in listOf(SourceKind.YOUTUBE, SourceKind.CLOUDSTREAM) } ?: SourceKind.STREAMX
        playbackOrder.value = prefs.getString(KEY_ORDER, null)?.let(::decodeOrder) ?: emptyList()
        extraFeedIds.value = prefs.getString(KEY_EXTRA_FEEDS, null)?.let(::decodeOrder) ?: emptyList()
        multiFeed.value = prefs.getBoolean(KEY_MULTI_FEED, false)
        val stored = prefs.getString(KEY_SOURCES, null)?.let(::decodeStored) ?: emptyList()
        val jioOptInMigrationDone = prefs.getBoolean(KEY_JIOSAAVN_OPT_IN_V1, false)
        val after = sourcesForInit(stored, forceJioSaavnOff = !jioOptInMigrationDone)

        // Publish state and the migration marker in one preferences edit. If a
        // process dies after recording the marker but before recording the
        // disabled source, the next launch would otherwise believe the forced
        // opt-out had already happened and silently restore the old on state.
        publish(after, persist = false)
        com.music.streamx.data.cloudstream.CloudStream.migrate(stored)
        val cloudConfigured = com.music.streamx.data.cloudstream.CloudStream.read(com.music.streamx.data.cloudstream.CloudStream.PROFILE).enabled
        if (cloudConfigured && stored.none { it.kind == SourceKind.CLOUDSTREAM }) {
            // Independent identity; the legacy server remains available in Sources.
            publish(configs.value.map { if (it.kind == SourceKind.CLOUDSTREAM) it.copy(baseUrl = "https://cloudstream.local", enabled = true) else it })
            prefs.edit().putString("catalogue", SourceKind.CLOUDSTREAM.name).apply()
            catalogue.value = SourceKind.CLOUDSTREAM
        }
        val storedFeed = prefs.getString("selectedExtensionId", null)
        val rememberedExtension = storedFeed?.takeIf { id -> after.any { it.id == id && feedEligible(it) } }
        val migratedExtension = if (after.none { it.enabled && it.kind in listOf(SourceKind.STREAMX, SourceKind.CLOUDSTREAM, SourceKind.YOUTUBE) }) {
            after.firstOrNull { it.kind == SourceKind.APK_PLUGIN && it.shownInSources && it.enabled && it.isComplete }?.id
        } else null
        // An empty id is a built-in feed chosen on purpose; see [useFeedCatalogue].
        if (storedFeed == "" || catalogue.value == SourceKind.CLOUDSTREAM && rememberedExtension == null) selectedExtensionId.value = null
        else selectExtension(rememberedExtension ?: catalogueExtension(after, null) ?: migratedExtension)
        after.firstOrNull { it.kind == SourceKind.STREAMX && it.baseUrl.isNotBlank() }?.let {
            StreamXSession.rememberServer(it.id, it.baseUrl, "")
        }
        if (after != stored || !jioOptInMigrationDone) {
            prefs.edit()
                .putString(KEY_SOURCES, json.encodeToString(ListSerializer(SourceConfig.serializer()), configs.value))
                .putBoolean(KEY_JIOSAAVN_OPT_IN_V1, true)
                .apply()
        }
    }

    /**
     * Built-in seeding and one-time source migrations, kept pure for tests.
     *
     * [forceJioSaavnOff] is true exactly once for every install that first runs
     * this version, including upgrades whose stored config currently says on.
     * Once the marker is written, a user who deliberately enables JioSaavn is
     * left enabled on subsequent launches.
     */
    internal fun sourcesForInit(
        stored: List<SourceConfig>,
        forceJioSaavnOff: Boolean,
    ): List<SourceConfig> {
        // Seeded rather than persisted-on-first-write, so a build adding a new
        // built-in kind picks it up for existing installs too. SourceConfig's
        // default is the policy: optional built-in providers start off.
        val seeded = stored + BUILT_IN_KINDS
            .filter { kind -> stored.none { it.kind == kind } }
            .map { SourceConfig(kind = it) }

        // The retired built-in module is removed, while a custom module entered
        // by the user is preserved.
        return seeded
            .filterNot { it.kind == SourceKind.MODULE }
            .map { config ->
                when {
                    config.kind == SourceKind.JIOSAAVN && forceJioSaavnOff -> config.copy(enabled = false)
                    else -> config
                }
            }
    }

    /**
     * Decodes a stored source list one entry at a time rather than as a
     * single list, so one entry naming a kind this build no longer has —
     * left over from before a kind was retired — doesn't take every other
     * entry down with it. A strict `List<SourceConfig>` decode fails whole:
     * one bad enum value and the user's real, working module config is
     * silently gone along with it.
     */
    private fun decodeStored(raw: String): List<SourceConfig> {
        val elements = runCatching { json.parseToJsonElement(raw).jsonArray }
            .getOrElse { return emptyList() }
        return elements.mapNotNull { element ->
            runCatching { json.decodeFromJsonElement(SourceConfig.serializer(), element) }
                .onFailure { TrackLog.w(TAG, "dropping unreadable stored source: ${it.message}") }
                .getOrNull()
        }
    }

    private fun decodeOrder(raw: String): List<String> =
        runCatching { json.decodeFromString(ListSerializer(String.serializer()), raw) }
            .getOrElse { TrackLog.w(TAG, "dropping unreadable source order: ${it.message}"); emptyList() }

    /** Saved ids come first; new sources follow in their previous default order. */
    internal fun ordered(
        sources: List<SourceConfig>,
        order: List<String> = playbackOrder.value,
    ): List<SourceConfig> {
        val position = order.withIndex().associate { (index, id) -> id to index }
        val named = sources.filter { it.id in position }.sortedBy { position.getValue(it.id) }
        val unnamed = sources.filterNot { it.id in position }.sortedBy { it.kind.rank }
        return named + unnamed
    }

    /**
     * The enabled sources in the user’s saved playback order.
     *
     * The user's standing choice and nothing else. Both playback and downloads
     * start here, so disabling JioSaavn or an addon excludes it from both. A
     * stream is budgeted further by [activeForPlayback]; downloads deliberately
     * apply no connection-quality ceiling — see [SourceResolver.forDownload].
     */
    fun active(): List<MusicSource> =
        ordered(enabledConfigs(configs.value))
            // Not in the walk at all while it would be refused anyway: an addon
            // that asked for a lossless output costs nothing — not even a timed
            // out search — while there isn't one. See [SourceConfig.checkValidLossless].
            .filterNot { it.awaitsLosslessOutput }
            .mapNotNull { instances[it.id] }

    /**
     * [active], minus the addons whose manifest said `allowDownloads: 0`. A
     * download walks this instead, so the next source in line serves it.
     */
    fun activeForDownload(): List<MusicSource> =
        active().filter { config(it.configId)?.allowDownloads != false }

    /**
     * Records what an addon's manifest said about downloads and lossless
     * output, if it changed.
     *
     * Does not rebuild the source the way an edit does — see [configuredBy] —
     * because nothing about where it points has changed, and this is called
     * from inside that source's own requests.
     */
    fun updatePolicy(configId: String, allowDownloads: Boolean, checkValidLossless: Boolean) {
        val current = config(configId) ?: return
        if (current.allowDownloads == allowDownloads && current.checkValidLossless == checkValidLossless) return
        TrackLog.d(
            TAG,
            "${current.displayName}: manifest now says allowDownloads=$allowDownloads " +
                "checkValidLossless=$checkValidLossless",
        )
        publish(
            configs.value.map {
                if (it.id == configId) it.copy(allowDownloads = allowDownloads, checkValidLossless = checkValidLossless) else it
            },
        )
    }

    /** The common eligibility gate used by playback and download source walks. */
    internal fun enabledConfigs(configs: List<SourceConfig>): List<SourceConfig> =
        configs.filter { it.enabled && it.isComplete && (it.kind != SourceKind.APK_PLUGIN || it.shownInSources) }

    /**
     * [active], minus the sources the ceiling on the connection in hand does
     * not pay for — see [AudioQuality.permits].
     *
     * Every path that starts or plans a *stream* asks this rather than
     * [active], so the Wi-Fi and mobile-data rungs stay two independent
     * answers to "what does this minute cost" and switching networks switches
     * between them. Downloads deliberately keep asking [active]: what a saved
     * file is worth is [DownloadQuality][com.music.streamx.data.settings.DownloadQuality]'s
     * question, and when it may be fetched is
     * [AppSettings.wifiOnlyDownloads][com.music.streamx.data.settings.AppSettings.wifiOnlyDownloads]'s.
     */
    fun activeForPlayback(): List<MusicSource> {
        val ceiling = AppSettings.effectiveAudioQuality
        return active().filter { ceiling.permits(it.kind) }
    }

    fun instance(configId: String): MusicSource? = instances[configId]

    fun config(configId: String): SourceConfig? = configs.value.firstOrNull { it.id == configId }

    /**
     * Drops completed addon track answers for an explicit quality retry.
     *
     * Empty responses are normally legitimate and cached, but an addon whose
     * upstream was temporarily down can express that outage as HTTP 200 with
     * no rows. The listener pressing "Upgrade quality" is an explicit request
     * to ask again now, not to repeat that cached answer or a previously issued
     * stream URL. In-flight calls stay shared so a quick second press cannot
     * duplicate work already on the wire.
     */
    fun clearCompletedAddonTrackCalls() {
        instances.values
            .filterIsInstance<AddonSource>()
            .forEach(AddonSource::clearCompletedTrackCalls)
    }

    // ── Editing ─────────────────────────────────────────────────────────

    fun add(config: SourceConfig) = publish(configs.value + config.tidied())

    fun update(config: SourceConfig) =
        config.tidied().let { tidied ->
            publish(configs.value.map { if (it.id == tidied.id) tidied else it })
        }

    /**
     * The config as it should be stored, rather than as it was typed.
     *
     * Only addons have anything to tidy, and only their URL: the two forms
     * people paste — the addon's root and its `manifest.json` — address the
     * same server, and storing them as typed makes two configs that behave
     * identically look different on screen and compare unequal in
     * [configuredBy], which would rebuild a warm source over a cosmetic edit.
     * Normalising here rather than in the editor keeps it true for every caller
     * and not just the one with a text field.
     */
    private fun SourceConfig.tidied(): SourceConfig =
        if (kind == SourceKind.APK_PLUGIN && !shownInSources) copy(enabled = false)
        else if (kind == SourceKind.ADDON) copy(baseUrl = AddonClient.normalizeBase(baseUrl)) else this

    fun showApkSource(extension: com.music.streamx.data.extensions.ApkExtension, show: Boolean) {
        val authority = extension.sourceAuthority ?: return
        val current = configs.value.firstOrNull { it.kind == SourceKind.APK_PLUGIN && it.extensionPackage == extension.packageName }
        val next = (current ?: SourceConfig(id = extension.skinId, kind = SourceKind.APK_PLUGIN, enabled = false)).copy(
            shownInSources = show, enabled = show && current?.enabled == true && current.extensionCertificate == extension.certificate,
            label = extension.name, extensionPackage = extension.packageName,
            extensionAuthority = authority, extensionCertificate = extension.certificate,
        )
        if (current == null) add(next) else update(next)
        if (!show && selectedExtensionId.value == next.id) selectExtension(null)
    }

    fun enableApk(extension: com.music.streamx.data.extensions.ApkExtension) {
        val authority = extension.sourceAuthority ?: return
        val current = configs.value.firstOrNull { it.kind == SourceKind.APK_PLUGIN && it.extensionPackage == extension.packageName }
        val next = (current ?: SourceConfig(kind = SourceKind.APK_PLUGIN)).copy(
            enabled = true, shownInSources = true, label = extension.name, extensionPackage = extension.packageName,
            extensionAuthority = authority, extensionCertificate = extension.certificate,
        )
        if (current == null) add(next) else update(next)
        // Playback only: the Home feed is chosen separately ([useFeedExtension]).
        // A first plugin still becomes the feed when nothing else can serve one.
        if (selectedExtensionId.value == null && !builtInFeedAvailable()) selectExtension(next.id)
    }

    /** Whether the current built-in catalogue can serve a Home feed without more setup. */
    private fun builtInFeedAvailable(): Boolean = when (catalogue.value) {
        SourceKind.YOUTUBE -> true
        SourceKind.CLOUDSTREAM -> configs.value.any { it.kind == SourceKind.CLOUDSTREAM && it.isComplete }
        else -> StreamXSession.state.value.token.isNotBlank()
    }

    fun remove(configId: String) {
        val target = config(configId) ?: return
        if (target.kind in BUILT_IN_KINDS) return
        publish(configs.value.filterNot { it.id == configId })
        if (selectedExtensionId.value == configId) selectExtension(null)
    }

    /** Turns a source on or off, including the optional YouTube provider. */
    fun setEnabled(configId: String, enabled: Boolean) {
        // Playback only. Switching a source on or off leaves the Home feed alone,
        // so a feed can come from a plugin that is not in the playback walk.
        publish(configs.value.map { if (it.id == configId) it.copy(enabled = enabled) else it })
    }

    /** Saves playback priority for all sources without changing the selected catalogue. */
    fun reorderSources(orderedIds: List<String>) {
        val current = ordered(configs.value)
        val byId = current.associateBy { it.id }
        val moved = orderedIds.mapNotNull(byId::get)
        val missed = current.filterNot { config -> moved.any { it.id == config.id } }
        val next = (moved + missed).map { it.id }
        if (next == playbackOrder.value) return
        playbackOrder.value = next
        if (::prefs.isInitialized) {
            prefs.edit()
                .putString(KEY_ORDER, json.encodeToString(ListSerializer(String.serializer()), next))
                .apply()
        }
    }

    /** Reorders addon slots while keeping other sources in place. */
    fun reorderAddons(orderedIds: List<String>) {
        val current = ordered(configs.value)
        val addons = current.filter { it.kind.isUserAdded }
        if (addons.size < 2) return
        val byId = addons.associateBy { it.id }
        val moved = orderedIds.mapNotNull(byId::get)
        val missed = addons.filterNot { config -> moved.any { it.id == config.id } }
        val slots = moved + missed
        var next = 0
        reorderSources(current.map { if (it.kind.isUserAdded) slots[next++] else it }.map { it.id })
    }

    private fun publish(next: List<SourceConfig>, persist: Boolean = true) {
        // Rebuilt against the previous map so that an untouched source keeps
        // the instance it already had, rather than being replaced by an
        // identical-but-cold one every time an unrelated row is toggled.
        val previous = instances
        instances = next.associate { config ->
            val existing = previous[config.id]?.takeIf { it.configuredBy(config) }
            config.id to (existing ?: build(config))
        }
        configs.value = next
        // Whatever the rebuild above left behind, told so. An addon that was
        // edited or removed is holding a manifest, a set of search answers and
        // a handful of stream URLs that all describe the server it used to
        // point at, and its client's own scope keeps them alive whether or not
        // anything still references the source. Serving one of those afterwards
        // would be answering a question about the old address with the new one
        // selected.
        previous.values.filterNot { it in instances.values }
            .filterIsInstance<AddonSource>()
            .forEach { it.release() }
        if (persist && ::prefs.isInitialized) {
            prefs.edit()
                .putString(KEY_SOURCES, json.encodeToString(ListSerializer(SourceConfig.serializer()), next))
                .apply()
        }
    }

    /**
     * Works out what is at [url] and returns a config that speaks to it, or a
     * refusal saying what was found instead.
     *
     * This is what makes the editor accept "any JSON" rather than one shape.
     * The kind is *detected* rather than chosen up front: pasting an addon's
     * root, an addon's `manifest.json`, or a module index all end here, and
     * which [MusicSource] gets built is decided by what the server actually
     * returned. Anything recognised but unplayable — an extension registry, a
     * manifest that cannot stream — comes back as a failure carrying a line
     * written for the person reading it.
     *
     * [existing] is carried through so editing a saved source keeps its id and
     * its on/off state; a new source gets a fresh config.
     */
    suspend fun identify(url: String, existing: SourceConfig? = null): Result<SourceConfig> {
        val detected = SourceFormats.identify(url).getOrElse { return Result.failure(it) }
        return when (detected) {
            is DetectedFormat.Addon -> Result.success(
                (existing ?: SourceConfig(kind = SourceKind.ADDON)).copy(
                    kind = SourceKind.ADDON,
                    baseUrl = detected.baseUrl,
                    label = detected.manifest.displayName,
                    allowDownloads = detected.manifest.downloadsAllowed,
                    checkValidLossless = detected.manifest.requiresLosslessOutput,
                ),
            )
            is DetectedFormat.ModuleIndex -> Result.success(
                (existing ?: SourceConfig(kind = SourceKind.CUSTOM_MODULE)).copy(
                    kind = SourceKind.CUSTOM_MODULE,
                    // The index URL itself, not a base: a module index is the
                    // document, where an addon's manifest only points at one.
                    baseUrl = detected.url,
                    label = existing?.label.orEmpty(),
                ),
            )
            is DetectedFormat.Unsupported -> Result.failure(AddonException(detected.reason))
        }
    }

    /**
     * The already-configured source pointing at [url], if there is one.
     *
     * Compared after [identify] has run rather than on the raw text, which is
     * what makes this catch the cases worth catching: an addon's root and its
     * `manifest.json` are the same server typed two ways, and both normalise to
     * one base before they reach here. A trailing slash, a `MANIFEST.JSON`, and
     * a host in a different case are all the same source too.
     *
     * [exceptId] is the source being edited, which is not its own duplicate —
     * without it, saving an existing addon without touching its URL would
     * refuse itself.
     */
    fun duplicateOf(url: String, exceptId: String? = null): SourceConfig? {
        val wanted = canonicalUrl(url)
        if (wanted.isEmpty()) return null
        return configs.value.firstOrNull { it.id != exceptId && canonicalUrl(it.baseUrl) == wanted }
    }

    /**
     * A URL reduced to the form two spellings of the same address share.
     *
     * Scheme and host are lowercased because they are case-insensitive; the
     * path deliberately is not, because on this protocol the path can carry a
     * user's token and two tokens differing only in case are two different
     * credentials. Falls back to the trimmed text when the URL will not parse,
     * so a malformed entry still compares equal to itself.
     */
    private fun canonicalUrl(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        val parsed = trimmed.toHttpUrlOrNull() ?: return trimmed
        val path = parsed.encodedPath.trimEnd('/')
        val query = parsed.encodedQuery?.let { "?$it" }.orEmpty()
        return "${parsed.scheme}://${parsed.host}${if (parsed.port != defaultPort(parsed.scheme)) ":${parsed.port}" else ""}$path$query"
    }

    private fun defaultPort(scheme: String) = if (scheme == "https") 443 else 80

    /**
     * Health-checks a config that hasn't been saved — what the editor's Test
     * button asks.
     *
     * Built fresh and thrown away rather than routed through [instances],
     * which hold the *stored* config: testing one of those would report on the
     * old address, which is precisely the state the user is in the middle of
     * correcting.
     */
    suspend fun probeCandidate(config: SourceConfig): SourceHealth = build(config).health()

    private fun build(config: SourceConfig): MusicSource = when (config.kind) {
        SourceKind.STREAMX, SourceKind.CLOUDSTREAM -> StreamXSource(config)
        SourceKind.APK_PLUGIN -> ApkExtensionSource(config)
        SourceKind.ADDON -> AddonSource(config)
        // Same protocol, same implementation — the kinds differ only in rank.
        SourceKind.CUSTOM_MODULE -> ModuleSource(config)
        SourceKind.MODULE -> ModuleSource(config)
        SourceKind.JIOSAAVN -> JioSaavnSource(config)
        SourceKind.YOUTUBE -> YouTubeSource(config)
    }

    /**
     * Whether an already-built instance still matches its stored config —
     * false after an edit that changes where it points, which is exactly when
     * the warm instance must be thrown away. The manifest switches are left
     * out: they change no address, and a source reads them live from here.
     */
    private fun MusicSource.configuredBy(config: SourceConfig): Boolean =
        this is ConfigBacked && this.config.withPolicyOf(config) == config

    /** Implemented by sources that carry their [SourceConfig], so [publish] can tell a real edit from a no-op. */
    internal interface ConfigBacked {
        val config: SourceConfig
    }

    // ── Track identity ──────────────────────────────────────────────────

    /**
     * A source-backed track's id, as it travels through the queue.
     *
     * Packed into the existing [Song.videoId][com.music.streamx.data.model.Song.videoId]
     * rather than added beside it: that field is the app's media id everywhere —
     * the queue, the notification, the history, the like state — and a second
     * identity field would have to be threaded through every one of them, with
     * each place that forgot silently falling back to treating the track as
     * YouTube's.
     */
    fun trackKey(configId: String, trackId: String) = "$PREFIX$configId$SEPARATOR$trackId"

    /** The `(configId, trackId)` inside a [trackKey], or null if this is an ordinary YouTube id. */
    fun parseTrackKey(key: String): Pair<String, String>? = SourceTrackKeys.parse(key)

    /** The playback URI for a source-backed track; [PlaybackService] resolves it at open time. */
    fun trackUri(configId: String, trackId: String): String =
        Uri.Builder()
            .scheme("streamx")
            .authority("source")
            .appendQueryParameter("s", configId)
            .appendQueryParameter("t", trackId)
            .build()
            .toString()

    private val BUILT_IN_KINDS = listOf(SourceKind.STREAMX, SourceKind.CLOUDSTREAM, SourceKind.JIOSAAVN, SourceKind.YOUTUBE)

    private const val KEY_ORDER = "source_order"

    private const val KEY_EXTRA_FEEDS = "extraFeeds"
    private const val KEY_MULTI_FEED = "multiFeed"
    private const val KEY_SOURCES = "sources"
    /** One-shot migration: existing users must explicitly opt in again. */
    private const val KEY_JIOSAAVN_OPT_IN_V1 = "jiosaavn_opt_in_v1"
    private const val PREFIX = "src:"
    private const val SEPARATOR = "::"
}
