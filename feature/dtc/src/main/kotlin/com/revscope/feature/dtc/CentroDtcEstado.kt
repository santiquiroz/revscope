package com.revscope.feature.dtc

import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.common.units.UnitFormatter
import com.revscope.core.obd.R as ObdR
import com.revscope.core.obd.diagnostics.DtcScan
import com.revscope.core.obd.diagnostics.RechazoBorradoDtc
import com.revscope.core.obd.diagnostics.ReglasBorradoDtc
import com.revscope.core.obd.model.DtcCode
import com.revscope.core.obd.model.DtcMode
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.protocol.DtcServicio
import com.revscope.core.obd.taller.dtc.AmbitoDtc
import com.revscope.core.obd.taller.dtc.DecodificadorDtc
import com.revscope.core.obd.taller.dtc.GuiaDtc
import com.revscope.core.obd.taller.sesion.SesionTaller
import com.revscope.core.obd.taller.sesion.Sintoma
import com.revscope.core.obd.taller.sesion.SolicitudSesion
import com.revscope.feature.dtc.R

sealed interface ExplicacionIa {
    data object NoPedida : ExplicacionIa
    data object Cargando : ExplicacionIa
    data class Lista(val texto: String, val faltaConfigurar: Boolean) : ExplicacionIa
    data object NoDisponible : ExplicacionIa
}

data class DtcCodeUi(
    val codigo: String,
    val modos: List<DtcMode>,
    val guia: GuiaDtc?,
    val sinGuia: String?,
    val explicacion: ExplicacionIa = ExplicacionIa.NoPedida,
)

data class MilUi(val encendida: Boolean, val texto: String)

data class FreezeFrameUi(val causante: String?, val items: List<FreezeFrameItem>)

sealed interface SesionDtcUi {
    data object Ninguna : SesionDtcUi
    data class Abierta(val titulo: String, val pasosMarcados: Set<String>) : SesionDtcUi
}

data class DetalleDtc(
    val mil: MilUi? = null,
    val freezeFrame: FreezeFrameUi? = null,
    val sesion: SesionDtcUi = SesionDtcUi.Ninguna,
    val lecturaEnSesion: Boolean = false,
    val guardandoEnSesion: Boolean = false,
    val guiasAbiertas: Set<String> = emptySet(),
    val pasosSinSesion: Set<String> = emptySet(),
    val bloqueoBorrado: String? = null,
    val mensaje: String? = null,
    val avisoLectura: String? = null,
    val sinCodigos: String? = null,
) {
    val pasosMarcados: Set<String>
        get() = (sesion as? SesionDtcUi.Abierta)?.pasosMarcados ?: pasosSinSesion
}

fun sesionDtcUi(sesion: SesionTaller?): SesionDtcUi =
    sesion?.let { SesionDtcUi.Abierta(it.titulo, it.pasosMarcados) } ?: SesionDtcUi.Ninguna

// Un mismo código suele venir a la vez como activo (03) y pendiente (07): una sola tarjeta con sus modos.
fun agruparPorCodigo(
    codigos: List<DtcCode>,
    guiaDe: (String) -> GuiaDtc?,
    resolver: StringResolver,
): List<DtcCodeUi> =
    codigos.groupBy { it.code }.map { (codigo, lista) ->
        val guia = guiaDe(codigo)
        DtcCodeUi(
            codigo = codigo,
            modos = lista.map { it.mode }.distinct(),
            guia = guia,
            sinGuia = if (guia == null) textoSinGuia(codigo, resolver) else null,
        )
    }

// Con un solo código la guía se abre sola: es lo primero que se va a consultar.
fun guiasAbiertasAlLeer(codigos: List<DtcCodeUi>): Set<String> =
    codigos.singleOrNull()?.takeIf { it.guia != null }?.let { setOf(it.codigo) } ?: emptySet()

fun textoSinGuia(codigo: String, resolver: StringResolver): String {
    val estructura = DecodificadorDtc.decodificar(codigo)
        ?: return resolver.get(R.string.dtc_unrecognized_code_format)
    val mensaje = DecodificadorDtc.mensajeSinGuia(estructura, resolver)
    return if (estructura.ambito == AmbitoDtc.FABRICANTE) mensaje
    else resolver.get(R.string.dtc_unlisted_code_explanation, estructura.explicacion(resolver), mensaje)
}

fun milUi(scan: DtcScan, resolver: StringResolver): MilUi? {
    val encendida = scan.milEncendida ?: return null
    val luz = resolver.get(if (encendida) R.string.dtc_mil_on else R.string.dtc_mil_off)
    val conteo = scan.conteoSegunEcu?.let {
        resolver.get(R.string.dtc_ecu_reports_count, textoConteo(it, resolver))
    }.orEmpty()
    return MilUi(encendida, luz + conteo)
}

private fun textoConteo(n: Int, resolver: StringResolver): String =
    resolver.plural(R.plurals.dtc_confirmed_code_count, n, n)

fun avisoLectura(scan: DtcScan, resolver: StringResolver): String? = when {
    !scan.activosConfirmados -> resolver.get(R.string.dtc_active_read_failed)
    scan.serviciosFallidos.isNotEmpty() -> {
        val servicios = scan.serviciosFallidos.sortedBy { it.ordinal }.map { servicio ->
            resolver.get(R.string.dtc_service_with_command, etiquetaServicio(servicio, resolver), servicio.comando)
        }
        resolver.get(R.string.dtc_services_read_failed, enumerar(servicios, resolver.get(R.string.dtc_and)))
    }
    scan.enlacePerdido -> resolver.get(R.string.dtc_link_lost_during_read)
    else -> null
}

fun textoSinCodigos(scan: DtcScan, resolver: StringResolver): String? {
    if (scan.todos.isNotEmpty() || !scan.activosConfirmados) return null
    val leidos = DtcServicio.entries.filterNot { it in scan.serviciosFallidos }
    val servicios = leidos.map { etiquetaServicio(it, resolver) }
    return resolver.get(R.string.dtc_no_codes_for_services, enumerar(servicios, resolver.get(R.string.dtc_nor)))
}

private fun etiquetaServicio(servicio: DtcServicio, resolver: StringResolver): String = resolver.get(
    when (servicio) {
        DtcServicio.ACTIVOS -> R.string.dtc_active_codes
        DtcServicio.PENDIENTES -> R.string.dtc_pending_codes
        DtcServicio.PERMANENTES -> R.string.dtc_permanent_codes
    },
)

private fun enumerar(partes: List<String>, conector: String): String =
    if (partes.size < 2) partes.joinToString()
    else partes.dropLast(1).joinToString(", ") + " $conector " + partes.last()

// Solo el movimiento comprobado deshabilita el botón: sin velocidad reciente, el diálogo pide declarar que está detenido.
fun motivoBorradoDeshabilitado(
    velocidad: ObdReading?,
    ahoraMs: Long,
    resolver: StringResolver,
    units: UnitFormatter,
): String? {
    val rechazo = ReglasBorradoDtc.evaluar(confirmado = true, velocidad = velocidad, ahoraMs = ahoraMs)
    return (rechazo as? RechazoBorradoDtc.EnMovimiento)?.let { textoRechazoUi(it, resolver, units) }
}

fun solicitudDesdeLectura(scan: DtcScan, resolver: StringResolver): SolicitudSesion {
    val codigos = scan.todos.map { it.code }.distinct()
    val titulo = if (codigos.isEmpty()) resolver.get(R.string.dtc_code_reading_title)
    else resolver.get(R.string.dtc_codes_title, codigos.joinToString())
    val sintomas = if (scan.milEncendida == true) setOf(Sintoma.MIL_ENCENDIDA) else emptySet()
    return SolicitudSesion(titulo = titulo, sintomas = sintomas)
}

fun etiquetaModo(mode: DtcMode, resolver: StringResolver): String = resolver.get(
    when (mode) {
        DtcMode.Active -> R.string.dtc_mode_active
        DtcMode.Pending -> R.string.dtc_mode_pending
        DtcMode.Permanent -> R.string.dtc_mode_permanent
    },
)
