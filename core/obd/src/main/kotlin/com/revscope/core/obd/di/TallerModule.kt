package com.revscope.core.obd.di

import com.revscope.core.obd.session.ObdSessionManager
import com.revscope.core.obd.taller.grafica.PreferenciasVref
import com.revscope.core.obd.taller.grafica.PreferenciasVrefDataStore
import com.revscope.core.obd.taller.pruebas.AnunciadorTaller
import com.revscope.core.obd.taller.pruebas.AnunciadorTallerVoz
import com.revscope.core.obd.taller.pruebas.CapturaPrueba
import com.revscope.core.obd.taller.pruebas.EnlacePrueba
import com.revscope.core.obd.taller.pruebas.EnlacePruebaObd
import com.revscope.core.obd.taller.pruebas.FuentesAnalisis
import com.revscope.core.obd.taller.pruebas.FuentesAnalisisApp
import com.revscope.core.obd.taller.pruebas.LectorAmbiente
import com.revscope.core.obd.taller.pruebas.LectorAmbienteAndroid
import com.revscope.core.obd.taller.pruebas.PreferenciasDesfase
import com.revscope.core.obd.taller.pruebas.PreferenciasDesfaseDataStore
import com.revscope.core.obd.taller.pruebas.RafagaVoltaje
import com.revscope.core.obd.taller.sesion.HistorialChequeos
import com.revscope.core.obd.taller.sesion.HistorialChequeosRoom
import com.revscope.core.obd.taller.sesion.TallerRepository
import com.revscope.core.obd.taller.sesion.TallerRepositoryRoom
import com.revscope.core.obd.taller.sesion.VehiculoActivo
import com.revscope.core.obd.taller.sesion.VehiculoActivoObd
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class TallerModule {

    @Binds
    abstract fun bindTallerRepository(impl: TallerRepositoryRoom): TallerRepository

    @Binds
    abstract fun bindVehiculoActivo(impl: VehiculoActivoObd): VehiculoActivo

    @Binds
    abstract fun bindHistorialChequeos(impl: HistorialChequeosRoom): HistorialChequeos

    @Binds
    abstract fun bindEnlacePrueba(impl: EnlacePruebaObd): EnlacePrueba

    @Binds
    abstract fun bindAnunciadorTaller(impl: AnunciadorTallerVoz): AnunciadorTaller

    @Binds
    abstract fun bindPreferenciasVref(impl: PreferenciasVrefDataStore): PreferenciasVref

    @Binds
    abstract fun bindPreferenciasDesfase(impl: PreferenciasDesfaseDataStore): PreferenciasDesfase

    @Binds
    abstract fun bindLectorAmbiente(impl: LectorAmbienteAndroid): LectorAmbiente

    @Binds
    abstract fun bindFuentesAnalisis(impl: FuentesAnalisisApp): FuentesAnalisis
}

@Module
@InstallIn(SingletonComponent::class)
object TallerPruebasModule {

    // La prueba guiada corre sobre la misma captura rápida del enlace vivo: una sola a la vez.
    @Provides
    fun provideCapturaPrueba(sessionManager: ObdSessionManager): CapturaPrueba = sessionManager.captura

    // La prueba de batería lee AT RV en ráfaga: comparte con la captura rápida el turno de una a la vez.
    @Provides
    @RafagaVoltaje
    fun provideRafagaVoltaje(sessionManager: ObdSessionManager): CapturaPrueba = sessionManager.rafagaVoltaje
}
