package com.revscope.core.obd.taller.dtc

import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.R
import com.revscope.core.obd.taller.multimetro.SensorMultimetro
import com.revscope.core.obd.taller.pruebas.TipoPrueba

data class CatalogoDtc(
    val version: Int,
    val fuenteDescripciones: String,
    val aviso: String,
    val guias: List<GuiaDtc>,
)

data class GuiaDtc(
    val codigo: String,
    val titulo: String,
    val sistema: String,
    val urgencia: UrgenciaDtc,
    val causas: List<String>,
    val verificaciones: List<VerificacionDtc>,
    val notasMoto: List<String>,
    val relacionados: List<String>,
)

data class VerificacionDtc(val paso: String, val detalle: String, val accion: AccionGuia?)

enum class UrgenciaDtc {
    DETENERSE,
    REVISAR_PRONTO,
    PUEDE_ESPERAR,
    ;

    fun etiqueta(resolver: StringResolver): String = resolver.get(
        when (this) {
            DETENERSE -> R.string.obd_dtc_urgency_stop
            REVISAR_PRONTO -> R.string.obd_dtc_urgency_soon
            PUEDE_ESPERAR -> R.string.obd_dtc_urgency_later
        },
    )

    val etiqueta: String get() = when (this) { // i18n-ignore
        DETENERSE -> "Detente y revisa antes de seguir rodando" // i18n-ignore
        REVISAR_PRONTO -> "Revisa pronto" // i18n-ignore
        PUEDE_ESPERAR -> "Puede esperar al próximo mantenimiento" // i18n-ignore
    }
}

sealed interface AccionGuia {
    val clave: String
    val etiqueta: String
    fun etiqueta(resolver: StringResolver): String

    data class Prueba(val tipo: TipoPrueba) : AccionGuia {
        override val clave get() = "$PREFIJO_PRUEBA${tipo.name}"
        override val etiqueta get() = "Prueba guiada: $nombre" // i18n-ignore
        override fun etiqueta(resolver: StringResolver) =
            resolver.get(R.string.obd_dtc_action_guided_test, tipo.titulo(resolver))

        // Sin resolutor (MCP y registros en español): el nombre de la prueba en español.
        val nombre: String get() = when (tipo) {
            TipoPrueba.TPS_BARRIDO -> "Barrido del TPS" // i18n-ignore
            TipoPrueba.MINIMO_RETORNO -> "Mínimo y retorno" // i18n-ignore
            TipoPrueba.ARRANQUE_FRIO -> "Arranque en frío" // i18n-ignore
            TipoPrueba.BATERIA_CARGA -> "Batería y carga" // i18n-ignore
            TipoPrueba.MAP_BARO -> "MAP contra presión barométrica" // i18n-ignore
        }
    }

    data class Multimetro(val sensor: SensorMultimetro) : AccionGuia {
        override val clave get() = "$PREFIJO_MULTIMETRO${sensor.name}"
        override val etiqueta get() = "Medir con multímetro: ${sensor.titulo}" // i18n-ignore
        override fun etiqueta(resolver: StringResolver) =
            resolver.get(R.string.obd_dtc_action_multimeter, sensor.titulo)
    }

    data object BorrarCodigos : AccionGuia {
        override val clave = "DTC:BORRAR"
        override val etiqueta = "Borrar códigos" // i18n-ignore
        override fun etiqueta(resolver: StringResolver) = resolver.get(R.string.obd_dtc_action_clear_codes)
    }

    companion object {
        private const val PREFIJO_PRUEBA = "PRUEBA:"
        private const val PREFIJO_MULTIMETRO = "MULTIMETRO:"

        fun desde(clave: String): AccionGuia? = when {
            clave == BorrarCodigos.clave -> BorrarCodigos
            clave.startsWith(PREFIJO_PRUEBA) -> prueba(clave.removePrefix(PREFIJO_PRUEBA))
            clave.startsWith(PREFIJO_MULTIMETRO) -> multimetro(clave.removePrefix(PREFIJO_MULTIMETRO))
            else -> null
        }

        private fun prueba(nombre: String): AccionGuia? =
            TipoPrueba.entries.firstOrNull { it.name == nombre }?.let(::Prueba)

        private fun multimetro(nombre: String): AccionGuia? =
            SensorMultimetro.entries.firstOrNull { it.name == nombre }?.let(::Multimetro)
    }
}
