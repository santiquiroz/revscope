package com.revscope.feature.workshop.taller.multimetro

import com.revscope.core.obd.taller.multimetro.Cable
import com.revscope.core.obd.taller.multimetro.ContextoMultimetro
import com.revscope.core.obd.taller.multimetro.EvaluadorCelda
import com.revscope.core.obd.taller.multimetro.FuncionCable
import com.revscope.core.obd.taller.multimetro.LecturaMultimetro
import com.revscope.core.obd.taller.multimetro.PlantillaCableado
import com.revscope.core.obd.taller.multimetro.ResolutorPlantilla
import com.revscope.core.obd.taller.multimetro.SensorMultimetro
import com.revscope.core.obd.taller.multimetro.VeredictoCombinado
import com.revscope.core.obd.taller.multimetro.VeredictoMultimetro
import com.revscope.core.obd.taller.referencia.BandaReferencia

object LectorDecimal {
    // Coma o punto: el teclado decimal del teléfono en español escribe coma.
    fun leer(texto: String): Double? = texto.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
}

internal object MapeoMultimetro {

    const val COLORES_EDITADOS = "Colores editados por ti, sin guardar en el modelo"
    // En los bornes de la batería y entre los terminales del inyector no hay un cable que identificar por color.
    private val SIN_CABLES_DE_COLOR = setOf(SensorMultimetro.BATERIA, SensorMultimetro.INYECTOR)

    const val SIN_COLORES = "Plantilla genérica típica, sin colores: los colores cambian con cada modelo"

    fun ui(l: LocalMultimetro): MultimetroUi {
        val ctx = l.contexto ?: return MultimetroUi(cargando = l.cargando, error = l.error, sensor = l.sensor, mensaje = l.mensaje)
        val plantilla = plantillaEfectiva(ctx, l.colores)
        val lecturas = lecturas(plantilla, l.textos)
        val veredicto = lecturas.takeIf { it.isNotEmpty() }?.let { VeredictoMultimetro.combinar(plantilla, it, ctx.bandas, ctx.ecu) }
        return MultimetroUi(
            cargando = false,
            error = l.error,
            vehiculo = ctx.vehiculo?.nombre,
            sensor = l.sensor,
            plantilla = plantillaUi(plantilla, l.textos, ctx.bandas, veredicto, editados(ctx, l.colores)),
            veredicto = veredicto?.let(::veredictoUi),
            colores = coloresUi(ctx, plantilla, l.colores),
            guardar = guardarUi(ctx, lecturas.isNotEmpty(), l.ocupado),
            dialogoColor = l.dialogoColor,
            mensaje = l.mensaje,
        )
    }

    fun plantillaEfectiva(ctx: ContextoMultimetro, colores: Map<FuncionCable, String?>): PlantillaCableado {
        if (!editados(ctx, colores)) return ctx.plantilla
        return ResolutorPlantilla.conColores(ctx.plantilla, colores, COLORES_EDITADOS)
    }

    fun lecturas(plantilla: PlantillaCableado, textos: Map<Pair<FuncionCable, String>, String>): List<LecturaMultimetro> =
        plantilla.celdas().mapNotNull { celda ->
            textos[celda]?.let(LectorDecimal::leer)?.let { LecturaMultimetro(celda.first, celda.second, it, plantilla.unidad) }
        }

    private fun editados(ctx: ContextoMultimetro, colores: Map<FuncionCable, String?>): Boolean =
        ctx.plantilla.cables.any { normalizar(colores[it.funcion]) != normalizar(it.color) }

    private fun normalizar(color: String?): String? = color?.trim()?.takeIf(String::isNotEmpty)

    private fun plantillaUi(
        p: PlantillaCableado,
        textos: Map<Pair<FuncionCable, String>, String>,
        bandas: Map<String, BandaReferencia>,
        veredicto: VeredictoCombinado?,
        editados: Boolean,
    ) = PlantillaUi(
        titulo = p.titulo,
        dondeMedir = p.dondeMedir,
        origenColores = when {
            p.sensor in SIN_CABLES_DE_COLOR -> origenSinCables(p.sensor)
            editados -> COLORES_EDITADOS
            p.fuenteColores != null -> "Colores: ${p.fuenteColores}"
            else -> SIN_COLORES
        },
        unidad = p.unidad,
        condiciones = p.condiciones.map { it.etiqueta },
        filas = p.cables.map { fila(p, it, textos, bandas, veredicto) },
    )

    private fun origenSinCables(sensor: SensorMultimetro): String =
        if (sensor == SensorMultimetro.BATERIA) "Bandas típicas de una batería de 12 V de plomo-ácido: compáralas con el manual"
        else "Banda típica de un inyector de alta impedancia: compárala con el manual"

    private fun fila(
        p: PlantillaCableado,
        cable: Cable,
        textos: Map<Pair<FuncionCable, String>, String>,
        bandas: Map<String, BandaReferencia>,
        veredicto: VeredictoCombinado?,
    ): FilaCableUi {
        val filaMedida = lecturas(p, textos).filter { it.funcion == cable.funcion }.associate { it.condicion to it.valor }
        val celdas = p.condiciones.map { condicion ->
            val clave = cable.funcion to condicion.clave
            val texto = textos[clave].orEmpty()
            val evaluada = veredicto?.celdas?.firstOrNull { it.funcion == cable.funcion && it.condicion == condicion.clave }
            CeldaUi(
                condicion = condicion.clave,
                etiquetaCondicion = condicion.etiqueta,
                texto = texto,
                estado = evaluada?.estado,
                referencia = evaluada?.referencia ?: referenciaEsperada(p, cable, condicion.clave, filaMedida, bandas),
                invalido = texto.isNotBlank() && LectorDecimal.leer(texto) == null,
                aplica = cable.esperado(condicion.clave) != null,
            )
        }
        return FilaCableUi(cable.funcion, cable.etiqueta, cable.color, celdas)
    }

    // El texto de lo esperado no depende del valor: se evalúa uno cualquiera para citarlo antes de medir.
    private fun referenciaEsperada(
        p: PlantillaCableado,
        cable: Cable,
        condicion: String,
        fila: Map<String, Double>,
        bandas: Map<String, BandaReferencia>,
    ): String? {
        if (cable.esperado(condicion) == null) return null
        return EvaluadorCelda.evaluar(p, LecturaMultimetro(cable.funcion, condicion, 0.0, p.unidad), fila, bandas).referencia
    }

    private fun veredictoUi(v: VeredictoCombinado) = VeredictoUi(
        veredicto = v.veredicto,
        titulo = v.titulo,
        interpretacion = v.interpretacion,
        ecu = v.ecu?.let { ComparacionEcuUi(it.resultado, it.texto, it.origen) },
    )

    private fun coloresUi(ctx: ContextoMultimetro, plantilla: PlantillaCableado, colores: Map<FuncionCable, String?>) = ColoresUi(
        cables = if (plantilla.sensor in SIN_CABLES_DE_COLOR) emptyList() else plantilla.cables.map { ColorCableUi(it.funcion, it.etiqueta, it.color) },
        editados = editados(ctx, colores),
        modelo = ctx.modelo?.nombre,
    )

    private fun guardarUi(ctx: ContextoMultimetro, hayLecturas: Boolean, ocupado: Boolean): GuardarUi {
        val sesion = ctx.sesion
        val vehiculo = ctx.vehiculo ?: return GuardarUi(false, "Guardar en la sesión", "Elige el vehículo activo para guardar la medición.")
        val puede = hayLecturas && !ocupado
        val pista = if (hayLecturas) null else "Escribe al menos una medida para guardarla."
        return when {
            sesion != null -> GuardarUi(puede, "Guardar en la sesión", listOfNotNull("Sesión abierta: ${sesion.titulo}.", pista).joinToString(" "))
            else -> GuardarUi(
                puede,
                "Guardar en una sesión nueva",
                listOfNotNull("No hay una sesión abierta: se abre una para ${vehiculo.nombre}.", pista).joinToString(" "),
            )
        }
    }
}
