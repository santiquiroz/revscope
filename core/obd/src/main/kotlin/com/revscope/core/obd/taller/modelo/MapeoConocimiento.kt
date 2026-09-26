package com.revscope.core.obd.taller.modelo

import com.revscope.core.data.db.entities.ReferenceBandEntity
import com.revscope.core.data.db.entities.VehicleKnowledgeEntity
import com.revscope.core.data.db.entities.VehiclePartEntity
import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.obd.taller.referencia.BandaReferencia
import com.revscope.core.obd.taller.referencia.OrigenBanda

fun VehicleKnowledgeEntity.aConocimiento(
    repuestos: List<VehiclePartEntity>,
    bandas: List<ReferenceBandEntity>,
) = ConocimientoModelo(
    clave = key,
    nombre = displayName,
    tipo = VehicleType.from(vehicleType),
    ecu = ecu,
    fuenteEcu = ecuSource,
    notasProtocolo = protocolNotes,
    notas = CodecConocimiento.notasDesdeJson(notes),
    cableado = CodecConocimiento.cableadoDesdeJson(wiringJson),
    repuestos = repuestos.map { it.aRepuesto() },
    bandas = bandas.mapNotNull { it.aBandaValidaONula() },
    editadoPorUsuario = userEdited,
)

fun VehicleKnowledgeEntity.aResumen() =
    ResumenModelo(clave = key, nombre = displayName, tipo = VehicleType.from(vehicleType))

fun ConocimientoModelo.aEntidad(seedVersion: Int) = VehicleKnowledgeEntity(
    key = clave,
    displayName = nombre,
    vehicleType = tipo.name,
    ecu = ecu,
    ecuSource = fuenteEcu,
    protocolNotes = notasProtocolo,
    notes = CodecConocimiento.notasAJson(notas),
    wiringJson = CodecConocimiento.cableadoAJson(cableado),
    seedVersion = seedVersion,
    userEdited = editadoPorUsuario,
)

fun RepuestoModelo.aEntidad(claveModelo: String) = VehiclePartEntity(
    knowledgeKey = claveModelo,
    role = rol,
    partNumber = referencia,
    description = descripcion,
    kind = tipo.name,
    note = nota,
    source = fuente,
)

fun BandaReferencia.aEntidad(claveModelo: String) = ReferenceBandEntity(
    knowledgeKey = claveModelo,
    bandKey = clave,
    minValue = min,
    maxValue = max,
    unit = unidad,
    origin = origen.name,
    source = fuente,
)

private fun VehiclePartEntity.aRepuesto() = RepuestoModelo(
    rol = role,
    referencia = partNumber,
    descripcion = description,
    tipo = enumONulo<TipoRepuesto>(kind) ?: TipoRepuesto.ALTERNATIVO,
    nota = note,
    fuente = source,
)

// Una fila con un origen desconocido (versión futura de la app) o inválida no se muestra como referencia.
private fun ReferenceBandEntity.aBandaValidaONula(): BandaReferencia? {
    val origen = enumONulo<OrigenBanda>(origin) ?: return null
    return runCatching { BandaReferencia(bandKey, minValue, maxValue, unit, origen, source) }.getOrNull()
}

private inline fun <reified E : Enum<E>> enumONulo(nombre: String): E? =
    enumValues<E>().firstOrNull { it.name == nombre }
