package com.revscope.core.maps.di

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.revscope.core.maps.MapDownloadService
import com.revscope.core.maps.MapStyleProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MapsModule {

    @Provides
    @Singleton
    fun provideMapDownloadService(@ApplicationContext context: Context): MapDownloadService {
        // mkdirs antes de construir: MapDownloadService.usableSpaceBytes por defecto lee
        // mapsDir.usableSpace, que devuelve 0 si el directorio todavía no existe.
        val mapsDir = File(context.filesDir, MapStyleProvider.MAPS_DIR_NAME).apply { mkdirs() }
        return MapDownloadService(
            mapsDir = mapsDir,
            isOnWifi = { isOnWifi(context) },
        )
    }

    private fun isOnWifi(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}
