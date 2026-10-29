package com.music.streamx.data.playlistsync

import android.os.Bundle
import com.music.streamx.data.YtMusicRepository
import com.music.streamx.data.extensions.ApkCatalogue
import com.music.streamx.data.extensions.ApkExtensions
import com.music.streamx.data.model.*
import com.music.streamx.data.sources.*
import com.music.streamx.data.spotify.LocalPlaylistStore
import com.music.streamx.data.streamx.StreamXClient
import com.music.streamx.data.streamx.StreamXSession
import org.json.JSONObject

internal class YouTubeTransferBackend(private val account: ()->String?): PlaylistTransferBackend {
    override val id="youtube"
    override val name="YouTube Music"
    override val writable=true
    override suspend fun identity()=account() ?: error("Connect YouTube Music first")
    override suspend fun playlists(editable: Boolean): List<UserPlaylist> {
        val all=if(editable)YtMusicRepository.userPlaylists().getOrThrow() else YtMusicRepository.allLibraryPlaylists().getOrThrow()
        return if(!editable) all else all.filter { YtMusicRepository.playlistOwned(it.browseId).getOrThrow()==true }
    }
    override suspend fun tracks(playlist: String)=YtMusicRepository.playlistEntries("VL${playlist.removePrefix("VL")}").getOrThrow()
    override suspend fun search(query: String)=YtMusicRepository.search(query,SearchFilter.SONGS).getOrThrow().filterIsInstance<SearchResult.Track>().map{it.song}
    override fun ownsTrack(id: String)=Regex("[A-Za-z0-9_-]{11}").matches(id)
    override suspend fun create(title: String): TransferDestination {
        val id=YtMusicRepository.createPlaylist(title,PlaylistPrivacy.PRIVATE).getOrThrow()
        return TransferDestination(id.removePrefix("VL"))
    }
    override suspend fun add(playlist: String,tracks: List<String>) { YtMusicRepository.addToPlaylist(playlist.removePrefix("VL"),tracks).getOrThrow();PlaylistTransferEvents.changed(id) }
}

internal class PluginTransferBackend(private val config: SourceConfig): PlaylistTransferBackend {
    override val id="plugin:${config.id}"
    override val name=config.displayName
    override val pluginPackage=config.extensionPackage
    private var capabilities: Set<String>?=null
    override val writable get()=capabilities?.let{setOf("search","playlists","create_playlist","add_to_playlist").all{operation->operation in it}} ?: true
    override val supportsUserPlaylists get()=capabilities?.contains("playlists") ?: true
    // This explicit picker session has its own narrow access path. Playback source flags stay unchanged.
    private val catalogue=ApkCatalogue(config,playlistTransfer=true)
    override suspend fun identity(): String {
        check(ApkExtensions.installed.value.any {it.packageName==config.extensionPackage && it.certificate==config.extensionCertificate && it.sourceAuthority==config.extensionAuthority}) { "The installed $name plugin changed" }
        if(capabilities==null) {
            val response=JSONObject(ApkExtensions.call(config,"health",Bundle()))
            check(!response.has("error")) {response.optString("error")}
            // A plugin that isn't ready (signed out, say) says why instead of listing what it can do.
            check(response.has("capabilities") || response.optBoolean("ok",true)) {response.optString("message").ifBlank{"Sign in to $name first"}}
            val array=response.optJSONArray("capabilities") ?: error("$name does not advertise playlist support")
            val supported=(0 until array.length()).map{array.optString(it)}.toSet()
            check("detail" in supported && "account" in supported) {"$name does not support playlist transfers"}
            capabilities=supported
        }
        return "${config.extensionPackage}|${config.extensionCertificate}|${catalogue.accountIdentity()}"
    }
    override suspend fun playlists(editable: Boolean)=if(editable) catalogue.playlists() else catalogue.libraryPlaylists()
    override suspend fun tracks(playlist: String)=catalogue.playlistTracks(playlist)
    override suspend fun search(query: String)=ApkExtensionSource(config).search(query,25)
    override fun ownsTrack(id: String)=SourceRegistry.parseTrackKey(id)?.first==config.id
    override suspend fun create(title: String)=catalogue.createPlaylist(title).let{TransferDestination(it.first,it.second)}
    override suspend fun add(playlist: String,tracks: List<String>) {catalogue.addTracksToPlaylist(playlist,tracks)}
}

internal class LocalTransferBackend: PlaylistTransferBackend {
    override val id="local"
    override val name="Local & Telegram playlists"
    override val writable=false
    override suspend fun identity()="device"
    override suspend fun playlists(editable: Boolean)=LocalPlaylistStore.playlists.value.map { UserPlaylist(it.browseId,it.title,"On this device",null) }
    override suspend fun tracks(playlist: String)=LocalPlaylistStore.getPlaylist(playlist.removePrefix("local:playlist:"))?.songs ?: error("Source playlist was removed")
    override suspend fun search(query: String)=emptyList<Song>()
    override fun ownsTrack(id: String)=false
    override suspend fun create(title: String): TransferDestination=error("Read-only source")
    override suspend fun add(playlist: String,tracks: List<String>): Unit=error("Read-only source")
}

internal class StreamXTransferBackend(private val kind: SourceKind): PlaylistTransferBackend {
    override val id="streamx:${kind.name}"
    override val name=if(kind==SourceKind.CLOUDSTREAM) "CloudStream" else "StreamX"
    override val writable=false
    override suspend fun identity(): String {
        check(SourceRegistry.catalogue.value==kind) { "Select the original $name account again" }
        return if(kind==SourceKind.CLOUDSTREAM) com.music.streamx.data.cloudstream.CloudStream.read(com.music.streamx.data.cloudstream.CloudStream.PROFILE).profileId
            else StreamXSession.state.value.scope
    }
    override suspend fun playlists(editable: Boolean)=StreamXClient.current().playlists()
    override suspend fun tracks(playlist: String): List<Song> {
        val detail=StreamXClient.current().detail(playlist,"","",null)
        return (detail.songs as? UiState.Success)?.data ?: error("Could not load source playlist")
    }
    override suspend fun search(query: String)=emptyList<Song>()
    override fun ownsTrack(id: String)=false
    override suspend fun create(title: String): TransferDestination=error("Read-only source")
    override suspend fun add(playlist: String,tracks: List<String>): Unit=error("Read-only source")
}

/** Build a certificate-bound connection without changing or enabling a playback source. */
internal fun transferPlugin(extension: com.music.streamx.data.extensions.ApkExtension): PluginTransferBackend {
    val existing=SourceRegistry.configs.value.firstOrNull {it.kind==SourceKind.APK_PLUGIN && it.extensionPackage==extension.packageName}
    val config=existing?.takeIf {it.extensionCertificate==extension.certificate && it.extensionAuthority==extension.sourceAuthority}
        ?: SourceConfig(id=extension.skinId,kind=SourceKind.APK_PLUGIN,enabled=false,shownInSources=false,label=extension.name,
            extensionPackage=extension.packageName,extensionAuthority=requireNotNull(extension.sourceAuthority),extensionCertificate=extension.certificate)
    return PluginTransferBackend(config)
}
