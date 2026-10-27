package com.music.streamx

import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File

/**
 * Carries data over from when the app was called BitChord.
 *
 * Settings, sign-ins and saved queues live in SharedPreferences files whose
 * names began `bitchord_`; they move to `streamx_` before anything opens them,
 * which is why this runs from [StreamXApplication.attachBaseContext].
 *
 * Plain files are renamed. Encrypted ones cannot be: their entries are sealed
 * with the file's name as associated data, so a renamed file opens but nothing
 * in it decrypts. Those are read under the old name and copied into a new store.
 */
internal object LegacyRename {
    private const val OLD = "bitchord_"
    private const val NEW = "streamx_"

    /** Stores opened through [com.music.streamx.auth.EncryptedPrefs]. */
    private val ENCRYPTED = listOf("auth", "sources")

    /** Written once the move is done, so later launches skip the Keystore work. */
    private const val DONE = "legacy-rename-v2"

    /** The library's own keyset entries inside an encrypted file. */
    private const val KEYSET_PREFIX = "__androidx_security_crypto_encrypted_prefs_"

    /** Renames the plain files. From attachBaseContext, before anything has opened them. */
    fun migrate(context: Context) {
        if (File(context.noBackupFilesDir, DONE).exists()) return
        val prefs = File(context.applicationInfo.dataDir, "shared_prefs")
        val encrypted = ENCRYPTED.map { "$OLD$it.xml" }.toSet()
        prefs.listFiles { file -> file.name.startsWith(OLD) && file.name.endsWith(".xml") && file.name !in encrypted }
            .orEmpty().forEach { old ->
                val renamed = File(prefs, NEW + old.name.removePrefix(OLD))
                // A file already under the new name is newer; the old one is left as it was.
                if (!renamed.exists()) old.renameTo(renamed)
            }
    }

    /**
     * Copies the encrypted stores. From the start of onCreate: the encryption
     * library needs an application context, which attachBaseContext lacks.
     */
    fun migrateEncrypted(context: Context) {
        val marker = File(context.noBackupFilesDir, DONE)
        if (marker.exists()) return
        val prefs = File(context.applicationInfo.dataDir, "shared_prefs")
        // Each store is tried on its own: one that fails must not stop the next.
        val copied = ENCRYPTED.map { copyEncrypted(context, prefs, it) }.all { it }
        // The playback channel was renamed too; drop the old one so Settings does not list it twice.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { context.getSystemService(NotificationManager::class.java)?.deleteNotificationChannel(OLD + "playback") }
        }
        // A copy that failed is tried again next launch rather than given up on.
        if (copied) runCatching { marker.createNewFile() }
    }

    private fun copyEncrypted(context: Context, prefs: File, store: String): Boolean = try {
        val oldName = OLD + store
        val newName = NEW + store
        val oldFile = File(prefs, "$oldName.xml")
        val newFile = File(prefs, "$newName.xml")
        // An earlier build renamed these files outright, which left them sealed
        // to the old name. Give such a file its old name back and copy it properly.
        if (!oldFile.exists() && newFile.exists() && !readable(context, newName)) newFile.renameTo(oldFile)
        if (oldFile.exists()) {
            val source = readAll(context, oldName)
            val edit = open(context, newName).edit()
            for ((key, value) in source) {
                when (value) {
                    is String -> edit.putString(key, value)
                    is Boolean -> edit.putBoolean(key, value)
                    is Int -> edit.putInt(key, value)
                    is Long -> edit.putLong(key, value)
                    is Float -> edit.putFloat(key, value)
                    is Set<*> -> edit.putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
            check(edit.commit()) { "could not write $newName" }
            context.deleteSharedPreferences(oldName)
        }
        true
    } catch (e: Throwable) {
        Log.w("StreamX", "Could not carry $store over from BitChord", e)
        false
    }

    /**
     * Everything in an encrypted store that still decrypts.
     *
     * A file the earlier build renamed may also hold entries written under the
     * new name, which never decrypt here, and one such entry makes `getAll`
     * throw for the whole file. So when that happens each entry is tried on its
     * own, beside the keysets, and only the ones that fail are left behind.
     */
    private fun readAll(context: Context, name: String): Map<String, *> {
        runCatching { return open(context, name).all }
        val raw = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val snapshot = raw.all.mapNotNull { (key, value) -> (value as? String)?.let { key to it } }.toMap()
        val (keysets, entries) = snapshot.entries.partition { it.key.startsWith(KEYSET_PREFIX) }
        val found = HashMap<String, Any?>()
        for (entry in entries) {
            raw.edit().clear().apply { keysets.forEach { putString(it.key, it.value) } }.putString(entry.key, entry.value).commit()
            runCatching { found.putAll(open(context, name).all) }
        }
        return found
    }

    private fun readable(context: Context, name: String): Boolean = runCatching { open(context, name).all }.isSuccess

    private fun open(context: Context, name: String): SharedPreferences = EncryptedSharedPreferences.create(
        context,
        name,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
}
