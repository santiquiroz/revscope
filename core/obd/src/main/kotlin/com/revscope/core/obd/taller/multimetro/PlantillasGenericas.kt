package com.revscope.core.obd.taller.multimetro

import com.revscope.core.data.db.entities.VehicleType
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

    private const val POR_DETRAS =
        "Por detrás del conector, sin desconectarlo, con el contacto puesto y el motor apagado: punta negra al " +
            "negativo de la batería y punta roja en cada cable."

    fun para(sensor: SensorMultimetro, tipo: VehicleType): PlantillaCableado = when (sensor) {
        SensorMultimetro.TPS -> tps()
        SensorMultimetro.MAP -> map()
        SensorMultimetro.ECT -> temperatura(SensorMultimetro.ECT)
        SensorMultimetro.IAT -> temperatura(SensorMultimetro.IAT)
        SensorMultimetro.INYECTOR -> inyector()
        SensorMultimetro.BATERIA -> bateria(tipo)
    }

    private fun tps(): PlantillaCableado {
        val condiciones = listOf(
            Condicion(CondicionesMultimetro.CERRADO, "Cerrado"),
            Condicion(CondicionesMultimetro.MEDIO, "Medio"),
            Condicion(CondicionesMultimetro.FONDO, "A fondo"),
        )
        val claves = condiciones.map { it.clave }
        val senal = mapOf(
            CondicionesMultimetro.CERRADO to Esperado.Banda(ClavesBanda.TPS_CERRADO_V),
            CondicionesMultimetro.MEDIO to Esperado.EntreCondiciones(CondicionesMultimetro.CERRADO, CondicionesMultimetro.FONDO),
            CondicionesMultimetro.FONDO to Esperado.Banda(ClavesBanda.TPS_FONDO_V),
        )
        return PlantillaCableado(
            sensor = SensorMultimetro.TPS,
            titulo = "TPS de 3 cables",
            cables = listOf(
                cable(FuncionCable.REF_5V, claves.associateWith { Esperado.Banda(ClavesBanda.REF_5V_V) }),
                cable(FuncionCable.MASA, claves.associateWith { Esperado.Banda(ClavesBanda.MASA_V) }),
                cable(FuncionCable.SENAL, senal),
            ),
            condiciones = condiciones,
            unidad = "V",
            dondeMedir = "$POR_DETRAS Lleva el acelerador a cada posición y sostenlo mientras lees.",
            condicionesEcu = setOf(CondicionesMultimetro.CERRADO, CondicionesMultimetro.FONDO),
        )
    }

    private fun map() = PlantillaCableado(
        sensor = SensorMultimetro.MAP,
        titulo = "MAP de 3 cables",
        cables = listOf(
            cable(FuncionCable.REF_5V, contacto(Esperado.Banda(ClavesBanda.REF_5V_V))),
            cable(FuncionCable.MASA, contacto(Esperado.Banda(ClavesBanda.MASA_V))),
            cable(
                FuncionCable.SENAL,
                contacto(Esperado.SinValor("Proporcional a la presión barométrica: compárala con la prueba del MAP")),
            ),
        ),
        condiciones = listOf(condicionContacto()),
        unidad = "V",
        dondeMedir = POR_DETRAS,
    )

    private fun temperatura(sensor: SensorMultimetro) = PlantillaCableado(
        sensor = sensor,
        titulo = if (sensor == SensorMultimetro.ECT) "Temperatura del motor de 2 cables" else "Temperatura del aire de 2 cables",
        cables = listOf(
            cable(
                FuncionCable.SENAL,
                contacto(Esperado.SinValor("Depende de la temperatura: compárala con la tabla del manual")),
            ),
            cable(FuncionCable.MASA, contacto(Esperado.Banda(ClavesBanda.MASA_V))),
        ),
        condiciones = listOf(condicionContacto()),
        unidad = "V",
        dondeMedir = POR_DETRAS,
    )

    private fun inyector() = PlantillaCableado(
        sensor = SensorMultimetro.INYECTOR,
        titulo = "Resistencia del inyector",
        cables = listOf(
            Cable(
                FuncionCable.OTRO,
                color = null,
                esperadoPorCondicion = mapOf(CondicionesMultimetro.DESCONECTADO to Esperado.Banda(ClavesBanda.INYECTOR_OHM)),
                etiqueta = "Bobina (entre terminales)",
            ),
        ),
        condiciones = listOf(Condicion(CondicionesMultimetro.DESCONECTADO, "Desconectado")),
        unidad = "Ω",
        dondeMedir = "Contacto quitado y el conector del inyector desconectado: multímetro en ohmios entre los dos " +
            "terminales del inyector.",
    )

    private fun bateria(tipo: VehicleType): PlantillaCableado {
        val reposo = if (tipo == VehicleType.MOTORCYCLE) ClavesBanda.BATERIA_CONTACTO_CON_LUCES_V else ClavesBanda.BATERIA_CONTACTO_V
        return PlantillaCableado(
            sensor = SensorMultimetro.BATERIA,
            titulo = "Batería y carga",
            cables = listOf(
                Cable(
                    FuncionCable.ALIMENTACION_12V,
                    color = null,
                    esperadoPorCondicion = mapOf(
                        CondicionesMultimetro.CONTACTO to Esperado.Banda(reposo),
                        CondicionesMultimetro.ARRANQUE to Esperado.Banda(ClavesBanda.ARRANQUE_MIN_V),
                        CondicionesMultimetro.CARGA to Esperado.Banda(ClavesBanda.CARGA_V),
                    ),
                    etiqueta = "Bornes de la batería",
                ),
            ),
            condiciones = listOf(
                Condicion(CondicionesMultimetro.CONTACTO, "Contacto"),
                Condicion(CondicionesMultimetro.ARRANQUE, "Al arrancar"),
                Condicion(CondicionesMultimetro.CARGA, "Motor a 3 000 rpm"),
            ),
            unidad = "V",
            dondeMedir = "En los bornes de la batería: punta roja al positivo y negra al negativo. Al arrancar anota " +
                "el valor más bajo que veas.",
        )
    }

    private fun cable(funcion: FuncionCable, esperado: Map<String, Esperado>) = Cable(funcion, color = null, esperadoPorCondicion = esperado)

    private fun contacto(esperado: Esperado) = mapOf(CondicionesMultimetro.CONTACTO to esperado)

    private fun condicionContacto() = Condicion(CondicionesMultimetro.CONTACTO, "Contacto")
}
