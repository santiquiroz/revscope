package com.revscope.feature.workshop.taller.informe

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.obd.taller.informe.GeneradorInformeTaller
import com.revscope.core.obd.taller.informe.InformeTallerHtml
import com.revscope.core.obd.taller.sesion.TallerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class InformeViewModel @Inject constructor(
    guardado: SavedStateHandle,
    private val generador: GeneradorInformeTaller,
    private val repositorio: TallerRepository,
) : ViewModel() {

    private val sesionId = checkNotNull(guardado.get<Long>(ARG_SESION)) { "Falta el argumento $ARG_SESION" }
    private val mutableEstado = MutableStateFlow(InformeUi())
    private val archivos = Channel<File>(Channel.BUFFERED)
    val estado: StateFlow<InformeUi> = mutableEstado.asStateFlow()
    val archivoCompartir = archivos.receiveAsFlow()

    init {
        cargar()
    }

    fun cargar() {
        mutableEstado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            mutableEstado.value = estadoCargado()
        }
    }

    fun abrirEditor() = mutableEstado.update {
        it.copy(editorVisible = true, borradorInterpretacion = it.interpretacion)
    }

    fun cambiarInterpretacion(texto: String) = mutableEstado.update { it.copy(borradorInterpretacion = texto) }

    fun cancelarEditor() = mutableEstado.update { it.copy(editorVisible = false, borradorInterpretacion = "") }

    fun guardarInterpretacion() {
        val texto = mutableEstado.value.borradorInterpretacion.trim()
        mutableEstado.update { it.copy(editorVisible = false) }
        viewModelScope.launch {
            val sesion = runCatching { repositorio.sesion(sesionId) }.getOrNull()
            if (sesion == null) {
                mutableEstado.update { it.copy(error = "La sesión ya no existe.") }
                return@launch
            }
            runCatching { repositorio.actualizarSesion(sesion.copy(interpretacion = texto)) }
                .onSuccess { cargar() }
                .onFailure { mutableEstado.update { it.copy(error = "No se pudo guardar la interpretación.") } }
        }
    }

    fun prepararArchivo(directorio: File) {
        val html = mutableEstado.value.html.takeIf(String::isNotBlank) ?: return
        viewModelScope.launch {
            val archivo = withContext(Dispatchers.IO) { escribir(directorio, html) }
            if (archivo == null) {
                mutableEstado.update { it.copy(mensaje = "No se pudo preparar el informe para compartir.") }
            } else {
                archivos.send(archivo)
            }
        }
    }

    fun mensajeMostrado() = mutableEstado.update { it.copy(mensaje = null) }

    private fun escribir(directorio: File, html: String): File? = runCatching {
        directorio.mkdirs()
        File(directorio, "informe-${marcaTiempo()}.html").apply { writeText(html, Charsets.UTF_8) }
    }.getOrNull()

    private fun marcaTiempo(): String = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())

    private suspend fun estadoCargado(): InformeUi {
        val informe = runCatching { generador.generar(sesionId) }
            .getOrElse { return InformeUi(cargando = false, error = "No se pudo generar el informe.") }
            ?: return InformeUi(cargando = false, error = "No se encontró la sesión para generar el informe.")
        return InformeUi(
            cargando = false,
            titulo = informe.titulo,
            html = InformeTallerHtml.render(informe),
            interpretacion = informe.interpretacionTecnico,
        )
    }

    companion object {
        const val ARG_SESION = "sesionId"
    }
}
