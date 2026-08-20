package com.asfaltosonoro.projectmoverlay

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * Tutto quello che riguarda i preset in un unico posto (prima era mischiato
 * nel menu Impostazioni generale, diventato troppo lungo): importazione,
 * download pacchetti, browser, e modalità di scorrimento/playlist.
 */
class PresetSettingsActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var repository: PresetRepository

    private val pickImportFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@registerForActivityResult
        contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        prefs.importedFolderUri = uri
        Toast.makeText(this, "Importazione in corso…", Toast.LENGTH_SHORT).show()
        repository.importFolder(uri) { count, error ->
            runOnUiThread {
                if (error != null) {
                    Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Importati $count preset", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_ProjectMOverlay_Settings)
        setContentView(R.layout.activity_preset_settings)

        prefs = Prefs(this)
        repository = PresetRepository(this)

        setupPresetsSection()
        setupPlaybackModeSection()
    }

    private fun setupPresetsSection() {
        findViewById<Button>(R.id.btnImportPresets).setOnClickListener {
            pickImportFolder.launch(null)
        }
        findViewById<Button>(R.id.btnOpenBrowser).setOnClickListener {
            startActivity(Intent(this, PresetBrowserActivity::class.java))
        }

        val container = findViewById<android.widget.LinearLayout>(R.id.presetPacksContainer)
        container.removeAllViews()
        PresetRepository.AVAILABLE_PACKS.forEach { pack ->
            val button = Button(this).apply {
                text = "${pack.displayName} (${pack.sizeLabel})"
                setOnClickListener { downloadPack(pack, this) }
            }
            container.addView(button)

            val description = android.widget.TextView(this).apply {
                text = pack.description
                textSize = 12f
                setPadding(0, 0, 0, 12)
            }
            container.addView(description)
        }
    }

    private fun downloadPack(pack: PresetPack, button: Button) {
        button.isEnabled = false
        val originalText = "${pack.displayName} (${pack.sizeLabel})"
        button.text = "Download 0%…"
        Toast.makeText(this, "Download di \"${pack.displayName}\" avviato", Toast.LENGTH_SHORT).show()

        repository.downloadAndExtractPresetPack(
            pack.downloadUrl,
            onProgress = { percent ->
                runOnUiThread { button.text = "Download $percent%…" }
            },
            onFinished = { count, error ->
                runOnUiThread {
                    button.isEnabled = true
                    button.text = originalText
                    if (error != null) {
                        Toast.makeText(
                            this,
                            "Errore scaricando \"${pack.displayName}\": $error",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(this, "Importati $count preset da \"${pack.displayName}\"", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    private fun setupPlaybackModeSection() {
        val modeSpinner = findViewById<Spinner>(R.id.playbackModeSpinner)
        val modeLabels = listOf(
            "Casuale tra i preferiti",
            "Casuale tra tutti i preset",
            "Playlist in ordine",
            "Playlist in ordine sparso",
            "Singolo preset (cambio manuale)"
        )
        modeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, modeLabels)
        modeSpinner.setSelection(PlaybackMode.entries.indexOf(prefs.playbackMode))

        val playlistSpinner = findViewById<Spinner>(R.id.playlistSpinner)
        refreshPlaylists(playlistSpinner)

        modeSpinner.onItemSelectedListener = onItemSelected { position ->
            prefs.playbackMode = PlaybackMode.entries[position]
        }
        playlistSpinner.onItemSelectedListener = onItemSelected { position ->
            repository.playlistNames().getOrNull(position)?.let { prefs.activePlaylistName = it }
        }

        val seekDuration = findViewById<SeekBar>(R.id.seekDuration)
        seekDuration.progress = prefs.presetDurationSeconds
        seekDuration.setOnSeekBarChangeListener(simpleSeekListener { prefs.presetDurationSeconds = maxOf(it, 3) })

        val manualNavRandomCheck = findViewById<CheckBox>(R.id.manualNavRandomCheck)
        manualNavRandomCheck.isChecked = prefs.manualNavRandom
        manualNavRandomCheck.setOnCheckedChangeListener { _, checked -> prefs.manualNavRandom = checked }

        val beatSyncCheck = findViewById<CheckBox>(R.id.beatSyncCheck)
        beatSyncCheck.isChecked = prefs.beatSyncEnabled
        beatSyncCheck.setOnCheckedChangeListener { _, checked -> prefs.beatSyncEnabled = checked }

        val seekBeatSyncN = findViewById<SeekBar>(R.id.seekBeatSyncN)
        seekBeatSyncN.progress = prefs.beatSyncEveryNBeats - 1 // slider parte da 0, valore minimo è 1 colpo
        seekBeatSyncN.setOnSeekBarChangeListener(simpleSeekListener { prefs.beatSyncEveryNBeats = it + 1 })

        // 0..100 → 0.0..10.0 secondi
        val seekTransition = findViewById<SeekBar>(R.id.seekTransitionDuration)
        seekTransition.progress = (prefs.transitionDurationSeconds * 10).toInt()
        seekTransition.setOnSeekBarChangeListener(simpleSeekListener { prefs.transitionDurationSeconds = it / 10f })
    }

    private fun refreshPlaylists(spinner: Spinner) {
        val names = repository.playlistNames()
        val labels = names.ifEmpty { listOf("Nessuna playlist creata") }
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        val idx = names.indexOf(prefs.activePlaylistName)
        if (idx >= 0) spinner.setSelection(idx)
    }

    override fun onResume() {
        super.onResume()
        refreshPlaylists(findViewById(R.id.playlistSpinner))
    }

    private fun simpleSeekListener(onChange: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            if (fromUser) onChange(progress)
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    }

    private fun onItemSelected(onSelected: (Int) -> Unit) = object : android.widget.AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
            onSelected(position)
        }
        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
    }
}
