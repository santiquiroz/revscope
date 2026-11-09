package com.revscope.feature.session

import com.revscope.core.data.db.dao.SeriesSpan
import com.revscope.core.data.db.entities.GpsPointEntity
import com.revscope.core.data.db.entities.HrPointEntity
import com.revscope.core.data.db.entities.ImuPointEntity
import com.revscope.core.data.db.entities.TelemetryPointEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripCsvExportTest {

    private val obd = (1..12_345L).map { i ->
        TelemetryPointEntity(id = i, sessionId = 1, timestamp = 1_000L + i / 3, pid = if (i == 7L) "A,\"B\"" else "0C", value = i.toFloat())
    }
    private val gps = (1..7L).map { i -> GpsPointEntity(id = i, sessionId = 1, timestamp = 2_000L + i, latitude = 6.25, longitude = -75.5, speedKmh = 30.5f) }
    private val imu = (1..5_000L).map { i -> ImuPointEntity(id = i, sessionId = 1, timestamp = 3_000L + i, gLat = 0.1f, gLong = -0.2f, leanDeg = 3f) }
    private val hr = (1..3L).map { i -> HrPointEntity(id = i, sessionId = 1, timestamp = 4_000L + i, bpm = 88f) }

    @Test
    fun `el csv del viaje trae todas las filas en orden y escapa los campos`() = runBlocking {
        val calls = mutableListOf<Int>()
        val out = StringBuilder()

        writeTripCsv(out, pages(calls), pageSize = 1_000)

        val lines = out.lines().dropLast(1)
        assertEquals("section,timestamp,key,value1,value2", lines.first())
        assertEquals(1 + obd.size + gps.size + imu.size + hr.size, lines.size)
        assertEquals(expectedLines(), lines.drop(1))
        assertTrue(lines.contains("obd,1002,\"A,\"\"B\"\"\",7.0,"))
        assertTrue(calls.all { it == 1_000 })
    }

    @Test
    fun `csvLine escapa comas y comillas`() {
        assertEquals("obd,\"x,y\",\"a\"\"b\"", csvLine("obd", "x,y", "a\"b"))
    }

    @Test
    fun `el balde no reduce series cortas y acota las largas`() {
        assertEquals(RAW_BUCKET_MS, reportBucketMs(SeriesSpan(REPORT_MAX_ROWS, 0L, 10_000L)))
        assertEquals(1_000L, reportBucketMs(SeriesSpan(100_000, 0L, 18_000_000L)))
        val bucket = reportBucketMs(SeriesSpan(900_000, 123L, 123L + 10 * 3_600_000L))
        assertTrue((10 * 3_600_000L) / bucket + 2 <= REPORT_MAX_ROWS)
    }

    private fun expectedLines(): List<String> =
        obd.map { csvLine("obd", it.timestamp, it.pid, it.value, "") } +
            gps.map { csvLine("gps", it.timestamp, it.latitude, it.longitude, it.speedKmh) } +
            imu.map { csvLine("imu", it.timestamp, it.gLat, it.gLong, it.leanDeg) } +
            hr.map { csvLine("hr", it.timestamp, it.bpm, "", "") }

    private fun pages(calls: MutableList<Int>) = TripCsvPages(
        obd = keyset(obd.shuffled(), calls) { PageKey(it.timestamp, it.id) },
        gps = keyset(gps, calls) { PageKey(it.timestamp, it.id) },
        imu = keyset(imu, calls) { PageKey(it.timestamp, it.id) },
        hr = keyset(hr, calls) { PageKey(it.timestamp, it.id) },
    )

    private fun <T> keyset(rows: List<T>, calls: MutableList<Int>, keyOf: (T) -> PageKey): PageFetch<T> {
        val order = compareBy<PageKey>({ it.timestamp }, { it.id })
        val sorted = rows.sortedWith { a, b -> order.compare(keyOf(a), keyOf(b)) }
        return { after, size ->
            calls += size
            sorted.filter { order.compare(keyOf(it), after) > 0 }.take(size)
        }
    }
}
