package aktarjabed.com.jaascanner.core

import java.text.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

object Formatters {

    fun fileSize(bytes: Long): String = when {
        bytes < 1_024L -> "$bytes B"
        bytes < 1_024L * 1_024L -> String.format(Locale.US, "%.1f KB", bytes / 1_024.0)
        bytes < 1_024L * 1_024L * 1_024L -> String.format(Locale.US, "%.1f MB", bytes / (1_024.0 * 1_024.0))
        else -> String.format(Locale.US, "%.1f GB", bytes / (1_024.0 * 1_024.0 * 1_024.0))
    }

    fun dateTime(timestampMs: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestampMs))

    fun defaultScanName(nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val pattern = DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm", Locale.US)
        return "Scan " + pattern.format(Instant.ofEpochMilli(nowMs).atZone(zone))
    }

    /** Longest file name, counted in Unicode code points so an emoji is never split in half. */
    private const val MAX_FILE_NAME_CODE_POINTS = 80

    /** Removes characters that are illegal in file names and keeps the result short. */
    fun safeFileName(name: String): String {
        val cleaned = name
            .replace(Regex("[\\\\/:*?\"<>|\\r\\n\\t]"), "_")
            .trim()
        return truncateCodePoints(cleaned, MAX_FILE_NAME_CODE_POINTS).ifBlank { "Document" }
    }

    /**
     * Cuts [value] after at most [maxCodePoints] code points. `String.take` counts UTF-16 code
     * units, so cutting a name that contains an emoji or another supplementary character at the
     * boundary would leave a lone high surrogate and produce an invalid file name.
     */
    internal fun truncateCodePoints(value: String, maxCodePoints: Int): String {
        if (maxCodePoints <= 0) return ""
        if (value.codePointCount(0, value.length) <= maxCodePoints) return value
        val end = value.offsetByCodePoints(0, maxCodePoints)
        return value.substring(0, end)
    }

    fun stripPdfExtension(name: String): String =
        if (name.endsWith(".pdf", ignoreCase = true)) name.dropLast(4) else name
}
