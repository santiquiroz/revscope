package com.revscope.core.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.revscope.core.data.db.AppDatabase
import com.revscope.core.data.db.entities.GpsPointEntity
import com.revscope.core.data.db.entities.HrPointEntity
import com.revscope.core.data.db.entities.ImuPointEntity
import com.revscope.core.data.db.entities.LapEntity
import com.revscope.core.data.db.entities.SessionEntity
import com.revscope.core.data.db.entities.TelemetryPointEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReportBoundedQueriesTest {

    private lateinit var db: AppDatabase
    private var sessionId = 0L

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        sessionId = db.sessionDao().insert(session())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `los baldes de telemetria no pasan del tope con 100 mil filas`() = runBlocking {
        seedTelemetry(TELEMETRY_ROWS, pid = "0C", stepMs = 180L)
        val dao = db.telemetryDao()
        val span = dao.spanForSessionAndPid(sessionId, "0C")
        assertEquals(TELEMETRY_ROWS, span.count)

        val buckets = dao.avgBucketsForPid(sessionId, "0C", bucketMs = 1_000L, limit = MAX_ROWS)

        assertTrue(buckets.size <= MAX_ROWS)
        assertEquals((span.lastTs!! - span.firstTs!!) / 1_000L + 1, buckets.size.toLong())
        assertEquals(buckets.sortedBy { it.timestamp }, buckets)
    }

    @Test
    fun `sin reducir el limite corta en el tope`() = runBlocking {
        seedTelemetry(TELEMETRY_ROWS, pid = "0C", stepMs = 180L)

        val raw = db.telemetryDao().avgBucketsForPid(sessionId, "0C", bucketMs = 1L, limit = MAX_ROWS)

        assertEquals(MAX_ROWS, raw.size)
    }

    @Test
    fun `balde de 1 ms devuelve las filas tal cual`() = runBlocking {
        seedTelemetry(50, pid = "0D", stepMs = 200L)

        val raw = db.telemetryDao().avgBucketsForPid(sessionId, "0D", bucketMs = 1L, limit = MAX_ROWS)

        assertEquals(db.telemetryDao().pointsForSessionAndPid(sessionId, "0D").map { it.timestamp to it.value },
            raw.map { it.timestamp to it.value })
    }

    @Test
    fun `la paginacion de telemetria devuelve cada fila una vez y en orden`() = runBlocking {
        seedTelemetry(TELEMETRY_ROWS, pid = null, stepMs = 0L)
        val dao = db.telemetryDao()

        val all = pageAll { after, size -> dao.pageForSession(sessionId, after.first, after.second, size) }
            .map { it.timestamp to it.id }

        assertEquals(TELEMETRY_ROWS, all.size)
        assertEquals(TELEMETRY_ROWS, all.map { it.second }.toSet().size)
        assertEquals(all.sortedWith(compareBy({ it.first }, { it.second })), all)
    }

    @Test
    fun `imu gps y hr acotados y paginados`() = runBlocking {
        val imu = (0 until 30_000).map { ImuPointEntity(sessionId = sessionId, timestamp = BASE + it * 20L, gLat = 0.1f, gLong = if (it % 50 == 7) -0.6f else 0.05f, leanDeg = 0f) }
        db.imuDao().insertAll(imu)
        db.gpsDao().insertAll((0 until 30_000).map { GpsPointEntity(sessionId = sessionId, timestamp = BASE + it * 20L, latitude = 6.2, longitude = -75.5, speedKmh = 40f) })
        repeat(12_000) { db.hrDao().insert(HrPointEntity(sessionId = sessionId, timestamp = BASE + it * 50L, bpm = 90f)) }

        val minG = db.imuDao().minGLongBuckets(sessionId, 1_000L, MAX_ROWS)
        assertTrue(minG.size <= MAX_ROWS)
        assertTrue(minG.all { it.gLong == -0.6f })
        assertTrue(db.imuDao().maxGLongBuckets(sessionId, 1_000L, MAX_ROWS).all { it.gLong == 0.05f })
        assertEquals(600, db.imuDao().firstSampleBuckets(sessionId, 1_000L, MAX_ROWS).size)
        assertEquals(MAX_ROWS, db.gpsDao().firstSampleBuckets(sessionId, 1L, MAX_ROWS).size)
        assertEquals(600, db.hrDao().avgBuckets(sessionId, 1_000L, MAX_ROWS).size)

        assertEquals(30_000, pageAll { a, s -> db.imuDao().pageForSession(sessionId, a.first, a.second, s) }.size)
        assertEquals(30_000, pageAll { a, s -> db.gpsDao().pageForSession(sessionId, a.first, a.second, s) }.size)
        assertEquals(12_000, pageAll { a, s -> db.hrDao().pageForSession(sessionId, a.first, a.second, s) }.size)
    }

    @Test
    fun `picos por vuelta en una sola consulta`() = runBlocking {
        db.lapDao().insert(LapEntity(sessionId = sessionId, lapNumber = 1, timeMs = 1_000L, completedAt = BASE + 1_000L))
        db.lapDao().insert(LapEntity(sessionId = sessionId, lapNumber = 2, timeMs = 1_000L, completedAt = BASE + 2_000L))
        db.lapDao().insert(LapEntity(sessionId = sessionId, lapNumber = 3, timeMs = 1_000L, completedAt = BASE + 9_000L))
        db.imuDao().insertAll(
            listOf(
                ImuPointEntity(sessionId = sessionId, timestamp = BASE + 500L, gLat = -1.2f, gLong = 0f, leanDeg = 30f),
                ImuPointEntity(sessionId = sessionId, timestamp = BASE + 1_500L, gLat = 0.4f, gLong = 0f, leanDeg = -10f),
            )
        )
        db.hrDao().insert(HrPointEntity(sessionId = sessionId, timestamp = BASE + 1_800L, bpm = 150f))
        val laps = db.lapDao().lapsForSession(sessionId)

        val lateral = db.imuDao().lapMaxAbsLateralG(sessionId).associate { it.lapId to it.value }
        val lean = db.imuDao().lapMaxAbsLean(sessionId).associate { it.lapId to it.value }
        val bpm = db.hrDao().lapMaxBpm(sessionId).associate { it.lapId to it.value }

        laps.forEach { lap ->
            val from = lap.completedAt - lap.timeMs
            assertEquals(db.imuDao().maxAbsLateralGBetween(sessionId, from, lap.completedAt), lateral[lap.id])
            assertEquals(db.imuDao().maxAbsLeanBetween(sessionId, from, lap.completedAt), lean[lap.id])
            assertEquals(db.hrDao().maxBpmBetween(sessionId, from, lap.completedAt), bpm[lap.id])
        }
        assertEquals(1.2f, lateral[laps[0].id])
        assertNull(lateral[laps[2].id])
    }

    private suspend fun seedTelemetry(rows: Int, pid: String?, stepMs: Long) {
        val pids = listOf("0C", "0D", "11", "05")
        (0 until rows).chunked(10_000).forEach { chunk ->
            db.telemetryDao().insertAll(
                chunk.map { i ->
                    val ts = if (stepMs > 0) BASE + i * stepMs else BASE + (i / 8) * 100L
                    TelemetryPointEntity(sessionId = sessionId, timestamp = ts, pid = pid ?: pids[i % 4], value = i.toFloat())
                }
            )
        }
    }

    private suspend fun <T> pageAll(fetch: suspend (Pair<Long, Long>, Int) -> List<T>): List<T> where T : Any {
        val out = mutableListOf<T>()
        var after = Long.MIN_VALUE to Long.MIN_VALUE
        while (true) {
            val page = fetch(after, PAGE_SIZE)
            assertTrue(page.size <= PAGE_SIZE)
            out += page
            if (page.size < PAGE_SIZE) return out
            after = keyOf(page.last())
        }
    }

    private fun keyOf(row: Any): Pair<Long, Long> = when (row) {
        is TelemetryPointEntity -> row.timestamp to row.id
        is ImuPointEntity -> row.timestamp to row.id
        is GpsPointEntity -> row.timestamp to row.id
        is HrPointEntity -> row.timestamp to row.id
        else -> error("fila desconocida")
    }

    private fun session() = SessionEntity(
        vehicleProfileId = 1L,
        startedAt = BASE,
        endedAt = BASE + 5 * 3_600_000L,
        adapterName = "test",
        maxRpm = 0,
        maxSpeed = 0,
        distanceKm = 0f,
    )

    private companion object {
        const val BASE = 1_700_000_000_000L
        const val TELEMETRY_ROWS = 100_000
        const val MAX_ROWS = 20_000
        const val PAGE_SIZE = 5_000
    }
}
