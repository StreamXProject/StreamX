package com.music.streamx.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import java.io.File

/**
 * Profile pictures to pick from when there's no photo to hand: animals, faces,
 * places and a few jokes, each drawn here rather than shipped as images, so any
 * size stays sharp and nothing is borrowed. Kept as files once chosen, so every
 * surface that shows the account's photo shows these the same way.
 */
object ProfileAvatars {
    data class Avatar(val id: String, val label: String, val group: String, val draw: DrawScope.() -> Unit)

    val all: List<Avatar> by lazy { listOf(
        Avatar("cat", "Cat", "Animals") { cat() },
        Avatar("dog", "Dog", "Animals") { dog() },
        Avatar("fox", "Fox", "Animals") { fox() },
        Avatar("panda", "Panda", "Animals") { panda() },
        Avatar("frog", "Frog", "Animals") { frog() },
        Avatar("penguin", "Penguin", "Animals") { penguin() },
        Avatar("bunny", "Bunny", "Animals") { bunny() },
        Avatar("owl", "Owl", "Animals") { owl() },
        Avatar("bear", "Bear", "Animals") { bear() },
        Avatar("koala", "Koala", "Animals") { koala() },
        Avatar("chick", "Chick", "Animals") { chick() },
        Avatar("pig", "Piggy", "Animals") { pig() },
        Avatar("tiger", "Tiger", "Animals") { tiger() },
        Avatar("hamster", "Hamster", "Animals") { hamster() },
    ) + anime + listOf(
        Avatar("sunset", "Sunset", "Places") { sunset() },
        Avatar("night", "Night sea", "Places") { nightSea() },
        Avatar("aurora", "Aurora", "Places") { aurora() },
        Avatar("dunes", "Dunes", "Places") { dunes() },
        Avatar("sakura_tree", "Blossom", "Places") { blossom() },
        Avatar("city", "City nights", "Places") { cityNight() },
        Avatar("forest", "Forest", "Places") { forest() },
        Avatar("beach", "Beach", "Places") { beach() },
        Avatar("alien", "Alien", "Funny") { alien() },
        Avatar("ghost", "Ghost", "Funny") { ghost() },
        Avatar("cool", "Too cool", "Funny") { coolFace() },
        Avatar("toast", "Toast", "Funny") { toast() },
        Avatar("robot", "Robot", "Funny") { robot() },
        Avatar("cactus", "Cactus", "Funny") { cactus() },
        Avatar("cloud", "Cloudy", "Funny") { cloudFace() },
        Avatar("slime", "Slime", "Funny") { slime() },
    ) }

    /**
     * Anime faces from parts: hair colour, eye colour, hairstyle and one extra,
     * mixed so no two in the row look alike.
     */
    private val anime: List<Avatar> by lazy {
        val names = listOf("Sakura", "Sora", "Kin", "Kuro", "Hana", "Ren", "Yuki", "Aoi", "Mika", "Kai", "Nori", "Rin",
            "Haru", "Momo", "Shin", "Tsuki", "Akane", "Midori", "Hoshi", "Kumo", "Natsu", "Yume", "Hikari", "Ryu")
        val hairs = listOf(0xFFFF9EC7, 0xFF3B5BDB, 0xFFFFC94D, 0xFF2B2B33, 0xFFE8EEF5, 0xFFB5179E, 0xFF2DC653, 0xFFFF6B35,
            0xFF8D6E63, 0xFF7B2CBF, 0xFF00B4D8, 0xFFD62828)
        val eyes = listOf(0xFF6A4BFF, 0xFF00B4D8, 0xFFE8590C, 0xFFD6336C, 0xFF2B9348, 0xFFFFB703)
        names.mapIndexed { i, name ->
            val hair = Color(hairs[i % hairs.size])
            val eye = Color(eyes[(i * 5 + 1) % eyes.size])
            val style = i % 3
            val extra = (i / 3) % 5
            Avatar("anime_$i", name, "Anime") { animeFace(hair, eye, style, extra) }
        }
    }

    /** Draws [avatar] to a PNG in the app's files and returns its URI, for the account's photo. */
    fun save(context: Context, avatar: Avatar, px: Int = 512): String {
        val image = ImageBitmap(px, px)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(image), Size(px.toFloat(), px.toFloat())) {
            avatar.draw(this)
        }
        val file = File(File(context.filesDir, "profile").apply { mkdirs() }, "avatar_${avatar.id}.png")
        file.outputStream().use { image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        // A fresh name per pick, so image caches don't serve the last one.
        return Uri.fromFile(file).buildUpon().appendQueryParameter("v", System.currentTimeMillis().toString()).build().toString()
    }

    /** Copies a picked photo into the app's files, so it outlives the picker's permission. */
    fun savePhoto(context: Context, source: Uri): String? = runCatching {
        val file = File(File(context.filesDir, "profile").apply { mkdirs() }, "photo.jpg")
        context.contentResolver.openInputStream(source)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
        Uri.fromFile(file).buildUpon().appendQueryParameter("v", System.currentTimeMillis().toString()).build().toString()
    }.getOrNull()
}

// ---------------------------------------------------------------- shared bits

private fun DrawScope.backdrop(top: Color, bottom: Color) =
    drawRect(Brush.verticalGradient(listOf(top, bottom)))

private fun DrawScope.u(f: Float) = size.minDimension * f
private fun DrawScope.at(x: Float, y: Float) = Offset(size.width * x, size.height * y)

private fun DrawScope.eyes(y: Float, gap: Float, r: Float, shine: Boolean = true) {
    for (side in listOf(-1, 1)) {
        drawCircle(Color(0xFF1E1E24), u(r), at(.5f + side * gap, y))
        if (shine) drawCircle(Color.White, u(r * .38f), at(.5f + side * gap + .012f, y - .015f))
    }
}

private fun DrawScope.blush(y: Float, gap: Float) {
    for (side in listOf(-1, 1)) drawOval(Color(0xFFFF8FA3).copy(alpha = .55f), at(.5f + side * gap - .05f, y - .02f), Size(u(.1f), u(.045f)))
}

private fun DrawScope.smile(y: Float, w: Float, color: Color = Color(0xFF1E1E24)) {
    val path = Path().apply {
        moveTo(size.width * (.5f - w), size.height * y)
        quadraticTo(size.width * .5f, size.height * (y + w * .9f), size.width * (.5f + w), size.height * y)
    }
    drawPath(path, color, style = Stroke(u(.022f), cap = StrokeCap.Round))
}

private fun DrawScope.triangle(a: Offset, b: Offset, c: Offset, color: Color) =
    drawPath(Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); close() }, color)

// ---------------------------------------------------------------- animals

private fun DrawScope.cat() {
    backdrop(Color(0xFFFFD6A5), Color(0xFFFFADAD))
    val fur = Color(0xFFF4A259)
    triangle(at(.22f, .42f), at(.28f, .14f), at(.46f, .3f), fur)
    triangle(at(.78f, .42f), at(.72f, .14f), at(.54f, .3f), fur)
    triangle(at(.27f, .36f), at(.3f, .2f), at(.4f, .3f), Color(0xFFFFC2C7))
    triangle(at(.73f, .36f), at(.7f, .2f), at(.6f, .3f), Color(0xFFFFC2C7))
    drawCircle(fur, u(.3f), at(.5f, .55f))
    eyes(.52f, .11f, .04f)
    triangle(at(.47f, .6f), at(.53f, .6f), at(.5f, .64f), Color(0xFFE5677F))
    smile(.65f, .05f)
    for (side in listOf(-1, 1)) for (k in 0..1) drawLine(Color(0xFF1E1E24).copy(alpha = .6f), at(.5f + side * .12f, .62f + k * .03f), at(.5f + side * .3f, .6f + k * .05f), u(.008f))
}

private fun DrawScope.dog() {
    backdrop(Color(0xFFB9FBC0), Color(0xFF98F5E1))
    val fur = Color(0xFFC08552)
    drawOval(Color(0xFF7A4F2A), at(.16f, .3f), Size(u(.2f), u(.38f)))
    drawOval(Color(0xFF7A4F2A), at(.64f, .3f), Size(u(.2f), u(.38f)))
    drawCircle(fur, u(.28f), at(.5f, .55f))
    drawOval(Color(0xFFF3E0C9), at(.36f, .58f), Size(u(.28f), u(.2f)))
    eyes(.5f, .1f, .04f)
    drawOval(Color(0xFF1E1E24), at(.46f, .58f), Size(u(.08f), u(.055f)))
    drawOval(Color(0xFFFF6B81), at(.47f, .69f), Size(u(.06f), u(.08f)))
}

private fun DrawScope.fox() {
    backdrop(Color(0xFF2B2D42), Color(0xFF4A4E69))
    val orange = Color(0xFFF77F00)
    triangle(at(.2f, .5f), at(.26f, .16f), at(.44f, .34f), orange)
    triangle(at(.8f, .5f), at(.74f, .16f), at(.56f, .34f), orange)
    triangle(at(.18f, .42f), at(.82f, .42f), at(.5f, .86f), orange)
    triangle(at(.26f, .52f), at(.5f, .56f), at(.5f, .86f), Color.White)
    triangle(at(.74f, .52f), at(.5f, .56f), at(.5f, .86f), Color.White)
    eyes(.53f, .12f, .035f)
    drawCircle(Color(0xFF1E1E24), u(.035f), at(.5f, .8f))
}

private fun DrawScope.panda() {
    backdrop(Color(0xFFCDEAC0), Color(0xFF8FC0A9))
    drawCircle(Color(0xFF1E1E24), u(.1f), at(.28f, .3f))
    drawCircle(Color(0xFF1E1E24), u(.1f), at(.72f, .3f))
    drawCircle(Color.White, u(.3f), at(.5f, .55f))
    for (side in listOf(-1, 1)) rotate(side * 25f, at(.5f + side * .12f, .52f)) {
        drawOval(Color(0xFF1E1E24), at(.5f + side * .12f - .06f, .47f), Size(u(.12f), u(.15f)))
    }
    eyes(.53f, .12f, .028f)
    drawOval(Color(0xFF1E1E24), at(.46f, .62f), Size(u(.08f), u(.05f)))
    blush(.68f, .2f)
}

private fun DrawScope.frog() {
    backdrop(Color(0xFFFFF3B0), Color(0xFFFFD166))
    val green = Color(0xFF52B788)
    drawCircle(green, u(.12f), at(.32f, .36f))
    drawCircle(green, u(.12f), at(.68f, .36f))
    drawOval(green, at(.18f, .38f), Size(u(.64f), u(.44f)))
    drawCircle(Color.White, u(.08f), at(.32f, .36f))
    drawCircle(Color.White, u(.08f), at(.68f, .36f))
    drawCircle(Color(0xFF1E1E24), u(.045f), at(.33f, .37f))
    drawCircle(Color(0xFF1E1E24), u(.045f), at(.67f, .37f))
    smile(.6f, .14f)
    blush(.62f, .22f)
}

private fun DrawScope.penguin() {
    backdrop(Color(0xFFBDE0FE), Color(0xFFA2D2FF))
    drawOval(Color(0xFF22223B), at(.2f, .18f), Size(u(.6f), u(.74f)))
    drawOval(Color.White, at(.3f, .36f), Size(u(.4f), u(.5f)))
    eyes(.42f, .08f, .035f)
    triangle(at(.44f, .5f), at(.56f, .5f), at(.5f, .58f), Color(0xFFFFA62B))
    blush(.54f, .15f)
}

private fun DrawScope.bunny() {
    backdrop(Color(0xFFE7C6FF), Color(0xFFC8B6FF))
    drawOval(Color.White, at(.3f, .04f), Size(u(.14f), u(.42f)))
    drawOval(Color.White, at(.56f, .04f), Size(u(.14f), u(.42f)))
    drawOval(Color(0xFFFFC2D1), at(.335f, .1f), Size(u(.07f), u(.3f)))
    drawOval(Color(0xFFFFC2D1), at(.595f, .1f), Size(u(.07f), u(.3f)))
    drawCircle(Color.White, u(.28f), at(.5f, .6f))
    eyes(.58f, .1f, .035f)
    drawOval(Color(0xFFFF8FAB), at(.47f, .64f), Size(u(.06f), u(.04f)))
    blush(.68f, .17f)
}

private fun DrawScope.owl() {
    backdrop(Color(0xFF1D3557), Color(0xFF457B9D))
    val brown = Color(0xFF9C6644)
    triangle(at(.22f, .3f), at(.3f, .14f), at(.38f, .3f), brown)
    triangle(at(.78f, .3f), at(.7f, .14f), at(.62f, .3f), brown)
    drawOval(brown, at(.2f, .22f), Size(u(.6f), u(.68f)))
    drawCircle(Color(0xFFFFE8D6), u(.12f), at(.38f, .44f))
    drawCircle(Color(0xFFFFE8D6), u(.12f), at(.62f, .44f))
    drawCircle(Color(0xFF1E1E24), u(.06f), at(.38f, .44f))
    drawCircle(Color(0xFF1E1E24), u(.06f), at(.62f, .44f))
    drawCircle(Color.White, u(.02f), at(.395f, .425f))
    drawCircle(Color.White, u(.02f), at(.635f, .425f))
    triangle(at(.46f, .54f), at(.54f, .54f), at(.5f, .62f), Color(0xFFFFB703))
}

// ---------------------------------------------------------------- anime

private fun DrawScope.animeFace(hair: Color, eye: Color, style: Int, extra: Int) {
    backdrop(Color(0xFFFFF0F6), Color(0xFFFFDEEB))
    // Long hair falls behind the face; the short cut doesn't.
    if (style != 1) drawOval(hair, at(.14f, .12f), Size(u(.72f), u(if (style == 2) .9f else .84f)))
    if (style == 2) { drawOval(hair, at(.08f, .5f), Size(u(.18f), u(.42f))); drawOval(hair, at(.74f, .5f), Size(u(.18f), u(.42f))) }
    if (extra == 1) for (side in listOf(-1, 1)) {
        triangle(at(.5f + side * .2f, .26f), at(.5f + side * .34f, .04f), at(.5f + side * .36f, .3f), hair)
        triangle(at(.5f + side * .24f, .24f), at(.5f + side * .32f, .1f), at(.5f + side * .33f, .26f), Color(0xFFFFC2D1))
    }
    drawOval(Color(0xFFFFE3D3), at(.24f, .28f), Size(u(.52f), u(.58f)))
    when (style) {
        0 -> for (i in 0..4) triangle(at(.22f + i * .12f, .22f), at(.34f + i * .12f, .22f), at(.28f + i * .12f, .44f), hair)
        1 -> { drawOval(hair, at(.2f, .14f), Size(u(.6f), u(.3f))); for (i in 0..3) triangle(at(.24f + i * .14f, .3f), at(.38f + i * .14f, .3f), at(.3f + i * .14f, .42f), hair) }
        else -> drawPath(Path().apply {
            moveTo(size.width * .22f, size.height * .4f)
            quadraticTo(size.width * .4f, size.height * .15f, size.width * .8f, size.height * .38f)
            lineTo(size.width * .8f, size.height * .22f); lineTo(size.width * .22f, size.height * .22f); close()
        }, hair)
    }
    // Big eyes, a coloured iris and two shines, as anime draws them.
    for (side in listOf(-1, 1)) {
        val c = at(.5f + side * .11f, .56f)
        drawOval(Color.White, Offset(c.x - u(.06f), c.y - u(.08f)), Size(u(.12f), u(.16f)))
        drawOval(eye, Offset(c.x - u(.05f), c.y - u(.07f)), Size(u(.1f), u(.14f)))
        drawOval(Color(0xFF1E1E24), Offset(c.x - u(.03f), c.y - u(.04f)), Size(u(.06f), u(.09f)))
        drawCircle(Color.White, u(.022f), Offset(c.x + u(.015f), c.y - u(.035f)))
        drawCircle(Color.White, u(.01f), Offset(c.x - u(.02f), c.y + u(.03f)))
        drawLine(Color(0xFF1E1E24), Offset(c.x - u(.07f), c.y - u(.085f)), Offset(c.x + u(.07f), c.y - u(.09f)), u(.016f), StrokeCap.Round)
    }
    blush(.69f, .17f)
    smile(.73f, .035f, Color(0xFFC2255C))
    when (extra) {
        2 -> for (side in listOf(-1, 1)) drawCircle(Color(0xFF1E1E24), u(.085f), at(.5f + side * .11f, .56f), style = Stroke(u(.014f)))
        3 -> { // headphones
            drawArc(Color(0xFF343A40), 200f, 140f, false, at(.18f, .1f), Size(u(.64f), u(.6f)), style = Stroke(u(.04f)))
            drawRoundRect(Color(0xFF343A40), at(.13f, .44f), Size(u(.1f), u(.18f)), CornerRadius(u(.04f)))
            drawRoundRect(Color(0xFF343A40), at(.77f, .44f), Size(u(.1f), u(.18f)), CornerRadius(u(.04f)))
        }
        4 -> { // bow
            triangle(at(.62f, .2f), at(.74f, .12f), at(.74f, .28f), Color(0xFFFF4D6D))
            triangle(at(.86f, .2f), at(.74f, .12f), at(.74f, .28f), Color(0xFFFF4D6D))
            drawCircle(Color(0xFFC9184A), u(.03f), at(.74f, .2f))
        }
    }
}

// ---------------------------------------------------------------- more animals

private fun DrawScope.bear() {
    backdrop(Color(0xFFFFE5D9), Color(0xFFFFCAD4))
    val fur = Color(0xFF8B5E3C)
    drawCircle(fur, u(.1f), at(.28f, .3f)); drawCircle(fur, u(.1f), at(.72f, .3f))
    drawCircle(Color(0xFFD4A373), u(.05f), at(.28f, .3f)); drawCircle(Color(0xFFD4A373), u(.05f), at(.72f, .3f))
    drawCircle(fur, u(.3f), at(.5f, .56f))
    drawOval(Color(0xFFD4A373), at(.38f, .58f), Size(u(.24f), u(.18f)))
    eyes(.5f, .11f, .035f)
    drawOval(Color(0xFF1E1E24), at(.46f, .6f), Size(u(.08f), u(.05f)))
    blush(.62f, .2f)
}

private fun DrawScope.koala() {
    backdrop(Color(0xFFD8F3DC), Color(0xFFB7E4C7))
    val grey = Color(0xFF9AA5B1)
    drawCircle(grey, u(.15f), at(.22f, .36f)); drawCircle(grey, u(.15f), at(.78f, .36f))
    drawCircle(Color(0xFFE9ECEF), u(.08f), at(.22f, .36f)); drawCircle(Color(0xFFE9ECEF), u(.08f), at(.78f, .36f))
    drawCircle(grey, u(.28f), at(.5f, .56f))
    eyes(.52f, .12f, .03f)
    drawOval(Color(0xFF343A40), at(.43f, .52f), Size(u(.14f), u(.18f)))
    blush(.66f, .2f)
}

private fun DrawScope.chick() {
    backdrop(Color(0xFFCAF0F8), Color(0xFF90E0EF))
    drawCircle(Color(0xFFFFD60A), u(.3f), at(.5f, .56f))
    for (i in -1..1) drawOval(Color(0xFFFFD60A), at(.47f + i * .04f, .18f), Size(u(.05f), u(.12f)))
    eyes(.52f, .1f, .035f)
    triangle(at(.45f, .58f), at(.55f, .58f), at(.5f, .66f), Color(0xFFFF8500))
    blush(.62f, .19f)
}

private fun DrawScope.pig() {
    backdrop(Color(0xFFFFF0F3), Color(0xFFFFCCD5))
    val pink = Color(0xFFFF8FAB)
    triangle(at(.24f, .4f), at(.26f, .2f), at(.42f, .3f), pink); triangle(at(.76f, .4f), at(.74f, .2f), at(.58f, .3f), pink)
    drawCircle(pink, u(.3f), at(.5f, .56f))
    eyes(.5f, .12f, .035f)
    drawOval(Color(0xFFFFB3C6), at(.4f, .56f), Size(u(.2f), u(.13f)))
    drawCircle(Color(0xFFC9184A), u(.022f), at(.46f, .625f)); drawCircle(Color(0xFFC9184A), u(.022f), at(.54f, .625f))
}

private fun DrawScope.tiger() {
    backdrop(Color(0xFF1B4332), Color(0xFF2D6A4F))
    val orange = Color(0xFFFF9F1C)
    drawCircle(orange, u(.1f), at(.28f, .3f)); drawCircle(orange, u(.1f), at(.72f, .3f))
    drawCircle(orange, u(.3f), at(.5f, .56f))
    for (side in listOf(-1, 1)) for (k in 0..2) drawLine(Color(0xFF1E1E24), at(.5f + side * (.3f - k * .02f), .46f + k * .08f), at(.5f + side * (.2f - k * .02f), .5f + k * .08f), u(.025f), StrokeCap.Round)
    for (k in 0..2) drawLine(Color(0xFF1E1E24), at(.5f, .3f + k * .04f), at(.5f, .33f + k * .04f), u(.02f), StrokeCap.Round)
    drawOval(Color.White, at(.38f, .58f), Size(u(.24f), u(.16f)))
    eyes(.5f, .11f, .035f)
    triangle(at(.47f, .6f), at(.53f, .6f), at(.5f, .64f), Color(0xFFE5677F))
}

private fun DrawScope.hamster() {
    backdrop(Color(0xFFFEFAE0), Color(0xFFFAEDCD))
    val fur = Color(0xFFE9C46A)
    drawCircle(fur, u(.08f), at(.3f, .32f)); drawCircle(fur, u(.08f), at(.7f, .32f))
    drawOval(fur, at(.18f, .3f), Size(u(.64f), u(.56f)))
    drawOval(Color.White, at(.26f, .5f), Size(u(.48f), u(.34f)))
    eyes(.5f, .12f, .035f)
    drawCircle(Color(0xFFFF8FA3), u(.025f), at(.5f, .6f))
    blush(.6f, .2f)
}

// ---------------------------------------------------------------- places

private fun DrawScope.sunset() {
    backdrop(Color(0xFFFF6B6B), Color(0xFFFFD93D))
    drawCircle(Color(0xFFFFF3B0), u(.18f), at(.5f, .55f))
    drawPath(Path().apply { moveTo(0f, size.height * .7f); lineTo(size.width * .3f, size.height * .42f); lineTo(size.width * .55f, size.height * .7f); close() }, Color(0xFF6D597A))
    drawPath(Path().apply { moveTo(size.width * .35f, size.height * .7f); lineTo(size.width * .7f, size.height * .36f); lineTo(size.width, size.height * .7f); close() }, Color(0xFF355070))
    drawRect(Color(0xFF2B2D42), at(0f, .7f), Size(size.width, size.height * .3f))
}

private fun DrawScope.nightSea() {
    backdrop(Color(0xFF0B132B), Color(0xFF1C2541))
    for (i in 0..14) drawCircle(Color.White.copy(alpha = .7f), u(.006f + (i % 3) * .003f), at((i * 37 % 100) / 100f, (i * 53 % 50) / 100f))
    drawCircle(Color(0xFFFDF0D5), u(.12f), at(.68f, .3f))
    drawCircle(Color(0xFF0B132B), u(.1f), at(.73f, .27f))
    for (k in 0..3) drawPath(Path().apply {
        val y = size.height * (.66f + k * .09f)
        moveTo(0f, y)
        var x = 0f
        while (x < size.width) { quadraticTo(x + size.width * .06f, y - u(.04f), x + size.width * .12f, y); x += size.width * .12f }
        lineTo(size.width, size.height); lineTo(0f, size.height); close()
    }, Color(0xFF3A506B).copy(alpha = .6f + k * .1f))
}

private fun DrawScope.aurora() {
    backdrop(Color(0xFF03045E), Color(0xFF023E8A))
    for (k in 0..2) drawPath(Path().apply {
        moveTo(0f, size.height * (.3f + k * .08f))
        cubicTo(size.width * .3f, size.height * (.1f + k * .1f), size.width * .6f, size.height * (.5f + k * .05f), size.width, size.height * (.25f + k * .08f))
    }, listOf(Color(0xFF52FFB8), Color(0xFF7BDFF2), Color(0xFFB388EB))[k].copy(alpha = .6f), style = Stroke(u(.07f), cap = StrokeCap.Round))
    triangle(at(0f, .9f), at(.25f, .55f), at(.5f, .9f), Color(0xFF001233))
    triangle(at(.3f, .9f), at(.62f, .5f), at(.95f, .9f), Color(0xFF001845))
    drawRect(Color(0xFF001233), at(0f, .88f), Size(size.width, size.height * .12f))
}

private fun DrawScope.dunes() {
    backdrop(Color(0xFF8ECAE6), Color(0xFFFFE5B4))
    drawCircle(Color(0xFFFFF1A8), u(.1f), at(.75f, .25f))
    drawPath(Path().apply { moveTo(0f, size.height * .65f); quadraticTo(size.width * .35f, size.height * .45f, size.width * .7f, size.height * .65f); lineTo(size.width, size.height * .6f); lineTo(size.width, size.height); lineTo(0f, size.height); close() }, Color(0xFFE9A86B))
    drawPath(Path().apply { moveTo(0f, size.height * .8f); quadraticTo(size.width * .55f, size.height * .6f, size.width, size.height * .82f); lineTo(size.width, size.height); lineTo(0f, size.height); close() }, Color(0xFFD08C60))
    drawRoundRect(Color(0xFF2D6A4F), at(.24f, .5f), Size(u(.04f), u(.18f)), CornerRadius(u(.02f)))
    drawRoundRect(Color(0xFF2D6A4F), at(.18f, .54f), Size(u(.06f), u(.03f)), CornerRadius(u(.015f)))
}

private fun DrawScope.blossom() {
    backdrop(Color(0xFFFFE5EC), Color(0xFFFFC2D1))
    drawLine(Color(0xFF6F4E37), at(.5f, .95f), at(.5f, .55f), u(.06f), StrokeCap.Round)
    drawLine(Color(0xFF6F4E37), at(.5f, .65f), at(.32f, .48f), u(.035f), StrokeCap.Round)
    drawLine(Color(0xFF6F4E37), at(.5f, .6f), at(.7f, .45f), u(.035f), StrokeCap.Round)
    for ((x, y, r) in listOf(Triple(.5f, .36f, .22f), Triple(.28f, .42f, .15f), Triple(.72f, .4f, .16f))) drawCircle(Color(0xFFFF8FAB), u(r), at(x, y))
    for (i in 0..9) drawCircle(Color.White.copy(alpha = .8f), u(.018f), at((i * 29 % 80 + 10) / 100f, (i * 41 % 40 + 20) / 100f))
}

private fun DrawScope.cityNight() {
    backdrop(Color(0xFF240046), Color(0xFF5A189A))
    drawCircle(Color(0xFFFFE066), u(.08f), at(.75f, .22f))
    val heights = listOf(.45f, .6f, .38f, .7f, .5f, .62f)
    heights.forEachIndexed { i, h ->
        val x = i / heights.size.toFloat()
        drawRect(Color(0xFF10002B), at(x, 1f - h), Size(size.width / heights.size + 2f, size.height * h))
        for (r in 0..4) for (c in 0..1) if ((i + r + c) % 3 != 0) drawRect(Color(0xFFFFD60A).copy(alpha = .8f),
            at(x + .03f + c * .07f, 1f - h + .06f + r * .1f), Size(u(.03f), u(.04f)))
    }
}

private fun DrawScope.forest() {
    backdrop(Color(0xFFB7E4C7), Color(0xFF74C69D))
    for ((x, h, c) in listOf(Triple(.2f, .5f, 0xFF2D6A4F), Triple(.5f, .65f, 0xFF1B4332), Triple(.8f, .5f, 0xFF40916C))) {
        triangle(at(x - .16f, .9f), at(x + .16f, .9f), at(x, .9f - h), Color(c))
    }
    drawRect(Color(0xFF52B788), at(0f, .88f), Size(size.width, size.height * .12f))
}

private fun DrawScope.beach() {
    backdrop(Color(0xFF48CAE4), Color(0xFFADE8F4))
    drawCircle(Color(0xFFFFF3B0), u(.1f), at(.75f, .22f))
    drawRect(Color(0xFF0077B6), at(0f, .55f), Size(size.width, size.height * .2f))
    drawPath(Path().apply { moveTo(0f, size.height * .7f); quadraticTo(size.width * .5f, size.height * .62f, size.width, size.height * .72f); lineTo(size.width, size.height); lineTo(0f, size.height); close() }, Color(0xFFFFE8A3))
    drawLine(Color(0xFF8D6E63), at(.28f, .82f), at(.32f, .42f), u(.03f), StrokeCap.Round)
    for (a in listOf(-60f, -20f, 20f, 60f)) rotate(a, at(.32f, .42f)) { drawOval(Color(0xFF2D6A4F), at(.32f, .38f), Size(u(.18f), u(.06f))) }
}

// ---------------------------------------------------------------- funny

private fun DrawScope.alien() {
    backdrop(Color(0xFF10002B), Color(0xFF3C096C))
    drawLine(Color(0xFF80ED99), at(.38f, .22f), at(.32f, .08f), u(.02f), StrokeCap.Round)
    drawLine(Color(0xFF80ED99), at(.62f, .22f), at(.68f, .08f), u(.02f), StrokeCap.Round)
    drawCircle(Color(0xFF80ED99), u(.035f), at(.32f, .08f))
    drawCircle(Color(0xFF80ED99), u(.035f), at(.68f, .08f))
    drawOval(Color(0xFF80ED99), at(.2f, .2f), Size(u(.6f), u(.68f)))
    for (side in listOf(-1, 1)) rotate(side * -20f, at(.5f + side * .13f, .5f)) {
        drawOval(Color(0xFF1E1E24), at(.5f + side * .13f - .08f, .45f), Size(u(.16f), u(.1f)))
    }
    smile(.7f, .06f)
}

private fun DrawScope.ghost() {
    backdrop(Color(0xFF6930C3), Color(0xFF5390D9))
    drawPath(Path().apply {
        moveTo(size.width * .22f, size.height * .85f)
        lineTo(size.width * .22f, size.height * .45f)
        cubicTo(size.width * .22f, size.height * .12f, size.width * .78f, size.height * .12f, size.width * .78f, size.height * .45f)
        lineTo(size.width * .78f, size.height * .85f)
        for (k in 0..3) quadraticTo(size.width * (.78f - .07f - k * .14f), size.height * (if (k % 2 == 0) .78f else .9f), size.width * (.78f - .14f * (k + 1)), size.height * .85f)
        close()
    }, Color.White)
    eyes(.45f, .1f, .045f, shine = false)
    drawOval(Color(0xFF1E1E24), at(.46f, .56f), Size(u(.08f), u(.1f)))
}

private fun DrawScope.coolFace() {
    backdrop(Color(0xFFFFBE0B), Color(0xFFFB5607))
    drawCircle(Color(0xFFFFD60A), u(.34f), at(.5f, .52f))
    drawRoundRect(Color(0xFF1E1E24), at(.22f, .4f), Size(u(.24f), u(.13f)), CornerRadius(u(.05f)))
    drawRoundRect(Color(0xFF1E1E24), at(.54f, .4f), Size(u(.24f), u(.13f)), CornerRadius(u(.05f)))
    drawLine(Color(0xFF1E1E24), at(.44f, .43f), at(.56f, .43f), u(.025f))
    drawLine(Color.White.copy(alpha = .5f), at(.26f, .43f), at(.32f, .43f), u(.015f), StrokeCap.Round)
    drawPath(Path().apply {
        moveTo(size.width * .36f, size.height * .66f)
        quadraticTo(size.width * .55f, size.height * .74f, size.width * .66f, size.height * .62f)
    }, Color(0xFF1E1E24), style = Stroke(u(.025f), cap = StrokeCap.Round))
}

private fun DrawScope.toast() {
    backdrop(Color(0xFF90E0EF), Color(0xFF48CAE4))
    val crust = Color(0xFFB5651D)
    drawRoundRect(crust, at(.18f, .24f), Size(u(.64f), u(.6f)), CornerRadius(u(.16f)))
    drawCircle(crust, u(.16f), at(.32f, .3f))
    drawCircle(crust, u(.16f), at(.68f, .3f))
    drawRoundRect(Color(0xFFF6D6A0), at(.22f, .28f), Size(u(.56f), u(.52f)), CornerRadius(u(.13f)))
    drawCircle(Color(0xFFF6D6A0), u(.13f), at(.33f, .31f))
    drawCircle(Color(0xFFF6D6A0), u(.13f), at(.67f, .31f))
    drawRoundRect(Color(0xFFFFE066), at(.4f, .38f), Size(u(.2f), u(.12f)), CornerRadius(u(.03f)))
    eyes(.56f, .1f, .035f)
    smile(.64f, .06f)
    blush(.64f, .17f)
}

private fun DrawScope.robot() {
    backdrop(Color(0xFFADB5BD), Color(0xFF6C757D))
    drawLine(Color(0xFF343A40), at(.5f, .2f), at(.5f, .1f), u(.02f)); drawCircle(Color(0xFFFF4D6D), u(.04f), at(.5f, .09f))
    drawRoundRect(Color(0xFFDEE2E6), at(.2f, .2f), Size(u(.6f), u(.6f)), CornerRadius(u(.12f)))
    drawRoundRect(Color(0xFF212529), at(.28f, .34f), Size(u(.44f), u(.18f)), CornerRadius(u(.09f)))
    drawCircle(Color(0xFF4CC9F0), u(.05f), at(.39f, .43f)); drawCircle(Color(0xFF4CC9F0), u(.05f), at(.61f, .43f))
    for (i in 0..3) drawRect(Color(0xFF495057), at(.34f + i * .09f, .62f), Size(u(.06f), u(.07f)))
}

private fun DrawScope.cactus() {
    backdrop(Color(0xFFFFE8D6), Color(0xFFFFB4A2))
    drawRoundRect(Color(0xFF52B788), at(.36f, .18f), Size(u(.28f), u(.62f)), CornerRadius(u(.14f)))
    drawRoundRect(Color(0xFF52B788), at(.18f, .36f), Size(u(.14f), u(.24f)), CornerRadius(u(.07f)))
    drawRoundRect(Color(0xFF52B788), at(.68f, .3f), Size(u(.14f), u(.24f)), CornerRadius(u(.07f)))
    drawRoundRect(Color(0xFFE76F51), at(.3f, .76f), Size(u(.4f), u(.18f)), CornerRadius(u(.03f)))
    eyes(.42f, .06f, .025f); smile(.5f, .04f); blush(.48f, .1f)
    drawCircle(Color(0xFFFF8FAB), u(.04f), at(.58f, .17f))
}

private fun DrawScope.cloudFace() {
    backdrop(Color(0xFF4EA8DE), Color(0xFF90E0EF))
    for ((x, y, r) in listOf(Triple(.35f, .55f, .18f), Triple(.55f, .45f, .22f), Triple(.7f, .58f, .15f), Triple(.5f, .62f, .2f))) drawCircle(Color.White, u(r), at(x, y))
    eyes(.55f, .09f, .03f); smile(.62f, .05f); blush(.62f, .16f)
}

private fun DrawScope.slime() {
    backdrop(Color(0xFF2B2D42), Color(0xFF3A0CA3))
    drawPath(Path().apply {
        moveTo(size.width * .18f, size.height * .82f)
        cubicTo(size.width * .14f, size.height * .3f, size.width * .86f, size.height * .3f, size.width * .82f, size.height * .82f)
        close()
    }, Color(0xFF4CC9F0))
    drawOval(Color.White.copy(alpha = .4f), at(.3f, .4f), Size(u(.12f), u(.08f)))
    eyes(.6f, .1f, .04f); smile(.68f, .05f)
}
