package com.revscope.core.maps

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException

private const val POLL_INTERVAL_MS = 10L
private const val POLL_TIMEOUT_MS = 5_000L

class MapDownloadServiceTest {

    @Test
    fun `descarga feliz escribe el part y promueve a Idle con exists true`() = runBlocking {
        val dir = tempMapsDir()
        val service = service(dir, bytesSource = FakeMapBytesSource(totalBytes = 400L, chunkBytes = 100L))

        service.download(allowCellular = true)
        val finalState = awaitState<MapDownloadState.Idle>(service)

        assertTrue(finalState.exists)
        assertEquals(400L, finalState.sizeBytes)
        assertFalse(partFile(dir).exists())
        assertTrue(targetFile(dir).isFile)
    }

    @Test
    fun `cancel corta la descarga en curso y borra el part`() = runBlocking {
        val dir = tempMapsDir()
        val service = service(
            dir,
            bytesSource = FakeMapBytesSource(totalBytes = 1_000L, chunkBytes = 100L, delayPerChunkMs = 50L),
        )

        service.download(allowCellular = true)
        awaitDownloadingStarted(service)
        service.cancel()

        // cancel() no espera a que la corrutina cancelada termine de desenrollarse (es async por
        // diseño, igual que Job.cancel()) — el estado Idle es inmediato, el borrado físico del
        // .part puede tardar un instante en completarse desde el catch(CancellationException).
        val state = service.state.value
        assertTrue(state is MapDownloadState.Idle)
        assertFalse((state as MapDownloadState.Idle).exists)
        awaitPartFileGone(dir)
        assertFalse(targetFile(dir).exists())
    }

    @Test
    fun `error de red deja Error y borra el part`() = runBlocking {
        val dir = tempMapsDir()
        val service = service(
            dir,
            bytesSource = FakeMapBytesSource(totalBytes = 100L, failWith = IOException("caida de red")),
        )

        service.download(allowCellular = true)
        val state = awaitState<MapDownloadState.Error>(service)

        assertTrue(state.message.isNotBlank())
        assertFalse(partFile(dir).exists())
    }

    @Test
    fun `part huerfano de un intento previo se limpia al reintentar`() = runBlocking {
        val dir = tempMapsDir()
        partFile(dir).apply { parentFile?.mkdirs(); writeBytes(ByteArray(50)) }
        val service = service(dir, bytesSource = FakeMapBytesSource(totalBytes = 400L, chunkBytes = 100L))

        service.download(allowCellular = true)
        val finalState = awaitState<MapDownloadState.Idle>(service)

        assertEquals(400L, finalState.sizeBytes)
        assertFalse(partFile(dir).exists())
    }

    @Test
    fun `el progreso observado es monotonico creciente hasta completar`() = runBlocking {
        val dir = tempMapsDir()
        val service = service(
            dir,
            bytesSource = FakeMapBytesSource(totalBytes = 500L, chunkBytes = 50L, delayPerChunkMs = 15L),
        )

        val observed = mutableListOf<Float>()
        service.download(allowCellular = true)
        withTimeout(POLL_TIMEOUT_MS) {
            while (service.state.value !is MapDownloadState.Idle) {
                (service.state.value as? MapDownloadState.Downloading)?.let { observed += it.progress }
                delay(POLL_INTERVAL_MS)
            }
        }

        assertTrue("se esperaban varias muestras de progreso, hubo ${observed.size}", observed.size >= 2)
        for (i in 1 until observed.size) {
            assertTrue(observed[i] >= observed[i - 1])
        }
    }

    @Test
    fun `sin wifi y sin permiso de datos moviles bloquea antes de tocar la red`() = runBlocking {
        val dir = tempMapsDir()
        val service = service(dir, isOnWifi = { false }, bytesSource = FakeMapBytesSource(totalBytes = 100L))

        service.download(allowCellular = false)
        val state = awaitState<MapDownloadState.Error>(service)

        assertTrue(state.message.isNotBlank())
        assertFalse(partFile(dir).exists())
    }

    @Test
    fun `espacio insuficiente bloquea antes de tocar la red`() = runBlocking {
        val dir = tempMapsDir()
        val service = service(dir, usableSpaceBytes = { 10L }, bytesSource = FakeMapBytesSource(totalBytes = 100L))

        service.download(allowCellular = true)
        val state = awaitState<MapDownloadState.Error>(service)

        assertTrue(state.message.isNotBlank())
        assertFalse(partFile(dir).exists())
    }

    @Test
    fun `delete borra el archivo descargado y vuelve a Idle sin existir`() = runBlocking {
        val dir = tempMapsDir()
        val service = service(dir, bytesSource = FakeMapBytesSource(totalBytes = 200L, chunkBytes = 100L))
        service.download(allowCellular = true)
        awaitState<MapDownloadState.Idle>(service)

        service.delete()

        val state = service.state.value
        assertTrue(state is MapDownloadState.Idle)
        assertFalse((state as MapDownloadState.Idle).exists)
        assertFalse(targetFile(dir).exists())
    }

    @Test
    fun `refresh no pisa una descarga en curso`() = runBlocking {
        val dir = tempMapsDir()
        val service = service(
            dir,
            bytesSource = FakeMapBytesSource(totalBytes = 1_000L, chunkBytes = 100L, delayPerChunkMs = 50L),
        )

        service.download(allowCellular = true)
        awaitDownloadingStarted(service)
        service.refresh()

        assertTrue(service.state.value is MapDownloadState.Downloading)
        service.cancel()
    }

    private fun service(
        dir: File,
        isOnWifi: () -> Boolean = { true },
        bytesSource: MapBytesSource,
        usableSpaceBytes: () -> Long = { Long.MAX_VALUE / 2 },
    ) = MapDownloadService(dir, isOnWifi, bytesSource, usableSpaceBytes)

    private fun tempMapsDir(): File =
        File.createTempFile("maps-test", "").apply {
            delete()
            mkdirs()
            deleteOnExit()
        }

    private fun partFile(dir: File) = File(dir, "${MapStyleProvider.PMTILES_FILE_NAME}.part")
    private fun targetFile(dir: File) = File(dir, MapStyleProvider.PMTILES_FILE_NAME)

    private suspend inline fun <reified T : MapDownloadState> awaitState(service: MapDownloadService): T =
        withTimeout(POLL_TIMEOUT_MS) {
            while (service.state.value !is T) delay(POLL_INTERVAL_MS)
            service.state.value as T
        }

    private suspend fun awaitDownloadingStarted(service: MapDownloadService) {
        withTimeout(POLL_TIMEOUT_MS) {
            while (service.state.value !is MapDownloadState.Downloading) delay(POLL_INTERVAL_MS)
        }
    }

    private suspend fun awaitPartFileGone(dir: File) {
        withTimeout(POLL_TIMEOUT_MS) {
            while (partFile(dir).exists()) delay(POLL_INTERVAL_MS)
        }
    }
}
