package com.revscope.core.obd.taller.dtc

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

enum class UrgenciaDtc(val etiqueta: String) {
    DETENERSE("Detente y revisa antes de seguir rodando"),
    REVISAR_PRONTO("Revisa pronto"),
    PUEDE_ESPERAR("Puede esperar al próximo mantenimiento"),
}

sealed interface AccionGuia {
    val clave: String
    val etiqueta: String

    data class Prueba(val tipo: TipoPrueba) : AccionGuia {
        override val clave get() = "$PREFIJO_PRUEBA${tipo.name}"
        override val etiqueta get() = "Prueba guiada: ${tipo.titulo}"
    }

    data class Multimetro(val sensor: SensorMultimetro) : AccionGuia {
        override val clave get() = "$PREFIJO_MULTIMETRO${sensor.name}"
        override val etiqueta get() = "Medir con multímetro: ${sensor.titulo}"
    }

    data object BorrarCodigos : AccionGuia {
        override val clave = "DTC:BORRAR"
        override val etiqueta = "Borrar códigos"
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
