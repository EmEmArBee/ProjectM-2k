package com.asfaltosonoro.projectmoverlay

import android.app.AlertDialog
import android.os.Bundle
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Browser dei preset: elenco con stella per i preferiti, checkbox "solo
 * preferiti", e una modalità "modifica playlist" per selezionare più preset
 * e salvarli come playlist con nome.
 */
class PresetBrowserActivity : AppCompatActivity() {

    private lateinit var repository: PresetRepository
    private lateinit var prefs: Prefs
    private lateinit var adapter: PresetAdapter
    private lateinit var recycler: RecyclerView

    private var editingPlaylist: String? = null
    private val checkedPaths = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_ProjectMOverlay_Settings)
        setContentView(R.layout.activity_preset_browser)

        repository = PresetRepository(this)
        prefs = Prefs(this)

        recycler = findViewById(R.id.presetRecycler)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = PresetAdapter(
            items = repository.allPresets(),
            playlistEditMode = { editingPlaylist != null },
            checkedPaths = checkedPaths,
            onToggleFavorite = { entry ->
                repository.setFavorite(entry.path, !entry.favorite)
                refreshList()
            },
            onCheckedChanged = { entry, checked ->
                if (checked) checkedPaths.add(entry.path) else checkedPaths.remove(entry.path)
            },
            onAddToPlaylist = { entry -> showAddToPlaylistDialog(entry) },
            onClick = { entry ->
                // tap su un preset fuori dalla modalità playlist: usalo come preset manuale singolo
                prefs.manualPresetPath = entry.path
                prefs.playbackMode = PlaybackMode.MANUAL_SINGLE
                Toast.makeText(this, getString(R.string.section_playback_mode) + ": " + entry.name, Toast.LENGTH_SHORT).show()
            }
        )
        recycler.adapter = adapter

        findViewById<CheckBox>(R.id.onlyFavoritesCheck).setOnCheckedChangeListener { _, _ -> refreshList() }

        findViewById<android.widget.Button>(R.id.btnNewPlaylist).setOnClickListener { promptNewPlaylistName() }
        findViewById<android.widget.Button>(R.id.btnSavePlaylist).setOnClickListener { saveEditingPlaylist() }

        refreshList()
    }

    /** Bottone "+" su un preset: scegli a quale playlist aggiungerlo, o creane una nuova al volo. */
    private fun showAddToPlaylistDialog(entry: PresetEntry) {
        val existing = repository.playlistNames()
        val options = existing + getString(R.string.new_playlist)
        AlertDialog.Builder(this)
            .setTitle(entry.title)
            .setItems(options.toTypedArray()) { _, which ->
                if (which == existing.size) {
                    promptNewPlaylistNameAndAdd(entry)
                } else {
                    repository.addToPlaylist(existing[which], entry.path)
                    Toast.makeText(this, "Aggiunto a \"${existing[which]}\"", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun promptNewPlaylistNameAndAdd(entry: PresetEntry) {
        val input = EditText(this)
        AlertDialog.Builder(this)
            .setTitle(R.string.new_playlist)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    repository.addToPlaylist(name, entry.path)
                    Toast.makeText(this, "Aggiunto a \"$name\"", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun promptNewPlaylistName() {
        val input = EditText(this)
        AlertDialog.Builder(this)
            .setTitle(R.string.new_playlist)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) startEditingPlaylist(name)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun startEditingPlaylist(name: String) {
        editingPlaylist = name
        checkedPaths.clear()
        checkedPaths.addAll(repository.playlist(name))
        findViewById<LinearLayout>(R.id.playlistEditBar).visibility = android.view.View.VISIBLE
        findViewById<TextView>(R.id.playlistEditLabel).text = name
        refreshList()
    }

    private fun saveEditingPlaylist() {
        val name = editingPlaylist ?: return
        repository.savePlaylist(name, checkedPaths.toList())
        editingPlaylist = null
        findViewById<LinearLayout>(R.id.playlistEditBar).visibility = android.view.View.GONE
        Toast.makeText(this, "\"$name\" salvata (${checkedPaths.size} preset)", Toast.LENGTH_SHORT).show()
        refreshList()
    }

    private fun refreshList() {
        val onlyFavorites = findViewById<CheckBox>(R.id.onlyFavoritesCheck).isChecked
        val list = if (onlyFavorites) repository.favoritePresets() else repository.allPresets()
        adapter.submitList(list)
    }
}
