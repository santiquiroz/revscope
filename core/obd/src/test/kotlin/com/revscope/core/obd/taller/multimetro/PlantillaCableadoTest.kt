package com.revscope.core.obd.taller.multimetro

import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.obd.taller.modelo.CableModelo
import com.revscope.core.obd.taller.modelo.CableadoSensor
import com.revscope.core.obd.taller.referencia.BandasTipicas
import com.revscope.core.obd.taller.referencia.ClavesBanda
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlantillaCableadoTest {

    private val benelliTps = CableadoSensor(
        sensor = "TPS",
        titulo = "Sensor de posición del acelerador (3 cables)",
        cables = listOf(
            CableModelo(FuncionCable.REF_5V, "Rojo"),
            CableModelo(FuncionCable.MASA, "Negro"),
            CableModelo(FuncionCable.SENAL, "Verde-amarillo"),
        ),
        fuente = "Medición del dueño con multímetro, 25-sep-2026 (editable)",
    )

    @Test
    fun `sin plantilla del modelo se usa la genérica sin colores`() {
        val p = ResolutorPlantilla.para(SensorMultimetro.TPS, VehicleType.MOTORCYCLE, emptyList())

        assertEquals(listOf(FuncionCable.REF_5V, FuncionCable.MASA, FuncionCable.SENAL), p.cables.map { it.funcion })
        assertTrue(p.cables.all { it.color == null })
        assertFalse(p.conColores)
        assertNull(p.fuenteColores)
    }

    @Test
    fun `el cableado de otro sensor del modelo no se aplica`() {
        val p = ResolutorPlantilla.para(SensorMultimetro.MAP, VehicleType.MOTORCYCLE, listOf(benelliTps))

        assertFalse(p.conColores)
    }

    @Test
    fun `la plantilla del modelo pone los colores y cita su fuente sin cambiar lo esperado`() {
        val generica = PlantillasGenericas.para(SensorMultimetro.TPS, VehicleType.MOTORCYCLE)

        val p = ResolutorPlantilla.para(SensorMultimetro.TPS, VehicleType.MOTORCYCLE, listOf(benelliTps))

        assertEquals(listOf("Rojo", "Negro", "Verde-amarillo"), p.cables.map { it.color })
        assertEquals("Medición del dueño con multímetro, 25-sep-2026 (editable)", p.fuenteColores)
        assertEquals(generica.cables.map { it.esperadoPorCondicion }, p.cables.map { it.esperadoPorCondicion })
    }

    @Test
    fun `guardar colores arma el cableado editado y reemplaza solo el de ese sensor`() {
        val tps = PlantillasGenericas.para(SensorMultimetro.TPS, VehicleType.MOTORCYCLE)
        val mapModelo = benelliTps.copy(sensor = "MAP")

        val nuevo = ResolutorPlantilla.aCableado(tps, mapOf(FuncionCable.REF_5V to " Rojo ", FuncionCable.SENAL to "Verde", FuncionCable.MASA to ""))

        assertEquals(listOf("Rojo", null, "Verde"), nuevo.cables.map { it.color })
        assertEquals(ResolutorPlantilla.FUENTE_EDITADA, nuevo.fuente)
        assertEquals(listOf("MAP", "TPS"), ResolutorPlantilla.reemplazar(listOf(benelliTps, mapModelo), nuevo).map { it.sensor })
    }

    @Test
    fun `las plantillas genéricas tienen bandas típicas existentes para cada celda con banda`() {
        val bandas = BandasTipicas.para(VehicleType.MOTORCYCLE)
        SensorMultimetro.entries.forEach { sensor ->
            val p = PlantillasGenericas.para(sensor, VehicleType.MOTORCYCLE)
            p.cables.flatMap { it.esperadoPorCondicion.values }.filterIsInstance<Esperado.Banda>().forEach {
                assertTrue("$sensor usa ${it.clave}", it.clave in bandas)
            }
            assertTrue("$sensor sin dónde medir", p.dondeMedir.isNotBlank())
        }
    }

    @Test
    fun `la batería de moto espera el reposo con la farola encendida y la de carro sin cargas`() {
        fun reposo(tipo: VehicleType) = (PlantillasGenericas.para(SensorMultimetro.BATERIA, tipo).cables.single()
            .esperado(CondicionesMultimetro.CONTACTO) as Esperado.Banda).clave

        assertEquals(ClavesBanda.BATERIA_CONTACTO_CON_LUCES_V, reposo(VehicleType.MOTORCYCLE))
        assertEquals(ClavesBanda.BATERIA_CONTACTO_V, reposo(VehicleType.CAR))
    }

    @Test
    fun `el inyector se mide en ohmios y el TPS se compara con la ECU en cerrado y a fondo`() {
        assertEquals("Ω", PlantillasGenericas.para(SensorMultimetro.INYECTOR, VehicleType.MOTORCYCLE).unidad)
        assertEquals(
            setOf(CondicionesMultimetro.CERRADO, CondicionesMultimetro.FONDO),
            PlantillasGenericas.para(SensorMultimetro.TPS, VehicleType.MOTORCYCLE).condicionesEcu,
        )
    }

    @Test
    fun `validar rechaza cable, condición y unidad que no son de la plantilla y lista los pendientes`() {
        val tps = PlantillasGenericas.para(SensorMultimetro.TPS, VehicleType.MOTORCYCLE)

        assertTrue(AsistenteMultimetro.validar(tps, LecturaMultimetro(FuncionCable.CALEFACTOR, "CERRADO", 1.0, "V"))!!.contains("REF_5V"))
        assertTrue(AsistenteMultimetro.validar(tps, LecturaMultimetro(FuncionCable.SENAL, "CONTACTO", 1.0, "V"))!!.contains("CERRADO"))
        assertTrue(AsistenteMultimetro.validar(tps, LecturaMultimetro(FuncionCable.SENAL, "CERRADO", 1.0, "Ω"))!!.contains("en V"))
        assertNull(AsistenteMultimetro.validar(tps, LecturaMultimetro(FuncionCable.SENAL, "CERRADO", 0.1, "V")))

        val pendientes = AsistenteMultimetro.pendientes(tps, listOf(LecturaMultimetro(FuncionCable.SENAL, "CERRADO", 0.1, "V")))
        assertEquals(8, pendientes.size)
    }
}
