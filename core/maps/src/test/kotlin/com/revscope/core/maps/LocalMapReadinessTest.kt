package com.revscope.core.maps

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private val SOME_DOWNLOADING = MapDownloadState.Downloading(progress = 0.3f, bytesDownloaded = 300L, totalBytes = 1_000L)
private val SOME_ERROR = MapDownloadState.Error("fallo de red")

class LocalMapReadinessTest {

    @Test
    fun `Idle con archivo existente queda listo`() {
        val idle = MapDownloadState.Idle(exists = true, sizeBytes = 100L, downloadedAtMs = 1L)
        assertTrue(LocalMapReadiness.step(prev = false, state = idle))
    }

    @Test
    fun `Idle sin archivo queda no listo`() {
        val idle = MapDownloadState.Idle(exists = false, sizeBytes = 0L, downloadedAtMs = null)
        assertFalse(LocalMapReadiness.step(prev = true, state = idle))
    }

    @Test
    fun `Downloading conserva listo mientras una re-descarga corre sobre un mapa ya instalado`() {
        assertTrue(LocalMapReadiness.step(prev = true, state = SOME_DOWNLOADING))
    }

    @Test
    fun `Downloading conserva no listo en una primera descarga`() {
        assertFalse(LocalMapReadiness.step(prev = false, state = SOME_DOWNLOADING))
    }

    @Test
    fun `Error tras una re-descarga fallida conserva el mapa viejo usable`() {
        assertTrue(LocalMapReadiness.step(prev = true, state = SOME_ERROR))
    }

    @Test
    fun `Error en una primera descarga nunca tuvo prev listo`() {
        assertFalse(LocalMapReadiness.step(prev = false, state = SOME_ERROR))
    }

    @Test
    fun `delete deja Idle sin archivo y por lo tanto no listo`() {
        val postDelete = MapDownloadState.Idle(exists = false, sizeBytes = 0L, downloadedAtMs = null)
        assertFalse(LocalMapReadiness.step(prev = true, state = postDelete))
    }
}
