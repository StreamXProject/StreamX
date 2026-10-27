package com.music.streamx.ui.screens

import android.os.Bundle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material3.*
import com.music.streamx.ui.components.SkinSwitch as Switch
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import com.music.streamx.ui.components.BottomFadeScrim
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.music.streamx.ui.components.FrostedTopBar
import com.music.streamx.ui.components.topBarContentPadding
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.music.streamx.data.extensions.ApkExtension
import com.music.streamx.data.extensions.ApkExtensions
import com.music.streamx.data.sources.SourceConfig
import com.music.streamx.data.sources.SourceKind
import com.music.streamx.data.sources.SourceRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/** Extension-owned preferences rendered with the same controls as every other settings page. */
@Composable
internal fun ExtensionSettingsDialog(extension: ApkExtension,onChanged: () -> Unit,onDismiss: () -> Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val config=remember(extension) {
        SourceRegistry.configs.value.firstOrNull {it.extensionPackage==extension.packageName}
            ?: SourceConfig(kind=SourceKind.APK_PLUGIN,label=extension.name,extensionPackage=extension.packageName,
                extensionAuthority=extension.sourceAuthority.orEmpty(),extensionCertificate=extension.certificate)
    }
    var preferences by remember(extension.packageName) {mutableStateOf<JSONObject?>(null)}
    var error by remember {mutableStateOf<String?>(null)}
    var busy by remember {mutableStateOf(false)}
    var pendingWrites by remember(extension.packageName) {mutableIntStateOf(0)}
    val preferenceLock=remember(extension.packageName) {Mutex()}
    var revision by remember {mutableIntStateOf(0)}
    var planRevision by remember(extension.packageName) {mutableIntStateOf(0)}
    var plan by remember(extension.packageName) {mutableStateOf<JSONObject?>(null)}
    var planError by remember(extension.packageName) {mutableStateOf<String?>(null)}
    suspend fun request(method: String,args: Bundle=Bundle()): JSONObject {
        val result=JSONObject(ApkExtensions.call(config,method,args))
        check(!result.has("error") || result.isNull("error")){result.optString("error")}
        return result
    }
    suspend fun load() = preferenceLock.withLock {
        if(pendingWrites>0)return@withLock
        busy=true
        try {
            val next=request("preferences");val previous=preferences
            if(pendingWrites==0) {preferences=next;error=null}
            if(previous!=null && (previous.optBoolean("connected")!=next.optBoolean("connected") || previous.optString("name")!=next.optString("name")))onChanged()
        }
        catch(cancelled: CancellationException){throw cancelled}
        catch(failure: Exception){error=failure.message ?: "Could not read extension settings"}
        finally {busy=false}
    }
    fun update(args: Bundle) {
        // Keep settled controls enabled and animate the new value immediately. Serialize
        // writes and resume refreshes so an older response cannot undo a newer toggle.
        pendingWrites++
        val changes=Bundle(args)
        preferences=preferences?.let { previous -> JSONObject(previous.toString()).apply {
            changes.keySet().forEach { key ->
                @Suppress("DEPRECATION")
                put(key,changes.get(key))
            }
        }}
        scope.launch {
            preferenceLock.withLock {
                try {
                    val saved=request("set_preferences",changes)
                    if(pendingWrites==1)preferences=saved
                    error=null
                }
                catch(cancelled: CancellationException){throw cancelled}
                catch(failure: Exception){
                    error=failure.message ?: "Could not update extension settings"
                    if(pendingWrites==1)try {preferences=request("preferences")}
                    catch(cancelled: CancellationException){throw cancelled}
                    catch(_: Exception){ /* Keep the visible value and the actionable error. */ }
                } finally {pendingWrites--}
            }
        }
    }
    val owner=LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_RESUME)revision++}
        owner.lifecycle.addObserver(observer)
        onDispose {owner.lifecycle.removeObserver(observer)}
    }
    LaunchedEffect(extension.packageName,revision){load()}
    val connected = preferences?.optBoolean("connected") == true
    val planSupported=preferences?.optBoolean("plan_supported",true)==true
    LaunchedEffect(extension.packageName, connected, planSupported, revision, planRevision) {
        plan = null
        planError = null
        if (connected && planSupported) {
            try { plan = request("plan", Bundle().apply {putBoolean("refresh",planRevision>0)}) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { planError = "Spotify could not verify your plan. Tap Refresh plan to retry." }
        }
    }
    fun openAccount() {
        try {context.startActivity(ApkExtensions.configurationIntent(extension).putExtra("streamx.open_login",true))}
        catch(failure: Exception){error=failure.message ?: "Could not open extension account"}
    }
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        com.music.streamx.ui.theme.SkinSectionTheme(com.music.streamx.data.skins.SkinSection.SETTINGS) {
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
            val switchColors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = MaterialTheme.colorScheme.primary,
            )
            val scroll = rememberScrollState()
            Column(Modifier.fillMaxSize().verticalScroll(scroll).navigationBarsPadding()) {
                Spacer(Modifier.height(topBarContentPadding()))
                Column(Modifier.fillMaxWidth().padding(start=20.dp,end=20.dp,top=8.dp,bottom=14.dp),
                    verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(extension.name,style=MaterialTheme.typography.displayLarge)
                    Text("Music service",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val state=preferences
                SettingsGroup(header="Account") {
                    SettingsRow(icon=Icons.Rounded.AccountCircle,title=if(state?.optBoolean("connected")==true)"Listen as" else "Account",
                        subtitle=state?.optString("name")?.takeIf {it.isNotBlank()},onClick=::openAccount)

                    if(state?.optBoolean("connected")==true) {
                        RowDivider()
                        SettingsRow(title="Sign out",enabled=!busy && pendingWrites==0,onClick={
                            busy=true
                            scope.launch {
                                try {request("sign_out");load()}
                                catch(cancelled: CancellationException){throw cancelled}
                                catch(failure: Exception){error=failure.message ?: "Sign out failed"}
                                finally {busy=false}
                            }
                        })
                    }
                }
                if(planSupported) SettingsGroup(header="Plan Tier") {
                    val tier = plan?.optString("tier").orEmpty()
                    val label = when {
                        !connected -> "Not connected"
                        planError != null -> "Unverified"
                        plan == null -> "Checking"
                        tier == "free" -> "Free"
                        tier == "premium" -> "Premium"
                        tier == "platinum" -> "Platinum"
                        else -> "Unknown"
                    }
                    val description = when {
                        !connected -> "Sign in to verify your Spotify plan."
                        planError != null -> planError
                        plan == null -> "Checking with Spotify…"
                        tier == "premium" -> "Spotify reports Premium. The exact subscription tier is not provided."
                        tier == "platinum" -> "Platinum verified. Native playback currently supports up to 320 kbps; FLAC is not supported."
                        tier == "free" -> "Spotify Free uses YouTube audio. Spotify playback requires Premium."
                        else -> "Spotify did not identify this subscription tier."
                    }
                    SettingsRow(title="Current plan",subtitle=description,trailing={
                        Surface(shape=MaterialTheme.shapes.small,
                            color=if(tier in setOf("free","premium","platinum")) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
                            Text(label,style=MaterialTheme.typography.labelLarge,
                                modifier=Modifier.padding(horizontal=12.dp,vertical=6.dp))
                        }
                    })
                    if (connected) {
                        RowDivider()
                        SettingsRow(title="Refresh plan",enabled=plan!=null || planError!=null,onClick={planRevision++})
                    }
                }
                if(state?.optBoolean("native_available")==true && plan?.optString("tier") in setOf("premium", "platinum")) {
                    val enabled=state.optBoolean("native_enabled")
                    SettingsGroup(header="Playback",footer="Changes apply to the next track.") {
                        SettingsRow(icon=Icons.Rounded.Headphones,title="Native with web fallback",
                            subtitle="Native audio is experimental and requires Premium. Web playback is used if native audio fails.",
                            trailing={com.music.streamx.ui.components.SkinSwitch(colors=switchColors,checked=enabled,enabled=preferences!=null,onCheckedChange={update(Bundle().apply {putBoolean("native_enabled",it)})})})
                        if(enabled) {
                            RowDivider()
                            SettingsRow(icon=Icons.Rounded.GraphicEq,title="Stream quality",subtitle=state.optString("native_status"))
                            SegmentedControl(options=listOf("96 kbps","160 kbps","320 kbps"),
                                selectedIndex=listOf(96,160,320).indexOf(state.optInt("native_kbps",320)).coerceAtLeast(0),
                                onSelect={index->update(Bundle().apply {putInt("native_kbps",listOf(96,160,320)[index])})},enabled=preferences!=null,
                                modifier=Modifier.padding(start=20.dp,end=20.dp,bottom=14.dp))
                            RowDivider()
                            SettingsRow(title="Retry native playback",enabled=preferences!=null,onClick={update(Bundle())})
                        }
                    }
                }
                if(state?.optBoolean("own_lyrics_supported")==true) SettingsGroup(header="Lyrics",footer="Use Apple Music’s synced lyrics for the exact track. Other providers are used when Apple has no lyrics. Applies to the next track.") {
                    val own=state.optBoolean("own_lyrics_enabled",true)
                    fun setOwn(value: Boolean){update(Bundle().apply {putBoolean("own_lyrics_enabled",value)})}
                    SettingsRow(title="Use Apple Music lyrics",subtitle="Synced lines and word timing when available",enabled=preferences!=null,
                        onClick={setOwn(!own)},trailing={com.music.streamx.ui.components.SkinSwitch(colors=switchColors,checked=own,enabled=preferences!=null,onCheckedChange=::setOwn)})
                }
                if(state?.optBoolean("alac_supported")==true) SettingsGroup(header="Lossless",footer="Download and play ALAC fragments through your bridge. Applies when you select a track. Stations use Apple Music account streaming.") {
                    fun configureAlac() {
                        try { context.startActivity(ApkExtensions.configurationIntent(extension).putExtra("streamx.open_alac",true)) }
                        catch(failure: Exception){error=failure.message ?: "Could not open ALAC settings"}
                    }
                    fun setLossless(enabled: Boolean) {
                        if(enabled && !state.optBoolean("alac_configured")) configureAlac()
                        else update(Bundle().apply {putBoolean("alac_enabled",enabled)})
                    }
                    SettingsRow(icon=Icons.Rounded.GraphicEq,title="Lossless streaming",enabled=preferences!=null,
                        subtitle=if(state.optBoolean("alac_enabled"))"Native lossless playback and audio processing" else "Requires an ALAC bridge connection",
                        onClick={setLossless(!state.optBoolean("alac_enabled"))},
                        trailing={com.music.streamx.ui.components.SkinSwitch(colors=switchColors,checked=state.optBoolean("alac_enabled"),enabled=preferences!=null,onCheckedChange=::setLossless)})
                    RowDivider()
                    SettingsRow(title="ALAC connection",subtitle="Server, access token and sample-rate limit",onClick=::configureAlac)
                }
                if(state?.optString("playback_mode")=="web") SettingsGroup(header="Playback") {
                    SettingsRow(icon=Icons.Rounded.Headphones,title="Account streaming",subtitle=state.optString("playback_description"))
                    val choices=state.optJSONArray("quality_options")?.let {array->
                        (0 until minOf(array.length(),6)).map {array.optInt(it)}.filter {it in 16..1536}.distinct()
                    }.orEmpty()
                    if(choices.isNotEmpty()) {
                        RowDivider()
                        SettingsRow(icon=Icons.Rounded.GraphicEq,title="Stream quality",subtitle=state.optString("quality_description"))
                        SegmentedControl(options=choices.map {"$it kbps"},
                            selectedIndex=choices.indexOf(state.optInt("quality_kbps")).coerceAtLeast(0),
                            onSelect={index->update(Bundle().apply {putInt("quality_kbps",choices[index])})},enabled=preferences!=null,
                            modifier=Modifier.padding(start=20.dp,end=20.dp,bottom=14.dp))
                    }
                    if(state.has("lossless_available") && !state.optBoolean("lossless_available")) {
                        RowDivider()
                        SettingsRow(title="Lossless audio",subtitle=state.optString("lossless_description"),
                            trailing={com.music.streamx.ui.components.SkinSwitch(colors=switchColors,checked=false,onCheckedChange=null,enabled=false)})
                    }
                    RowDivider()
                    SettingsRow(title="Player status",subtitle=state.optString("playback_status").ifBlank {"Ready"})
                }
                if(busy && preferences==null)LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp))
                error?.let {message->
                    Text(message,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(20.dp))
                    TextButton(onClick={revision++}){Text("Retry")}
                }
                Spacer(Modifier.height(24.dp))
            }
            BottomFadeScrim(modifier=Modifier.rotate(180f))
            FrostedTopBar(title=extension.name,scrolled=scroll.value>0,onBack=onDismiss,transparentBackdrop=true,artworkPageChrome=true)
            }
        }
        }
    }
}
