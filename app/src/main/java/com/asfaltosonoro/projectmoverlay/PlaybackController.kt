package com.asfaltosonoro.projectmoverlay

import android.os.Handler
import android.os.Looper

enum class PlaybackMode {
    RANDOM_FAVORITES,
    RANDOM_ALL,
    PLAYLIST_ORDERED,
    PLAYLIST_SHUFFLED,
    MANUAL_SINGLE
}

/**
 * Decide quale preset caricare e quando, in base alla modalità scelta
 * nelle Impostazioni. Non tocca la libreria nativa direttamente: chiama
 * ProjectMBridge.nativeLoadPresetFile con il path calcolato.
 */
class PlaybackController(
    private val repository: PresetRepository,
    private val prefs: Prefs,
    // projectM va toccato solo dal thread GL (stesso thread di nativeInit/
    // nativeRenderFrame): questa lambda è glView.queueEvent { ... } passata
    // da MainActivity. Senza, il preset non cambia mai davvero (bug corretto).
    private val runOnGlThread: (() -> Unit) -> Unit = { it() }
) {
    private val handler = Handler(Looper.getMainLooper())
    private var autoAdvanceRunnable: Runnable? = null

    private val history = ArrayDeque<String>() // per il "previous" anche in modalità random
    private var historyCursor = -1

    private var playlistOrder: List<String> = emptyList()
    private var playlistIndex = 0

    fun start() {
        rebuildIfNeeded()
        loadCurrentOrFirst()
        scheduleAutoAdvance()
    }

    fun stop() {
        autoAdvanceRunnable?.let { handler.removeCallbacks(it) }
    }

    /** Il preset attualmente caricato, se noto. Usato per ripristinarlo se
     * il contesto OpenGL viene ricreato (es. tornando dalle Impostazioni). */
    fun currentPresetPath(): String? = history.getOrNull(historyCursor)

    fun onModeOrPlaylistChanged() {
        rebuildIfNeeded()
        next()
    }

    private fun rebuildIfNeeded() {
        if (prefs.playbackMode == PlaybackMode.PLAYLIST_ORDERED ||
            prefs.playbackMode == PlaybackMode.PLAYLIST_SHUFFLED
        ) {
            val paths = repository.playlist(prefs.activePlaylistName ?: "")
            playlistOrder = if (prefs.playbackMode == PlaybackMode.PLAYLIST_SHUFFLED) paths.shuffled() else paths
            playlistIndex = 0
        }
    }

    private fun scheduleAutoAdvance() {
        autoAdvanceRunnable?.let { handler.removeCallbacks(it) }
        if (prefs.playbackMode == PlaybackMode.MANUAL_SINGLE) return
        val runnable = object : Runnable {
            override fun run() {
                next()
                handler.postDelayed(this, prefs.presetDurationSeconds * 1000L)
            }
        }
        autoAdvanceRunnable = runnable
        handler.postDelayed(runnable, prefs.presetDurationSeconds * 1000L)
    }

    private fun loadCurrentOrFirst() {
        val path = pickNextPath() ?: return
        loadPath(path)
    }

    fun next() {
        val path = pickNextPath() ?: return
        loadPath(path)
        scheduleAutoAdvance() // riavvia il timer se l'utente ha appena forzato un cambio manuale
    }

    /** Usato dal browser preset "live": carica subito il preset scelto e riparte il timer. */
    fun forceLoad(path: String) {
        loadPath(path)
        scheduleAutoAdvance()
    }

    fun previous() {
        if (historyCursor > 0) {
            historyCursor--
            val path = history[historyCursor]
            runOnGlThread { ProjectMBridge.nativeLoadPresetFile(path, true) }
        }
        scheduleAutoAdvance()
    }

    private fun loadPath(path: String) {
        runOnGlThread { ProjectMBridge.nativeLoadPresetFile(path, true) }
        // taglia la history "futura" se stavamo tornando indietro e ora andiamo avanti
        while (history.size > historyCursor + 1) history.removeLast()
        history.addLast(path)
        historyCursor = history.size - 1
        if (history.size > 200) { history.removeFirst(); historyCursor-- }
    }

    private fun pickNextPath(): String? {
        return when (prefs.playbackMode) {
            PlaybackMode.RANDOM_FAVORITES -> repository.favoritePresets().randomOrNull()?.path
                ?: repository.allPresets().randomOrNull()?.path
            PlaybackMode.RANDOM_ALL -> repository.allPresets().randomOrNull()?.path
            PlaybackMode.PLAYLIST_ORDERED, PlaybackMode.PLAYLIST_SHUFFLED -> {
                if (playlistOrder.isEmpty()) return null
                val p = playlistOrder[playlistIndex % playlistOrder.size]
                playlistIndex++
                p
            }
            PlaybackMode.MANUAL_SINGLE -> prefs.manualPresetPath ?: repository.allPresets().firstOrNull()?.path
        }
    }
}
