package com.revscope.core.obd.taller.multimetro

import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.R
import com.revscope.core.obd.taller.referencia.BandaReferencia
import com.revscope.core.obd.taller.sesion.OrigenEvento
import com.revscope.core.obd.taller.sesion.RegistroTaller
import com.revscope.core.obd.taller.sesion.SesionTaller
import com.revscope.core.obd.taller.sesion.TallerRepository
import com.revscope.core.obd.taller.sesion.VehiculoActivo
import com.revscope.core.obd.taller.sesion.VehiculoTaller
import javax.inject.Inject

data class ModeloReferencia(val clave: String, val nombre: String)

data class ContextoMultimetro(
    val vehiculo: VehiculoTaller?,
    val modelo: ModeloReferencia?,
    val plantilla: PlantillaCableado,
    val bandas: Map<String, BandaReferencia>,
    val sesion: SesionTaller?,
    val lecturasSesion: List<LecturaMultimetro>,
    val ecu: LecturasEcu?,
)

data class CeldaRegistrada(
    val sesionId: Long,
    val eventoId: Long,
    val plantilla: PlantillaCableado,
    val celda: EvaluacionCelda,
    val acumulado: VeredictoCombinado,
    val pendientes: List<Pair<FuncionCable, String>>,
)

// Lo que comparten la pantalla del multímetro y la tool registrar_medicion: la plantilla del modelo (o la
// genérica), las bandas del vehículo, lo ya medido en la sesión y la prueba guiada contra la que se compara.
class AsistenteMultimetro @Inject constructor(
    private val repositorio: TallerRepository,
    private val vehiculo: VehiculoActivo,
    private val registro: RegistroTaller,
    private val resolver: StringResolver,
) {

    suspend fun contexto(sensor: SensorMultimetro): ContextoMultimetro {
        val actual = vehiculo.actual()
        val tipo = actual?.tipo ?: VehicleType.MOTORCYCLE
        val conocimiento = actual?.claveModelo?.let { repositorio.conocimiento(it) }
        val sesion = actual?.let { repositorio.sesionAbierta(it.id) }
        val eventos = sesion?.let { repositorio.eventos(it.id) }.orEmpty()
        return ContextoMultimetro(
            vehiculo = actual,
            modelo = conocimiento?.let { ModeloReferencia(it.clave, it.nombre) },
            plantilla = ResolutorPlantilla.para(sensor, tipo, conocimiento?.cableado.orEmpty(), resolver),
            bandas = repositorio.bandasResueltas(actual?.claveModelo, tipo),
            sesion = sesion,
            lecturasSesion = LecturasSesion.de(eventos, sensor),
            ecu = LecturasEcuSesion.desde(eventos, sensor, resolver),
        )
    }

    suspend fun registrarCelda(sensor: SensorMultimetro, lectura: LecturaMultimetro, origen: OrigenEvento): Result<CeldaRegistrada> {
        val ctx = contexto(sensor)
        val sesion = ctx.sesion ?: return Result.failure(IllegalStateException(resolver.get(R.string.obd_multimeter_unnamed_session)))
        validar(ctx.plantilla, lectura, resolver)?.let { return Result.failure(IllegalArgumentException(it)) }
        val todas = LecturasSesion.reemplazar(ctx.lecturasSesion, lectura)
        val acumulado = VeredictoMultimetro.combinar(ctx.plantilla, todas, ctx.bandas, ctx.ecu, resolver)
        val celda = EvaluadorCelda.evaluar(ctx.plantilla, lectura, EvaluadorCelda.filaDe(todas, lectura.funcion), ctx.bandas, resolver)
        val eventoId = registro.anotar(EventoMedicion.celda(ctx.plantilla, celda, acumulado, origen, resolver))
            ?: return Result.failure(IllegalStateException(resolver.get(R.string.obd_multimeter_save_failed)))
        return Result.success(CeldaRegistrada(sesion.id, eventoId, ctx.plantilla, celda, acumulado, pendientes(ctx.plantilla, todas)))
    }

    suspend fun guardarTabla(plantilla: PlantillaCableado, veredicto: VeredictoCombinado): Long? =
        registro.anotar(EventoMedicion.tabla(plantilla, veredicto, resolver = resolver))

    suspend fun guardarColores(plantilla: PlantillaCableado, colores: Map<FuncionCable, String?>): Boolean {
        val clave = vehiculo.actual()?.claveModelo ?: return false
        return repositorio.guardarCableado(clave, ResolutorPlantilla.aCableado(plantilla, colores))
    }

    companion object {
        const val SIN_SESION = "No hay sesión de taller abierta: ábrela con iniciar_sesion_taller" // i18n-ignore

        fun validar(plantilla: PlantillaCableado, lectura: LecturaMultimetro, resolver: StringResolver): String? {
            val cable = plantilla.cable(lectura.funcion)
                ?: return resolver.get(
                    R.string.obd_multimeter_missing_wire,
                    plantilla.sensor.name,
                    lectura.funcion.name,
                    plantilla.cables.joinToString { it.funcion.name },
                )
            if (cable.esperado(lectura.condicion) == null) {
                return resolver.get(
                    R.string.obd_multimeter_missing_condition,
                    lectura.condicion,
                    lectura.funcion.name,
                    plantilla.condiciones.filter { cable.esperado(it.clave) != null }.joinToString { it.clave },
                )
            }
            if (lectura.unidad != plantilla.unidad) {
                return resolver.get(R.string.obd_multimeter_wrong_unit, plantilla.sensor.name, plantilla.unidad)
            }
            if (!lectura.valor.isFinite()) return resolver.get(R.string.obd_multimeter_not_number)
            return null
        }

        fun pendientes(plantilla: PlantillaCableado, lecturas: List<LecturaMultimetro>): List<Pair<FuncionCable, String>> {
            val medidas = lecturas.map { it.funcion to it.condicion }.toSet()
            return plantilla.celdas().filterNot { it in medidas }
        }
    }
}
