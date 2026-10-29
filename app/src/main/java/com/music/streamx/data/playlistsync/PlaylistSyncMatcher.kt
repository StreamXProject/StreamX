package com.music.streamx.data.playlistsync

import com.music.streamx.data.model.Song
import com.music.streamx.data.sources.TrackMatcher
import java.text.Normalizer
import java.util.Locale

/** Different releases of the same recording must not compete as ambiguous matches. */
private fun normalized(value: String): String = Normalizer.normalize(value,Normalizer.Form.NFKC)
    .lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+")," ").trim()

private fun credits(value: String): Set<String> = value.split(Regex("(?i)\\s*(?:,|&| feat\\.? | featuring )\\s*"))
    .map(::normalized).map {if(it.endsWith(" music") && it.removeSuffix(" music").length>=5)it.removeSuffix(" music")else it}
    .filter(String::isNotBlank).toSet()

private fun recordingKey(song: Song): List<Any?> = listOf(
    normalized(song.title),credits(song.artist),TrackMatcher.secondsOf(song.durationText),song.isExplicit,
    Regex("\\b(?:live|remix|remaster(?:ed)?|acoustic|instrumental|karaoke|demo|sped up|slowed)\\b")
        .findAll(normalized(song.albumName.orEmpty())).map {it.value}.toList().sorted(),
)

internal fun reviewPlaylistMatch(song: Song,candidates: List<Song>,rememberedId: String? = null): SyncMatch {
    val target=TrackMatcher.targetOf(song)
    val ranked=TrackMatcher.ranked(candidates,target).distinctBy {it.videoId}
        .distinctBy(::recordingKey).take(5)
    val best=ranked.firstOrNull()
    val credited=best!=null && (TrackMatcher.sharesArtist(song.artist,best.artist) || credits(song.artist).intersect(credits(best.artist)).isNotEmpty())
    val strong=best!=null && credited && TrackMatcher.withinSeconds(best,target,5) &&
        (ranked.size==1 || TrackMatcher.score(best,target)!=TrackMatcher.score(ranked[1],target))
    // Retain a remembered selection in the choices even if another release was
    // ranked first in its group; explicit choices must remain reviewable.
    val choices=if(rememberedId!=null && ranked.none {it.videoId==rememberedId})
        candidates.firstOrNull {it.videoId==rememberedId}?.let {listOf(it)+ranked.take(4)} ?: ranked else ranked
    return SyncMatch(song,choices,rememberedId ?: best?.videoId?.takeIf {strong},!strong && rememberedId==null)
}

/** Brute mode fills unresolved choices, retaining uncertainty for the transfer record. */
internal fun brutePlaylistMatches(rows: List<SyncMatch>, enabled: Boolean): List<SyncMatch> = rows.map { row ->
    when {
        row.excluded -> row
        enabled && row.selectedId == null && row.candidates.isNotEmpty() ->
            row.copy(selectedId = row.candidates.first().videoId, uncertain = true)
        !enabled && row.uncertain -> row.copy(selectedId = null)
        else -> row
    }
}

/**
 * Songs already in the listener's destination playlists, by track id and by a
 * loose title-and-artist key, so a different release of a song they already
 * have still counts as had. Values name the playlist it was found in.
 */
internal class ExistingTracks(private val ids: Map<String,String>,private val keys: Map<String,String>) {
    val size: Int get()=ids.size
    /** Where [song] (a source track) already is, if anywhere. */
    fun find(song: Song): String? = ids[song.videoId] ?: songKeys(song).firstNotNullOfOrNull { keys[it] }
    /** Where the destination track [id] is, if anywhere. */
    fun findId(id: String): String? = ids[id]
    companion object {
        fun of(playlists: List<Pair<String,List<Song>>>): ExistingTracks {
            val ids=HashMap<String,String>();val keys=HashMap<String,String>()
            for((title,songs) in playlists) for(song in songs) {
                ids.putIfAbsent(song.videoId,title)
                songKeys(song).forEach{keys.putIfAbsent(it,title)}
            }
            return ExistingTracks(ids,keys)
        }
    }
}

/** Title (without featured-artist credits) with each credited artist. */
private fun songKeys(song: Song): List<String> {
    val title=normalized(song.title.replace(Regex("(?i)\\s*[(\\[](?:feat|ft|with)\\.?\\s[^)\\]]*[)\\]]"),""))
    if(title.isBlank()) return emptyList()
    return credits(song.artist).map{"$title|$it"}
}
