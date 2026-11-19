package com.aprax.modstore.data.source

import com.aprax.modstore.domain.model.SourceId
import com.aprax.modstore.domain.model.SourceInfo
import org.json.JSONArray
import org.json.JSONObject

/**
 * Small, total helpers for reading third-party JSON.
 *
 * Every accessor returns null instead of throwing, because the source sites are not ours
 * to control: a renamed field must degrade one listing, not crash a screen.
 */
internal object Json {

    fun objectOrNull(parent: JSONObject?, key: String): JSONObject? =
        parent?.optJSONObject(key)

    fun arrayOrNull(parent: JSONObject?, key: String): JSONArray? =
        parent?.optJSONArray(key)

    fun string(parent: JSONObject?, key: String): String? =
        parent?.optString(key)?.takeIf { it.isNotBlank() && it != "" }

    /** Reads a nested value by path, e.g. `string(post, "title", "rendered")`. */
    fun nestedString(root: JSONObject?, vararg path: String): String? {
        if (path.isEmpty()) return null
        var current: JSONObject? = root
        for (index in 0 until path.size - 1) {
            current = current?.optJSONObject(path[index]) ?: return null
        }
        return string(current, path.last())
    }

    /** First element of an array, or null. */
    fun first(array: JSONArray?): JSONObject? =
        if (array != null && array.length() > 0) array.optJSONObject(0) else null

    fun long(parent: JSONObject?, key: String): Long? =
        parent?.let { if (it.has(key) && !it.isNull(key)) it.optLong(key) else null }

    fun int(parent: JSONObject?, key: String): Int? =
        parent?.let { if (it.has(key) && !it.isNull(key)) it.optInt(key) else null }

    fun stringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val value = array.optString(index)
                if (value.isNotBlank()) add(value)
            }
        }
    }
}

/**
 * Shared text cleanup for scraped content.
 *
 * Source pages carry HTML entities, tracking cruft and occasional marketing noise. These
 * helpers reduce a fragment to plain prose without inventing anything.
 */
internal object TextCleanup {

    private val TAG = Regex("<[^>]+>")
    private val WHITESPACE = Regex("[\\t\\r\\n ]+")

    /** Removes markup and collapses whitespace, leaving readable sentences. */
    fun stripHtml(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val withoutTags = TAG.replace(raw, " ")
        val decoded = decodeEntities(withoutTags)
        val collapsed = WHITESPACE.replace(decoded, " ").trim()
        return collapsed.takeIf { it.isNotBlank() }
    }

    /** Minimal entity table for the characters the sources actually emit. */
    fun decodeEntities(raw: String): String = raw
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#039;", "'")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&hellip;", "…")
        .replace("&mdash;", "—")
        .replace("&ndash;", "–")
        .replace("&rsquo;", "'")
        .replace("&lsquo;", "'")
        .replace("&ldquo;", "\"")
        .replace("&rdquo;", "\"")
        .replace(Regex("&#(\\d+);")) { match ->
            match.groupValues[1].toIntOrNull()
                ?.let { codePoint -> String(Character.toChars(codePoint)) }
                ?: match.value
        }
        .replace(Regex("&#x([0-9a-fA-F]+);")) { match ->
            match.groupValues[1].toIntOrNull(16)
                ?.let { codePoint -> String(Character.toChars(codePoint)) }
                ?: match.value
        }

    /**
     * Shortens a description to a card-sized teaser, cutting on a word boundary and never
     * adding an ellipsis to text that was already short enough.
     */
    fun summarise(raw: String?, maxChars: Int = 160): String? {
        val text = stripHtml(raw) ?: return null
        if (text.length <= maxChars) return text
        val cut = text.take(maxChars)
        val lastSpace = cut.lastIndexOf(' ')
        val body = if (lastSpace > maxChars / 2) cut.take(lastSpace) else cut
        return "${body.trimEnd(' ', ',', '.', ';', ':')}…"
    }

    /** Removes the marketing suffix source titles carry, keeping the app name. */
    fun cleanTitle(raw: String?): String? {
        val text = stripHtml(raw) ?: return null
        val patterns = listOf(
            Regex("\\s*[-|–]\\s*MODYOLO.*$", RegexOption.IGNORE_CASE),
            Regex("\\s*[-|–]\\s*HappyMod.*$", RegexOption.IGNORE_CASE),
            Regex("\\s+v?\\d[\\d.\\w]*\\s+MOD APK.*$", RegexOption.IGNORE_CASE),
            Regex("\\s+MOD APK.*$", RegexOption.IGNORE_CASE),
            Regex("\\s+APK Download.*$", RegexOption.IGNORE_CASE),
            Regex("\\s+Mod Apk\\s+[\\d.]+.*$", RegexOption.IGNORE_CASE),
        )
        var result = text
        for (pattern in patterns) {
            result = pattern.replace(result, "")
        }
        return result.trim().takeIf { it.isNotBlank() } ?: text
    }
}

/** Static facts shared by all providers, resolved from [SourceInfo]. */
internal val SourceId.infoOrNull: SourceInfo?
    get() = SourceInfo.All.firstOrNull { it.id == this }
