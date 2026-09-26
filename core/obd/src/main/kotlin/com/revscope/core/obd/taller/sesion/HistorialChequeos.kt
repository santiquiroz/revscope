package com.revscope.core.obd.taller.sesion

import com.revscope.core.data.db.dao.HealthReportDao
import com.revscope.core.data.db.entities.HealthReportEntity
import com.revscope.core.obd.workshop.DiagnosticRules
import com.revscope.core.obd.workshop.HealthReportFormato
import com.revscope.core.obd.workshop.MetricasChequeo
import javax.inject.Inject

data class ChequeoRegistrado(
    val id: Long,
    val vehiculoId: Long,
    val instante: Long,
    val items: List<DiagnosticRules.Diagnosis>,
    val metricas: MetricasChequeo?,
)

interface HistorialChequeos {
    suspend fun ultimoAntesDe(vehiculoId: Long, instante: Long): ChequeoRegistrado?

    suspend fun porId(id: Long): ChequeoRegistrado?
}

class HistorialChequeosRoom @Inject constructor(private val dao: HealthReportDao) : HistorialChequeos {

    override suspend fun ultimoAntesDe(vehiculoId: Long, instante: Long): ChequeoRegistrado? =
        dao.latestForProfileBefore(vehiculoId, instante)?.aChequeo()

    override suspend fun porId(id: Long): ChequeoRegistrado? = dao.getById(id)?.aChequeo()

    private fun HealthReportEntity.aChequeo(): ChequeoRegistrado {
        val guardado = HealthReportFormato.leer(resultsJson)
        return ChequeoRegistrado(id, vehicleProfileId, timestamp, guardado.items, guardado.metricas)
    }
}
