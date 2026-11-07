package com.revscope.core.obd.taller.pruebas

import com.revscope.core.common.i18n.LocaleNumbers
import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.R
import com.revscope.core.obd.taller.FormatoTaller
import com.revscope.core.obd.taller.pruebas.AnalizadorArranqueFrio.Pasos
import kotlin.math.abs

// Redacción del arranque en frío: la condición (frío o tibio) va en el título, las cifras con su banda y
// origen, y la nota de los motores refrigerados por aire siempre, porque la ECU no dice cómo se enfría.
object TextosArranqueFrio {

    val TITULOS_PASO: Map<String, Int> = mapOf(
        Pasos.CONTACTO to R.string.obd_arranque_titulo_paso_contacto,
        Pasos.ARRANQUE to R.string.obd_arranque_titulo_paso_arranque,
        Pasos.CALENTAMIENTO to R.string.obd_arranque_titulo_paso_calentamiento,
    )

    fun titulosPaso(resolver: StringResolver): Map<String, String> = TITULOS_PASO.mapValues { resolver.get(it.value) }

    fun notaAire(resolver: StringResolver): String = resolver.get(R.string.obd_arranque_nota_aire)

    fun resultado(a: AnalisisArranqueFrio, resolver: StringResolver): ResultadoPrueba = Redactor(a, resolver).resultado()

    fun titulo(a: AnalisisArranqueFrio, resolver: StringResolver): String = Redactor(a, resolver).titulo()

    fun medidas(a: AnalisisArranqueFrio, resolver: StringResolver): List<String> = Redactor(a, resolver).medidas()

    private class Redactor(private val a: AnalisisArranqueFrio, private val res: StringResolver) {

        fun resultado() = ResultadoPrueba(
            tipo = TipoPrueba.ARRANQUE_FRIO,
            veredicto = a.patron.veredicto,
            titulo = titulo(),
            interpretacion = interpretacion(),
            siguientePaso = siguientePaso(),
            hallazgos = hallazgos(),
            bajaConfianza = false,
            detalle = a,
        )

        fun titulo(): String {
            val cuerpo = cuerpoTitulo()
            return when (a.plausibilidad.condicion) {
                CondicionMotor.FRIO -> res.get(R.string.obd_arranque_titulo_frio, cuerpo)
                CondicionMotor.TIBIO -> res.get(R.string.obd_arranque_titulo_tibio, cuerpo)
                CondicionMotor.SIN_DATO -> cuerpo.replaceFirstChar(Char::uppercase)
            }
        }

        private fun cuerpoTitulo(): String = when (a.patron) {
            PatronArranque.SIN_DATOS -> res.get(R.string.obd_arranque_cuerpo_sin_datos)
            PatronArranque.NO_ARRANCO -> res.get(R.string.obd_arranque_cuerpo_no_arranco)
            PatronArranque.SE_APAGA -> res.get(R.string.obd_arranque_cuerpo_se_apaga, veces(a.apagones))
            PatronArranque.SALTOS_ECT -> res.get(R.string.obd_arranque_cuerpo_saltos_ect)
            PatronArranque.VARIOS_INTENTOS -> res.get(R.string.obd_arranque_cuerpo_varios_intentos, a.arranque.intentos, enTiempo())
            PatronArranque.IRREGULAR -> res.get(R.string.obd_arranque_cuerpo_irregular)
            PatronArranque.NORMAL -> res.get(R.string.obd_arranque_cuerpo_normal, enTiempo())
        }

        private fun enTiempo(): String =
            a.arranque.tiempoS?.let { " " + res.get(R.string.obd_arranque_en_tiempo, segundos(it)) }.orEmpty()

        // ── Interpretación ──────────────────────────────────────────────────

        private fun interpretacion(): String = listOfNotNull(
            fraseCondicion(a.plausibilidad),
            fraseArranque(),
            fraseCalentamiento(),
            frasePerfil(a.perfil),
            fraseSaltos(),
            notaAire(res),
        ).joinToString(" ")

        private fun fraseCondicion(p: PlausibilidadFrio): String {
            val ect = p.ectC ?: return res.get(R.string.obd_arranque_sin_lectura_motor)
            val iat = p.iatC ?: return res.get(R.string.obd_arranque_sin_lectura_aire)
            val medidas = res.get(
                R.string.obd_arranque_frase_condicion,
                grados(ect),
                grados(iat),
                grados(ect - iat),
                grados(p.limiteC),
                origenLimite(p),
            )
            return when (p.condicion) {
                CondicionMotor.TIBIO -> res.get(R.string.obd_arranque_frase_tibio, medidas)
                else -> medidas
            }
        }

        private fun origenLimite(p: PlausibilidadFrio): String =
            p.banda?.etiquetaOrigen(res) ?: res.get(R.string.obd_band_origin_typical)

        private fun fraseArranque(): String {
            val r = a.arranque
            if (!r.arranco) {
                if (r.intentos == 0) return res.get(R.string.obd_arranque_no_giro)
                return res.get(R.string.obd_arranque_no_quedo_encendido, intentos(r.intentos))
            }
            val intento = if (r.intentos == 1) {
                res.get(R.string.obd_arranque_al_primer_intento)
            } else {
                res.get(R.string.obd_arranque_al_intento, r.intentos)
            }
            val apagones = if (a.apagones > 0) " " + res.get(R.string.obd_arranque_despues_se_apago, veces(a.apagones)) else ""
            return res.get(R.string.obd_arranque_frase_arranque, intento, segundos(r.tiempoS ?: 0.0)) + apagones
        }

        private fun fraseCalentamiento(): String? {
            val calentado = grados(AnalizadorArranqueFrio.CALENTADO_C)
            a.tiempoHasta60S?.let { return res.get(R.string.obd_arranque_llego_a, calentado, duracion(it)) }
            val maximo = a.ectMaxC ?: return null
            return if (a.arranque.arranco) res.get(R.string.obd_arranque_llego_hasta, grados(maximo), calentado) else null
        }

        private fun frasePerfil(p: PerfilMinimoRapido?): String? {
            if (p == null) return null
            val baja = p.baja ?: return null
            val verbo = res.get(if (p.rpmFin <= p.rpmInicio) R.string.obd_arranque_verbo_bajo else R.string.obd_arranque_verbo_subio)
            val cierre = res.get(if (baja) R.string.obd_arranque_cierre_esperado else R.string.obd_arranque_cierre_deberia_bajar)
            return res.get(
                R.string.obd_arranque_frase_perfil,
                verbo,
                rpm(p.rpmInicio),
                rpm(p.rpmFin),
                grados(p.ectInicioC),
                grados(p.ectFinC),
                cierre,
            )
        }

        private fun fraseSaltos(): String? {
            val mayor = a.saltosEct.maxByOrNull { abs(it.delta) } ?: return null
            return res.get(R.string.obd_arranque_frase_saltos, grados(abs(mayor.delta)), segundos(mayor.duracionMs / 1_000.0))
        }

        // ── Hallazgos y siguiente paso ──────────────────────────────────────

        private fun hallazgos(): List<String> = buildList {
            hallazgosSensores(a.plausibilidad).forEach(::add)
            if (a.arranque.arranco && a.arranque.intentos > 1) add(res.get(R.string.obd_arranque_hallazgo_intento, a.arranque.intentos))
            if (a.apagones > 0) add(res.get(R.string.obd_arranque_hallazgo_se_apago, veces(a.apagones)))
            a.saltosEct.forEach {
                add(res.get(R.string.obd_arranque_hallazgo_salto, grados(abs(it.delta)), segundos(it.duracionMs / 1_000.0)))
            }
            if (a.ectBajaMientrasCalienta) add(res.get(R.string.obd_arranque_hallazgo_ect_bajo, grados(a.caidaEctMaxC)))
            if (a.perfil?.baja == false) {
                add(res.get(R.string.obd_arranque_hallazgo_minimo_subio, rpm(a.perfil.rpmInicio), rpm(a.perfil.rpmFin)))
            }
        }

        private fun hallazgosSensores(p: PlausibilidadFrio): List<String> = listOfNotNull(
            p.takeIf { it.motorBajoAire }?.let {
                res.get(R.string.obd_arranque_hallazgo_motor_bajo_aire, grados(-(it.diferenciaC ?: 0.0)))
            },
            p.takeIf { it.aireLejosDelAmbiente }?.let {
                res.get(R.string.obd_arranque_hallazgo_aire_ambiente, grados(it.iatC ?: 0.0), grados(it.ambienteC ?: 0.0))
            },
        )

        private fun siguientePaso(): String? = when (a.patron) {
            PatronArranque.SIN_DATOS -> res.get(R.string.obd_arranque_siguiente_sin_datos)
            PatronArranque.NO_ARRANCO -> res.get(R.string.obd_arranque_siguiente_no_arranco)
            PatronArranque.SE_APAGA -> res.get(R.string.obd_arranque_siguiente_se_apaga)
            PatronArranque.SALTOS_ECT -> res.get(R.string.obd_arranque_siguiente_saltos_ect)
            PatronArranque.VARIOS_INTENTOS -> res.get(R.string.obd_arranque_siguiente_varios_intentos)
            PatronArranque.IRREGULAR -> siguienteIrregular()
            PatronArranque.NORMAL ->
                res.get(R.string.obd_arranque_siguiente_repetir_frio).takeIf { a.plausibilidad.condicion == CondicionMotor.TIBIO }
        }

        private fun siguienteIrregular(): String = when {
            !a.plausibilidad.coherente -> res.get(R.string.obd_arranque_siguiente_sensores)
            a.ectBajaMientrasCalienta -> res.get(R.string.obd_arranque_siguiente_ect)
            else -> res.get(R.string.obd_arranque_siguiente_minimo)
        }

        // ── Medidas: una línea por cifra, para la pantalla y el informe ──────

        fun medidas(): List<String> = listOfNotNull(
            medidaContacto(a.plausibilidad),
            medidaArranque(a.arranque),
            res.get(R.string.obd_arranque_medida_apagones, a.apagones),
            a.perfil?.let { p ->
                res.get(R.string.obd_arranque_medida_perfil, rpm(p.rpmInicio), grados(p.ectInicioC), rpm(p.rpmFin), grados(p.ectFinC))
            },
            medidaCalentamiento(),
            res.get(R.string.obd_arranque_medida_saltos, saltos()),
        )

        private fun saltos(): String =
            if (a.saltosEct.isEmpty()) res.get(R.string.obd_arranque_ninguno) else a.saltosEct.size.toString()

        private fun medidaContacto(p: PlausibilidadFrio): String {
            val partes = listOfNotNull(
                p.ectC?.let { res.get(R.string.obd_arranque_parte_motor, grados(it)) },
                p.iatC?.let { res.get(R.string.obd_arranque_parte_aire, grados(it)) },
                p.ambienteC?.let { res.get(R.string.obd_arranque_parte_ambiente, grados(it)) },
            )
            val texto = partes.ifEmpty { listOf(res.get(R.string.obd_arranque_sin_temperaturas)) }.joinToString(", ")
            return res.get(R.string.obd_arranque_medida_contacto, texto, condicion(p.condicion))
        }

        private fun condicion(c: CondicionMotor): String = res.get(
            when (c) {
                CondicionMotor.FRIO -> R.string.obd_arranque_condicion_frio
                CondicionMotor.TIBIO -> R.string.obd_arranque_condicion_tibio
                CondicionMotor.SIN_DATO -> R.string.obd_arranque_condicion_sin_dato
            },
        )

        private fun medidaArranque(r: ArranqueMedido): String = if (r.arranco) {
            res.get(R.string.obd_arranque_medida_arranque, intentos(r.intentos), segundos(r.tiempoS ?: 0.0))
        } else {
            res.get(R.string.obd_arranque_medida_no_prendio, intentos(r.intentos))
        }

        private fun medidaCalentamiento(): String? {
            val calentado = grados(AnalizadorArranqueFrio.CALENTADO_C)
            a.tiempoHasta60S?.let { return res.get(R.string.obd_arranque_medida_hasta, calentado, duracion(it)) }
            return a.ectMaxC?.let { res.get(R.string.obd_arranque_medida_no_llego, calentado, grados(it)) }
        }

        // ── Formato ─────────────────────────────────────────────────────────

        private fun duracion(s: Double): String =
            if (s < 120) segundos(s) else LocaleNumbers.decimal(s / 60, 1, res.locale) + ESPACIO_DURO + "min"

        private fun veces(n: Int) = res.plural(R.plurals.obd_arranque_veces, n, n)

        private fun intentos(n: Int) = res.plural(R.plurals.obd_arranque_intentos, n, n)

        private fun grados(x: Double) = LocaleNumbers.decimal(x, 0, res.locale) + ESPACIO_DURO + "°C"

        private fun rpm(x: Double) = LocaleNumbers.decimal(x, 0, res.locale)

        private fun segundos(x: Double) = LocaleNumbers.compact(FormatoTaller.redondear(x, 1), res.locale) + ESPACIO_DURO + "s"
    }

    private const val ESPACIO_DURO = " "
}
