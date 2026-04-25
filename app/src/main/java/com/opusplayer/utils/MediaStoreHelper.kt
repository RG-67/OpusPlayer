package com.opusplayer.utils

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.File

object MediaStoreHelper {

    /**
     * Asks Android to scan a newly downloaded file and add it to MediaStore.
     * This makes it appear in the Home tab immediately without a reboot.
     */
    fun scanFile(context: Context, filePath: String, callback: ((String, Uri?) -> Unit)? = null) {
        MediaScannerConnection.scanFile(
            context,
            arrayOf(filePath),
            arrayOf("audio/mpeg")
        ) { path, uri ->
            callback?.invoke(path, uri)
        }
    }

    /**
     * On Android Q+ inserts the track into MediaStore directly so it is
     * immediately visible even before the scanner runs.
     */
    fun insertTrack(context: Context, file: File, title: String, artist: String = "Unknown Artist"): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            scanFile(context, file.absolutePath)
            return null
        }

        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Audio.Media.TITLE, title)
            put(MediaStore.Audio.Media.ARTIST, artist)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
            put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/OpusPlayer")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }

        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val itemUri = context.contentResolver.insert(collection, values) ?: return null

        try {
            context.contentResolver.openOutputStream(itemUri)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            }
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            context.contentResolver.update(itemUri, values, null, null)
        } catch (e: Exception) {
            context.contentResolver.delete(itemUri, null, null)
            return null
        }

        return itemUri
    }
}
