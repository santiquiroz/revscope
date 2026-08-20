package com.revscope.core.maps

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Único punto de verdad para "¿hay WiFi activo ahora?" — antes duplicado verbatim en
 * MapsModule (isOnWifi que le pasa a MapDownloadService) y en SettingsViewModel.isOnWifiNow
 * (para elegir qué diálogo de confirmación mostrar antes de arrancar la descarga).
 */
fun isOnWifi(context: Context): Boolean {
    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
}
