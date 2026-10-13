package com.treinosmart.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import org.json.JSONObject

/** Motor de sessao: detector de BPM opt-in, registrando estimativas e permitindo correcoes manuais. */
class WorkoutSessionService : Service() {
    companion object {
        const val START = "com.treinosmart.app.SESSION_START"
        const val BEGIN = "com.treinosmart.app.SET_BEGIN"
        const val END = "com.treinosmart.app.SET_END"
        const val FINISH = "com.treinosmart.app.SESSION_FINISH"
        const val DEMO = "com.treinosmart.app.DEMO_START"
        const val TEST_NOTIFICATION = "com.treinosmart.app.TEST_REST_ALERT"
        const val AUTO_ON = "com.treinosmart.app.AUTO_ON"
        const val AUTO_OFF = "com.treinosmart.app.AUTO_OFF"
        const val HR_SAMPLE = "com.treinosmart.app.HR_SAMPLE"
        const val UPDATE = "com.treinosmart.app.SESSION_UPDATE"
        const val EXTRA_REPS = "actual_reps"
        const val EXTRA_LOAD = "actual_load"
        private const val FOREGROUND_ID = 4500
        private const val REST_ID = 4501
        private const val STATUS_CHANNEL = "treinosmart_session_status"
        private const val ALERT_CHANNEL = "treinosmart_rest_alert"
        @Volatile var engine: WorkoutEngine? = null
        @Volatile var lastMessage = "Pronto para iniciar"
        @Volatile var automaticMode = false
        @Volatile var motionStatus = "Detector BPM desligado"
    }

    private lateinit var log: TrainingLog
    private lateinit var manager: NotificationManager
    private val handler = Handler(Looper.getMainLooper())
    private val detector = HeartRateSetDetector()
    private var active = false
    private var durationNoticeAtMs = -1L
    private var demoStage = 0
    private var demoStageStart = 0L
    private val tick = object : Runnable {
        override fun run() {
            val e = engine
            val now = SystemClock.elapsedRealtime()
            if (e != null && active) {
                e.tick(now)?.let { transition ->
                    emit(transition)
                    alert("Descanso concluido", transition.message)
                    commandWatch(GtsMonitorService.ACTION_VIBRATE)
                }
                if (!e.demo && e.phase == SessionPhase.ACTIVE && e.currentExercise.maxDurationSec != null &&
                    e.elapsedSec(now) > e.currentExercise.maxDurationSec!! && durationNoticeAtMs != e.phaseStartedMs) {
                    durationNoticeAtMs = e.phaseStartedMs
                    alert("Ritmo do treino", "A duracao ultrapassou a faixa planejada; confira a execucao.")
                }
                if (e.demo) runDemo(now)
                manager.notify(FOREGROUND_ID, ongoing())
                broadcast()
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        log = TrainingLog(this)
        manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(STATUS_CHANNEL, "Treino em andamento", NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(NotificationChannel(ALERT_CHANNEL, "Avisos do treino", NotificationManager.IMPORTANCE_HIGH).apply {
            enableVibration(true)
        })
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val now = SystemClock.elapsedRealtime()
        when (intent?.action) {
            START, DEMO -> {
                if (active) return START_STICKY
                val simulated = intent.action == DEMO
                val plan = if (simulated) WorkoutPlan("DEMO simulada", listOf(ExercisePlan("Demo", 2, 12, 8, null, 4, 10))) else Plans.load(this)
                if (plan.exercises.isEmpty()) { lastMessage = "Cadastre exercicios"; stopSelf(); return START_NOT_STICKY }
                engine = WorkoutEngine(plan).apply { start(now, System.currentTimeMillis().toString(), simulated) }
                active = true
                detector.reset()
                durationNoticeAtMs = -1L
                demoStage = 0; demoStageStart = now
                log.add("session_start", if (simulated) "simulation" else "user", engine!!.sessionId)
                startForeground(FOREGROUND_ID, ongoing())
                handler.removeCallbacks(tick); handler.post(tick)
                if (!simulated) commandWatch(GtsMonitorService.ACTION_HR_DYNAMIC_ON)
                lastMessage = if (simulated) "DEMO (simulada)" else "Sessao iniciada; solicitando sensor no relogio"
            }
            BEGIN -> {
                val e = engine
                if (e?.demo == true) return START_STICKY
                if (e?.phase == SessionPhase.REST) log.add("rest_end", "manual", (now - e.phaseStartedMs).toString())
                e?.beginSet(now)?.let(::emit)
            }
            END -> {
                val e = engine
                if (e?.demo == true) return START_STICKY
                val reps = intent.getIntExtra(EXTRA_REPS, -1).takeIf { it >= 0 }
                val load = intent.getDoubleExtra(EXTRA_LOAD, -1.0).takeIf { it >= 0.0 }
                e?.endSet(now, reps, load, "manual")?.forEach(::emit)
                detector.reset()
                if (e?.phase == SessionPhase.FINISHED) stopSession()
            }
            AUTO_ON -> {
                automaticMode = true; detector.reset()
                motionStatus = "Detector de BPM experimental ligado (sem garantia de acerto)"
                lastMessage = "Analise de BPM ligada. Corrija os limites das series se precisar."
                log.add("hr_detector", "user", "on")
            }
            AUTO_OFF -> {
                automaticMode = false; detector.reset()
                motionStatus = "Detector de BPM desligado"
                log.add("hr_detector", "user", "off")
            }
            HR_SAMPLE -> {
                val bpm = intent.getIntExtra("bpm", -1)
                val at = intent.getLongExtra("elapsed_ms", now)
                onHeartSample(at, bpm)
            }
            TEST_NOTIFICATION -> { alert("Teste de aviso", "Alerta de descanso no celular") }
            FINISH -> {
                engine?.confirmLastRest(now)
                engine?.finish()?.let(::emit)
                stopSession()
            }
            else -> return START_NOT_STICKY
        }
        broadcast()
        return START_STICKY
    }

    private fun onHeartSample(at: Long, bpm: Int) {
        val e = engine ?: return
        if (!active || e.demo || !automaticMode) return
        val found = detector.accept(at, bpm, e.phase, e.phaseStartedMs) ?: return
        val logInfo = JSONObject().put("session_id", e.sessionId).put("event", found.event.name)
            .put("evidence", found.evidence).put("score", found.score)
            .put("source", "watch_bpm").put("time_elapsed_ms", at)
        log.add("hr_boundary_candidate", "watch_bpm", logInfo.toString())
        when (found.event) {
            HeartRateSetDetector.Event.POSSIBLE_START -> {
                if (e.phase == SessionPhase.REST) log.add("rest_end", "hr_estimate", (at - e.phaseStartedMs).toString())
                e.beginSet(at)?.let { emit(it, "watch_bpm_estimate") }
                lastMessage = "Possivel inicio de serie por tendencia do BPM; confira depois."
            }
            HeartRateSetDetector.Event.POSSIBLE_END -> {
                e.endSet(at, null, null, "watch_bpm_estimate").forEach { emit(it, "watch_bpm_estimate") }
                lastMessage = "Possivel fim de serie por BPM; descanso iniciado (estimativa)."
                if (e.phase == SessionPhase.FINISHED) stopSession()
            }
        }
    }

    private fun runDemo(now: Long) {
        val e = engine ?: return
        when (demoStage) {
            0 -> if (now - demoStageStart >= 1500) { e.beginSet(now)?.let(::emit); demoStage = 1; demoStageStart = now }
            1 -> if (now - demoStageStart >= 6500) {
                e.endSet(now, 12, null, "simulation").forEach(::emit)
                demoStage = 2; demoStageStart = now
                if (e.phase == SessionPhase.FINISHED) stopSession()
            }
            2 -> if (e.phase == SessionPhase.REST && now > e.restDeadlineMs + 1500L) {
                e.beginSet(now)?.let(::emit); demoStage = 1; demoStageStart = now
            }
        }
    }
    private fun emit(t: Transition, source: String = "app") {
        lastMessage = t.message
        val e = engine ?: return
        val payload = JSONObject().put("session_id", e.sessionId).put("phase", e.phase.name)
            .put("message", t.message).put("demo", e.demo)
        t.set?.let { set -> payload.put("duration_ms", set.durationMs).put("set", set.setNumber)
            .put("actual_reps", set.actualReps ?: JSONObject.NULL)
            .put("actual_load_kg", set.actualLoadKg ?: JSONObject.NULL)
            .put("prescribed_reps", e.plan.exercises[set.exerciseIndex].reps)
            .put("detector_source", set.source) }
        log.add(t.type, if (e.demo) "simulation" else source, payload.toString())
        if (t.type == "feedback") alert("Acompanhamento do treino", t.message)
    }
    private fun commandWatch(action: String) {
        try { startService(Intent(this, GtsMonitorService::class.java).setAction(action)) }
        catch (e: Exception) { log.add("watch_command_error", "app", action, note = e.message ?: "") }
    }
    private fun alert(title: String, message: String) {
        manager.notify(REST_ID, Notification.Builder(this, ALERT_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title).setContentText(message)
            .setStyle(Notification.BigTextStyle().bigText(message))
            .setPriority(Notification.PRIORITY_HIGH).setAutoCancel(true).build())
    }
    private fun ongoing(): Notification {
        val e = engine
        val label = when (e?.phase) {
            SessionPhase.READY -> "Aguardando serie"
            SessionPhase.ACTIVE -> "${e.currentExercise.name} - ${e.elapsedSec(SystemClock.elapsedRealtime())}s"
            SessionPhase.REST -> "Descanso: ${e.remainingRest(SystemClock.elapsedRealtime())}s"
            else -> "Sessao TreinoSmart"
        }
        return Notification.Builder(this, STATUS_CHANNEL).setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(if (e?.demo == true) "DEMO (SIMULADA)" else "TreinoSmart - treino")
            .setContentText(label)
            .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .setOngoing(true).build()
    }
    private fun broadcast() = sendBroadcast(Intent(UPDATE).setPackage(packageName))
    private fun stopSession() {
        active = false
        automaticMode = false; motionStatus = "Detector de BPM desligado"
        handler.removeCallbacks(tick)
        commandWatch(GtsMonitorService.ACTION_HR_DYNAMIC_OFF)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    override fun onDestroy() { handler.removeCallbacks(tick); active = false; super.onDestroy() }
}
