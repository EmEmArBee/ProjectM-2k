package com.asfaltosonoro.projectmoverlay

import android.content.Context
import android.net.Uri

enum class AudioSourceType { MIC, USB, INTERNAL_PLAYER }
enum class PulseVisual { SCALE, OPACITY, BOTH }

/** Wrapper leggero su SharedPreferences per tutte le impostazioni dell'app. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("projectm_overlay_prefs", Context.MODE_PRIVATE)

    var logoUri: Uri?
        get() = sp.getString("logo_uri", null)?.let { Uri.parse(it) }
        set(value) = sp.edit().putString("logo_uri", value?.toString()).apply()

    var pulseVisual: PulseVisual
        get() = PulseVisual.valueOf(sp.getString("pulse_visual", PulseVisual.SCALE.name)!!)
        set(value) = sp.edit().putString("pulse_visual", value.name).apply()

    /** 0..1 */
    var pulseIntensity: Float
        get() = sp.getFloat("pulse_intensity", 0.5f)
        set(value) = sp.edit().putFloat("pulse_intensity", value).apply()

    /** 0..1, più alto = risposta più rapida/scattante */
    var pulseSpeed: Float
        get() = sp.getFloat("pulse_speed", 0.5f)
        set(value) = sp.edit().putFloat("pulse_speed", value).apply()

    var audioSource: AudioSourceType
        get() = AudioSourceType.valueOf(sp.getString("audio_source", AudioSourceType.MIC.name)!!)
        set(value) = sp.edit().putString("audio_source", value.name).apply()

    var usbDeviceId: Int
        get() = sp.getInt("usb_device_id", -1)
        set(value) = sp.edit().putInt("usb_device_id", value).apply()

    var internalPlayerUri: Uri?
        get() = sp.getString("internal_player_uri", null)?.let { Uri.parse(it) }
        set(value) = sp.edit().putString("internal_player_uri", value?.toString()).apply()

    var playbackMode: PlaybackMode
        get() = PlaybackMode.valueOf(sp.getString("playback_mode", PlaybackMode.RANDOM_ALL.name)!!)
        set(value) = sp.edit().putString("playback_mode", value.name).apply()

    var activePlaylistName: String?
        get() = sp.getString("active_playlist", null)
        set(value) = sp.edit().putString("active_playlist", value).apply()

    var manualPresetPath: String?
        get() = sp.getString("manual_preset_path", null)
        set(value) = sp.edit().putString("manual_preset_path", value).apply()

    var presetDurationSeconds: Int
        get() = sp.getInt("preset_duration_seconds", 20)
        set(value) = sp.edit().putInt("preset_duration_seconds", value).apply()

    var importedFolderUri: Uri?
        get() = sp.getString("imported_folder_uri", null)?.let { Uri.parse(it) }
        set(value) = sp.edit().putString("imported_folder_uri", value?.toString()).apply()

    /** Moltiplicatore dimensione del logo: 1.0 = dimensione base (140dp) */
    var logoScale: Float
        get() = sp.getFloat("logo_scale", 1f)
        set(value) = sp.edit().putFloat("logo_scale", value).apply()

    /** Moltiplicatore del segnale audio in ingresso prima di darlo a projectM */
    var audioGain: Float
        get() = sp.getFloat("audio_gain", 1f)
        set(value) = sp.edit().putFloat("audio_gain", value).apply()

    var fullscreenImmersive: Boolean
        get() = sp.getBoolean("fullscreen_immersive", true)
        set(value) = sp.edit().putBoolean("fullscreen_immersive", value).apply()

    /** Se true, il doppio tap avanti/indietro pesca sempre un preset casuale
     * tra TUTTI quelli disponibili, ignorando la modalità di scorrimento
     * automatico impostata. */
    var manualNavRandom: Boolean
        get() = sp.getBoolean("manual_nav_random", false)
        set(value) = sp.edit().putBoolean("manual_nav_random", value).apply()

    /** Se true, il cambio preset automatico segue i colpi di basso rilevati
     * invece della durata fissa in secondi. */
    var beatSyncEnabled: Boolean
        get() = sp.getBoolean("beat_sync_enabled", false)
        set(value) = sp.edit().putBoolean("beat_sync_enabled", value).apply()

    /** Ogni quanti colpi di basso rilevati cambiare preset, in modalità beat sync. */
    var beatSyncEveryNBeats: Int
        get() = sp.getInt("beat_sync_every_n", 4)
        set(value) = sp.edit().putInt("beat_sync_every_n", value).apply()

    /** Durata (secondi) del crossfade tra un preset e il successivo. */
    var transitionDurationSeconds: Float
        get() = sp.getFloat("transition_duration_seconds", 2.5f)
        set(value) = sp.edit().putFloat("transition_duration_seconds", value).apply()

    /** Opacità "base" del logo a riposo (0..1). Si combina moltiplicativamente
     * con la pulsazione nelle modalità Opacità/Entrambi. */
    var logoBaseAlpha: Float
        get() = sp.getFloat("logo_base_alpha", 1f)
        set(value) = sp.edit().putFloat("logo_base_alpha", value).apply()

    /** 0..1: soglia di energia dei bassi oltre la quale scatta un "colpo"
     * rilevato (usata per la pulsazione reattiva e per il cambio preset a
     * tempo di beat). Più bassa = più sensibile. */
    var beatDetectionThreshold: Float
        get() = sp.getFloat("beat_detection_threshold", 0.5f)
        set(value) = sp.edit().putFloat("beat_detection_threshold", value).apply()
}
