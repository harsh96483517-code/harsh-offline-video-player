package com.harsh.player.ui.library

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.harsh.player.data.VideoRepository
import com.harsh.player.data.model.VideoItem
import com.harsh.player.databinding.ItemVideoCardBinding
import com.harsh.player.util.Formatters
import com.harsh.player.util.ThumbnailLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Horizontal card list used by home shelves and the library grid. Cards are
 * remote-focusable and show thumbnail, title, duration, resolution and a
 * progress bar for partially watched videos.
 */
class VideoCardAdapter(
    private val onClick: (VideoItem) -> Unit
) : RecyclerView.Adapter<VideoCardAdapter.CardHolder>() {

    private val items = ArrayList<VideoItem>()
    private val scope = CoroutineScope(Dispatchers.Main)
    private var repo: VideoRepository? = null

    fun setRepo(repository: VideoRepository) { repo = repository }

    fun submit(list: List<VideoItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardHolder {
        val binding = ItemVideoCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CardHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: CardHolder, position: Int) = holder.bind(items[position])

    inner class CardHolder(private val b: ItemVideoCardBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: VideoItem) {
            b.title.text = item.title
            val meta = buildString {
                append(Formatters.duration(item.durationMs))
                if (item.width > 0 && item.height > 0) {
                    append(" · ")
                    append(Formatters.resolution(item.width, item.height))
                }
            }
            b.meta.text = meta

            val resume = repo?.history?.resumePosition(item.key) ?: 0L
            if (resume > 0L && item.durationMs > 0L) {
                b.progress.max = 100
                b.progress.progress = ((resume * 100) / item.durationMs).toInt().coerceIn(0, 100)
                b.progress.visibility = android.view.View.VISIBLE
            } else {
                b.progress.visibility = android.view.View.GONE
            }

            // Placeholder until the thumbnail resolves.
            b.thumb.setImageDrawable(null)
            scope.launch {
                val ctx = b.root.context
                withContext(Dispatchers.Main) {
                    ThumbnailLoader.load(ctx, item, b.thumb)
                }
            }

            b.root.setOnClickListener { onClick(item) }
        }
    }
}
