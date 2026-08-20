package com.asfaltosonoro.projectmoverlay

import android.content.Intent
import android.media.AudioDeviceInfo
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var repository: PresetRepository
    private lateinit var audioEngine: AudioEngine

    private val pickLogo = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        prefs.logoUri = uri
        Toast.makeText(this, "Logo aggiornato", Toast.LENGTH_SHORT).show()
    }

    private val pickInternalAudio = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        prefs.internalPlayerUri = uri
        Toast.makeText(this, "File audio impostato", Toast.LENGTH_SHORT).show()
    }

    private val exportBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@registerForActivityResult
        try {
            contentResolver.openOutputStream(uri)?.use { it.write(repository.exportBackupJson().toByteArray()) }
            Toast.makeText(this, "Backup esportato", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Errore esportazione: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private val importBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        try {
            val json = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (json != null) {
                repository.importBackupJson(json)
                Toast.makeText(this, "Backup importato", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Errore importazione: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_ProjectMOverlay_Settings)
        setContentView(R.layout.activity_settings)

        prefs = Prefs(this)
        repository = PresetRepository(this)
        audioEngine = AudioEngine(this, BassEnergyAnalyzer(44100)) {}

        setupLogoSection()
        setupPulseSection()
        setupAudioSourceSection()
        setupPresetSettingsLink()
        setupBackupSection()
        setupDisplaySection()
    }

    private fun setupLogoSection() {
        findViewById<Button>(R.id.btnPickLogo).setOnClickListener {
            pickLogo.launch(arrayOf("image/png"))
        }
        findViewById<Button>(R.id.btnRemoveLogo).setOnClickListener {
            prefs.logoUri = null
            Toast.makeText(this, "Logo rimosso", Toast.LENGTH_SHORT).show()
        }
        // 100 = scala 1.0x (dimensione base), range 10%..250%
        val seekLogoScale = findViewById<SeekBar>(R.id.seekLogoScale)
        seekLogoScale.progress = (prefs.logoScale * 100).toInt()
        seekLogoScale.setOnSeekBarChangeListener(simpleSeekListener {
            prefs.logoScale = maxOf(it, 10) / 100f
        })

        val seekLogoAlpha = findViewById<SeekBar>(R.id.seekLogoAlpha)
        seekLogoAlpha.progress = (prefs.logoBaseAlpha * 100).toInt()
        seekLogoAlpha.setOnSeekBarChangeListener(simpleSeekListener { prefs.logoBaseAlpha = it / 100f })
    }

    private fun setupPulseSection() {
        val group = findViewById<RadioGroup>(R.id.pulseVisualGroup)
        when (prefs.pulseVisual) {
            PulseVisual.SCALE -> group.check(R.id.radioScale)
            PulseVisual.OPACITY -> group.check(R.id.radioOpacity)
            PulseVisual.BOTH -> group.check(R.id.radioBoth)
        }
        group.setOnCheckedChangeListener { _, checkedId ->
            prefs.pulseVisual = when (checkedId) {
                R.id.radioOpacity -> PulseVisual.OPACITY
                R.id.radioBoth -> PulseVisual.BOTH
                else -> PulseVisual.SCALE
            }
        }

        val seekIntensity = findViewById<SeekBar>(R.id.seekIntensity)
        seekIntensity.progress = (prefs.pulseIntensity * 100).toInt()
        seekIntensity.setOnSeekBarChangeListener(simpleSeekListener { prefs.pulseIntensity = it / 100f })

        val seekSpeed = findViewById<SeekBar>(R.id.seekSpeed)
        seekSpeed.progress = (prefs.pulseSpeed * 100).toInt()
        seekSpeed.setOnSeekBarChangeListener(simpleSeekListener { prefs.pulseSpeed = it / 100f })

        val seekBeatThreshold = findViewById<SeekBar>(R.id.seekBeatThreshold)
        seekBeatThreshold.progress = (prefs.beatDetectionThreshold * 100).toInt()
        seekBeatThreshold.setOnSeekBarChangeListener(simpleSeekListener { prefs.beatDetectionThreshold = it / 100f })
    }

    private fun setupAudioSourceSection() {
        val sourceSpinner = findViewById<Spinner>(R.id.audioSourceSpinner)
        val sourceLabels = listOf("Microfono interno", "Scheda audio USB", "Player interno")
        sourceSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, sourceLabels)
        sourceSpinner.setSelection(AudioSourceType.entries.indexOf(prefs.audioSource))

        val usbSpinner = findViewById<Spinner>(R.id.usbDeviceSpinner)
        refreshUsbDevices(usbSpinner)

        sourceSpinner.onItemSelectedListener = onItemSelected { position ->
            prefs.audioSource = AudioSourceType.entries[position]
            if (prefs.audioSource == AudioSourceType.USB) refreshUsbDevices(usbSpinner)
        }
        usbSpinner.onItemSelectedListener = onItemSelected { position ->
            val devices = audioEngine.availableUsbInputs()
            devices.getOrNull(position)?.let { prefs.usbDeviceId = it.id }
        }

        findViewById<Button>(R.id.btnPickInternalAudio).setOnClickListener {
            pickInternalAudio.launch(arrayOf("audio/*"))
        }

        // 100 = gain 1.0x (nessuna amplificazione), range 0x..3x
        val seekGain = findViewById<SeekBar>(R.id.seekGain)
        seekGain.progress = (prefs.audioGain * 100).toInt()
        seekGain.setOnSeekBarChangeListener(simpleSeekListener { prefs.audioGain = it / 100f })
    }

    private fun setupPresetSettingsLink() {
        findViewById<Button>(R.id.btnOpenPresetSettings).setOnClickListener {
            startActivity(Intent(this, PresetSettingsActivity::class.java))
        }
    }

    private fun setupBackupSection() {
        findViewById<Button>(R.id.btnExportBackup).setOnClickListener {
            exportBackup.launch("projectm_overlay_backup.json")
        }
        findViewById<Button>(R.id.btnImportBackup).setOnClickListener {
            importBackup.launch(arrayOf("application/json"))
        }
    }

    private fun setupDisplaySection() {
        val fullscreenCheck = findViewById<CheckBox>(R.id.fullscreenCheck)
        fullscreenCheck.isChecked = prefs.fullscreenImmersive
        fullscreenCheck.setOnCheckedChangeListener { _, checked -> prefs.fullscreenImmersive = checked }

        val performanceModeCheck = findViewById<CheckBox>(R.id.performanceModeCheck)
        performanceModeCheck.isChecked = prefs.performanceMode
        performanceModeCheck.setOnCheckedChangeListener { _, checked -> prefs.performanceMode = checked }

        // slider 0..960 → risoluzione 320..1280px
        val seekPerformanceResolution = findViewById<SeekBar>(R.id.seekPerformanceResolution)
        seekPerformanceResolution.progress = (prefs.performanceTargetWidth - 320).coerceIn(0, 960)
        seekPerformanceResolution.setOnSeekBarChangeListener(simpleSeekListener {
            prefs.performanceTargetWidth = 320 + it
        })
    }

    private fun refreshUsbDevices(spinner: Spinner) {
        val devices = audioEngine.availableUsbInputs()
        val labels = if (devices.isEmpty()) listOf("Nessuna scheda USB rilevata")
        else devices.map { it.productName?.toString() ?: "USB device ${it.id}" }
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        val idx = devices.indexOfFirst { it.id == prefs.usbDeviceId }
        if (idx >= 0) spinner.setSelection(idx)
    }

    // --- helper -------------------------------------------------------
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
