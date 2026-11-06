package com.revscope.core.obd.taller.pruebas

import com.revscope.core.common.i18n.LocaleNumbers
import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.R
import com.revscope.core.obd.taller.pruebas.AnalizadorBateria.Pasos
import com.revscope.core.obd.taller.referencia.ClavesBanda

// Redacción de batería y carga: cada voltaje con su banda y origen, el desfase de AT RV siempre dicho y
// «compatible con…», nunca un dictamen. Las unidades van con espacio duro.
object TextosBateria {

    val TITULOS_PASO: Map<String, Int> = mapOf(
        Pasos.CONTACTO to R.string.obd_bateria_titulo_paso_contacto,
        Pasos.ARRANQUE to R.string.obd_bateria_titulo_paso_arranque,
        Pasos.MINIMO to R.string.obd_bateria_titulo_paso_minimo,
        Pasos.RPM_ALTAS to R.string.obd_bateria_titulo_paso_rpm_altas,
    )

    fun titulosPaso(resolver: StringResolver): Map<String, String> = TITULOS_PASO.mapValues { resolver.get(it.value) }

    fun resultado(a: AnalisisBateria, resolver: StringResolver): ResultadoPrueba = Redactor(a, resolver).resultado()

    fun titulo(a: AnalisisBateria, resolver: StringResolver): String = Redactor(a, resolver).titulo()

    fun medidas(a: AnalisisBateria, resolver: StringResolver): List<String> = Redactor(a, resolver).medidas()

    private class Redactor(private val a: AnalisisBateria, private val res: StringResolver) {

        fun resultado() = ResultadoPrueba(
            tipo = TipoPrueba.BATERIA_CARGA,
            veredicto = a.patron.veredicto,
            titulo = titulo(),
            interpretacion = interpretacion(),
            siguientePaso = siguientePaso(),
            hallazgos = hallazgos(),
            bajaConfianza = a.bajaConfianza,
            detalle = a,
        )

        fun titulo(): String = when (a.patron) {
            PatronBateria.SIN_DATOS -> res.get(R.string.obd_bateria_titulo_sin_datos)
            PatronBateria.SOBRECARGA -> res.get(R.string.obd_bateria_titulo_sobrecarga, v(a.cargaMaxV))
            PatronBateria.ARRANQUE_BAJO -> res.get(R.string.obd_bateria_titulo_arranque_bajo, v(a.arranque?.valleV))
            PatronBateria.ADAPTADOR_REINICIADO -> res.get(R.string.obd_bateria_titulo_adaptador_reiniciado)
            PatronBateria.NO_CARGA -> res.get(R.string.obd_bateria_titulo_no_carga, v(a.rpmAltas?.mediaV))
            PatronBateria.CARGA_ALTA -> res.get(R.string.obd_bateria_titulo_carga_alta, v(a.rpmAltas?.mediaV))
            PatronBateria.SIN_CARGA_EN_MINIMO -> res.get(R.string.obd_bateria_titulo_sin_carga_en_minimo)
            PatronBateria.BATERIA_BAJA -> res.get(R.string.obd_bateria_titulo_bateria_baja)
            PatronBateria.NORMAL -> res.get(R.string.obd_bateria_titulo_normal)
        }

        // ── Interpretación ──────────────────────────────────────────────────

        private fun interpretacion(): String {
            if (a.patron == PatronBateria.SIN_DATOS) return res.get(R.string.obd_bateria_interp_sin_datos)
            return listOfNotNull(
                fraseContacto(),
                fraseArranque(),
                fraseCarga(),
                fraseVeredicto(),
                fraseDesfase(a.desfase),
            ).joinToString(" ")
        }

        private fun fraseContacto(): String? {
            val contacto = a.contacto ?: return null
            val banda = banda(AnalizadorBateria.claveContacto(a.conFarola))
            val condicion = res.get(if (a.conFarola) R.string.obd_bateria_condicion_farola else R.string.obd_bateria_condicion_sin_cargas)
            val farola = if (a.conFarola) " " + res.get(R.string.obd_bateria_aviso_farola) else ""
            return res.get(R.string.obd_bateria_frase_contacto, v(contacto.mediaV), condicion, banda) + farola
        }

        private fun fraseArranque(): String? {
            val r = a.arranque ?: return null
            if (r.adaptadorReiniciado) return fraseReinicio(r)
            if (!r.visto) return res.get(R.string.obd_bateria_arranque_no_visto)
            return res.get(R.string.obd_bateria_frase_arranque, v(r.valleV), v(r.caidaV), banda(ClavesBanda.ARRANQUE_MIN_V))
        }

        private fun fraseReinicio(r: ArranqueVoltaje): String {
            val ultimo = r.valleV?.let { " " + res.get(R.string.obd_bateria_reinicio_ultimo, v(it)) }.orEmpty()
            val cuanto = if (r.enlacePerdido) {
                res.get(R.string.obd_bateria_reinicio_enlace_perdido)
            } else {
                res.get(R.string.obd_bateria_reinicio_sin_respuesta, n(r.sinRespuestaMs / 1_000.0, 1))
            }
            return res.get(R.string.obd_bateria_frase_reinicio, cuanto, v(AnalizadorBateria.REINICIO_TIPICO_V, 0)) + ultimo
        }

        private fun fraseCarga(): String? {
            val altas = a.rpmAltas
            val minimo = a.minimo
            if (altas == null && minimo == null) return null
            val partes = listOfNotNull(
                minimo?.let { res.get(R.string.obd_bateria_carga_minimo, v(it.mediaV)) },
                altas?.let { res.get(R.string.obd_bateria_carga_altas, v(it.mediaV)) },
            )
            val texto = partes.singleOrNull() ?: res.get(R.string.obd_bateria_partes_y, partes[0], partes[1])
            return res.get(R.string.obd_bateria_frase_carga, texto, banda(ClavesBanda.CARGA_V))
        }

        private fun fraseVeredicto(): String? = when (a.patron) {
            PatronBateria.SOBRECARGA -> res.get(R.string.obd_bateria_veredicto_sobrecarga, banda(ClavesBanda.SOBRECARGA_MAX_V))
            PatronBateria.ARRANQUE_BAJO -> res.get(R.string.obd_bateria_veredicto_arranque_bajo)
            PatronBateria.NO_CARGA -> res.get(R.string.obd_bateria_veredicto_no_carga)
            PatronBateria.CARGA_ALTA -> res.get(R.string.obd_bateria_veredicto_carga_alta)
            PatronBateria.SIN_CARGA_EN_MINIMO -> res.get(R.string.obd_bateria_veredicto_sin_carga_en_minimo)
            PatronBateria.BATERIA_BAJA -> res.get(R.string.obd_bateria_veredicto_bateria_baja)
            else -> null
        }

        private fun fraseDesfase(d: DesfaseVoltaje): String = if (d.calibrado) {
            res.get(R.string.obd_bateria_desfase_corregido, conSigno(d.voltios), d.origenTexto(res))
        } else {
            res.get(R.string.obd_bateria_nota_at_rv)
        }

        // ── Hallazgos y siguiente paso ──────────────────────────────────────

        private fun hallazgos(): List<String> = buildList {
            val r = a.arranque
            if (r?.adaptadorReiniciado == true) add(res.get(R.string.obd_bateria_hallazgo_reinicio))
            if (r != null && !r.adaptadorReiniciado && !r.visto && r.valleV != null) {
                add(res.get(R.string.obd_bateria_hallazgo_no_visto))
            }
            if (a.bajaConfianza) add(res.get(R.string.obd_bateria_hallazgo_baja_confianza, n(r?.tasaHz, 0)))
            if (a.patron == PatronBateria.SOBRECARGA) add(res.get(R.string.obd_bateria_titulo_sobrecarga, v(a.cargaMaxV)))
            if (a.patron == PatronBateria.ARRANQUE_BAJO) add(res.get(R.string.obd_bateria_hallazgo_arranque_bajo, v(r?.valleV)))
            if (a.patron == PatronBateria.NO_CARGA) add(res.get(R.string.obd_bateria_hallazgo_no_carga, v(a.rpmAltas?.mediaV)))
        }

        private fun siguientePaso(): String? = when (a.patron) {
            PatronBateria.SIN_DATOS -> res.get(R.string.obd_bateria_siguiente_sin_datos)
            PatronBateria.ARRANQUE_BAJO, PatronBateria.ADAPTADOR_REINICIADO -> res.get(R.string.obd_bateria_siguiente_arranque)
            PatronBateria.SOBRECARGA, PatronBateria.NO_CARGA, PatronBateria.CARGA_ALTA, PatronBateria.SIN_CARGA_EN_MINIMO ->
                res.get(R.string.obd_bateria_siguiente_carga)
            PatronBateria.BATERIA_BAJA -> res.get(R.string.obd_bateria_siguiente_bateria_baja)
            PatronBateria.NORMAL -> null
        }

        // ── Medidas: una línea por cifra, para la pantalla y el informe ──────

        fun medidas(): List<String> = listOfNotNull(
            a.contacto?.let { res.get(R.string.obd_bateria_medida_contacto, tramo(it)) },
            a.arranque?.let(::medidaArranque),
            a.minimo?.let { res.get(R.string.obd_bateria_medida_minimo, tramo(it)) },
            a.rpmAltas?.let { res.get(R.string.obd_bateria_medida_rpm_altas, tramo(it)) },
            res.get(R.string.obd_bateria_medida_desfase, desfase()),
        )

        private fun desfase(): String = if (a.desfase.calibrado) {
            "${conSigno(a.desfase.voltios)} (${a.desfase.origenTexto(res)})"
        } else {
            res.get(R.string.obd_bateria_desfase_sin_calibrar)
        }

        private fun medidaArranque(r: ArranqueVoltaje): String = when {
            r.adaptadorReiniciado -> res.get(R.string.obd_bateria_medida_arranque_reinicio) +
                (r.valleV?.let { " " + res.get(R.string.obd_bateria_medida_arranque_ultimo, v(it)) } ?: "")
            !r.visto -> res.get(R.string.obd_bateria_medida_arranque_no_visto, n(r.tasaHz, 0))
            else -> res.get(R.string.obd_bateria_medida_arranque, v(r.valleV), v(r.caidaV), n(r.tasaHz, 0))
        }

        // ── Formato ─────────────────────────────────────────────────────────

        private fun tramo(t: TramoVoltaje): String {
            val desde = n(t.minV, 1)
            val hasta = n(t.maxV, 1)
            return if (desde == hasta) v(t.mediaV) else res.get(R.string.obd_bateria_tramo, v(t.mediaV), desde, v(t.maxV))
        }

        private fun banda(clave: String): String {
            val c = a.comprobacion(clave)?.banda ?: return res.get(R.string.obd_bateria_banda_tipica)
            return TextoBanda.citar(c, 1, res)
        }

        private fun v(x: Double?, decimales: Int = 1) = n(x, decimales) + ESPACIO_DURO + "V"

        private fun conSigno(x: Double) = LocaleNumbers.signed(x, 2, res.locale) + ESPACIO_DURO + "V"

        private fun n(x: Double?, decimales: Int) = LocaleNumbers.decimal(x ?: 0.0, decimales, res.locale)
    }

    private const val ESPACIO_DURO = " "
}
