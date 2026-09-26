package com.revscope.core.obd.taller.grafica

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import com.revscope.core.obd.taller.multimetro.FuncionCable
import com.revscope.core.obd.taller.pruebas.ReferenciaVoltaje
import com.revscope.core.obd.taller.sesion.EventoTaller
import com.revscope.core.obd.taller.sesion.TallerRepository
import com.revscope.core.obd.taller.sesion.TipoEvento
import com.revscope.core.obd.taller.sesion.VehiculoActivo
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

// La referencia con la que se pasa el % de un PID de posición a voltios: la que editó el técnico para el
// vehículo, si no la medida con el multímetro en la sesión abierta, y si no la típica de 5,0 V.
data class EstadoVref(val usada: ReferenciaVoltaje, val medida: ReferenciaVoltaje?, val editadaV: Double?)

object VrefSesion {
    const val ORIGEN = "Medida con el multímetro en esta sesión"

    // Lee la forma de LecturaMultimetro del asistente (§2.5): {"lecturas":[{"funcion","valor","unidad"}]}.
    fun desde(eventos: List<EventoTaller>): ReferenciaVoltaje? = eventos
        .filter { it.tipo == TipoEvento.MEDICION_MULTIMETRO }
        .sortedByDescending { it.instante }
        .firstNotNullOfOrNull(::referenciaDe)

    private fun referenciaDe(evento: EventoTaller): ReferenciaVoltaje? {
        val lecturas = runCatching { JSONObject(evento.payloadJson).optJSONArray("lecturas") }.getOrNull() ?: return null
        val valores = voltiosDeReferencia(lecturas).filter { it in ReferenciaVoltaje.MIN_V..ReferenciaVoltaje.MAX_V }
        return valores.takeIf { it.isNotEmpty() }?.let { ReferenciaVoltaje(it.average(), ORIGEN) }
    }

    private fun voltiosDeReferencia(lecturas: JSONArray): List<Double> = (0 until lecturas.length())
        .mapNotNull { lecturas.optJSONObject(it) }
        .filter { it.optString("funcion") == FuncionCable.REF_5V.name && it.optString("unidad", "V") == "V" }
        .mapNotNull { it.optDouble("valor").takeUnless(Double::isNaN) }
}

object ResolutorVref {
    fun resolver(editadaV: Double?, medida: ReferenciaVoltaje?): ReferenciaVoltaje =
        editadaV?.let(ReferenciaVoltaje::editada) ?: medida ?: ReferenciaVoltaje.TIPICA

    fun leer(texto: String): Double? =
        texto.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in ReferenciaVoltaje.MIN_V..ReferenciaVoltaje.MAX_V }

    // Quitar el valor editado vuelve a lo medido en la sesión, o a la típica si no hay medición.
    fun textoRestablecer(estado: EstadoVref): String? {
        if (estado.editadaV == null) return null
        return estado.medida?.let { "Usar la del multímetro (${FormatoPosicion.voltios(it.voltios)})" } ?: "Volver a 5,0 V típico"
    }
}

interface PreferenciasVref {
    suspend fun leer(vehiculoId: Long?): Double?

    suspend fun guardar(vehiculoId: Long?, voltios: Double?)
}

class PreferenciasVrefDataStore @Inject constructor(private val settings: DataStore<Preferences>) : PreferenciasVref {

    override suspend fun leer(vehiculoId: Long?): Double? = settings.data.first()[clave(vehiculoId)]

    override suspend fun guardar(vehiculoId: Long?, voltios: Double?) {
        settings.edit { prefs -> if (voltios == null) prefs.remove(clave(vehiculoId)) else prefs[clave(vehiculoId)] = voltios }
    }

    private fun clave(vehiculoId: Long?) = doublePreferencesKey("taller_vref_v_${vehiculoId ?: "sin_vehiculo"}")
}

class FuenteVref @Inject constructor(
    private val preferencias: PreferenciasVref,
    private val repositorio: TallerRepository,
    private val vehiculo: VehiculoActivo,
) {
    suspend fun actual(): EstadoVref {
        val id = vehiculo.actual()?.id
        val editada = preferencias.leer(id)
        val medida = id?.let { medidaEnSesion(it) }
        return EstadoVref(ResolutorVref.resolver(editada, medida), medida, editada)
    }

    suspend fun guardar(voltios: Double?) = preferencias.guardar(vehiculo.actual()?.id, voltios)

    private suspend fun medidaEnSesion(vehiculoId: Long): ReferenciaVoltaje? {
        val sesion = repositorio.sesionAbierta(vehiculoId) ?: return null
        return VrefSesion.desde(repositorio.eventos(sesion.id))
    }
}
