package com.revscope.core.obd.taller.informe

import com.revscope.core.obd.taller.FormatoTaller
import com.revscope.core.obd.taller.dtc.GuiaDtc
import com.revscope.core.obd.taller.dtc.PasosGuia
import com.revscope.core.obd.taller.modelo.ConocimientoModelo
import com.revscope.core.obd.taller.modelo.TipoNota
import com.revscope.core.obd.taller.sesion.AnalisisSesion
import com.revscope.core.obd.taller.sesion.EventoTaller
import com.revscope.core.obd.taller.sesion.FilaComparacion
import com.revscope.core.obd.taller.sesion.SesionTaller
import com.revscope.core.obd.taller.sesion.TipoEvento
import com.revscope.core.obd.taller.sesion.VehiculoTaller
import com.revscope.core.obd.taller.sesion.Veredicto
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

data class InformeTaller(
    val generadoEn: Long,
    val titulo: String,
    val vehiculo: String,
    val codigo: String,
    val sintomas: String,
    val comoSeMidio: String,
    val conclusion: ConclusionInforme,
    val mediciones: List<MedicionInforme>,
    val pruebas: List<PruebaInforme>,
    val lineaTiempo: List<EventoInforme>,
    val comparacion: List<FilaComparacion>,
    val interpretacionAutomatica: List<String>,
    val interpretacionTecnico: String,
    val repuestos: List<RepuestoInforme>,
    val pasosGuia: List<PasoGuiaInforme>,
    val pendientes: List<String>,
    val fuentes: List<FuenteInforme>,
    val versionApp: String,
)

data class ConclusionInforme(val veredicto: Veredicto, val frase: String, val cables: List<CableInforme>)

data class CableInforme(val etiqueta: String, val valor: String, val veredicto: Veredicto)

data class MedicionInforme(
    val sensor: String,
    val cable: String,
    val condicion: String,
    val valor: Double,
    val unidad: String,
    val estado: String,
    val referencia: String,
    val bandaMin: Double? = null,
    val bandaMax: Double? = null,
)

data class PruebaInforme(
    val titulo: String,
    val veredicto: Veredicto,
    val resumen: String,
    val interpretacion: String,
    val hallazgos: List<String>,
    val pasos: List<PasoPruebaInforme>,
    val series: List<SerieGrafica>,
    val tramos: List<TramoGrafica> = emptyList(),
)

data class PasoPruebaInforme(val nombre: String, val porcentaje: Double?, val voltios: Double?)

data class EventoInforme(
    val instante: Long,
    val tipo: String,
    val titulo: String,
    val resumen: String,
    val veredicto: Veredicto,
)

data class RepuestoInforme(
    val rol: String,
    val referencia: String,
    val descripcion: String,
    val tipo: String,
    val nota: String,
    val fuente: String,
)

data class PasoGuiaInforme(val numero: Int, val titulo: String, val detalle: String)

data class FuenteInforme(val nombre: String, val url: String? = null)

class ArmadorInformeTaller @Inject constructor() {

    fun armar(
        sesion: SesionTaller,
        eventos: List<EventoTaller>,
        vehiculo: VehiculoTaller,
        analisis: AnalisisSesion,
        conocimiento: ConocimientoModelo?,
        guia: GuiaDtc?,
        versionApp: String,
        generadoEn: Long = sesion.cierre ?: sesion.inicio,
    ): InformeTaller {
        val ordenados = eventos.sortedWith(compareBy(EventoTaller::instante, EventoTaller::id))
        val mediciones = mediciones(ordenados)
        val pruebas = pruebas(ordenados)
        val pendientesGuia = pasosPendientes(guia, sesion.pasosMarcados)
        val fuentes = fuentes(conocimiento)
        return InformeTaller(
            generadoEn = generadoEn,
            titulo = sesion.titulo,
            vehiculo = vehiculo.nombre,
            codigo = analisis.codigos.joinToString().ifBlank { "Sin códigos registrados" },
            sintomas = sintomas(sesion),
            comoSeMidio = comoSeMidio(ordenados, conocimiento),
            conclusion = conclusion(ordenados, mediciones),
            mediciones = mediciones,
            pruebas = pruebas,
            lineaTiempo = ordenados.map(::eventoInforme),
            comparacion = analisis.comparacion,
            interpretacionAutomatica = interpretacionAutomatica(ordenados, pruebas),
            interpretacionTecnico = sesion.interpretacion,
            repuestos = conocimiento?.repuestos.orEmpty().map {
                RepuestoInforme(it.rol, it.referencia, it.descripcion, it.tipo.name, it.nota, it.fuente)
            },
            pasosGuia = pendientesGuia,
            pendientes = pendientes(conocimiento, pendientesGuia, analisis, pruebas),
            fuentes = fuentes,
            versionApp = versionApp,
        )
    }

    private fun sintomas(sesion: SesionTaller): String =
        (sesion.sintomas.sortedBy { it.ordinal }.map { it.etiqueta } + sesion.sintomasTexto.trim().takeIf { it.isNotEmpty() })
            .filterNotNull()
            .joinToString(" · ")
            .ifBlank { "Sin síntomas anotados" }

    private fun comoSeMidio(eventos: List<EventoTaller>, conocimiento: ConocimientoModelo?): String = buildList {
        add("Adaptador OBD2")
        conocimiento?.notasProtocolo?.takeIf(String::isNotBlank)?.let(::add)
        if (eventos.any { it.tipo == TipoEvento.PRUEBA_GUIADA }) add("RevScope")
        if (eventos.any { it.tipo == TipoEvento.MEDICION_MULTIMETRO }) add("Multímetro")
    }.joinToString(" · ")

    private fun conclusion(eventos: List<EventoTaller>, mediciones: List<MedicionInforme>): ConclusionInforme {
        val peor = eventos.map(EventoTaller::veredicto).maxByOrNull(::peso) ?: Veredicto.INFO
        val frase = eventos.lastOrNull { it.veredicto == peor }?.resumen?.takeIf(String::isNotBlank)
            ?: when (peor) {
                Veredicto.FALLA -> "Hay hallazgos compatibles con una falla; confirma antes de reemplazar piezas."
                Veredicto.ATENCION -> "Hay mediciones que conviene confirmar."
                Veredicto.OK -> "Las comprobaciones registradas quedaron dentro de referencia."
                Veredicto.INFO -> "Aún no hay suficientes mediciones para una conclusión."
            }
        val cables = mediciones.map { CableInforme(it.cable, valor(it.valor, it.unidad), veredictoMedicion(it.estado)) }
        return ConclusionInforme(peor, frase, cables)
    }

    private fun mediciones(eventos: List<EventoTaller>): List<MedicionInforme> = eventos
        .asSequence()
        .filter { it.tipo == TipoEvento.MEDICION_MULTIMETRO }
        .flatMap { mediciones(it).asSequence() }
        .associateBy { listOf(it.sensor, it.cable, it.condicion, it.unidad) }
        .values
        .toList()

    private fun mediciones(evento: EventoTaller): List<MedicionInforme> {
        val payload = json(evento.payloadJson) ?: return emptyList()
        val sensor = payload.optString("titulo", evento.titulo)
        return payload.optJSONArray("lecturas").objetos().mapNotNull { lectura ->
            val valor = lectura.optDouble("valor").takeUnless(Double::isNaN) ?: return@mapNotNull null
            val referencia = lectura.optString("referencia", "Sin referencia")
            val (min, max) = limites(referencia)
            MedicionInforme(
                sensor = sensor,
                cable = listOfNotNull(
                    lectura.optString("funcion").textoTitulo(),
                    lectura.optString("color").takeIf(String::isNotBlank)?.let { "($it)" },
                ).joinToString(" "),
                condicion = lectura.optString("condicion").textoTitulo(),
                valor = valor,
                unidad = lectura.optString("unidad", payload.optString("unidad")),
                estado = lectura.optString("estado", "SIN_REFERENCIA"),
                referencia = referencia,
                bandaMin = min,
                bandaMax = max,
            )
        }
    }

    private fun limites(referencia: String): Pair<Double?, Double?> {
        val numeros = Regex("-?\\d+(?:[.,]\\d+)?").findAll(referencia).map {
            it.value.replace(',', '.').toDoubleOrNull()
        }.filterNotNull().take(2).toList()
        return when (numeros.size) {
            0 -> null to null
            1 -> when {
                referencia.contains('≥') || referencia.contains(">=") -> numeros[0] to null
                referencia.contains('≤') || referencia.contains("<=") -> null to numeros[0]
                else -> numeros[0] to numeros[0]
            }
            else -> numeros[0] to numeros[1]
        }
    }

    private fun pruebas(eventos: List<EventoTaller>): List<PruebaInforme> = eventos
        .filter { it.tipo == TipoEvento.PRUEBA_GUIADA }
        .mapNotNull(::prueba)

    private fun prueba(evento: EventoTaller): PruebaInforme? {
        val payload = json(evento.payloadJson) ?: return null
        val detalle = payload.optJSONObject("detalle")
        return PruebaInforme(
            titulo = payload.optString("tituloPrueba", evento.titulo),
            veredicto = payload.optString("veredicto").aVeredicto(evento.veredicto),
            resumen = payload.optString("titulo", evento.resumen),
            interpretacion = payload.optString("interpretacion"),
            hallazgos = payload.optJSONArray("hallazgos").textos(),
            pasos = detalle?.optJSONArray("pasos").objetos().map(::pasoPrueba),
            series = series(payload.optJSONObject("serie")),
            tramos = payload.optJSONArray("segmentos").objetos().mapNotNull(::tramo),
        )
    }

    private fun tramo(segmento: JSONObject): TramoGrafica? {
        val inicio = segmento.numeroONulo("inicio_ms") ?: return null
        val fin = segmento.numeroONulo("fin_ms") ?: return null
        return TramoGrafica(inicio, fin, segmento.optString("clave").textoTitulo())
    }

    private fun pasoPrueba(paso: JSONObject) = PasoPruebaInforme(
        nombre = paso.optString("clave").textoTitulo(),
        porcentaje = paso.numeroONulo("mediaPct"),
        voltios = paso.numeroONulo("mediaV"),
    )

    private fun series(objeto: JSONObject?): List<SerieGrafica> {
        if (objeto == null) return emptyList()
        return objeto.keys().asSequence().mapNotNull { nombre ->
            val serie = objeto.optJSONObject(nombre) ?: return@mapNotNull null
            val tiempos = serie.optJSONArray("t_ms") ?: return@mapNotNull null
            val valores = serie.optJSONArray("v") ?: return@mapNotNull null
            val puntos = (0 until minOf(tiempos.length(), valores.length())).mapNotNull { i ->
                val x = tiempos.optDouble(i).takeUnless(Double::isNaN) ?: return@mapNotNull null
                val y = valores.optDouble(i).takeUnless(Double::isNaN) ?: return@mapNotNull null
                PuntoGrafica(x, y)
            }
            SerieGrafica(nombre, GraficaSvg.reducir(puntos))
        }.toList()
    }

    private fun eventoInforme(evento: EventoTaller) = EventoInforme(
        evento.instante,
        evento.tipo.name.textoTitulo(),
        evento.titulo,
        evento.resumen,
        evento.veredicto,
    )

    private fun interpretacionAutomatica(eventos: List<EventoTaller>, pruebas: List<PruebaInforme>): List<String> =
        (pruebas.flatMap { listOf(it.interpretacion) + it.hallazgos } +
            eventos.filter { it.veredicto == Veredicto.FALLA || it.veredicto == Veredicto.ATENCION }.map { it.resumen })
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()

    private fun pasosPendientes(guia: GuiaDtc?, marcados: Set<String>): List<PasoGuiaInforme> = guia?.verificaciones
        ?.mapIndexedNotNull { indice, paso ->
            val numero = indice + 1
            if (PasosGuia.marcado(marcados, guia.codigo, numero)) null
            else PasoGuiaInforme(numero, paso.paso, paso.detalle)
        }.orEmpty()

    private fun pendientes(
        conocimiento: ConocimientoModelo?,
        pasosGuia: List<PasoGuiaInforme>,
        analisis: AnalisisSesion,
        pruebas: List<PruebaInforme>,
    ): List<String> = buildList {
        conocimiento?.notas?.filter { it.tipo == TipoNota.PENDIENTE }?.forEach { add(it.texto) }
        if (pasosGuia.isNotEmpty()) add("Quedan ${pasosGuia.size} pasos de la guía sin marcar.")
        val ejecutadas = pruebas.map { it.titulo.lowercase() }
        analisis.sugeridas.filterNot { sugerida -> ejecutadas.any { it.contains(sugerida.accion.etiqueta.substringAfter(": ").lowercase()) } }
            .forEach { add("${it.accion.etiqueta} ${it.motivo}") }
    }.distinct()

    private fun fuentes(conocimiento: ConocimientoModelo?): List<FuenteInforme> = buildList {
        conocimiento?.fuenteEcu?.takeIf(String::isNotBlank)?.let { add(FuenteInforme(it)) }
        conocimiento?.notas?.forEach { add(FuenteInforme(it.fuente, it.url)) }
        conocimiento?.repuestos?.forEach { add(FuenteInforme(it.fuente)) }
        conocimiento?.cableado?.forEach { add(FuenteInforme(it.fuente)) }
        conocimiento?.bandas?.filter { it.fuente.isNotBlank() }?.forEach { add(FuenteInforme(it.fuente)) }
    }.distinctBy { it.nombre to it.url }

    private fun peso(veredicto: Veredicto) = when (veredicto) {
        Veredicto.INFO -> 0
        Veredicto.OK -> 1
        Veredicto.ATENCION -> 2
        Veredicto.FALLA -> 3
    }

    private fun veredictoMedicion(estado: String) = when (estado) {
        "DENTRO" -> Veredicto.OK
        "BAJO", "ALTO" -> Veredicto.FALLA
        else -> Veredicto.INFO
    }

    private fun valor(valor: Double, unidad: String) = "${FormatoTaller.numero(valor, if (unidad == "V") 2 else 1)} $unidad".trim()

    private fun String.aVeredicto(porDefecto: Veredicto) = Veredicto.entries.firstOrNull { it.name == this } ?: porDefecto

    private fun String.textoTitulo(): String = lowercase().replace('_', ' ').replaceFirstChar { it.titlecase() }

    private fun json(texto: String): JSONObject? = runCatching { JSONObject(texto) }.getOrNull()

    private fun JSONObject.numeroONulo(clave: String): Double? = optDouble(clave).takeUnless(Double::isNaN)

    private fun JSONArray?.objetos(): List<JSONObject> =
        (0 until (this?.length() ?: 0)).mapNotNull { this?.optJSONObject(it) }

    private fun JSONArray?.textos(): List<String> =
        (0 until (this?.length() ?: 0)).mapNotNull { this?.optString(it)?.takeIf(String::isNotBlank) }
}
