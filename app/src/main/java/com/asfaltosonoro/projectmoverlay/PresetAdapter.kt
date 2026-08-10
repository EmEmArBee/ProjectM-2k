package com.asfaltosonoro.projectmoverlay

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

class PresetAdapter(
    private var items: List<PresetEntry>,
    private val playlistEditMode: () -> Boolean,
    private val checkedPaths: MutableSet<String>,
    private val onToggleFavorite: (PresetEntry) -> Unit,
    private val onCheckedChanged: (PresetEntry, Boolean) -> Unit,
    private val onAddToPlaylist: (PresetEntry) -> Unit,
    private val onClick: (PresetEntry) -> Unit
) : RecyclerView.Adapter<PresetAdapter.VH>() {

    class VH(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.presetTitle)
        val author: TextView = view.findViewById(R.id.presetAuthor)
        val swatch: android.view.View = view.findViewById(R.id.previewSwatch)
        val favorite: ImageButton = view.findViewById(R.id.favoriteButton)
        val addToPlaylist: ImageButton = view.findViewById(R.id.addToPlaylistButton)
        val check: CheckBox = view.findViewById(R.id.playlistCheck)
    }

    fun submitList(newItems: List<PresetEntry>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_preset, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val entry = items[position]
        holder.title.text = entry.title
        holder.author.text = entry.author ?: ""
        holder.author.visibility = if (entry.author != null) android.view.View.VISIBLE else android.view.View.GONE
        holder.swatch.setBackgroundColor(colorForPreset(entry.path))

        holder.favorite.setImageResource(
            if (entry.favorite) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off
        )
        holder.favorite.setOnClickListener { onToggleFavorite(entry) }
        holder.addToPlaylist.setOnClickListener { onAddToPlaylist(entry) }

        if (playlistEditMode()) {
            holder.check.visibility = android.view.View.VISIBLE
            holder.check.setOnCheckedChangeListener(null)
            holder.check.isChecked = entry.path in checkedPaths
            holder.check.setOnCheckedChangeListener { _, checked -> onCheckedChanged(entry, checked) }
            holder.itemView.setOnClickListener { holder.check.toggle() }
        } else {
            holder.check.visibility = android.view.View.GONE
            holder.itemView.setOnClickListener { onClick(entry) }
        }
    }

    /** Colore deterministico (stesso preset = sempre stesso colore) per lo swatch. */
    private fun colorForPreset(path: String): Int {
        val hue = (abs(path.hashCode()) % 360).toFloat()
        return Color.HSVToColor(floatArrayOf(hue, 0.55f, 0.85f))
    }
}
