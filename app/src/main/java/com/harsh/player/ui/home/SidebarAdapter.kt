package com.harsh.player.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.harsh.player.data.model.LibraryCategory
import com.harsh.player.databinding.ItemSidebarBinding

/** Left navigation sidebar. Fully D-pad navigable with a visible focus state. */
class SidebarAdapter(
    private val onSelect: (LibraryCategory) -> Unit
) : RecyclerView.Adapter<SidebarAdapter.ItemHolder>() {

    private val entries = listOf(
        LibraryCategory.HOME to "Home",
        LibraryCategory.ALL_VIDEOS to "All Videos",
        LibraryCategory.MOVIES to "Movies",
        LibraryCategory.SERIES to "Series",
        LibraryCategory.FOLDERS to "Folders",
        LibraryCategory.RECENTLY_PLAYED to "Recently Played",
        LibraryCategory.FAVORITES to "Favorites",
        LibraryCategory.PLAYLISTS to "Playlists",
        LibraryCategory.SETTINGS to "Settings",
        LibraryCategory.ABOUT to "About HARSH"
    )

    private var selected = LibraryCategory.HOME

    fun select(category: LibraryCategory) {
        val old = entries.indexOfFirst { it.first == selected }
        selected = category
        val new = entries.indexOfFirst { it.first == selected }
        if (old >= 0) notifyItemChanged(old)
        if (new >= 0) notifyItemChanged(new)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemHolder {
        val b = ItemSidebarBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ItemHolder(b)
    }

    override fun getItemCount(): Int = entries.size

    override fun onBindViewHolder(holder: ItemHolder, position: Int) {
        val (category, label) = entries[position]
        holder.bind(category, label, category == selected)
    }

    inner class ItemHolder(private val b: ItemSidebarBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(category: LibraryCategory, label: String, isSelected: Boolean) {
            b.navLabel.text = label
            b.root.isSelected = isSelected
            b.root.setOnClickListener { onSelect(category) }
        }
    }
}
