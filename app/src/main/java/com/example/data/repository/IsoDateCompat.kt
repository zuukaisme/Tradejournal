package com.example.data.repository

private val ISO_TIMESTAMP_REGEX = Regex(
    """^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?(?:\.(\d{1,9}))?(Z|([+-])(\d{2}):?(\d{2}))$""",
    RegexOption.IGNORE_CASE
)

internal fun parseIso8601EpochMillis(raw: String): Long? {
    val trimmed = raw.trim()
    val match = ISO_TIMESTAMP_REGEX.matchEntire(trimmed) ?: return null
    val year = match.groupValues[1].toIntOrNull() ?: return null
    val month = match.groupValues[2].toIntOrNull() ?: return null
    val day = match.groupValues[3].toIntOrNull() ?: return null
    val hour = match.groupValues[4].toIntOrNull() ?: return null
    val minute = match.groupValues[5].toIntOrNull() ?: return null
    val second = match.groupValues[6].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
    val fracRaw = match.groupValues[7]
    val millis = if (fracRaw.isNotEmpty()) {
        fracRaw.padEnd(3, '0').substring(0, 3).toLongOrNull() ?: return null
    } else {
        0L
    }

    if (month !in 1..12 || day !in 1..31 || hour !in 0..23 || minute !in 0..59 || second !in 0..59) {
        return null
    }

    var yr = year.toLong()
    if (month <= 2) yr -= 1L
    val era = if (yr >= 0L) yr / 400L else (yr - 399L) / 400L
    val yoe = yr - era * 400L
    val mAdj = month + if (month > 2) -3 else 9
    val doy = (153L * mAdj + 2L) / 5L + day - 1L
    val doe = yoe * 365L + yoe / 4L - yoe / 100L + doy
    val epochDays = era * 146097L + doe - 719468L
    val baseMillis = (epochDays * 86_400L + hour * 3600L + minute * 60L + second) * 1000L + millis

    val tzSign = match.groupValues[9]
    if (tzSign.isEmpty()) return baseMillis
    val offH = match.groupValues[10].toIntOrNull() ?: return null
    val offM = match.groupValues[11].toIntOrNull() ?: return null
    if (offH > 18 || offM > 59) return null
    val offsetMs = (offH * 60L + offM) * 60_000L
    return if (tzSign == "+") baseMillis - offsetMs else baseMillis + offsetMs
}

internal fun formatUtcIsoDate(epochMillis: Long): String {
    val epochDays = Math.floorDiv(epochMillis, 86_400_000L)
    val z = epochDays + 719468L
    val era = if (z >= 0L) z / 146097L else (z - 146096L) / 146097L
    val doe = z - era * 146097L
    val yoe = (doe - doe / 1460L + doe / 36524L - doe / 146096L) / 365L
    var year = (yoe + era * 400L).toInt()
    val doy = doe - (365L * yoe + yoe / 4L - yoe / 100L)
    val mp = (5L * doy + 2L) / 153L
    val day = (doy - (153L * mp + 2L) / 5L + 1L).toInt()
    val month = (mp + if (mp < 10L) 3L else -9L).toInt()
    if (month <= 2) year += 1
    return buildString(10) {
        append(year.toString().padStart(4, '0')).append('-')
        append(month.toString().padStart(2, '0')).append('-')
        append(day.toString().padStart(2, '0'))
    }
}
