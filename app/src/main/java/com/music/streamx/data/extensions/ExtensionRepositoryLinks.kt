package com.music.streamx.data.extensions

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

internal data class ExtensionRepositoryLink(
    val url: String,
    val name: String,
    val certificates: Set<String>,
)

/** Public repository addresses and signer pins; GitHub credentials stay in RepositoryAccess. */
internal object ExtensionRepositoryLinks {
    private val links = MutableStateFlow<List<ExtensionRepositoryLink>>(emptyList())
    private var initialized = false
    private fun preferences(context: Context) = context.applicationContext
        .getSharedPreferences("extension_repositories", Context.MODE_PRIVATE)

    @Synchronized fun observe(context: Context): StateFlow<List<ExtensionRepositoryLink>> {
        if (!initialized) {
            val array = runCatching { JSONArray(preferences(context).getString("links", "[]")) }.getOrDefault(JSONArray())
            links.value = (0 until array.length()).mapNotNull { index ->
                runCatching {
                    val item = array.getJSONObject(index)
                    val certificates = item.getJSONArray("certificates")
                    ExtensionRepositoryLink(normalizeUrl(item.getString("url")), item.getString("name"),
                        (0 until certificates.length()).map { certificates.getString(it) }.toSet())
                }.getOrNull()
            }
            initialized = true
        }
        return links
    }

    internal fun normalizeUrl(value: String): String {
        val uri = URI(value.trim())
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null &&
            uri.rawQuery == null && uri.fragment == null) { "Enter a public HTTPS repository or index.json link" }
        val path = uri.path.trimEnd('/')
        val parts = path.trim('/').split('/')
        if (uri.host.equals("github.com", true) && parts.size == 2) {
            val repository = parts[1].removeSuffix(".git")
            require(parts[0].matches(Regex("[A-Za-z0-9_.-]+")) && repository.matches(Regex("[A-Za-z0-9_.-]+"))) { "Invalid GitHub repository link" }
            return "https://github.com/${parts[0]}/$repository/releases/download/repo/index.json"
        }
        if (uri.host.equals("github.com", true) && parts.size >= 5 && parts[2] == "blob") {
            return "https://raw.githubusercontent.com/${parts[0]}/${parts[1]}/${parts.drop(3).joinToString("/")}"
        }
        require(path.endsWith(".json", true)) { "Use the repository’s index.json link or its GitHub repository link" }
        return uri.toASCIIString()
    }

    @Synchronized fun add(context: Context, link: ExtensionRepositoryLink) {
        observe(context)
        require(links.value.none { it.url == link.url }) { "This repository has already been added" }
        require(links.value.size < 20) { "You can add up to 20 repositories" }
        require(link.certificates.isNotEmpty()) { "The repository has no signing certificates" }
        save(context, links.value + link)
    }

    @Synchronized fun remove(context: Context, url: String) {
        observe(context)
        save(context, links.value.filterNot { it.url == url })
    }

    private fun save(context: Context, value: List<ExtensionRepositoryLink>) {
        val array = JSONArray(value.map { link -> JSONObject().put("url", link.url).put("name", link.name)
            .put("certificates", JSONArray(link.certificates.sorted())) })
        check(preferences(context).edit().putString("links", array.toString()).commit()) { "Could not save repositories" }
        links.value = value
    }
}
