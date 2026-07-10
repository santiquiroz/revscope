package com.revscope.core.obd.di

import com.revscope.core.obd.mcp.GetChequeoSaludTool
import com.revscope.core.obd.mcp.GetDocumentosTool
import com.revscope.core.obd.mcp.GetDtcTool
import com.revscope.core.obd.mcp.GetEstadoTool
import com.revscope.core.obd.mcp.GetMantenimientoTool
import com.revscope.core.obd.mcp.GetViajeDetalleTool
import com.revscope.core.obd.mcp.GetViajesTool
import com.revscope.core.obd.mcp.McpDispatcher
import com.revscope.core.obd.mcp.McpTool
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Wires the 7 read-only MCP tools (plan6 Task 4) into a single [McpDispatcher]. */
@Module
@InstallIn(SingletonComponent::class)
object McpModule {

    @Provides
    @Singleton
    fun provideMcpTools(
        getEstado: GetEstadoTool,
        getViajes: GetViajesTool,
        getViajeDetalle: GetViajeDetalleTool,
        getChequeoSalud: GetChequeoSaludTool,
        getDtc: GetDtcTool,
        getMantenimiento: GetMantenimientoTool,
        getDocumentos: GetDocumentosTool,
    ): List<McpTool> = listOf(
        getEstado, getViajes, getViajeDetalle, getChequeoSalud, getDtc, getMantenimiento, getDocumentos,
    )

    @Provides
    @Singleton
    fun provideMcpDispatcher(tools: @JvmSuppressWildcards List<McpTool>): McpDispatcher = McpDispatcher(tools)
}
