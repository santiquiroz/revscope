package com.revscope.core.obd.taller.informe

import com.revscope.core.common.i18n.LocaleNumbers
import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.R
import com.revscope.core.obd.taller.FormatoTaller
import kotlin.math.max
import kotlin.math.min

data class PuntoGrafica(val x: Double, val y: Double)

data class SerieGrafica(val nombre: String, val puntos: List<PuntoGrafica>)

data class TramoGrafica(val inicioX: Double, val finX: Double, val etiqueta: String)

data class PesaGrafica(
    val etiqueta: String,
    val medido: Double,
    val minimo: Double?,
    val maximo: Double?,
    val unidad: String,
    val origen: String,
)

object GraficaSvg {

    const val MAX_PUNTOS_POR_SERIE = 400

    fun reducir(puntos: List<PuntoGrafica>, maximo: Int = MAX_PUNTOS_POR_SERIE): List<PuntoGrafica> {
        require(maximo >= 2) { "Se necesitan al menos 2 puntos" } // i18n-ignore
        if (puntos.size <= maximo) return puntos
        val cubetas = maximo / 2
        return (0 until cubetas).flatMap { indice ->
            val desde = indice * puntos.size / cubetas
            val hasta = (indice + 1) * puntos.size / cubetas
            extremosEnOrden(puntos.subList(desde, hasta))
        }
    }

    fun lineas(
        series: List<SerieGrafica>,
        tramos: List<TramoGrafica> = emptyList(),
        ancho: Int = 900,
        alto: Int = 320,
        resolver: StringResolver,
    ): String {
        val reducidas = series.map { it.copy(puntos = reducir(it.puntos.sortedBy(PuntoGrafica::x))) }
        val puntos = reducidas.flatMap(SerieGrafica::puntos)
        if (puntos.isEmpty()) return vacia(ancho, alto, resolver.get(R.string.obd_chart_no_data))
        val limites = Limites(
            minX = puntos.minOf(PuntoGrafica::x),
            maxX = puntos.maxOf(PuntoGrafica::x),
            minY = puntos.minOf(PuntoGrafica::y),
            maxY = puntos.maxOf(PuntoGrafica::y),
        ).conMargen()
        return buildString {
            append(cabecera(ancho, alto, resolver.get(R.string.obd_chart_readings_description)))
            append(ejes(ancho, alto, limites, resolver))
            tramos.forEachIndexed { indice, tramo -> append(tramo(tramo, indice, limites, ancho, alto)) }
            reducidas.forEachIndexed { indice, serie ->
                val puntosSvg = serie.puntos.joinToString(" ") { punto ->
                    "${x(punto.x, limites, ancho)},${y(punto.y, limites, alto)}"
                }
                append("<polyline class=\"serie s${indice % 3} patron${indice % 3}\" points=\"") // i18n-ignore
                    .append(puntosSvg).append("\"/>") // i18n-ignore
            }
            reducidas.forEachIndexed { indice, serie ->
                val posicion = 70 + indice * 180
                append("<line class=\"serie s${indice % 3} patron${indice % 3}\" x1=\"").append(posicion) // i18n-ignore
                    .append("\" y1=\"20\" x2=\"").append(posicion + 24).append("\" y2=\"20\"/>") // i18n-ignore
                append("<text class=\"leyenda l${indice % 3}\" x=\"").append(posicion + 30) // i18n-ignore
                    .append("\" y=\"24\">").append(escapar(serie.nombre)).append("</text>") // i18n-ignore
            }
            append("</svg>")
        }
    }

    fun pesas(pesas: List<PesaGrafica>, ancho: Int = 900, resolver: StringResolver): String {
        if (pesas.isEmpty()) return vacia(ancho, 120, resolver.get(R.string.obd_chart_no_multimeter_data))
        val alto = 48 + pesas.size * 72
        return buildString {
            append(cabecera(ancho, alto, resolver.get(R.string.obd_chart_reference_band_description)))
            pesas.forEachIndexed { indice, pesa -> append(pesa(pesa, indice, ancho, resolver)) }
            append("</svg>")
        }
    }

    private fun extremosEnOrden(puntos: List<PuntoGrafica>): List<PuntoGrafica> {
        val minimo = puntos.indices.minBy { puntos[it].y }
        val maximo = puntos.indices.maxBy { puntos[it].y }
        return listOf(minimo, maximo).distinct().sorted().map(puntos::get)
    }

    private fun pesa(pesa: PesaGrafica, indice: Int, ancho: Int, resolver: StringResolver): String {
        val y = 52 + indice * 72
        val minReferencia = pesa.minimo ?: pesa.medido
        val maxReferencia = pesa.maximo ?: pesa.medido
        val minEscala = min(0.0, min(minReferencia, pesa.medido))
        val maxEscala = max(maxReferencia, pesa.medido).takeIf { it > minEscala } ?: minEscala + 1.0
        val inicio = 210.0
        val fin = ancho - 36.0
        val posicion = { valor: Double -> inicio + (valor - minEscala) / (maxEscala - minEscala) * (fin - inicio) }
        val bandaInicio = posicion(minReferencia)
        val bandaFin = posicion(maxReferencia)
        val medido = posicion(pesa.medido)
        return buildString {
            append("<text x=\"16\" y=\"").append(y).append("\">").append(escapar(pesa.etiqueta)).append("</text>") // i18n-ignore
            append("<line class=\"escala\" x1=\"").append(inicio).append("\" y1=\"").append(y - 5) // i18n-ignore
                .append("\" x2=\"").append(fin).append("\" y2=\"").append(y - 5).append("\"/>") // i18n-ignore
            append("<line class=\"banda\" x1=\"").append(bandaInicio).append("\" y1=\"").append(y - 5) // i18n-ignore
                .append("\" x2=\"").append(bandaFin).append("\" y2=\"").append(y - 5).append("\"/>") // i18n-ignore
            append("<circle class=\"medido\" cx=\"").append(medido).append("\" cy=\"").append(y - 5).append("\" r=\"7\"/>") // i18n-ignore
            append("<text class=\"dato\" x=\"").append(inicio).append("\" y=\"").append(y + 22).append("\">") // i18n-ignore
                .append(escapar(textoPesa(pesa, resolver))).append("</text>") // i18n-ignore
        }
    }

    private fun tramo(tramo: TramoGrafica, indice: Int, limites: Limites, ancho: Int, alto: Int): String {
        val inicio = x(tramo.inicioX, limites, ancho)
        val fin = x(tramo.finX, limites, ancho)
        val anchoTramo = (fin.toDouble() - inicio.toDouble()).coerceAtLeast(0.0)
        return "<rect class=\"tramo tramo${indice % 2}\" x=\"$inicio\" y=\"36\" width=\"$anchoTramo\" height=\"${alto - 70}\"/>" + // i18n-ignore
            "<text class=\"etiqueta-tramo\" x=\"${inicio.toDouble() + 5}\" y=\"54\">${escapar(tramo.etiqueta)}</text>" // i18n-ignore
    }

    private fun textoPesa(pesa: PesaGrafica, resolver: StringResolver): String {
        fun numero(valor: Double) = LocaleNumbers.decimal(valor, 2, resolver.locale)
        val banda = when {
            pesa.minimo != null && pesa.maximo != null -> resolver.get(
                R.string.obd_chart_range,
                numero(pesa.minimo),
                numero(pesa.maximo),
                pesa.unidad,
            )
            pesa.minimo != null -> resolver.get(R.string.obd_chart_minimum, numero(pesa.minimo), pesa.unidad)
            pesa.maximo != null -> resolver.get(R.string.obd_chart_maximum, numero(pesa.maximo), pesa.unidad)
            else -> resolver.get(R.string.obd_chart_no_band)
        }
        return resolver.get(
            R.string.obd_chart_measured_value,
            numero(pesa.medido),
            pesa.unidad,
            banda,
            pesa.origen,
        )
    }

    private fun cabecera(ancho: Int, alto: Int, descripcion: String): String =
        "<svg xmlns=\"http://www.w3.org/2000/svg\" role=\"img\" aria-label=\"${escapar(descripcion)}\" " +
            "viewBox=\"0 0 $ancho $alto\"><style>" + // i18n-ignore
            "text{fill:#333;font:14px system-ui,sans-serif}.dato{font-size:12px}.serie{fill:none;stroke-width:3}.s0{stroke:#738500}.s1{stroke:#006d77}.s2{stroke:#9c2c2c}" + // i18n-ignore
            ".patron0{stroke-dasharray:none}.patron1{stroke-dasharray:10 6}.patron2{stroke-dasharray:2 6}.leyenda{font-weight:700}.l0{fill:#687900}.l1{fill:#006d77}.l2{fill:#9c2c2c}.eje,.escala{stroke:#777;stroke-width:1}.tramo{fill:#738500;opacity:.09}.tramo1{fill:#006d77}.etiqueta-tramo{font-size:11px;font-weight:700}.banda{stroke:#738500;stroke-width:12;opacity:.35}.medido{fill:#1f1f1f;stroke:#f0dc00;stroke-width:3}" + // i18n-ignore
            "@media(prefers-color-scheme:dark){text{fill:#e7e7df}.eje,.escala{stroke:#aaa}.medido{fill:#f5f5ef}}" +
            "</style>"

    private fun ejes(ancho: Int, alto: Int, limites: Limites, resolver: StringResolver): String =
        "<line class=\"eje\" x1=\"52\" y1=\"36\" x2=\"52\" y2=\"${alto - 34}\"/>" + // i18n-ignore
            "<line class=\"eje\" x1=\"52\" y1=\"${alto - 34}\" x2=\"${ancho - 20}\" y2=\"${alto - 34}\"/>" + // i18n-ignore
            "<text x=\"8\" y=\"48\">${LocaleNumbers.decimal(limites.maxY, 1, resolver.locale)}</text>" + // i18n-ignore
            "<text x=\"8\" y=\"${alto - 34}\">${LocaleNumbers.decimal(limites.minY, 1, resolver.locale)}</text>" // i18n-ignore

    private fun x(valor: Double, limites: Limites, ancho: Int): String =
        FormatoTaller.redondear(52 + (valor - limites.minX) / (limites.maxX - limites.minX) * (ancho - 72), 2).toString()

    private fun y(valor: Double, limites: Limites, alto: Int): String =
        FormatoTaller.redondear(36 + (limites.maxY - valor) / (limites.maxY - limites.minY) * (alto - 70), 2).toString()

    private fun vacia(ancho: Int, alto: Int, mensaje: String): String =
        cabecera(ancho, alto, mensaje) + "<text x=\"16\" y=\"40\">${escapar(mensaje)}</text></svg>" // i18n-ignore

    private fun escapar(texto: String): String = texto
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private data class Limites(val minX: Double, val maxX: Double, val minY: Double, val maxY: Double) {
        fun conMargen(): Limites {
            val x2 = if (maxX > minX) maxX else minX + 1.0
            val margen = ((maxY - minY) * 0.05).takeIf { it > 0 } ?: 1.0
            return copy(maxX = x2, minY = minY - margen, maxY = maxY + margen)
        }
    }
}
