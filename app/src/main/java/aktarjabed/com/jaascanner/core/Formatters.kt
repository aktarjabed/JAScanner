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

    /** Removes characters that are illegal in file names and keeps the result short. */
    fun safeFileName(name: String): String {
        val cleaned = name
            .replace(Regex("[\\\\/:*?\"<>|\\r\\n\\t]"), "_")
            .trim()
            .take(80)
        return cleaned.ifBlank { "Document" }
    }

    fun stripPdfExtension(name: String): String =
        if (name.endsWith(".pdf", ignoreCase = true)) name.dropLast(4) else name
}
