package com.asfaltosonoro.projectmoverlay

import kotlin.math.PI
import kotlin.math.sqrt

/**
 * Stima l'energia dei bassi dal PCM in ingresso con un semplice filtro
 * passa-basso (~150 Hz) seguito da un envelope follower attack/release.
 * Nessuna FFT: leggero abbastanza da girare su ogni buffer audio.
 *
 * Rileva anche i "colpi" (kick/bassi che salgono bruscamente sopra una
 * soglia), usati per il cambio preset a tempo di beat: non è un vero
 * beat-tracker musicale, ma un semplice rilevatore di transienti con un
 * periodo di refrattarietà per non contare più volte lo stesso colpo.
 */
class BassEnergyAnalyzer(private val sampleRate: Int) {

    private var lpState = 0.0
    private var envelope = 0.0
    private var lastBeatAtMs = 0L

    /** 0..1: più alto = risposta più rapida ai transienti (kick) */
    var speed: Float = 0.5f

    /** 0..1: livello di energia dei bassi oltre il quale scatta un "colpo".
     * Più basso = più sensibile (rileva anche bassi leggeri), più alto =
     * scatta solo sui colpi molto marcati. Regolabile dalle Impostazioni. */
    var beatThreshold: Float = 0.5f

    /** true se l'ultima process() ha rilevato l'inizio di un "colpo" di basso. */
    @Volatile var lastWasBeat: Boolean = false
        private set

    fun process(samples: ShortArray): Float {
        val cutoffHz = 150.0
        val rc = 1.0 / (2 * PI * cutoffHz)
        val dt = 1.0 / sampleRate
        val alpha = dt / (rc + dt)

        var sumSq = 0.0
        for (s in samples) {
            lpState += alpha * (s - lpState)
            sumSq += lpState * lpState
        }
        val rms = sqrt(sumSq / samples.size) / 32768.0

        val attack = 0.2 + speed * 0.6   // reattività alla salita
        val release = 0.02 + speed * 0.15 // velocità di rilascio
        val previousEnvelope = envelope
        envelope += if (rms > envelope) (rms - envelope) * attack else (rms - envelope) * release
        val level = envelope.toFloat().coerceIn(0f, 1f)

        val refractoryMs = 220L
        val now = System.currentTimeMillis()
        lastWasBeat = envelope > beatThreshold &&
            previousEnvelope <= beatThreshold &&
            (now - lastBeatAtMs) > refractoryMs
        if (lastWasBeat) lastBeatAtMs = now

        return level
    }
}
