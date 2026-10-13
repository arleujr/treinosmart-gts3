package com.treinosmart.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.ArrayDeque
import java.util.UUID

/** Recebe notificacoes GATT; escreve somente CCCD para habilitar as assinaturas BLE. */
class GtsBleClient(
    private val context: Context,
    private val onStatus: (String) -> Unit,
    private val onSample: (kind: String, value: String, raw: String) -> Unit
) {
    private val main = Handler(Looper.getMainLooper())
    private var gatt: BluetoothGatt? = null
    private var device: BluetoothDevice? = null
    private var shouldReconnect = false
    private var attempt = 0
    private var pendingReconnect: Runnable? = null
    private val pendingCccd = ArrayDeque<BluetoothGattCharacteristic>()
    private val commands = ArrayDeque<Pair<String, ByteArray>>()
    private var readyForCommands = false
    private var sending = false
    private var autoHeartRate = false
    private val commandTick = object : Runnable {
        override fun run() {
            sending = false
            drainCommands()
        }
    }

    companion object {
        val HEART_RATE_SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HEART_RATE_MEASUREMENT: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val MOYOUNG_SERVICE: UUID = UUID.fromString("0000feea-0000-1000-8000-00805f9b34fb")
        val MOYOUNG_NOTIFY: UUID = UUID.fromString("0000fee3-0000-1000-8000-00805f9b34fb")
        val MOYOUNG_WRITE: UUID = UUID.fromString("0000fee2-0000-1000-8000-00805f9b34fb")
        val MOYOUNG_AUX: UUID = UUID.fromString("0000fee1-0000-1000-8000-00805f9b34fb")
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        fun bpm(payload: ByteArray): Int? {
            if (payload.size < 2) return null
            val flag = payload[0].toInt() and 0xff
            return if (flag and 0x01 == 0) {
                payload[1].toInt() and 0xff
            } else if (payload.size >= 3) {
                (payload[1].toInt() and 0xff) or ((payload[2].toInt() and 0xff) shl 8)
            } else null
        }
        fun hex(payload: ByteArray): String = payload.joinToString("") { "%02X".format(it.toInt() and 0xff) }
    }

    @SuppressLint("MissingPermission")
    fun connect(selected: BluetoothDevice) {
        disconnect()
        device = selected
        shouldReconnect = true
        attempt = 0
        connectCurrent()
    }

    @SuppressLint("MissingPermission")
    private fun connectCurrent() {
        val target = device ?: return
        onStatus("Conectando ao relogio...")
        try {
            gatt = target.connectGatt(context.applicationContext, false, callback, BluetoothDevice.TRANSPORT_LE)
        } catch (ex: Exception) {
            onStatus("Falha na conexao: ${ex.message}")
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        if (!shouldReconnect) return
        attempt++
        val delay = minOf(30000L, 2000L * minOf(attempt, 10))
        onStatus("Desconectado. Nova tentativa em ${delay / 1000} s...")
        val action = Runnable { if (shouldReconnect) connectCurrent() }
        pendingReconnect = action
        main.postDelayed(action, delay)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        shouldReconnect = false
        pendingReconnect?.let(main::removeCallbacks)
        pendingReconnect = null
        pendingCccd.clear()
        commands.clear()
        readyForCommands = false
        sending = false
        main.removeCallbacks(commandTick)
        val old = gatt
        gatt = null
        old?.disconnect()
        old?.close()
        onStatus("Desconectado manualmente")
    }

    private val callback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (g !== gatt) return
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                attempt = 0
                onStatus("Conectado. Descobrindo servicos...")
                readyForCommands = false
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                readyForCommands = false
                commands.clear()
                sending = false
                main.removeCallbacks(commandTick)
                onSample("connection", "disconnected", "status=$status")
                g.close()
                if (g === gatt) gatt = null
                main.post { scheduleReconnect() }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (g !== gatt) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                onStatus("Falha ao descobrir servicos: $status")
                return
            }
            pendingCccd.clear()
            readyForCommands = false
            g.getService(HEART_RATE_SERVICE)?.getCharacteristic(HEART_RATE_MEASUREMENT)?.let { pendingCccd.add(it) }
            g.getService(MOYOUNG_SERVICE)?.getCharacteristic(MOYOUNG_NOTIFY)?.let { pendingCccd.add(it) }
            g.getService(MOYOUNG_SERVICE)?.getCharacteristic(MOYOUNG_AUX)?.let {
                if ((it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 && it.getDescriptor(CCCD) != null) pendingCccd.add(it)
            }
            if (pendingCccd.isEmpty()) {
                onStatus("Servicos HR e Moyoung nao encontrados")
            } else {
                onStatus("Servicos encontrados. Habilitando notificacoes...")
                subscribeNext(g)
            }
        }

        @SuppressLint("MissingPermission")
        private fun subscribeNext(g: BluetoothGatt) {
            if (g !== gatt) return
            if (pendingCccd.isEmpty()) {
                readyForCommands = true
                onStatus("BLE preparado. Controle Moyoung disponivel para teste.")
                main.post {
                    if (autoHeartRate) enqueue("Iniciar BPM dinamico", MoyoungCommands.dynamicHeartRate(true))
                    drainCommands()
                }
                return
            }
            val characteristic = pendingCccd.removeFirst()
            if (!g.setCharacteristicNotification(characteristic, true)) {
                onStatus("Nao foi possivel habilitar ${characteristic.uuid}")
                subscribeNext(g)
                return
            }
            val descriptor = characteristic.getDescriptor(CCCD)
            if (descriptor == null) {
                onStatus("Sem CCCD em ${characteristic.uuid}")
                subscribeNext(g)
                return
            }
            val value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            val started = if (Build.VERSION.SDK_INT >= 33) {
                g.writeDescriptor(descriptor, value) == android.bluetooth.BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = value
                @Suppress("DEPRECATION")
                g.writeDescriptor(descriptor)
            }
            if (!started) {
                onStatus("Erro ao assinar ${characteristic.uuid}")
                subscribeNext(g)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (g !== gatt) return
            if (status != BluetoothGatt.GATT_SUCCESS) onStatus("Falha CCCD ${descriptor.characteristic.uuid}: $status")
            subscribeNext(g)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            receive(characteristic.uuid, characteristic.value ?: byteArrayOf())
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            receive(characteristic.uuid, value)
        }
    }


    /** Nunca envia comandos antes das assinaturas, nem grava firmware. */
    fun setDynamicHeartRate(enable: Boolean) {
        autoHeartRate = enable
        enqueue(if (enable) "BPM dinamico ON" else "BPM dinamico OFF", MoyoungCommands.dynamicHeartRate(enable))
    }

    fun requestManualHeartRate() = enqueue("Medicao pontual BPM", MoyoungCommands.manualHeartRate(true))

    fun vibrateFindWatch() = enqueue("Localizar relogio / vibrar", MoyoungCommands.findWatch())

    private fun enqueue(label: String, frame: ByteArray) {
        main.post {
            if (!readyForCommands) {
                onStatus("Aguardando BLE: comando pendente: $label")
                // Evitar fila de comandos antigos se o relogio ficar desconectado.
                return@post
            }
            commands.addLast(label to frame)
            drainCommands()
        }
    }

    @SuppressLint("MissingPermission")
    private fun drainCommands() {
        if (!readyForCommands || sending || commands.isEmpty()) return
        val current = gatt ?: return
        val char = current.getService(MOYOUNG_SERVICE)?.getCharacteristic(MOYOUNG_WRITE)
        if (char == null) { onStatus("Canal 0xFEE2 indisponivel"); commands.clear(); return }
        val (label, frame) = commands.removeFirst()
        val noResponse = char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
        char.writeType = if (noResponse) BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        val ok = try {
            if (Build.VERSION.SDK_INT >= 33) {
                current.writeCharacteristic(char, frame, char.writeType) == android.bluetooth.BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                char.value = frame
                @Suppress("DEPRECATION")
                current.writeCharacteristic(char)
            }
        } catch (e: Exception) {
            onStatus("Falha enviando $label: ${e.message}")
            false
        }
        if (ok) onStatus("Enviado $label (${hex(frame)}); efeito nao confirmado")
        else onStatus("BLE rejeitou envio $label")
        // E' WRITE_NO_RESPONSE: espacamento conservador entre comandos curtos.
        sending = true
        main.postDelayed(commandTick, 650L)
    }

    private fun receive(uuid: UUID, value: ByteArray) {
        when (uuid) {
            HEART_RATE_MEASUREMENT -> {
                val hr = bpm(value)
                if (hr != null) onSample("hr", hr.toString(), hex(value))
                else onSample("hr_unparsed", "", hex(value))
            }
            MOYOUNG_NOTIFY -> onSample("moyoung", "", hex(value))
            MOYOUNG_AUX -> onSample("moyoung_aux", "", hex(value))
        }
    }
}
