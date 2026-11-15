package com.example.domain.usecase

import com.example.domain.model.EconomicCalendarEvent
import com.example.domain.model.NewsEventType
import com.example.domain.model.NewsRelease
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object GoldNewsFeedMapper {

    private val goldKeywordRegex = Regex(
        """(payroll|nfp|cpi|inflation|fomc|federal|powell|rate\s+decision|pce|ppi|gdp|retail\s+sales|unemployment|jobless|ism|pmi|gold)""",
        RegexOption.IGNORE_CASE
    )

    fun filterGoldMovingEvents(
        events: List<EconomicCalendarEvent>,
        nowMillis: Long = System.currentTimeMillis()
    ): List<EconomicCalendarEvent> {
        val relevant = events.filter { event ->
            val isUsd = event.currency.equals("USD", ignoreCase = true)
            val isHighOrMedium = event.impact.equals("High", ignoreCase = true) ||
                event.impact.equals("Medium", ignoreCase = true)
            val matchesKeyword = goldKeywordRegex.containsMatchIn(event.title)
            (isUsd && (isHighOrMedium || matchesKeyword)) ||
                (event.impact.equals("High", ignoreCase = true) && matchesKeyword)
        }
        val upcoming = relevant.filter { it.timestamp >= nowMillis - 30 * 60_000L }
            .sortedBy { it.timestamp }
        val past = relevant.filter { it.timestamp < nowMillis - 30 * 60_000L }
            .sortedByDescending { it.timestamp }
        return upcoming + past
    }

    fun toNewsReleases(
        events: List<EconomicCalendarEvent>,
        nowMillis: Long = System.currentTimeMillis()
    ): List<NewsRelease> {
        val utcFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val releases = mutableListOf<NewsRelease>()
        for (event in events) {
            if (!event.currency.equals("USD", ignoreCase = true)) continue
            val title = event.title
            val eventType = when {
                title.contains("non-farm", ignoreCase = true) ||
                    title.contains("nonfarm", ignoreCase = true) ||
                    title.contains("payroll", ignoreCase = true) -> NewsEventType.NFP
                title.contains("CPI", ignoreCase = true) &&
                    !title.contains("Core", ignoreCase = true) -> NewsEventType.CPI
                else -> null
            } ?: continue

            val previousNum = parseNumericValue(event.previous) ?: 0.0
            val actualNum = parseNumericValue(event.actual)
            val surprise = if (actualNum != null) actualNum - previousNum else null
            val unit = inferUnit(event.actual ?: event.previous ?: event.forecast, eventType)
            val dateString = utcFormat.format(Date(event.timestamp))
            val isUpcoming = actualNum == null && event.timestamp >= nowMillis - 60 * 60_000L

            releases.add(
                NewsRelease(
                    eventType = eventType,
                    dateString = dateString,
                    timestamp = event.timestamp,
                    forecast = parseNumericValue(event.forecast) ?: 0.0,
                    previous = previousNum,
                    actual = actualNum,
                    surprise = surprise,
                    unit = unit,
                    isUpcoming = isUpcoming
                )
            )
        }
        return releases.distinctBy { it.eventType to it.timestamp }
    }

    internal fun parseNumericValue(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.trim()
            .replace(",", "")
            .replace("%", "")
            .replace("K", "", ignoreCase = true)
            .replace("M", "", ignoreCase = true)
            .replace("B", "", ignoreCase = true)
            .trim()
        return cleaned.toDoubleOrNull()
    }

    private fun inferUnit(raw: String?, eventType: NewsEventType): String {
        val text = raw.orEmpty()
        return when {
            text.contains("%") -> "%"
            text.contains("K", ignoreCase = true) -> "K"
            text.contains("M", ignoreCase = true) -> "M"
            eventType == NewsEventType.NFP -> "K"
            else -> "%"
        }
    }
}
