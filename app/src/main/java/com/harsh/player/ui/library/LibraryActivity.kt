package com.harsh.player.ui.library

import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.harsh.player.R
import com.harsh.player.data.VideoRepository
import com.harsh.player.data.model.LibraryCategory
import com.harsh.player.data.model.SortOrder
import com.harsh.player.data.model.VideoItem
import com.harsh.player.databinding.ActivityLibraryBinding
import com.harsh.player.ui.player.PlayerActivity
import kotlinx.coroutines.launch

/**
 * Library screen used for All Videos / Movies / Series / Favorites /
 * Recently Played / Folders. Supports in-memory sort and search — no repeated
 * full-storage scans.
 */
class LibraryActivity : AppCompatActivity() {

    private lateinit var b: ActivityLibraryBinding
    private lateinit var repo: VideoRepository
    private lateinit var adapter: VideoCardAdapter

    private var category: LibraryCategory = LibraryCategory.ALL_VIDEOS
    private var query: String = ""
    private var sort: SortOrder = SortOrder.NAME
    private var folderFilter: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityLibraryBinding.inflate(layoutInflater)
        setContentView(b.root)
        repo = VideoRepository.get(this)

        category = runCatching {
            LibraryCategory.valueOf(intent.getStringExtra(EXTRA_CATEGORY) ?: "")
        }.getOrDefault(LibraryCategory.ALL_VIDEOS)
        query = intent.getStringExtra(EXTRA_QUERY) ?: ""

        adapter = VideoCardAdapter { openPlayer(it) }
        adapter.setRepo(repo)

        b.grid.layoutManager = GridLayoutManager(this, 4)
        b.grid.adapter = adapter
        b.grid.setHasFixedSize(true)
        b.grid.itemAnimator = null

        b.title.text = titleFor(category)
        b.btnSort.setOnClickListener { showSortDialog() }
        b.btnSearch.setOnClickListener { showSearch() }

        if (category == LibraryCategory.FOLDERS) showFolders() else reload()
    }

    override fun onResume() {
        super.onResume()
        if (category != LibraryCategory.FOLDERS) reload()
    }

    private fun reload() {
        lifecycleScope.launch {
            if (repo.videos.value.isEmpty()) repo.refresh()
            val list = when (category) {
                LibraryCategory.MOVIES -> repo.movies()
                LibraryCategory.SERIES -> repo.series()
                LibraryCategory.FAVORITES -> repo.favorites()
                LibraryCategory.RECENTLY_PLAYED -> repo.recentlyPlayed()
                else -> repo.sorted(sort, query)
            }.let { base ->
                val filtered = if (folderFilter != null) base.filter { it.bucketName == folderFilter } else base
                when (category) {
                    LibraryCategory.ALL_VIDEOS -> sortList(filtered)
                    LibraryCategory.MOVIES, LibraryCategory.SERIES -> sortList(filtered)
                    else -> filtered
                }
            }
            adapter.submit(list)
            b.emptyState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun sortList(list: List<VideoItem>): List<VideoItem> = when (sort) {
        SortOrder.NAME -> list.sortedBy { it.title.lowercase() }
        SortOrder.DURATION -> list.sortedByDescending { it.durationMs }
        SortOrder.SIZE -> list.sortedByDescending { it.sizeBytes }
        SortOrder.DATE_ADDED -> list.sortedByDescending { it.dateAddedSec }
    }

    private fun showSortDialog() {
        val options = arrayOf("Name", "Duration", "File size", "Date added")
        AlertDialog.Builder(this)
            .setTitle(R.string.sort_by)
            .setSingleChoiceItems(options, sort.ordinal) { d, which ->
                sort = SortOrder.values()[which]
                d.dismiss()
                reload()
            }
            .show()
    }

    private fun showSearch() {
        val input = EditText(this)
        input.setText(query)
        input.hint = getString(R.string.search_hint)
        AlertDialog.Builder(this)
            .setTitle(R.string.search_title)
            .setView(input)
            .setPositiveButton(R.string.action_search) { _, _ ->
                query = input.text.toString()
                reload()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showFolders() {
        val folders = repo.folders()
        if (folders.isEmpty()) {
            b.emptyState.visibility = View.VISIBLE
            return
        }
        val names = folders.keys.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.folders)
            .setItems(names) { _, which ->
                folderFilter = names[which]
                b.title.text = names[which]
                reload()
            }
            .show()
    }

    private fun titleFor(category: LibraryCategory): String = when (category) {
        LibraryCategory.MOVIES -> getString(R.string.nav_movies)
        LibraryCategory.SERIES -> getString(R.string.nav_series)
        LibraryCategory.FAVORITES -> getString(R.string.nav_favorites)
        LibraryCategory.RECENTLY_PLAYED -> getString(R.string.nav_recent)
        LibraryCategory.FOLDERS -> getString(R.string.nav_folders)
        else -> getString(R.string.nav_all_videos)
    }

    private fun openPlayer(item: VideoItem) {
        startActivity(PlayerActivity.intent(this, item))
    }

    companion object {
        const val EXTRA_CATEGORY = "extra_category"
        const val EXTRA_QUERY = "extra_query"
    }
}
