package com.asfaltosonoro.projectmoverlay

import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Browser preset mostrato come DIALOG sopra MainActivity — non come Activity
 * separata. Motivo: un'Activity separata metterebbe in pausa il GLSurfaceView
 * sottostante (MainActivity.onPause chiama glView.onPause()), fermando il
 * rendering. Un Dialog invece resta "sopra" alla stessa Activity, che
 * continua a girare regolarmente: la parte di schermo che questo dialog non
 * copre (in alto, vedi setLayout più sotto) resta quindi una vera finestra
 * dal vivo sul visualizer. Toccare un preset nella lista lo carica SUBITO
 * (via onPreviewPreset, che MainActivity implementa con glView.queueEvent),
 * quindi si vede il risultato mentre si continua a scorrere la lista sotto.
 */
class PresetPickerDialog(
    private val onPreviewPreset: (String) -> Unit
) : DialogFragment() {

    private lateinit var repository: PresetRepository
    private lateinit var prefs: Prefs
    private lateinit var adapter: PresetAdapter

    private var editingPlaylist: String? = null
    private val checkedPaths = mutableSetOf<String>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext(), R.style.Theme_ProjectMOverlay_TransparentDialog)
        val root = LayoutInflater.from(requireContext()).inflate(R.layout.activity_preset_browser, null)
        dialog.setContentView(root)

        repository = PresetRepository(requireContext())
        prefs = Prefs(requireContext())

        val recycler = root.findViewById<RecyclerView>(R.id.presetRecycler)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        adapter = PresetAdapter(
            items = repository.allPresets(),
            playlistEditMode = { editingPlaylist != null },
            checkedPaths = checkedPaths,
            onToggleFavorite = { entry ->
                repository.setFavorite(entry.path, !entry.favorite)
                refreshList(root)
            },
            onCheckedChanged = { entry, checked ->
                if (checked) checkedPaths.add(entry.path) else checkedPaths.remove(entry.path)
            },
            onAddToPlaylist = { entry -> showAddToPlaylistDialog(entry) },
            onClick = { entry ->
                // anteprima LIVE: carica subito sul visualizer sottostante,
                // visibile nella "finestra" di schermo sopra a questo dialog
                onPreviewPreset(entry.path)
                prefs.manualPresetPath = entry.path
                prefs.playbackMode = PlaybackMode.MANUAL_SINGLE
            }
        )
        recycler.adapter = adapter

        root.findViewById<CheckBox>(R.id.onlyFavoritesCheck).setOnCheckedChangeListener { _, _ -> refreshList(root) }
        root.findViewById<Button>(R.id.btnNewPlaylist).setOnClickListener { promptNewPlaylistName(root) }
        root.findViewById<Button>(R.id.btnSavePlaylist).setOnClickListener { saveEditingPlaylist(root) }

        refreshList(root)

        dialog.window?.let { w ->
            // sfondo semi-trasparente SOLO dietro alla lista (leggibilità);
            // la parte di schermo fuori dai bordi di questa finestra resta
            // scoperta e nitida sul visualizer, perché l'altezza qui sotto è
            // volutamente meno del 100% e l'ancoraggio è in basso.
            w.setBackgroundDrawable(ColorDrawable(Color.parseColor("#DD000000")))
            w.setGravity(Gravity.BOTTOM)
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
            val attrs = w.attributes
            attrs.height = (resources.displayMetrics.heightPixels * 0.62).toInt()
            w.attributes = attrs
        }
        return dialog
    }

    private fun promptNewPlaylistName(root: View) {
        val input = EditText(requireContext())
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.new_playlist)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) startEditingPlaylist(name, root)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun startEditingPlaylist(name: String, root: View) {
        editingPlaylist = name
        checkedPaths.clear()
        checkedPaths.addAll(repository.playlist(name))
        root.findViewById<LinearLayout>(R.id.playlistEditBar).visibility = View.VISIBLE
        root.findViewById<TextView>(R.id.playlistEditLabel).text = name
        refreshList(root)
    }

    private fun saveEditingPlaylist(root: View) {
        val name = editingPlaylist ?: return
        repository.savePlaylist(name, checkedPaths.toList())
        editingPlaylist = null
        root.findViewById<LinearLayout>(R.id.playlistEditBar).visibility = View.GONE
        Toast.makeText(requireContext(), "\"$name\" salvata (${checkedPaths.size} preset)", Toast.LENGTH_SHORT).show()
        refreshList(root)
    }

    private fun showAddToPlaylistDialog(entry: PresetEntry) {
        val existing = repository.playlistNames()
        val options = existing + getString(R.string.new_playlist)
        AlertDialog.Builder(requireContext())
            .setTitle(entry.title)
            .setItems(options.toTypedArray()) { _, which ->
                if (which == existing.size) {
                    val input = EditText(requireContext())
                    AlertDialog.Builder(requireContext())
                        .setTitle(R.string.new_playlist)
                        .setView(input)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            val name = input.text.toString().trim()
                            if (name.isNotEmpty()) {
                                repository.addToPlaylist(name, entry.path)
                                Toast.makeText(requireContext(), "Aggiunto a \"$name\"", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                } else {
                    repository.addToPlaylist(existing[which], entry.path)
                    Toast.makeText(requireContext(), "Aggiunto a \"${existing[which]}\"", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun refreshList(root: View) {
        val onlyFavorites = root.findViewById<CheckBox>(R.id.onlyFavoritesCheck).isChecked
        val list = if (onlyFavorites) repository.favoritePresets() else repository.allPresets()
        adapter.submitList(list)
    }
}
