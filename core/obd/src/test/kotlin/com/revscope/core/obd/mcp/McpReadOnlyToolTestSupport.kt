package com.revscope.core.obd.mcp

import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.connection.Transport
import com.revscope.core.obd.session.ObdSessionManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow

internal data class McpToolLeaseFixture(
    val manager: ObdSessionManager,
    val transport: Transport,
)

internal fun leaseFixture(conectado: Boolean = true): McpToolLeaseFixture {
    val manager = mockk<ObdSessionManager>()
    val transport = mockk<Transport>()
    val estado: ConnectionState = if (conectado) ConnectionState.Connected("ELM327") else ConnectionState.Disconnected
    every { manager.connectionState } returns MutableStateFlow(estado)
    coEvery { manager.withDiagnosticLease<Any?>(any(), any(), any()) } coAnswers {
        Result.success(thirdArg<suspend (Transport) -> Any?>().invoke(transport))
    }
    return McpToolLeaseFixture(manager, transport)
}
