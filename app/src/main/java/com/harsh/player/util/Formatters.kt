package com.harsh.player.util

import android.content.Context
import android.net.Uri
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Small formatting helpers shared across the UI. No allocations beyond the result. */
object Formatters {

    fun duration(millis: Long): String {
        if (millis <= 0L) return "--:--"
        val h = TimeUnit.MILLISECONDS.toHours(millis)
        val m = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        val s = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%02d:%02d", m, s)
    }

    fun fileSize(bytes: Long): String {
        if (bytes <= 0L) return "—"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            else -> String.format(Locale.US, "%.0f KB", kb)
        }
    }

    fun resolution(width: Int, height: Int): String {
        if (width <= 0 || height <= 0) return "Unknown"
        val label = when {
            height >= 2160 -> "4K"
            height >= 1440 -> "1440p"
            height >= 1080 -> "1080p"
            height >= 720 -> "720p"
            height >= 480 -> "480p"
            else -> "${height}p"
        }
        return "$label · ${width}×${height}"
    }

    /** Human-readable name from a content:// or file:// URI's last path segment. */
    fun displayNameFromUri(context: Context, uri: Uri): String {
        uri.lastPathSegment?.let { return it.substringAfterLast('/') }
        return "Unknown video"
    }
}
