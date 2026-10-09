package com.harsh.player.data

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.harsh.player.data.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Discovers local videos.
 *
 * Internal + USB volumes are read through MediaStore with a projection limited
 * to the columns we actually use, so the cursor stays small. User-selected SAF
 * folders are walked with DocumentFile. No directory is ever accessed outside
 * what Android exposes to the app.
 */
class MediaStoreScanner(private val context: Context) {

    private val videoExtensions = setOf(
        "mp4", "mkv", "webm", "avi", "mov", "m4v", "3gp", "ts", "flv", "mpg", "mpeg", "wmv"
    )

    /** Query all MediaStore-visible videos on internal + removable volumes. */
    suspend fun scanMediaStore(): List<VideoItem> = withContext(Dispatchers.IO) {
        val results = ArrayList<VideoItem>(256)
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.DATA
        )

        val sort = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        runCatching {
            context.contentResolver.query(collection, projection, null, null, sort)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val wCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val hCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: continue
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (ext.isNotEmpty() && ext !in videoExtensions) continue
                    val uri = Uri.withAppendedPath(collection, id.toString())
                    val data = if (dataCol >= 0) cursor.getString(dataCol) ?: "" else ""
                    val source = if (isRemovable(data)) VideoItem.Source.USB else VideoItem.Source.INTERNAL
                    results.add(
                        VideoItem(
                            id = id,
                            uri = uri,
                            title = name,
                            durationMs = cursor.getLong(durCol),
                            sizeBytes = cursor.getLong(sizeCol),
                            width = cursor.getInt(wCol),
                            height = cursor.getInt(hCol),
                            relativePath = data,
                            dateAddedSec = cursor.getLong(dateCol),
                            bucketName = cursor.getString(bucketCol) ?: "Unknown",
                            source = source
                        )
                    )
                }
            }
        }
        results
    }

    /** Walk a user-selected SAF tree URI and collect video documents. */
    suspend fun scanSafFolder(treeUri: Uri): List<VideoItem> = withContext(Dispatchers.IO) {
        val out = ArrayList<VideoItem>()
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext out
        walk(root, out, depth = 0)
        out
    }

    private fun walk(dir: DocumentFile, out: MutableList<VideoItem>, depth: Int) {
        if (depth > 8) return // guard against pathological trees
        val children = runCatching { dir.listFiles() }.getOrDefault(emptyArray())
        for (child in children) {
            if (child.isDirectory) {
                walk(child, out, depth + 1)
            } else {
                val name = child.name ?: continue
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext in videoExtensions) {
                    out.add(
                        VideoItem(
                            id = name.hashCode().toLong(),
                            uri = child.uri,
                            title = name,
                            durationMs = 0L,
                            sizeBytes = child.length(),
                            width = 0,
                            height = 0,
                            relativePath = child.uri.toString(),
                            dateAddedSec = child.lastModified() / 1000,
                            bucketName = dir.name ?: "Folder",
                            source = VideoItem.Source.SAF_FOLDER
                        )
                    )
                }
            }
        }
    }

    /** Read a display name for an arbitrary content URI (used for picked files). */
    fun displayName(uri: Uri): String {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c: Cursor ->
                    if (c.moveToFirst()) {
                        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) return c.getString(idx) ?: uri.lastPathSegment ?: "video"
                    }
                }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "video"
    }

    private fun isRemovable(path: String): Boolean {
        if (path.isBlank()) return false
        val removable = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.getExternalFilesDirs(null)
                    .filterNotNull()
                    .any { Environment.isExternalStorageRemovable(it) }
            } else false
        }.getOrDefault(false)
        return removable && (path.contains("/storage/", ignoreCase = true) &&
            !path.startsWith(Environment.getExternalStorageDirectory().absolutePath))
    }
}
