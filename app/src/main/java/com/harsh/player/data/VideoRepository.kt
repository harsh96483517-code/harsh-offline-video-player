package com.harsh.player.data

import android.content.Context
import android.net.Uri
import com.harsh.player.data.model.SortOrder
import com.harsh.player.data.model.VideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single in-memory source of truth for the video library.
 *
 * The library is loaded once, cached in memory and filtered/sorted in memory
 * rather than re-querying MediaStore on every screen. Callers may request a
 * refresh, which re-scans in the background.
 */
class VideoRepository private constructor(private val context: Context) {

    private val scanner = MediaStoreScanner(context)
    val history = HistoryStore(context)

    private val _videos = MutableStateFlow<List<VideoItem>>(emptyList())
    val videos: StateFlow<List<VideoItem>> = _videos.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val safFolders = LinkedHashSet<Uri>()

    suspend fun refresh() {
        _loading.value = true
        try {
            val fromStore = scanner.scanMediaStore()
            val fromSaf = safFolders.flatMap { scanner.scanSafFolder(it) }
            _videos.value = (fromStore + fromSaf).distinctBy { it.key }
        } finally {
            _loading.value = false
        }
    }

    suspend fun addSafFolder(treeUri: Uri) {
        safFolders.add(treeUri)
        val extra = scanner.scanSafFolder(treeUri)
        _videos.value = (_videos.value + extra).distinctBy { it.key }
    }

    fun addPickedVideo(uri: Uri) {
        val name = scanner.displayName(uri)
        val item = VideoItem(
            id = name.hashCode().toLong(),
            uri = uri,
            title = name,
            durationMs = 0L,
            sizeBytes = 0L,
            width = 0, height = 0,
            relativePath = uri.toString(),
            dateAddedSec = System.currentTimeMillis() / 1000,
            bucketName = "Picked",
            source = VideoItem.Source.SAF_FOLDER
        )
        _videos.value = (_videos.value + item).distinctBy { it.key }
    }

    fun findByKey(key: String): VideoItem? = _videos.value.firstOrNull { it.key == key }

    // ---- Derived views ----------------------------------------------------

    fun sorted(order: SortOrder, query: String = ""): List<VideoItem> {
        val base = if (query.isBlank()) _videos.value
        else _videos.value.filter { it.title.contains(query, ignoreCase = true) }
        return when (order) {
            SortOrder.NAME -> base.sortedBy { it.title.lowercase() }
            SortOrder.DURATION -> base.sortedByDescending { it.durationMs }
            SortOrder.SIZE -> base.sortedByDescending { it.sizeBytes }
            SortOrder.DATE_ADDED -> base.sortedByDescending { it.dateAddedSec }
        }
    }

    fun movies() = _videos.value.filter { it.isMovieLike }
    fun series() = _videos.value.filter { it.isSeriesLike }

    fun recentlyAdded(limit: Int = 20) =
        _videos.value.sortedByDescending { it.dateAddedSec }.take(limit)

    fun usbVideos() = _videos.value.filter { it.source == VideoItem.Source.USB }

    fun favorites(): List<VideoItem> {
        val favs = history.favorites()
        return _videos.value.filter { it.key in favs }
    }

    /** Recently played resolved against the current library (skips missing files). */
    fun recentlyPlayed(): List<VideoItem> =
        history.recentlyPlayed().mapNotNull { findByKey(it) }

    /** Videos with a resume point that is not finished — used for Continue Watching. */
    fun continueWatching(limit: Int = 20): List<VideoItem> =
        recentlyPlayed().filter { history.resumePosition(it.key) > 0L }.take(limit)

    fun folders(): Map<String, List<VideoItem>> =
        _videos.value.groupBy { it.bucketName }.toSortedMap()

    companion object {
        @Volatile
        private var instance: VideoRepository? = null

        fun get(context: Context): VideoRepository =
            instance ?: synchronized(this) {
                instance ?: VideoRepository(context.applicationContext).also { instance = it }
            }
    }
}
