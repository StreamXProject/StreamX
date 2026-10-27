package com.music.streamx.data.canvas

/**
 * Looping clips a source attaches to its feed cards, keyed by the card's browseId.
 *
 * Held beside [com.music.streamx.data.model.ShelfItem] rather than on it: skins
 * already installed construct ShelfItem themselves, and a new constructor
 * parameter would take away the signature they were compiled against.
 */
object ShelfMotion {
    private const val LIMIT = 512
    private val urls = object : LinkedHashMap<String, String>(64, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>) = size > LIMIT
    }

    /** Records [url] for [browseId], or forgets a clip the card no longer carries. */
    fun put(browseId: String, url: String?) = synchronized(urls) {
        if (url != null && url.startsWith("https://") && url.length <= 2048) urls[browseId] = url else urls.remove(browseId)
    }

    fun get(browseId: String?): String? = browseId?.let { synchronized(urls) { urls[it] } }
}

/** The tall card a source asks for on a lead shelf, as Apple's "Top Picks" draws it. */
data class ShelfHeroArt(
    val thumbnailUrl: String,
    val motionUrl: String?,
    val eyebrow: String,
    val description: String,
    /** Opaque ARGB values chosen by the source to stay legible over this art. */
    val textColor: Long?,
    val secondaryTextColor: Long?,
    val backgroundColor: Long?,
    /** Square art standing in for a missing tall rendition: set at the top over [backgroundColor]. */
    val square: Boolean = false,
)

/**
 * [ShelfHeroArt] by shelf and browseId, held off the card for the same reason
 * as [ShelfMotion]. Keyed by the shelf as well because the same station can be
 * a tall card in one shelf and an ordinary square in the next.
 */
object ShelfHero {
    private const val LIMIT = 256
    private val art = object : LinkedHashMap<String, ShelfHeroArt>(32, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ShelfHeroArt>) = size > LIMIT
    }
    private fun key(shelf: String, browseId: String) = "$shelf\u0000$browseId"

    fun put(shelf: String, browseId: String, value: ShelfHeroArt?) = synchronized(art) {
        if (value != null) art[key(shelf, browseId)] = value else art.remove(key(shelf, browseId))
    }

    fun get(shelf: String, browseId: String?): ShelfHeroArt? = browseId?.let { synchronized(art) { art[key(shelf, it)] } }
}

/** The parts of a source's release page that live outside its tracks, as Apple Music lays them out. */
data class PageExtras(
    /** The looping header clip, square, drawn inside the sleeve. */
    val motionUrl: String?,
    /** The release footer: date, "Updated …", copyright, in that order. */
    val footer: List<String>,
)

/** [PageExtras] by detail browseId, held off [com.music.streamx.data.model.DetailPage] like [ShelfMotion]. */
object PageExtrasStore {
    private const val LIMIT = 64
    private val pages = object : LinkedHashMap<String, PageExtras>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PageExtras>) = size > LIMIT
    }

    fun put(browseId: String, value: PageExtras?) = synchronized(pages) {
        if (value != null) pages[browseId] = value else pages.remove(browseId)
    }

    fun get(browseId: String?): PageExtras? = browseId?.let { synchronized(pages) { pages[it] } }
}
