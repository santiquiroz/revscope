package com.revscope.feature.map

import org.junit.Assert.assertEquals
import org.junit.Test

class TilesFailureClassifierTest {

    @Test
    fun `tag remoto degrada aunque el estado actual ya haya avanzado a local`() {
        // Caso real del bug (fix W1 CRITICAL): la descarga completa mid-flight del tier remoto
        // hace que la composición avance a LOCAL antes de que llegue el fallo tardío del load
        // REMOTE. classifyTilesFailure es deliberadamente ciega a ese "estado actual" — solo
        // mira el tag que MapLibreMapView correlacionó con el estilo que de verdad falló.
        assertEquals(TilesFailureAction.DEGRADE_REMOTE, classifyTilesFailure(TilesTier.REMOTE))
    }

    @Test
    fun `tag local borra el pmtiles corrupto`() {
        assertEquals(TilesFailureAction.DELETE_LOCAL, classifyTilesFailure(TilesTier.LOCAL))
    }

    @Test
    fun `tag none o sin correlacion no hacen nada`() {
        assertEquals(TilesFailureAction.IGNORE, classifyTilesFailure(TilesTier.NONE))
        assertEquals(TilesFailureAction.IGNORE, classifyTilesFailure(null))
    }

    @Test
    fun `parseTilesTier traduce el tag de MapLibreMapView de vuelta al enum`() {
        assertEquals(TilesTier.LOCAL, parseTilesTier("LOCAL"))
        assertEquals(TilesTier.REMOTE, parseTilesTier("REMOTE"))
        assertEquals(TilesTier.NONE, parseTilesTier("NONE"))
    }

    @Test
    fun `parseTilesTier devuelve null para un tag desconocido o ausente`() {
        assertEquals(null, parseTilesTier("algo-que-no-es-un-tier"))
        assertEquals(null, parseTilesTier(null))
    }
}
