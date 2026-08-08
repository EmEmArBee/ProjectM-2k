package com.asfaltosonoro.projectmoverlay

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import kotlin.concurrent.thread

/**
 * Unifica le tre sorgenti audio richieste:
 *  - MIC: microfono interno del telefono
 *  - USB: interfaccia audio USB collegata (instradata via AudioRecord.setPreferredDevice)
 *  - INTERNAL_PLAYER: un file audio riprodotto dentro l'app (via MediaPlayer + Visualizer
 *    sulla sessione di quel player)
 *
 * In tutti i casi il PCM raccolto viene inoltrato sia a projectM
 * (ProjectMBridge.nativePcmAdd) sia al BassEnergyAnalyzer per la pulsazione del logo.
 */
class AudioEngine(
    private val context: Context,
    private val bassAnalyzer: BassEnergyAnalyzer,
    private val onBassLevel: (Float) -> Unit
) {
    private val sampleRate = 44100
    private var recordThread: Thread? = null
    @Volatile private var running = false

    private var mediaPlayer: MediaPlayer? = null
    private var visualizer: Visualizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun availableUsbInputs(): List<AudioDeviceInfo> {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return am.getDevices(AudioManager.GET_DEVICES_INPUTS).filter {
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
        }
    }

    fun start(source: AudioSourceType, usbDeviceId: Int, internalPlayerUri: Uri?) {
        stop()
        when (source) {
            AudioSourceType.MIC -> startAudioRecord(preferredDevice = null)
            AudioSourceType.USB -> {
                val device = availableUsbInputs().firstOrNull { it.id == usbDeviceId }
                    ?: availableUsbInputs().firstOrNull()
                startAudioRecord(preferredDevice = device)
            }
            AudioSourceType.INTERNAL_PLAYER -> startInternalPlayer(internalPlayerUri)
        }
    }

    fun stop() {
        running = false
        recordThread?.join(300)
        recordThread = null

        visualizer?.enabled = false
        visualizer?.release()
        visualizer = null

        mediaPlayer?.release()
        mediaPlayer = null
    }

    @SuppressLint("MissingPermission")
    private fun startAudioRecord(preferredDevice: AudioDeviceInfo?) {
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val bufSize = maxOf(minBuf, 2048)
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufSize
        )
        preferredDevice?.let { record.preferredDevice = it }

        if (record.state != AudioRecord.STATE_INITIALIZED) return

        running = true
        record.startRecording()
        recordThread = thread(start = true) {
            val buffer = ShortArray(bufSize / 2)
            while (running) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    val chunk = if (read == buffer.size) buffer else buffer.copyOf(read)
                    ProjectMBridge.nativePcmAdd(chunk, 1)
                    val level = bassAnalyzer.process(chunk)
                    mainHandler.post { onBassLevel(level) }
                }
            }
            record.stop()
            record.release()
        }
    }

    private fun startInternalPlayer(uri: Uri?) {
        uri ?: return
        val mp = MediaPlayer().apply {
            setDataSource(context, uri)
            isLooping = true
            prepare()
        }
        mediaPlayer = mp

        visualizer = Visualizer(mp.audioSessionId).apply {
            captureSize = Visualizer.getCaptureSizeRange()[1]
            setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, rate: Int) {
                    waveform ?: return
                    val pcm = ShortArray(waveform.size)
                    for (i in waveform.indices) pcm[i] = (((waveform[i].toInt() and 0xFF) - 128) * 256).toShort()
                    ProjectMBridge.nativePcmAdd(pcm, 1)
                    val level = bassAnalyzer.process(pcm)
                    mainHandler.post { onBassLevel(level) }
                }
                override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, rate: Int) {}
            }, Visualizer.getMaxCaptureRate() / 2, true, false)
            enabled = true
        }
        mp.start()
    }
}
