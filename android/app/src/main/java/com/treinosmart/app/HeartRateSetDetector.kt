package com.treinosmart.app

import java.util.ArrayDeque

/**
 * Pesquisa: limites de series ESTIMADOS pelo BPM, nao marcacao de movimento nem prescricao clinica.
 * Detector conservador com histerese + evidencias registraveis.
 * Queda de BPM pode acontecer depois do fim real: a marcacao deve ser corrigivel.
 */
class HeartRateSetDetector {
    enum class Event { POSSIBLE_START, POSSIBLE_END }
    data class Detection(val event: Event, val evidence: String, val score: Double)
    private data class Sample(val time: Long, val bpm: Int)
    private val samples = ArrayDeque<Sample>()
    private var lastEventAt = -60_000L
    private var peakActive = 0
    private var activeBaseline = 0

    fun reset() { samples.clear(); lastEventAt = -60_000L; peakActive = 0; activeBaseline = 0 }

    fun accept(now: Long, bpm: Int, phase: SessionPhase, phaseStarted: Long): Detection? {
        if (bpm !in 40..210) return null
        if (samples.isNotEmpty() && now < samples.last.time) return null
        if (samples.isNotEmpty() && now - samples.last.time > 6_000L) reset()
        samples.addLast(Sample(now, bpm))
        while (samples.isNotEmpty() && now - samples.first.time > 40_000L) samples.removeFirst()
        if (now - lastEventAt < 9_000L) return null
        val recent = samples.filter { now - it.time <= 5_000L }.map { it.bpm }
        val prior = samples.filter { now - it.time in 12_000L..22_000L }.map { it.bpm }
        if (recent.size < 4 || prior.size < 5) return null
        val currentMean = recent.average()
        val priorMean = prior.average()
        val delta = currentMean - priorMean
        when (phase) {
            SessionPhase.READY, SessionPhase.REST -> {
                if (phase == SessionPhase.REST && now - phaseStarted < 15_000L) return null
                if (delta >= 9.0) {
                    lastEventAt = now
                    peakActive = bpm
                    activeBaseline = priorMean.toInt()
                    return Detection(Event.POSSIBLE_START, "HR subida ${"%.1f".format(java.util.Locale.US, delta)} bpm/15s; base=$activeBaseline", (delta/18.0).coerceIn(0.0,1.0))
                }
            }
            SessionPhase.ACTIVE -> {
                peakActive = maxOf(peakActive, bpm)
                if (now - phaseStarted < 22_000L) return null
                if (delta <= -6.0 && peakActive - currentMean >= 7.0) {
                    lastEventAt = now
                    val peak = peakActive
                    peakActive = 0
                    return Detection(Event.POSSIBLE_END, "HR queda ${"%.1f".format(java.util.Locale.US, -delta)} bpm/15s; pico=$peak", ((-delta)/15.0).coerceIn(0.0,1.0))
                }
            }
            else -> Unit
        }
        return null
    }
}
