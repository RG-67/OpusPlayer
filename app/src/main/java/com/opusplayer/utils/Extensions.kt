package com.opusplayer.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.View
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.opusplayer.R
import java.io.InputStream

fun View.visible() {
    visibility = View.VISIBLE
}

fun View.gone() {
    visibility = View.GONE
}

fun View.invisible() {
    visibility = View.INVISIBLE
}

fun Long.toReadableSize(): String {
    return when {
        this >= 1_073_741_824 -> "%.1f GB".format(this / 1_073_741_824.0)
        this >= 1_048_576 -> "%.1f MB".format(this / 1_048_576.0)
        this >= 1024 -> "%.1f KB".format(this / 1024.0)
        else -> "$this B"
    }
}

fun Long.toGb(): Float = this / (1024f * 1024f * 1024f)

fun Int.toTimeString(): String {
    val totalSeconds = this / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

fun ImageView.loadAlbumArt(uri: String?, cornerRadius: Int = 8) {
    if (cornerRadius == 0) {
        Glide.with(this)
            .load(uri)
            .apply(
                RequestOptions()
                    .placeholder(R.drawable.bg_album_placeholder)
                    .error(R.drawable.bg_album_placeholder)
                    .centerCrop()
            )
            .into(this)
    } else {
        Glide.with(this)
            .load(uri)
            .apply(
                RequestOptions()
                    .placeholder(R.drawable.bg_album_placeholder)
                    .error(R.drawable.bg_album_placeholder)
                    .transform(RoundedCorners(cornerRadius))
            )
            .into(this)
    }
}

fun ImageView.loadAlbumArtLarge(uri: String?) {
    Glide.with(this)
        .load(uri)
        .apply(
            RequestOptions()
                .placeholder(R.drawable.bg_album_placeholder)
                .error(R.drawable.bg_album_placeholder)
                .transform(RoundedCorners(32))
        )
        .into(this)
}

fun Context.getBitmapFromUri(uriString: String?): Bitmap? {
    if (uriString == null) return null
    return try {
        val uri = Uri.parse(uriString)
        val inputStream: InputStream? = contentResolver.openInputStream(uri)
        BitmapFactory.decodeStream(inputStream)
    } catch (e: Exception) {
        null
    }
}

fun String.sanitizeFilename(): String =
    this.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()

fun Long.formatMillis(): String {
    val minutes = (this / 60000).toInt()
    val seconds = ((this % 60000) / 1000).toInt()
    return "%d:%02d".format(minutes, seconds)
}
