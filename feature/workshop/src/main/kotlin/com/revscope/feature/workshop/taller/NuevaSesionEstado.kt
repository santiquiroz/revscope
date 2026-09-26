package com.revscope.feature.workshop.taller

import com.revscope.core.obd.taller.FormatoTaller
import com.revscope.core.obd.taller.sesion.ChequeoRegistrado
import com.revscope.core.obd.taller.sesion.Sintoma
import java.time.ZoneId

data class OpcionChequeoBase(val id: Long, val fecha: String, val resumen: String)

data class SesionPorCerrar(val titulo: String, val desde: String)

data class NuevaSesionEstado(
    val cargando: Boolean = true,
    val vehiculo: String? = null,
    val sintomas: Set<Sintoma> = emptySet(),
    val sintomasTexto: String = "",
    val odometro: String = "",
    val odometroDeEcu: Boolean = false,
    val notas: String = "",
    val bases: List<OpcionChequeoBase> = emptyList(),
    val baseElegida: Long? = null,
    val errorOdometro: String? = null,
    val porCerrar: SesionPorCerrar? = null,
    val abriendo: Boolean = false,
    val error: String? = null,
) {
    val puedeAbrir: Boolean get() = vehiculo != null && !abriendo && !cargando
}

internal object OdometroTexto {

    const val ERROR = "Escribe los kilómetros solo con números, por ejemplo 12345"
    private const val MAXIMO_KM = 2_000_000.0

    fun leer(texto: String): Result<Double?> {
        val limpio = texto.trim().replace(" ", "").replace(".", "").replace(',', '.')
        if (limpio.isEmpty()) return Result.success(null)
        val km = limpio.toDoubleOrNull()?.takeIf { it in 0.0..MAXIMO_KM }
        return if (km == null) Result.failure(IllegalArgumentException(ERROR)) else Result.success(km)
    }

    fun escribir(km: Double): String = FormatoTaller.compacto(FormatoTaller.redondear(km, 0))
}

internal object OpcionesChequeoBase {

    fun de(chequeo: ChequeoRegistrado, zona: ZoneId): OpcionChequeoBase =
        OpcionChequeoBase(chequeo.id, FechasTaller.fechaHora(chequeo.instante, zona), resumen(chequeo))

    private fun resumen(chequeo: ChequeoRegistrado): String {
        val metricas = chequeo.metricas
        return when {
            metricas == null || !metricas.dtcsLeidos -> hallazgos(chequeo.items.size)
            metricas.dtcs.isEmpty() -> "Sin códigos"
            else -> "Códigos: ${metricas.dtcs.joinToString()}"
        }
    }

    private fun hallazgos(n: Int): String = if (n == 1) "1 hallazgo" else "$n hallazgos"
}
