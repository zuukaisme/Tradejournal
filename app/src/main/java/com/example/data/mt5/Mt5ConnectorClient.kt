package com.example.data.mt5

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLEncoder
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class Mt5AccountInfo(
    val login: String,
    val server: String,
    val company: String,
    val currency: String,
    val terminalConnected: Boolean
)

data class Mt5Symbol(
    val name: String,
    val description: String,
    val path: String,
    val visible: Boolean,
    val tradeMode: Int
)

class Mt5ConnectorClient(
    baseUrl: String,
    private val token: String,
    private val http: OkHttpClient = OkHttpClient.Builder().build()
) {
    private val baseUrl = baseUrl.trim().trimEnd('/')

    init {
        require(this.baseUrl.startsWith("http://") || this.baseUrl.startsWith("https://")) {
            "Enter a valid connector URL beginning with http:// or https://."
        }
        require(token.isNotBlank()) { "Connector access token is required." }
    }

    fun health() = getJson("/health")
    fun account(): Mt5AccountInfo {
        val json = getJson("/account")
        return Mt5AccountInfo(
            login = json.optString("login"),
            server = json.optString("server"),
            company = json.optString("company"),
            currency = json.optString("currency"),
            terminalConnected = json.optBoolean("terminal_connected")
        )
    }

    fun symbols(): List<Mt5Symbol> {
        val array = getJsonArray("/symbols")
        return (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let {
                Mt5Symbol(
                    name = it.optString("name"),
                    description = it.optString("description"),
                    path = it.optString("path"),
                    visible = it.optBoolean("visible"),
                    tradeMode = it.optInt("trade_mode")
                )
            }
        }.filter { it.name.isNotBlank() }
    }

    fun downloadRatesToFile(
        symbol: String,
        timeframe: String,
        from: String,
        to: String,
        destination: File,
        onProgress: (Long) -> Unit = {}
    ): File {
        val query = listOf(
            "symbol" to symbol,
            "timeframe" to timeframe,
            "from" to from,
            "to" to to
        ).joinToString("&") { "${it.first}=${URLEncoder.encode(it.second, "UTF-8")}" }
        val request = Request.Builder()
            .url("$baseUrl/rates.csv?$query")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val detail = response.body?.string().orEmpty()
                throw IOException(detail.ifBlank { "Connector request failed (HTTP ${response.code})." })
            }
            val body = response.body ?: throw IOException("Connector returned an empty response.")
            destination.parentFile?.mkdirs()
            var totalBytes = 0L
            body.byteStream().use { input ->
                FileOutputStream(destination).buffered(65_536).use { output ->
                    val buffer = ByteArray(65_536)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        totalBytes += count
                        onProgress(totalBytes)
                    }
                }
            }
            if (totalBytes == 0L) {
                destination.delete()
                throw IOException("MT5 returned no candle data for this symbol, timeframe and date range.")
            }
            return destination
        }
    }

    private fun getJsonArray(path: String): org.json.JSONArray {
        val request = Request.Builder()
            .url("$baseUrl$path")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val detail = response.body?.string().orEmpty()
                throw IOException(detail.ifBlank { "Connector request failed (HTTP ${response.code})." })
            }
            return org.json.JSONArray(response.body?.string().orEmpty())
        }
    }

    private fun getJson(path: String): org.json.JSONObject {
        val request = Request.Builder()
            .url("$baseUrl$path")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val detail = response.body?.string().orEmpty()
                throw IOException(detail.ifBlank { "Connector request failed (HTTP ${response.code})." })
            }
            return org.json.JSONObject(response.body?.string().orEmpty())
        }
    }
}

class Mt5ConnectionStore(context: Context) {
    private val preferences = context.getSharedPreferences("mt5_connector_secure", Context.MODE_PRIVATE)
    private val alias = "tradepulse_mt5_connector_key"

    fun save(url: String, token: String) {
        val bytes = token.toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(bytes)
        preferences.edit()
            .putString("url", url.trim())
            .putString("token_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("token_data", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    fun load(): Pair<String, String> {
        val url = preferences.getString("url", "").orEmpty()
        val iv = preferences.getString("token_iv", null)
        val data = preferences.getString("token_data", null)
        if (iv == null || data == null) return url to ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
        )
        val token = cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)).toString(Charsets.UTF_8)
        return url to token
    }

    fun clear() {
        preferences.edit().clear().apply()
        runCatching {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            keyStore.deleteEntry(alias)
        }
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}
