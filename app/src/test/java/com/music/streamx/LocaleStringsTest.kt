package com.music.streamx

import com.music.streamx.ui.components.SUPPORTED_LANGUAGES
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Every language must translate base keys except the explicitly recorded English fallbacks.
 * This ratchets existing translation debt: new gaps fail, and translated entries must be
 * removed from the fallback inventory once all locales in their group contain them.
 */
@RunWith(Parameterized::class)
class LocaleStringsTest(private val tag: String) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun locales(): List<String> = SUPPORTED_LANGUAGES.map { it.tag }.filter { it != "en" }

        private val resDir: File by lazy {
            val projectRoot = File(".").canonicalFile
            if (File(projectRoot, "app/src/main/res").exists()) {
                File(projectRoot, "app/src/main/res")
            } else {
                File(projectRoot, "src/main/res")
            }
        }

        private fun parse(file: File): Document =
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)

        /** Keys that need translating: `translatable="false"` entries are excluded. */
        private fun translatableKeys(doc: Document): Set<String> {
            val keys = mutableSetOf<String>()
            for (tagName in listOf("string", "plurals", "string-array")) {
                val nodes = doc.getElementsByTagName(tagName)
                for (i in 0 until nodes.length) {
                    val element = nodes.item(i) as Element
                    if (element.getAttribute("translatable") == "false") continue
                    val name = element.getAttribute("name")
                    if (name.isNotEmpty()) keys.add(name)
                }
            }
            return keys
        }

        private val baseKeys: Set<String> by lazy {
            translatableKeys(parse(File(resDir, "values/strings.xml")))
        }

        /**
         * App language tags are BCP-47 (`zh-CN` / `zh-TW`) while Android
         * resource directories use `-r` regions (`values-zh`, `values-zh-rTW`).
         * Mirrors what Android resolves at runtime: Simplified is the bare
         * `zh` catalogue, Traditional is `zh-rTW`.
         */
        internal fun tagToQualifier(tag: String): String = when (tag) {
            "zh-CN" -> "zh"
            "zh-TW" -> "zh-rTW"
            else -> tag
        }
    }

    private fun fallbackKeys(file: String): Set<String> =
        requireNotNull(javaClass.getResourceAsStream("/localization/$file")) {
            "Missing localization fallback inventory: $file"
        }.bufferedReader().useLines { lines ->
            lines.map(String::trim).filter { it.isNotEmpty() && !it.startsWith("#") }.toSet()
        }

    @Test
    fun strings_containsAllBaseKeys() {
        val qualifier = tagToQualifier(tag)
        val localeFile = File(resDir, "values-$qualifier/strings.xml")
        assertTrue("values-$qualifier/strings.xml must exist (for language tag $tag)", localeFile.exists())

        val allowed = fallbackKeys("english-fallbacks.txt") +
            if (tag.startsWith("zh-")) fallbackKeys("chinese-english-fallbacks.txt") else emptySet()
        assertTrue("Fallback inventory contains deleted base keys", baseKeys.containsAll(allowed))
        val missingKeys = baseKeys - translatableKeys(parse(localeFile)) - allowed
        assertTrue(
            "values-$qualifier/strings.xml (language $tag) is missing ${missingKeys.size} keys from values/strings.xml: $missingKeys",
            missingKeys.isEmpty(),
        )
    }

    @Test
    fun fallbackInventory_containsOnlyOutstandingTranslations() {
        val tags = if (tag.startsWith("zh-")) listOf("zh-CN", "zh-TW") else locales()
        val untranslated = tags.flatMap { locale ->
            baseKeys - translatableKeys(parse(File(resDir, "values-${tagToQualifier(locale)}/strings.xml")))
        }.toSet()
        val inventory = fallbackKeys("english-fallbacks.txt") +
            if (tag.startsWith("zh-")) fallbackKeys("chinese-english-fallbacks.txt") else emptySet()
        assertTrue("Remove completed translations from the fallback inventory: ${inventory - untranslated}",
            untranslated.containsAll(inventory))
    }
}
