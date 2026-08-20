package com.asfaltosonoro.projectmoverlay

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.File

class MainActivity : AppCompatActivity() {

    companion object {
        private const val BASE_LOGO_SIZE_DP = 200
        // fattore massimo di ingrandimento della pulsazione (deve combaciare
        // con la formula in applyPulse: 1 + intensity*level*0.6, intensity e
        // level entrambi al massimo 1.0)
        private const val MAX_PULSE_MULTIPLIER = 1.6f
    }

    private lateinit var glView: GLSurfaceView
    private lateinit var logoView: ImageView
    private lateinit var favoriteFlash: ImageView
    private lateinit var bassAnalyzer: BassEnergyAnalyzer
    private lateinit var repository: PresetRepository
    private lateinit var prefs: Prefs
    private lateinit var playback: PlaybackController
    private lateinit var audioEngine: AudioEngine
    private lateinit var gestureDetector: GestureDetector

    // false se la libreria nativa non si è caricata: in quel caso l'app resta
    // aperta (niente visualizer, niente crash) mostrando la diagnostica.
    private var nativeLibraryOk = true

    // contatori per la misura dell'fps reale nei primi secondi (vedi onFrameRendered)
    private var perfSampleCount = 0
    private var perfSampleTotalNanos = 0L

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

        bassAnalyzer = BassEnergyAnalyzer(sampleRate = 44100).apply { beatThreshold = prefs.beatDetectionThreshold }
        audioEngine = AudioEngine(this, bassAnalyzer) { level ->
            applyPulse(level)
            if (bassAnalyzer.lastWasBeat && nativeLibraryOk && ::playback.isInitialized) {
                playback.onBeatDetected()
            }
        }
        BootLog.log(this, "onCreate: audio engine creato")

        glView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(3)
            if (nativeLibraryOk) {
                setRenderer(ProjectMRenderer(
                    this@MainActivity,
                    onSurfaceReady = {
                        // il contesto OpenGL è stato (ri)creato: se avevamo già un
                        // preset caricato in precedenza, ripristinalo subito invece
                        // di lasciare quello predefinito di projectM.
                        ProjectMBridge.nativeSetTransitionDuration(prefs.transitionDurationSeconds)
                        if (::playback.isInitialized) {
                            playback.currentPresetPath()?.let { path ->
                                ProjectMBridge.nativeLoadPresetFile(path, false)
                            }
                        }
                    },
                    onFrameRendered = { deltaNanos -> onFrameRendered(deltaNanos) }
                ))
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            }
            // se la libreria nativa non c'è, niente renderer: la view resta
            // semplicemente nera, ma non tocca ProjectMBridge e non crasha.
        }
        BootLog.log(this, "onCreate: GLSurfaceView creata (il render vero parte async, vedi righe 'renderer.*' sotto)")

        // IMPORTANTE: projectM va toccato solo dal thread GL (stesso thread di
        // nativeInit/nativeRenderFrame). Passiamo queueEvent al controller così
        // il cambio preset viene eseguito lì e non sul thread UI (prima il
        // preset non cambiava mai davvero per questo motivo).
        playback = PlaybackController(repository, prefs, runOnGlThread = { glView.queueEvent(it) })
        BootLog.log(this, "onCreate: playback controller creato")
        findViewById<FrameLayout>(R.id.glContainer).addView(glView)
        BootLog.log(this, "onCreate: GLSurfaceView aggiunta al layout")
        applyPerformanceModePref()

        logoView = findViewById(R.id.logoOverlay)
        logoView.setLayerType(android.view.View.LAYER_TYPE_NONE, null)
        favoriteFlash = findViewById(R.id.favoriteFlash)
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
    // centro = apri impostazioni. Tieni premuto (long-press): al centro =
    // aggiungi/rimuovi rapido dai preferiti, ai lati = finestra live sui
    // preset (vedi PresetPickerDialog) ----------------------------------
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

            override fun onLongPress(e: MotionEvent) {
                val third = glView.width / 3f
                if (e.x in third..(third * 2)) {
                    if (nativeLibraryOk) toggleFavoriteCurrentPreset()
                } else {
                    PresetPickerDialog { path ->
                        if (nativeLibraryOk) glView.queueEvent {
                            ProjectMBridge.nativeLoadPresetFile(path, true)
                        }
                    }.show(supportFragmentManager, "preset_picker")
                }
            }
        })
        glView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    /** Aggiunge/rimuove dai preferiti il preset attualmente in riproduzione,
     * con un flash della stellina a schermo come conferma visiva. */
    private fun toggleFavoriteCurrentPreset() {
        val path = playback.currentPresetPath() ?: return
        val nowFavorite = !repository.isFavorite(path)
        repository.setFavorite(path, nowFavorite)
        showFavoriteFlash(nowFavorite)
    }

    private fun showFavoriteFlash(added: Boolean) {
        favoriteFlash.setImageResource(
            if (added) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off
        )
        favoriteFlash.animate().cancel()
        favoriteFlash.alpha = 0f
        favoriteFlash.scaleX = 0.6f
        favoriteFlash.scaleY = 0.6f
        favoriteFlash.animate()
            .alpha(1f).scaleX(1.15f).scaleY(1.15f)
            .setDuration(150)
            .withEndAction {
                favoriteFlash.animate().alpha(0f).setStartDelay(350).setDuration(300).start()
            }.start()
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
        val uri = prefs.logoUri
        if (uri != null) {
            if (!loadLogoBitmapSafely(uri)) {
                logoView.setImageDrawable(null)
                Toast.makeText(
                    this,
                    "Non riesco a caricare questo logo (memoria insufficiente o file non valido)",
                    Toast.LENGTH_LONG
                ).show()
            }
        } else {
            logoView.setImageDrawable(null) // "Rimuovi logo" dalle Impostazioni
        }
        applyLogoBaseSize()
        logoView.alpha = prefs.logoBaseAlpha
    }

    /** Decodifica il PNG del logo GIÀ ridotto alla dimensione che serve
     * davvero (invece di lasciare che ImageView decodifichi l'immagine
     * originale a piena risoluzione e poi la scali): su dispositivi con poca
     * memoria (head unit economiche, telefoni datati) un PNG grande poteva
     * causare un crash per OutOfMemoryError. Non fa mai crashare l'app: se
     * qualcosa va storto ritorna semplicemente false. */
    private fun loadLogoBitmapSafely(uri: Uri): Boolean {
        return try {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return false
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

            // margine oltre la dimensione massima che il logo potrà mai raggiungere
            // (zoom massimo × margine di pulsazione), non ha senso tenere più risoluzione di così
            val targetPx = (BASE_LOGO_SIZE_DP * resources.displayMetrics.density * 2.5f * MAX_PULSE_MULTIPLIER).toInt()
            var sampleSize = 1
            while (bounds.outWidth / (sampleSize * 2) >= targetPx || bounds.outHeight / (sampleSize * 2) >= targetPx) {
                sampleSize *= 2
            }
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions) ?: return false
            logoView.setImageBitmap(bitmap)
            true
        } catch (_: OutOfMemoryError) {
            false
        } catch (_: Exception) {
            false
        }
    }

    /** Dimensione "base" del logo (dallo slider Impostazioni). La view viene
     * dimensionata GIÀ al massimo che potrà mai raggiungere (base × margine
     * di pulsazione), rispettando le proporzioni REALI dell'immagine (non
     * forzando un quadrato: un logo largo come un banner altrimenti verrebbe
     * "adattato" dentro una forma diversa dalla sua, col rischio di margini
     * di arrotondamento che lo tagliano ai lati). Parte rimpicciolita a
     * riposo: la pulsazione cresce verso le dimensioni reali della view
     * invece di superarle, quindi non viene mai tagliata. */
    private fun applyLogoBaseSize() {
        val needsHeadroom = prefs.pulseVisual != PulseVisual.OPACITY
        val headroom = if (needsHeadroom) MAX_PULSE_MULTIPLIER else 1f
        val maxDimensionPx = BASE_LOGO_SIZE_DP * resources.displayMetrics.density * prefs.logoScale * headroom

        val drawable = logoView.drawable
        val aspect = if (drawable != null && drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
            drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight
        } else 1f // nessun logo caricato ancora: quadrato di default

        val (widthPx, heightPx) = if (aspect >= 1f) {
            maxDimensionPx to maxDimensionPx / aspect
        } else {
            maxDimensionPx * aspect to maxDimensionPx
        }

        val params = logoView.layoutParams
        params.width = widthPx.toInt()
        params.height = heightPx.toInt()
        logoView.layoutParams = params
        val restScale = if (needsHeadroom) 1f / MAX_PULSE_MULTIPLIER else 1f
        logoView.scaleX = restScale
        logoView.scaleY = restScale
    }

    private fun applyPulse(level: Float) {
        val intensity = prefs.pulseIntensity
        val baseAlpha = prefs.logoBaseAlpha
        val pulseFactor = 1f + intensity * level * 0.6f // 1.0 (riposo) .. 1.6 (picco)
        when (prefs.pulseVisual) {
            PulseVisual.SCALE -> {
                val scale = pulseFactor / MAX_PULSE_MULTIPLIER // resta sempre entro i bound reali della view
                logoView.scaleX = scale
                logoView.scaleY = scale
                // bug corretto: prima, passando da "Entrambi" a "Scala", l'opacità
                // restava "congelata" all'ultimo valore lasciato dalla pulsazione
                // precedente invece di tornare a quella base impostata.
                logoView.alpha = baseAlpha
            }
            PulseVisual.OPACITY -> {
                logoView.scaleX = 1f
                logoView.scaleY = 1f
                logoView.alpha = (baseAlpha * (0.35f + intensity * level * 0.65f)).coerceIn(0f, 1f)
            }
            PulseVisual.BOTH -> {
                val scale = pulseFactor / MAX_PULSE_MULTIPLIER
                logoView.scaleX = scale
                logoView.scaleY = scale
                logoView.alpha = (baseAlpha * (0.35f + intensity * level * 0.65f)).coerceIn(0f, 1f)
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

    /** Misura l'fps REALE nei primi ~90 frame (invece di indovinarlo da RAM/
     * core, che su una head unit con CPU discreta ma GPU debolissima porta
     * a conclusioni sbagliate) e, se è troppo basso, attiva da sola la
     * modalità prestazioni — solo al primissimo avvio, solo se l'utente non
     * l'ha già impostata a mano, e senza mai disattivarla da sola. */
    private fun onFrameRendered(deltaNanos: Long) {
        if (prefs.performanceAutoDetected) return
        perfSampleCount++
        perfSampleTotalNanos += deltaNanos
        if (perfSampleCount < 90) return

        val avgMs = (perfSampleTotalNanos / perfSampleCount) / 1_000_000.0
        val fps = 1000.0 / avgMs
        prefs.performanceAutoDetected = true
        if (fps < 24.0 && !prefs.performanceMode) {
            prefs.performanceMode = true
            runOnUiThread {
                applyPerformanceModePref()
                Toast.makeText(
                    this,
                    "Dispositivo lento rilevato (~${fps.toInt()} fps): attivata la modalità prestazioni. Puoi disattivarla dalle Impostazioni.",
                    Toast.LENGTH_LONG
                ).show()
            }
            BootLog.log(this, "perf: fps misurato ~${fps.toInt()}, modalità prestazioni attivata automaticamente")
        } else {
            BootLog.log(this, "perf: fps misurato ~${fps.toInt()}, nessuna modifica automatica")
        }
    }

    /** In modalità prestazioni, il visualizer viene renderizzato a una
     * risoluzione interna ridotta e poi scalato per riempire lo schermo:
     * costa molto meno alla GPU, utile su dispositivi lenti (il render è
     * comunque a schermo intero, solo con meno dettaglio). */
    private fun applyPerformanceModePref() {
        if (!nativeLibraryOk) return
        if (prefs.performanceMode) {
            val targetWidth = prefs.performanceTargetWidth
            val ratio = resources.displayMetrics.heightPixels.toFloat() /
                resources.displayMetrics.widthPixels.toFloat()
            val targetHeight = (targetWidth * ratio).toInt().coerceAtLeast(1)
            glView.holder.setFixedSize(targetWidth, targetHeight)
        } else {
            glView.holder.setSizeFromLayout() // torna alla risoluzione naturale della view
        }
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
        applyPerformanceModePref()
        bassAnalyzer.beatThreshold = prefs.beatDetectionThreshold
        startAudioEngine()
        if (nativeLibraryOk) {
            glView.queueEvent { ProjectMBridge.nativeSetTransitionDuration(prefs.transitionDurationSeconds) }
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
