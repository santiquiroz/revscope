package com.revscope.core.data.secure

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class SecureStoreRecoveryTest {

    private val store = Any()

    private fun failingTimes(failures: Int): () -> Any {
        var calls = 0
        return {
            calls++
            if (calls <= failures) throw IllegalStateException("keyset ilegible #$calls")
            store
        }
    }

    @Test
    fun `abre a la primera sin borrar nada`() {
        var wipes = 0

        val opened = openWithRecovery(create = failingTimes(0), wipe = { wipes++ })

        assertSame(store, opened)
        assertEquals(0, wipes)
    }

    @Test
    fun `si el creador falla una vez borra el almacen y reintenta`() {
        var wipes = 0

        val opened = openWithRecovery(create = failingTimes(1), wipe = { wipes++ })

        assertSame(store, opened)
        assertEquals(1, wipes)
    }

    @Test
    fun `si el creador falla dos veces devuelve null tras un solo borrado`() {
        var wipes = 0

        val opened = openWithRecovery(create = failingTimes(2), wipe = { wipes++ })

        assertNull(opened)
        assertEquals(1, wipes)
    }

    @Test
    fun `si el borrado tambien falla devuelve null sin lanzar`() {
        val opened = openWithRecovery(
            create = failingTimes(1),
            wipe = { throw SecurityException("sin permiso") },
        )

        assertNull(opened)
    }
}
