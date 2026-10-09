package com.harsh.player.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.harsh.player.data.VideoRepository
import com.harsh.player.data.model.VideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A single horizontal shelf on the home screen. */
data class Shelf(val title: String, val items: List<VideoItem>)

data class HomeUiState(
    val loading: Boolean = true,
    val shelves: List<Shelf> = emptyList(),
    val empty: Boolean = false
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = VideoRepository.get(app)

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    fun refresh() {
        viewModelScope.launch { load(force = true) }
    }

    private suspend fun load(force: Boolean = false) {
        _state.value = _state.value.copy(loading = true)
        if (force || repo.videos.value.isEmpty()) {
            repo.refresh()
        }
        val shelves = buildList {
            val cont = repo.continueWatching()
            if (cont.isNotEmpty()) add(Shelf("Continue Watching", cont))
            val recent = repo.recentlyPlayed()
            if (recent.isNotEmpty()) add(Shelf("Recently Played", recent))
            val added = repo.recentlyAdded()
            if (added.isNotEmpty()) add(Shelf("Recently Added", added))
            val favs = repo.favorites()
            if (favs.isNotEmpty()) add(Shelf("Favorites", favs))
            val usb = repo.usbVideos()
            if (usb.isNotEmpty()) add(Shelf("USB Storage", usb))
            val movies = repo.movies()
            if (movies.isNotEmpty()) add(Shelf("Movies", movies.take(20)))
            val series = repo.series()
            if (series.isNotEmpty()) add(Shelf("Series", series.take(20)))
        }
        _state.value = HomeUiState(
            loading = false,
            shelves = shelves,
            empty = shelves.isEmpty()
        )
    }
}
