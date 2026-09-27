package com.revscope.feature.dtc

import com.revscope.core.obd.diagnostics.DtcScan
import com.revscope.core.obd.diagnostics.RechazoBorradoDtc
import com.revscope.core.obd.diagnostics.ReglasBorradoDtc
import com.revscope.core.obd.model.DtcCode
import com.revscope.core.obd.model.DtcMode
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.taller.dtc.AmbitoDtc
import com.revscope.core.obd.taller.dtc.DecodificadorDtc
import com.revscope.core.obd.taller.dtc.GuiaDtc
import com.revscope.core.obd.taller.sesion.SesionTaller
import com.revscope.core.obd.taller.sesion.Sintoma
import com.revscope.core.obd.taller.sesion.SolicitudSesion

const val SIN_CODIGOS_LECTURA_COMPLETA = "Sin códigos activos, pendientes ni permanentes"

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
    val sinCodigos: String? = SIN_CODIGOS_LECTURA_COMPLETA,
) {
    val pasosMarcados: Set<String>
        get() = (sesion as? SesionDtcUi.Abierta)?.pasosMarcados ?: pasosSinSesion
}

fun sesionDtcUi(sesion: SesionTaller?): SesionDtcUi =
    sesion?.let { SesionDtcUi.Abierta(it.titulo, it.pasosMarcados) } ?: SesionDtcUi.Ninguna

// Un mismo código suele venir a la vez como activo (03) y pendiente (07): una sola tarjeta con sus modos.
fun agruparPorCodigo(codigos: List<DtcCode>, guiaDe: (String) -> GuiaDtc?): List<DtcCodeUi> =
    codigos.groupBy { it.code }.map { (codigo, lista) ->
        val guia = guiaDe(codigo)
        DtcCodeUi(
            codigo = codigo,
            modos = lista.map { it.mode }.distinct(),
            guia = guia,
            sinGuia = if (guia == null) textoSinGuia(codigo) else null,
        )
    }

// Con un solo código la guía se abre sola: es lo primero que se va a consultar.
fun guiasAbiertasAlLeer(codigos: List<DtcCodeUi>): Set<String> =
    codigos.singleOrNull()?.takeIf { it.guia != null }?.let { setOf(it.codigo) } ?: emptySet()

fun textoSinGuia(codigo: String): String {
    val estructura = DecodificadorDtc.decodificar(codigo) ?: return "Código con un formato que RevScope no reconoce."
    val mensaje = DecodificadorDtc.mensajeSinGuia(estructura)
    return if (estructura.ambito == AmbitoDtc.FABRICANTE) mensaje else "${estructura.explicacion}. $mensaje"
}

fun milUi(scan: DtcScan): MilUi? {
    val encendida = scan.milEncendida ?: return null
    val luz = if (encendida) "Testigo de falla encendido" else "Testigo de falla apagado"
    val conteo = scan.conteoSegunEcu?.let { " · la ECU reporta ${textoConteo(it)}" }.orEmpty()
    return MilUi(encendida, luz + conteo)
}

private fun textoConteo(n: Int): String = if (n == 1) "1 código confirmado" else "$n códigos confirmados"

// Solo el movimiento comprobado deshabilita el botón: sin velocidad reciente, el diálogo pide declarar que está detenido.
fun motivoBorradoDeshabilitado(velocidad: ObdReading?, ahoraMs: Long): String? {
    val rechazo = ReglasBorradoDtc.evaluar(confirmado = true, velocidad = velocidad, ahoraMs = ahoraMs)
    return (rechazo as? RechazoBorradoDtc.EnMovimiento)?.let(::textoRechazoUi)
}

fun solicitudDesdeLectura(scan: DtcScan): SolicitudSesion {
    val codigos = scan.todos.map { it.code }.distinct()
    val titulo = if (codigos.isEmpty()) "Lectura de códigos" else "Códigos ${codigos.joinToString()}"
    val sintomas = if (scan.milEncendida == true) setOf(Sintoma.MIL_ENCENDIDA) else emptySet()
    return SolicitudSesion(titulo = titulo, sintomas = sintomas)
}

fun etiquetaModo(mode: DtcMode): String = when (mode) {
    DtcMode.Active -> "Activo"
    DtcMode.Pending -> "Pendiente"
    DtcMode.Permanent -> "Permanente"
}
