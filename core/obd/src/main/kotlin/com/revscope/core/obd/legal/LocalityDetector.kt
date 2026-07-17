package com.revscope.core.obd.legal

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Locale
import kotlin.coroutines.resume

private const val MAX_RESULTS = 1

/**
 * Detecta cambios de municipio/localidad a partir de fixes GPS usando [Geocoder].
 * A diferencia de [CityRegistry], no está limitado a las ciudades con pico y placa —
 * funciona para cualquier pueblo. El throttle (120s + 3km, ver [GpsEvaluationThrottle])
 * se evalúa de forma síncrona en [shouldEvaluate] antes de gastar batería en un lookup
 * de Geocoder.
 *
 * La primera localidad detectada tras el arranque solo fija el estado base — no
 * cuenta como "cambio" (evita anunciar la ciudad en la que ya estás al conectar).
 */
class LocalityDetector(private val context: Context) {

    data class Locality(val municipio: String, val departamento: String?, val pais: String? = null)

    private val throttle = GpsEvaluationThrottle()

    @Volatile private var lastMunicipio: String? = null
    @Volatile private var hasBaseline = false

    /** Chequeo síncrono barato — llamar antes de lanzar una corrutina para el lookup real. */
    fun shouldEvaluate(latitude: Double, longitude: Double, nowMs: Long = System.currentTimeMillis()): Boolean =
        throttle.shouldEvaluate(latitude, longitude, nowMs)

    /**
     * Marca el intento de evaluación — debe llamarse una vez por fix aceptado por
     * [shouldEvaluate], incluso si el llamador termina descartando el resultado (ej.
     * la función está apagada), para que el throttle avance igual.
     */
    fun markEvaluationAttempt(latitude: Double, longitude: Double, nowMs: Long = System.currentTimeMillis()) {
        throttle.recordEvaluation(latitude, longitude, nowMs)
    }

    /**
     * Resuelve la localidad del fix y la compara con la anterior. Devuelve la nueva
     * [Locality] solo si CAMBIÓ respecto a la anterior y no es la primera fijación
     * (esa solo establece la base). Null también ante error o sin Geocoder disponible
     * en el dispositivo — nunca lanza.
     */
    suspend fun detectLocalityChange(latitude: Double, longitude: Double): Locality? {
        val locality = resolveLocality(latitude, longitude) ?: return null

        val previous = lastMunicipio
        lastMunicipio = locality.municipio
        if (!hasBaseline) {
            hasBaseline = true
            return null
        }
        if (locality.municipio == previous) return null
        return locality
    }

    /** Localidad actual del fix sin semántica de cambio — nunca lanza, null ante error. */
    suspend fun resolveLocality(latitude: Double, longitude: Double): Locality? {
        if (!Geocoder.isPresent()) return null
        val address = resolveAddress(latitude, longitude) ?: return null
        val municipio = address.locality ?: address.subAdminArea ?: return null
        return Locality(municipio, address.adminArea, address.countryName)
    }

    private suspend fun resolveAddress(latitude: Double, longitude: Double): Address? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            resolveAddressAsync(latitude, longitude)
        } else {
            resolveAddressSync(latitude, longitude)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "LocalityDetector: geocoding failed")
        null
    }

    private suspend fun resolveAddressAsync(latitude: Double, longitude: Double): Address? =
        suspendCancellableCoroutine { cont ->
            Geocoder(context, Locale("es", "CO")).getFromLocation(
                latitude,
                longitude,
                MAX_RESULTS,
                object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        if (cont.isActive) cont.resume(addresses.firstOrNull())
                    }

                    override fun onError(errorMessage: String?) {
                        if (cont.isActive) cont.resume(null)
                    }
                },
            )
        }

    @Suppress("DEPRECATION")
    private suspend fun resolveAddressSync(latitude: Double, longitude: Double): Address? =
        withContext(Dispatchers.IO) {
            Geocoder(context, Locale("es", "CO")).getFromLocation(latitude, longitude, MAX_RESULTS)?.firstOrNull()
        }
}
