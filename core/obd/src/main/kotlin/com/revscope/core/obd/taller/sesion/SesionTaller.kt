package com.revscope.core.obd.taller.sesion

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

enum class Sintoma(val etiqueta: String) {
    NO_ARRANCA("No arranca"),
    ARRANQUE_DIFICIL_FRIO("Cuesta arrancar en frío"),
    NO_SOSTIENE_MINIMO_FRIO("No sostiene el mínimo en frío"),
    MINIMO_INESTABLE("Mínimo inestable"),
    SE_APAGA_AL_SOLTAR("Se apaga al soltar el acelerador"),
    SE_AHOGA_AL_ACELERAR("Se ahoga al acelerar"),
    PIERDE_POTENCIA("Pierde potencia"),
    TIRONES("Tirones"),
    CONSUMO_ALTO("Consume más"),
    MIL_ENCENDIDA("Testigo de falla encendido"),
    BATERIA_DESCARGA("Se descarga la batería"),
    SOBRECALIENTA("Se calienta de más"),
    OTRO("Otro"),
}

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
