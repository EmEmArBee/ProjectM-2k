package com.asfaltosonoro.projectmoverlay

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PresetAdapter(
    private var items: List<PresetEntry>,
    private val playlistEditMode: () -> Boolean,
    private val checkedPaths: MutableSet<String>,
    private val onToggleFavorite: (PresetEntry) -> Unit,
    private val onCheckedChanged: (PresetEntry, Boolean) -> Unit,
    private val onClick: (PresetEntry) -> Unit
) : RecyclerView.Adapter<PresetAdapter.VH>() {

    class VH(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.presetName)
        val favorite: ImageButton = view.findViewById(R.id.favoriteButton)
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
        holder.name.text = entry.name
        holder.favorite.setImageResource(
            if (entry.favorite) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off
        )
        holder.favorite.setOnClickListener { onToggleFavorite(entry) }

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
}
