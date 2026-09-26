package com.revscope.core.obd.taller.sesion

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

// filesDir y no cacheDir: el sistema puede vaciar la caché y la sesión perdería sus CSV.
@Singleton
class AdjuntosTaller(private val raiz: File) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(File(context.filesDir, CARPETA))

    fun carpeta(sesionId: Long): File = File(raiz, sesionId.toString())

    suspend fun copiar(sesionId: Long, origen: File): String? = withContext(Dispatchers.IO) {
        if (!origen.isFile) return@withContext null
        val destino = File(carpeta(sesionId).apply { mkdirs() }, origen.name)
        origen.copyTo(destino, overwrite = true).absolutePath
    }

    suspend fun borrar(sesionId: Long): Boolean = withContext(Dispatchers.IO) { carpeta(sesionId).deleteRecursively() }

    companion object {
        const val CARPETA = "taller"
    }
}
