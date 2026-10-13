package com.treinosmart.app

enum class SessionPhase { IDLE, READY, ACTIVE, REST, FINISHED }

fun main() {
    val detector = HeartRateSetDetector()
    var phase = SessionPhase.READY
    var phaseAt = 0L
    val outputs = mutableListOf<HeartRateSetDetector.Event>()
    for (sec in 0..115) {
        val hr = when {
            sec < 18 -> 75
            sec < 40 -> 75 + (sec - 18) * 2
            sec < 65 -> 120
            else -> maxOf(76, 120 - (sec - 65) * 2)
        }
        val dt = sec * 1000L
        val res = detector.accept(dt, hr, phase, phaseAt)
        if (res != null) {
            outputs += res.event
            if (res.event == HeartRateSetDetector.Event.POSSIBLE_START) { phase = SessionPhase.ACTIVE; phaseAt = dt }
            else { phase = SessionPhase.REST; phaseAt = dt }
        }
    }
    check(HeartRateSetDetector.Event.POSSIBLE_START in outputs) { "No start" }
    check(HeartRateSetDetector.Event.POSSIBLE_END in outputs) { "No end" }
    println("Detector transitions: $outputs")
}
