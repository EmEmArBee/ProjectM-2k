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

    // false se la libreria nativa non si è caricata: in quel caso l'app resta
    // aperta (niente visualizer, niente crash) mostrando la diagnostica.
    private var nativeLibraryOk = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showLastCrashIfAny() // mostra crash Java ed eventuale boot log del run precedente
        BootLog.reset(this)
        BootLog.log(this, "onCreate: inizio")
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)
        BootLog.log(this, "onCreate: layout impostato")

        // Proviamo a caricare la libreria nativa PRIMA di toccare ProjectMBridge
        // in qualsiasi altro punto, così se fallisce lo catturiamo qui invece
        // di far crashare tutta l'app.
        val nativeDiag = NativeLibDiagnostics.tryLoadAndDiagnose(this)
        nativeLibraryOk = nativeDiag == null
        if (nativeDiag != null) {
            BootLog.log(this, "onCreate: libreria nativa NON caricata:\n$nativeDiag")
        } else {
            BootLog.log(this, "onCreate: libreria nativa caricata OK")
        }

        prefs = Prefs(this)
        repository = PresetRepository(this).apply { ensureBundledPresetsCopied() }
        BootLog.log(this, "onCreate: prefs e repository pronti")
        playback = PlaybackController(repository, prefs)

        val bassAnalyzer = BassEnergyAnalyzer(sampleRate = 44100)
        audioEngine = AudioEngine(this, bassAnalyzer) { level -> applyPulse(level) }
        BootLog.log(this, "onCreate: playback controller e audio engine creati")

        glView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(3)
            if (nativeLibraryOk) {
                setRenderer(ProjectMRenderer(this@MainActivity))
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            }
            // se la libreria nativa non c'è, niente renderer: la view resta
            // semplicemente nera, ma non tocca ProjectMBridge e non crasha.
        }
        BootLog.log(this, "onCreate: GLSurfaceView creata (il render vero parte async, vedi righe 'renderer.*' sotto)")
        findViewById<FrameLayout>(R.id.glContainer).addView(glView)
        BootLog.log(this, "onCreate: GLSurfaceView aggiunta al layout")

        logoView = findViewById(R.id.logoOverlay)
        applyLogoFromPrefs()
        BootLog.log(this, "onCreate: logo applicato")

        applyFullscreenPref()

        setupGestures()
        BootLog.log(this, "onCreate: gesti configurati")
        requestAudioPermissionAndStart()
        BootLog.log(this, "onCreate: richiesta permesso audio avviata")

        if (nativeLibraryOk) {
            playback.start()
            BootLog.log(this, "onCreate: FINE (playback.start chiamato)")
        } else {
            BootLog.log(this, "onCreate: FINE (playback.start SALTATO, libreria nativa mancante)")
            showNativeLibDiagnosticDialog(nativeDiag!!)
        }
    }

    private fun showNativeLibDiagnosticDialog(diag: String) {
        val textView = TextView(this).apply {
            setText(diag)
            setPadding(32, 32, 32, 32)
            setTextIsSelectable(true)
            movementMethod = ScrollingMovementMethod()
        }
        AlertDialog.Builder(this)
            .setTitle("Il visualizer non parte — diagnostica (tieni premuto sul testo per copiarlo)")
            .setView(textView)
            .setPositiveButton(android.R.string.ok, null)
            .setCancelable(false)
            .show()
    }

    // --- doppio tap: sinistra = preset precedente, destra = successivo,
    // centro = apri impostazioni --------------------------------------
    private fun setupGestures() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                val third = glView.width / 3f
                when {
                    e.x < third -> if (nativeLibraryOk) playback.previous()
                    e.x > third * 2 -> if (nativeLibraryOk) playback.next()
                    else -> startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                }
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
        if (!nativeLibraryOk) return super.onKeyDown(keyCode, event)
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
        logoView.scaleX = prefs.logoScale
        logoView.scaleY = prefs.logoScale
    }

    private fun applyPulse(level: Float) {
        val intensity = prefs.pulseIntensity
        val base = prefs.logoScale
        when (prefs.pulseVisual) {
            PulseVisual.SCALE -> {
                val scale = base * (1f + intensity * level * 0.6f)
                logoView.scaleX = scale
                logoView.scaleY = scale
            }
            PulseVisual.OPACITY -> {
                logoView.scaleX = base
                logoView.scaleY = base
                logoView.alpha = (0.35f + intensity * level * 0.65f).coerceIn(0f, 1f)
            }
            PulseVisual.BOTH -> {
                val scale = base * (1f + intensity * level * 0.6f)
                logoView.scaleX = scale
                logoView.scaleY = scale
                logoView.alpha = (0.35f + intensity * level * 0.65f).coerceIn(0f, 1f)
            }
        }
    }

    private fun showLastCrashIfAny() {
        val crashFile = File(filesDir, "crash_log.txt")
        val javaCrash = if (crashFile.exists()) crashFile.readText().also { crashFile.delete() } else null
        val bootLog = BootLog.readPreviousRunLog(this)

        if (javaCrash == null && bootLog == null) return

        val combined = buildString {
            if (javaCrash != null) {
                appendLine("=== ECCEZIONE JAVA ===")
                appendLine(javaCrash)
                appendLine()
            }
            if (bootLog != null) {
                appendLine("=== LOG DELL'AVVIO PRECEDENTE (l'ultima riga è dove si è fermato) ===")
                appendLine(bootLog)
            }
        }

        val textView = TextView(this).apply {
            setText(combined)
            setPadding(32, 32, 32, 32)
            setTextIsSelectable(true)
            movementMethod = ScrollingMovementMethod()
        }
        AlertDialog.Builder(this)
            .setTitle("Info sull'avvio precedente — tieni premuto sul testo per copiarlo")
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
        audioEngine.start(prefs.audioSource, prefs.usbDeviceId, prefs.internalPlayerUri, prefs.audioGain)
    }

    private fun applyFullscreenPref() {
        val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        if (prefs.fullscreenImmersive) {
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
            controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, true)
            controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyFullscreenPref()
    }

    override fun onResume() {
        super.onResume()
        if (nativeLibraryOk) glView.onResume()
        // le impostazioni potrebbero essere cambiate (sorgente audio, modalità, logo, playlist, schermo)
        applyLogoFromPrefs()
        applyFullscreenPref()
        startAudioEngine()
        if (nativeLibraryOk) {
            playback.onModeOrPlaylistChanged()
        }
    }

    override fun onPause() {
        if (nativeLibraryOk) glView.onPause()
        audioEngine.stop()
        super.onPause()
    }

    override fun onDestroy() {
        playback.stop()
        audioEngine.stop()
        if (nativeLibraryOk) {
            ProjectMBridge.nativeDestroy()
        }
        super.onDestroy()
    }
}
