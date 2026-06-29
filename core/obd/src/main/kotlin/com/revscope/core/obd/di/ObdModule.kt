package com.revscope.core.obd.di

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import com.revscope.core.obd.pid.PidRegistry
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ObdModule {

    @Provides
    @Singleton
    fun provideBluetoothAdapter(@ApplicationContext context: Context): BluetoothAdapter? {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
        return manager?.adapter
    }

    @Provides
    @Singleton
    fun providePidRegistry(@ApplicationContext context: Context): PidRegistry {
        val json = context.assets.open("pids_mode01.json").bufferedReader().readText()
        return PidRegistry(json)
    }
}
