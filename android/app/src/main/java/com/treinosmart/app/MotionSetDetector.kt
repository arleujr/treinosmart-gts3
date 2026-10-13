package com.treinosmart.app
import java.util.ArrayDeque

/**
 * Detector EXPERIMENTAL de janelas temporais do acelerometro do CELULAR.
 * Valores de entrada: RMS de aceleracao linear em m/s2 em blocos de 1 segundo.
 * Nao e detector validado do movimento do relogio. Pode confundir caminhar ou ajustar aparelhos.
 */
class MotionSetDetector(
    private val movementThreshold: Double = 0.70,
    private val stillThreshold: Double = 0.26,
    private val minExecutionMs: Long = 12_000L,
    private val quietEndMs: Long = 8_000L
) {
    enum class Event { START, END }
    data class Detection(val event: Event, val estimatedAtMs: Long, val evidence: String)
    private val recent = ArrayDeque<Boolean>()
    private var lastActiveAtMs = 0L
    private var lastPhase: SessionPhase? = null
    private var startLockUntil = 0L

    fun reset() {
        recent.clear()
        lastActiveAtMs = 0L
        lastPhase = null
        startLockUntil = 0L
    }

    fun onWindow(nowMs: Long, rms: Double, phase: SessionPhase, phaseStartMs: Long): Detection? {
        if (!rms.isFinite() || rms < 0.0) return null
        if (phase != lastPhase) {
            recent.clear()
            lastActiveAtMs = 0L
            lastPhase = phase
            if (phase == SessionPhase.REST) startLockUntil = nowMs + 5_000L
        }
        when (phase) {
            SessionPhase.READY, SessionPhase.REST -> {
                recent.addLast(rms >= movementThreshold)
                if (recent.size > 6) recent.removeFirst()
                if (recent.size >= 6 && recent.count { it } >= 5 && nowMs >= startLockUntil) {
                    recent.clear()
                    // O movimento acumulou por varios segundos: estimativa retroativa.
                    return Detection(Event.START, nowMs - 4_000L, "5/6 janelas com movimento > limiar")
                }
            }
            SessionPhase.ACTIVE -> {
                if (rms >= movementThreshold) lastActiveAtMs = nowMs
                val executionAge = nowMs - phaseStartMs
                if (executionAge >= minExecutionMs && lastActiveAtMs > 0L &&
                    nowMs - lastActiveAtMs >= quietEndMs && rms <= stillThreshold) {
                    val estimated = (lastActiveAtMs + 1_000L).coerceAtMost(nowMs)
                    lastActiveAtMs = 0L
                    return Detection(Event.END, estimated, "movimento seguido por >= 8s de quietude")
                }
            }
            else -> recent.clear()
        }
        return null
    }
}
