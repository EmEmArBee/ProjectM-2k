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
}
