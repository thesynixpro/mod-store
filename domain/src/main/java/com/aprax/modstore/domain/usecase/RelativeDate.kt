package com.aprax.modstore.domain.usecase

/**
 * Turns a source's published date string into something a person can read.
 *
 * Sources publish several shapes ("2026-11-18T19:06:00", "2026-11-18T12:06:00+00:00",
 * "18 November 2026"). Rather than pull in a date library, this normalises the common ISO
 * shapes and otherwise returns the raw value, so the UI never shows a fabricated date.
 */
object RelativeDate {

    private val ISO_DATE_TIME = Regex(
        "^(\\d{4})-(\\d{2})-(\\d{2})(?:[T ](\\d{2}):(\\d{2})(?::(\\d{2}))?)?"
    )

    /**
     * @param raw ISO-8601-ish date string from the source.
     * @param nowMillis current wall clock, injected so this is testable.
     * @return a short human label, or the trimmed input when it cannot be interpreted.
     */
    fun describe(raw: String?, nowMillis: Long = System.currentTimeMillis()): String? {
        if (raw.isNullOrBlank()) return null
        val parts = parse(raw) ?: return raw.trim()
        val then = toEpochMillis(parts) ?: return raw.trim()
        return describe(then, nowMillis)
    }

    fun describe(epochMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        val delta = nowMillis - epochMillis
        if (delta < 0) return "scheduled"
        val minutes = delta / 60_000
        val hours = minutes / 60
        val days = hours / 24
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "$minutes min ago"
            hours < 24 -> if (hours == 1L) "1 hour ago" else "$hours hours ago"
            days == 1L -> "yesterday"
            days < 7 -> "$days days ago"
            days < 30 -> "${days / 7}w ago"
            days < 365 -> "${days / 30}mo ago"
            else -> "${days / 365}y ago"
        }
    }

    /** ISO-8601 date only, for a stable sortable key. */
    fun isoDate(raw: String?): String? {
        val parts = parse(raw ?: return null) ?: return null
        return "%04d-%02d-%02d".format(parts.year, parts.month, parts.day)
    }

    private data class Parts(
        val year: Int,
        val month: Int,
        val day: Int,
        val hour: Int,
        val minute: Int,
        val second: Int,
    )

    private fun parse(raw: String): Parts? {
        val match = ISO_DATE_TIME.find(raw.trim()) ?: return null
        val (year, month, day) = match.destructured
        val hour = match.groupValues[4].ifBlank { "0" }.toIntOrNull() ?: return null
        val minute = match.groupValues[5].ifBlank { "0" }.toIntOrNull() ?: return null
        val second = match.groupValues[6].ifBlank { "0" }.toIntOrNull() ?: 0
        return Parts(
            year = year.toIntOrNull() ?: return null,
            month = month.toIntOrNull() ?: return null,
            day = day.toIntOrNull() ?: return null,
            hour = hour,
            minute = minute,
            second = second,
        )
    }

    /**
     * Days-from-civil (Howard Hinnant's algorithm), used instead of java.time so the
     * function behaves identically on API 24 without desugaring.
     */
    private fun toEpochMillis(parts: Parts): Long? {
        if (parts.month !in 1..12 || parts.day !in 1..31) return null
        val adjustedYear = if (parts.month <= 2) parts.year - 1 else parts.year
        val era = (if (adjustedYear >= 0) adjustedYear else adjustedYear - 399) / 400
        val yearOfEra = adjustedYear - era * 400
        val dayOfYear = (153 * (parts.month + (if (parts.month > 2) -3 else 9)) + 2) / 5 + parts.day - 1
        val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
        val days = era * 146097L + dayOfEra - 719468L
        val seconds = days * 86_400L +
            parts.hour * 3_600L + parts.minute * 60L + parts.second
        return seconds * 1000L
    }
}
