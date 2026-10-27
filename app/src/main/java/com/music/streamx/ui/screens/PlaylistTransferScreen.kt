package com.music.streamx.ui.screens

import com.music.streamx.ui.components.SkinAlertDialog as AlertDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.streamx.data.extensions.ApkExtensions
import com.music.streamx.data.model.UserPlaylist
import com.music.streamx.data.playlistsync.*
import com.music.streamx.data.sources.SourceRegistry
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import com.music.streamx.ui.components.BottomFadeScrim
import com.music.streamx.ui.components.FrostedTopBar
import com.music.streamx.ui.components.topBarContentPadding

/** Where the new transfer flow is: Soundiiz-style steps, one decision per page. */
private enum class TransferStepPage { SOURCE, PLAYLISTS, DESTINATION }

@Composable
internal fun PlaylistSyncScreen(coordinator: PlaylistTransferCoordinator,legacy: PlaylistSyncCoordinator,
    onSignIn: ()->Unit,onDismiss: ()->Unit) {
    val ui by coordinator.state.collectAsStateWithLifecycle()
    val installed by ApkExtensions.installed.collectAsStateWithLifecycle()
    val configs by SourceRegistry.configs.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableStateOf(TransferStepPage.SOURCE) }
    var destinationPicker by remember { mutableStateOf(false) }
    var legacyOpen by remember { mutableStateOf(false) }
    var profilesOpen by remember { mutableStateOf(false) }
    var recordId by remember { mutableStateOf<String?>(null) }
    var detailId by remember { mutableStateOf<String?>(null) }
    val transferList=rememberLazyListState()
    val profilesList=rememberLazyListState()
    val running=ui.batch.isNotEmpty()
    // Settings' search can leave the keyboard up over this screen.
    val keyboard=androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus=androidx.compose.ui.platform.LocalFocusManager.current
    LaunchedEffect(Unit) {focus.clearFocus();keyboard?.hide()}
    LaunchedEffect(ui.stage,step,running) { transferList.scrollToItem(0) }
    // A playlist opened from its own page arrives already chosen.
    LaunchedEffect(ui.busy) { if(!ui.busy && !running && step==TransferStepPage.SOURCE && ui.selectedSources.isNotEmpty()) step=TransferStepPage.DESTINATION }
    val list=if(profilesOpen)profilesList else transferList
    DisposableEffect(legacy) {legacy.blockAutomaticSync(true);onDispose{legacy.blockAutomaticSync(false);coordinator.close()}}
    LaunchedEffect(Unit) { if(ui.providers.isEmpty() && !ui.busy) coordinator.open() }
    if(legacyOpen) {
        LegacyPlaylistSyncScreen(legacy,onSignIn) {legacyOpen=false}
        return
    }
    val reviewing=ui.stage==SyncStage.REVIEW
    val back: ()->Unit={ when {
        profilesOpen -> profilesOpen=false
        reviewing && !ui.busy -> coordinator.configure()
        !running && step==TransferStepPage.DESTINATION && !ui.busy -> step=TransferStepPage.PLAYLISTS
        !running && step==TransferStepPage.PLAYLISTS && !ui.busy -> step=TransferStepPage.SOURCE
        else -> {coordinator.close();onDismiss()}
    } }
    BackHandler(onBack=back)
    val source=ui.providers.firstOrNull{it.id==ui.sourceProvider}
    val destination=ui.providers.firstOrNull{it.id==ui.destinationProvider}
    if(destinationPicker) TransferDestinationDialog(ui,onNew={combine->destinationPicker=false;coordinator.combine(combine)},
        onPick={destinationPicker=false;coordinator.destination(it)},onDismiss={destinationPicker=false})
    ui.batch.firstOrNull{it.playlist.playlistId==detailId}?.let { item ->
        TransferResultDialog(item,ui.busy,onAddClose={coordinator.addCloseMatches(item.playlist.playlistId)},onDismiss={detailId=null})
    }
    val c=MaterialTheme.colorScheme
    val header=when {
        profilesOpen -> "Sync Profiles"
        reviewing -> "Review changes"
        running -> if(ui.busy)"Transferring" else "Transfer complete"
        step==TransferStepPage.PLAYLISTS -> "Choose playlists"
        step==TransferStepPage.DESTINATION -> "Transfer to"
        else -> "Playlist transfer"
    }
    ui.records.firstOrNull{it.id==recordId}?.let { record ->
        TransferRecordDialog(record,onDismiss={recordId=null},onReviewed={coordinator.markRecordReviewed(record.id);recordId=null})
    }
    Box(Modifier.fillMaxSize().background(c.background).navigationBarsPadding()) {
        // Like Sources, content occupies the whole viewport beneath floating chrome.
        // A separate Column row clips the title at the toolbar's bottom on scroll.
        LazyColumn(state=list,modifier=Modifier.fillMaxSize(),contentPadding=PaddingValues(top=topBarContentPadding(),bottom=20.dp)) {
            item(key="screen-title") {
                Column(Modifier.padding(horizontal=20.dp).padding(top=8.dp)) {
                    Text(header,style=MaterialTheme.typography.displayLarge,color=c.onBackground)
                    if(!profilesOpen && !reviewing && !running) TransferStepDots(step.ordinal)
                }
            }
            if(ui.busy && !running) item {
                Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp)
                    Text(ui.progress.ifBlank{"Loading playlists…"},Modifier.weight(1f),textAlign=TextAlign.Start,color=c.onSurfaceVariant)
                }
            }
            ui.error?.let { message -> item {
                Column(Modifier.padding(start=16.dp,end=16.dp,top=14.dp)) { TransferCard {
                    Text(message,color=MaterialTheme.colorScheme.error,textAlign=TextAlign.Start)
                    if((ui.sourceProvider=="youtube" || ui.destinationProvider=="youtube") &&
                        (message.contains("Connect YouTube Music",true) || message.contains("YouTube",true) && message.contains("sign in",true))) TextButton(onClick=onSignIn,enabled=!ui.busy){Text("Connect YouTube Music")}
                    for(provider in listOfNotNull(source,destination).distinctBy{it.id}.filter{it.id.startsWith("plugin:") &&
                        (message.contains("authorization",true) || message.contains("sign in",true) || message.contains("connect ${it.name}",true))}) {
                        val config=configs.firstOrNull{it.id==provider.id.removePrefix("plugin:")}
                        val extension=installed.firstOrNull{it.packageName==(provider.pluginPackage ?: config?.extensionPackage)}
                        if(extension!=null) TextButton(onClick={ApkExtensions.showSettings(extension)},enabled=!ui.busy){Text("Manage ${provider.name} account")}
                    }
                } }
            } }
            when {
                profilesOpen -> profilesPage(ui,coordinator,legacy,onReview={profilesOpen=false;coordinator.reviewLink(it)},onRecord={recordId=it},onLegacy={legacy.open();legacyOpen=true})
                reviewing -> reviewPage(ui,coordinator,source,destination)
                running -> progressPage(ui,destination,onDetail={detailId=it})
                step==TransferStepPage.SOURCE -> item(key="source") {
                    SettingsGroup(header="Transfer from",footer="Your original playlists stay unchanged.") {
                        val providers=ui.providers
                        providers.forEachIndexed { index,provider ->
                            if(index>0) RowDivider()
                            SettingsRow(icon=serviceIcon(provider.name),title=provider.name,
                                value=if(provider.id==ui.sourceProvider && ui.selectedSources.isNotEmpty()) "${ui.selectedSources.size} selected" else null,
                                enabled=!ui.busy,onClick={
                                    if(provider.id!=ui.sourceProvider || ui.sourcePlaylists.isEmpty()) coordinator.source(provider.id)
                                    step=TransferStepPage.PLAYLISTS
                                })
                        }
                    }
                    SettingsGroup {
                        SettingsRow(icon=Icons.Rounded.Sync,title="Sync profiles",subtitle="Playlists kept up to date automatically",
                            value=ui.links.size.takeIf{it>0}?.toString(),enabled=!ui.busy,onClick={profilesOpen=true})
                    }
                }
                step==TransferStepPage.PLAYLISTS -> playlistsPage(ui,coordinator,source)
                else -> destinationPage(ui,coordinator,source,destination,onPickExisting={destinationPicker=true})
            }
            val action: Pair<String,(()->Unit)?>? = when {
                profilesOpen -> null
                reviewing -> (if(ui.busy)ui.progress.ifBlank{"Working…"} else "Save reviewed changes") to coordinator::apply.takeIf{!ui.busy}
                running -> if(ui.busy) null else "Done" to {coordinator.close();onDismiss()}
                step==TransferStepPage.PLAYLISTS -> (if(ui.selectedSources.isEmpty()) "Select playlists" else "Continue with ${ui.selectedSources.size}") to
                    {step=TransferStepPage.DESTINATION}.takeIf{!ui.busy && ui.selectedSources.isNotEmpty()}
                step==TransferStepPage.DESTINATION -> {
                    val count=ui.selectedSources.size
                    val named=ui.destinationPlaylist==null && (ui.combine || count==1)
                    (if(count==1)"Transfer playlist" else if(ui.combine || ui.destinationPlaylist!=null)"Combine $count playlists" else "Transfer $count playlists") to coordinator::transferSelected.takeIf{
                        !ui.busy && ui.error==null && count>0 && destination?.writable==true && (!named || ui.title.isNotBlank())}
                }
                else -> null
            }
            action?.let { (label,onClick) -> item(key="transfer-action") {
                TransferAction(label=label,enabled=onClick!=null,onClick={onClick?.invoke()},modifier=Modifier.padding(horizontal=16.dp).padding(top=24.dp))
            } }
            if(running && !ui.busy) item(key="transfer-more") {
                TextButton(onClick={step=TransferStepPage.SOURCE;coordinator.open()},Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp)) {Text("Transfer more playlists",color=transferAccent)}
            }
        }
        val topFadeAlpha by remember(list) { derivedStateOf {
            if(list.firstVisibleItemIndex>0) 1f else {
                val titleHeight=list.layoutInfo.visibleItemsInfo.firstOrNull{it.index==0}?.size?.toFloat() ?: 1f
                ((list.firstVisibleItemScrollOffset-titleHeight*.4f)/(titleHeight*.6f)).coerceIn(0f,1f)
            }
        } }
        BottomFadeScrim(modifier=Modifier.rotate(180f).graphicsLayer { alpha=topFadeAlpha })
        FrostedTopBar(title=header,scrolled=false,onBack=back,transparentBackdrop=true,artworkPageChrome=true)
    }
}

@Composable private fun TransferStepDots(current: Int) {
    Row(Modifier.padding(top=10.dp,bottom=4.dp),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
        listOf("Source","Playlists","Destination").forEachIndexed { index,label ->
            Box(Modifier.height(4.dp).width(if(index==current)28.dp else 14.dp).clip(CircleShape)
                .background(if(index<=current)transferAccent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=.3f)))
        }
        Text("Step ${current+1} of 3",Modifier.padding(start=6.dp),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun serviceIcon(name: String)=when {
    name.contains("youtube",true) -> Icons.Rounded.SmartDisplay
    name.contains("local",true) -> Icons.Rounded.PhoneAndroid
    name.contains("cloud",true) -> Icons.Rounded.Cloud
    else -> Icons.Rounded.LibraryMusic
}

@Composable private fun SelectMark(selected: Boolean) {
    Icon(if(selected)Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,if(selected)"Selected" else null,Modifier.size(24.dp),
        tint=if(selected)transferAccent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=.55f))
}

private fun LazyListScope.playlistsPage(ui: PlaylistTransferUi,coordinator: PlaylistTransferCoordinator,source: TransferProvider?) {
    item(key="playlist-filter") {
        Column(Modifier.padding(horizontal=16.dp).padding(top=18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            if(source?.userPlaylists==true) com.music.streamx.ui.components.GlassSegmentedControl(listOf("Your playlists","All saved"),
                if(ui.sourceUserOnly)0 else 1,{if(!ui.busy)coordinator.sourceFilter(it==0)})
        }
    }
    item(key="playlists") {
        var query by rememberSaveable(ui.sourceProvider){mutableStateOf("")}
        if(ui.sourcePlaylists.size>8) Box(Modifier.padding(start=16.dp,end=16.dp,top=12.dp)) {
            com.music.streamx.ui.components.SkinOutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),label={Text("Search playlists")},labelText="Search playlists",singleLine=true)
        }
        val rows=visibleTransferPlaylists(ui.sourcePlaylists,emptySet(),true,query)
        if(rows.isEmpty() && !ui.busy) Text(if(query.isNotBlank())"No matching playlists" else if(ui.sourceUserOnly && source?.userPlaylists==true)"No playlists of your own here. Try All saved." else "No playlists found.",
            Modifier.padding(horizontal=20.dp,vertical=24.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(rows.isNotEmpty()) {
            val all=ui.sourcePlaylists.isNotEmpty() && ui.sourcePlaylists.all{it.playlistId in ui.selectedSources}
            SettingsGroup(header="${source?.name ?: "Playlists"} · ${ui.sourcePlaylists.size}",
                footer=if(ui.selectedSources.isEmpty()) "Pick one or more. You can keep them separate or combine them in the next step." else null,topSpacing=14.dp) {
                if(query.isBlank()) {
                    SettingsRow(title=if(all)"Deselect all" else "Select all",enabled=!ui.busy,onClick={coordinator.selectAllSources(!all)}) {SelectMark(all)}
                    RowDivider()
                }
                rows.forEachIndexed { index,playlist ->
                    if(index>0) RowDivider()
                    val selected=playlist.playlistId in ui.selectedSources
                    val counted=playlist.subtitle.any{it.isDigit()}
                    if(!counted) LaunchedEffect(playlist.playlistId) {coordinator.requestTrackCount(playlist.playlistId)}
                    val subtitle=if(counted) playlist.subtitle else ui.trackCounts[playlist.playlistId]?.let{if(it==1)"1 song" else "$it songs"}
                    SettingsRow(icon=Icons.Rounded.QueueMusic,title=playlist.title,subtitle=subtitle,
                        enabled=!ui.busy,onClick={coordinator.toggleSource(playlist.playlistId)}) {SelectMark(selected)}
                }
            }
        }
    }
}

private fun LazyListScope.destinationPage(ui: PlaylistTransferUi,coordinator: PlaylistTransferCoordinator,source: TransferProvider?,
    destination: TransferProvider?,onPickExisting: ()->Unit) {
    item(key="destination") {
        val count=ui.selectedSources.size
        val names=ui.sourcePlaylists.filter{it.playlistId in ui.selectedSources}.map{it.title}
        Text("${source?.name ?: ""} · ${if(count==1) names.firstOrNull().orEmpty() else "$count playlists"}",
            Modifier.padding(horizontal=20.dp).padding(top=10.dp),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
        val targets=ui.providers.filter{it.writable && it.id!=ui.sourceProvider}
        SettingsGroup(header="Music service",footer=if(targets.isEmpty())"Install a plugin or connect YouTube Music to transfer playlists." else null,topSpacing=14.dp) {
            targets.forEachIndexed { index,provider ->
                if(index>0) RowDivider()
                SettingsRow(icon=serviceIcon(provider.name),title=provider.name,enabled=!ui.busy,onClick={coordinator.destinationProvider(provider.id)}) {
                    SelectMark(provider.id==ui.destinationProvider)
                }
            }
        }
        val existing=ui.destinationPlaylists.firstOrNull{it.playlistId==ui.destinationPlaylist}
        val named=existing==null && (ui.combine || count==1)
        if(destination!=null) SettingsGroup(header="Options",
            footer=when {
                ui.keepSynchronized -> "Songs you add to ${if(count==1)"this playlist" else "these playlists"} later are copied over while StreamX is open."
                count>1 && (existing!=null || ui.combine) -> "Songs that appear in more than one playlist are added once, in playlist order."
                else -> "Songs that can't be matched are listed when the transfer finishes."
            }) {
            SettingsRow(icon=Icons.Rounded.PlaylistAdd,title="Add to",
                value=existing?.title ?: if(count==1) "New playlist" else if(ui.combine) "One new playlist" else "Separate playlists",
                enabled=!ui.busy,onClick=onPickExisting)
            if(named) Box(Modifier.fillMaxWidth().padding(start=TEXT_INSET,end=ROW_INSET,bottom=12.dp)) {
                com.music.streamx.ui.components.SkinOutlinedTextField(ui.title,coordinator::title,Modifier.fillMaxWidth(),
                    label={Text("Playlist name")},labelText="Playlist name",singleLine=true)
            }
            RowDivider()
            SettingsRow(icon=Icons.Rounded.Sync,title="Keep in sync",enabled=!ui.busy,onClick={coordinator.keepSynchronized(!ui.keepSynchronized)}) {
                com.music.streamx.ui.components.SkinSwitch(ui.keepSynchronized,coordinator::keepSynchronized,enabled=!ui.busy,colors=syncSwitchColors())
            }
        }
    }
}

private fun LazyListScope.progressPage(ui: PlaylistTransferUi,destination: TransferProvider?,onDetail: (String)->Unit) {
    item(key="summary") {
        val c=MaterialTheme.colorScheme
        val finished=ui.batch.count{it.status==BatchStatus.DONE || it.status==BatchStatus.FAILED}
        val moved=ui.batch.sumOf{it.added+it.alreadyPresent}
        val total=ui.batch.sumOf{it.total}
        val missing=ui.batch.sumOf{it.notFound.size+it.close.size}
        Column(Modifier.padding(horizontal=16.dp).padding(top=14.dp)) { TransferCard {
            Text(if(ui.busy)"$finished of ${ui.batch.size} playlists" else "$moved of $total songs moved",style=MaterialTheme.typography.titleLarge.copy(fontWeight=FontWeight.Bold))
            LinearProgressIndicator(progress={if(ui.busy) (finished.toFloat()/ui.batch.size.coerceAtLeast(1)) else if(total==0) 1f else moved.toFloat()/total},
                Modifier.fillMaxWidth().clip(CircleShape),color=transferAccent,trackColor=c.onSurfaceVariant.copy(alpha=.18f))
            Text(when {
                ui.busy -> "Keep this screen open until it finishes."
                missing>0 -> "$missing songs weren't found on ${destination?.name ?: "the destination"}. Tap a playlist to see them."
                else -> "Every song was found on ${destination?.name ?: "the destination"}."
            },style=MaterialTheme.typography.bodyMedium,color=c.onSurfaceVariant)
        } }
    }
    item(key="batch") {
        SettingsGroup(header="Playlists",topSpacing=14.dp) {
            ui.batch.forEachIndexed { index,item ->
                if(index>0) RowDivider()
                val missing=item.notFound.size+item.close.size
                val status=when(item.status) {
                    BatchStatus.WAITING -> "Waiting"
                    BatchStatus.MATCHING -> if(item.total>0)"Matching ${item.done} of ${item.total}" else "Reading songs…"
                    BatchStatus.ADDING -> "Adding songs…"
                    BatchStatus.DONE -> "${item.added+item.alreadyPresent} of ${item.total} moved"+if(missing>0)" · $missing not found" else ""
                    BatchStatus.FAILED -> item.error ?: "Failed"
                }
                SettingsRow(icon=Icons.Rounded.QueueMusic,title=item.playlist.title,subtitle=status,
                    onClick=if(item.status==BatchStatus.DONE && missing>0 || item.status==BatchStatus.FAILED) ({onDetail(item.playlist.playlistId)}) else null) {
                    when(item.status) {
                        BatchStatus.WAITING -> Icon(Icons.Rounded.Schedule,null,Modifier.size(22.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=.6f))
                        BatchStatus.MATCHING,BatchStatus.ADDING -> CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp,color=transferAccent)
                        BatchStatus.DONE -> Row(verticalAlignment=Alignment.CenterVertically) {
                            Icon(if(missing>0)Icons.Rounded.Info else Icons.Rounded.CheckCircle,null,Modifier.size(22.dp),tint=if(missing>0)Color(0xFFCB8830) else Color(0xFF32BE63))
                            if(missing>0) Icon(Icons.Rounded.ChevronRight,null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=.6f))
                        }
                        BatchStatus.FAILED -> Icon(Icons.Rounded.ErrorOutline,null,Modifier.size(22.dp),tint=MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

private fun LazyListScope.reviewPage(ui: PlaylistTransferUi,coordinator: PlaylistTransferCoordinator,source: TransferProvider?,destination: TransferProvider?) {
    item {val c=MaterialTheme.colorScheme;Column(Modifier.padding(horizontal=16.dp).padding(top=14.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        BruteOption(ui.brute,!ui.busy,coordinator::brute)
        TransferCard {
            Column {
                Text(ui.title,style=MaterialTheme.typography.titleMedium.copy(fontWeight=FontWeight.Bold))
                Text("${source?.name} → ${destination?.name}",style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                TransferCount(ui.matches.count{it.selectedId!=null && !it.excluded},"Selected",Modifier.weight(1f),Color(0xFF32BE63))
                TransferCount(ui.matches.count{it.selectedId==null && !it.excluded},"Review",Modifier.weight(1f),Color(0xFFCB8830))
                TransferCount(ui.matches.count{it.excluded},"Skipped",Modifier.weight(1f),c.onSurfaceVariant)
            }
        }
    }}
    itemsIndexed(ui.matches,key={index,row->"$index:${row.source.videoId}"}) {index,row ->
        var expanded by remember(row.source.videoId,index){mutableStateOf(false)}
        val c=MaterialTheme.colorScheme
        Column(Modifier.padding(horizontal=16.dp).padding(top=10.dp)) { TransferCard {
            Row(Modifier.fillMaxWidth().clickable(enabled=!ui.busy){expanded=!expanded},verticalAlignment=Alignment.CenterVertically) {
                ServiceTile("playlist",row.source.thumbnailUrl)
                Column(Modifier.weight(1f).padding(horizontal=12.dp)) {
                    Text(row.source.title,style=MaterialTheme.typography.titleSmall.copy(fontWeight=FontWeight.SemiBold),maxLines=2,overflow=TextOverflow.Ellipsis)
                    Text(row.source.artist,style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
                Icon(if(expanded)Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,"Choose match",tint=c.onSurfaceVariant)
            }
            val selected=row.candidates.firstOrNull{it.videoId==row.selectedId}
            Text(when {row.excluded->"Skipped";selected!=null->"${if(row.uncertain)"Brute · " else ""}${selected.title} · ${selected.artist}";row.candidates.isEmpty()->"No matching track found";else->"Choose a recording"},
                style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis)
            if(expanded) {
                row.candidates.forEach {candidate ->
                    Row(Modifier.fillMaxWidth().clickable(enabled=!ui.busy){coordinator.choose(index,candidate.videoId)},verticalAlignment=Alignment.CenterVertically) {
                        RadioButton(row.selectedId==candidate.videoId,onClick=null,enabled=!ui.busy,colors=RadioButtonDefaults.colors(selectedColor=transferAccent))
                        Column(Modifier.weight(1f).padding(start=8.dp)) {
                            Text(candidate.title,style=MaterialTheme.typography.bodyMedium)
                            Text("${candidate.artist} · ${candidate.durationText.orEmpty()}",style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)
                        }
                    }
                }
                TextButton(onClick={coordinator.choose(index,null)},enabled=!ui.busy){Text("Skip this track",color=transferAccent)}
            }
        } }
    }
}

private fun LazyListScope.profilesPage(ui: PlaylistTransferUi,coordinator: PlaylistTransferCoordinator,legacy: PlaylistSyncCoordinator,
    onReview: (String)->Unit,onRecord: (String)->Unit,onLegacy: ()->Unit) {
    item {
        SettingsGroup(header="Synchronized playlists",footer="New matching songs are added automatically while StreamX is open. Uncertain matches wait for your review.",topSpacing=14.dp) {
            if(ui.links.isEmpty()) SettingsRow(icon=Icons.Rounded.Sync,title="No sync profiles yet",subtitle="Turn on Keep in sync when you transfer a playlist.")
            ui.links.forEachIndexed { index,link ->
                if(index>0) RowDivider()
                SettingsRow(icon=Icons.Rounded.Sync,title=link.title,
                    subtitle="${ui.providers.firstOrNull{it.id==link.source}?.name ?: link.source} → ${ui.providers.firstOrNull{it.id==link.destination}?.name ?: link.destination} · ${if(link.enabled)link.status else "Paused"}") {
                    com.music.streamx.ui.components.SkinSwitch(link.enabled,{coordinator.enableLink(link.id,it)},enabled=!ui.busy,colors=syncSwitchColors())
                }
                Row(Modifier.fillMaxWidth().padding(start=TEXT_INSET-12.dp,end=8.dp),horizontalArrangement=Arrangement.Start) {
                    TextButton(onClick={onReview(link.id)},enabled=!ui.busy){Text("Review changes",color=transferAccent)}
                    TextButton(onClick={coordinator.removeLink(link.id)},enabled=!ui.busy){Text("Unlink",color=MaterialTheme.colorScheme.error)}
                }
            }
        }
    }
    if(ui.records.isNotEmpty()) item {
        SettingsGroup(header="Transfer records",footer="Close matches and Brute picks are kept here for later review. The latest 20 are saved on this device.") {
            ui.records.forEachIndexed { index,record ->
                if(index>0) RowDivider()
                val pending=record.rows.count{it.uncertain && it.selectedId!=null && !it.excluded}
                SettingsRow(icon=Icons.Rounded.History,title=record.title,subtitle="${record.route} · $pending to review",onClick={onRecord(record.id)})
            }
        }
    }
    if(legacy.hasLinks) item {SettingsGroup {SettingsRow(icon=Icons.Rounded.SmartDisplay,title="Earlier YouTube sync profiles",enabled=!ui.busy,onClick=onLegacy)}}
}

@Composable private fun TransferDestinationDialog(ui: PlaylistTransferUi,onNew: (Boolean)->Unit,onPick: (UserPlaylist)->Unit,onDismiss: ()->Unit) {
    var query by remember{mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Add to")},
        text={Column(Modifier.fillMaxWidth().heightIn(max=440.dp).verticalScroll(rememberScrollState())) {
            @Composable fun Option(title: String,selected: Boolean,subtitle: String?=null,onClick: ()->Unit) {
                Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                    RadioButton(selected,onClick=null)
                    Column(Modifier.weight(1f).padding(start=12.dp)) {
                        Text(title,textAlign=TextAlign.Start)
                        subtitle?.let{Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    }
                }
            }
            val fresh=ui.destinationPlaylist==null
            if(ui.selectedSources.size>1) {
                Option("Separate playlists",fresh && !ui.combine,"A new playlist for each one") {onNew(false)}
                Option("One new playlist",fresh && ui.combine,"Combine them into a single playlist") {onNew(true)}
            } else Option("New playlist",fresh) {onNew(false)}
            if(ui.destinationPlaylists.isNotEmpty()) Text("Existing playlists",Modifier.padding(top=12.dp,bottom=4.dp),
                style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(ui.destinationPlaylists.size>8) com.music.streamx.ui.components.SkinOutlinedTextField(query,{query=it},Modifier.fillMaxWidth().padding(vertical=8.dp),
                label={Text("Search playlists")},labelText="Search playlists",singleLine=true)
            if(ui.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical=8.dp))
            visibleTransferPlaylists(ui.destinationPlaylists,emptySet(),true,query).filter{it.playlistId!=ui.sourcePlaylist}.forEach { playlist ->
                Option(playlist.title,playlist.playlistId==ui.destinationPlaylist) {onPick(playlist)}
            }
        }},confirmButton={TextButton(onClick=onDismiss){Text("Done")}})
}

@Composable private fun TransferResultDialog(item: BatchTransfer,busy: Boolean,onAddClose: ()->Unit,onDismiss: ()->Unit) {
    val c=MaterialTheme.colorScheme
    AlertDialog(onDismissRequest=onDismiss,title={Text(item.playlist.title)},text={
        LazyColumn(Modifier.heightIn(max=460.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            item.error?.let { error -> item {Text(error,color=c.error)} }
            if(item.status==BatchStatus.DONE) item {Text("${item.added} added · ${item.alreadyPresent} already there",style=MaterialTheme.typography.bodyMedium)}
            item.warning.takeIf{it.isNotBlank()}?.let { warning -> item {Text(warning,style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)} }
            if(item.close.isNotEmpty()) {
                item {Text("Close matches · ${item.close.size}",style=MaterialTheme.typography.titleSmall.copy(fontWeight=FontWeight.Bold),modifier=Modifier.padding(top=6.dp))}
                item {Text("A similar version was found, but not an exact one.",style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)}
                items(item.close) { row -> TransferSongLine(row.source.title,"${row.source.artist}  →  ${row.candidates.first().title} · ${row.candidates.first().artist}") }
            }
            if(item.notFound.isNotEmpty()) {
                item {Text("Not found · ${item.notFound.size}",style=MaterialTheme.typography.titleSmall.copy(fontWeight=FontWeight.Bold),modifier=Modifier.padding(top=6.dp))}
                items(item.notFound) { song -> TransferSongLine(song.title,song.artist) }
            }
        }
    },confirmButton={
        if(item.close.isNotEmpty()) TextButton(onClick=onAddClose,enabled=!busy){Text(if(busy)"Adding…" else "Add ${item.close.size} close matches")}
        else TextButton(onClick=onDismiss){Text("Close")}
    },dismissButton=if(item.close.isNotEmpty()) ({TextButton(onClick=onDismiss){Text("Close")}}) else null)
}

@Composable private fun TransferSongLine(title: String,subtitle: String) {
    Column {
        Text(title,style=MaterialTheme.typography.bodyMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
        Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis)
    }
}

private val transferAccent: Color @Composable get()=MaterialTheme.colorScheme.primary

@Composable private fun TransferCard(content: @Composable ColumnScope.()->Unit) {
    val c=MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.extraLarge,color=c.surfaceVariant,contentColor=c.onSurface) {
        Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp),content=content)
    }
}
@Composable private fun ServiceTile(service: String,artwork: String?=null) {
    val youtube=service.contains("youtube",true)
    val apple=service.contains("apple",true)
    val c=MaterialTheme.colorScheme
    val color=when {youtube->Color(0xFFFF172A);apple->Color(0xFFFA2D48);else->c.surfaceContainerHighest}
    val icon=when {youtube->Icons.Rounded.PlayArrow;apple->Icons.Rounded.MusicNote;service=="sync"->Icons.Rounded.Sync;service=="playlist"->Icons.Rounded.QueueMusic;else->Icons.Rounded.LibraryMusic}
    Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(color),contentAlignment=Alignment.Center) {
        Icon(icon,null,Modifier.size(24.dp),tint=if(youtube || apple)Color.White else c.onSurfaceVariant)
        if(!artwork.isNullOrBlank())AsyncImage(artwork,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
    }
}
@Composable private fun syncSwitchColors(): SwitchColors {
    val dark=MaterialTheme.colorScheme.background.luminance()<.5f
    val apple=com.music.streamx.ui.skins.LocalSkinRenderer.current is com.music.streamx.ui.skins.SkinDefaultPlayerProvider
    return SwitchDefaults.colors(checkedTrackColor=if(apple)Color(0xFF30C75A)else MaterialTheme.colorScheme.primary,
        checkedThumbColor=if(apple)Color.White else MaterialTheme.colorScheme.onPrimary,
        uncheckedTrackColor=if(dark)Color(0xFF3A3A3C)else Color(0xFFE0E0E5),
        uncheckedBorderColor=if(dark)Color(0xFF717176)else Color(0xFF9A9AA0),
        uncheckedThumbColor=if(dark)Color(0xFFB9B9BE)else Color.White)
}

@Composable private fun TransferCount(count: Int,label: String,modifier: Modifier,color: Color) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(color.copy(alpha=.1f)).padding(10.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Text("$count",style=MaterialTheme.typography.titleMedium.copy(fontWeight=FontWeight.Bold),color=color)
        Text(label,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun TransferAction(label: String,enabled: Boolean,onClick: ()->Unit,modifier: Modifier=Modifier) {
    Button(onClick=onClick,enabled=enabled,modifier=modifier.fillMaxWidth().heightIn(min=56.dp),
        shape=MaterialTheme.shapes.large,contentPadding=PaddingValues(horizontal=20.dp,vertical=14.dp)) {
        Text(label,Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
        Icon(Icons.Rounded.ChevronRight,null)
    }
}

@Composable private fun BruteOption(checked: Boolean,enabled: Boolean,onChange: (Boolean)->Unit) {
    TransferCard {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end=14.dp)) {
                Text("Brute",style=MaterialTheme.typography.titleMedium)
                Text("Select the first available match automatically. Keep the choices in Transfer records for later review.",
                    Modifier.padding(top=4.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            com.music.streamx.ui.components.SkinSwitch(checked,onChange,enabled=enabled,colors=syncSwitchColors())
        }
    }
}

@Composable private fun TransferRecordDialog(record: TransferRecord,onDismiss: ()->Unit,onReviewed: ()->Unit) {
    AlertDialog(onDismissRequest=onDismiss,title={Text(record.title)},text={
        LazyColumn(Modifier.heightIn(max=440.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            item {Text("${record.route} · ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(record.createdAt))}",style=MaterialTheme.typography.bodySmall)}
            item {Text("This is the saved transfer record. Reviewing it does not change the destination playlist.",style=MaterialTheme.typography.bodySmall)}
            itemsIndexed(record.rows) {_,row -> Column {
                Text("${row.source.title} · ${row.source.artist}",style=MaterialTheme.typography.titleSmall)
                val selected=row.candidates.firstOrNull{it.videoId==row.selectedId}
                Text(if(row.excluded)"Skipped" else if(selected==null)"No selection" else "${if(row.uncertain)"Brute · " else "Selected · "}${selected.title} · ${selected.artist} · ${selected.durationText.orEmpty()}",style=MaterialTheme.typography.bodySmall)
                row.candidates.filter{it.videoId!=row.selectedId}.forEach {candidate ->
                    Text("Alternate: ${candidate.title} · ${candidate.artist} · ${candidate.durationText.orEmpty()}",Modifier.padding(top=4.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }}
        }
    },confirmButton={TextButton(onClick=onReviewed){Text("Mark reviewed")}},dismissButton={TextButton(onClick=onDismiss){Text("Close")}})
}
