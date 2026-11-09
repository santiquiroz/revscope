package com.revscope.core.obd.taller.sesion

import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.R
data class SesionTaller(
    val id: Long = 0,
    val vehiculoId: Long,
    val claveModelo: String? = null,
    val inicio: Long,
    val cierre: Long? = null,
    val titulo: String,
    val sintomas: Set<Sintoma> = emptySet(),
    val sintomasTexto: String = "",
    val notas: String = "",
    val odometroKm: Double? = null,
    val chequeoBaseId: Long? = null,
    val interpretacion: String = "",
    val pasosMarcados: Set<String> = emptySet(),
) {
    val abierta: Boolean get() = cierre == null
}

enum class Sintoma {
    NO_ARRANCA,
    ARRANQUE_DIFICIL_FRIO,
    NO_SOSTIENE_MINIMO_FRIO,
    MINIMO_INESTABLE,
    SE_APAGA_AL_SOLTAR,
    SE_AHOGA_AL_ACELERAR,
    PIERDE_POTENCIA,
    TIRONES,
    CONSUMO_ALTO,
    MIL_ENCENDIDA,
    BATERIA_DESCARGA,
    SOBRECALIENTA,
    OTRO,
}

val Sintoma.etiqueta: String get() = when (this) { // i18n-ignore
    Sintoma.NO_ARRANCA -> "No arranca" // i18n-ignore
    Sintoma.ARRANQUE_DIFICIL_FRIO -> "Cuesta arrancar en frío" // i18n-ignore
    Sintoma.NO_SOSTIENE_MINIMO_FRIO -> "No sostiene el mínimo en frío" // i18n-ignore
    Sintoma.MINIMO_INESTABLE -> "Mínimo inestable" // i18n-ignore
    Sintoma.SE_APAGA_AL_SOLTAR -> "Se apaga al soltar el acelerador" // i18n-ignore
    Sintoma.SE_AHOGA_AL_ACELERAR -> "Se ahoga al acelerar" // i18n-ignore
    Sintoma.PIERDE_POTENCIA -> "Pierde potencia" // i18n-ignore
    Sintoma.TIRONES -> "Tirones" // i18n-ignore
    Sintoma.CONSUMO_ALTO -> "Consume más" // i18n-ignore
    Sintoma.MIL_ENCENDIDA -> "Testigo de falla encendido" // i18n-ignore
    Sintoma.BATERIA_DESCARGA -> "Se descarga la batería" // i18n-ignore
    Sintoma.SOBRECALIENTA -> "Se calienta de más" // i18n-ignore
    Sintoma.OTRO -> "Otro" // i18n-ignore
}

fun Sintoma.etiqueta(resolver: StringResolver): String = resolver.get(
    when (this) {
        Sintoma.NO_ARRANCA -> R.string.obd_session_symptom_no_start
        Sintoma.ARRANQUE_DIFICIL_FRIO -> R.string.obd_session_symptom_hard_start_cold
        Sintoma.NO_SOSTIENE_MINIMO_FRIO -> R.string.obd_session_symptom_cannot_idle_cold
        Sintoma.MINIMO_INESTABLE -> R.string.obd_session_symptom_unsteady_idle
        Sintoma.SE_APAGA_AL_SOLTAR -> R.string.obd_session_symptom_stalls_release
        Sintoma.SE_AHOGA_AL_ACELERAR -> R.string.obd_session_symptom_stumbles_accelerating
        Sintoma.PIERDE_POTENCIA -> R.string.obd_session_symptom_power_loss
        Sintoma.TIRONES -> R.string.obd_session_symptom_jerking
        Sintoma.CONSUMO_ALTO -> R.string.obd_session_symptom_high_fuel_use
        Sintoma.MIL_ENCENDIDA -> R.string.obd_session_symptom_mil_on
        Sintoma.BATERIA_DESCARGA -> R.string.obd_session_symptom_battery_discharges
        Sintoma.SOBRECALIENTA -> R.string.obd_session_symptom_overheating
        Sintoma.OTRO -> R.string.obd_session_symptom_other
    },
)

data class EventoTaller(
    val id: Long = 0,
    val sesionId: Long,
    val instante: Long,
    val tipo: TipoEvento,
    val origen: OrigenEvento = OrigenEvento.APP,
    val titulo: String,
    val resumen: String = "",
    val veredicto: Veredicto = Veredicto.INFO,
    val payloadVersion: Int = 1,
    val payloadJson: String = "{}",
    val adjunto: String? = null,
)

enum class TipoEvento {
    NOTA, SINTOMAS, DTC_LECTURA, DTC_BORRADO, CHEQUEO, PRUEBA_GUIADA, CAPTURA,
    MEDICION_MULTIMETRO, INSTANTANEA_SENSORES,
}

enum class OrigenEvento { APP, MCP }

enum class Veredicto { OK, ATENCION, FALLA, INFO }
