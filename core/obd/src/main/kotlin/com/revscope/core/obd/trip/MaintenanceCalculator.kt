package com.revscope.core.obd.trip

import com.revscope.core.data.db.entities.MaintenanceItemEntity
import com.revscope.core.obd.legal.DocumentStatusCalculator.Nivel
import kotlin.math.roundToLong

private const val ATENCION_FRACTION = 0.10

/**
 * Motor puro de mantenimiento por kilometraje — combina el odómetro actual del vehículo
 * (odometerBaseKm + suma de distanceKm de sus sesiones) con los ítems configurados para
 * decidir cuánto falta y qué tan urgente es cada uno. Reutiliza [Nivel] de
 * DocumentStatusCalculator para que la card de "Vehículo al día" use el mismo semáforo.
 */
object MaintenanceCalculator {

    data class ItemStatus(
        val item: MaintenanceItemEntity,
        val kmRestantes: Double,
        val nivel: Nivel,
    )

    fun odometroActual(odometerBaseKm: Double, sumaSesionesKm: Double): Double =
        odometerBaseKm + sumaSesionesKm

    fun calculate(odometroActual: Double, items: List<MaintenanceItemEntity>): List<ItemStatus> =
        items.map { itemStatus(odometroActual, it) }

    /** Nivel más severo entre los ítems; SIN_CONFIGURAR si no hay ítems configurados. */
    fun peorNivel(estados: List<ItemStatus>): Nivel =
        estados.maxByOrNull { severidad(it.nivel) }?.nivel ?: Nivel.SIN_CONFIGURAR

    /** Ítem con menos kilómetros restantes — el próximo en vencer. */
    fun proximo(estados: List<ItemStatus>): ItemStatus? =
        estados.minByOrNull { it.kmRestantes }

    /** Texto corto para la card de "Vehículo al día" — "Aceite en 420 km" o "Por configurar". */
    fun detalleTexto(estados: List<ItemStatus>): String {
        val proximoItem = proximo(estados) ?: return "Por configurar"
        val km = proximoItem.kmRestantes.roundToLong()
        return if (km <= 0) "${proximoItem.item.nombre} vencido" else "${proximoItem.item.nombre} en $km km"
    }

    private fun itemStatus(odometroActual: Double, item: MaintenanceItemEntity): ItemStatus {
        val kmRestantes = (item.ultimoServicioKm + item.intervaloKm) - odometroActual
        return ItemStatus(item, kmRestantes, nivelFor(kmRestantes, item.intervaloKm))
    }

    private fun nivelFor(kmRestantes: Double, intervaloKm: Double): Nivel = when {
        kmRestantes <= 0 -> Nivel.VENCIDO
        kmRestantes <= intervaloKm * ATENCION_FRACTION -> Nivel.ATENCION
        else -> Nivel.OK
    }

    private fun severidad(nivel: Nivel): Int = when (nivel) {
        Nivel.VENCIDO -> 3
        Nivel.ATENCION -> 2
        Nivel.OK -> 1
        Nivel.SIN_CONFIGURAR -> 0
    }
}
