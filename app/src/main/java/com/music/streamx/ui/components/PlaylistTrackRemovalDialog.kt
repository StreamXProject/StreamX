package com.music.streamx.ui.components

import com.music.streamx.ui.components.SkinAlertDialog as AlertDialog
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import kotlinx.coroutines.launch
import com.music.streamx.data.extensions.ApkCatalogue
import com.music.streamx.data.model.Song
import com.music.streamx.data.model.UserPlaylist
import com.music.streamx.data.sources.SourceRegistry

internal fun editableTrackPlaylist(current: UserPlaylist?,browseId: String?,song: Song,fromPlayer: Boolean,playlists: List<UserPlaylist>): UserPlaylist? {
    if(fromPlayer)return null
    val target=browseId?.let {ApkCatalogue.parseBrowseId(it)}
    // Plugins that remove a track by its own id rather than a per-entry one:
    // Apple Music, and any extension that says it can (Amazon Music). Limited
    // to playlists in the listener's editable list either way.
    val byTrack=target?.kind=="playlist" && (SourceRegistry.config(target.configId)?.extensionPackage=="com.music.streamx.extensions.applemusic" ||
        com.music.streamx.data.sources.ApkExtensionSource.supports(target.configId,"remove_from_playlist"))
    if(song.setVideoId==null && !byTrack)return null
    return current ?: if(byTrack)playlists.firstOrNull {it.browseId==browseId} else null
}

@Composable internal fun PlaylistTrackRemovalDialog(target: Pair<String,Song>?,onDismiss: ()->Unit,onRemove: (String,Song)->Unit) {
    if(target==null)return
    val (id,song)=target
    AlertDialog(onDismissRequest=onDismiss,title={Text("Remove from playlist?")},
        text={Text("Remove all copies of “${song.title}” from this playlist? The song stays in your library.")},
        confirmButton={TextButton(onClick={onRemove(id,song)}){Text("Remove",color=MaterialTheme.colorScheme.error)}},
        dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}

@Composable internal fun LibraryMutationDialogs(
    track: Pair<String,Song>?,library: Pair<String,ApkCatalogue.LibraryRemovalInfo>?,
    viewModel: com.music.streamx.ui.MainViewModel,navigation: com.music.streamx.ui.skins.SkinLibraryNavigation,
    dismissTrack: ()->Unit,dismissLibrary: ()->Unit,
    haze: dev.chrisbanes.haze.HazeState? = null,
) {
    val scope=androidx.compose.runtime.rememberCoroutineScope()
    PlaylistTrackRemovalDialog(track,dismissTrack) {id,song ->dismissTrack();viewModel.removeFromPlaylist(id,song)}
    if(library!=null) LibraryRemovalDialog(library.second.label,library.second.confirmation,
        haze=haze,onDismiss=dismissLibrary,onConfirm={
            dismissLibrary()
            scope.launch {
                viewModel.removeLibraryItem(library.first)
                navigation.collectionId=null
                navigation.collectionItems=null
            }
        })
}
