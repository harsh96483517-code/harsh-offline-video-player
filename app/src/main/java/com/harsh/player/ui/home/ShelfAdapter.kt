package com.harsh.player.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.harsh.player.data.VideoRepository
import com.harsh.player.data.model.VideoItem
import com.harsh.player.databinding.ItemShelfBinding
import com.harsh.player.ui.library.VideoCardAdapter

/** Vertical list of horizontal shelves for the home screen. */
class ShelfAdapter(
    private val repo: VideoRepository,
    private val onVideoClick: (VideoItem) -> Unit
) : RecyclerView.Adapter<ShelfAdapter.ShelfHolder>() {

    private val shelves = ArrayList<Shelf>()

    fun submit(list: List<Shelf>) {
        shelves.clear()
        shelves.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShelfHolder {
        val b = ItemShelfBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ShelfHolder(b)
    }

    override fun getItemCount(): Int = shelves.size

    override fun onBindViewHolder(holder: ShelfHolder, position: Int) = holder.bind(shelves[position])

    inner class ShelfHolder(private val b: ItemShelfBinding) : RecyclerView.ViewHolder(b.root) {
        private val cardAdapter = VideoCardAdapter(onVideoClick)

        init {
            cardAdapter.setRepo(repo)
            b.row.layoutManager = LinearLayoutManager(
                b.root.context, LinearLayoutManager.HORIZONTAL, false
            )
            b.row.adapter = cardAdapter
            b.row.setHasFixedSize(true)
            b.row.itemAnimator = null // keep focus movement cheap
        }

        fun bind(shelf: Shelf) {
            b.shelfTitle.text = shelf.title
            cardAdapter.submit(shelf.items)
        }
    }
}
