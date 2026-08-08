package com.asfaltosonoro.projectmoverlay

import kotlin.math.PI
import kotlin.math.sqrt

/**
 * Stima l'energia dei bassi dal PCM in ingresso con un semplice filtro
 * passa-basso (~150 Hz) seguito da un envelope follower attack/release.
 * Nessuna FFT: leggero abbastanza da girare su ogni buffer audio.
 */
class BassEnergyAnalyzer(private val sampleRate: Int) {

    private var lpState = 0.0
    private var envelope = 0.0

    /** 0..1: più alto = risposta più rapida ai transienti (kick) */
    var speed: Float = 0.5f

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
        envelope += if (rms > envelope) (rms - envelope) * attack else (rms - envelope) * release

        return envelope.toFloat().coerceIn(0f, 1f)
    }
}
