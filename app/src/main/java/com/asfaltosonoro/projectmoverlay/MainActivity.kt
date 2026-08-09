package com.asfaltosonoro.projectmoverlay

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var glView: GLSurfaceView
    private lateinit var logoView: ImageView
    private lateinit var repository: PresetRepository
    private lateinit var prefs: Prefs
    private lateinit var playback: PlaybackController
    private lateinit var audioEngine: AudioEngine
    private lateinit var gestureDetector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showLastCrashIfAny()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        prefs = Prefs(this)
        repository = PresetRepository(this).apply { ensureBundledPresetsCopied() }
        playback = PlaybackController(repository, prefs)

        val bassAnalyzer = BassEnergyAnalyzer(sampleRate = 44100)
        audioEngine = AudioEngine(this, bassAnalyzer) { level -> applyPulse(level) }

        glView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(3)
            setRenderer(ProjectMRenderer())
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
        findViewById<FrameLayout>(R.id.glContainer).addView(glView)

        logoView = findViewById(R.id.logoOverlay)
        applyLogoFromPrefs()

        findViewById<ImageButton>(R.id.settingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        setupGestures()
        requestAudioPermissionAndStart()
        playback.start()
    }

    // --- doppio tap: destra = next preset, sinistra = previous preset -----
    private fun setupGestures() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                val screenMidpoint = glView.width / 2f
                if (e.x >= screenMidpoint) playback.next() else playback.previous()
                return true
            }
        })
        glView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    // --- tastiera USB/bluetooth: frecce sinistra/destra --------------------
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> { playback.next(); true }
            KeyEvent.KEYCODE_DPAD_LEFT -> { playback.previous(); true }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun applyLogoFromPrefs() {
        prefs.logoUri?.let { uri ->
            try {
                logoView.setImageURI(uri)
            } catch (_: SecurityException) {
                // permesso persistente perso (es. cartella rimossa): ignora, l'utente ricarica dal menu
            }
        }
    }

    private fun applyPulse(level: Float) {
        val intensity = prefs.pulseIntensity
        when (prefs.pulseVisual) {
            PulseVisual.SCALE -> {
                val scale = 1f + intensity * level * 0.6f
                logoView.scaleX = scale
                logoView.scaleY = scale
            }
            PulseVisual.OPACITY -> {
                logoView.alpha = (0.35f + intensity * level * 0.65f).coerceIn(0f, 1f)
            }
            PulseVisual.BOTH -> {
                val scale = 1f + intensity * level * 0.6f
                logoView.scaleX = scale
                logoView.scaleY = scale
                logoView.alpha = (0.35f + intensity * level * 0.65f).coerceIn(0f, 1f)
            }
        }
    }

    private fun showLastCrashIfAny() {
        val logFile = File(filesDir, "crash_log.txt")
        if (!logFile.exists()) return
        val text = logFile.readText()
        logFile.delete()

        val textView = TextView(this).apply {
            setText(text)
            setPadding(32, 32, 32, 32)
            setTextIsSelectable(true)
            movementMethod = ScrollingMovementMethod()
        }
        AlertDialog.Builder(this)
            .setTitle("L'app si era chiusa per un errore — tieni premuto sul testo per copiarlo")
            .setView(textView)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun requestAudioPermissionAndStart() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 42)
        } else {
            startAudioEngine()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 42 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startAudioEngine()
        }
    }

    private fun startAudioEngine() {
        audioEngine.start(prefs.audioSource, prefs.usbDeviceId, prefs.internalPlayerUri)
    }

    override fun onResume() {
        super.onResume()
        glView.onResume()
        // le impostazioni potrebbero essere cambiate (sorgente audio, modalità, logo, playlist)
        applyLogoFromPrefs()
        startAudioEngine()
        playback.onModeOrPlaylistChanged()
    }

    override fun onPause() {
        glView.onPause()
        audioEngine.stop()
        super.onPause()
    }

    override fun onDestroy() {
        playback.stop()
        audioEngine.stop()
        ProjectMBridge.nativeDestroy()
        super.onDestroy()
    }
}
