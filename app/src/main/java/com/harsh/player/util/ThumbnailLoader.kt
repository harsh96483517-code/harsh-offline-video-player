package com.harsh.player.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.widget.ImageView
import com.harsh.player.data.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Thumbnail loading with a small in-memory LRU cache and downscaling.
 *
 * We reuse Android-provided thumbnails (MediaStore) where possible instead of
 * decoding frames ourselves, which is cheaper and avoids holding full-size
 * bitmaps. All decoding happens off the main thread.
 */
object ThumbnailLoader {

    // ~1/16th of available heap per Android guidance, but capped hard for TV.
    private val cache = object : LruCache<String, Bitmap>(cacheSizeKb()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    private fun cacheSizeKb(): Int {
        val max = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        return (max / 16).coerceAtMost(8 * 1024) // never above 8 MB
    }

    /** Load a thumbnail for a video into the given ImageView. */
    suspend fun load(context: Context, item: VideoItem, target: ImageView) {
        val key = item.key
        cache.get(key)?.let { target.setImageBitmap(it); return }

        val bmp = withContext(Dispatchers.IO) { decode(context, item) }
        if (bmp != null) {
            cache.put(key, bmp)
            withContext(Dispatchers.Main) { target.setImageBitmap(bmp) }
        } else {
            withContext(Dispatchers.Main) { target.setImageDrawable(null) }
        }
    }

    private fun decode(context: Context, item: VideoItem): Bitmap? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(item.uri, android.util.Size(320, 180), null)
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Video.Thumbnails.getThumbnail(
                    context.contentResolver,
                    item.id,
                    MediaStore.Video.Thumbnails.MINI_KIND,
                    null
                )
            }
        }.getOrNull()
    }

    fun clear() = cache.evictAll()

    fun trimTo(maxKb: Int) = cache.trimToSize(maxKb)
}
