package com.revscope.feature.dtc

import com.revscope.core.obd.diagnostics.BorradoDtc
import com.revscope.core.obd.diagnostics.RechazoBorradoDtc

data class ConfirmacionBorradoDtc(
    val codigos: List<String>,
    val rechazo: RechazoBorradoDtc?,
    val freezeFrameEnSesion: Boolean = false,
)

data class ResultadoBorradoUi(
    val antes: List<String>,
    val despues: List<String>,
    val rechazadoPorEcu: Boolean,
    val enviado: Boolean = true,
    val antesLeido: Boolean = true,
    val despuesLeido: Boolean = true,
)

fun resultadoBorradoUi(borrado: BorradoDtc): ResultadoBorradoUi = ResultadoBorradoUi(
    antes = borrado.antes.activos.map { it.code },
    despues = borrado.despues.activos.map { it.code },
    rechazadoPorEcu = borrado.rechazadoPorCondiciones,
    enviado = borrado.respuestaCruda != null,
    antesLeido = borrado.antes.activosConfirmados,
    despuesLeido = borrado.despues.activosConfirmados,
)

// Sin 0D reciente (muchas motos no lo reportan o no hay sondeo) la persona puede declarar que el
// vehículo está detenido; en movimiento nunca. El MCP no tiene esa salida: allí no hay nadie mirando.
fun permiteBorrarDesdeUi(rechazo: RechazoBorradoDtc?, declaraDetenido: Boolean): Boolean = when (rechazo) {
    null -> true
    RechazoBorradoDtc.SinVelocidadReciente -> declaraDetenido
    RechazoBorradoDtc.SinConfirmar, is RechazoBorradoDtc.EnMovimiento -> false
}

fun textoRechazoUi(rechazo: RechazoBorradoDtc): String = when (rechazo) {
    RechazoBorradoDtc.SinConfirmar -> "Falta confirmar el borrado."
    RechazoBorradoDtc.SinVelocidadReciente ->
        "No hay una lectura reciente de velocidad para comprobar que el vehículo está detenido."
    is RechazoBorradoDtc.EnMovimiento ->
        "El vehículo va a ${rechazo.kmh} km/h. Detente antes de borrar los códigos."
}

const val AVISO_READINESS_DTC =
    "Los monitores de preparación (readiness) quedan incompletos: la revisión técnico-mecánica puede " +
        "rechazar el vehículo hasta completar ciclos de manejo."
const val AVISO_REAPARICION_DTC = "Borrar no repara nada: si la falla sigue, el código vuelve."

fun avisosBorradoDtc(freezeFrameEnSesion: Boolean): List<String> =
    listOf(AVISO_READINESS_DTC, avisoFreezeFrame(freezeFrameEnSesion), AVISO_REAPARICION_DTC)

private fun avisoFreezeFrame(enSesion: Boolean): String =
    if (enSesion) {
        "El freeze frame (el estado del motor cuando se guardó la falla) se borra de la ECU; ya quedó guardado en la sesión."
    } else {
        "El freeze frame (el estado del motor cuando se guardó la falla) se pierde. Guarda antes la lectura en una sesión."
    }

fun textoResultadoBorrado(resultado: ResultadoBorradoUi): String = when {
    resultado.rechazadoPorEcu ->
        "La ECU rechazó el borrado (7F 04 22: condiciones no correctas). Muchas ECU solo borran con el motor " +
            "apagado: apaga el motor, deja el contacto puesto y vuelve a intentarlo. Los códigos siguen guardados."
    !resultado.enviado ->
        "No se pudo enviar el borrado: el adaptador no respondió. Vuelve a leer los códigos antes de intentarlo otra vez."
    resultado.despues.isNotEmpty() ->
        "Se envió el borrado, pero la ECU sigue reportando ${resultado.despues.joinToString()}: la falla sigue presente."
    !resultado.despuesLeido ->
        "Se envió el borrado, pero no se pudo releer la ECU para confirmarlo. Vuelve a leer los códigos."
    else -> "Códigos borrados. Antes: ${textoAntes(resultado)}. Después: sin códigos activos."
}

private fun textoAntes(resultado: ResultadoBorradoUi): String =
    if (resultado.antesLeido) codigosOSinCodigos(resultado.antes) else "no se pudieron leer"

private fun codigosOSinCodigos(codigos: List<String>): String = codigos.joinToString().ifEmpty { "sin códigos activos" }
