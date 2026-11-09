package com.revscope.core.obd.telemetry

import com.revscope.core.data.db.dao.GpsDao
import com.revscope.core.data.db.dao.TelemetryDao
import com.revscope.core.data.db.entities.GpsPointEntity
import com.revscope.core.data.db.entities.TelemetryPointEntity
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RetryingBatchBufferTest {

    @Test
    fun `telemetry rows are retried once in order after insert failure`() = runTest {
        val dao = mockk<TelemetryDao>()
        val inserted = mutableListOf<TelemetryPointEntity>()
        var attempts = 0
        coEvery { dao.insertAll(any()) } coAnswers {
            if (attempts++ == 0) error("temporary database failure")
            inserted += firstArg<List<TelemetryPointEntity>>()
        }
        val buffer = RetryingBatchBuffer<TelemetryPointEntity>(onDrop = {}, onFailure = { _, _ -> })
        val rows = listOf(
            TelemetryPointEntity(sessionId = 1, timestamp = 10, pid = "0C", value = 1f),
            TelemetryPointEntity(sessionId = 1, timestamp = 20, pid = "0C", value = 2f),
        )
        rows.forEach(buffer::add)

        buffer.flush(dao::insertAll)
        buffer.flush(dao::insertAll)

        assertEquals(rows, inserted)
        assertEquals(2, attempts)
    }

    @Test
    fun `gps rows are retried once in order after insert failure`() = runTest {
        val dao = mockk<GpsDao>()
        val inserted = mutableListOf<GpsPointEntity>()
        var attempts = 0
        coEvery { dao.insertAll(any()) } coAnswers {
            if (attempts++ == 0) error("temporary database failure")
            inserted += firstArg<List<GpsPointEntity>>()
        }
        val buffer = RetryingBatchBuffer<GpsPointEntity>(onDrop = {}, onFailure = { _, _ -> })
        val rows = listOf(
            GpsPointEntity(sessionId = 1, timestamp = 10, latitude = 1.0, longitude = 2.0, speedKmh = 3f),
            GpsPointEntity(sessionId = 1, timestamp = 20, latitude = 4.0, longitude = 5.0, speedKmh = 6f),
        )
        rows.forEach(buffer::add)

        buffer.flush(dao::insertAll)
        buffer.flush(dao::insertAll)

        assertEquals(rows, inserted)
        assertEquals(2, attempts)
    }

    @Test
    fun `buffer drops and reports only the oldest rows above its limit`() = runTest {
        val inserted = mutableListOf<Int>()
        val dropped = mutableListOf<Int>()
        val buffer = RetryingBatchBuffer<Int>(onDrop = { dropped += it }, onFailure = { _, _ -> })
        repeat(10_001) { buffer.add(it) }

        buffer.flush { inserted += it }

        assertEquals((1..10_000).toList(), inserted)
        assertEquals(listOf(1), dropped)
    }

    @Test
    fun `cancellation is rethrown and leaves the batch available`() = runTest {
        val buffer = RetryingBatchBuffer<Int>(onDrop = {}, onFailure = { _, _ -> })
        buffer.add(7)

        try {
            buffer.flush { throw CancellationException("cancelled") }
            throw AssertionError("Expected cancellation")
        } catch (_: CancellationException) {
            Unit
        }

        val inserted = mutableListOf<Int>()
        buffer.flush { inserted += it }
        assertEquals(listOf(7), inserted)
    }
}
