package com.revscope.core.obd.taller.dtc

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BaseConocimientoDtc(private val leerJson: () -> String) {

    @Inject
    constructor(@ApplicationContext context: Context) : this({ leerAsset(context) })

    private val carga: CargaGuiaDtc by lazy(::cargarSinRomper)
    private val porCodigo: Map<String, GuiaDtc> by lazy { catalogo.guias.associateBy { it.codigo } }

    val catalogo: CatalogoDtc get() = carga.catalogo

    val problemas: List<String> get() = carga.problemas

    fun guia(codigo: String): GuiaDtc? = DecodificadorDtc.normalizar(codigo)?.let(porCodigo::get)

    // Una guía dañada no debe tumbar el Taller: sin ella queda la decodificación estructural.
    private fun cargarSinRomper(): CargaGuiaDtc =
        runCatching { CargaGuiaDtc.desdeJson(leerJson()) }
            .onSuccess { carga -> carga.problemas.forEach { Timber.w("Guía DTC: %s", it) } }
            .getOrElse { error ->
                Timber.e(error, "No se pudo cargar la guía DTC")
                CargaGuiaDtc.VACIA.copy(problemas = listOf("No se pudo leer $ASSET_GUIA: ${error.message}"))
            }

    companion object {
        const val ASSET_GUIA = "taller/dtc_guia_es.json"

        private fun leerAsset(context: Context): String =
            context.assets.open(ASSET_GUIA).bufferedReader().use { it.readText() }
    }
}
