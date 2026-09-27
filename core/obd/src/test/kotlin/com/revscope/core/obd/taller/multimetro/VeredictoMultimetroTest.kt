package com.revscope.core.obd.taller.multimetro

import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.obd.taller.modelo.CableModelo
import com.revscope.core.obd.taller.modelo.CableadoSensor
import com.revscope.core.obd.taller.referencia.BandaReferencia
import com.revscope.core.obd.taller.referencia.BandasTipicas
import com.revscope.core.obd.taller.referencia.ClavesBanda
import com.revscope.core.obd.taller.referencia.OrigenBanda
import com.revscope.core.obd.taller.sesion.Veredicto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VeredictoMultimetroTest {

    private val bandas = BandasTipicas.para(VehicleType.MOTORCYCLE)
    private val cableadoBenelli = CableadoSensor(
        sensor = "TPS",
        titulo = "Sensor de posición del acelerador (3 cables)",
        cables = listOf(
            CableModelo(FuncionCable.REF_5V, "Rojo"),
            CableModelo(FuncionCable.MASA, "Negro"),
            CableModelo(FuncionCable.SENAL, "Verde-amarillo"),
        ),
        fuente = "Medición del dueño con multímetro, 25-sep-2026 (editable)",
    )
    private val tps = ResolutorPlantilla.para(SensorMultimetro.TPS, VehicleType.MOTORCYCLE, listOf(cableadoBenelli))

    // El multímetro del caso: rojo 5,0 V y negro 0 V en las tres posiciones; la señal 0,1 / 0,35 / 0,8 V.
    private val casoBenelli = listOf(CondicionesMultimetro.CERRADO, CondicionesMultimetro.MEDIO, CondicionesMultimetro.FONDO)
        .flatMap { c -> listOf(v(FuncionCable.REF_5V, c, 5.0), v(FuncionCable.MASA, c, 0.0)) } +
        listOf(
            v(FuncionCable.SENAL, CondicionesMultimetro.CERRADO, 0.1),
            v(FuncionCable.SENAL, CondicionesMultimetro.MEDIO, 0.35),
            v(FuncionCable.SENAL, CondicionesMultimetro.FONDO, 0.8),
        )

    private fun v(funcion: FuncionCable, condicion: String, valor: Double) = LecturaMultimetro(funcion, condicion, valor, "V")

    private fun celda(r: VeredictoCombinado, funcion: FuncionCable, condicion: String) =
        r.celdas.single { it.funcion == funcion && it.condicion == condicion }

    @Test
    fun `caso Benelli referencia y masa correctas y señal baja en todo el recorrido`() {
        val r = VeredictoMultimetro.combinar(tps, casoBenelli, bandas)

        assertEquals("Referencia y masa correctas; señal baja en todo el recorrido", r.titulo)
        assertEquals(Veredicto.FALLA, r.veredicto)
        assertTrue(r.interpretacion!!.startsWith("Apunta al sensor (dañado o no equivalente al original)"))
        assertTrue(r.celdas.filter { it.funcion != FuncionCable.SENAL }.all { it.estado == EstadoCelda.DENTRO })
        assertEquals(
            listOf(EstadoCelda.BAJO, EstadoCelda.BAJO, EstadoCelda.BAJO),
            listOf(CondicionesMultimetro.CERRADO, CondicionesMultimetro.MEDIO, CondicionesMultimetro.FONDO)
                .map { celda(r, FuncionCable.SENAL, it).estado },
        )
        assertNull(r.ecu)
    }

    @Test
    fun `cada celda cita su banda con el origen y la media no tiene banda fija`() {
        val r = VeredictoMultimetro.combinar(tps, casoBenelli, bandas)

        assertEquals("4,8–5,2 V · Típico (editable)", celda(r, FuncionCable.REF_5V, CondicionesMultimetro.CERRADO).referencia)
        assertEquals("0,3–1,0 V · Típico (editable)", celda(r, FuncionCable.SENAL, CondicionesMultimetro.CERRADO).referencia)
        assertTrue(celda(r, FuncionCable.SENAL, CondicionesMultimetro.MEDIO).referencia.startsWith("Sin banda fija: entre cerrado y a fondo"))
    }

    @Test
    fun `contra la ECU 0,12 y 0,89 V la ECU recibe lo mismo que el multímetro`() {
        val ecu = LecturasEcu(
            "Prueba guiada «Barrido del TPS» de esta sesión",
            mapOf(CondicionesMultimetro.CERRADO to 0.12, CondicionesMultimetro.MEDIO to 0.46, CondicionesMultimetro.FONDO to 0.89),
        )

        val r = VeredictoMultimetro.combinar(tps, casoBenelli, bandas, ecu)

        val comparacion = r.ecu!!
        assertEquals(ResultadoEcu.COINCIDE, comparacion.resultado)
        assertTrue(comparacion.texto, comparacion.texto.contains("la ECU recibe lo mismo"))
        assertEquals(listOf(CondicionesMultimetro.CERRADO, CondicionesMultimetro.FONDO), comparacion.filas.map { it.condicion })
    }

    @Test
    fun `si el sensor entrega bien y la ECU recibe menos apunta al cableado del lado de la ECU`() {
        val sano = casoBenelli.filter { it.funcion != FuncionCable.SENAL } + listOf(
            v(FuncionCable.SENAL, CondicionesMultimetro.CERRADO, 0.6),
            v(FuncionCable.SENAL, CondicionesMultimetro.MEDIO, 2.4),
            v(FuncionCable.SENAL, CondicionesMultimetro.FONDO, 4.4),
        )
        val ecu = LecturasEcu("barrido", mapOf(CondicionesMultimetro.CERRADO to 0.3, CondicionesMultimetro.FONDO to 3.9))

        val r = VeredictoMultimetro.combinar(tps, sano, bandas, ecu)

        assertEquals("Referencia y masa correctas; señal dentro de las bandas", r.titulo)
        assertEquals(Veredicto.OK, r.veredicto)
        assertEquals(ResultadoEcu.ECU_RECIBE_MENOS, r.ecu!!.resultado)
        assertTrue(r.ecu!!.texto.contains("cableado o al conector del lado de la ECU"))
    }

    @Test
    fun `una referencia baja se nombra primero porque arrastra la señal`() {
        val lecturas = listOf(
            v(FuncionCable.REF_5V, CondicionesMultimetro.CERRADO, 4.2),
            v(FuncionCable.MASA, CondicionesMultimetro.CERRADO, 0.0),
            v(FuncionCable.SENAL, CondicionesMultimetro.CERRADO, 0.2),
        )

        val r = VeredictoMultimetro.combinar(tps, lecturas, bandas)

        assertEquals("Referencia de 5 V baja y masa correcta; señal baja (cerrado)", r.titulo)
        assertTrue(r.interpretacion!!.contains("alimentación de 5 V"))
    }

    @Test
    fun `solo la señal medida pide medir también referencia y masa`() {
        val lecturas = casoBenelli.filter { it.funcion == FuncionCable.SENAL }

        val r = VeredictoMultimetro.combinar(tps, lecturas, bandas)

        assertEquals("Señal baja en todo el recorrido", r.titulo)
        assertTrue(r.interpretacion!!.startsWith("Mide también la referencia y la masa"))
    }

    @Test
    fun `la banda editada por el técnico cambia el veredicto de la celda`() {
        val propias = bandas + (ClavesBanda.TPS_CERRADO_V to BandaReferencia(ClavesBanda.TPS_CERRADO_V, 0.05, 0.2, "V", OrigenBanda.USUARIO))

        val r = VeredictoMultimetro.combinar(tps, casoBenelli, propias)

        val cerrado = celda(r, FuncionCable.SENAL, CondicionesMultimetro.CERRADO)
        assertEquals(EstadoCelda.DENTRO, cerrado.estado)
        assertTrue(cerrado.referencia.endsWith("Editado por ti"))
        assertEquals(EstadoCelda.DENTRO, celda(r, FuncionCable.SENAL, CondicionesMultimetro.MEDIO).estado)
        assertEquals("Referencia y masa correctas; señal baja (a fondo)", r.titulo)
    }

    @Test
    fun `la señal del ECT no tiene valor esperado y lo dice`() {
        val ect = PlantillasGenericas.para(SensorMultimetro.ECT, VehicleType.MOTORCYCLE)
        val lecturas = listOf(v(FuncionCable.SENAL, CondicionesMultimetro.CONTACTO, 2.1), v(FuncionCable.MASA, CondicionesMultimetro.CONTACTO, 0.02))

        val r = VeredictoMultimetro.combinar(ect, lecturas, bandas)

        assertEquals("Masa correcta; señal sin valor esperado: compárala con el manual", r.titulo)
        assertEquals(EstadoCelda.SIN_REFERENCIA, celda(r, FuncionCable.SENAL, CondicionesMultimetro.CONTACTO).estado)
        assertEquals(Veredicto.OK, r.veredicto)
    }

    @Test
    fun `inyector de 10-16 ohmios típico y batería con valle bajo al arrancar`() {
        val inyector = PlantillasGenericas.para(SensorMultimetro.INYECTOR, VehicleType.MOTORCYCLE)
        val ok = VeredictoMultimetro.combinar(
            inyector,
            listOf(LecturaMultimetro(FuncionCable.OTRO, CondicionesMultimetro.DESCONECTADO, 12.5, "Ω")),
            bandas,
        )
        assertEquals("Todo lo medido está dentro de las bandas", ok.titulo)
        assertEquals("10–16 Ω · Típico (editable)", ok.celdas.single().referencia)

        val bateria = PlantillasGenericas.para(SensorMultimetro.BATERIA, VehicleType.MOTORCYCLE)
        val r = VeredictoMultimetro.combinar(
            bateria,
            listOf(
                v(FuncionCable.ALIMENTACION_12V, CondicionesMultimetro.CONTACTO, 12.2),
                v(FuncionCable.ALIMENTACION_12V, CondicionesMultimetro.ARRANQUE, 8.9),
                v(FuncionCable.ALIMENTACION_12V, CondicionesMultimetro.CARGA, 14.2),
            ),
            bandas,
        )
        assertEquals("Fuera de banda: bornes de la batería, al arrancar (bajo)", r.titulo)
        assertTrue(r.interpretacion!!.startsWith("Caída fuerte al arrancar"))
    }

    @Test
    fun `sin medidas no hay veredicto ni comparación`() {
        val r = VeredictoMultimetro.combinar(tps, emptyList(), bandas)

        assertEquals("Sin medidas todavía", r.titulo)
        assertEquals(Veredicto.INFO, r.veredicto)
        assertNull(r.interpretacion)
    }
}
