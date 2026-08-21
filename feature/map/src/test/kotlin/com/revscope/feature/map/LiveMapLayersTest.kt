package com.revscope.feature.map

import com.revscope.core.data.db.entities.VehicleType
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveMapLayersTest {

    @Test
    fun `moto elige el icono de moto`() {
        assertEquals(ICON_ME_MOTO, puckIcon(VehicleType.MOTORCYCLE))
    }

    @Test
    fun `auto elige el icono de auto`() {
        assertEquals(ICON_ME_AUTO, puckIcon(VehicleType.CAR))
    }

    @Test
    fun `sin perfil configurado cae al dot plano`() {
        assertEquals(ICON_ME, puckIcon(null))
    }
}
