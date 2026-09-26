package com.revscope.feature.workshop.taller

import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.session.ObdSessionManager
import com.revscope.core.obd.taller.sesion.VehiculoTaller
import com.revscope.core.obd.taller.sesion.aVehiculoTaller
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.ZoneId
import javax.inject.Inject

// Lo que las pantallas del Taller leen del resto de la app, detrás de una interfaz para probar los ViewModels sin ECU.
interface EntornoTaller {
    val vehiculo: Flow<VehiculoTaller?>
    val conexion: Flow<ConnectionState>
    val zona: ZoneId

    fun ahora(): Long

    fun odometroEcuKm(): Double?

    fun lecturas(): Map<String, ObdReading>
}

class EntornoTallerObd @Inject constructor(private val manager: ObdSessionManager) : EntornoTaller {
    override val vehiculo: Flow<VehiculoTaller?> = manager.activeProfile.map { it?.aVehiculoTaller() }
    override val conexion: Flow<ConnectionState> = manager.connectionState
    override val zona: ZoneId get() = ZoneId.systemDefault()

    override fun ahora(): Long = System.currentTimeMillis()

    override fun odometroEcuKm(): Double? = manager.odometerCheck.value?.reading?.km

    override fun lecturas(): Map<String, ObdReading> = manager.readings.value
}

@Module
@InstallIn(SingletonComponent::class)
abstract class EntornoTallerModule {
    @Binds
    abstract fun bindEntornoTaller(impl: EntornoTallerObd): EntornoTaller
}
