package com.revscope.core.obd.taller.modelo

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SemillaAlArrancar @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sembrador: SembradorConocimiento,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun lanzar() {
        scope.launch {
            // Un catálogo dañado no debe tumbar la app: el Taller funciona sin conocimiento por modelo.
            runCatching { sembrador.sembrar(leerCatalogo()) }
                .onSuccess { acciones -> Timber.d("Conocimiento por modelo: %s", acciones) }
                .onFailure { Timber.e(it, "No se pudo sembrar el conocimiento por modelo") }
        }
    }

    private fun leerCatalogo(): String =
        context.assets.open(SembradorConocimiento.ASSET_CATALOGO).bufferedReader().use { it.readText() }
}
