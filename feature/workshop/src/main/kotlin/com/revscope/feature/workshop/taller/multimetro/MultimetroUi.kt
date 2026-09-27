package com.revscope.feature.workshop.taller.multimetro

import com.revscope.core.obd.taller.multimetro.ContextoMultimetro
import com.revscope.core.obd.taller.multimetro.EstadoCelda
import com.revscope.core.obd.taller.multimetro.FuncionCable
import com.revscope.core.obd.taller.multimetro.PlantillaCableado
import com.revscope.core.obd.taller.multimetro.ResultadoEcu
import com.revscope.core.obd.taller.multimetro.SensorMultimetro
import com.revscope.core.obd.taller.sesion.Veredicto

data class MultimetroUi(
    val cargando: Boolean = true,
    val error: String? = null,
    val vehiculo: String? = null,
    val sensor: SensorMultimetro = SensorMultimetro.TPS,
    val plantilla: PlantillaUi? = null,
    val veredicto: VeredictoUi? = null,
    val colores: ColoresUi = ColoresUi(),
    val guardar: GuardarUi = GuardarUi(),
    val dialogoColor: DialogoColorUi? = null,
    val mensaje: String? = null,
) {
    val sensores: List<SensorMultimetro> get() = SensorMultimetro.entries
}

data class PlantillaUi(
    val titulo: String,
    val dondeMedir: String,
    val origenColores: String,
    val unidad: String,
    val condiciones: List<String>,
    val filas: List<FilaCableUi>,
)

data class FilaCableUi(val funcion: FuncionCable, val etiqueta: String, val color: String?, val celdas: List<CeldaUi>)

// [aplica] = false: el cable no se mide en esa condición (la celda queda vacía en la tabla).
data class CeldaUi(
    val condicion: String,
    val etiquetaCondicion: String,
    val texto: String,
    val estado: EstadoCelda?,
    val referencia: String?,
    val invalido: Boolean = false,
    val aplica: Boolean = true,
)

data class VeredictoUi(
    val veredicto: Veredicto,
    val titulo: String,
    val interpretacion: String?,
    val ecu: ComparacionEcuUi?,
)

data class ComparacionEcuUi(val resultado: ResultadoEcu, val texto: String, val origen: String)

data class ColoresUi(
    val cables: List<ColorCableUi> = emptyList(),
    val editados: Boolean = false,
    val modelo: String? = null,
) {
    val puedeGuardar: Boolean get() = editados && modelo != null
}

data class ColorCableUi(val funcion: FuncionCable, val etiqueta: String, val color: String?)

data class GuardarUi(val habilitado: Boolean = false, val texto: String = "Guardar en la sesión", val detalle: String? = null)

data class DialogoColorUi(val funcion: FuncionCable, val etiqueta: String, val texto: String)

// Lo que el ViewModel guarda: el contexto del núcleo y lo que el técnico escribe.
internal data class LocalMultimetro(
    val sensor: SensorMultimetro,
    val contexto: ContextoMultimetro? = null,
    val cargando: Boolean = true,
    val error: String? = null,
    val textos: Map<Pair<FuncionCable, String>, String> = emptyMap(),
    val colores: Map<FuncionCable, String?> = emptyMap(),
    val dialogoColor: DialogoColorUi? = null,
    val ocupado: Boolean = false,
    val mensaje: String? = null,
)

internal fun PlantillaCableado.colores(): Map<FuncionCable, String?> = cables.associate { it.funcion to it.color }
