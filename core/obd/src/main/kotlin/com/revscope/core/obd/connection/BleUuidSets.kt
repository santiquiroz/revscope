package com.revscope.core.obd.connection

/**
 * BLE UUID sets for the 6 ELM327-compatible chip families verified by AndrOBD.
 * Each set identifies the GATT service and the write/notify characteristics.
 */
data class BleUuidSet(
    val name: String,
    val service: String,
    val writeChar: String,
    val notifyChar: String,
)

object BleUuidSets {

    val CC254X = BleUuidSet(
        name = "CC254X",
        service    = "0000FFE0-0000-1000-8000-00805F9B34FB",
        writeChar  = "0000FFE1-0000-1000-8000-00805F9B34FB",
        notifyChar = "0000FFE1-0000-1000-8000-00805F9B34FB",
    )

    val VLINK = BleUuidSet(
        name = "VLink",
        service    = "000018F0-0000-1000-8000-00805F9B34FB",
        writeChar  = "00002AF0-0000-1000-8000-00805F9B34FB",
        notifyChar = "00002AF1-0000-1000-8000-00805F9B34FB",
    )

    val NEXAS = BleUuidSet(
        name = "Nexas",
        service    = "0000FFF0-0000-1000-8000-00805F9B34FB",
        writeChar  = "0000FFF2-0000-1000-8000-00805F9B34FB",
        notifyChar = "0000FFF1-0000-1000-8000-00805F9B34FB",
    )

    val NORDIC_UART = BleUuidSet(
        name = "Nordic UART",
        service    = "6E400001-B5A3-F393-E0A9-E50E24DCCA9E",
        writeChar  = "6E400002-B5A3-F393-E0A9-E50E24DCCA9E",
        notifyChar = "6E400003-B5A3-F393-E0A9-E50E24DCCA9E",
    )

    val MICROCHIP = BleUuidSet(
        name = "Microchip",
        service    = "49535343-FE7D-4AE5-8FA9-9FAFD205E455",
        writeChar  = "49535343-8841-43F4-A8D4-ECBE34729BB3",
        notifyChar = "49535343-1E4D-4BD9-BA61-23C647249616",
    )

    val TIO = BleUuidSet(
        name = "TIO",
        service    = "0000FEFB-0000-1000-8000-00805F9B34FB",
        writeChar  = "00000002-0000-0000-0000-000000000000",
        notifyChar = "00000001-0000-0000-0000-000000000000",
    )

    val ALL: List<BleUuidSet> = listOf(CC254X, VLINK, NEXAS, NORDIC_UART, MICROCHIP, TIO)
}
