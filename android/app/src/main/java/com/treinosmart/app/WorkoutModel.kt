package com.treinosmart.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/** Prescricao, nao declaracao de execucao. Valores preenchidos pelo usuario/professor. */
data class ExercisePlan(
    val name: String,
    val sets: Int,
    val reps: Int,
    val restSec: Int,
    val plannedLoadKg: Double? = null,
    val minDurationSec: Int? = null,
    val maxDurationSec: Int? = null
)

data class WorkoutPlan(val name: String, val exercises: List<ExercisePlan>)

data class CompletedSet(
    val exerciseIndex: Int,
    val setNumber: Int,
    val durationMs: Long,
    val actualReps: Int?,
    val actualLoadKg: Double?,
    val source: String,
    var restMs: Long? = null
)

enum class SessionPhase { IDLE, READY, ACTIVE, REST, FINISHED }

data class Transition(val type: String, val message: String, val set: CompletedSet? = null)

/** Motor puro: o BLE nao determina transicoes sem classificador validado. */
class WorkoutEngine(val plan: WorkoutPlan) {
    var phase: SessionPhase = SessionPhase.IDLE
        private set
    var exerciseIndex = 0
        private set
    var nextSetIndex = 0
        private set
    var phaseStartedMs = 0L
        private set
    var restDeadlineMs = 0L
        private set
    var restNoticeSent = false
        private set
    val completed = mutableListOf<CompletedSet>()
    var demo = false
    var sessionId = ""

    val currentExercise: ExercisePlan get() = plan.exercises[exerciseIndex.coerceIn(0, plan.exercises.lastIndex)]
    val currentSetNumber: Int get() = nextSetIndex + 1

    fun start(now: Long, id: String, demoMode: Boolean = false): Transition {
        check(plan.exercises.isNotEmpty())
        check(phase == SessionPhase.IDLE || phase == SessionPhase.FINISHED)
        phase = SessionPhase.READY
        exerciseIndex = 0; nextSetIndex = 0
        completed.clear(); phaseStartedMs = now; restDeadlineMs = 0L
        restNoticeSent = false; demo = demoMode; sessionId = id
        return Transition("session_start", "Treino iniciado: ${plan.name}")
    }

    fun beginSet(now: Long): Transition? {
        if (phase != SessionPhase.READY && phase != SessionPhase.REST) return null
        if (phase == SessionPhase.REST) completed.lastOrNull()?.restMs = (now - phaseStartedMs).coerceAtLeast(0)
        phase = SessionPhase.ACTIVE; phaseStartedMs = now
        restNoticeSent = false
        return Transition("set_start", "${currentExercise.name}: serie $currentSetNumber/${currentExercise.sets}")
    }

    fun endSet(now: Long, actualReps: Int?, actualLoadKg: Double?, source: String = "manual"): List<Transition> {
        if (phase != SessionPhase.ACTIVE) return emptyList()
        val ex = currentExercise
        val set = CompletedSet(exerciseIndex, currentSetNumber,
            (now - phaseStartedMs).coerceAtLeast(0), actualReps, actualLoadKg, source)
        completed.add(set)
        val output = mutableListOf(Transition("set_end", "Serie ${set.setNumber}: ${set.durationMs / 1000}s", set))
        val assessment = feedback(ex, set)
        if (assessment != null) output.add(Transition("feedback", assessment, set))
        val lastExercise = exerciseIndex == plan.exercises.lastIndex
        val lastSet = nextSetIndex + 1 >= ex.sets
        if (lastExercise && lastSet) {
            phase = SessionPhase.FINISHED
            output.add(Transition("session_end", "Treino concluido"))
        } else {
            // Proximo exercicio e proxima serie conhecidos pela sequencia prescrita.
            if (lastSet) { exerciseIndex++; nextSetIndex = 0 }
            else nextSetIndex++
            phase = SessionPhase.REST
            phaseStartedMs = now
            restDeadlineMs = now + ex.restSec.coerceAtLeast(1) * 1000L
            restNoticeSent = false
            output.add(Transition("rest_start", "Descanso prescrito: ${ex.restSec}s"))
        }
        return output
    }

    fun tick(now: Long): Transition? {
        if (phase == SessionPhase.REST && !restNoticeSent && now >= restDeadlineMs) {
            restNoticeSent = true
            return Transition("rest_due", "Descanso concluido: ${currentExercise.name}, serie $currentSetNumber")
        }
        return null
    }

    fun remainingRest(now: Long): Long = if (phase == SessionPhase.REST) ((restDeadlineMs - now).coerceAtLeast(0) + 999) / 1000 else 0L
    fun elapsedSec(now: Long): Long = if (phase == SessionPhase.ACTIVE) ((now - phaseStartedMs).coerceAtLeast(0)) / 1000 else 0L

    fun finish(): Transition {
        phase = SessionPhase.FINISHED
        return Transition("session_end", "Sessao encerrada")
    }

    fun confirmLastRest(now: Long) {
        if (phase == SessionPhase.REST) completed.lastOrNull()?.restMs = (now - phaseStartedMs).coerceAtLeast(0)
    }

    private fun feedback(ex: ExercisePlan, set: CompletedSet): String? {
        val duration = set.durationMs / 1000.0
        val lower = ex.minDurationSec
        val upper = ex.maxDurationSec
        if (lower != null && duration < lower) return "Tempo abaixo da faixa planejada ($lower s). Confira a cadencia; o tempo sozinho nao confirma erro."
        if (upper != null && duration > upper) return "Tempo acima da faixa planejada ($upper s). Verifique o registro e a execucao com seu professor."
        val prior = completed.dropLast(1).lastOrNull { it.exerciseIndex == set.exerciseIndex }
        if (prior != null && prior.durationMs > 0L && set.actualReps != null && prior.actualReps == set.actualReps) {
            val ratio = set.durationMs.toDouble() / prior.durationMs
            if (ratio < 0.75) return "Esta serie foi ${((1 - ratio) * 100).roundToInt()}% mais rapida que a anterior com as mesmas repeticoes registradas. Confira a cadencia."
        }
        return null
    }
}

object Plans {
    private const val KEY = "prescription_json"
    fun example(): WorkoutPlan = WorkoutPlan("Treino exemplo - personalize", listOf(
        ExercisePlan("Supino reto", 4, 12, 90, null, 35, 55),
        ExercisePlan("Puxada frontal", 3, 10, 90),
        ExercisePlan("Agachamento", 3, 10, 120)
    ))
    fun load(context: Context): WorkoutPlan = try {
        val raw = context.getSharedPreferences("treinosmart_plan", Context.MODE_PRIVATE).getString(KEY, null)
        if (raw.isNullOrBlank()) example() else fromJson(JSONObject(raw))
    } catch (_: Exception) { example() }
    fun save(context: Context, plan: WorkoutPlan) {
        context.getSharedPreferences("treinosmart_plan", Context.MODE_PRIVATE).edit()
            .putString(KEY, toJson(plan).toString()).apply()
    }
    fun toJson(plan: WorkoutPlan): JSONObject = JSONObject().put("name", plan.name).put("exercises", JSONArray().apply {
        plan.exercises.forEach { ex -> put(JSONObject()
            .put("name", ex.name).put("sets", ex.sets).put("reps", ex.reps)
            .put("restSec", ex.restSec)
            .put("plannedLoadKg", ex.plannedLoadKg ?: JSONObject.NULL)
            .put("minDurationSec", ex.minDurationSec ?: JSONObject.NULL)
            .put("maxDurationSec", ex.maxDurationSec ?: JSONObject.NULL)) }
    })
    fun fromJson(obj: JSONObject): WorkoutPlan {
        val arr = obj.getJSONArray("exercises")
        val exercises = (0 until arr.length()).map { i ->
            val e = arr.getJSONObject(i)
            fun intOrNull(key: String): Int? = if (e.isNull(key) || !e.has(key)) null else e.optInt(key).takeIf { it > 0 }
            ExercisePlan(
                e.getString("name"), e.getInt("sets").coerceIn(1, 20),
                e.getInt("reps").coerceIn(1, 100), e.getInt("restSec").coerceIn(1, 1800),
                if (e.isNull("plannedLoadKg") || !e.has("plannedLoadKg")) null else e.optDouble("plannedLoadKg").takeIf { it.isFinite() && it >= 0.0 },
                intOrNull("minDurationSec"), intOrNull("maxDurationSec"))
        }
        return WorkoutPlan(obj.optString("name", "Treino"), exercises)
    }
}
