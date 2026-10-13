package com.treinosmart.ble
fun main() {
  fun hex(data: ByteArray) = data.joinToString(" ") { "%02X".format(it.toInt() and 255) }
  check(hex(MoyoungCommands.dynamicHeartRate(true)) == "FE EA 20 06 68 00")
  check(hex(MoyoungCommands.manualHeartRate(true)) == "FE EA 20 06 6D 00")
  check(hex(MoyoungCommands.findWatch()) == "FE EA 20 05 61")
  println("Protocol frames OK")
}
