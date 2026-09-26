package com.revscope.core.obd.di

import com.revscope.core.obd.mcp.AgregarNotaTallerTool
import com.revscope.core.obd.mcp.BorrarDtcTool
import com.revscope.core.obd.mcp.DetenerCapturaTool
import com.revscope.core.obd.mcp.GetCapturaTool
import com.revscope.core.obd.mcp.GetMuestreoTool
import com.revscope.core.obd.mcp.IniciarCapturaTool
import com.revscope.core.obd.mcp.SetMuestreoTool
import com.revscope.core.obd.mcp.FinalizarViajeTool
import com.revscope.core.obd.mcp.GetChequeoSaludTool
import com.revscope.core.obd.mcp.GetDocumentosTool
import com.revscope.core.obd.mcp.GetDtcTool
import com.revscope.core.obd.mcp.GetEstadoTool
import com.revscope.core.obd.mcp.GetGuiaDtcTool
import com.revscope.core.obd.mcp.GetMantenimientoTool
import com.revscope.core.obd.mcp.GetSesionTallerTool
import com.revscope.core.obd.mcp.GetViajeDetalleTool
import com.revscope.core.obd.mcp.GetViajesTool
import com.revscope.core.obd.mcp.IniciarSesionTallerTool
import com.revscope.core.obd.mcp.IniciarViajeTool
import com.revscope.core.obd.mcp.McpActivityTracker
import com.revscope.core.obd.mcp.McpDispatcher
import com.revscope.core.obd.mcp.McpPermisosProvider
import com.revscope.core.obd.mcp.McpTool
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Las tools de lectura (plan6 Task 4, get_muestreo, get_captura, get_guia_dtc y get_sesion_taller) más las
 * de control (viaje, preset de muestreo, captura rápida y sesión del Taller) y borrado de DTC, que el
 * dispatcher solo lista y ejecuta si el dueño activó sus permisos en Ajustes.
 */
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
        finalizarViaje: FinalizarViajeTool,
        iniciarViaje: IniciarViajeTool,
        borrarDtc: BorrarDtcTool,
        getMuestreo: GetMuestreoTool,
        setMuestreo: SetMuestreoTool,
        iniciarCaptura: IniciarCapturaTool,
        getCaptura: GetCapturaTool,
        detenerCaptura: DetenerCapturaTool,
        getGuiaDtc: GetGuiaDtcTool,
        getSesionTaller: GetSesionTallerTool,
        iniciarSesionTaller: IniciarSesionTallerTool,
        agregarNotaTaller: AgregarNotaTallerTool,
    ): List<McpTool> = listOf(
        getEstado, getViajes, getViajeDetalle, getChequeoSalud, getDtc, getGuiaDtc, getMantenimiento, getDocumentos,
        finalizarViaje, iniciarViaje, borrarDtc,
        getMuestreo, setMuestreo, iniciarCaptura, getCaptura, detenerCaptura,
        getSesionTaller, iniciarSesionTaller, agregarNotaTaller,
    )

    @Provides
    @Singleton
    fun provideMcpDispatcher(
        tools: @JvmSuppressWildcards List<McpTool>,
        permisos: McpPermisosProvider,
        actividad: McpActivityTracker,
    ): McpDispatcher = McpDispatcher(tools, permisos = permisos::actuales, alLlamarTool = actividad::registrarLlamada)
}
