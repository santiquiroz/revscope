package com.revscope.core.obd.taller.informe

import android.content.Context
import com.revscope.core.data.db.dao.VehicleProfileDao
import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.obd.taller.dtc.BaseConocimientoDtc
import com.revscope.core.obd.taller.sesion.AnalizadorSesion
import com.revscope.core.obd.taller.sesion.TallerRepository
import com.revscope.core.obd.taller.sesion.VehiculoTaller
import com.revscope.core.obd.taller.sesion.aVehiculoTaller
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class GeneradorInformeTaller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repositorio: TallerRepository,
    private val vehiculos: VehicleProfileDao,
    private val analizador: AnalizadorSesion,
    private val baseDtc: BaseConocimientoDtc,
    private val armador: ArmadorInformeTaller,
) {

    suspend fun generar(sesionId: Long): InformeTaller? {
        val sesion = repositorio.sesion(sesionId) ?: return null
        val eventos = repositorio.eventos(sesionId)
        val perfil = vehiculos.getById(sesion.vehiculoId)?.aVehiculoTaller()
        val claveModelo = sesion.claveModelo ?: perfil?.claveModelo
        val conocimiento = claveModelo?.let { repositorio.conocimiento(it) }
        val vehiculo = perfil ?: VehiculoTaller(
            sesion.vehiculoId,
            "Vehículo #${sesion.vehiculoId}",
            claveModelo,
            conocimiento?.tipo ?: VehicleType.MOTORCYCLE,
        )
        val analisis = analizador.analizar(sesion, eventos, vehiculo.tipo)
        val guia = analisis.codigos.firstOrNull()?.let(baseDtc::guia)
        return armador.armar(
            sesion = sesion,
            eventos = eventos,
            vehiculo = vehiculo,
            analisis = analisis,
            conocimiento = conocimiento,
            guia = guia,
            versionApp = versionInstalada(),
        )
    }

    private fun versionInstalada(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull()?.takeIf(String::isNotBlank) ?: "desconocida"
}
