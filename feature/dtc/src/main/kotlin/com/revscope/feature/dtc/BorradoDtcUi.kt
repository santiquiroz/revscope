package com.revscope.feature.dtc

import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.common.units.UnitFormatter
import com.revscope.core.obd.diagnostics.BorradoDtc
import com.revscope.core.obd.diagnostics.RechazoBorradoDtc
import com.revscope.feature.dtc.R
import kotlin.math.roundToInt

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

fun textoRechazoUi(rechazo: RechazoBorradoDtc, resolver: StringResolver, units: UnitFormatter): String = when (rechazo) {
    RechazoBorradoDtc.SinConfirmar -> resolver.get(R.string.dtc_clear_confirmation_required)
    RechazoBorradoDtc.SinVelocidadReciente ->
        resolver.get(R.string.dtc_no_recent_speed)
    is RechazoBorradoDtc.EnMovimiento ->
        resolver.get(R.string.dtc_vehicle_moving, textoVelocidad(rechazo.kmh, units))
}

// Espacio normal, no el duro del formatter: el texto en métrico queda idéntico al de antes.
internal fun textoVelocidad(kmh: Int, units: UnitFormatter): String =
    "${units.speed(kmh.toDouble()).roundToInt()} ${units.speedLabel()}"

fun avisosBorradoDtc(freezeFrameEnSesion: Boolean, resolver: StringResolver): List<String> =
    listOf(
        resolver.get(R.string.dtc_readiness_warning),
        avisoFreezeFrame(freezeFrameEnSesion, resolver),
        resolver.get(R.string.dtc_clear_does_not_repair),
    )

private fun avisoFreezeFrame(enSesion: Boolean, resolver: StringResolver): String =
    resolver.get(if (enSesion) R.string.dtc_freeze_frame_clear_saved else R.string.dtc_freeze_frame_clear_lost)

fun textoResultadoBorrado(resultado: ResultadoBorradoUi, resolver: StringResolver): String = when {
    resultado.rechazadoPorEcu ->
        resolver.get(R.string.dtc_clear_rejected_by_ecu)
    !resultado.enviado ->
        resolver.get(R.string.dtc_clear_send_failed)
    resultado.despues.isNotEmpty() ->
        resolver.get(R.string.dtc_codes_still_reported, resultado.despues.joinToString())
    !resultado.despuesLeido ->
        resolver.get(R.string.dtc_clear_reread_failed)
    else -> resolver.get(
        R.string.dtc_codes_cleared_summary,
        textoAntes(resultado, resolver),
        resolver.get(R.string.dtc_no_active_codes),
    )
}

private fun textoAntes(resultado: ResultadoBorradoUi, resolver: StringResolver): String =
    if (resultado.antesLeido) codigosOSinCodigos(resultado.antes, resolver)
    else resolver.get(R.string.dtc_codes_not_read)

private fun codigosOSinCodigos(codigos: List<String>, resolver: StringResolver): String =
    codigos.joinToString().ifEmpty { resolver.get(R.string.dtc_no_active_codes) }
