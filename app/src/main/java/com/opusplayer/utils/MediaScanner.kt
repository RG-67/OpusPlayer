package com.opusplayer.utils

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.opusplayer.model.Song
import java.io.File

object MediaScanner {

    fun getAllSongs(context: Context): List<Song> {
        val songs = mutableListOf<Song>()

        // 1. Scan MediaStore (device-wide MP3s)
        songs.addAll(scanMediaStore(context))

        // 2. Also scan app-private Music folder (files downloaded by this app)
        val appMusicDir = context.getExternalFilesDir("Music")
        if (appMusicDir != null && appMusicDir.exists()) {
            val appSongs = scanDirectory(appMusicDir)
            // Merge — avoid duplicates by path
            val existingPaths = songs.map { it.path }.toHashSet()
            songs.addAll(appSongs.filter { it.path !in existingPaths })
        }

        return songs.sortedByDescending { File(it.path).lastModified() }
    }

    private fun scanMediaStore(context: Context): List<Song> {
        val songs = mutableListOf<Song>()

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.SIZE
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 " +
                "AND ${MediaStore.Audio.Media.DURATION} >= 30000"

        try {
            val cursor: Cursor? = context.contentResolver.query(
                collection, projection, selection, null,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )
            cursor?.use { c ->
                val idCol      = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol   = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol  = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol   = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durCol     = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol    = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val sizeCol    = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

                while (c.moveToNext()) {
                    val path = c.getString(dataCol) ?: continue
                    if (!File(path).exists()) continue

                    val albumId = c.getLong(albumIdCol)
                    val albumArtUri = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"), albumId
                    ).toString()

                    val rawArtist = c.getString(artistCol) ?: "Unknown Artist"
                    songs.add(
                        Song(
                            id       = c.getLong(idCol),
                            title    = cleanTitle(c.getString(titleCol) ?: "Unknown"),
                            artist   = if (rawArtist == "<unknown>") "Unknown Artist" else rawArtist,
                            album    = c.getString(albumCol) ?: "Unknown Album",
                            duration = c.getLong(durCol),
                            path     = path,
                            albumArtUri = albumArtUri,
                            size     = c.getLong(sizeCol)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return songs
    }

    /** Scan a directory recursively for audio files without needing MediaStore permission */
    fun scanDirectory(dir: File): List<Song> {
        val songs = mutableListOf<Song>()
        val audioExtensions = setOf("mp3", "m4a", "flac", "ogg", "wav", "aac", "opus")

        dir.walkTopDown()
            .filter { it.isFile }
            .filter { it.extension.lowercase() in audioExtensions }
            .forEach { file ->
                val title = file.nameWithoutExtension.replace("_", " ").trim()
                songs.add(
                    Song(
                        id       = file.absolutePath.hashCode().toLong(),
                        title    = title,
                        artist   = "Unknown Artist",
                        album    = "Unknown Album",
                        duration = getFileDuration(file),
                        path     = file.absolutePath,
                        albumArtUri = null,
                        size     = file.length()
                    )
                )
            }
        return songs
    }

    private fun getFileDuration(file: File): Long {
        return try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val dur = retriever.extractMetadata(
                android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L
            retriever.release()
            dur
        } catch (e: Exception) { 0L }
    }

    fun getStorageUsedByApp(context: Context): Long {
        var total = 0L
        context.getExternalFilesDir("Music")?.walkTopDown()
            ?.filter { it.isFile }
            ?.forEach { total += it.length() }
        return total
    }

    private fun cleanTitle(title: String): String =
        title.replace(Regex("\\[.*?\\]"), "")
             .replace(Regex("\\((Official|Audio|Lyric|HD|HQ|Video|Music).*?\\)", RegexOption.IGNORE_CASE), "")
             .trim()
             .ifEmpty { title }
}
