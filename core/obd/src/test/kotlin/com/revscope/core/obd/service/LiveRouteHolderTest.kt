package com.revscope.core.obd.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveRouteHolderTest {

    // Intervalo 0: snapshot en cada append — los tests validan el contrato, no el throttle.
    private val holder = LiveRouteHolder(snapshotIntervalMs = 0L)

    @Test
    fun `append acumula puntos en orden`() {
        holder.append(6.24, -75.58)
        holder.append(6.25, -75.59)
        assertEquals(2, holder.points.value.size)
        assertEquals(6.24, holder.points.value.first().lat, 0.0001)
    }

    @Test
    fun `clear vacia la ruta`() {
        holder.append(6.24, -75.58)
        holder.clear()
        assertTrue(holder.points.value.isEmpty())
    }

    @Test
    fun `append limita la ruta al maximo de puntos`() {
        repeat(LiveRouteHolder.MAX_POINTS + 100) { holder.append(6.0 + it * 0.0001, -75.0) }
        assertEquals(LiveRouteHolder.MAX_POINTS, holder.points.value.size)
    }

    @Test
    fun `revision avanza en cada append incluso cuando el tamano se estanca`() {
        repeat(LiveRouteHolder.MAX_POINTS) { holder.append(6.0 + it * 0.0001, -75.0) }
        val revisionAtCap = holder.revision.value
        val sizeAtCap = holder.points.value.size

        holder.append(6.99, -75.0)

        assertEquals(sizeAtCap, holder.points.value.size)
        assertTrue(holder.revision.value > revisionAtCap)
    }

    @Test
    fun `clear reinicia la revision`() {
        holder.append(6.24, -75.58)
        val revisionBeforeClear = holder.revision.value

        holder.clear()

        assertTrue(holder.revision.value < revisionBeforeClear)
    }
}
