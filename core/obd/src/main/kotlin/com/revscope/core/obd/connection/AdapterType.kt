package com.revscope.core.obd.connection

/** Tipo de transporte del adaptador ELM327. Persiste como string en DataStore (ADAPTER_TYPE). */
enum class AdapterType {
    CLASSIC_BT,
    BLE;

    companion object {
        fun from(value: String?): AdapterType =
            entries.firstOrNull { it.name == value } ?: CLASSIC_BT
    }
}
