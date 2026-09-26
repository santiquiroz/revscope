package com.revscope.feature.session

import com.revscope.core.data.db.entities.SessionEntity
import com.revscope.core.uitesting.MatrizCaptura
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CapturasViajesTest {

    @Test
    fun sessionItem() = MatrizCaptura.componente("SessionItem") {
        SessionItem(
            session = SessionEntity(
                vehicleProfileId = 1,
                startedAt = 1_790_000_000_000,
                endedAt = 1_790_000_000_000 + 47 * 60_000 + 12_000,
                adapterName = "Android-Vlink",
                maxRpm = 9948,
                maxSpeed = 112,
                distanceKm = 38.4f,
            ),
            isCompareCandidate = false,
            onClick = {},
            onCompare = {},
            onDelete = {},
        )
    }
}
