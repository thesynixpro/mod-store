package com.aprax.modstore.domain.usecase

/**
 * Pure helpers for reasoning about version strings.
 *
 * These are deliberately dependency-free and unit-tested, because "is this an upgrade?"
 * is shown to the user on the details screen and must not be guessed.
 */
object VersionCompare {

    /**
     * Compares two version strings segment by segment.
     *
     * Returns a negative number when [left] is older, zero when equal, positive when newer.
     * Numeric segments compare numerically ("9.10" > "9.9"), and a stable release outranks
     * a pre-release of the same base ("1.2" > "1.2-beta"). Unparseable input falls back to
     * a lexical comparison so the function is total and never throws.
     */
    fun compare(left: String?, right: String?): Int {
        if (left.isNullOrBlank() && right.isNullOrBlank()) return 0
        if (left.isNullOrBlank()) return -1
        if (right.isNullOrBlank()) return 1

        val l = parse(left)
        val r = parse(right)

        val max = maxOf(l.numbers.size, r.numbers.size)
        for (index in 0 until max) {
            val a = l.numbers.getOrElse(index) { 0 }
            val b = r.numbers.getOrElse(index) { 0 }
            if (a != b) return if (a < b) -1 else 1
        }

        // Same numeric base: a release is newer than its own pre-release.
        return when {
            l.isPreRelease && !r.isPreRelease -> -1
            !l.isPreRelease && r.isPreRelease -> 1
            else -> left.compareTo(right)
        }
    }

    /** True when [candidate] is a strictly newer version than [current]. */
    fun isUpgrade(candidate: String?, current: String?): Boolean =
        compare(candidate, current) > 0

    /** True when [candidate] is a strictly older version than [current]. */
    fun isDowngrade(candidate: String?, current: String?): Boolean =
        compare(candidate, current) < 0

    private data class Parsed(val numbers: List<Long>, val isPreRelease: Boolean)

    private val PRE_RELEASE_MARKERS = listOf("alpha", "beta", "rc", "pre", "dev", "snapshot")

    private fun parse(raw: String): Parsed {
        val lower = raw.lowercase().trim()
        val isPreRelease = PRE_RELEASE_MARKERS.any { lower.contains(it) }
        val numbers = Regex("\\d+")
            .findAll(lower)
            .mapNotNull { it.value.toLongOrNull() }
            .toList()
        return Parsed(numbers, isPreRelease)
    }
}

/**
 * Formats a byte count the way a marketplace does.
 *
 * Returns null for unknown sizes so callers render "size unknown" instead of a fake "0 B".
 */
object ByteFormat {

    private const val KB = 1024.0
    private const val MB = KB * 1024
    private const val GB = MB * 1024

    fun format(bytes: Long?): String? {
        if (bytes == null || bytes <= 0L) return null
        val value = bytes.toDouble()
        val (unit, divisor) = when {
            value >= GB -> "GB" to GB
            value >= MB -> "MB" to MB
            value >= KB -> "KB" to KB
            else -> "B" to 1.0
        }
        val scaled = value / divisor
        // Whole numbers read better as "80 MB" than "80.0 MB".
        return if (scaled >= 10 || scaled % 1.0 == 0.0) {
            "${scaled.roundToIntIfClose()} $unit"
        } else {
            "${String.format("%.1f", scaled)} $unit"
        }
    }

    /**
     * Human-readable description of a byte count, never null.
     *
     * Download rows render size next to other facts, so an unknown size reads as "Unknown"
     * instead of disappearing or faking "0 B".
     */
    fun describeBytes(bytes: Long?): String =
        format(bytes) ?: "Unknown"

    /**
     * Parses the loose size strings the source sites publish, e.g. "80M", "1.2 GB",
     * "45.6MB". Returns null when the string is not an unambiguous size, because guessing
     * would put a wrong number in front of the user.
     */
    fun parseSourceSize(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val text = raw.trim().uppercase().replace(" ", "")
        val match = Regex("^([0-9]+(?:\\.[0-9]+)?)(B|KB|K|MB|M|GB|G|TB|T)$").find(text)
            ?: return null
        val amount = match.groupValues[1].toDoubleOrNull() ?: return null
        val multiplier = when (match.groupValues[2]) {
            "B" -> 1.0
            "K", "KB" -> KB
            "M", "MB" -> MB
            "G", "GB" -> GB
            "T", "TB" -> GB * 1024
            else -> return null
        }
        return (amount * multiplier).toLong()
    }

    private fun Double.roundToIntIfClose(): String {
        val rounded = kotlin.math.round(this)
        return if (kotlin.math.abs(this - rounded) < 0.05) rounded.toLong().toString()
        else String.format("%.1f", this)
    }
}
