package com.revscope.feature.workshop.taller.modelo

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.taller.referencia.BandaReferencia
import com.revscope.core.obd.taller.referencia.OrigenBanda
import com.revscope.core.obd.taller.sesion.TallerRepository
import com.revscope.core.obd.taller.sesion.VehiculoTaller
import com.revscope.feature.workshop.R
import com.revscope.feature.workshop.taller.EntornoTaller
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.DecimalFormatSymbols
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ReferenciasViewModel @Inject constructor(
    private val repositorio: TallerRepository,
    entorno: EntornoTaller,
    private val resolver: StringResolver,
) : ViewModel() {

    private val local = MutableStateFlow(ReferenciasUi())
    val estado: StateFlow<ReferenciasUi> = local.asStateFlow()
    private var vehiculoActual: VehiculoTaller? = null
    private var versionCarga = 0L

    init {
        viewModelScope.launch {
            entorno.vehiculo.collectLatest { vehiculo ->
                vehiculoActual = vehiculo
                cargar(vehiculo)
            }
        }
    }

    fun reintentar() = viewModelScope.launch { cargar(vehiculoActual) }

    fun editar(clave: String) {
        val banda = local.value.bandas.firstOrNull { it.clave == clave } ?: return
        local.update {
            it.copy(dialogo = EdicionBandaUi(banda, banda.min.formatoEditable(), banda.max.formatoEditable()))
        }
    }

    fun cambiarMinimo(texto: String) = cambiarDialogo { copy(minimo = texto.take(MAX_VALOR), error = null) }

    fun cambiarMaximo(texto: String) = cambiarDialogo { copy(maximo = texto.take(MAX_VALOR), error = null) }

    fun cerrarDialogo() = local.update { it.copy(dialogo = null) }

    fun guardar() {
        val claveModelo = vehiculoActual?.claveModelo ?: return avisar(R.string.workshop_ref_choose_model)
        val dialogo = local.value.dialogo ?: return
        val limites = limites(dialogo) ?: return
        val banda = dialogo.banda.copy(min = limites.first, max = limites.second, origen = OrigenBanda.USUARIO, fuente = "")
        viewModelScope.launch {
            ejecutarGuardado(R.string.workshop_ref_saved) { repositorio.guardarBandaUsuario(claveModelo, banda) }
        }
    }

    fun restablecer(clave: String) {
        val claveModelo = vehiculoActual?.claveModelo ?: return avisar(R.string.workshop_ref_choose_model)
        viewModelScope.launch {
            ejecutarGuardado(R.string.workshop_ref_reset_done) {
                check(repositorio.restablecerBanda(claveModelo, clave)) { "La referencia no existe" } // i18n-ignore
            }
        }
    }

    fun mensajeMostrado() = local.update { it.copy(mensaje = null) }

    private suspend fun cargar(vehiculo: VehiculoTaller?, conservarMensaje: Boolean = false) {
        val version = ++versionCarga
        val mensaje = local.value.mensaje.takeIf { conservarMensaje }
        local.value = ReferenciasUi(vehiculo = vehiculo?.nombre, cargando = true, mensaje = mensaje)
        if (vehiculo == null) {
            local.value = local.value.copy(cargando = false)
            return
        }
        try {
            val conocimiento = vehiculo.claveModelo?.let { repositorio.conocimiento(it) }
            val bandas = repositorio.bandasResueltas(vehiculo.claveModelo, vehiculo.tipo).values.sortedBy { it.clave }
            if (version != versionCarga) return
            local.value = local.value.copy(modelo = conocimiento?.nombre, bandas = bandas, cargando = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (version != versionCarga) return
            Timber.w(e, "Referencias: no se pudieron cargar")
            local.value = local.value.copy(cargando = false, error = resolver.get(R.string.workshop_ref_load_failed))
        }
    }

    private suspend fun ejecutarGuardado(@StringRes mensaje: Int, accion: suspend () -> Unit) {
        try {
            accion()
            local.update { it.copy(dialogo = null, mensaje = resolver.get(mensaje)) }
            cargar(vehiculoActual, conservarMensaje = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Referencias: no se pudo guardar")
            avisar(R.string.workshop_ref_save_failed)
        }
    }

    private fun limites(dialogo: EdicionBandaUi): Pair<Double?, Double?>? {
        val min = dialogo.minimo.decimalONull()
        val max = dialogo.maximo.decimalONull()
        val error = when {
            dialogo.minimo.isNotBlank() && min == null -> R.string.workshop_ref_error_min_invalid
            dialogo.maximo.isNotBlank() && max == null -> R.string.workshop_ref_error_max_invalid
            min == null && max == null -> R.string.workshop_ref_error_no_limit
            min != null && max != null && min > max -> R.string.workshop_ref_error_inverted
            else -> null
        }
        if (error == null) return min to max
        val texto = resolver.get(error)
        cambiarDialogo { copy(error = texto) }
        return null
    }

    private fun cambiarDialogo(cambio: EdicionBandaUi.() -> EdicionBandaUi) =
        local.update { estado -> estado.copy(dialogo = estado.dialogo?.cambio()) }

    private fun avisar(@StringRes mensaje: Int) = local.update { it.copy(mensaje = resolver.get(mensaje)) }

    private fun String.decimalONull(): Double? =
        trim().takeIf(String::isNotEmpty)?.replace(',', '.')?.toDoubleOrNull()?.takeIf(Double::isFinite)

    // El campo se relee con decimalONull, que acepta coma o punto; aquí solo se muestra con el separador del idioma.
    private fun Double?.formatoEditable(): String {
        val separador = DecimalFormatSymbols.getInstance(resolver.locale).decimalSeparator
        return this?.toString()?.replace('.', separador).orEmpty()
    }

    companion object {
        private const val MAX_VALOR = 16
    }
}
