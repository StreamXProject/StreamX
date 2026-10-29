package com.music.streamx.ui.screens

import com.music.streamx.ui.skins.SkinIcon as Icon
import com.music.streamx.wear.WatchInstaller
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BlurOff
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Brightness4
import androidx.compose.material.icons.rounded.Cached
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Gradient
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material.icons.rounded.MotionPhotosOff
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.WebAssetOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material.icons.rounded.Wifi
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import com.music.streamx.data.lyrics.translationLanguageName
import com.music.streamx.ui.components.SkinAlertDialog as AlertDialog
import com.music.streamx.playback.smart.AutomixEngine.State as AutomixState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import com.music.streamx.ui.components.SkinOutlinedTextField as OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import com.music.streamx.ui.components.SkinSwitch as Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalHapticFeedback
import com.music.streamx.ui.skins.skinAndroidPainterResource as painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import com.music.streamx.ui.components.isGlassSupported
import com.music.streamx.ui.components.languageDisplayNameRes
import com.music.streamx.ui.components.MessageState
import com.music.streamx.ui.components.SearchField
import com.music.streamx.ui.components.thumbnailBorder
import com.music.streamx.ui.icons.StreamXIcons
import com.music.streamx.ui.performance.resolvePerformanceRefreshRate
import com.music.streamx.ui.performance.supportedPerformanceRefreshRates
import com.music.streamx.data.model.Account
import com.music.streamx.data.LocalMediaRepository
import com.music.streamx.data.NerdStats
import com.music.streamx.data.scrobbling.LastFM
import com.music.streamx.data.listentogether.ListenTogether
import com.music.streamx.data.settings.AppSettings
import com.music.streamx.data.settings.OutputPcmMode
import com.music.streamx.playback.AudioOutputStatus
import com.music.streamx.data.settings.AutomixPerformanceMode
import com.music.streamx.R
import com.music.streamx.data.sources.DeviceCodecs
import com.music.streamx.data.settings.AudioQuality
import com.music.streamx.data.settings.DownloadQuality
import com.music.streamx.data.settings.ThemeMode
import com.music.streamx.data.stats.Backup
import com.music.streamx.playback.AudioCache
import com.music.streamx.ui.player.fullBleedArtworkAvailable
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import java.util.Locale

/**
 * Grouped settings, in the shape phones have taught people to expect: inset
 * cards of rows, a leading glyph per row, the current value on the right, and a
 * plain-language footer under any group whose effect isn't obvious from its
 * title. Anything with more than two choices opens a sheet rather than pushing
 * a row of chips into the layout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    /** The window's width, for the gates that depend on it. */
    windowWidth: Dp,
    signedIn: Boolean,
    account: Account?,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onAccountScrobbling: () -> Unit,
    onEqualizer: () -> Unit,
    onOpenReplay: () -> Unit,
    onLyricsSources: () -> Unit,
    onTranslationLanguage: () -> Unit,
    onSources: () -> Unit,
    onPlaylistSync: () -> Unit,
    onSkinsPlugins: () -> Unit,
    onListenTogether: () -> Unit,
    onWearOs: () -> Unit,
    onSpotifyCanvasAuth: () -> Unit,
    onAppLanguage: () -> Unit,
    onEditSignatureApi: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onBack: ()->Unit = {},
) {
    val context = LocalContext.current

    val wifiQuality by AppSettings.audioQualityWifi.collectAsStateWithLifecycle()
    val cellularQuality by AppSettings.audioQualityCellular.collectAsStateWithLifecycle()
    val metered by AppSettings.meteredConnection.collectAsStateWithLifecycle()
    val crossfade by AppSettings.crossfadeSeconds.collectAsStateWithLifecycle()
    val smartFade by AppSettings.smartFadeEnabled.collectAsStateWithLifecycle()
    val automixPerformance by AppSettings.automixPerformanceMode.collectAsStateWithLifecycle()
    val skipSilence by AppSettings.skipSilence.collectAsStateWithLifecycle()
    val dolbyAtmos by AppSettings.dolbyAtmos.collectAsStateWithLifecycle()
    // A property of the hardware, so it is read once rather than remembered
    // against a key that can never change — see [DeviceCodecs.playsDolbyAtmos],
    // which caches the codec-list walk for the life of the process.
    val dolbyAtmosSupported = DeviceCodecs.playsDolbyAtmos
    val spatialAudio by AppSettings.spatialAudio.collectAsStateWithLifecycle()
    val nerdStats by AppSettings.showNerdStats.collectAsStateWithLifecycle()
    val reduceAnimation by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val liquidGlass by AppSettings.liquidGlass.collectAsStateWithLifecycle()
    val liquidGlassSupported = isGlassSupported()
    val lyricsBlur by AppSettings.lyricsBlur.collectAsStateWithLifecycle()
    val animatedCanvas by AppSettings.animatedCanvas.collectAsStateWithLifecycle()
    val canvasOverCellular by AppSettings.canvasOverCellular.collectAsStateWithLifecycle()
    val signatureApiEnabled by AppSettings.signatureApiEnabled.collectAsStateWithLifecycle()
    val signatureApiUrl by AppSettings.signatureApiUrl.collectAsStateWithLifecycle()
    val fullBleedArtwork by AppSettings.fullBleedArtwork.collectAsStateWithLifecycle()
    val legacyMeshGradient by AppSettings.legacyMeshGradient.collectAsStateWithLifecycle()
    val syncedLyrics by AppSettings.syncedLyrics.collectAsStateWithLifecycle()
    val lyricsSources by AppSettings.lyricsSources.collectAsStateWithLifecycle()
    val translationLanguage by AppSettings.translationLanguage.collectAsStateWithLifecycle()
    val theme by AppSettings.themeMode.collectAsStateWithLifecycle()
    val sessionId by AppSettings.audioSessionId.collectAsStateWithLifecycle()
    val outputPcmMode by AppSettings.outputPcmMode.collectAsStateWithLifecycle()
    val preferUsbDac by AppSettings.preferUsbDac.collectAsStateWithLifecycle()
    val loudnessNormalization by AppSettings.loudnessNormalization.collectAsStateWithLifecycle()
    val loudnessOffOnSpeaker by AppSettings.loudnessOffOnSpeaker.collectAsStateWithLifecycle()
    val outputStatus by AudioOutputStatus.current.collectAsStateWithLifecycle()
    val playingFormat by NerdStats.current.collectAsStateWithLifecycle()
    val playingDolbyAtmos = playingFormat?.isDolbyAtmos == true
    val cacheLimitBytes by AppSettings.audioCacheLimitBytes.collectAsStateWithLifecycle()
    val showCacheFolder by AppSettings.showCacheFolder.collectAsStateWithLifecycle()
    val downloadQuality by AppSettings.downloadQuality.collectAsStateWithLifecycle()
    val wifiOnlyDownloads by AppSettings.wifiOnlyDownloads.collectAsStateWithLifecycle()
    val exportDownloads by AppSettings.exportDownloads.collectAsStateWithLifecycle()
    val stopOnTaskRemoved by AppSettings.stopOnTaskRemoved.collectAsStateWithLifecycle()
    val hideVolumeBar by AppSettings.hideVolumeBar.collectAsStateWithLifecycle()
    val hideStatusBarInPlayer by AppSettings.hideStatusBarInPlayer.collectAsStateWithLifecycle()
    val hideSongStatus by AppSettings.hideSongStatus.collectAsStateWithLifecycle()
    val showQueueHistory by AppSettings.showQueueHistory.collectAsStateWithLifecycle()
    val swipeToPlayNext by AppSettings.swipeToPlayNext.collectAsStateWithLifecycle()
    val dontRepeatSuggestions by AppSettings.dontRepeatSuggestions.collectAsStateWithLifecycle()
    val preferMusicOnly by AppSettings.preferMusicOnly.collectAsStateWithLifecycle()
    val smartVersionAlignment by AppSettings.smartVersionAlignment.collectAsStateWithLifecycle()
    val filterNonMusicAudio by AppSettings.filterNonMusicAudio.collectAsStateWithLifecycle()
    val localMusicFolderUri by AppSettings.localMusicFolderUri.collectAsStateWithLifecycle()
    val highPerformanceMode by AppSettings.highPerformanceMode.collectAsStateWithLifecycle()
    val performanceRefreshRate by AppSettings.performanceRefreshRate.collectAsStateWithLifecycle()
    val currentDisplay = LocalView.current.display
    val supportedRefreshRates = remember(currentDisplay) {
        currentDisplay.supportedPerformanceRefreshRates()
    }
    val selectedPerformanceRefreshRate = remember(currentDisplay, performanceRefreshRate) {
        currentDisplay.resolvePerformanceRefreshRate(performanceRefreshRate)
    }

    LaunchedEffect(selectedPerformanceRefreshRate, performanceRefreshRate) {
        if (selectedPerformanceRefreshRate != performanceRefreshRate) {
            AppSettings.setPerformanceRefreshRate(selectedPerformanceRefreshRate)
        }
    }

    // Scrobbling states
    val lastfmEnabled by AppSettings.lastfmEnabled.collectAsStateWithLifecycle()
    val lastfmUsername by AppSettings.lastfmUsername.collectAsStateWithLifecycle()
    val lastfmSessionKey by AppSettings.lastfmSessionKey.collectAsStateWithLifecycle()
    val lastfmScrobbleEnabled by AppSettings.lastfmScrobbleEnabled.collectAsStateWithLifecycle()
    val lastfmNowPlayingEnabled by AppSettings.lastfmNowPlaying.collectAsStateWithLifecycle()
    val scrobbleMinDuration by AppSettings.scrobbleMinDuration.collectAsStateWithLifecycle()
    val scrobbleDelayPercent by AppSettings.scrobbleDelayPercent.collectAsStateWithLifecycle()
    val scrobbleDelaySeconds by AppSettings.scrobbleDelaySeconds.collectAsStateWithLifecycle()
    val listenBrainzEnabled by AppSettings.listenBrainzEnabled.collectAsStateWithLifecycle()
    val listenBrainzToken by AppSettings.listenBrainzToken.collectAsStateWithLifecycle()

    val replayGenres by AppSettings.replayGenres.collectAsStateWithLifecycle()

    // Read here so the row can say "In a party · ABC123" rather than making
    // somebody open the screen to find out whether they are still in one.
    val party by ListenTogether.state.collectAsStateWithLifecycle()

    // Filters the rows below — see [SettingsSearch]. Blank shows everything,
    // exactly as if the field weren't there.
    var searchQuery by remember { mutableStateOf("") }
    /** The section open as its own page, by group key; null is the main list. */
    var page by rememberSaveable { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf<QualityTarget?>(null) }
    var pickingDownloadQuality by remember { mutableStateOf(false) }
    var pickingAutomixPerformance by remember { mutableStateOf(false) }
    var confirmAutomixDownload by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        com.music.streamx.playback.smart.AutomixEngine.attach(context)
        com.music.streamx.playback.smart.AutomixEngine.refreshSize()
    }
    val automixEngineState = com.music.streamx.playback.smart.AutomixEngine.state.collectAsStateWithLifecycle()
    val automixEngine by automixEngineState
    // Read here, outside the groups, so the card drops its download row when a
    // download starts: a group's own rows are only rebuilt with the sheet.
    // Derived, so progress ticks do not recompose the whole sheet.
    val automixFetchingState = remember {
        androidx.compose.runtime.derivedStateOf {
            automixEngineState.value.let { it is AutomixState.Downloading || it is AutomixState.Verifying }
        }
    }
    val automixFetching = automixFetchingState.value
    // What the last export or import did, shown on the row that did it rather
    // than as a toast: a backup is the one action here whose outcome nobody can
    // check by looking at the app afterwards. Held per direction, or an import's
    // result reports itself under the word "Export".
    var exportStatus by remember { mutableStateOf<String?>(null) }
    var importStatus by remember { mutableStateOf<String?>(null) }
    var confirmImport by remember { mutableStateOf(false) }
    var confirmClearSongCache by remember { mutableStateOf(false) }
    var showPerformanceWarning by remember { mutableStateOf(false) }
    var showPerformanceConfirmation by remember { mutableStateOf(false) }
    val backupScope = rememberCoroutineScope()

    val batterySettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        showPerformanceConfirmation = true
    }
    val localMusicFolderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { folder ->
        if (folder == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                folder,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        AppSettings.setLocalMusicFolderUri(folder.toString())
    }

    /**
     * Both halves go through the system document picker rather than a path of
     * this app's own choosing. That is what puts the file somewhere the user can
     * actually find it — Drive, Files, a folder they already back up — and it
     * means neither direction needs a storage permission, since the grant
     * arrives with the document they picked.
     */
    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { target ->
        if (target == null) return@rememberLauncherForActivityResult
        backupScope.launch {
            exportStatus = Backup.exportTo(context, target).fold(
                onSuccess = { months ->
                    context.getString(R.string.export_succeeded, context.countOfMonths(months))
                },
                onFailure = {
                    context.getString(R.string.export_failed, it.message ?: context.getString(R.string.unknown_error))
                },
            )
        }
    }
    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { source ->
        if (source == null) return@rememberLauncherForActivityResult
        backupScope.launch {
            importStatus = Backup.importFrom(context, source).fold(
                onSuccess = {
                    context.getString(R.string.import_succeeded, context.countOfMonths(it.months), it.from)
                },
                onFailure = {
                    context.getString(R.string.import_failed, it.message ?: context.getString(R.string.unknown_error))
                },
            )
        }
    }
    var showListenBrainzTokenDialog by remember { mutableStateOf(false) }
    var showLastfmLoginDialog by remember { mutableStateOf(false) }
    val scrobbleScope = rememberCoroutineScope()

    val version = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.0"
    }

    // Made fresh on every pass rather than remembered: it tallies the rows
    // that answered to the query, and that tally has to start from zero each
    // time the query changes.
    // Sections open as their own pages, as BitChord's do, unless a skin draws its
    // own settings page (some of those already split it into categories by key)
    // or a search is running, which looks through every section at once.
    val paged = com.music.streamx.ui.skins.LocalSkinRenderer.current !is com.music.streamx.ui.skins.SkinSettingsPageProvider &&
        searchQuery.isBlank()
    val openPage = page.takeIf { paged }
    val search = SettingsSearch(searchQuery, paged, openPage)
    androidx.activity.compose.BackHandler(enabled = openPage != null) { page = null }
    val back: () -> Unit = { if (openPage != null) page = null else onBack() }

    // What is left after a keystroke is a different list, and the offset the
    // last one was scrolled to means nothing in it.
    val scrollState = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
    var isFirstSearchComposition by remember { mutableStateOf(true) }
    LaunchedEffect(openPage) { if (!isFirstSearchComposition) scrollState.scrollTo(0) }
    LaunchedEffect(searchQuery) {
        if (isFirstSearchComposition && searchQuery.isEmpty()) {
            // Skip the initial composition during Activity recreation:
            // rememberSaveable already restored the scroll position.
            isFirstSearchComposition = false
        } else {
            isFirstSearchComposition = false
            scrollState.scrollTo(0)
        }
    }

    val pageTitle = openPage?.let { settingsSectionTitle(it) }
    SettingsPresentation(modifier, contentPadding, scrollState, searchQuery, {searchQuery=it}, back, pageTitle) {
        SearchableSettingsGroup(search, key = "account") {
            val accountTitle = stringResource(R.string.account_integrations)
            // What the row opens is the scrobbling screen, so the services it
            // signs into are worth typing at this field even though none of
            // them is named on the row itself.
            row(accountTitle, "scrobbling", "last.fm", "listenbrainz") {
                SettingsRow(
                    icon = Icons.Rounded.Person,
                    title = accountTitle,
                    subtitle = account?.email?.takeIf { it.isNotBlank() }
                        ?: stringResource(if (signedIn) R.string.signed_in else R.string.not_signed_in),
                    onClick = onAccountScrobbling,
                )
            }
            // Sits with the account rather than with Playback: a party is up to
            // five signed-in people, and being signed in is the whole of what
            // the row needs before it will do anything.
            val listenTogetherTitle = stringResource(R.string.listen_together)
            row(listenTogetherTitle, "jam", "party", "sync", "friends") {
                SettingsRow(
                    icon = Icons.Rounded.Groups,
                    title = listenTogetherTitle,
                    subtitle = party.code?.let {
                        stringResource(R.string.listen_together_in_party, it)
                    } ?: stringResource(R.string.listen_together_subtitle),
                    badge = party.members.size.takeIf { party.inParty && it > 1 }?.toString(),
                    onClick = onListenTogether,
                )
            }
        }

        // The row that used to sit at the top of this group was called
        // "Lossless / HQ Audio" and toggled `SourceRegistry.setModuleEnabled` —
        // it switched the *module source* on and off, not lossless. Sources
        // above lists that as the module's own row now. Lossless itself is no
        // longer a setting at all — see
        // [SourceResolver.requestForNow][com.music.streamx.data.sources.SourceResolver.requestForNow].
        SearchableSettingsGroup(search, header = stringResource(R.string.audio_quality), key = "audio_quality") {
            val sourceTitle = stringResource(R.string.source)
            val sourceSubtitle = "Music sources, plugins and appearance"
            row(sourceTitle, sourceSubtitle, "addon", "lossless", "skin", "plugin", "extension", "theme") {
                SettingsRow(
                    icon = Icons.Rounded.Extension,
                    title = sourceTitle,
                    subtitle = sourceSubtitle,
                    onClick = onSources,
                )
            }
            val onWifiTitle = stringResource(R.string.on_wifi)
            row(onWifiTitle, "wi-fi", "streaming quality") {
                SettingsRow(
                    icon = Icons.Rounded.Wifi,
                    title = onWifiTitle,
                    badge = stringResource(R.string.in_use).takeIf { metered == false },
                    value = wifiQuality.localizedLabel(),
                    onClick = { picking = QualityTarget.WIFI },
                )
            }
            val onMobileDataTitle = stringResource(R.string.on_mobile_data)
            row(onMobileDataTitle, "cellular", "streaming quality") {
                SettingsRow(
                    icon = Icons.Rounded.SignalCellularAlt,
                    title = onMobileDataTitle,
                    badge = stringResource(R.string.in_use).takeIf { metered == true },
                    value = cellularQuality.localizedLabel(),
                    onClick = { picking = QualityTarget.CELLULAR },
                )
            }
            // Sits with the quality ceilings rather than with Playback: it
            // decides which version of a track gets fetched, the same question
            // the two rows above answer, and not how one is played back.
            //
            // Greyed rather than hidden where the device can't decode E-AC-3.
            // A missing row reads as a feature the app doesn't have; a disabled
            // one with a reason under it is the difference between "StreamX
            // has no Atmos" and "this phone has no Dolby decoder", and only the
            // second is true. The stored preference is left untouched either
            // way — see [AppSettings.dolbyAtmos].
            val dolbyAtmosTitle = stringResource(R.string.dolby_atmos)
            row(dolbyAtmosTitle, "surround", "e-ac-3") {
                SettingsRow(
                    iconPainter = painterResource(R.drawable.ic_dolby_atmos),
                    title = dolbyAtmosTitle,
                    subtitle = stringResource(
                        if (dolbyAtmosSupported) {
                            R.string.dolby_atmos_subtitle
                        } else {
                            R.string.dolby_atmos_unavailable
                        },
                    ),
                    enabled = dolbyAtmosSupported,
                    trailing = {
                        com.music.streamx.ui.components.SkinSwitch(
                            checked = dolbyAtmos && dolbyAtmosSupported,
                            onCheckedChange = AppSettings::setDolbyAtmos,
                            enabled = dolbyAtmosSupported,
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                checkedBorderColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    },
                    onClick = { AppSettings.setDolbyAtmos(!dolbyAtmos) },
                )
            }
        }

        // Its own group rather than rows bolted onto the two above, because a
        // download is not a third kind of connection. The ceilings answer "what
        // does this minute cost"; these answer "what am I keeping, and when may
        // it be fetched" — and those two questions only make sense read
        // together, which is what puts them side by side here.
        if (paged && openPage == null) {
            SettingsGroup {
                SETTINGS_SECTIONS.forEachIndexed { index, (key, icon) ->
                    if (index > 0) RowDivider()
                    SettingsRow(icon = icon, title = settingsSectionTitle(key), onClick = { page = key })
                }
            }
        }

        SearchableSettingsGroup(search, header = stringResource(R.string.downloads), key = "downloads") {
            val downloadQualityTitle = stringResource(R.string.download_quality)
            row(downloadQualityTitle, "offline", "lossless") {
                SettingsRow(
                    icon = Icons.Rounded.Download,
                    title = downloadQualityTitle,
                    subtitle = stringResource(R.string.download_quality_subtitle, downloadQuality.perTrack),
                    value = downloadQuality.localizedLabel(),
                    onClick = { pickingDownloadQuality = true },
                )
            }
            // Reads as part of Download quality above it, not as a setting
            // of its own — same treatment as Play animated cover over
            // cellular gets under Animated cover art.
            val downloadWifiOnlyTitle = stringResource(R.string.download_wifi_only)
            row(downloadWifiOnlyTitle, "wi-fi", "cellular", divided = false) {
                SettingsSubRow(
                    title = downloadWifiOnlyTitle,
                    checked = wifiOnlyDownloads,
                    onCheckedChange = AppSettings::setWifiOnlyDownloads,
                    badge = stringResource(R.string.blocking).takeIf { wifiOnlyDownloads && metered == true },
                )
            }
            val exportDownloadsTitle = stringResource(R.string.export_compatible_downloads)
            row(exportDownloadsTitle, "music folder", divided = false) {
                SettingsSubRow(
                    title = exportDownloadsTitle,
                    checked = exportDownloads,
                    onCheckedChange = AppSettings::setExportDownloads,
                    subtitle = "Music/StreamX".takeIf { exportDownloads },
                )
            }
        }

        SearchableSettingsGroup(search, header = stringResource(R.string.playback), key = "playback") {
            val preferMusicOnlyTitle = stringResource(R.string.prefer_music_only)
            row(preferMusicOnlyTitle, "video", "audio", "music video") {
                SettingsRow(
                    icon = Icons.Rounded.SmartDisplay,
                    title = preferMusicOnlyTitle,
                    subtitle = stringResource(R.string.prefer_music_only_subtitle),
                    trailing = {
                        com.music.streamx.ui.components.SkinSwitch(
                            checked = preferMusicOnly,
                            onCheckedChange = AppSettings::setPreferMusicOnly,
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                checkedBorderColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    },
                    onClick = { AppSettings.setPreferMusicOnly(!preferMusicOnly) },
                )
            }
            val outputPrecisionTitle = stringResource(R.string.output_precision)
            row(outputPrecisionTitle, "pcm", "bit depth", "sample rate", "dac") {
                SettingsRow(
                    icon = Icons.Rounded.GraphicEq,
                    title = outputPrecisionTitle,
                    subtitle = buildString {
                        append(outputStatus.sink)
                        append(" · ")
                        append(outputStatus.deviceName)
                        (outputStatus.actualSampleRateHz ?: outputStatus.sampleRatesHz.firstOrNull())
                            ?.let { append(" · ${it / 1000.0} kHz") }
                        append(" · ")
                        append(AudioOutputStatus.encodingLabel(outputStatus))
                    },
                )
                SegmentedControl(
                    options = OutputPcmMode.entries.map(OutputPcmMode::label),
                    selectedIndex = OutputPcmMode.entries.indexOf(outputPcmMode),
                    onSelect = { AppSettings.setOutputPcmMode(OutputPcmMode.entries[it]) },
                    modifier = Modifier.padding(start = TEXT_INSET, end = ROW_INSET, bottom = 14.dp),
                )
            }
            val preferUsbDacTitle = stringResource(R.string.prefer_usb_dac)
            row(preferUsbDacTitle, "headphone", "output") {
                SettingsSubRow(
                    title = preferUsbDacTitle,
                    checked = preferUsbDac,
                    onCheckedChange = AppSettings::setPreferUsbDac,
                    badge = stringResource(R.string.connected).takeIf { outputStatus.isUsb },
                )
            }
            val loudnessTitle = stringResource(R.string.loudness_normalization)
            row(loudnessTitle, "loudness", "volume", "normalize", "replaygain", "lufs") {
                SettingsRow(
                    icon = Icons.Rounded.VolumeUp,
                    title = loudnessTitle,
                    subtitle = stringResource(R.string.loudness_normalization_subtitle),
                    trailing = {
                        com.music.streamx.ui.components.SkinSwitch(
                            checked = loudnessNormalization,
                            onCheckedChange = AppSettings::setLoudnessNormalization,
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                checkedBorderColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    },
                    onClick = { AppSettings.setLoudnessNormalization(!loudnessNormalization) },
                )
            }
            val loudnessSpeakerTitle = stringResource(R.string.loudness_off_on_speaker)
            row(loudnessSpeakerTitle, "loudness", "speaker", "normalize") {
                SettingsSubRow(
                    title = loudnessSpeakerTitle,
                    checked = loudnessOffOnSpeaker,
                    onCheckedChange = AppSettings::setLoudnessOffOnSpeaker,
                )
            }
            // Automix decides its own length from each pair of tracks —
            // tempo, key, structure — so it replaces the manual slider rather
            // than needing it set to anything first.
            if (!smartFade) {
                val crossfadeTitle = stringResource(R.string.crossfade)
                row(crossfadeTitle, "fade", "gapless") {
                    SliderRow(
                        icon = Icons.Rounded.Waves,
                        title = crossfadeTitle,
                        subtitle = stringResource(R.string.crossfade_subtitle),
                        value = if (crossfade == 0) stringResource(R.string.off) else "${crossfade}s",
                        sliderValue = crossfade.toFloat(),
                        onSliderValue = { AppSettings.setCrossfadeSeconds(it.roundToInt()) },
                        valueRange = 0f..12f,
                        steps = 11,
                    )
                }
            }
            val automixTitle = stringResource(R.string.automix)
            // Turning Automix on asks before fetching its engine, if it isn't here yet.
            fun setAutomix(enabled: Boolean) {
                if (enabled && automixEngine.let { it is AutomixState.Missing || it is AutomixState.Failed }) {
                    com.music.streamx.playback.smart.AutomixEngine.refreshSize()
                    confirmAutomixDownload = true
                    return
                }
                AppSettings.setSmartFadeEnabled(enabled)
            }
            row(automixTitle, "crossfade", "smart fade", "mix") {
                SettingsRow(
                    icon = Icons.Rounded.AutoAwesome,
                    title = automixTitle,
                    subtitle = when (val engine = automixEngine) {
                        // The download's progress takes the switch's place, so say what it is doing here.
                        is AutomixState.Downloading -> stringResource(
                            R.string.automix_engine_downloading_background,
                            android.text.format.Formatter.formatShortFileSize(context, engine.done),
                            android.text.format.Formatter.formatShortFileSize(context, engine.total),
                        )
                        AutomixState.Verifying -> stringResource(R.string.automix_engine_verifying)
                        else -> if (smartFade) {
                            stringResource(R.string.automix_enabled_subtitle)
                        } else {
                            stringResource(R.string.automix_disabled_subtitle)
                        }
                    },
                    trailing = {
                        if (automixFetching) {
                            AutomixDownloadProgress(automixEngine)
                        } else {
                            com.music.streamx.ui.components.SkinSwitch(
                                checked = smartFade,
                                onCheckedChange = { setAutomix(it) },
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                                    checkedBorderColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                        }
                    },
                    onClick = { if (!automixFetching) setAutomix(!smartFade) },
                )
            }
            if (smartFade && !automixFetching) {
                row(stringResource(R.string.automix_engine), "automix", "download", "model") {
                    AutomixDownloadRow()
                }
            }
            val automixPerformanceTitle = stringResource(R.string.automix_performance)
            row(automixPerformanceTitle, "cpu", "battery") {
                SettingsRow(
                    icon = Icons.Rounded.Tune,
                    title = automixPerformanceTitle,
                    subtitle = stringResource(R.string.automix_performance_subtitle),
                    value = automixPerformance.localizedLabel(),
                    onClick = { pickingAutomixPerformance = true },
                )
            }
            val skipSilenceTitle = stringResource(R.string.skip_silence)
            row(skipSilenceTitle, "silence") {
                SettingsRow(
                    icon = Icons.AutoMirrored.Rounded.VolumeOff,
                    title = skipSilenceTitle,
                    // Silence skipping is Media3's own processor, and
                    // `DefaultAudioSink.configure` appends that processor list
                    // only on its 16-bit branch — the float branch gets the
                    // format converter and nothing else. So on a float track
                    // this switch really does nothing, and the row says so
                    // rather than letting someone toggle it and wonder. The
                    // equaliser is unaffected: it runs upstream of that sink.
                    subtitle = if (outputStatus.actualEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
                        stringResource(R.string.skip_silence_float_subtitle)
                    } else {
                        stringResource(R.string.skip_silence_subtitle)
                    },
                    trailing = {
                        com.music.streamx.ui.components.SkinSwitch(
                            checked = skipSilence,
                            onCheckedChange = AppSettings::setSkipSilence,
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                checkedBorderColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    },
                    onClick = { AppSettings.setSkipSilence(!skipSilence) },
                )
            }
            val spatialAudioTitle = stringResource(R.string.spatial_audio)
            row(spatialAudioTitle, "surround", "3d") {
                SettingsRow(
                    icon = Icons.Rounded.SurroundSound,
                    title = spatialAudioTitle,
                    // Widening a JOC stream would fight the object-based mix
                    // Dolby has already spatialized, so the service holds this
                    // off for the duration of an Atmos track — see
                    // `PlaybackService.applySpatialAudioEnabled`. Said on the
                    // row because the switch stays where the listener left it,
                    // and a switch reading "on" over an effect that is not
                    // running is the same silent lie the equaliser screen used
                    // to tell.
                    subtitle = if (playingDolbyAtmos) {
                        stringResource(R.string.spatial_audio_atmos_subtitle)
                    } else {
                        stringResource(R.string.spatial_audio_subtitle)
                    },
                    trailing = {
                        com.music.streamx.ui.components.SkinSwitch(
                            checked = spatialAudio,
                            onCheckedChange = AppSettings::setSpatialAudio,
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                checkedBorderColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    },
                    onClick = { AppSettings.setSpatialAudio(!spatialAudio) },
                )
            }
            // The system panel is not listed here as well. A device with a
            // Dolby or Dirac panel has something StreamX cannot reproduce and
            // keeps its row — but one level in, at the foot of the equaliser
            // screen, rather than as a second equaliser entry alongside ours.
            val equalizerTitle = stringResource(R.string.equalizer)
            row(equalizerTitle, "eq", "bass", "treble") {
                SettingsRow(
                    icon = Icons.Rounded.Tune,
                    title = equalizerTitle,
                    subtitle = stringResource(R.string.equalizer_subtitle),
                    onClick = onEqualizer,
                )
            }
        }

        SearchableSettingsGroup(search, header = stringResource(R.string.appearance), key = "appearance") {
            val themeTitle = stringResource(R.string.theme)
            row(themeTitle, "dark mode", "light mode") {
                SettingsRow(icon = Icons.Rounded.Brightness4, title = themeTitle)
                SegmentedControl(
                    options = ThemeMode.entries.map { it.localizedLabel() },
                    selectedIndex = ThemeMode.entries.indexOf(theme),
                    onSelect = { AppSettings.setThemeMode(ThemeMode.entries[it]) },
                    modifier = Modifier.padding(start = ROW_INSET, end = ROW_INSET, bottom = 14.dp),
                )
            }
            val reduceAnimationTitle = stringResource(R.string.reduce_animation)
            row(reduceAnimationTitle, "motion") {
                SettingsRow(
                    icon = Icons.Rounded.MotionPhotosOff,
                    title = reduceAnimationTitle,
                    subtitle = stringResource(R.string.reduce_animation_subtitle),
                    trailing = {
                        com.music.streamx.ui.components.SkinSwitch(
                            checked = reduceAnimation,
                            onCheckedChange = AppSettings::setReduceAnimation,
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                checkedBorderColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    },
                    onClick = { AppSettings.setReduceAnimation(!reduceAnimation) },
                )
            }
            val reduceDynamicBlurTitle = stringResource(R.string.reduce_dynamic_blur)
            row(reduceDynamicBlurTitle, "blur", "performance") {
                SettingsRow(
                    icon = Icons.Rounded.BlurOff,
                    title = reduceDynamicBlurTitle,
                    subtitle = stringResource(R.string.reduce_dynamic_blur_subtitle),
                    trailing = {
                        com.music.streamx.ui.components.SkinSwitch(
                            checked = reduceDynamicBlur,
                            onCheckedChange = AppSettings::setReduceDynamicBlur,
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                checkedBorderColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    },
                    onClick = { AppSettings.setReduceDynamicBlur(!reduceDynamicBlur) },
                )
            }
            val liquidGlassTitle = stringResource(R.string.liquid_glass)
            row(liquidGlassTitle, "glass", "blur") {
                SettingsRow(
                    icon = Icons.Rounded.AutoAwesome,
                    title = liquidGlassTitle,
                    subtitle = stringResource(
                        if (liquidGlassSupported) {
                            R.string.liquid_glass_subtitle
                        } else {
                            R.string.liquid_glass_unavailable
                        },
                    ),
                    enabled = liquidGlassSupported,
                    trailing = {
                        com.music.streamx.ui.components.SkinSwitch(
                            checked = liquidGlass,
                            onCheckedChange = AppSettings::setLiquidGlass,
                            enabled = liquidGlassSupported,
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                checkedBorderColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    },
                    onClick = { AppSettings.setLiquidGlass(!liquidGlass) },
                )
            }
            // iOS 26's own control for the same thing, under the switch while it's on.
            if (liquidGlass && liquidGlassSupported) {
                row("Glass look", "glass", "clear", "tinted") {
                    val glassTint by AppSettings.liquidGlassTint.collectAsStateWithLifecycle()
                    // iOS's own: the picture with glass laid over it, live, and its slider.
                    com.music.streamx.ui.components.GlassLookPreview(glassTint, AppSettings::setLiquidGlassTint)
                    Text("Clear is more transparent and tinted increases opacity, adding contrast to content and controls.",
                        Modifier.padding(start = 30.dp, end = 30.dp, bottom = 12.dp), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // Left out where the player won't honour it: a window too wide for
            // the player to fill and too narrow to stand a page beside it keeps
            // the sleeve either way. A docked pane is a phone's width, so it does
            // honour it — see [fullBleedArtworkAvailable].
            if (fullBleedArtworkAvailable(windowWidth)) {
                val fullScreenCoverArtTitle = stringResource(R.string.full_screen_cover_art)
                row(fullScreenCoverArtTitle, "artwork", "player") {
                    SettingsRow(
                        icon = Icons.Rounded.Fullscreen,
                        title = fullScreenCoverArtTitle,
                        subtitle = stringResource(R.string.full_screen_cover_art_subtitle),
                        trailing = {
                            com.music.streamx.ui.components.SkinSwitch(
                                checked = fullBleedArtwork,
                                onCheckedChange = AppSettings::setFullBleedArtwork,
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                                    checkedBorderColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                        },
                        onClick = { AppSettings.setFullBleedArtwork(!fullBleedArtwork) },
                    )
                }
            }
            val legacyMeshGradientTitle = stringResource(R.string.legacy_mesh_gradient)
            row(legacyMeshGradientTitle, "background", "player") {
                SettingsRow(
                    icon = Icons.Rounded.Gradient,
                    title = legacyMeshGradientTitle,
                    subtitle = stringResource(R.string.legacy_mesh_gradient_subtitle),
                    trailing = {
                        com.music.streamx.ui.components.SkinSwitch(
                            checked = legacyMeshGradient,
                            onCheckedChange = AppSettings::setLegacyMeshGradient,
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                checkedBorderColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    },
                    onClick = { AppSettings.setLegacyMeshGradient(!legacyMeshGradient) },
                )
            }
            val animatedCoverArtTitle = stringResource(R.string.animated_cover_art)
            // Carries the keywords of the rows that only appear once it is on,
            // so searching "spotify" with animated covers switched off turns up
            // the switch that brings the Spotify row back rather than nothing.
            row(animatedCoverArtTitle, "canvas", "video", "spotify") {
                SettingsRow(
                    icon = Icons.Rounded.Animation,
                    title = animatedCoverArtTitle,
                    subtitle = stringResource(R.string.animated_cover_art_subtitle),
                    trailing = {
                        com.music.streamx.ui.components.SkinSwitch(
                            checked = animatedCanvas,
                            onCheckedChange = AppSettings::setAnimatedCanvas,
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                checkedBorderColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    },
                    onClick = { AppSettings.setAnimatedCanvas(!animatedCanvas) },
                )
            }
            // Reads as part of the Animated cover art option above it, not
            // as a separate setting. Nothing to narrow while the clip itself
            // is off. Defaults to off: a clip loops for as long as its track
            // plays, so on cellular this is not a one-time video cost but
            // that cost repeated on every loop — see AppSettings.canvasOverCellular.
            if (animatedCanvas) {
                val coverCellularTitle = stringResource(R.string.animated_cover_cellular)
                row(coverCellularTitle, "canvas", "cellular", divided = false) {
                    SettingsSubRow(
                        title = coverCellularTitle,
                        checked = canvasOverCellular,
                        onCheckedChange = AppSettings::setCanvasOverCellular,
                    )
                }
                val spotifyCanvasTitle = stringResource(R.string.integrate_spotify_canvas)
                row(spotifyCanvasTitle, "spotify", "canvas", divided = false) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onSpotifyCanvasAuth)
                            .padding(start = ROW_INSET, end = ROW_INSET, top = 4.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = spotifyCanvasTitle,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Chevron()
                    }
                }
            }
            val artistSignaturesTitle = stringResource(R.string.artist_signatures)
            row(artistSignaturesTitle, "signature", "artist", "logo") {
                SettingsRow(
                    icon = Icons.Rounded.Draw,
                    title = artistSignaturesTitle,
                    subtitle = stringResource(R.string.artist_signatures_subtitle),
                    trailing = {
                        com.music.streamx.ui.components.SkinSwitch(
                            checked = signatureApiEnabled,
                            onCheckedChange = AppSettings::setSignatureApiEnabled,
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                checkedBorderColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    },
                    onClick = { AppSettings.setSignatureApiEnabled(!signatureApiEnabled) },
                )
            }
            if (signatureApiEnabled) {
                val signatureUrlTitle = stringResource(R.string.artist_signatures_url)
                row(signatureUrlTitle, "signature", "api", "url", divided = false) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onEditSignatureApi)
                            .padding(start = ROW_INSET, end = ROW_INSET, top = 4.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = signatureApiUrl.ifBlank {
                                stringResource(R.string.artist_signatures_unconfigured)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Chevron()
                    }
                }
            }
            val syncedLyricsTitle = stringResource(R.string.synced_lyrics)
            // Same reasoning as Animated cover art above: the lyrics source and
            // translation rows are only here while this is on.
            row(syncedLyricsTitle, "lyrics", "translation", "lrclib", "musixmatch") {
                SettingsRow(
                    icon = Icons.AutoMirrored.Rounded.Notes,
                    title = syncedLyricsTitle,
                    subtitle = stringResource(R.string.synced_lyrics_subtitle),
                    trailing = {
                        com.music.streamx.ui.components.SkinSwitch(
                            checked = syncedLyrics,
                            onCheckedChange = AppSettings::setSyncedLyrics,
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                checkedBorderColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    },
                    onClick = { AppSettings.setSyncedLyrics(!syncedLyrics) },
                )
            }
            // Nothing to choose between while the feature is off, and the
            // sources are third-party services being reached on the user's
            // connection — which is the part worth being able to narrow.
            if (syncedLyrics) {
                val lyricsBlurTitle = stringResource(R.string.blur_unfocused_lyrics)
                row(lyricsBlurTitle, "lyrics", "blur") {
                    SettingsRow(
                        icon = Icons.Rounded.BlurOn,
                        title = lyricsBlurTitle,
                        subtitle = stringResource(R.string.blur_unfocused_lyrics_subtitle),
                        trailing = {
                            com.music.streamx.ui.components.SkinSwitch(
                                checked = lyricsBlur,
                                onCheckedChange = AppSettings::setLyricsBlur,
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                                    checkedBorderColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                        },
                        onClick = { AppSettings.setLyricsBlur(!lyricsBlur) },
                    )
                }
                val lyricsSourcesTitle = stringResource(R.string.lyrics_sources)
                row(lyricsSourcesTitle, "lyrics", "lrclib", "musixmatch") {
                    SettingsRow(
                        icon = Icons.Rounded.Language,
                        title = lyricsSourcesTitle,
                        subtitle = lyricsSources
                            .sortedBy { it.ordinal }
                            .joinToString(", ") { it.label }
                            .ifEmpty { stringResource(R.string.no_lyrics_sources_enabled) },
                        trailing = { Chevron() },
                        onClick = onLyricsSources,
                    )
                }
                val translationLanguageTitle = stringResource(R.string.translation_language)
                row(translationLanguageTitle, "lyrics", "translate") {
                    SettingsRow(
                        icon = Icons.Rounded.Translate,
                        title = translationLanguageTitle,
                        subtitle = if (translationLanguage.isBlank()) {
                            stringResource(R.string.translation_language_subtitle)
                        } else {
                            translationLanguageName(
                                translationLanguage,
                                AppCompatDelegate.getApplicationLocales().get(0) ?: Locale.getDefault(),
                            )
                        },
                        trailing = { Chevron() },
                        onClick = onTranslationLanguage,
                    )
                }
            }
        }

        SearchableSettingsGroup(search, header = stringResource(R.string.performance), key = "performance") {
            val highPerformanceModeTitle = stringResource(R.string.high_performance_mode)
            row(highPerformanceModeTitle, "frame rate", "refresh rate", "hz", "battery", "smooth") {
                SettingsRow(
                    icon = StreamXIcons.Performance,
                    title = highPerformanceModeTitle,
                    subtitle = if (highPerformanceMode) {
                        stringResource(R.string.high_performance_active, selectedPerformanceRefreshRate)
                    } else {
                        stringResource(R.string.high_performance_subtitle)
                    },
                    trailing = {
                        com.music.streamx.ui.components.SkinSwitch(
                            checked = highPerformanceMode,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    showPerformanceWarning = true
                                } else {
                                    AppSettings.setHighPerformanceMode(false)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                checkedBorderColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    },
                    onClick = {
                        if (highPerformanceMode) {
                            AppSettings.setHighPerformanceMode(false)
                        } else {
                            showPerformanceWarning = true
                        }
                    },
                )
            }
            if (highPerformanceMode) {
                val refreshRateTitle = stringResource(R.string.refresh_rate)
                row(refreshRateTitle, "hz", "frame rate") {
                    SettingsRow(
                        icon = StreamXIcons.FrameRate,
                        title = refreshRateTitle,
                    )
                    SegmentedControl(
                        options = supportedRefreshRates.map { "$it Hz" },
                        selectedIndex = supportedRefreshRates.indexOf(selectedPerformanceRefreshRate),
                        onSelect = { index ->
                            AppSettings.setPerformanceRefreshRate(supportedRefreshRates[index])
                        },
                        modifier = Modifier.padding(
                            start = TEXT_INSET,
                            end = ROW_INSET,
                            bottom = 14.dp,
                        ),
                    )
                }
            }
        }

        SearchableSettingsGroup(search, header = stringResource(R.string.library), key = "library") {
            val syncTitle = stringResource(R.string.playlist_sync_title)
            row(syncTitle, "youtube", "playlist", "sync", "migration", "transfer", "spotify", "apple", "plugin") {
                SettingsRow(
                    icon = Icons.Rounded.PlaylistPlay,
                    title = syncTitle,
                    subtitle = stringResource(R.string.playlist_sync_description),
                    onClick = onPlaylistSync,
                )
            }
        }

        SearchableSettingsGroup(search, header = stringResource(R.string.local_music), key = "local_music") {
            val localMusicFolderTitle = stringResource(R.string.local_music_folder)
            row(localMusicFolderTitle, "folder", "offline", "files") {
                SettingsRow(
                    icon = Icons.Rounded.Folder,
                    title = localMusicFolderTitle,
                    subtitle = LocalMediaRepository.selectedFolderLabel(localMusicFolderUri)
                        ?: stringResource(R.string.all_audio_folders),
                    onClick = { localMusicFolderPicker.launch(null) },
                )
            }
            if (localMusicFolderUri.isNotBlank()) {
                val useAllAudioFoldersTitle = stringResource(R.string.use_all_audio_folders)
                row(useAllAudioFoldersTitle, "folder") {
                    SettingsRow(
                        icon = Icons.Rounded.LibraryMusic,
                        title = useAllAudioFoldersTitle,
                        subtitle = stringResource(R.string.use_all_audio_folders_subtitle),
                        onClick = { AppSettings.setLocalMusicFolderUri("") },
                    )
                }
            }
            val filterNonMusicAudioTitle = stringResource(R.string.filter_non_music_audio)
            row(filterNonMusicAudioTitle, "podcast", "recording") {
                SettingsRow(
                    icon = Icons.Rounded.FilterAlt,
                    title = filterNonMusicAudioTitle,
                    subtitle = stringResource(R.string.filter_non_music_audio_subtitle),
                    trailing = {
                        com.music.streamx.ui.components.SkinSwitch(
                            checked = filterNonMusicAudio,
                            onCheckedChange = AppSettings::setFilterNonMusicAudio,
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                checkedBorderColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    },
                    onClick = { AppSettings.setFilterNonMusicAudio(!filterNonMusicAudio) },
                )
            }
        }

        val cacheUnlimited = cacheLimitBytes == AppSettings.UNLIMITED_CACHE_LIMIT_BYTES
        // The slider's last stop, one step past the largest fixed size, is
        // "Unlimited" — see [AppSettings.setAudioCacheLimitBytes].
        val cacheLimitMb = if (cacheUnlimited) CACHE_UNLIMITED_STOP_MB else (cacheLimitBytes / (1024 * 1024)).toInt()
        val unlimitedLabel = stringResource(R.string.song_cache_unlimited)
        val cacheLimitLabel = if (cacheUnlimited) unlimitedLabel else formatCacheSize(cacheLimitMb)
        SearchableSettingsGroup(search, header = stringResource(R.string.storage), key = "storage") {
            val showCacheFolderTitle = stringResource(R.string.show_cache_folder)
            row(showCacheFolderTitle, "cache", "cached songs", "library", "folder") {
                SettingsRow(
                    icon = Icons.Rounded.Cached,
                    title = showCacheFolderTitle,
                    subtitle = stringResource(R.string.show_cache_folder_subtitle),
                    trailing = {
                        com.music.streamx.ui.components.SkinSwitch(
                            checked = showCacheFolder,
                            onCheckedChange = AppSettings::setShowCacheFolder,
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                checkedBorderColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    },
                    onClick = { AppSettings.setShowCacheFolder(!showCacheFolder) },
                )
            }
            val songCacheLimitTitle = stringResource(R.string.song_cache_limit)
            row(songCacheLimitTitle, "cache", "space", "unlimited") {
                SliderRow(
                    icon = Icons.Rounded.Storage,
                    title = songCacheLimitTitle,
                    subtitle = when {
                        cacheUnlimited -> stringResource(R.string.song_cache_unlimited_subtitle)
                        cacheLimitMb > CACHE_WARNING_MB ->
                            stringResource(R.string.song_cache_large_subtitle, cacheLimitLabel)
                        else -> stringResource(R.string.song_cache_limit_subtitle)
                    },
                    value = cacheLimitLabel,
                    sliderValue = cacheLimitMb.toFloat(),
                    onSliderValue = {
                        AppSettings.setAudioCacheLimitBytes(it.roundToInt().toLong() * 1024 * 1024)
                    },
                    valueRange = (AppSettings.DEFAULT_CACHE_LIMIT_BYTES / (1024 * 1024)).toFloat()..
                        CACHE_UNLIMITED_STOP_MB.toFloat(),
                    steps = 19,
                )
            }
            val clearSongCacheTitle = stringResource(R.string.clear_song_cache)
            row(clearSongCacheTitle, "cache", "free space") {
                SettingsRow(
                    icon = Icons.Rounded.DeleteSweep,
                    title = clearSongCacheTitle,
                    subtitle = stringResource(R.string.clear_song_cache_subtitle),
                    onClick = { confirmClearSongCache = true },
                )
            }
            val clearImageCacheTitle = stringResource(R.string.clear_image_cache)
            row(clearImageCacheTitle, "cache", "artwork", "free space") {
                SettingsRow(
                    icon = Icons.Rounded.DeleteSweep,
                    title = clearImageCacheTitle,
                    subtitle = stringResource(R.string.clear_image_cache_subtitle),
                    onClick = {
                        val loader = SingletonImageLoader.get(context)
                        loader.memoryCache?.clear()
                        loader.diskCache?.clear()
                        Toast.makeText(context, context.getString(R.string.image_cache_cleared), Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }

        // Only in a build that carries the watch app — see app/build.gradle.kts.
        if (remember { WatchInstaller.available(context) }) {
            SearchableSettingsGroup(search) {
                row(stringResource(R.string.wear_os_section), "watch", "wear os", "galaxy watch", "pixel watch", "install") {
                    WearOsSettingsRow(onClick = onWearOs)
                }
            }
        }

        SearchableSettingsGroup(search, header = stringResource(R.string.your_data), key = "your_data") {
            val replayTitle = stringResource(R.string.replay)
            row(replayTitle, "stats", "history", "wrapped") {
                SettingsRow(
                    icon = Icons.Rounded.BarChart,
                    title = replayTitle,
                    subtitle = stringResource(R.string.replay_subtitle),
                    onClick = onOpenReplay,
                )
            }
            val workOutGenresTitle = stringResource(R.string.work_out_genres)
            row(workOutGenresTitle, "genre", "replay") {
                SettingsRow(
                    icon = Icons.Rounded.LocalOffer,
                    title = workOutGenresTitle,
                    subtitle = if (replayGenres) {
                        stringResource(R.string.replay_genres_enabled_subtitle)
                    } else {
                        stringResource(R.string.replay_genres_disabled_subtitle)
                    },
                    trailing = {
                        com.music.streamx.ui.components.SkinSwitch(
                            checked = replayGenres,
                            onCheckedChange = AppSettings::setReplayGenres,
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                checkedBorderColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    },
                    onClick = { AppSettings.setReplayGenres(!replayGenres) },
                )
            }
            val exportDataTitle = stringResource(R.string.export_data)
            row(exportDataTitle, "backup") {
                SettingsRow(
                    icon = Icons.Rounded.FileUpload,
                    title = exportDataTitle,
                    subtitle = exportStatus ?: stringResource(R.string.export_data_subtitle),
                    onClick = { exportPicker.launch(Backup.suggestedName()) },
                )
            }
            val importDataTitle = stringResource(R.string.import_data)
            row(importDataTitle, "backup", "restore") {
                SettingsRow(
                    icon = Icons.Rounded.FileDownload,
                    title = importDataTitle,
                    subtitle = importStatus ?: stringResource(R.string.import_data_subtitle),
                    onClick = { confirmImport = true },
                )
            }
        }

        // No footer: it only restated the stop-on-close row's own subtitle,
        // which sits four rows above it and says the same thing in fewer words.
        SearchableSettingsGroup(search, header = stringResource(R.string.miscellaneous), key = "miscellaneous") {
            val playNextOnSwipeTitle = stringResource(R.string.play_next_on_swipe)
            row(playNextOnSwipeTitle, "swipe", "queue") {
                SettingsRow(
                    icon = Icons.Rounded.PlaylistPlay,
                    title = playNextOnSwipeTitle,
                    subtitle = if (swipeToPlayNext) {
                        stringResource(R.string.swipe_plays_next)
                    } else {
                        stringResource(R.string.swipe_adds_to_queue)
                    },
                    trailing = {
                        com.music.streamx.ui.components.SkinSwitch(
                            checked = swipeToPlayNext,
                            onCheckedChange = AppSettings::setSwipeToPlayNext,
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                checkedBorderColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    },
                    onClick = { AppSettings.setSwipeToPlayNext(!swipeToPlayNext) },
                )
            }
            val dontRepeatSongsTitle = stringResource(R.string.dont_repeat_songs)
            row(dontRepeatSongsTitle, "radio", "suggestions") {
                SettingsRow(
                    icon = Icons.Rounded.History,
                    title = dontRepeatSongsTitle,
                    subtitle = stringResource(R.string.dont_repeat_songs_subtitle),
                    trailing = {
                        com.music.streamx.ui.components.SkinSwitch(
                            checked = dontRepeatSuggestions,
                            onCheckedChange = AppSettings::setDontRepeatSuggestions,
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                checkedBorderColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    },
                    onClick = { AppSettings.setDontRepeatSuggestions(!dontRepeatSuggestions) },
                )
            }
            val stopMusicOnCloseTitle = stringResource(R.string.stop_music_on_close)
            row(stopMusicOnCloseTitle, "notification", "background") {
                SettingsRow(
                    icon = Icons.Rounded.MusicOff,
                    title = stopMusicOnCloseTitle,
                    subtitle = stringResource(R.string.stop_music_on_close_subtitle),
                    trailing = {
                        com.music.streamx.ui.components.SkinSwitch(
                            checked = stopOnTaskRemoved,
                            onCheckedChange = AppSettings::setStopOnTaskRemoved,
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                checkedBorderColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    },
                    onClick = { AppSettings.setStopOnTaskRemoved(!stopOnTaskRemoved) },
                )
            }
            val hideVolumeBarTitle = stringResource(R.string.hide_volume_bar)
            row(hideVolumeBarTitle, "volume", "player") {
                SettingsRow(
                    icon = Icons.Rounded.VolumeOff,
                    title = hideVolumeBarTitle,
                    subtitle = stringResource(R.string.hide_volume_bar_subtitle),
                    trailing = {
                        com.music.streamx.ui.components.SkinSwitch(
                            checked = hideVolumeBar,
                            onCheckedChange = AppSettings::setHideVolumeBar,
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                checkedBorderColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    },
                    onClick = { AppSettings.setHideVolumeBar(!hideVolumeBar) },
                )
            }
            val hideStatusBarTitle = stringResource(R.string.hide_status_bar_in_player)
            row(hideStatusBarTitle, "status bar", "immersive", "fullscreen", "player") {
                SettingsRow(
                    icon = Icons.Rounded.WebAssetOff,
                    title = hideStatusBarTitle,
                    subtitle = stringResource(R.string.hide_status_bar_in_player_subtitle),
                    trailing = {
                        com.music.streamx.ui.components.SkinSwitch(
                            checked = hideStatusBarInPlayer,
                            onCheckedChange = AppSettings::setHideStatusBarInPlayer,
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                checkedBorderColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    },
                    onClick = { AppSettings.setHideStatusBarInPlayer(!hideStatusBarInPlayer) },
                )
            }
            val showQueueHistoryTitle = stringResource(R.string.show_queue_history)
            row(showQueueHistoryTitle, "player", "queue", "history", "previously played") {
                SettingsRow(
                    icon = Icons.Rounded.History,
                    title = showQueueHistoryTitle,
                    subtitle = stringResource(R.string.show_queue_history_subtitle),
                    trailing = {
                        com.music.streamx.ui.components.SkinSwitch(
                            checked = showQueueHistory,
                            onCheckedChange = AppSettings::setShowQueueHistory,
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                checkedBorderColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    },
                    onClick = { AppSettings.setShowQueueHistory(!showQueueHistory) },
                )
            }
            val hideSongStatusTitle = stringResource(R.string.hide_song_status)
            row(hideSongStatusTitle, "player", "playing from", "played by") {
                SettingsRow(
                    icon = Icons.Rounded.VisibilityOff,
                    title = hideSongStatusTitle,
                    subtitle = stringResource(R.string.hide_song_status_subtitle),
                    trailing = {
                        com.music.streamx.ui.components.SkinSwitch(
                            checked = hideSongStatus,
                            onCheckedChange = AppSettings::setHideSongStatus,
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                checkedBorderColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    },
                    onClick = { AppSettings.setHideSongStatus(!hideSongStatus) },
                )
            }
        }

        SearchableSettingsGroup(search, header = stringResource(R.string.language), key = "language") {
            val appLanguageTitle = stringResource(R.string.app_language)
            row(appLanguageTitle, "locale", "translate") {
                val selectedLanguage = AppCompatDelegate.getApplicationLocales().get(0)?.toLanguageTag()
                    ?: Locale.getDefault().toLanguageTag()
                SettingsRow(
                    icon = Icons.Rounded.Language,
                    title = appLanguageTitle,
                    subtitle = stringResource(languageDisplayNameRes(selectedLanguage)),
                    onClick = onAppLanguage,
                )
            }
        }

        SearchableSettingsGroup(search, header = stringResource(R.string.advanced_options), key = "advanced_options") {
            val smartAlignmentTitle = stringResource(R.string.smart_version_alignment)
            row(smartAlignmentTitle, "alignment", "sync", "waveform", "video", "audio", "skit", "intro") {
                SettingsRow(
                    icon = Icons.Rounded.GraphicEq,
                    title = smartAlignmentTitle,
                    subtitle = stringResource(R.string.smart_version_alignment_subtitle),
                    trailing = {
                        com.music.streamx.ui.components.SkinSwitch(
                            checked = smartVersionAlignment,
                            onCheckedChange = AppSettings::setSmartVersionAlignment,
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                checkedBorderColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    },
                    onClick = { AppSettings.setSmartVersionAlignment(!smartVersionAlignment) },
                )
            }
            val showNerdStatsTitle = stringResource(R.string.show_nerd_stats)
            row(showNerdStatsTitle, "debug", "bitrate", "codec") {
                SettingsRow(
                    icon = Icons.Rounded.BarChart,
                    title = showNerdStatsTitle,
                    subtitle = stringResource(R.string.show_nerd_stats_subtitle),
                    trailing = {
                        com.music.streamx.ui.components.SkinSwitch(
                            checked = nerdStats,
                            onCheckedChange = AppSettings::setShowNerdStats,
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                checkedBorderColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    },
                    onClick = { AppSettings.setShowNerdStats(!nerdStats) },
                )
            }
        }

        // Read after every group above has had its turn at the query, which is
        // what makes this an accurate "nothing here" rather than a guess.
        if (!search.anyMatch) {
            MessageState(stringResource(R.string.settings_search_empty, searchQuery))
        }

        // The version line is the page's colophon, not a setting: it belongs to
        // the whole list, so it goes when the list is narrowed to a few rows.
        if (searchQuery.isBlank() && openPage == null) {
        Text(
            text = buildAnnotatedString {
                append("StreamX $version  ")
                val linkStyles = TextLinkStyles(
                    style = SpanStyle(
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                    ),
                )
                withLink(LinkAnnotation.Url("https://github.com/StreamXProject/StreamX", linkStyles)) {
                    append("GitHub")
                }
                append("  ")
                withLink(LinkAnnotation.Url("https://github.com/SriKousik", linkStyles)) {
                    append("Developer")
                }
                append("  ")
                withLink(LinkAnnotation.Url("https://discord.gg/pDdKfrdHY6", linkStyles)) {
                    append("Discord")
                }
                append("  ")
                withLink(LinkAnnotation.Url("https://streamxproject.github.io/StreamX/", linkStyles)) {
                    append("Website")
                }
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 8.dp),
        )
        }
    }

    val optionSheets = com.music.streamx.ui.skins.LocalSkinRenderer.current as? com.music.streamx.ui.skins.SkinOptionSheet
    picking?.let { target ->
        if (optionSheets != null) {
            val qualities = AudioQuality.entries.reversed()
            optionSheets.OptionSheet(
                title = stringResource(R.string.audio_quality),
                subtitle = stringResource(R.string.audio_quality_connection, target.localizedTitle().lowercase(Locale.getDefault())),
                options = qualities.map { com.music.streamx.ui.skins.SkinOption(it.localizedLabel(), stringResource(R.string.quality_hourly, it.detail, it.hourly)) },
                selectedIndex = qualities.indexOf(when (target) { QualityTarget.WIFI -> wifiQuality; QualityTarget.CELLULAR -> cellularQuality }),
                onSelect = { index ->
                    when (target) {
                        QualityTarget.WIFI -> AppSettings.setAudioQualityWifi(qualities[index])
                        QualityTarget.CELLULAR -> AppSettings.setAudioQualityCellular(qualities[index])
                    }
                    picking = null
                },
                onDismissRequest = { picking = null },
            )
        } else ModalBottomSheet(
            onDismissRequest = { picking = null },
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            QualitySheet(
                target = target,
                selected = when (target) {
                    QualityTarget.WIFI -> wifiQuality
                    QualityTarget.CELLULAR -> cellularQuality
                },
                // Writes the one ceiling that was being edited and nothing
                // else. There used to be a `SourceRegistry.applyQualityPreset`
                // call here that flipped the module and JioSaavn switches to
                // match — which meant budgeting *mobile data* switched those
                // sources off while sitting on Wi-Fi, and coming back to Wi-Fi
                // never switched them on again. Which sources a rung consults
                // is now read per stream off the connection in force; see
                // [AudioQuality.permits].
                onSelect = { quality ->
                    when (target) {
                        QualityTarget.WIFI -> AppSettings.setAudioQualityWifi(quality)
                        QualityTarget.CELLULAR -> AppSettings.setAudioQualityCellular(quality)
                    }
                    picking = null
                },
            )
        }
    }

    if (pickingDownloadQuality) {
        if (optionSheets != null) {
            val qualities = DownloadQuality.entries.reversed()
            optionSheets.OptionSheet(
                title = stringResource(R.string.download_quality),
                subtitle = stringResource(R.string.download_quality_dialog_subtitle),
                options = qualities.map { com.music.streamx.ui.skins.SkinOption(it.localizedLabel(), stringResource(R.string.quality_per_track, it.detail, it.perTrack)) },
                selectedIndex = qualities.indexOf(downloadQuality),
                onSelect = { index -> AppSettings.setDownloadQuality(qualities[index]); pickingDownloadQuality = false },
                onDismissRequest = { pickingDownloadQuality = false },
            )
        } else ModalBottomSheet(
            onDismissRequest = { pickingDownloadQuality = false },
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            DownloadQualitySheet(
                selected = downloadQuality,
                onSelect = { quality ->
                    AppSettings.setDownloadQuality(quality)
                    pickingDownloadQuality = false
                },
            )
        }
    }

    if (pickingAutomixPerformance) {
        if (optionSheets != null) {
            val modes = AutomixPerformanceMode.entries
            optionSheets.OptionSheet(
                title = stringResource(R.string.automix_performance),
                subtitle = stringResource(R.string.automix_performance_warning),
                options = modes.map { mode -> com.music.streamx.ui.skins.SkinOption(mode.localizedLabel(), stringResource(when (mode) {
                    AutomixPerformanceMode.EFFICIENT -> R.string.automix_mode_efficient_subtitle
                    AutomixPerformanceMode.BALANCED -> R.string.automix_mode_balanced_subtitle
                    AutomixPerformanceMode.PERFORMANCE -> R.string.automix_mode_performance_subtitle
                })) },
                selectedIndex = modes.indexOf(automixPerformance),
                onSelect = { index -> AppSettings.setAutomixPerformanceMode(modes[index]); pickingAutomixPerformance = false },
                onDismissRequest = { pickingAutomixPerformance = false },
            )
        } else ModalBottomSheet(
            onDismissRequest = { pickingAutomixPerformance = false },
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            AutomixPerformanceSheet(
                selected = automixPerformance,
                onSelect = { mode ->
                    AppSettings.setAutomixPerformanceMode(mode)
                    pickingAutomixPerformance = false
                },
            )
        }
    }

    // The engine is a download of its own, so turning Automix on asks first.
    if (confirmAutomixDownload) {
        val bytes = when (val engine = automixEngine) {
            is AutomixState.Missing -> engine.bytes
            is AutomixState.Failed -> engine.bytes
            else -> null
        }
        AlertDialog(
            onDismissRequest = { confirmAutomixDownload = false },
            title = { Text(stringResource(R.string.automix_download_title)) },
            text = {
                Text(
                    if (bytes != null) stringResource(R.string.automix_download_message_size,
                        android.text.format.Formatter.formatShortFileSize(context, bytes))
                    else stringResource(R.string.automix_download_message),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmAutomixDownload = false
                    AppSettings.setSmartFadeEnabled(true)
                    com.music.streamx.playback.smart.AutomixEngine.download()
                }) {
                    Text(
                        if (bytes != null) stringResource(R.string.automix_download_confirm_size,
                            android.text.format.Formatter.formatShortFileSize(context, bytes))
                        else stringResource(R.string.automix_engine_download),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmAutomixDownload = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // Everything in the Cached songs folder goes with it, so say so first.
    if (confirmClearSongCache) {
        AlertDialog(
            onDismissRequest = { confirmClearSongCache = false },
            title = { Text(stringResource(R.string.clear_song_cache_title)) },
            text = { Text(stringResource(R.string.clear_song_cache_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearSongCache = false
                    AudioCache.clear {
                        Toast.makeText(context, context.getString(R.string.song_cache_cleared), Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text(stringResource(R.string.clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearSongCache = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // Asked before the picker opens rather than after a file is chosen: the
    // thing being confirmed is that this device's own history is about to be
    // thrown away, and that is true whichever file gets picked.
    if (confirmImport) {
        AlertDialog(
            onDismissRequest = { confirmImport = false },
            title = { Text(stringResource(R.string.import_backup_title)) },
            text = {
                Text(stringResource(R.string.import_backup_warning))
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmImport = false
                    importPicker.launch(arrayOf("application/json", "text/plain", "*/*"))
                }) {
                    Text(stringResource(R.string.choose_file))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmImport = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showPerformanceWarning) {
        AlertDialog(
            onDismissRequest = { showPerformanceWarning = false },
            title = { Text(stringResource(R.string.high_performance_before_enabling)) },
            text = { Text(stringResource(R.string.high_performance_battery_warning)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPerformanceWarning = false
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        val settingsIntent = intent.takeIf {
                            it.resolveActivity(context.packageManager) != null
                        } ?: Intent(Settings.ACTION_SETTINGS)
                        batterySettingsLauncher.launch(settingsIntent)
                    },
                ) {
                    Text(stringResource(R.string.open_battery_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPerformanceWarning = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showPerformanceConfirmation) {
        AlertDialog(
            onDismissRequest = { showPerformanceConfirmation = false },
            title = { Text(stringResource(R.string.enable_high_performance_title)) },
            text = { Text(stringResource(R.string.enable_high_performance_confirmation)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPerformanceConfirmation = false
                        AppSettings.setHighPerformanceMode(true)
                    },
                ) {
                    Text(stringResource(R.string.enable))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPerformanceConfirmation = false }) {
                    Text(stringResource(R.string.not_yet))
                }
            },
        )
    }

    if (showListenBrainzTokenDialog) {
        var tokenInput by remember { mutableStateOf(listenBrainzToken) }
        AlertDialog(
            onDismissRequest = { showListenBrainzTokenDialog = false },
            title = { Text(stringResource(R.string.listenbrainz_token)) },
            text = {
                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it },
                    label = { Text(stringResource(R.string.api_token)) }, labelText = stringResource(R.string.api_token),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    AppSettings.setListenBrainzToken(tokenInput.trim())
                    showListenBrainzTokenDialog = false
                }) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showListenBrainzTokenDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showLastfmLoginDialog) {
        var usernameInput by remember { mutableStateOf("") }
        var passwordInput by remember { mutableStateOf("") }
        var lastfmError by remember { mutableStateOf<String?>(null) }
        var lastfmLoading by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!lastfmLoading) showLastfmLoginDialog = false },
            title = { Text(stringResource(R.string.lastfm_login)) },
            text = {
                Column {
                    if (lastfmError != null) {
                        Text(
                            text = lastfmError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    OutlinedTextField(
                        value = usernameInput,
                        onValueChange = { usernameInput = it },
                        label = { Text(stringResource(R.string.username)) }, labelText = stringResource(R.string.username),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text(stringResource(R.string.password)) }, labelText = stringResource(R.string.password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        lastfmLoading = true
                        lastfmError = null
                        scrobbleScope.launch {
                            try {
                                // Use the credentials supplied for this build.
                                LastFM.initialize(
                                    apiKey = AppSettings.lastfmApiKey.value,
                                    secret = AppSettings.lastfmSecret.value,
                                )
                                LastFM.getMobileSession(usernameInput.trim(), passwordInput)
                                    .onSuccess { auth ->
                                        AppSettings.setLastfmSessionKey(auth.session.key)
                                        AppSettings.setLastfmUsername(auth.session.name)
                                        AppSettings.setLastfmEnabled(true)
                                        showLastfmLoginDialog = false
                                    }
                                    .onFailure { e ->
                                        lastfmError = e.message ?: context.getString(R.string.login_failed)
                                    }
                            } catch (e: Exception) {
                                lastfmError = e.message ?: context.getString(R.string.login_failed)
                            } finally {
                                lastfmLoading = false
                            }
                        }
                    },
                    enabled = !lastfmLoading && usernameInput.isNotBlank() && passwordInput.isNotBlank(),
                ) {
                    Text(stringResource(if (lastfmLoading) R.string.signing_in else R.string.sign_in))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLastfmLoginDialog = false }, enabled = !lastfmLoading) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

}

/** "3 months of listening" — the unit a backup is actually measured in. */
private fun Context.countOfMonths(months: Int): String = if (months == 0) {
    getString(R.string.no_listening_history)
} else {
    resources.getQuantityString(R.plurals.listening_month_count, months, months)
}

/** Which ceiling the open picker is editing. */
private enum class QualityTarget(val icon: ImageVector) {
    WIFI(Icons.Rounded.Wifi),
    CELLULAR(Icons.Rounded.SignalCellularAlt),
}

@Composable
private fun QualityTarget.localizedTitle(): String = stringResource(
    if (this == QualityTarget.WIFI) R.string.wifi else R.string.mobile_data,
)

@Composable
private fun AudioQuality.localizedLabel(): String = stringResource(
    when (this) {
        AudioQuality.LOW -> R.string.low
        AudioQuality.MEDIUM -> R.string.medium
        AudioQuality.HIGH -> R.string.high
        AudioQuality.LOSSLESS -> R.string.lossless
    },
)

@Composable
private fun DownloadQuality.localizedLabel(): String = stringResource(
    when (this) {
        DownloadQuality.STANDARD -> R.string.standard
        DownloadQuality.HIGH -> R.string.high
        DownloadQuality.LOSSLESS -> R.string.lossless
    },
)

@Composable
private fun ThemeMode.localizedLabel(): String = stringResource(
    when (this) {
        ThemeMode.SYSTEM -> R.string.system
        ThemeMode.LIGHT -> R.string.light
        ThemeMode.DARK -> R.string.dark
    },
)

@Composable
private fun AutomixPerformanceMode.localizedLabel(): String = stringResource(
    when (this) {
        AutomixPerformanceMode.EFFICIENT -> R.string.automix_mode_efficient
        AutomixPerformanceMode.BALANCED -> R.string.automix_mode_balanced
        AutomixPerformanceMode.PERFORMANCE -> R.string.automix_mode_performance
    },
)

/**
 * Hands the device's own effect panel this app's audio session.
 *
 * Shared with [EqualizerScreen], which offers the same escape hatch a second
 * time next to the equaliser it might be an alternative to.
 */
internal fun openEqualizer(context: Context, sessionId: Int) {
    val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
        putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
        putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
        putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
    }
    runCatching { context.startActivity(intent) }.onFailure {
        Toast.makeText(context, context.getString(R.string.no_equalizer), Toast.LENGTH_SHORT).show()
    }
}

/** Above this, the cache limit slider's subtitle warns rather than reassures. */
private const val CACHE_WARNING_MB = 2048

/**
 * Where the cache limit slider's "Unlimited" stop sits: one 512MB step past
 * [AppSettings.MAX_CACHE_LIMIT_BYTES], so the fixed sizes keep their positions.
 */
private const val CACHE_UNLIMITED_STOP_MB = 10 * 1024 + 512

/** "512 MB", "2 GB", "2.5 GB" — whichever reads more naturally at that size. */
private fun formatCacheSize(mb: Int): String {
    if (mb < 1024) return "$mb MB"
    val gb = mb / 1024f
    return if (gb == gb.toInt().toFloat()) "${gb.toInt()} GB" else "%.1f GB".format(Locale.ROOT, gb)
}

/** Who you're signed in as, straight from YouTube Music's account menu. */
@Composable
internal fun AccountCard(
    signedIn: Boolean,
    account: Account?,
    onSignIn: () -> Unit,
    onClick: (() -> Unit)? = null,
    /** Sits inside a [SettingsGroup] that already supplies the card, so draws none of its own. */
    grouped: Boolean = false,
) {
    val catalogue by com.music.streamx.data.sources.SourceRegistry.catalogue.collectAsStateWithLifecycle()
    val extensionId by com.music.streamx.data.sources.SourceRegistry.selectedExtensionId.collectAsStateWithLifecycle()
    val configs by com.music.streamx.data.sources.SourceRegistry.configs.collectAsStateWithLifecycle()
    // The plugin that leads Home, whether or not it is also in the playback order.
    val extension = configs.firstOrNull { it.id == extensionId && com.music.streamx.data.sources.SourceRegistry.feedEligible(it) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val displayedAccount = account
    val streamX = catalogue == com.music.streamx.data.sources.SourceKind.STREAMX
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (grouped) {
                    Modifier
                } else {
                    Modifier
                        .padding(horizontal = GROUP_INSET)
                        .clip(GroupShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                },
            )
            .then(
                when {
                    extension != null -> Modifier.clickable {
                        com.music.streamx.data.extensions.ApkExtensions.installed.value.firstOrNull { it.packageName == extension.extensionPackage }
                            ?.let { installed -> runCatching { com.music.streamx.data.extensions.ApkExtensions.showSettings(installed) } }
                    }
                    signedIn && onClick != null -> Modifier.clickable(onClick = onClick)
                    !signedIn -> Modifier.clickable(onClick = onSignIn)
                    else -> Modifier
                },
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (displayedAccount?.thumbnailUrl != null) {
            AsyncImage(
                model = displayedAccount.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(52.dp).clip(CircleShape).thumbnailBorder(CircleShape),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outline),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = extension?.displayName ?: displayedAccount?.name
                    ?: stringResource(if (signedIn) R.string.signed_in else R.string.not_signed_in),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (catalogue == com.music.streamx.data.sources.SourceKind.CLOUDSTREAM && extension == null) "CloudStream library" else if (extension != null) "Open extension account" else displayedAccount?.email?.takeIf { it.isNotBlank() }
                    ?: stringResource(
                        if (streamX) { if (signedIn) R.string.streamx_account else R.string.streamx_sign_in }
                        else if (signedIn) R.string.youtube_music_account else R.string.tap_to_sign_in_google,
                    ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!signedIn) {
            Spacer(Modifier.width(8.dp))
            Chevron()
        }
    }
}

/** The quality options for one connection, with what each costs in data. */
@Composable
private fun QualitySheet(
    target: QualityTarget,
    selected: AudioQuality,
    onSelect: (AudioQuality) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Row(
            modifier = Modifier.padding(start = 22.dp, end = 22.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = target.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = stringResource(R.string.audio_quality),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = stringResource(
                        R.string.audio_quality_connection,
                        target.localizedTitle().lowercase(Locale.getDefault()),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)

        // Best first — the option most people want shouldn't be last.
        AudioQuality.entries.reversed().forEach { quality ->
            val chosen = quality == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(quality)
                    }
                    .padding(horizontal = 22.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = quality.localizedLabel(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = stringResource(R.string.quality_hourly, quality.detail, quality.hourly),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (chosen) {
                    Spacer(Modifier.width(12.dp))
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = stringResource(R.string.selected),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

/** CPU budget picker for the background models that prepare Automix. */
@Composable
private fun AutomixPerformanceSheet(
    selected: AutomixPerformanceMode,
    onSelect: (AutomixPerformanceMode) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Row(
            modifier = Modifier.padding(start = 22.dp, end = 22.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = stringResource(R.string.automix_performance),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = stringResource(R.string.automix_performance_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)
        AutomixPerformanceMode.entries.forEach { mode ->
            val chosen = mode == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(mode)
                    }
                    .padding(horizontal = 22.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = mode.localizedLabel(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = stringResource(
                            when (mode) {
                                AutomixPerformanceMode.EFFICIENT -> R.string.automix_mode_efficient_subtitle
                                AutomixPerformanceMode.BALANCED -> R.string.automix_mode_balanced_subtitle
                                AutomixPerformanceMode.PERFORMANCE -> R.string.automix_mode_performance_subtitle
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (chosen) {
                    Spacer(Modifier.width(12.dp))
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = stringResource(R.string.selected),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

/**
 * What to keep when a track is saved, with what each rung costs on disk.
 *
 * Priced per track rather than per hour, the way [QualitySheet] is. That sheet
 * is answering "what will listening cost me this hour", because a stream is
 * spent again on every replay; this one is answering "what will keeping this
 * cost me", and the answer is charged once. Same widget, different question, so
 * the numbers beside the options are in different units on purpose.
 */
@Composable
private fun DownloadQualitySheet(
    selected: DownloadQuality,
    onSelect: (DownloadQuality) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Row(
            modifier = Modifier.padding(start = 22.dp, end = 22.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Download,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = stringResource(R.string.download_quality),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = stringResource(R.string.download_quality_dialog_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)

        // Best first, matching [QualitySheet] — and here the best rung is also
        // the default, so the checkmark starts where the eye does.
        DownloadQuality.entries.reversed().forEach { quality ->
            val chosen = quality == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(quality)
                    }
                    .padding(horizontal = 22.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = quality.localizedLabel(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = stringResource(R.string.quality_per_track, quality.detail, quality.perTrack),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (chosen) {
                    Spacer(Modifier.width(12.dp))
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = stringResource(R.string.selected),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

// ---- Building blocks --------------------------------------------------------

internal val GroupShape = RoundedCornerShape(14.dp)
internal val GROUP_INSET = 16.dp
internal val ROW_INSET = 16.dp
internal val ICON_SIZE = 22.dp
internal val ICON_GAP = 14.dp

/** Where a row's text starts — dividers are inset to match, as on iOS. */
internal val TEXT_INSET = ROW_INSET + ICON_SIZE + ICON_GAP

/**
 * What is typed into the field under the Settings title, and whether anything
 * on the screen has answered to it.
 *
 * A blank query matches everything, so the screen with the field untouched is
 * the screen as it was before there was a field. Rows ask [matches] as they
 * are composed; the empty state at the foot of the list reads [anyMatch] after
 * every group has had its turn, which is why this is made fresh on each pass
 * rather than remembered.
 */
private class SettingsSearch(query: String, val paged: Boolean = false, val page: String? = null) {
    /** Whether the group [key] is on the screen: its own page, or the main list for the groups that stay there. */
    fun shows(key: String?): Boolean = !paged || if (page == null) key == null || key in ROOT_GROUPS else key == page

    // Trimmed, because a trailing space is a typo rather than a search for
    // something ending in one — and on a phone keyboard it is one keystroke
    // away from every word typed.
    private val needle = query.trim()

    var anyMatch = false
        private set

    fun matches(vararg keywords: String?): Boolean {
        val hit = needle.isEmpty() ||
            keywords.any { it?.contains(needle, ignoreCase = true) == true }
        if (hit) anyMatch = true
        return hit
    }
}

/** Collects the rows of one group that survived the search. */
private class SettingsGroupScope(
    private val search: SettingsSearch,
    private val header: String?,
) {
    class Entry(val divided: Boolean, val content: @Composable () -> Unit)

    val entries = mutableListOf<Entry>()

    /**
     * One searchable setting. [keywords] is everything somebody might type
     * looking for it: its title at least, plus whatever its subtitle or the
     * controls under it say that the title doesn't — "Spotify", say, for the
     * canvas link that lives under Animated cover art.
     *
     * [divided] is false for the rows drawn as part of the row above them
     * rather than as settings in their own right, so they keep hugging their
     * parent when both survive the filter.
     */
    fun row(
        vararg keywords: String?,
        divided: Boolean = true,
        content: @Composable () -> Unit,
    ) {
        // The header counts for every row beneath it: searching "playback"
        // should turn up the playback group entire, not nothing at all.
        if (search.matches(header, *keywords)) entries += Entry(divided, content)
    }
}

/**
 * A [SettingsGroup] whose rows are filtered by the search field, and which
 * takes itself off the screen when none of them are left. Dividers fall
 * between the rows that survived rather than around the gaps left by the ones
 * that didn't, so a filtered card is indistinguishable from a hand-written one.
 */
@Composable
private fun SearchableSettingsGroup(
    search: SettingsSearch,
    header: String? = null,
    key: String? = null,
    footer: String? = null,
    topSpacing: Dp = 26.dp,
    content: @Composable SettingsGroupScope.() -> Unit,
) {
    if (!search.shows(key)) return
    val scope = SettingsGroupScope(search, header)
    scope.content()
    if (scope.entries.isEmpty()) return
    // On its own page the section's name is the page title, not a header above it.
    val ownPage = search.paged && search.page != null
    androidx.compose.runtime.CompositionLocalProvider(com.music.streamx.ui.skins.LocalSkinSettingsGroupKey provides key) {
    SettingsGroup(header = header.takeUnless { ownPage }, footer = footer, topSpacing = if (ownPage) 14.dp else topSpacing) {
        scope.entries.forEachIndexed { index, entry ->
            when {
                index > 0 && entry.divided -> RowDivider()
                // An undivided row is drawn tight to the top because something
                // it belongs to is usually above it. Filtered down to itself it
                // has nothing to sit under, so it is given that room back.
                index == 0 && !entry.divided -> Spacer(Modifier.height(10.dp))
            }
            entry.content()
        }
    }
    }
}

/**
 * One inset card of rows, with an uppercase header above and an optional
 * plain-language [footer] below. Rows are separated by [RowDivider].
 */
@Composable
internal fun SettingsGroup(
    header: String? = null,
    footer: String? = null,
    /** Room above the card, where there is no [header] to provide it. */
    topSpacing: Dp = 26.dp,
    content: @Composable () -> Unit,
) {
    val skin=com.music.streamx.ui.skins.LocalSkinRenderer.current as? com.music.streamx.ui.skins.SkinSettingsProvider
    if(skin!=null) {skin.SettingsGroup(header,footer,topSpacing,content);return}
    val flat = com.music.streamx.ui.skins.LocalSkinLayout.current.settings == com.music.streamx.ui.skins.SettingsLayout.FLAT
    if (header != null) {
        Text(
            text = if (flat) header else header.uppercase(Locale.ROOT),
            style = if (flat) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                start = GROUP_INSET + 4.dp,
                end = GROUP_INSET,
                top = 26.dp,
                bottom = 8.dp,
            ),
        )
    } else {
        Spacer(Modifier.height(topSpacing))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = GROUP_INSET)
            .clip(if (flat) RoundedCornerShape(0.dp) else GroupShape)
            .background(if (flat) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surfaceVariant),
    ) {
        content()
    }
    if (footer != null) {
        Text(
            text = footer,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                start = GROUP_INSET + 4.dp,
                end = GROUP_INSET + 4.dp,
                top = 8.dp,
            ),
        )
    }
}

@Composable
internal fun RowDivider() {
    val skin=com.music.streamx.ui.skins.LocalSkinRenderer.current as? com.music.streamx.ui.skins.SkinSettingsProvider
    if(skin!=null) {skin.SettingsDivider();return}
    if (com.music.streamx.ui.skins.LocalSkinLayout.current.settings == com.music.streamx.ui.skins.SettingsLayout.FLAT) return
    HorizontalDivider(
        modifier = Modifier.padding(start = TEXT_INSET),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outline,
    )
}

/**
 * The standard row: glyph, title, optional subtitle, and on the right either
 * [trailing] (a switch, say) or the current [value] followed by a chevron.
 *
 * [iconPainter] is for the handful of rows whose glyph is a drawable rather
 * than a Material icon — the Dolby double-D, which is a mark and not something
 * to approximate with the nearest speaker outline. Exactly one of it and [icon]
 * is expected; the painter wins where both are given.
 */
@Composable
internal fun SettingsRow(
    icon: ImageVector? = null,
    title: String,
    subtitle: String? = null,
    subtitleContent: (@Composable () -> Unit)? = null,
    value: String? = null,
    badge: String? = null,
    enabled: Boolean = true,
    iconPainter: Painter? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val skin=com.music.streamx.ui.skins.LocalSkinRenderer.current as? com.music.streamx.ui.skins.SkinSettingsProvider
    if(skin!=null) {skin.SettingsRow(icon,iconPainter,title,subtitle,subtitleContent,value,badge,enabled,onClick,trailing);return}
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .alpha(if (enabled) 1f else 0.45f)
            .heightIn(min = 52.dp)
            .padding(horizontal = ROW_INSET, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconPainter != null) {
            Icon(
                painter = iconPainter,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(ICON_SIZE),
            )
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(ICON_SIZE),
            )
        }
        Spacer(Modifier.width(ICON_GAP))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (badge != null) {
                    Spacer(Modifier.width(8.dp))
                    Badge(badge)
                }
            }
            if (subtitleContent != null) {
                subtitleContent()
            } else if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 5,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        if (trailing != null) {
            trailing()
        } else if (value != null || onClick != null) {
            if (value != null) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Spacer(Modifier.width(4.dp))
            }
            Chevron()
        }
    }
}

/**
 * A toggle that reads as part of the option above it rather than a setting
 * of its own: no icon, no divider, and pulled up close against its parent
 * instead of getting the same breathing room a full [SettingsRow] gets.
 */
@Composable
internal fun SettingsSubRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    badge: String? = null,
    subtitle: String? = null,
) {
    // A skin drawing Settings rows draws these too; left to the host they sat
    // at its own inset, out of line with every other row in the group.
    (com.music.streamx.ui.skins.LocalSkinRenderer.current as? com.music.streamx.ui.skins.SkinSettingsProvider)?.let { skin ->
        skin.SettingsRow(null, null, title, subtitle, null, null, badge, true, { onCheckedChange(!checked) }) {
            com.music.streamx.ui.components.SkinSwitch(checked = checked, onCheckedChange = onCheckedChange)
        }
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(start = ROW_INSET, end = ROW_INSET, top = 0.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (badge != null) {
                    Spacer(Modifier.width(8.dp))
                    Badge(badge)
                }
            }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 5,
                )
            }
        }
        com.music.streamx.ui.components.SkinSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

/** Marks the connection whose ceiling is actually in force right now. */
@Composable
internal fun Badge(text: String) {
    Text(
        text = text.uppercase(Locale.ROOT),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
internal fun Chevron() {
    Icon(
        Icons.Rounded.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.size(20.dp),
    )
}

/** A continuous setting: label and current value on one line, track beneath. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SliderRow(
    icon: ImageVector,
    title: String,
    value: String,
    sliderValue: Float,
    onSliderValue: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    subtitle: String? = null,
) {
    // A skin drawing Settings rows takes the heading, so the row lines up with
    // its group, and its own slider if it has one, under that heading.
    val rowSkin = com.music.streamx.ui.skins.LocalSkinRenderer.current as? com.music.streamx.ui.skins.SkinSettingsProvider
    if (rowSkin != null) {
        Column {
            rowSkin.SettingsRow(null, null, title, subtitle, null, value, null, true, null, null)
            val skinSlider = com.music.streamx.ui.skins.LocalSkinRenderer.current as? com.music.streamx.ui.skins.SkinSlider
            val inset = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp)
            if (skinSlider != null) skinSlider.Slider(sliderValue, onSliderValue, valueRange, steps, true, inset)
            else Slider(value = sliderValue, onValueChange = onSliderValue, valueRange = valueRange, steps = steps, modifier = inset)
        }
        return
    }
    val colors = SliderDefaults.colors(
        thumbColor = MaterialTheme.colorScheme.primary,
        activeTrackColor = MaterialTheme.colorScheme.primary,
        inactiveTrackColor = MaterialTheme.colorScheme.outline,
    )
    Column(Modifier.padding(start = ROW_INSET, end = ROW_INSET, top = 12.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(ICON_SIZE),
            )
            Spacer(Modifier.width(ICON_GAP))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val skinSlider = com.music.streamx.ui.skins.LocalSkinRenderer.current as? com.music.streamx.ui.skins.SkinSlider
        if (skinSlider != null) skinSlider.Slider(sliderValue, onSliderValue, valueRange, steps, true, Modifier.padding(start = ICON_SIZE + ICON_GAP))
        else Slider(
            value = sliderValue,
            onValueChange = onSliderValue,
            valueRange = valueRange,
            steps = steps,
            colors = colors,
            // Bare track: the step ticks and the end-stop dot are noise when the
            // value is already spelled out on the line above.
            track = { state ->
                SliderDefaults.Track(
                    sliderState = state,
                    colors = colors,
                    drawStopIndicator = null,
                    drawTick = { _, _ -> },
                )
            },
            modifier = Modifier.padding(start = ICON_SIZE + ICON_GAP),
        )
    }
}

/** Sign out: centered, accent-coloured, no glyph — the shape of a real one. */
@Composable
internal fun DestructiveRow(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Sliding pill selector, for the handful of settings with two or three states. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    (com.music.streamx.ui.skins.LocalSkinRenderer.current as? com.music.streamx.ui.skins.SkinSegmentedControl)?.let {
        it.SegmentedControl(options, selectedIndex, onSelect, enabled, modifier)
        return
    }
    if(com.music.streamx.ui.skins.LocalSkinRenderer.current is com.music.streamx.ui.skins.SkinThemeProvider) {
        SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
            options.forEachIndexed { index,label ->
                SegmentedButton(selected=index==selectedIndex,onClick={onSelect(index)},enabled=enabled,
                    shape=SegmentedButtonDefaults.itemShape(index,options.size),
                    colors=SegmentedButtonDefaults.colors(
                        activeContainerColor=MaterialTheme.colorScheme.surfaceContainerHigh,
                        activeContentColor=MaterialTheme.colorScheme.onSurface,
                        inactiveContainerColor=MaterialTheme.colorScheme.surface,
                        inactiveContentColor=MaterialTheme.colorScheme.onSurfaceVariant,
                        activeBorderColor=MaterialTheme.colorScheme.outlineVariant,
                        inactiveBorderColor=MaterialTheme.colorScheme.outlineVariant)) { Text(label,maxLines=1) }
            }
        }
        return
    }
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.outline)
            // Enough of a margin for the track to read as a track. At 2dp the
            // selected pill sat all but flush against the container's own edge,
            // so the two rounded rectangles merged into one shape and the
            // control stopped looking like something with a position in it.
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { index, label ->
            val chosen = index == selectedIndex
            val pill by animateColorAsState(
                targetValue = if (chosen) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.Transparent
                },
                animationSpec = tween(160),
                label = "segmentPill",
            )
            val labelColor by animateColorAsState(
                targetValue = if (chosen) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                animationSpec = tween(160),
                label = "segmentLabel",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(pill)
                    .clickable(enabled = enabled) {
                        if (!chosen) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(index)
                        }
                    }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = labelColor,
                    maxLines = 1,
                )
            }
        }
    }
}


@Composable
private fun SettingsPresentation(modifier: Modifier,padding: PaddingValues,scroll: ScrollState,query: String,onQuery: (String)->Unit,onBack: ()->Unit,
    /** A section's own page is titled with its name and has no search field; null is the main list. */
    pageTitle: String? = null,content: @Composable ()->Unit) {
    val skin=com.music.streamx.ui.skins.LocalSkinRenderer.current as? com.music.streamx.ui.skins.SkinSettingsPageProvider
    if(skin!=null) { androidx.compose.foundation.layout.Box(modifier) {skin.SettingsPage(query,onQuery,padding,onBack,content)};return }
    Column(modifier.fillMaxWidth().verticalScroll(scroll).padding(padding)) {
        Text(pageTitle ?: stringResource(R.string.settings),style=MaterialTheme.typography.displayLarge,color=MaterialTheme.colorScheme.onBackground,modifier=Modifier.padding(start=20.dp,end=20.dp,top=8.dp,bottom=14.dp))
        if(pageTitle==null) SearchField(query=query,onQueryChange=onQuery,onSubmit={},placeholder=stringResource(R.string.settings_search_hint),modifier=Modifier.padding(start=20.dp,end=20.dp,bottom=4.dp))
        content()
    }
}

/** Groups that stay on the main settings list; every other one is a page of its own. */
private val ROOT_GROUPS = setOf("account", "audio_quality")

/** The pages the main list opens, in its order, by group key. */
private val SETTINGS_SECTIONS = listOf(
    "downloads" to Icons.Rounded.Download,
    "playback" to Icons.Rounded.PlayCircle,
    "appearance" to Icons.Rounded.Palette,
    "performance" to Icons.Rounded.Speed,
    "library" to Icons.Rounded.LibraryMusic,
    "local_music" to Icons.Rounded.Folder,
    "storage" to Icons.Rounded.Storage,
    "your_data" to Icons.Rounded.Insights,
    "miscellaneous" to Icons.Rounded.Tune,
    "language" to Icons.Rounded.Language,
    "advanced_options" to Icons.Rounded.Science,
)

@Composable
private fun settingsSectionTitle(key: String): String = stringResource(when (key) {
    "downloads" -> R.string.downloads
    "playback" -> R.string.playback
    "appearance" -> R.string.appearance
    "performance" -> R.string.performance
    "library" -> R.string.library
    "local_music" -> R.string.local_music
    "storage" -> R.string.storage
    "your_data" -> R.string.your_data
    "miscellaneous" -> R.string.miscellaneous
    "language" -> R.string.language
    else -> R.string.advanced_options
})
