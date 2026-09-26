package com.revscope.core.obd.taller.sesion

import com.revscope.core.data.db.entities.DiagEventEntity
import com.revscope.core.data.db.entities.DiagSessionEntity

private const val SEPARADOR = ","

fun DiagSessionEntity.aSesion() = SesionTaller(
    id = id,
    vehiculoId = vehicleProfileId,
    claveModelo = knowledgeKey,
    inicio = startedAt,
    cierre = closedAt,
    titulo = title,
    sintomas = leerLista(symptomTags).mapNotNull { enumONulo<Sintoma>(it) }.toSet(),
    sintomasTexto = symptomsText,
    notas = notes,
    odometroKm = odometerKm,
    chequeoBaseId = baselineHealthReportId,
    interpretacion = interpretation,
    pasosMarcados = leerLista(checkedSteps).toSet(),
)

fun SesionTaller.aEntidad() = DiagSessionEntity(
    id = id,
    vehicleProfileId = vehiculoId,
    knowledgeKey = claveModelo,
    startedAt = inicio,
    closedAt = cierre,
    title = titulo,
    symptomTags = escribirLista(sintomas.map { it.name }),
    symptomsText = sintomasTexto,
    notes = notas,
    odometerKm = odometroKm,
    baselineHealthReportId = chequeoBaseId,
    interpretation = interpretacion,
    checkedSteps = escribirLista(pasosMarcados),
)

fun DiagEventEntity.aEvento() = EventoTaller(
    id = id,
    sesionId = sessionId,
    instante = timestamp,
    tipo = enumONulo<TipoEvento>(type) ?: TipoEvento.NOTA,
    origen = enumONulo<OrigenEvento>(source) ?: OrigenEvento.APP,
    titulo = title,
    resumen = summary,
    veredicto = enumONulo<Veredicto>(verdict) ?: Veredicto.INFO,
    payloadVersion = payloadVersion,
    payloadJson = payloadJson,
    adjunto = attachmentPath,
)

fun EventoTaller.aEntidad() = DiagEventEntity(
    id = id,
    sessionId = sesionId,
    timestamp = instante,
    type = tipo.name,
    source = origen.name,
    title = titulo,
    summary = resumen,
    verdict = veredicto.name,
    payloadVersion = payloadVersion,
    payloadJson = payloadJson,
    attachmentPath = adjunto,
)

private fun leerLista(texto: String): List<String> =
    texto.split(SEPARADOR).map { it.trim() }.filter { it.isNotEmpty() }

// Orden estable: guardar dos veces el mismo conjunto produce el mismo texto.
private fun escribirLista(valores: Iterable<String>): String {
    val limpios = valores.map { it.trim() }.filter { it.isNotEmpty() }
    require(limpios.none { it.contains(SEPARADOR) }) { "Las claves no pueden contener «$SEPARADOR»" }
    return limpios.sorted().joinToString(SEPARADOR)
}

private inline fun <reified E : Enum<E>> enumONulo(nombre: String): E? =
    enumValues<E>().firstOrNull { it.name == nombre }
