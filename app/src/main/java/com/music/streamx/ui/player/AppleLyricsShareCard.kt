package com.music.streamx.ui.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import com.music.streamx.ui.replay.Fonts
import com.music.streamx.ui.replay.drawLogo
import com.music.streamx.ui.replay.ellipsised
import com.music.streamx.ui.replay.loadBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * The picked lines as Apple Music lays them out on its lyric card, for the
 * Apple skin: the whole card in the sleeve's own colour, the song heading it
 * (cover, title, artist) rather than signing it off, the lines underneath in
 * heavy white type, and only a small mark at the foot.
 *
 * Shared on its own the card is a rounded card on transparency, as Apple's is.
 * As a story it floats, shadowed, on the cover blurred out to the 9:16 frame.
 * Drawn on a software canvas for the reasons [renderLyricsShareCard] gives.
 */
internal suspend fun renderAppleLyricsCard(
    context: Context,
    card: LyricsShareRequest,
    story: Boolean = false,
): Bitmap = withContext(Dispatchers.Default) {
    val type = Fonts(context)
    val cover = card.artworkUrl?.let { loadBitmap(context, it) }
    val tone = cardTone(cover)
    if (!story) return@withContext drawCard(context, type, card, cover, tone, STORY_W)

    val frame = Bitmap.createBitmap(STORY_W, STORY_H, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(frame)
    drawBlurredCover(canvas, cover, tone)
    val inner = drawCard(context, type, card, cover, tone, STORY_W - STORY_INSET * 2, maxHeight = STORY_H - STORY_INSET * 2)
    val left = STORY_INSET.toFloat()
    val top = ((STORY_H - inner.height) / 2f).coerceAtLeast(STORY_INSET.toFloat())
    val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { setShadowLayer(60f, 0f, 24f, 0x66000000) ; color = Color.TRANSPARENT }
    canvas.drawRoundRect(RectF(left, top, left + inner.width, top + inner.height), CARD_RADIUS, CARD_RADIUS, shadow)
    canvas.drawBitmap(inner, left, top, null)
    frame
}

/** One card, [width] wide and only as tall as its lines need. */
private fun drawCard(
    context: Context,
    type: Fonts,
    card: LyricsShareRequest,
    cover: Bitmap?,
    tone: Int,
    width: Int,
    maxHeight: Int = MAX_H,
): Bitmap {
    val content = width - PAD * 2f
    val headerBottom = PAD + THUMB
    val bodyTop = headerBottom + GAP_HEADER
    val footRoom = GAP_FOOT + MARK_H + PAD
    val room = maxHeight - bodyTop - footRoom
    val rows = fit(type, card.lines, content, room)
    val bodyHeight = rows.sumOf { (it.gap + it.height).toDouble() }.toFloat()
    val height = (bodyTop + bodyHeight + footRoom).roundToInt().coerceAtMost(maxHeight.coerceAtLeast(MIN_H))
        .coerceAtLeast(MIN_H)

    // Drawn on transparency, then clipped to the rounded card.
    val body = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(body)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, 0f, 0f, height.toFloat(), tone, ColorUtils.blendARGB(tone, Color.BLACK, .28f), Shader.TileMode.CLAMP)
    }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)

    // ── Song ────────────────────────────────────────────────────────────────
    drawThumb(canvas, cover, PAD, PAD, THUMB)
    val textX = PAD + THUMB + 30f
    val room2 = width - PAD - textX
    val title = type.body(44f, Color.WHITE, bold = true)
    val artist = type.body(38f, 0xB3FFFFFF.toInt())
    canvas.drawText(ellipsised(card.song.title, title, room2), textX, PAD + THUMB / 2f - 8f, title)
    canvas.drawText(ellipsised(card.song.artist, artist, room2), textX, PAD + THUMB / 2f + 44f, artist)

    // ── Lines ───────────────────────────────────────────────────────────────
    var y = bodyTop
    for (row in rows) {
        y += row.gap
        val x = if (row.centered) width / 2f else PAD
        canvas.drawText(row.text, x, y - row.paint.ascent(), row.paint)
        y += row.height
    }

    // ── Mark ────────────────────────────────────────────────────────────────
    val markBaseline = height - PAD.toFloat()
    val brand = type.body(32f, 0xCCFFFFFF.toInt(), bold = true)
    val brandX = PAD + LOGO_ROOM
    canvas.drawText("StreamX", brandX, markBaseline, brand)
    drawLogo(canvas, context, brandX - 12f, markBaseline)

    val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val clip = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = BitmapShader(body, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
    Canvas(out).drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), CARD_RADIUS, CARD_RADIUS, clip)
    body.recycle()
    return out
}

private class Line(val text: String, val paint: Paint, val height: Float, val gap: Float, val centered: Boolean = false)

/** The largest size whose rows fit [room], down to a floor past which the card grows instead. */
private fun fit(type: Fonts, lines: List<LyricsShareLine>, width: Float, room: Float): List<Line> {
    for (size in LADDER) {
        val rows = layout(type, lines, width, size)
        if (rows.sumOf { (it.gap + it.height).toDouble() } <= room) return rows
    }
    return layout(type, lines, width, LADDER.last())
}

private fun layout(type: Fonts, lines: List<LyricsShareLine>, width: Float, size: Float): List<Line> {
    val words = type.heading(size, Color.WHITE).apply { letterSpacing = -0.02f }
    val subSize = (size * .58f).coerceAtLeast(26f)
    val sub = type.body(subSize, 0xCCFFFFFF.toInt(), bold = true)
    val out = mutableListOf<Line>()
    lines.forEachIndexed { index, line ->
        val lead = if (out.isEmpty()) 0f else size * .42f
        if (line.isGap) {
            val marker = type.body(size * .6f, 0x80FFFFFF.toInt())
            out += Line("(...)", marker, marker.descent() - marker.ascent(), lead)
            return@forEachIndexed
        }
        wrap(line.text, words, width).forEachIndexed { i, text -> out += Line(text, words, size * 1.12f, if (i == 0) lead else 0f) }
        line.subText?.takeIf { it.isNotBlank() && it != line.text }?.let { s ->
            wrap(s, sub, width).forEach { out += Line(it, sub, subSize * 1.25f, 0f) }
        }
    }
    return out
}

/**
 * The card's colour: the sleeve's most vivid swatch, held dark enough that
 * white type stays readable on it, as Apple keeps its own cards.
 */
private fun cardTone(cover: Bitmap?): Int {
    val palette = cover?.let { runCatching { Palette.from(it).maximumColorCount(16).generate() }.getOrNull() }
    // The cover's deep, vivid colour, as Apple's cards are: its loudest accent
    // turned a red sleeve with gold in it olive, and its ground went to near-black.
    var tone = palette?.let { it.darkVibrantSwatch ?: it.vibrantSwatch ?: it.dominantSwatch }?.rgb ?: 0xFF3A3A3C.toInt()
    val hsl = FloatArray(3).also { ColorUtils.colorToHSL(tone, it) }
    hsl[1] = (hsl[1] * 1.15f).coerceAtMost(.85f)
    tone = ColorUtils.HSLToColor(hsl)
    var steps = 0
    while (ColorUtils.calculateLuminance(tone) > .2 && steps++ < 12) tone = ColorUtils.blendARGB(tone, Color.BLACK, .12f)
    steps = 0
    while (ColorUtils.calculateLuminance(tone) < .05 && steps++ < 8) tone = ColorUtils.blendARGB(tone, Color.WHITE, .08f)
    return tone
}

/** The story's backdrop: the cover, filled to the frame and blurred out by scaling, then dimmed. */
private fun drawBlurredCover(canvas: Canvas, cover: Bitmap?, tone: Int) {
    canvas.drawColor(ColorUtils.blendARGB(tone, Color.BLACK, .35f))
    if (cover != null) {
        // A real blur on a small copy, then scaled up: scaling alone left the
        // copy's pixels showing as a mosaic.
        val soft = boxBlurred(Bitmap.createScaledBitmap(cover, BLUR_PX, BLUR_PX, true), 5, 3)
        val side = maxOf(STORY_W, STORY_H).toFloat()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(soft, null, RectF((STORY_W - side) / 2f, (STORY_H - side) / 2f, (STORY_W + side) / 2f, (STORY_H + side) / 2f), paint)
        soft.recycle()
    }
    canvas.drawColor(0x59000000)
}

/** A separable box blur, [passes] times over — near enough a Gaussian at this size. */
private fun boxBlurred(source: Bitmap, radius: Int, passes: Int): Bitmap {
    val w = source.width; val h = source.height
    var px = IntArray(w * h).also { source.getPixels(it, 0, w, 0, 0, w, h) }
    var tmp = IntArray(w * h)
    fun channel(c: Int, shift: Int) = (c shr shift) and 0xFF
    repeat(passes) {
        for (horizontal in listOf(true, false)) {
            for (line in 0 until if (horizontal) h else w) {
                val len = if (horizontal) w else h
                for (i in 0 until len) {
                    var r = 0; var g = 0; var b = 0; var n = 0
                    for (k in -radius..radius) {
                        val j = (i + k).coerceIn(0, len - 1)
                        val c = if (horizontal) px[line * w + j] else px[j * w + line]
                        r += channel(c, 16); g += channel(c, 8); b += channel(c, 0); n++
                    }
                    val out = (0xFF shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
                    if (horizontal) tmp[line * w + i] = out else tmp[i * w + line] = out
                }
            }
            val swap = px; px = tmp; tmp = swap
        }
    }
    source.recycle()
    return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
}

private fun drawThumb(canvas: Canvas, cover: Bitmap?, x: Float, y: Float, size: Float) {
    val bounds = RectF(x, y, x + size, y + size)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    if (cover == null) {
        paint.color = 0x33FFFFFF
        canvas.drawRoundRect(bounds, size * .14f, size * .14f, paint)
        return
    }
    val scaled = Bitmap.createScaledBitmap(cover, size.roundToInt(), size.roundToInt(), true)
    paint.shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
        setLocalMatrix(android.graphics.Matrix().apply { setTranslate(x, y) })
    }
    canvas.drawRoundRect(bounds, size * .14f, size * .14f, paint)
}

private const val BLUR_PX = 48
private const val STORY_W = 1080
private const val STORY_H = 1920
private const val STORY_INSET = 84
private const val MAX_H = 1920
private const val MIN_H = 560
private const val PAD = 72f
private const val THUMB = 150f
private const val GAP_HEADER = 64f
private const val GAP_FOOT = 56f
private const val MARK_H = 40f
private const val LOGO_ROOM = 64f
private const val CARD_RADIUS = 64f
private val LADDER = floatArrayOf(92f, 84f, 76f, 68f, 60f, 54f, 48f, 42f, 36f)
