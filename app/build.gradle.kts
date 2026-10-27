import com.android.build.api.variant.BuildConfigField
import java.util.Locale
import java.util.Properties
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val appBaseVersion = "1.8"

/**
 * Signing details, kept out of the repository in `keystore.properties`
 * (see keystore.properties.example). Absent on a fresh checkout, in which case
 * the release build still runs and simply comes out unsigned rather than
 * failing — only whoever holds the key can produce a shippable APK.
 */
val signing = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val lastfmApiKey: String = (
    localProps.getProperty("LASTFM_API_KEY")
        ?: System.getenv("LASTFM_API_KEY")
        ?: ""
    ).trim()
val lastfmSecret: String = (
    localProps.getProperty("LASTFM_SECRET")
        ?: System.getenv("LASTFM_SECRET")
        ?: ""
    ).trim()

/*
 * Where Listen Together's party server lives. Not a credential — it is a public
 * URL, and every device in a party has to be pointed at the same one — but it is
 * deployment-specific rather than a property of the source, which is what puts
 * it here beside the others instead of in a constant.
 *
 * Empty is a supported state, not a broken build: the field below is only the
 * *default* the address box on the Listen Together screen starts with, and
 * anything typed there wins and persists. So a fresh checkout without this line
 * builds and runs, and simply asks for an address the first time somebody opens
 * the screen. See ListenTogether.DEFAULT_SERVER.
 */
val listenTogetherServer: String = (
    localProps.getProperty("LISTEN_TOGETHER_SERVER")
        ?: System.getenv("LISTEN_TOGETHER_SERVER")
        ?: "https://streamx-listen-together.onrender.com"
    ).trim().trimEnd('/')

/*
 * Advanced Automix's engine and models are not in the APK: they are downloaded
 * on demand from this address, which holds automix-manifest.json and the
 * per-ABI packages that `./gradlew :app:packageAutomix` writes to
 * app/build/automix-package. Deployment-specific like the server above, so it
 * lives in local.properties or the environment. Empty is a supported state:
 * Settings then says the download is not available, and Automix keeps to its
 * plain-fade fallback.
 */
val automixBaseUrl: String = (
    localProps.getProperty("STREAMX_AUTOMIX_BASE_URL")
        ?: System.getenv("STREAMX_AUTOMIX_BASE_URL")
        ?: "https://github.com/StreamXProject/StreamX-Releases/releases/download/automix-1"
    ).trim().trimEnd('/')

/*
 * The downloaded engine package, as the app understands it. The ONNX Runtime
 * version must match the Java classes compiled in below (the native bridge
 * refuses a mismatched API), so the engine and bridge hashes are pinned here
 * from that release's AAR, per ABI, and checked on the phone before anything
 * is loaded — a manifest can update the models but never swap the native code.
 * Bumping onnxRuntimeVersion means re-deriving these; packageAutomix fails
 * the build if the AAR no longer matches them.
 */
val onnxRuntimeVersion = "1.28.0"
val automixPackageVersion = 1
val automixEnginePins = mapOf(
    "arm64-v8a" to (
        "f826d8efb03adf0a84f10e7ba408f9d4cd11b0a2ccd8d08aeb0f7451fb50cacc" to
            "99db53ca399095cfa17f3f40924b1012a0ff5846c32198e8cef01dd8480f6b45"
        ),
    "armeabi-v7a" to (
        "19e4ab7b9c374d9a3900a6382b2e4978267b8d4b874596c64c8bf49bb7560b47" to
            "2ca1f065169a8204cbc8140f14f49db0d67d50a47443ea8fdd5601fa1d8980e2"
        ),
    "x86_64" to (
        "8a31feb45126d981ffac430bd10bc66cfa1036e0864a9fbd80a94121f3085800" to
            "064dbef1fe9eab5727974b051d9249f4d4079bd059f7275efb1e2d9fcacb92c4"
        ),
)

/*
 * Bump this by hand before cutting each sideloaded test build ("beta2",
 * "beta3", ...) and blank it out before cutting the real release. Marks the
 * versionName below as a pre-release: AppUpdateChecker.isNewer() treats any
 * "-suffix" as older than a clean release of the same number, so testers
 * still get the update prompt once the matching tag is actually published.
 *
 * Applied to release builds as well as debug ones, and that is the whole
 * point of it. A sideloaded beta is a *release* build — signed with the real
 * key, installed over the real package — so leaving the marker off it is
 * exactly the case that strands a tester: their build calls itself 1.6.1,
 * the published 1.6.1 then matches it, isNewer() says no, and no prompt ever
 * comes. Blanking this line is the one step that turns a beta into a release,
 * so it is the one place to get right.
 */
val betaSuffix = ""

// Keep the Media3 1.11 Java wrapper and use the supplied decoder's matching JNI ABI.
// Import only the decoder: the AAR also contains Android system libc/liblog stubs.
val extractAlacDecoder by tasks.registering(Sync::class) {
    from(zipTree(layout.projectDirectory.file("libs/lib-decoder-ffmpeg-release.aar"))) {
        include("jni/**/libffmpegJNI.so")
        eachFile { path = path.removePrefix("jni/") }
        includeEmptyDirs = false
    }
    into(layout.buildDirectory.dir("generated/alacDecoder/jniLibs"))
}

android {
    namespace = "com.music.streamx"
    // InnerTubeX's AAR requires compiling against 37; targetSdk (runtime behaviour) stays 36.
    compileSdk = 37

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    defaultConfig {
        applicationId = "com.music.streamx"
        // 26 keeps reach wide; real-time blur (RenderEffect) kicks in on API 31+,
        // Haze falls back to a translucent scrim below that.
        minSdk = 26
        targetSdk = 36
        // CI passes -PappBuildNumber=N (the workflow's run number), so each
        // published build is 1.8.N with a code above every earlier one, as
        // skins are. Local builds stay plain 1.8.
        val appBuildNumber = providers.gradleProperty("appBuildNumber").orNull?.toInt()?.also {
            require(it in 1..9999) { "appBuildNumber must be between 1 and 9999" }
        }
        versionCode = if (appBuildNumber == null) 26 else 26 * 10000 + appBuildNumber
        versionName = if (appBuildNumber == null) appBaseVersion else "$appBaseVersion.$appBuildNumber"
        buildConfigField("String", "UPDATE_APK_PREFIX", "\"StreamX-\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Last.fm credentials are supplied locally and never committed.
        buildConfigField("String", "LASTFM_API_KEY", "\"${lastfmApiKey.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        buildConfigField("String", "LASTFM_SECRET", "\"${lastfmSecret.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        buildConfigField("String", "AUTOMIX_BASE_URL", "\"$automixBaseUrl\"")
        buildConfigField("String", "AUTOMIX_ONNX_RUNTIME", "\"$onnxRuntimeVersion\"")
        buildConfigField("int", "AUTOMIX_PACKAGE_VERSION", "$automixPackageVersion")
        // "abi:engineSha256:bridgeSha256" entries, ';'-separated. See automixEnginePins.
        buildConfigField(
            "String",
            "AUTOMIX_ENGINE_PINS",
            "\"${automixEnginePins.entries.joinToString(";") { (abi, pins) -> "$abi:${pins.first}:${pins.second}" }}\"",
        )
        buildConfigField(
            "String",
            "LISTEN_TOGETHER_SERVER",
            "\"${listenTogetherServer.replace("\\", "\\\\").replace("\"", "\\\"")}\"",
        )
    }

    splits {
        abi {
            isEnable = true
            reset()
            val requestedAbis = providers.gradleProperty("streamxAbis").orNull
                ?.split(',')?.map(String::trim)
            val supportedAbis = listOf("armeabi-v7a", "arm64-v8a", "x86_64")
            require(requestedAbis == null || requestedAbis.isNotEmpty() && requestedAbis.all { it in supportedAbis }) {
                "streamxAbis must contain supported Android ABIs: $supportedAbis"
            }
            include(*(requestedAbis ?: supportedAbis).toTypedArray())
            isUniversalApk = requestedAbis == null
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // applicationId can only be overridden per flavor, not per build type, so a
    // dev/prod dimension exists purely to let both sit installed side by side
    // on the same device instead of the dev build overwriting the prod one.
    flavorDimensions += "env"
    productFlavors {
        create("dev") {
            dimension = "env"
            applicationId = "com.dev.streamx"
            resValue("string", "app_name", "StreamX Dev")
        }
        create("prod") {
            dimension = "env"
            // Matches defaultConfig — this is the package already shipped/installed.
        }
    }

    signingConfigs {
        // Both halves have to be there, not just the properties file: it *names*
        // the keystore rather than containing it, and both are gitignored
        // separately, so a checkout can easily end up with the one and not the
        // other. A signing config pointing at a keystore that is not on disk
        // fails the release build outright at validateSigningRelease — which is
        // exactly the failure the unsigned fallback above exists to avoid, so
        // the keystore has to be looked for rather than assumed.
        val store = signing.getProperty("storeFile")?.let { rootProject.file(it) }
        if (store != null && store.exists()) {
            create("release") {
                storeFile = store
                storePassword = signing.getProperty("storePassword")
                keyAlias = signing.getProperty("keyAlias")
                keyPassword = signing.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            if (betaSuffix.isNotEmpty()) versionNameSuffix = "-$betaSuffix"
        }
        release {
            // Carried here too — see [betaSuffix]. A sideloaded beta is a
            // release build, and it is the one that most needs the marker.
            if (betaSuffix.isNotEmpty()) versionNameSuffix = "-$betaSuffix"
            /*
             * On for what it does to speed, not size. Compose is written to be
             * run through R8 — without it every composable keeps the debug-era
             * shape the compiler emits, and the whole UI runs measurably slower.
             *
             * Nothing is renamed (-dontobfuscate), and every library that reaches
             * for classes by name — Rhino running YouTube's player JavaScript,
             * NewPipe, InnerTubeX, QuickJS, SMBJ and BouncyCastle, ONNX's JNI,
             * protobuf-lite, Ktor — is kept whole: see proguard-rules.pro. What R8
             * is left to optimise is Compose, Media3, coroutines and our own
             * code, which is where the time goes. Checked on a device through the
             * `benchmark` build type below before it ships.
             */
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
                "skin-ui-api.pro"
            )
            // Null without a keystore to sign with: the build then produces
            // app-release-unsigned.apk instead of failing outright.
            signingConfig = signingConfigs.findByName("release")
        }
        /*
         * The release build, installable next to the dev and prod apps: same R8,
         * same non-debuggable runtime, signed with the debug key under its own
         * package so it never replaces either. For measuring startup the way
         * users get it and for checking that shrinking broke nothing — a debug
         * build is interpreted and verified at runtime and says little about
         * either. `./gradlew installDevBenchmark`.
         */
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".benchmark"
            matchingFallbacks += listOf("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets.getByName("main").jniLibs.setSrcDirs(
        listOf(layout.buildDirectory.dir("generated/alacDecoder/jniLibs")),
    )
    packaging {
        resources.merges += setOf("META-INF/LICENSE.md", "META-INF/NOTICE.md")
        resources {
            // SMBJ's BouncyCastle and jspecify both ship this descriptor.
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            // Unit tests run against a stub android.jar whose methods throw
            // rather than return. That is the right default for anything whose
            // behaviour depends on the framework, and wrong for android.util.Log
            // — which [TrackLog] calls on every decision the source layer makes,
            // so a test of that layer fails on the logging rather than on the
            // logic it was written to check.
            isReturnDefaultValues = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-nowarn",
            "-Xsuppress-version-warnings"
        )
    }
}

/*
 * NewPipeExtractor ships its own org.schabi.newpipe.extractor.utils.Utils, and
 * app/src/main/java carries a patched copy at the same package path (see that
 * file for why it exists). A debug build keeps project and library dex separate,
 * so the project copy simply wins at class-load time and the two coexist; a
 * release build merges every input into one dex set, where D8 rejects the
 * duplicate type outright ("Utils is defined multiple times"). So the library's
 * copy is stripped from its jar before it reaches dexing, leaving exactly one
 * definition of the class in the build.
 *
 * The artifact is resolved on its own and non-transitive purely to re-jar it;
 * the transitive dependencies it would otherwise have carried are declared by
 * hand in the dependencies block below, since dropping the module drops them too.
 */
val newPipeExtractorRaw: Configuration by configurations.creating {
    isTransitive = false
    isCanBeConsumed = false
}
/** Public Telegram native App URL host; environment overrides local.properties. */
fun telegramHostSetting(name: String): String? =
    providers.environmentVariable(name).orNull?.trim()?.takeIf { it.isNotEmpty() }
        ?: localProps.getProperty(name)?.trim()?.takeIf { it.isNotEmpty() }

androidComponents {
    onVariants(selector().all()) { variant ->
        val flavor = variant.productFlavors.first { it.first == "env" }.second.uppercase(Locale.ROOT)
        val buildType = variant.buildType.orEmpty().uppercase(Locale.ROOT)
        val key = "TELEGRAM_LOGIN_HOST_$flavor"
        val host = telegramHostSetting("${key}_$buildType") ?: telegramHostSetting(key).orEmpty()
        require(host.isEmpty() || Regex("[a-z0-9]+(?:[.-][a-z0-9]+)+").matches(host)) {
            "$key must be a hostname only, without https://, a path, or query parameters."
        }
        checkNotNull(variant.buildConfigFields) { "BuildConfig must be enabled for Telegram native login configuration." }
            .put("TELEGRAM_LOGIN_HOST", BuildConfigField("String", "\"$host\"", "Telegram native login callback host"))
        // An unconfigured build remains usable for password login and claims no real callback host.
        variant.manifestPlaceholders.put("telegramLoginHost", host.ifBlank { "telegram-login.invalid" })
    }
}

dependencies {
    implementation("io.coil-kt.coil3:coil-svg:3.0.4")
    implementation("com.google.android.gms:play-services-wearable:20.0.1")
    implementation("androidx.wear:wear-remote-interactions:1.1.0")
    implementation("com.github.MuntashirAkon:libadb-android:3.1.1") { exclude(group = "org.bouncycastle") }
    implementation("org.conscrypt:conscrypt-android:2.5.3")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.85")
    newPipeExtractorRaw("com.github.TeamNewPipe:NewPipeExtractor:v0.26.3")
}
val newPipeExtractorStripped = tasks.register<org.gradle.api.tasks.bundling.Jar>(
    "stripNewPipeExtractorUtils"
) {
    archiveFileName.set("NewPipeExtractor-v0.26.3-noutils.jar")
    destinationDirectory.set(layout.buildDirectory.dir("stripped-libs"))
    from(provider { newPipeExtractorRaw.map { zipTree(it) } }) {
        // The class itself, plus any nested or synthetic siblings the upstream
        // compiler emitted alongside it, so nothing from the jar's Utils survives.
        exclude("org/schabi/newpipe/extractor/utils/Utils.class")
        exclude("org/schabi/newpipe/extractor/utils/Utils\$*.class")
    }
}

/*
 * ONNX Runtime's Java classes without its native libraries or its loader.
 *
 * The onnxruntime-android AAR would put ~29 MB of engine per ABI into the APK.
 * Advanced Automix downloads that engine instead (see AutomixEngine), so the
 * AAR is resolved on its own, non-transitively, and only its classes.jar is
 * consumed — minus ai/onnxruntime/OnnxRuntime.class, which app/src/main/java
 * replaces with a copy that can load the engine from the download directory.
 * Same reason for stripping as NewPipe's Utils above.
 */
val onnxRuntimeAar: Configuration by configurations.creating {
    isTransitive = false
    isCanBeConsumed = false
}
dependencies {
    onnxRuntimeAar("com.microsoft.onnxruntime:onnxruntime-android:$onnxRuntimeVersion@aar")
}
val onnxRuntimeClasses = tasks.register<org.gradle.api.tasks.bundling.Jar>("stripOnnxRuntimeLoader") {
    archiveFileName.set("onnxruntime-android-$onnxRuntimeVersion-classes.jar")
    destinationDirectory.set(layout.buildDirectory.dir("stripped-libs"))
    from(provider {
        onnxRuntimeAar.map { aar -> zipTree(zipTree(aar).matching { include("classes.jar") }.singleFile) }
    }) {
        exclude("ai/onnxruntime/OnnxRuntime.class")
        exclude("META-INF/**")
    }
}

/*
 * The download Settings offers for advanced Automix: one zip per ABI holding the
 * ONNX Runtime engine and JNI bridge from the AAR above plus both models, with
 * a package.json inside giving each file's size and SHA-256, and an
 * automix-manifest.json beside them giving each zip's. Upload the contents of
 * app/build/automix-package to STREAMX_AUTOMIX_BASE_URL.
 */
val automixModels = layout.projectDirectory.dir("automix-models")
val packageAutomix = tasks.register("packageAutomix") {
    val output = layout.buildDirectory.dir("automix-package")
    inputs.files(onnxRuntimeAar)
    inputs.dir(automixModels)
    outputs.dir(output)
    doLast {
        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val out = output.get().asFile.apply { deleteRecursively(); mkdirs() }
        val aar = ZipFile(onnxRuntimeAar.singleFile)
        val models = listOf("beat_this_int8.onnx", "vocals_umxhq_int8.onnx")
            .map { it to automixModels.file(it).asFile.readBytes() }
        val packages = automixEnginePins.map { (abi, pins) ->
            fun native(name: String) = aar.getInputStream(aar.getEntry("jni/$abi/$name")).use { it.readBytes() }
            val engine = native("libonnxruntime.so")
            val bridge = native("libonnxruntime4j_jni.so")
            check(sha256(engine) == pins.first && sha256(bridge) == pins.second) {
                "ONNX Runtime $onnxRuntimeVersion for $abi no longer matches automixEnginePins"
            }
            val files = listOf("libonnxruntime.so" to engine, "libonnxruntime4j_jni.so" to bridge) + models
            val description = groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(mapOf(
                "version" to automixPackageVersion,
                "abi" to abi,
                "onnxRuntime" to onnxRuntimeVersion,
                "files" to files.map { (name, bytes) ->
                    mapOf("name" to name, "size" to bytes.size, "sha256" to sha256(bytes))
                },
            )))
            val zipName = "streamx-automix-$automixPackageVersion-$abi.zip"
            val zip = File(out, zipName)
            ZipOutputStream(zip.outputStream()).use { stream ->
                (listOf("package.json" to description.toByteArray()) + files).forEach { (name, bytes) ->
                    // A fixed timestamp keeps the zip, and so its checksum, reproducible.
                    stream.putNextEntry(ZipEntry(name).apply { time = 315532800000L })
                    stream.write(bytes)
                    stream.closeEntry()
                }
            }
            abi to mapOf("file" to zipName, "size" to zip.length(), "sha256" to sha256(zip.readBytes()))
        }.toMap()
        aar.close()
        File(out, "automix-manifest.json").writeText(groovy.json.JsonOutput.prettyPrint(
            groovy.json.JsonOutput.toJson(mapOf(
                "version" to automixPackageVersion,
                "onnxRuntime" to onnxRuntimeVersion,
                "packages" to packages,
            )),
        ))
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":sharedUi"))

    // ---- Compose (Material 3) ----
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    // Pinned above the BOM's 1.7.6: [IosOverscroll] uses OverscrollFactory,
    // which that version doesn't have. Newer foundation alongside the BOM's
    // older ui/material3 is a combination Compose supports deliberately —
    // foundation depends on ui, not the reverse — and this exact pairing was
    // already in effect (foundation was reaching 1.10.0 transitively through
    // the liquid-glass library before that dependency was removed).
    implementation("androidx.compose.foundation:foundation:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    // ProcessLifecycleOwner: whether the app is on screen, for the open-app count.
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // ---- Media playback: Media3 / ExoPlayer ----
    implementation("androidx.media3:media3-exoplayer:1.11.0")
    implementation("androidx.media3:media3-session:1.11.0")
    implementation("androidx.media3:media3-common:1.11.0")
    implementation("androidx.media3:media3-datasource-okhttp:1.11.0")
    // Audio is progressive, but Apple serves its motion artwork as HLS — this
    // is what lets the animated sleeve play it. See CanvasArtworkPlayer.
    implementation("androidx.media3:media3-exoplayer-hls:1.11.0")
    // Source modules hand back manifests rather than files, and which kind is
    // the backend's choice, not ours: the Tidal one served `.m3u8` until
    // September 2026 and `.mpd` after it, for the same track and the same
    // request. Without this artifact a DASH manifest is not merely unplayed —
    // DefaultMediaSourceFactory cannot build a source for it, falls back to
    // progressive, and the extractors try to sniff XML as audio
    // (ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED). See withResolvedStreamType.
    implementation("androidx.media3:media3-exoplayer-dash:1.11.0")
    // FFmpeg/ALAC: the supplied app/libs AAR provides JNI binaries through
    // extractAlacDecoder. The vendored Media3 1.11 wrapper shares the existing
    // AudioSink and DSP chain and remains the fallback after platform decoding.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.9.0")
    implementation("androidx.browser:browser:1.8.0") // Telegram SDK browser fallback

    // ---- Images: Coil 3 + Palette (dominant colors for the mesh gradient) ----
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")
    implementation("androidx.palette:palette-ktx:1.0.0")

    // ---- Frosted glass / progressive blur (Telegram-style bars) ----
    implementation("dev.chrisbanes.haze:haze:1.3.1")
    implementation("dev.chrisbanes.haze:haze-materials:1.3.1")

    // ---- QR encoding, for the party invite ----
    // `core` only: the `android-core`/`zxing-android-embedded` artifacts bring
    // a camera scanner and an Activity with it, and nothing here reads a code —
    // a party is joined by tapping somebody else's link or typing six
    // characters. This produces the bit matrix; the drawing is ours, in
    // [com.music.streamx.ui.components.QrCode], so the result is styled like
    // the rest of the app rather than a stock black-and-white bitmap.
    implementation("com.google.zxing:core:3.5.3")

    // ---- Markdown rendering (release notes in the update dialog) ----
    // Pure Compose, not an AndroidView wrapper — needed so the text composes
    // correctly under the dialog's Haze blur.
    implementation("com.halilibo.compose-richtext:richtext-ui-material3:0.20.0")
    implementation("com.halilibo.compose-richtext:richtext-commonmark:0.20.0")

    // ---- Innertube (YouTube Music) client: Ktor + kotlinx.serialization ----
    // Ktor and serialization are held at InnerTubeX's versions (below) so the
    // upgrade it forces is explicit rather than resolved behind our backs.
    implementation("io.ktor:ktor-client-core:3.5.2")
    implementation("io.ktor:ktor-client-okhttp:3.5.2")
    implementation("io.ktor:ktor-client-content-negotiation:3.5.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.5.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    // ---- Discord Rich Presence: the gateway is a WebSocket, so Ktor needs the plugin ----
    implementation("io.ktor:ktor-client-websockets:3.5.2")

    // ---- YouTube stream extraction: live-benchmarked client catalog + cipher tiers ----
    implementation("com.github.MetrolistGroup.innertubex:innertubex-android:v0.7.0")

    // ---- Stream resolution: NewPipe solves YouTube's signature + `n` throttling ----
    // Pinned to v0.26.3, not the newer v0.26.4: v0.26.4's player-JS parser fails with
    // "Could not parse deobfuscation function" on the current player build, which blocks
    // WEB_REMIX's ciphered formats entirely. v0.26.3 solves the same signatures cleanly
    // against the same player JS — confirmed side by side against PixelMusic-ref, which
    // pins v0.26.3 and doesn't hit the parse failure.
    //
    // Consumed as a stripped jar rather than as the module, so its own
    // Utils.class does not reach dexing. See newPipeExtractorStripped above; the
    // transitive dependencies the module would have brought are listed here
    // because dropping its artifact drops them too. If the version changes,
    // re-derive this list with
    //   ./gradlew :app:dependencies --configuration prodReleaseRuntimeClasspath
    implementation(files(newPipeExtractorStripped))
    implementation("com.github.TeamNewPipe:nanojson:e9d656ddb49a412a5a0a5d5ef20ca7ef09549996")
    implementation("org.jsoup:jsoup:1.22.2")
    implementation("com.google.code.findbugs:jsr305:3.0.2")
    implementation("com.google.protobuf:protobuf-javalite:4.35.0")
    implementation("org.mozilla:rhino:1.8.1")
    implementation("org.mozilla:rhino-engine:1.8.1")

    // ---- Auth/session storage ----
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // ---- JS module execution: QuickJS VM for style source plugins ----
    // Held at InnerTubeX's version; the same VM runs QuickJsExecutor's module sources.
    implementation("io.github.dokar3:quickjs-kt-android:1.0.14")

    // ---- SMB file shares: pure-Java SMB2/3 client (listing + streaming) ----
    implementation("com.hierynomus:smbj:0.15.0")

    // ---- Automix: on-device beat/downbeat model (Beat This!, MIT-licensed) ----
    // The full android artifact, not onnxruntime-mobile: mobile only loads .ort
    // files, which would put an offline conversion step between the model and
    // the app for a saving that does not matter in a self-distributed APK.
    //
    // Consumed as its classes only: the engine itself is an on-demand download.
    // See onnxRuntimeClasses and packageAutomix above.
    implementation(files(onnxRuntimeClasses))

    // ---- Casting: Google Cast sender + the route discovery it sits on ----
    // The framework and the router only. media3-cast is left out on purpose:
    // its CastPlayer hands the receiver whatever URI a MediaItem carries, and
    // ours are `streamx://` addresses that only the service's resolver can
    // turn into a real stream — see [com.music.streamx.playback.cast.CastPlayback].
    implementation("com.google.android.gms:play-services-cast-framework:22.2.0")
    implementation("androidx.mediarouter:mediarouter:1.8.1")

    testImplementation("junit:junit:4.13.2")
    // A real HTTP server for the addon tests. The addon protocol is entirely
    // "what does this app send, and what does it do with what comes back", and
    // a hand-rolled fake of the client would be a test of the fake. Pinned to
    // the OkHttp version already on the runtime classpath.
    testImplementation("com.squareup.okhttp3:mockwebserver:5.3.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}

/*
 * A debug APK lands on the device uncompiled — `dumpsys package dexopt` reports
 * it as run-from-apk — so every launch verifies the whole app's classes at
 * runtime before a line of our code runs. Measured on the BlueStacks box, that
 * was about two seconds of every cold start and most of why the dev build felt
 * so much slower than a release one. `verify` is the cheapest filter that
 * removes it (~15s once per install), and unlike `speed` it leaves the debug
 * build debuggable exactly as before.
 *
 * Runs after `installDevDebug` from the command line. Android Studio's Run
 * button deploys on its own and never reaches this task, so from there run
 * `./gradlew verifyDevInstall` after installing.
 */
val verifyDevInstall = tasks.register("verifyDevInstall") {
    group = "install"
    description = "Pre-verifies the installed dev build on every connected device."
    val adb = androidComponents.sdkComponents.adb
    doLast {
        val adbPath = adb.get().asFile.absolutePath
        val serials = ProcessBuilder(adbPath, "devices").start()
            .inputStream.bufferedReader().readLines()
            .drop(1)
            .mapNotNull { line -> line.split('\t').takeIf { it.size == 2 && it[1] == "device" }?.get(0) }
        serials.forEach { serial ->
            logger.lifecycle("verifyDevInstall: compiling com.dev.streamx on $serial")
            ProcessBuilder(
                adbPath, "-s", serial, "shell", "cmd", "package", "compile",
                "-m", "verify", "-f", "com.dev.streamx",
            ).inheritIO().start().waitFor()
        }
    }
}
tasks.matching { it.name == "installDevDebug" }.configureEach { finalizedBy(verifyDevInstall) }

// Extraction is incremental and runs only when the local AAR changes.
tasks.named("preBuild").configure { dependsOn(extractAlacDecoder) }
