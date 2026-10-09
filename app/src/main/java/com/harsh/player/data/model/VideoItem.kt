package com.harsh.player.data.model

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * A single playable video discovered from MediaStore or a user-selected SAF
 * folder. Kept intentionally small so lists of thousands of items stay cheap.
 */
@Parcelize
data class VideoItem(
    val id: Long,
    val uri: Uri,
    val title: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val relativePath: String,
    val dateAddedSec: Long,
    val bucketName: String,
    /** Source classification used for the sidebar filters. */
    val source: Source = Source.INTERNAL
) : Parcelable {
    enum class Source { INTERNAL, USB, SAF_FOLDER }

    /** Lightweight key used for history / favorites, independent of thumbnails. */
    val key: String get() = "$id:$title"

    val isSeriesLike: Boolean
        get() {
            val t = title.lowercase()
            return t.contains("s0") || t.contains("season") || t.contains("episode") ||
                Regex("""\bE\d{1,3}\b""").containsMatchIn(title) ||
                Regex("""\bS\d{1,2}E\d{1,3}\b""").containsMatchIn(title)
        }

    val isMovieLike: Boolean get() = !isSeriesLike
}

/** Category grouping used by the home screen and sidebar. */
enum class LibraryCategory { HOME, ALL_VIDEOS, MOVIES, SERIES, FOLDERS, RECENTLY_PLAYED, FAVORITES, PLAYLISTS, SETTINGS, ABOUT }

/** Sort options for the library. */
enum class SortOrder { NAME, DURATION, SIZE, DATE_ADDED }
