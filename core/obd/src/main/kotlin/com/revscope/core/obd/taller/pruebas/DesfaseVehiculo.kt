package com.revscope.core.obd.taller.pruebas

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.revscope.core.obd.taller.FormatoTaller
import com.revscope.core.obd.taller.sesion.VehiculoActivo
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import kotlin.math.abs

object CalibracionVoltaje {

    // Calibrar: lo del multímetro en los bornes menos lo que marca el adaptador en ese momento.
    fun desde(multimetroV: Double?, adaptadorV: Double?): DesfaseVoltaje? {
        if (multimetroV == null || adaptadorV == null) return null
        if (multimetroV !in VOLTAJE_PLAUSIBLE || adaptadorV !in VOLTAJE_PLAUSIBLE) return null
        if (abs(multimetroV - adaptadorV) > DesfaseVoltaje.MAX_V) return null
        return DesfaseVoltaje.medido(multimetroV, adaptadorV)
    }

    fun leer(texto: String): Double? = texto.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in VOLTAJE_PLAUSIBLE }

    fun texto(d: DesfaseVoltaje): String = if (d.calibrado) {
        "${FormatoTaller.conSigno(d.voltios, 2)} V · ${d.origen}"
    } else {
        "Sin calibrar: AT RV mide con ±0,1-0,2 V (típico)"
    }

    private val VOLTAJE_PLAUSIBLE = 6.0..18.0
}

interface PreferenciasDesfase {
    suspend fun leer(vehiculoId: Long?): DesfaseVoltaje?

    suspend fun guardar(vehiculoId: Long?, desfase: DesfaseVoltaje?)
}

class PreferenciasDesfaseDataStore @Inject constructor(private val settings: DataStore<Preferences>) : PreferenciasDesfase {

    override suspend fun leer(vehiculoId: Long?): DesfaseVoltaje? {
        val prefs = settings.data.first()
        val voltios = prefs[voltios(vehiculoId)] ?: return null
        return runCatching { DesfaseVoltaje(voltios, prefs[origen(vehiculoId)] ?: DesfaseVoltaje.ORIGEN_EDITADO) }.getOrNull()
    }

    override suspend fun guardar(vehiculoId: Long?, desfase: DesfaseVoltaje?) {
        settings.edit { prefs ->
            if (desfase == null) {
                prefs.remove(voltios(vehiculoId))
                prefs.remove(origen(vehiculoId))
            } else {
                prefs[voltios(vehiculoId)] = desfase.voltios
                prefs[origen(vehiculoId)] = desfase.origen
            }
        }
    }

    private fun voltios(vehiculoId: Long?) = doublePreferencesKey("taller_desfase_rv_v_${vehiculoId ?: SIN_VEHICULO}")

    private fun origen(vehiculoId: Long?) = stringPreferencesKey("taller_desfase_rv_origen_${vehiculoId ?: SIN_VEHICULO}")

    private companion object {
        const val SIN_VEHICULO = "sin_vehiculo"
    }
}

// El desfase del vehículo activo: cada moto o carro tiene el suyo, porque depende del cableado hasta el conector.
class FuenteDesfase @Inject constructor(
    private val preferencias: PreferenciasDesfase,
    private val vehiculo: VehiculoActivo,
) {
    suspend fun actual(): DesfaseVoltaje = preferencias.leer(vehiculo.actual()?.id) ?: DesfaseVoltaje.SIN_CALIBRAR

    suspend fun guardar(desfase: DesfaseVoltaje?) = preferencias.guardar(vehiculo.actual()?.id, desfase?.takeIf { it.calibrado })
}

class FuentesAnalisisApp @Inject constructor(
    private val lector: LectorAmbiente,
    private val desfase: FuenteDesfase,
) : FuentesAnalisis {
    override suspend fun ambiente(): LecturasAmbiente = lector.leer()

    override suspend fun desfaseVoltaje(): DesfaseVoltaje = desfase.actual()
}
