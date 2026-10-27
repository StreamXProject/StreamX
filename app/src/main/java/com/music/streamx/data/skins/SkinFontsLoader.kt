package com.music.streamx.data.skins

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.music.streamx.data.extensions.ApkExtensions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Reads bounded font assets from the verified APK; executable UI is handled separately by SkinUiLoader. */
internal object SkinFontsLoader {
    suspend fun load(context: Context, skin: SkinSpec): FontFamily? = withContext(Dispatchers.IO) {
        val fonts = skin.fonts ?: return@withContext null
        fonts.validate()
        val packageName = requireNotNull(skin.extensionPackage)
        check(ApkExtensions.certificate(packageName) == skin.extensionCertificate) { "Skin signing certificate changed" }
        val assets = context.createPackageContext(packageName, 0).assets
        val directory = File(context.cacheDir, "skin-fonts/$packageName/${skin.extensionVersion}").apply { mkdirs() }
        fun extract(path: String, name: String): File {
            val bytes = assets.open(path).use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= 2 * 1024 * 1024) { "Each skin font must be at most 2 MiB" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            require(bytes.isNotEmpty() && bytes.size <= 2 * 1024 * 1024) { "Each skin font must be at most 2 MiB" }
            val file = File(directory, name)
            if (!file.exists() || !file.readBytes().contentEquals(bytes)) file.writeBytes(bytes)
            // Fail application of an invalid font rather than leaving a broken active skin.
            Typeface.createFromFile(file)
            return file
        }
        val regular = extract(fonts.regular, "regular.font")
        val bold = extract(fonts.bold, "bold.font")
        check(ApkExtensions.certificate(packageName) == skin.extensionCertificate) { "Skin changed while loading fonts" }
        FontFamily(Font(regular, FontWeight.Normal), Font(bold, FontWeight.Bold))
    }
}
