package com.revscope.core.obd.taller.pruebas

import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.obd.session.ObdSessionManager
import com.revscope.core.obd.taller.referencia.BandaReferencia
import com.revscope.core.obd.taller.referencia.ClavesBanda
import com.revscope.core.obd.taller.referencia.PosicionEnBanda

// Batería y carga con la ráfaga de AT RV (§2.4.5 del diseño del Taller): reposo con el contacto, valle del
// arranque, carga en mínimo y a rpm altas. Si el adaptador se apaga al arrancar, es un hallazgo, no un error.
object AnalizadorBateria {

    const val PID_VOLTAJE = ObdSessionManager.VBAT_PID
    const val CAIDA_ARRANQUE_VISTA_V = 0.5
    const val CARGA_SOBRE_REPOSO_V = 0.3
    const val SIN_RESPUESTA_MS = 1_000L
    const val TASA_CONFIABLE_HZ = 5.0
    const val REINICIO_TIPICO_V = 8.0
    const val CLAVE_CARGA_EN_MINIMO = "BATERIA_CARGA_EN_MINIMO"
    const val CLAVE_ADAPTADOR_AGUANTO = "BATERIA_ADAPTADOR_AGUANTO"

    object Pasos {
        const val CONTACTO = "CONTACTO"
        const val ARRANQUE = "ARRANQUE"
        const val MINIMO = "MINIMO"
        const val RPM_ALTAS = "RPM_ALTAS"
    }

    fun analizar(datos: DatosPrueba, bandas: Map<String, BandaReferencia>): ResultadoPrueba {
        val contacto = tramo(serie(datos, Pasos.CONTACTO))
        val base = AnalisisBateria(
            desfase = datos.contexto.desfaseVoltaje,
            conFarola = datos.contexto.tipoVehiculo == VehicleType.MOTORCYCLE,
            contacto = contacto,
            arranque = arranque(datos, contacto),
            minimo = tramo(serie(datos, Pasos.MINIMO)),
            rpmAltas = tramo(serie(datos, Pasos.RPM_ALTAS)),
            comprobaciones = emptyList(),
            patron = PatronBateria.NORMAL,
        )
        val comprobado = base.copy(comprobaciones = comprobaciones(base, bandas))
        return TextosBateria.resultado(comprobado.copy(patron = patron(comprobado)))
    }

    // Motos: la farola suele quedar encendida con el contacto (AHO), así que el reposo se mide con esa carga.
    fun claveContacto(conFarola: Boolean): String =
        if (conFarola) ClavesBanda.BATERIA_CONTACTO_CON_LUCES_V else ClavesBanda.BATERIA_CONTACTO_V

    // ── Tramos ──────────────────────────────────────────────────────────────

    private fun serie(datos: DatosPrueba, clave: String): List<Punto> =
        datos.serie(PID_VOLTAJE, clave).map { it.copy(valor = datos.contexto.desfaseVoltaje.corregir(it.valor)) }

    private fun tramo(serie: List<Punto>): TramoVoltaje? {
        val valores = serie.map { it.valor }.takeIf { it.isNotEmpty() } ?: return null
        return TramoVoltaje(valores.average(), valores.min(), valores.max(), valores.size)
    }

    private fun arranque(datos: DatosPrueba, contacto: TramoVoltaje?): ArranqueVoltaje? {
        val segmento = datos.segmento(Pasos.ARRANQUE) ?: return null
        val serie = serie(datos, Pasos.ARRANQUE)
        val valle = serie.minOfOrNull { it.valor }
        return ArranqueVoltaje(
            valleV = valle,
            caidaV = if (valle != null && contacto != null) contacto.mediaV - valle else null,
            tasaHz = if (segmento.utilMs > 0) serie.size * 1_000.0 / segmento.utilMs else 0.0,
            sinRespuestaMs = huecoMayorMs(serie.map { it.tMs }, segmento),
            enlacePerdido = datos.contexto.enlacePerdidoEn == Pasos.ARRANQUE,
        )
    }

    // Del inicio del paso a la primera lectura, entre lecturas y de la última al final del paso.
    private fun huecoMayorMs(tiempos: List<Long>, segmento: SegmentoPaso): Long {
        val bordes = listOf(segmento.utilDesdeMs) + tiempos + segmento.finMs
        return bordes.zipWithNext { a, b -> b - a }.maxOrNull() ?: 0L
    }

    // ── Comprobaciones y patrón ─────────────────────────────────────────────

    private fun comprobaciones(a: AnalisisBateria, bandas: Map<String, BandaReferencia>): List<Comprobacion> = listOfNotNull(
        a.contacto?.let { Comprobacion.contra(claveContacto(a.conFarola), etiquetaContacto(a.conFarola), it.mediaV, "V", bandas) },
        a.arranque?.valleV?.let { Comprobacion.contra(ClavesBanda.ARRANQUE_MIN_V, "Valle al arrancar", it, "V", bandas) },
        a.arranque?.let { Comprobacion.siNo(CLAVE_ADAPTADOR_AGUANTO, "El adaptador siguió respondiendo al arrancar", !it.adaptadorReiniciado) },
        cargaEnMinimo(a)?.let { Comprobacion.siNo(CLAVE_CARGA_EN_MINIMO, "Carga en mínimo (sube sobre el reposo)", it) },
        a.rpmAltas?.let { Comprobacion.contra(ClavesBanda.CARGA_V, "Carga a rpm altas", it.mediaV, "V", bandas) },
        a.cargaMaxV?.let { Comprobacion.contra(ClavesBanda.SOBRECARGA_MAX_V, "Máximo con el motor en marcha", it, "V", bandas) },
    )

    fun cargaEnMinimo(a: AnalisisBateria): Boolean? {
        val minimo = a.minimo ?: return null
        val reposo = a.contacto ?: return null
        return minimo.mediaV >= reposo.mediaV + CARGA_SOBRE_REPOSO_V
    }

    private fun etiquetaContacto(conFarola: Boolean) =
        if (conFarola) "Batería en contacto (con la farola encendida)" else "Batería en contacto (sin cargas)"

    private fun patron(a: AnalisisBateria): PatronBateria = when {
        a.contacto == null && a.arranque?.valleV == null && a.cargaMaxV == null -> PatronBateria.SIN_DATOS
        fuera(a, ClavesBanda.SOBRECARGA_MAX_V) == PosicionEnBanda.ALTO -> PatronBateria.SOBRECARGA
        fuera(a, ClavesBanda.ARRANQUE_MIN_V) == PosicionEnBanda.BAJO -> PatronBateria.ARRANQUE_BAJO
        a.arranque?.adaptadorReiniciado == true -> PatronBateria.ADAPTADOR_REINICIADO
        fuera(a, ClavesBanda.CARGA_V) == PosicionEnBanda.BAJO -> PatronBateria.NO_CARGA
        fuera(a, ClavesBanda.CARGA_V) == PosicionEnBanda.ALTO -> PatronBateria.CARGA_ALTA
        cargaEnMinimo(a) == false -> PatronBateria.SIN_CARGA_EN_MINIMO
        fuera(a, claveContacto(a.conFarola)) == PosicionEnBanda.BAJO -> PatronBateria.BATERIA_BAJA
        else -> PatronBateria.NORMAL
    }

    private fun fuera(a: AnalisisBateria, clave: String): PosicionEnBanda? =
        a.comprobacion(clave)?.posicion?.takeIf { it != PosicionEnBanda.DENTRO }
}
