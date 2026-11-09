package com.revscope.core.obd.taller.multimetro

import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.R
import com.revscope.core.obd.taller.referencia.ClavesBanda

object CondicionesMultimetro {
    const val CERRADO = "CERRADO"
    const val MEDIO = "MEDIO"
    const val FONDO = "FONDO"
    const val CONTACTO = "CONTACTO"
    const val DESCONECTADO = "DESCONECTADO"
    const val ARRANQUE = "ARRANQUE"
    const val CARGA = "CARGA"
}

// Plantillas típicas sin colores: los colores cambian con cada modelo y solo se muestran si vienen del modelo.
object PlantillasGenericas {

    fun para(sensor: SensorMultimetro, tipo: VehicleType, resolver: StringResolver): PlantillaCableado = when (sensor) {
        SensorMultimetro.TPS -> tps(resolver)
        SensorMultimetro.MAP -> map(resolver)
        SensorMultimetro.ECT -> temperatura(SensorMultimetro.ECT, resolver)
        SensorMultimetro.IAT -> temperatura(SensorMultimetro.IAT, resolver)
        SensorMultimetro.INYECTOR -> inyector(resolver)
        SensorMultimetro.BATERIA -> bateria(tipo, resolver)
    }

    private fun tps(resolver: StringResolver): PlantillaCableado {
        val condiciones = listOf(
            Condicion(CondicionesMultimetro.CERRADO, resolver.get(R.string.obd_multimeter_condition_closed)),
            Condicion(CondicionesMultimetro.MEDIO, resolver.get(R.string.obd_multimeter_condition_half)),
            Condicion(CondicionesMultimetro.FONDO, resolver.get(R.string.obd_multimeter_condition_full)),
        )
        val claves = condiciones.map { it.clave }
        val senal = mapOf(
            CondicionesMultimetro.CERRADO to Esperado.Banda(ClavesBanda.TPS_CERRADO_V),
            CondicionesMultimetro.MEDIO to Esperado.EntreCondiciones(CondicionesMultimetro.CERRADO, CondicionesMultimetro.FONDO),
            CondicionesMultimetro.FONDO to Esperado.Banda(ClavesBanda.TPS_FONDO_V),
        )
        return PlantillaCableado(
            sensor = SensorMultimetro.TPS,
            titulo = resolver.get(R.string.obd_multimeter_template_tps),
            cables = listOf(
                cable(FuncionCable.REF_5V, claves.associateWith { Esperado.Banda(ClavesBanda.REF_5V_V) }),
                cable(FuncionCable.MASA, claves.associateWith { Esperado.Banda(ClavesBanda.MASA_V) }),
                cable(FuncionCable.SENAL, senal),
            ),
            condiciones = condiciones,
            unidad = "V",
            dondeMedir = resolver.get(R.string.obd_multimeter_tps_instructions, resolver.get(R.string.obd_multimeter_backprobe)),
            condicionesEcu = setOf(CondicionesMultimetro.CERRADO, CondicionesMultimetro.FONDO),
        )
    }

    private fun map(resolver: StringResolver) = PlantillaCableado(
        sensor = SensorMultimetro.MAP,
        titulo = resolver.get(R.string.obd_multimeter_template_map),
        cables = listOf(
            cable(FuncionCable.REF_5V, contacto(Esperado.Banda(ClavesBanda.REF_5V_V))),
            cable(FuncionCable.MASA, contacto(Esperado.Banda(ClavesBanda.MASA_V))),
            cable(
                FuncionCable.SENAL,
                contacto(Esperado.SinValor(resolver.get(R.string.obd_multimeter_map_expected))),
            ),
        ),
        condiciones = listOf(condicionContacto(resolver)),
        unidad = "V",
        dondeMedir = resolver.get(R.string.obd_multimeter_backprobe),
    )

    private fun temperatura(sensor: SensorMultimetro, resolver: StringResolver) = PlantillaCableado(
        sensor = sensor,
        titulo = resolver.get(if (sensor == SensorMultimetro.ECT) R.string.obd_multimeter_template_ect else R.string.obd_multimeter_template_iat),
        cables = listOf(
            cable(
                FuncionCable.SENAL,
                contacto(Esperado.SinValor(resolver.get(R.string.obd_multimeter_temperature_expected))),
            ),
            cable(FuncionCable.MASA, contacto(Esperado.Banda(ClavesBanda.MASA_V))),
        ),
        condiciones = listOf(condicionContacto(resolver)),
        unidad = "V",
        dondeMedir = resolver.get(R.string.obd_multimeter_backprobe),
    )

    private fun inyector(resolver: StringResolver) = PlantillaCableado(
        sensor = SensorMultimetro.INYECTOR,
        titulo = resolver.get(R.string.obd_multimeter_template_injector),
        cables = listOf(
            Cable(
                FuncionCable.OTRO,
                color = null,
                esperadoPorCondicion = mapOf(CondicionesMultimetro.DESCONECTADO to Esperado.Banda(ClavesBanda.INYECTOR_OHM)),
                etiqueta = resolver.get(R.string.obd_multimeter_injector_coil),
            ),
        ),
        condiciones = listOf(Condicion(CondicionesMultimetro.DESCONECTADO, resolver.get(R.string.obd_multimeter_condition_unplugged))),
        unidad = "Ω",
        dondeMedir = resolver.get(R.string.obd_multimeter_injector_instructions),
    )

    private fun bateria(tipo: VehicleType, resolver: StringResolver): PlantillaCableado {
        val reposo = if (tipo == VehicleType.MOTORCYCLE) ClavesBanda.BATERIA_CONTACTO_CON_LUCES_V else ClavesBanda.BATERIA_CONTACTO_V
        return PlantillaCableado(
            sensor = SensorMultimetro.BATERIA,
            titulo = resolver.get(R.string.obd_multimeter_template_battery),
            cables = listOf(
                Cable(
                    FuncionCable.ALIMENTACION_12V,
                    color = null,
                    esperadoPorCondicion = mapOf(
                        CondicionesMultimetro.CONTACTO to Esperado.Banda(reposo),
                        CondicionesMultimetro.ARRANQUE to Esperado.Banda(ClavesBanda.ARRANQUE_MIN_V),
                        CondicionesMultimetro.CARGA to Esperado.Banda(ClavesBanda.CARGA_V),
                    ),
                    etiqueta = resolver.get(R.string.obd_multimeter_battery_terminals),
                ),
            ),
            condiciones = listOf(
                Condicion(CondicionesMultimetro.CONTACTO, resolver.get(R.string.obd_multimeter_condition_ignition)),
                Condicion(CondicionesMultimetro.ARRANQUE, resolver.get(R.string.obd_multimeter_condition_starting)),
                Condicion(CondicionesMultimetro.CARGA, resolver.get(R.string.obd_multimeter_condition_charging)),
            ),
            unidad = "V",
            dondeMedir = resolver.get(R.string.obd_multimeter_battery_instructions),
        )
    }

    private fun cable(funcion: FuncionCable, esperado: Map<String, Esperado>) = Cable(funcion, color = null, esperadoPorCondicion = esperado)

    private fun contacto(esperado: Esperado) = mapOf(CondicionesMultimetro.CONTACTO to esperado)

    private fun condicionContacto(resolver: StringResolver) =
        Condicion(CondicionesMultimetro.CONTACTO, resolver.get(R.string.obd_multimeter_condition_ignition))
}
