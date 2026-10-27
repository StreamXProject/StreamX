package com.music.streamx.data.extensions

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ExtensionRepositoryTest {
    private fun entry(abi: String="arm64-v8a",version: Long=12)=JSONObject()
        .put("name","Apple Music").put("packageName","com.music.streamx.skins.applemusic")
        .put("versionCode",version).put("versionName","1.5.2").put("type","skin")
        .put("abis",JSONArray(listOf(abi))).put("minSdk",26)
        .put("apkUrl","https://github.com/StreamXProject/Skins/releases/download/build-1/test.apk")
        .put("sha256","a".repeat(64)).put("certificateSha256",ExtensionRepository.SIGNER).put("size",1234)
    private fun parse(vararg entries: JSONObject)=ExtensionRepository.parseIndex(
        JSONObject().put("schemaVersion",1).put("extensions",JSONArray(entries.toList())).toString(),"skin",listOf("arm64-v8a","armeabi-v7a"),36)
    @Test fun optionalExtensionIconsRemainBackwardsCompatible() {
        assertNull(parse(entry()).single().iconUrl)
        val url = "https://extensions.example/icons/comic.svg"
        assertEquals(url, parse(entry().put("iconUrl", url)).single().iconUrl)
    }
    @Test fun unsafeIconsDoNotBreakTheCatalogue() {
        listOf("http://example.com/icon.png", "file:///tmp/icon.png", "https://user:password@example.com/icon.png", "not a URL").forEach { url ->
            assertNull(parse(entry().put("iconUrl", url)).single().iconUrl)
        }
    }
    @Test fun choosesNewestCompatibleAbi() {
        assertEquals(13L,parse(entry(version=12),entry("x86_64",14),entry("arm64-v8a",13)).single().versionCode)
    }
    @Test fun prefersDeviceAbiOverUniversal() {
        val universal=entry().put("abis",JSONArray(listOf("arm64-v8a","x86_64")))
        assertEquals(listOf("arm64-v8a"),parse(universal,entry()).single().abis)
    }
    @Test fun hidesIncompatibleMinimumSdk() {assertTrue(parse(entry().put("minSdk",40)).isEmpty())}
    @Test fun rejectsForeignSigner() {assertThrows(IllegalStateException::class.java){parse(entry().put("certificateSha256","b".repeat(64)))}}
    @Test fun rejectsNonRepositoryDownload() {assertThrows(IllegalStateException::class.java){parse(entry().put("apkUrl","https://example.com/extension.apk"))}}
    @Test fun rejectsCleartextDownload() {assertThrows(IllegalStateException::class.java){parse(entry().put("apkUrl","http://github.com/StreamXProject/Skins/releases/download/build-1/test.apk"))}}
    @Test fun rejectsMalformedChecksum() {assertThrows(IllegalStateException::class.java){parse(entry().put("sha256","broken"))}}
    @Test fun rejectsOversizedDownload() {assertThrows(IllegalStateException::class.java){parse(entry().put("size",Long.MAX_VALUE))}}
    @Test fun acceptsSkinFromUnifiedRepository() {
        assertEquals(1, parse(entry().put("apkUrl", "https://github.com/StreamXProject/Plugins/releases/download/build-1/test.apk")).size)
    }
    @Test fun bothTypesUseTheSameManifestEndpoint() {
        assertEquals(ExtensionRepository.indexUrl("plugin"), ExtensionRepository.indexUrl("skin"))
        assertEquals("https://streamxproject.github.io/StreamX-Releases/extensions/index.json", ExtensionRepository.indexUrl("skin"))
    }
    @Test fun filtersBothTypesFromOneManifest() {
        val skin = entry().put("apkUrl", "https://github.com/StreamXProject/StreamX-Releases/releases/download/extensions-v1/skin.apk")
        val plugin = entry().put("type", "plugin").put("packageName", "com.music.streamx.extensions.applemusic")
            .put("apkUrl", "https://github.com/StreamXProject/StreamX-Releases/releases/download/extensions-v1/plugin.apk")
        val index = JSONObject().put("schemaVersion", 1).put("extensions", JSONArray(listOf(skin, plugin))).toString()
        assertEquals(listOf("skin"), ExtensionRepository.parseIndex(index, "skin", listOf("arm64-v8a"), 36).map { it.type })
        assertEquals(listOf("plugin"), ExtensionRepository.parseIndex(index, "plugin", listOf("arm64-v8a"), 36).map { it.type })
        plugin.put("apkUrl", "https://github.com/StreamXProject/Skins/releases/download/build-1/plugin.apk")
        assertThrows(IllegalStateException::class.java) {
            ExtensionRepository.parseIndex(JSONObject().put("schemaVersion", 1).put("extensions", JSONArray(listOf(plugin))).toString(), "plugin", listOf("arm64-v8a"), 36)
        }
    }

    private val contributor = ExtensionRepositoryLink("https://contributor.example/index.json", "Community", setOf("b".repeat(64)))
    private fun communityEntry() = entry().put("packageName", "org.contributor.music.skin")
        .put("apkUrl", "https://cdn.contributor.example/skin.apk").put("certificateSha256", "b".repeat(64))
    private fun communityParse(item: JSONObject, source: ExtensionRepositoryLink = contributor) = ExtensionRepository.parseIndex(
        JSONObject().put("schemaVersion", 1).put("extensions", JSONArray(listOf(item))).toString(),
        "skin", listOf("arm64-v8a"), 36, source)

    @Test fun acceptsContributorPackageAndPreservesRepositoryIdentity() {
        val result = communityParse(communityEntry()).single()
        assertEquals("org.contributor.music.skin", result.packageName)
        assertEquals(contributor.url, result.repositoryUrl)
        assertEquals(contributor.name, result.repositoryName)
        assertEquals("b".repeat(64), result.certificateSha256)
    }
    @Test fun rejectsContributorSignerChanges() {
        assertThrows(IllegalStateException::class.java) {
            communityParse(communityEntry().put("certificateSha256", "c".repeat(64)))
        }
    }
    @Test fun rejectsContributorCleartextAndCredentials() {
        listOf("http://cdn.contributor.example/skin.apk", "https://user:pass@cdn.contributor.example/skin.apk").forEach { url ->
            assertThrows(IllegalStateException::class.java) { communityParse(communityEntry().put("apkUrl", url)) }
        }
    }
    @Test fun rejectsInvalidContributorPackage() {
        assertThrows(IllegalStateException::class.java) { communityParse(communityEntry().put("packageName", "../../app")) }
    }
    @Test fun acceptsNewSignerOnlyDuringInitialRepositoryInspection() {
        assertEquals(1, communityParse(communityEntry(), contributor.copy(certificates = emptySet())).size)
    }
    @Test fun normalizesGitHubRepositoryLinks() {
        assertEquals("https://github.com/contributor/extensions/releases/download/repo/index.json",
            ExtensionRepositoryLinks.normalizeUrl(" https://github.com/contributor/extensions.git/ "))
        assertEquals("https://raw.githubusercontent.com/contributor/extensions/main/index.json",
            ExtensionRepositoryLinks.normalizeUrl("https://github.com/contributor/extensions/blob/main/index.json"))
    }
    @Test fun preservesDirectIndexLinks() {
        assertEquals(contributor.url, ExtensionRepositoryLinks.normalizeUrl(contributor.url))
    }
    @Test fun rejectsUnsafeAndNonIndexLinks() {
        listOf("http://example.com/index.json", "https://user:password@example.com/index.json",
            "https://example.com/index.json?token=secret", "https://example.com/", "file:///tmp/index.json").forEach { url ->
            assertThrows(IllegalArgumentException::class.java) { ExtensionRepositoryLinks.normalizeUrl(url) }
        }
    }
}
