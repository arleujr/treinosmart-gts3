package com.treinosmart.ble

/** Pacotes curtos MoYoung V2, sem firmware, sem escrita de configuracoes permanentes. */
object MoyoungCommands {
    fun frame(command: Int, vararg payload: Int): ByteArray {
        require(command in 0..255 && payload.all { it in 0..255 })
        val n = 5 + payload.size
        require(n <= 20) { "Only short control frames are supported" }
        return byteArrayOf(0xFE.toByte(), 0xEA.toByte(), 0x20, n.toByte(), command.toByte(), *payload.map { it.toByte() }.toByteArray())
    }
    // Documentados como {0} para iniciar e {-1} para parar.
    fun dynamicHeartRate(enable: Boolean) = frame(0x68, if (enable) 0 else 255)
    fun manualHeartRate(enable: Boolean) = frame(0x6D, if (enable) 0 else 255)
    // Comando de localizar relogio (nao altera firmware nem o mostrador).
    fun findWatch() = frame(0x61)
}
