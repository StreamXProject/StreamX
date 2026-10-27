package com.music.streamx.data.extensions

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Per-device GitHub access; never falls back to plaintext or ships in a build. */
internal object RepositoryAccess {
    private var store: SharedPreferences?=null
    @Synchronized private fun preferences(context: Context): SharedPreferences = store ?: EncryptedSharedPreferences.create(
        context.applicationContext,"streamx_extension_repository_access",
        MasterKey.Builder(context.applicationContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    ).also{store=it}
    fun token(context: Context): String=preferences(context).getString("github_token","").orEmpty()
    fun save(context: Context,value: String) {
        val token=value.trim()
        require(token.isEmpty() || token.length in 20..512 && token.none{it.isWhitespace()}){"Enter a valid GitHub access token"}
        check(preferences(context).edit().apply{if(token.isBlank())remove("github_token")else putString("github_token",token)}.commit()) {"Could not save repository access"}
    }
}
