package com.treinosmart.app

import android.content.Context
import java.io.File
import java.time.Instant

/** Diario de eventos local e simples. Migrar para Room quando a camada BLE estiver validada. */
class TrainingLog(context: Context) {
    private val file = File(context.filesDir, "training_events.csv")
    companion object { private val fileLock = Any() }
    init { synchronized(fileLock) {
        if (!file.exists()) file.writeText("timestamp_utc,event_type,source,value,raw_hex,note\n")
    } }
    fun add(event: String, source: String, value: String = "", raw: String = "", note: String = "") = synchronized(fileLock) {
        fun csv(s: String) = "\"" + s.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + "\""
        val line = listOf(Instant.now().toString(), event, source, value, raw, note).joinToString(",") { csv(it) }
        file.appendText("$line\n")
    }
    fun bytes(): ByteArray = synchronized(fileLock) { file.readBytes() }
}
