package com.revscope.feature.map

import org.junit.Assert.assertEquals
import org.junit.Test

class AnchoLeaderboardTest {

    @Test
    fun `el ranking crece con la letra hasta una vez y media su ancho`() {
        assertEquals(220f, anchoLeaderboard(1.0f).value, 0.01f)
        assertEquals(286f, anchoLeaderboard(1.3f).value, 0.01f)
        assertEquals(330f, anchoLeaderboard(2.0f).value, 0.01f)
        assertEquals(220f, anchoLeaderboard(0.85f).value, 0.01f)
    }
}
