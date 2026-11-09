package com.revscope.feature.workshop.taller.multimetro

import com.revscope.core.common.i18n.StringResolver
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
import com.revscope.feature.workshop.R

object LectorDecimal {
    // Coma o punto: el teclado decimal del teléfono en español escribe coma.
    fun leer(texto: String): Double? = texto.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
}

internal object MapeoMultimetro {

    // En los bornes de la batería y entre los terminales del inyector no hay un cable que identificar por color.
    private val SIN_CABLES_DE_COLOR = setOf(SensorMultimetro.BATERIA, SensorMultimetro.INYECTOR)

    fun ui(l: LocalMultimetro, resolver: StringResolver): MultimetroUi {
        val ctx = l.contexto ?: return sinContexto(l, resolver)
        val plantilla = plantillaEfectiva(ctx, l.colores, resolver)
        val lecturas = lecturas(plantilla, l.textos)
        val veredicto = lecturas.takeIf { it.isNotEmpty() }
            ?.let { VeredictoMultimetro.combinar(plantilla, it, ctx.bandas, ctx.ecu, resolver) }
        return MultimetroUi(
            cargando = false,
            error = l.error,
            vehiculo = ctx.vehiculo?.nombre,
            sensor = l.sensor,
            plantilla = plantillaUi(plantilla, l.textos, ctx.bandas, veredicto, editados(ctx, l.colores), resolver),
            veredicto = veredicto?.let(::veredictoUi),
            colores = coloresUi(ctx, plantilla, l.colores),
            guardar = guardarUi(ctx, lecturas.isNotEmpty(), l.ocupado, resolver),
            dialogoColor = l.dialogoColor,
            mensaje = l.mensaje,
        )
    }

    private fun sinContexto(l: LocalMultimetro, resolver: StringResolver) = MultimetroUi(
        cargando = l.cargando,
        error = l.error,
        sensor = l.sensor,
        guardar = GuardarUi(texto = resolver.get(R.string.workshop_multimetro_save_session)),
        mensaje = l.mensaje,
    )

    fun plantillaEfectiva(ctx: ContextoMultimetro, colores: Map<FuncionCable, String?>, resolver: StringResolver): PlantillaCableado {
        if (!editados(ctx, colores)) return ctx.plantilla
        return ResolutorPlantilla.conColores(ctx.plantilla, colores, resolver.get(R.string.workshop_multimetro_colors_edited))
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
        resolver: StringResolver,
    ) = PlantillaUi(
        titulo = p.titulo,
        dondeMedir = p.dondeMedir,
        origenColores = origenColores(p, editados, resolver),
        unidad = p.unidad,
        condiciones = p.condiciones.map { it.etiqueta },
        filas = p.cables.map { fila(p, it, textos, bandas, veredicto, resolver) },
    )

    private fun origenColores(p: PlantillaCableado, editados: Boolean, resolver: StringResolver): String {
        val fuente = p.fuenteColores
        return when {
            p.sensor in SIN_CABLES_DE_COLOR -> resolver.get(origenSinCables(p.sensor))
            editados -> resolver.get(R.string.workshop_multimetro_colors_edited)
            fuente != null -> resolver.get(R.string.workshop_multimetro_colors_source, fuente)
            else -> resolver.get(R.string.workshop_multimetro_colors_generic)
        }
    }

    private fun origenSinCables(sensor: SensorMultimetro): Int =
        if (sensor == SensorMultimetro.BATERIA) R.string.workshop_multimetro_battery_bands
        else R.string.workshop_multimetro_injector_band

    private fun fila(
        p: PlantillaCableado,
        cable: Cable,
        textos: Map<Pair<FuncionCable, String>, String>,
        bandas: Map<String, BandaReferencia>,
        veredicto: VeredictoCombinado?,
        resolver: StringResolver,
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
                referencia = evaluada?.referencia ?: referenciaEsperada(p, cable, condicion.clave, filaMedida, bandas, resolver),
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
        resolver: StringResolver,
    ): String? {
        if (cable.esperado(condicion) == null) return null
        return EvaluadorCelda.evaluar(p, LecturaMultimetro(cable.funcion, condicion, 0.0, p.unidad), fila, bandas, resolver).referencia
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

    private fun guardarUi(ctx: ContextoMultimetro, hayLecturas: Boolean, ocupado: Boolean, resolver: StringResolver): GuardarUi {
        val sesion = ctx.sesion
        val enSesion = resolver.get(R.string.workshop_multimetro_save_session)
        val vehiculo = ctx.vehiculo
            ?: return GuardarUi(false, enSesion, resolver.get(R.string.workshop_multimetro_choose_vehicle))
        val puede = hayLecturas && !ocupado
        val pista = if (hayLecturas) null else resolver.get(R.string.workshop_multimetro_enter_reading_hint)
        return when {
            sesion != null -> GuardarUi(
                puede,
                enSesion,
                listOfNotNull(resolver.get(R.string.workshop_multimetro_session_open, sesion.titulo), pista).joinToString(" "),
            )
            else -> GuardarUi(
                puede,
                resolver.get(R.string.workshop_multimetro_save_new_session),
                listOfNotNull(resolver.get(R.string.workshop_multimetro_session_will_open, vehiculo.nombre), pista).joinToString(" "),
            )
        }
    }
}
