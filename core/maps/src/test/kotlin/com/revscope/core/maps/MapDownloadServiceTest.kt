package com.revscope.core.maps

import com.revscope.core.uitesting.XmlStringResolver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

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
        assertTrue(partFiles(dir).isEmpty())
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
    fun `error de red antes de escribir bytes deja Error sin crear el part`() = runBlocking {
        val dir = tempMapsDir()
        val service = service(
            dir,
            bytesSource = FakeMapBytesSource(totalBytes = 100L, failWith = IOException("caida de red")),
        )

        service.download(allowCellular = true)
        val state = awaitState<MapDownloadState.Error>(service)

        assertTrue(state.message.isNotBlank())
        assertTrue(partFiles(dir).isEmpty())
    }

    @Test
    fun `error de red sin mensaje usa el texto por defecto en ingles cuando el idioma es ingles`() = runBlocking {
        val dir = tempMapsDir()
        val service = service(
            dir,
            bytesSource = FakeMapBytesSource(totalBytes = 100L, failWith = IOException()),
            resolver = XmlStringResolver.en(R::class.java),
        )

        service.download(allowCellular = true)
        val state = awaitState<MapDownloadState.Error>(service)

        assertEquals("Network error while downloading the offline map", state.message)
    }

    @Test
    fun `error de red a mitad de la descarga borra el part con bytes reales escritos`() = runBlocking {
        val dir = tempMapsDir()
        // 2 de 4 chunks escritos (200 de 400 bytes reales en disco) antes de que la fuente tire —
        // a diferencia del failWith sin failAfterChunks, acá el .part existe de verdad al fallar.
        val service = service(
            dir,
            bytesSource = FakeMapBytesSource(totalBytes = 400L, chunkBytes = 100L, failAfterChunks = 2),
        )

        service.download(allowCellular = true)
        val state = awaitState<MapDownloadState.Error>(service)

        assertTrue(state.message.isNotBlank())
        assertTrue(partFiles(dir).isEmpty())
    }

    @Test
    fun `redescarga sobre un mapa ya existente reemplaza el archivo target`() = runBlocking {
        val dir = tempMapsDir()
        targetFile(dir).apply { parentFile?.mkdirs(); writeBytes(ByteArray(999) { 1 }) }
        val service = service(dir, bytesSource = FakeMapBytesSource(totalBytes = 400L, chunkBytes = 100L))

        service.download(allowCellular = true)
        val finalState = awaitState<MapDownloadState.Idle>(service)

        assertTrue(finalState.exists)
        assertEquals(400L, finalState.sizeBytes)
        assertTrue(partFiles(dir).isEmpty())
    }

    @Test
    fun `part huerfano de un intento previo se limpia al reintentar`() = runBlocking {
        val dir = tempMapsDir()
        partFile(dir).apply { parentFile?.mkdirs(); writeBytes(ByteArray(50)) }
        val service = service(dir, bytesSource = FakeMapBytesSource(totalBytes = 400L, chunkBytes = 100L))

        service.download(allowCellular = true)
        val finalState = awaitState<MapDownloadState.Idle>(service)

        assertEquals(400L, finalState.sizeBytes)
        assertTrue(partFiles(dir).isEmpty())
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
        assertTrue(partFiles(dir).isEmpty())
    }

    @Test
    fun `espacio insuficiente bloquea antes de tocar la red`() = runBlocking {
        val dir = tempMapsDir()
        val service = service(dir, usableSpaceBytes = { 10L }, bytesSource = FakeMapBytesSource(totalBytes = 100L))

        service.download(allowCellular = true)
        val state = awaitState<MapDownloadState.Error>(service)

        assertTrue(state.message.isNotBlank())
        assertTrue(partFiles(dir).isEmpty())
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

    @Test
    fun `la limpieza tardia de un intento cancelado no borra el part del intento nuevo`() = runBlocking {
        val dir = tempMapsDir()
        val source = RacingBytesSource()
        val service = service(dir, bytesSource = source)

        service.download(allowCellular = true)
        source.firstStarted.await()
        service.cancel()
        service.download(allowCellular = true)

        val finalState = withTimeout(POLL_TIMEOUT_MS) {
            while ((service.state.value as? MapDownloadState.Idle)?.exists != true) delay(POLL_INTERVAL_MS)
            service.state.value as MapDownloadState.Idle
        }
        assertTrue(source.secondFileSurvived)
        assertEquals(RacingBytesSource.SECOND_SIZE.toLong(), finalState.sizeBytes)
        assertTrue(targetFile(dir).readBytes().all { it == RacingBytesSource.SECOND_BYTE })
        assertTrue(partFiles(dir).isEmpty())
    }

    @Test
    fun `download publica Downloading de inmediato sin esperar a la corrutina`() = runBlocking {
        val dir = tempMapsDir()
        targetFile(dir).apply { parentFile?.mkdirs(); writeBytes(ByteArray(999) { 1 }) }
        val service = service(dir, bytesSource = FakeMapBytesSource(totalBytes = 400L, chunkBytes = 100L, delayPerChunkMs = 50L))

        service.download(allowCellular = true)

        assertTrue("state should be Downloading immediately", service.state.value is MapDownloadState.Downloading)
        val finalState = awaitState<MapDownloadState.Idle>(service)

        assertEquals(400L, finalState.sizeBytes)
    }

    private class RacingBytesSource : MapBytesSource {
        private val calls = AtomicInteger(0)
        val firstStarted = CompletableDeferred<Unit>()
        private val firstJob = CompletableDeferred<Job>()
        private val secondStarted = CompletableDeferred<Unit>()

        @Volatile
        var secondFileSurvived = false

        override suspend fun download(
            url: String,
            destination: File,
            onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit,
        ) {
            if (calls.incrementAndGet() == 1) firstAttempt(destination) else secondAttempt(destination, onProgress)
        }

        // Escritura bloqueante que ignora el cancel hasta que el segundo intento ya arrancó: su
        // catch(CancellationException) corre recién después.
        private suspend fun firstAttempt(destination: File) {
            firstJob.complete(currentCoroutineContext()[Job]!!)
            destination.writeBytes(ByteArray(10) { 1 })
            firstStarted.complete(Unit)
            withContext(NonCancellable) { secondStarted.await() }
            currentCoroutineContext().ensureActive()
        }

        private suspend fun secondAttempt(destination: File, onProgress: (Long, Long) -> Unit) {
            destination.writeBytes(ByteArray(SECOND_SIZE) { SECOND_BYTE })
            secondStarted.complete(Unit)
            firstJob.await().join()
            secondFileSurvived = destination.isFile && destination.length() == SECOND_SIZE.toLong()
            onProgress(SECOND_SIZE.toLong(), SECOND_SIZE.toLong())
        }

        companion object {
            const val SECOND_SIZE = 300
            const val SECOND_BYTE: Byte = 2
        }
    }

    private fun service(
        dir: File,
        isOnWifi: () -> Boolean = { true },
        bytesSource: MapBytesSource,
        usableSpaceBytes: () -> Long = { Long.MAX_VALUE / 2 },
        resolver: com.revscope.core.common.i18n.StringResolver = XmlStringResolver.es(R::class.java),
    ) = MapDownloadService(dir, isOnWifi, bytesSource, usableSpaceBytes, resolver)

    private fun tempMapsDir(): File =
        File.createTempFile("maps-test", "").apply {
            delete()
            mkdirs()
            deleteOnExit()
        }

    private fun partFile(dir: File) = File(dir, "${MapStyleProvider.PMTILES_FILE_NAME}.part")
    private fun partFiles(dir: File): List<File> =
        dir.listFiles { f -> f.name.startsWith("${MapStyleProvider.PMTILES_FILE_NAME}.part") }?.toList().orEmpty()
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
            while (partFiles(dir).isNotEmpty()) delay(POLL_INTERVAL_MS)
        }
    }
}
