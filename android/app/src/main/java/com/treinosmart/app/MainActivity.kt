package com.treinosmart.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

/** Sem dependencias de UI externas: facil de compilar no GitHub Actions. */
class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var log: TrainingLog
    private lateinit var root: LinearLayout
    private lateinit var connection: TextView
    private lateinit var heart: TextView
    private lateinit var health: TextView
    private lateinit var motionInfo: TextView
    private lateinit var state: TextView
    private lateinit var timer: TextView
    private lateinit var contextInfo: TextView
    private lateinit var metrics: TextView
    private lateinit var paired: Spinner
    private lateinit var exerciseSpinner: Spinner
    private lateinit var titleField: EditText
    private lateinit var nameField: EditText
    private lateinit var setField: EditText
    private lateinit var repField: EditText
    private lateinit var restField: EditText
    private lateinit var loadField: EditText
    private lateinit var minField: EditText
    private lateinit var maxField: EditText
    private lateinit var realReps: EditText
    private lateinit var realLoad: EditText
    private lateinit var beginButton: Button
    private lateinit var endButton: Button
    private var bonded = listOf<BluetoothDevice>()
    private var plan: WorkoutPlan = Plans.example()
    private var editingIndex: Int? = null
    private val bleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { refreshLive() }
    }
    private val sessionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { refreshLive() }
    }
    private val pulse = object : Runnable {
        override fun run() { if (::root.isInitialized) refreshLive(); handler.postDelayed(this, 1000L) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log = TrainingLog(this)
        plan = Plans.load(this)
        buildInterface()
        refreshDevices()
        refreshExerciseList()
        handler.post(pulse)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 203)
        }
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 204)
        }
    }

    private val background = Color.rgb(245, 247, 251)
    private val ink = Color.rgb(23, 36, 57)
    private val muted = Color.rgb(94, 107, 126)
    private val accent = Color.rgb(21, 84, 155)
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun text(value: String, size: Float = 15f, bold: Boolean = false): TextView = TextView(this).apply {
        this.text = value
        textSize = size
        setTextColor(if (bold) ink else muted)
        if (bold) setTypeface(null, Typeface.BOLD)
        setPadding(0, dp(3), 0, dp(3))
    }
    private fun field(label: String, placeholder: String, decimal: Boolean = false): EditText = EditText(this).apply {
        hint = label
        setSingleLine(true)
        setText(placeholder)
        textSize = 15f
        inputType = if (decimal) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED else InputType.TYPE_CLASS_TEXT
        setPadding(dp(8), dp(7), dp(8), dp(7))
    }
    private fun num(label: String, value: String = ""): EditText = field(label, value, true)
    private fun button(label: String, click: () -> Unit): Button = Button(this).apply {
        text = label; textSize = 13f; isAllCaps = false; setOnClickListener { click() }
    }
    private fun card(title: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(15))
        background = GradientDrawable().apply {
            setColor(Color.WHITE); cornerRadius = dp(14).toFloat()
            setStroke(dp(1), Color.rgb(221, 227, 235))
        }
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = dp(12)
        root.addView(this, lp)
        addView(text(title, 17f, true))
    }
    private fun pair(layout: LinearLayout, left: View, right: View) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val a = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        val b = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        a.rightMargin = dp(5); b.leftMargin = dp(5)
        row.addView(left, a); row.addView(right, b)
        layout.addView(row)
    }

    private fun buildInterface() {
        val scroll = ScrollView(this).apply { setBackgroundColor(this@MainActivity.background) }
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(18), dp(16), dp(26)) }
        scroll.addView(root)
        root.addView(text("TreinoSmart", 27f, true))
        root.addView(text("v0.5 • Comandos diretos Moyoung + detector experimental pelo BPM", 12f))

        val bleCard = card("01  Relogio e dados")
        paired = Spinner(this); bleCard.addView(paired)
        pair(bleCard, button("Atualizar lista") { refreshDevices() }, button("Conectar GTS 3") { connectWatch() })
        bleCard.addView(button("Desconectar BLE") { service(GtsMonitorService::class.java, GtsMonitorService.ACTION_DISCONNECT) })
        connection = text("Bluetooth: desconectado", 14f); bleCard.addView(connection)
        heart = text("-- BPM", 32f, true); bleCard.addView(heart)
        health = text("Sem amostras recentes. O sensor pode estar inativo.", 12f); bleCard.addView(health)
        bleCard.addView(text("Primeiro conecte o GTS 3. Os comandos sao experimentais; observe se o relogio responde e registre os dados.", 12f))
        pair(bleCard,
            button("Ligar BPM dinamico") { service(GtsMonitorService::class.java, GtsMonitorService.ACTION_HR_DYNAMIC_ON) },
            button("Parar BPM dinamico") { service(GtsMonitorService::class.java, GtsMonitorService.ACTION_HR_DYNAMIC_OFF) })
        pair(bleCard,
            button("Solicitar BPM") { service(GtsMonitorService::class.java, GtsMonitorService.ACTION_REQUEST_HR) },
            button("Testar vibracao no GTS 3") { service(GtsMonitorService::class.java, GtsMonitorService.ACTION_VIBRATE) })

        val current = card("02  Sessao em andamento")
        state = text("Nenhuma sessao ativa", 18f, true); current.addView(state)
        timer = text("00:00", 32f, true); current.addView(timer)
        contextInfo = text("Inicie o treino cadastrado ou teste a demonstracao.", 13f); current.addView(contextInfo)
        metrics = text("Sem series registradas", 13f); current.addView(metrics)
        pair(current, button("Iniciar treino") { startTraining(false) }, button("Demo automatica") { startTraining(true) })
        beginButton = button("Iniciar serie") { service(WorkoutSessionService::class.java, WorkoutSessionService.BEGIN) }
        endButton = button("Concluir serie → descansar") { endSet() }
        current.addView(beginButton); current.addView(endButton)
        val automation = card("03  Automacao experimental")
        automation.addView(text("Usa SOMENTE o BPM enviado pelo relogio para estimar inicio/fim de series por tendencias. A frequencia cardiaca reage com atraso; revise as marcacoes. Nao precisa levar o celular no corpo.", 12f))
        motionInfo = text("Deteccao desativada", 13f); automation.addView(motionInfo)
        pair(automation, button("Ativar detector") {
            if (!isWorkoutRunning()) toast("Inicie uma sessao real primeiro")
            else service(WorkoutSessionService::class.java, WorkoutSessionService.AUTO_ON)
        }, button("Desativar detector") { service(WorkoutSessionService::class.java, WorkoutSessionService.AUTO_OFF) })
        automation.addView(text("Quando termina o descanso, o aplicativo envia o comando Moyoung 'Localizar relogio' (0x61). E' um teste de vibracao direta, nao um novo aplicativo instalado no relogio.", 12f))
        pair(current, button("Encerrar treino") { service(WorkoutSessionService::class.java, WorkoutSessionService.FINISH) },
            button("Testar aviso") { service(WorkoutSessionService::class.java, WorkoutSessionService.TEST_NOTIFICATION) })
        current.addView(text("Os botoes de serie sao a confirmacao manual provisoria. A demo usa tempos simulados, nao detecta movimentos reais.", 12f))

        val confirm = card("04  Dados reais da ultima serie")
        confirm.addView(text("Preencha apenas se souber o que realmente executou. Em branco = nao confirmado.", 12f))
        realReps = num("Repeticoes realizadas (opcional)")
        realLoad = num("Carga realizada em kg (opcional)")
        pair(confirm, realReps, realLoad)

        val setup = card("05  Planilha prescrita")
        titleField = field("Nome do treino", plan.name); setup.addView(titleField)
        setup.addView(button("Salvar nome do treino") {
            plan = plan.copy(name = titleField.text.toString().ifBlank { "Treino" })
            persistPlan()
        })
        setup.addView(text("Selecione para editar. A sequencia determina o exercicio atual.", 12f))
        exerciseSpinner = Spinner(this)
        setup.addView(exerciseSpinner)
        pair(setup, button("Editar selecionado") { editSelected() }, button("Novo exercicio") { blankEditor() })
        pair(setup, button("Mover para cima") { moveExercise(-1) }, button("Mover para baixo") { moveExercise(1) })
        nameField = field("Nome do exercicio", "")
        setField = num("Series", "3")
        repField = num("Repeticoes previstas", "10")
        restField = num("Descanso (segundos)", "90")
        loadField = num("Carga planejada kg (opcional)")
        minField = num("Tempo minimo/série em s (opcional)")
        maxField = num("Tempo maximo/série em s (opcional)")
        setup.addView(nameField)
        pair(setup, setField, repField)
        pair(setup, restField, loadField)
        pair(setup, minField, maxField)
        pair(setup, button("Salvar exercicio") { saveExercise() }, button("Excluir selecionado") { deleteExercise() })
        setup.addView(text("Exemplo inicial e ilustrativo. Substitua-o pelo treino do professor antes de usar.", 12f))

        val export = card("06  Analise e exportacao")
        export.addView(text("O CSV contem eventos de series/descansos, BPM com horario, e pacotes BLE. Dados simulados sao marcados como simulation.", 12f))
        export.addView(button("Exportar dados CSV") { exportCsv() })
        export.addView(text("Analise: tempo por serie, desvios frente a faixas definidas e descansos reais. Tonelagem so quando carga/repeticoes foram confirmadas.", 12f))
        val future = card("07  Pesquisa: metas do produto")
        future.addView(text("Em desenvolvimento: inicio pelo relogio, ativacao do BPM via Moyoung, deteccao automatica por movimento, aviso vibratorio DIRETO no relogio, sono e sincronizacao com backend/Strava.", 13f))
        future.addView(text("Controle direto do GTS 3 por Moyoung V2: comandos BPM e alerta. O detector usa tendencias do BPM, e as estimativas precisam de validacao com series marcadas manualmente. Uma tela instalavel no relogio ainda exige investigar o formato do mostrador especifico.", 12f))
        setContentView(scroll)
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    private fun persistPlan() {
        Plans.save(this, plan)
        refreshExerciseList()
        toast("Planilha salva no celular")
    }
    private fun refreshExerciseList() {
        if (!::exerciseSpinner.isInitialized) return
        val options = plan.exercises.mapIndexed { index, ex ->
            "${index + 1}. ${ex.name} • ${ex.sets}x${ex.reps} • ${ex.restSec}s"
        }
        exerciseSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, options)
    }
    private fun blankEditor() {
        editingIndex = null
        nameField.setText(""); setField.setText("3"); repField.setText("10")
        restField.setText("90"); loadField.setText(""); minField.setText(""); maxField.setText("")
    }
    private fun editSelected() {
        val ix = exerciseSpinner.selectedItemPosition
        val ex = plan.exercises.getOrNull(ix) ?: return
        editingIndex = ix
        nameField.setText(ex.name); setField.setText(ex.sets.toString())
        repField.setText(ex.reps.toString()); restField.setText(ex.restSec.toString())
        loadField.setText(ex.plannedLoadKg?.toString() ?: "")
        minField.setText(ex.minDurationSec?.toString() ?: "")
        maxField.setText(ex.maxDurationSec?.toString() ?: "")
    }
    private fun parseDouble(v: EditText): Double? = v.text.toString().trim().replace(',', '.').toDoubleOrNull()
    private fun saveExercise() {
        if (isWorkoutRunning()) { toast("Encerre a sessao antes de modificar o treino"); return }
        val name = nameField.text.toString().trim()
        val count = parseDouble(setField)?.toInt()
        val reps = parseDouble(repField)?.toInt()
        val rest = parseDouble(restField)?.toInt()
        val lo = parseDouble(minField)?.toInt()
        val hi = parseDouble(maxField)?.toInt()
        val load = parseDouble(loadField)
        if (name.isBlank() || count == null || count !in 1..20 || reps == null || reps !in 1..100 || rest == null || rest !in 1..1800 ||
            (load != null && load < 0) || (lo != null && lo < 1) || (hi != null && hi < 1) ||
            (lo != null && hi != null && lo > hi)) { toast("Confira nome, series, repeticoes, descanso e faixa de tempo"); return }
        val newExercise = ExercisePlan(name, count!!, reps!!, rest!!, load, lo, hi)
        val modified = plan.exercises.toMutableList()
        val ix = editingIndex
        if (ix != null && ix in modified.indices) modified[ix] = newExercise else modified.add(newExercise)
        plan = plan.copy(exercises = modified)
        persistPlan(); editingIndex = null; blankEditor()
    }
    private fun moveExercise(direction: Int) {
        if (isWorkoutRunning()) { toast("Encerre a sessao antes de reorganizar"); return }
        val from = exerciseSpinner.selectedItemPosition
        val to = from + direction
        if (from !in plan.exercises.indices || to !in plan.exercises.indices) return
        val list = plan.exercises.toMutableList()
        val moved = list.removeAt(from)
        list.add(to, moved)
        plan = plan.copy(exercises = list)
        persistPlan()
        exerciseSpinner.setSelection(to)
        editingIndex = null
    }
    private fun deleteExercise() {
        if (isWorkoutRunning()) { toast("Encerre a sessao antes de modificar o treino"); return }
        val ix = exerciseSpinner.selectedItemPosition
        if (ix !in plan.exercises.indices) return
        if (plan.exercises.size == 1) { toast("Mantenha ao menos um exercicio"); return }
        plan = plan.copy(exercises = plan.exercises.filterIndexed { i, _ -> i != ix })
        persistPlan(); blankEditor()
    }
    private fun isWorkoutRunning(): Boolean = WorkoutSessionService.engine?.phase in
        listOf(SessionPhase.ACTIVE, SessionPhase.READY, SessionPhase.REST)
    private fun startTraining(demo: Boolean) {
        if (isWorkoutRunning()) { toast("Encerre o treino atual antes de iniciar outro"); return }
        if (!demo && plan.exercises.isEmpty()) { toast("Adicione um exercicio"); return }
        service(WorkoutSessionService::class.java, if (demo) WorkoutSessionService.DEMO else WorkoutSessionService.START, true)
    }
    private fun endSet() {
        val intent = Intent(this, WorkoutSessionService::class.java).apply {
            action = WorkoutSessionService.END
            putExtra(WorkoutSessionService.EXTRA_REPS, parseDouble(realReps)?.toInt() ?: -1)
            putExtra(WorkoutSessionService.EXTRA_LOAD, parseDouble(realLoad) ?: -1.0)
        }
        startService(intent)
        realReps.setText(""); realLoad.setText("")
    }
    private fun service(clazz: Class<*>, actionName: String, foreground: Boolean = false) {
        try {
            val intent = Intent(this, clazz).apply { action = actionName }
            if (foreground && Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
        } catch (e: Exception) { toast("Nao foi possivel iniciar: ${e.message}") }
    }

    @SuppressLint("MissingPermission")
    private fun refreshDevices() {
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 204); return
        }
        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        bonded = if (adapter?.isEnabled == true) adapter.bondedDevices.sortedByDescending { it.name?.contains("GTS", true) == true } else emptyList()
        paired.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            bonded.map { it.name ?: "Dispositivo Bluetooth" })
    }
    @SuppressLint("MissingPermission")
    private fun connectWatch() {
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 204); return
        }
        val device = bonded.getOrNull(paired.selectedItemPosition)
        if (device == null) { toast("Selecione um dispositivo pareado"); return }
        try {
            val command = Intent(this, GtsMonitorService::class.java).apply {
                action = GtsMonitorService.ACTION_CONNECT
                putExtra(GtsMonitorService.EXTRA_ADDRESS, device.address)
            }
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(command) else startService(command)
        } catch (e: Exception) { toast("Falha Bluetooth: ${e.message}") }
    }

    private fun refreshLive() {
        val m = MonitorState
        connection.text = "Bluetooth: ${m.connectionStatus}"
        val age = if (m.lastBpmAtMs > 0) SystemClock.elapsedRealtime() - m.lastBpmAtMs else Long.MAX_VALUE
        heart.text = if (age <= 5000 && m.lastBpm != null) "${m.lastBpm} BPM" else "-- BPM"
        health.text = "Amostras: ${m.samples} • lacunas: ${m.gaps} • ${if (age <= 5000) "sinal recente" else "sem BPM recente"}"
        if (::motionInfo.isInitialized) motionInfo.text = "BPM como detector: ${if (WorkoutSessionService.automaticMode) "LIGADO" else "desligado"}; ${WorkoutSessionService.motionStatus}"
        val e = WorkoutSessionService.engine
        if (e == null || e.phase == SessionPhase.IDLE) {
            state.text = "Nenhuma sessao ativa"; timer.text = "00:00"
            contextInfo.text = "Cadastre o treino e toque em Iniciar treino"
            metrics.text = "Sem series registradas"; beginButton.isEnabled = false; endButton.isEnabled = false
            return
        }
        val ex = e.currentExercise
        state.text = (if (e.demo) "SIMULACAO • " else "") + "${e.phase.name} • ${ex.name}"
        contextInfo.text = "Serie ${e.currentSetNumber}/${ex.sets} • prescritas ${ex.reps} reps • descanso ${ex.restSec}s"
        val now = SystemClock.elapsedRealtime()
        timer.text = when (e.phase) {
            SessionPhase.ACTIVE -> "Serie ${e.elapsedSec(now)} s"
            SessionPhase.REST -> "Descanso ${e.remainingRest(now)} s"
            SessionPhase.FINISHED -> "Concluido"
            else -> "Preparado"
        }
        val confirmed = e.completed.filter { it.actualReps != null && it.actualLoadKg != null }
        val volume = confirmed.sumOf { it.actualReps!! * it.actualLoadKg!! }
        val tone = String.format(Locale.US, "%.1f", volume)
        val last = e.completed.lastOrNull()
        val average = if (e.completed.isEmpty()) "--" else String.format(Locale.US, "%.1f", e.completed.map { it.durationMs }.average() / 1000.0)
        metrics.text = "${e.completed.size} series registradas • media ${average}s • volume confirmado ${tone} kg-reps (${confirmed.size} series com carga e reps confirmadas)" +
            if (last != null) "\nUltima: ${last.durationMs / 1000}s • ${WorkoutSessionService.lastMessage}" else ""
        beginButton.isEnabled = e.phase == SessionPhase.READY || e.phase == SessionPhase.REST
        endButton.isEnabled = e.phase == SessionPhase.ACTIVE && !e.demo
    }

    private fun exportCsv() {
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/csv"
            putExtra(Intent.EXTRA_TITLE, "treinosmart_eventos.csv")
        }, 311)
    }
    @Deprecated("Usado apenas para salvar o CSV em documento escolhido pelo usuario")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 311 && resultCode == RESULT_OK) {
            try {
                val target = data?.data ?: return
                contentResolver.openOutputStream(target)?.use { it.write(log.bytes()) }
                toast("CSV exportado")
            } catch (ex: Exception) { toast("Falha ao exportar: ${ex.message}") }
        }
    }
    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(bleReceiver, IntentFilter(GtsMonitorService.ACTION_STATUS), GtsMonitorService.PRIVATE_PERMISSION, null, Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(sessionReceiver, IntentFilter(WorkoutSessionService.UPDATE), Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(bleReceiver, IntentFilter(GtsMonitorService.ACTION_STATUS), GtsMonitorService.PRIVATE_PERMISSION, null)
            registerReceiver(sessionReceiver, IntentFilter(WorkoutSessionService.UPDATE))
        }
    }
    override fun onStop() {
        unregisterReceiver(bleReceiver); unregisterReceiver(sessionReceiver)
        super.onStop()
    }
    override fun onDestroy() {
        handler.removeCallbacks(pulse)
        super.onDestroy()
    }
}
