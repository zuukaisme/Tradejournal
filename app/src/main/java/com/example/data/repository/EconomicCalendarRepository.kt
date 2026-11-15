package com.example.data.repository

import com.example.domain.model.EconomicCalendarEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

class EconomicCalendarRepository {
    private val client = OkHttpClient()

    suspend fun fetchEvents(): Result<List<EconomicCalendarEvent>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("https://nfs.faireconomy.media/ff_calendar_thisweek.json")
                .header("Accept", "application/json")
                .header("User-Agent", "TradePulse-Android")
                .build()

            val body = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Calendar HTTP ${response.code}")
                }
                response.body?.string() ?: throw IllegalStateException("Empty calendar response")
            }

            val array = JSONArray(body)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val title = item.optString("title").trim()
                    val currency = item.optString("country").trim().uppercase()
                    val date = item.optString("date").trim()
                    val timestamp = parseIso8601EpochMillis(date) ?: continue
                    if (title.isBlank() || currency.isBlank()) continue

                    add(
                        EconomicCalendarEvent(
                            id = "${currency}_${timestamp}_${title}",
                            title = title,
                            currency = currency,
                            timestamp = timestamp,
                            impact = item.optString("impact", "Low").ifBlank { "Low" },
                            actual = item.optString("actual").takeUnless { it.isBlank() || it == "null" },
                            forecast = item.optString("forecast").takeUnless { it.isBlank() || it == "null" },
                            previous = item.optString("previous").takeUnless { it.isBlank() || it == "null" }
                        )
                    )
                }
            }.sortedBy { it.timestamp }
        }
    }
}
