package com.revscope.feature.vehicle

import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.obd.taller.modelo.ResumenModelo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModeloReferenciaValidoTest {

    private val modelos = listOf(
        ResumenModelo(
            clave = "benelli-tnt150i-2022",
            nombre = "Benelli TNT 150i (2022)",
            tipo = VehicleType.MOTORCYCLE,
        ),
        ResumenModelo(
            clave = "mazda-cx30-2024",
            nombre = "Mazda CX-30 (2024)",
            tipo = VehicleType.CAR,
        ),
    )

    @Test
    fun `conserva una seleccion vacia`() {
        assertNull(modeloReferenciaValido(null, "MOTORCYCLE", modelos))
    }

    @Test
    fun `conserva una clave existente compatible con el tipo`() {
        assertEquals(
            "benelli-tnt150i-2022",
            modeloReferenciaValido("benelli-tnt150i-2022", "MOTORCYCLE", modelos),
        )
    }

    @Test
    fun `descarta una clave desconocida`() {
        assertNull(modeloReferenciaValido("modelo-inexistente", "MOTORCYCLE", modelos))
    }

    @Test
    fun `descarta una clave incompatible con el tipo de vehiculo`() {
        assertNull(modeloReferenciaValido("benelli-tnt150i-2022", "CAR", modelos))
    }

    @Test
    fun `conserva la clave al guardar mientras los modelos no se han cargado`() {
        assertEquals(
            "benelli-tnt150i-2022",
            claveModeloSegunEstado(
                "benelli-tnt150i-2022",
                "MOTORCYCLE",
                EstadoCargaModelosReferencia.Cargando,
            ),
        )
    }

    @Test
    fun `conserva la clave si fallo la carga de modelos`() {
        assertEquals(
            "benelli-tnt150i-2022",
            claveModeloSegunEstado(
                "benelli-tnt150i-2022",
                "MOTORCYCLE",
                EstadoCargaModelosReferencia.Fallo,
            ),
        )
    }

    @Test
    fun `una lista vacia cargada descarta una clave inexistente`() {
        assertNull(
            claveModeloSegunEstado(
                "benelli-tnt150i-2022",
                "MOTORCYCLE",
                EstadoCargaModelosReferencia.Cargados(emptyList()),
            ),
        )
    }

    @Test
    fun `al cargar modelos valida la clave contra el tipo`() {
        assertNull(
            claveModeloSegunEstado(
                "benelli-tnt150i-2022",
                "CAR",
                EstadoCargaModelosReferencia.Cargados(modelos),
            ),
        )
    }
}
