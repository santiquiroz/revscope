package com.revscope.core.common

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class FlowExtTest {

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `un flow que lanza no mata el scope - emite lo previo y se queda en el ultimo valor`() = runTest(UnconfinedTestDispatcher()) {
        val source = flow {
            emit(listOf(1))
            throw IllegalStateException("db corrupta")
        }
        val state = source.stateInSafe(backgroundScope, emptyList())
        // SharingStarted.WhileSubscribed (default de stateInSafe) solo arranca la fuente
        // cuando hay un colector real.
        backgroundScope.launch { state.collect {} }
        yield()
        assertEquals(listOf(1), state.value)
        // No crashea el test: la excepción quedó contenida.
    }
}
