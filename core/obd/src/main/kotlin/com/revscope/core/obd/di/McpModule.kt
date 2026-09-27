package com.revscope.core.obd.di

import com.revscope.core.obd.mcp.AgregarNotaTallerTool
import com.revscope.core.obd.mcp.AvanzarPruebaGuiadaTool
import com.revscope.core.obd.mcp.BorrarDtcTool
import com.revscope.core.obd.mcp.CancelarPruebaGuiadaTool
import com.revscope.core.obd.mcp.DetenerCapturaTool
import com.revscope.core.obd.mcp.GetCapturaTool
import com.revscope.core.obd.mcp.GetMuestreoTool
import com.revscope.core.obd.mcp.IniciarCapturaTool
import com.revscope.core.obd.mcp.IniciarPruebaGuiadaTool
import com.revscope.core.obd.mcp.SetMuestreoTool
import com.revscope.core.obd.mcp.FinalizarViajeTool
import com.revscope.core.obd.mcp.GetChequeoSaludTool
import com.revscope.core.obd.mcp.GetDocumentosTool
import com.revscope.core.obd.mcp.GetDtcTool
import com.revscope.core.obd.mcp.GetEstadoTool
import com.revscope.core.obd.mcp.GetGuiaDtcTool
import com.revscope.core.obd.mcp.GetInformeTallerTool
import com.revscope.core.obd.mcp.GetMantenimientoTool
import com.revscope.core.obd.mcp.GetPruebaGuiadaTool
import com.revscope.core.obd.mcp.GetSesionTallerTool
import com.revscope.core.obd.mcp.GetViajeDetalleTool
import com.revscope.core.obd.mcp.GetViajesTool
import com.revscope.core.obd.mcp.IniciarSesionTallerTool
import com.revscope.core.obd.mcp.IniciarViajeTool
import com.revscope.core.obd.mcp.McpActivityTracker
import com.revscope.core.obd.mcp.McpDispatcher
import com.revscope.core.obd.mcp.McpPermisosProvider
import com.revscope.core.obd.mcp.McpTool
import com.revscope.core.obd.mcp.RegistrarMedicionTool
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Las tools de lectura (plan6 Task 4, get_muestreo, get_captura, get_guia_dtc, get_sesion_taller y
 * get_prueba_guiada) más las de control (viaje, preset de muestreo, captura rápida, sesión y pruebas guiadas
 * del Taller, y la medición con multímetro) y borrado de DTC, que el dispatcher solo lista y ejecuta si el dueño activó sus permisos en
 * Ajustes.
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
        getInformeTaller: GetInformeTallerTool,
        getSesionTaller: GetSesionTallerTool,
        iniciarSesionTaller: IniciarSesionTallerTool,
        agregarNotaTaller: AgregarNotaTallerTool,
        registrarMedicion: RegistrarMedicionTool,
        getPruebaGuiada: GetPruebaGuiadaTool,
        iniciarPruebaGuiada: IniciarPruebaGuiadaTool,
        avanzarPruebaGuiada: AvanzarPruebaGuiadaTool,
        cancelarPruebaGuiada: CancelarPruebaGuiadaTool,
    ): List<McpTool> = listOf(
        getEstado, getViajes, getViajeDetalle, getChequeoSalud, getDtc, getGuiaDtc, getMantenimiento, getDocumentos,
        finalizarViaje, iniciarViaje, borrarDtc,
        getMuestreo, setMuestreo, iniciarCaptura, getCaptura, detenerCaptura,
        getSesionTaller, getInformeTaller, iniciarSesionTaller, agregarNotaTaller, registrarMedicion,
        getPruebaGuiada, iniciarPruebaGuiada, avanzarPruebaGuiada, cancelarPruebaGuiada,
    )

    @Provides
    @Singleton
    fun provideMcpDispatcher(
        tools: @JvmSuppressWildcards List<McpTool>,
        permisos: McpPermisosProvider,
        actividad: McpActivityTracker,
    ): McpDispatcher = McpDispatcher(tools, permisos = permisos::actuales, alLlamarTool = actividad::registrarLlamada)
}
