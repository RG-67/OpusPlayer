package com.opusplayer.utils

import android.webkit.MimeTypeMap
import java.net.URI

/**
 * Utility that decides whether a URL intercepted in the WebView
 * is a direct audio file that should be downloaded instead of loaded.
 */
object WebViewInterceptor {

    private val AUDIO_EXTENSIONS = setOf(
        "mp3", "m4a", "flac", "ogg", "opus", "wav", "aac", "wma", "aiff"
    )

    private val AUDIO_MIME_TYPES = setOf(
        "audio/mpeg", "audio/mp3", "audio/mp4", "audio/x-m4a",
        "audio/flac", "audio/ogg", "audio/opus", "audio/wav",
        "audio/aac", "audio/x-aac", "audio/x-ms-wma",
        "application/octet-stream"   // generic binary – rely on extension
    )

    /**
     * Returns true if the URL looks like a direct audio download.
     */
    fun shouldIntercept(url: String, mimeType: String? = null): Boolean {
        // 1. Check explicit MIME type
        if (mimeType != null) {
            val baseMime = mimeType.split(";").first().trim().lowercase()
            if (baseMime in AUDIO_MIME_TYPES && extensionFromUrl(url) in AUDIO_EXTENSIONS) return true
            if (baseMime.startsWith("audio/")) return true
        }

        // 2. Check URL extension
        val ext = extensionFromUrl(url)
        if (ext in AUDIO_EXTENSIONS) return true

        // 3. Heuristic: URL contains "download" + audio keyword
        val lower = url.lowercase()
        if (lower.contains("download") &&
            (lower.contains("mp3") || lower.contains("audio") || lower.contains("music"))
        ) return true

        return false
    }

    /**
     * Derives a safe local filename from the URL.
     */
    fun guessFileName(url: String, contentDisposition: String? = null, mimeType: String? = null): String {
        // Try Content-Disposition header first
        if (contentDisposition != null) {
            val match = Regex("""filename[^;=\n]*=(['"]?)([^;\n'"]+)\1""")
                .find(contentDisposition)
            val name = match?.groupValues?.getOrNull(2)?.trim()
            if (!name.isNullOrBlank()) {
                return ensureAudioExtension(name)
            }
        }

        // Fall back to last path segment
        return try {
            val path = URI(url).path ?: ""
            val last = path.substringAfterLast('/')
            val clean = last.substringBefore('?').trim()
            ensureAudioExtension(clean.ifEmpty { "track" })
        } catch (e: Exception) {
            "track_${System.currentTimeMillis()}.mp3"
        }
    }

    private fun extensionFromUrl(url: String): String {
        return try {
            val path = url.substringBefore("?").substringBefore("#")
            path.substringAfterLast('.').lowercase().trim()
        } catch (e: Exception) { "" }
    }

    private fun ensureAudioExtension(name: String): String {
        val ext = name.substringAfterLast('.').lowercase()
        return if (ext in AUDIO_EXTENSIONS) name else "$name.mp3"
    }
}
