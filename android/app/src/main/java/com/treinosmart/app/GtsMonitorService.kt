package com.treinosmart.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import com.treinosmart.ble.GtsBleClient

/**
 * Mantem a conexao BLE em primeiro plano (foreground service do tipo connectedDevice).
 * Permite comandos Moyoung documentados com resultado real ainda a validar no hardware.
 * A coleta continua enquanto o servico permanecer ativo, mesmo com a Activity fechada.
 */
class GtsMonitorService : Service() {
    companion object {
        const val ACTION_CONNECT = "com.treinosmart.app.CONNECT"
        const val ACTION_DISCONNECT = "com.treinosmart.app.DISCONNECT"
        const val ACTION_HR_DYNAMIC_ON = "com.treinosmart.app.HR_DYNAMIC_ON"
        const val ACTION_HR_DYNAMIC_OFF = "com.treinosmart.app.HR_DYNAMIC_OFF"
        const val ACTION_REQUEST_HR = "com.treinosmart.app.HR_MANUAL_REQUEST"
        const val ACTION_VIBRATE = "com.treinosmart.app.VIBRATE_DIRECT"
        const val ACTION_STATUS = "com.treinosmart.app.MONITOR_UPDATE"
        const val EXTRA_ADDRESS = "address"
        const val EXTRA_KIND = "kind"
        const val EXTRA_TEXT = "text"
        private const val CHANNEL = "treinosmart_ble_monitor"
        private const val NOTIFICATION_ID = 3401
        const val PRIVATE_PERMISSION = "com.treinosmart.app.permission.TELEMETRY"
    }

    private lateinit var ble: GtsBleClient
    private lateinit var log: TrainingLog
    private val main = Handler(Looper.getMainLooper())
    private var running = false
    private var silentLogged = false
    private val monitorTick = object : Runnable {
        override fun run() {
            if (running) {
                val last = MonitorState.lastBpmAtMs
                if (last > 0L) {
                    val elapsed = SystemClock.elapsedRealtime() - last
                    if (elapsed > 5000L && !silentLogged) {
                        silentLogged = true
                        log.add("hr_silence", "app", elapsed.toString(), note = "no HR received for over 5 seconds")
                        publish("status", "Sem nova amostra de BPM (>5s), BLE pode seguir conectado")
                    }
                    if (elapsed <= 5000L) silentLogged = false
                }
                main.postDelayed(this, 2000L)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        log = TrainingLog(this)
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Monitoramento do relogio", NotificationManager.IMPORTANCE_LOW))
        ble = GtsBleClient(this,
            onStatus = { text -> main.post {
                MonitorState.connectionStatus = text
                publish("status", text)
                log.add("ble_status", "app", text)
            } },
            onSample = { kind, value, raw -> main.post {
                when (kind) {
                    "hr" -> {
                        val now = SystemClock.elapsedRealtime()
                        val prior = MonitorState.lastBpmAtMs
                        if (prior > 0 && now - prior >= 5000) {
                            MonitorState.gaps++
                            log.add("hr_gap", "app", (now - prior).toString(), note = "milliseconds between received samples")
                        }
                        silentLogged = false
                        MonitorState.lastBpm = value.toIntOrNull()
                        MonitorState.lastBpmAtMs = now
                        MonitorState.samples++
                        publish("hr", value)
                        val e = WorkoutSessionService.engine
                        if (e != null && e.phase in setOf(SessionPhase.READY, SessionPhase.ACTIVE, SessionPhase.REST) && WorkoutSessionService.automaticMode) {
                            try {
                                startService(Intent(this@GtsMonitorService, WorkoutSessionService::class.java)
                                    .setAction(WorkoutSessionService.HR_SAMPLE)
                                    .putExtra("bpm", value.toInt())
                                    .putExtra("elapsed_ms", now))
                            } catch (ex: Exception) {
                                log.add("hr_routing_error", "app", ex.message ?: "unknown")
                            }
                        }
                    }
                    "moyoung", "moyoung_aux" -> publish("packet", "$kind $raw")
                    "connection" -> {
                        MonitorState.lastBpm = null
                        MonitorState.lastBpmAtMs = 0L
                        publish("status", "Conexao interrompida ($raw)")
                    }
                }
                log.add(kind, "watch", value, raw, note = "session_id=${WorkoutSessionService.engine?.sessionId ?: ""}")
            } }
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(): Notification {
        val launch = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("TreinoSmart - Bluetooth ativo")
            .setContentText("Registrando os dados recebidos do GTS 3")
            .setContentIntent(launch)
            .setOngoing(true)
            .build()
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    publish("status", "Permissao Bluetooth nao concedida")
                    stopSelf()
                    return START_NOT_STICKY
                }
                // Ativar foreground imediatamente antes do trabalho BLE.
                startForeground(NOTIFICATION_ID, notification())
                val address = intent.getStringExtra(EXTRA_ADDRESS)
                val manager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
                val adapter = manager.adapter
                val remote = try { if (adapter != null && !address.isNullOrBlank()) adapter.getRemoteDevice(address) else null }
                    catch (_: IllegalArgumentException) { null }
                if (remote == null || adapter == null || !adapter.bondedDevices.any { it.address == address }) {
                    publish("status", "Dispositivo nao encontrado entre os pareados")
                    stopSelf()
                    return START_NOT_STICKY
                }
                running = true
                silentLogged = false
                main.removeCallbacks(monitorTick)
                main.postDelayed(monitorTick, 2000L)
                MonitorState.active = true
                MonitorState.lastBpm = null
                MonitorState.lastBpmAtMs = 0L
                MonitorState.samples = 0
                MonitorState.gaps = 0
                log.add("monitor_start", "user", "GTS 3", note = "address intentionally omitted")
                ble.connect(remote)
                publish("status", "Iniciando monitoramento BLE")
            }
            ACTION_HR_DYNAMIC_ON -> {
                if (running) { ble.setDynamicHeartRate(true); log.add("hr_command", "app", "dynamic_on") }
                else { publish("status", "Conecte ao GTS 3 primeiro"); stopSelf() }
            }
            ACTION_HR_DYNAMIC_OFF -> {
                if (running) { ble.setDynamicHeartRate(false); log.add("hr_command", "app", "dynamic_off") }
                else stopSelf()
            }
            ACTION_REQUEST_HR -> {
                if (running) { ble.requestManualHeartRate(); log.add("hr_command", "app", "manual_request") }
                else { publish("status", "Conecte ao GTS 3 primeiro"); stopSelf() }
            }
            ACTION_VIBRATE -> {
                if (running) { ble.vibrateFindWatch(); log.add("watch_alert", "app", "find_watch_command") }
                else { publish("status", "Sem conexao para enviar vibracao"); stopSelf() }
            }
            ACTION_DISCONNECT -> {
                // Conexao fecha imediatamente; envie OFF antes se quiser encerrar medicao remotamente.
                ble.disconnect()
                running = false
                main.removeCallbacks(monitorTick)
                MonitorState.active = false
                log.add("monitor_stop", "user")
                publish("status", "Monitoramento encerrado")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun publish(kind: String, value: String) {
        val update = Intent(ACTION_STATUS).setPackage(packageName)
            .putExtra(EXTRA_KIND, kind).putExtra(EXTRA_TEXT, value)
        sendBroadcast(update, PRIVATE_PERMISSION)
    }

    override fun onDestroy() {
        main.removeCallbacks(monitorTick)
        if (running) ble.disconnect()
        running = false
        MonitorState.active = false
        super.onDestroy()
    }
}

/** Snapshot temporario no processo: CSV continua sendo o registro persistente. */
object MonitorState {
    @Volatile var active: Boolean = false
    @Volatile var connectionStatus: String = "Ainda nao conectado"
    @Volatile var lastBpm: Int? = null
    @Volatile var lastBpmAtMs: Long = 0L
    @Volatile var samples: Int = 0
    @Volatile var gaps: Int = 0
}
