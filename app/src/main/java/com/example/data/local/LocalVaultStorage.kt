package com.example.data.local

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

object LocalVaultStorage {

    private const val PREFS_NAME = "tradepulse_vault_prefs"
    private const val KEY_AUTO_VAULT_ENABLED = "auto_vault_enabled"
    private const val KEY_OWN_MEDIA_URI = "own_media_uri"
    private const val KEY_LAST_SAVED_AT = "last_saved_at"
    private const val KEY_LAST_LOCATION = "last_location"
    private const val KEY_RESTORE_DISMISSED = "restore_prompt_dismissed"

    private const val VAULT_FILE_NAME = "tradepulse_vault.json"
    private const val MIRROR_FILE_NAME = "tradepulse_vault_mirror.json"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isAutoVaultEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_VAULT_ENABLED, true)

    fun setAutoVaultEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_VAULT_ENABLED, enabled).apply()
    }

    fun getLastSavedAt(context: Context): Long =
        prefs(context).getLong(KEY_LAST_SAVED_AT, 0L)

    fun getLastLocation(context: Context): String =
        prefs(context).getString(KEY_LAST_LOCATION, "") ?: ""

    fun isRestorePromptDismissed(context: Context): Boolean =
        prefs(context).getBoolean(KEY_RESTORE_DISMISSED, false)

    fun setRestorePromptDismissed(context: Context, dismissed: Boolean) {
        prefs(context).edit().putBoolean(KEY_RESTORE_DISMISSED, dismissed).apply()
    }

    fun saveVaultSnapshot(context: Context, json: String): Result<String> {
        val appContext = context.applicationContext
        // Always keep an app-private mirror too
        runCatching {
            File(appContext.filesDir, MIRROR_FILE_NAME).writeText(json, Charsets.UTF_8)
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val now = System.currentTimeMillis()
            val location = "App Private Vault (use Save File for external backup)"
            prefs(appContext).edit()
                .putLong(KEY_LAST_SAVED_AT, now)
                .putString(KEY_LAST_LOCATION, location)
                .apply()
            return Result.success(location)
        }

        return runCatching {
            val resolver = appContext.contentResolver
            val existingUriStr = prefs(appContext).getString(KEY_OWN_MEDIA_URI, null)
            var savedLocation = "Downloads/TradePulse/$VAULT_FILE_NAME"

            if (!existingUriStr.isNullOrBlank()) {
                val existingUri = Uri.parse(existingUriStr)
                val overwritten = runCatching {
                    resolver.openOutputStream(existingUri, "wt")?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                        out.flush()
                    } != null
                }.getOrDefault(false)

                if (overwritten) {
                    val now = System.currentTimeMillis()
                    prefs(appContext).edit()
                        .putLong(KEY_LAST_SAVED_AT, now)
                        .putString(KEY_LAST_LOCATION, savedLocation)
                        .apply()
                    return@runCatching savedLocation
                }
            }

            val relPath = Environment.DIRECTORY_DOWNLOADS + "/TradePulse"
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, VAULT_FILE_NAME)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.RELATIVE_PATH, relPath)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

            val insertedUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Could not create vault file in Downloads/TradePulse.")

            try {
                resolver.openOutputStream(insertedUri, "wt")?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                    out.flush()
                } ?: error("Failed to write vault stream.")

                val publishValues = ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                }
                resolver.update(insertedUri, publishValues, null, null)
            } catch (t: Throwable) {
                runCatching { resolver.delete(insertedUri, null, null) }
                throw t
            }

            // Query actual display name in case OS suffixed e.g. tradepulse_vault (1).json
            val resolvedName = runCatching {
                resolver.query(
                    insertedUri,
                    arrayOf(MediaStore.Downloads.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(MediaStore.Downloads.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                }
            }.getOrNull() ?: VAULT_FILE_NAME

            savedLocation = "Downloads/TradePulse/$resolvedName"
            val now = System.currentTimeMillis()
            prefs(appContext).edit()
                .putString(KEY_OWN_MEDIA_URI, insertedUri.toString())
                .putLong(KEY_LAST_SAVED_AT, now)
                .putString(KEY_LAST_LOCATION, savedLocation)
                .apply()

            savedLocation
        }
    }

    fun tryFindReadableVaultJson(context: Context): String? {
        val appContext = context.applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val found = runCatching {
                val resolver = appContext.contentResolver
                val projection = arrayOf(
                    MediaStore.Downloads._ID,
                    MediaStore.Downloads.DISPLAY_NAME
                )
                val selection = "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?"
                val selectionArgs = arrayOf("tradepulse_vault%.json")
                val sortOrder = "${MediaStore.Downloads.DATE_MODIFIED} DESC"

                resolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val uri = ContentUris.withAppendedId(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            id
                        )
                        val content = runCatching {
                            resolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        }.getOrNull()
                        if (!content.isNullOrBlank() && content.trimStart().startsWith("{")) {
                            return@use content
                        }
                    }
                    null
                }
            }.getOrNull()

            if (!found.isNullOrBlank()) return found
        }

        val mirror = File(appContext.filesDir, MIRROR_FILE_NAME)
        if (mirror.exists() && mirror.length() > 2L) {
            return runCatching { mirror.readText(Charsets.UTF_8) }.getOrNull()
        }
        return null
    }

    fun writeUriText(context: Context, uri: Uri, json: String): Result<Unit> = runCatching {
        context.applicationContext.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            out.write(json.toByteArray(Charsets.UTF_8))
            out.flush()
        } ?: error("Unable to open destination file for writing.")
    }

    fun readUriText(context: Context, uri: Uri): Result<String> = runCatching {
        context.applicationContext.contentResolver.openInputStream(uri)?.use { input ->
            input.bufferedReader(Charsets.UTF_8).readText()
        } ?: error("Unable to open selected Vault file.")
    }
}
