package com.revscope.core.obd.taller.dtc

import android.content.Context
import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.R
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BaseConocimientoDtc(private val leerJson: () -> String, private val resolver: StringResolver) {

    @Inject
    constructor(@ApplicationContext context: Context, resolver: StringResolver) : this({ leerAsset(context, resolver) }, resolver)

    private val carga: CargaGuiaDtc by lazy(::cargarSinRomper)
    private val porCodigo: Map<String, GuiaDtc> by lazy { catalogo.guias.associateBy { it.codigo } }

    val catalogo: CatalogoDtc get() = carga.catalogo

    val problemas: List<String> get() = carga.problemas

    fun guia(codigo: String): GuiaDtc? = DecodificadorDtc.normalizar(codigo)?.let(porCodigo::get)

    // Una guía dañada no debe tumbar el Taller: sin ella queda la decodificación estructural.
    private fun cargarSinRomper(): CargaGuiaDtc =
        runCatching { CargaGuiaDtc.desdeJson(leerJson(), resolver) }
            .onSuccess { carga -> carga.problemas.forEach { Timber.w("Guía DTC: %s", it) } }
            .getOrElse { error ->
                Timber.e(error, "No se pudo cargar la guía DTC")
                CargaGuiaDtc.VACIA.copy(
                    problemas = listOf(resolver.get(R.string.obd_dtc_asset_read_error, ASSET_GUIA, error.message.orEmpty())),
                )
            }

    companion object {
        const val ASSET_GUIA = "taller/dtc_guia_es.json"
        internal const val ASSET_GUIA_EN = "taller/dtc_guide_en.json"

        private fun leerAsset(context: Context, resolver: StringResolver): String {
            val asset = dtcGuideAsset(resolver.locale.language) { path ->
                context.assets.list("taller")?.contains(path.substringAfterLast('/')) == true
            }
            return context.assets.open(asset).bufferedReader().use { it.readText() }
        }
    }
}

internal fun dtcGuideAsset(language: String, exists: (String) -> Boolean): String {
    if (language == "es") return BaseConocimientoDtc.ASSET_GUIA
    val localized = "taller/dtc_guide_$language.json"
    if (exists(localized)) return localized
    return if (exists(BaseConocimientoDtc.ASSET_GUIA_EN)) {
        BaseConocimientoDtc.ASSET_GUIA_EN
    } else {
        BaseConocimientoDtc.ASSET_GUIA
    }
}
