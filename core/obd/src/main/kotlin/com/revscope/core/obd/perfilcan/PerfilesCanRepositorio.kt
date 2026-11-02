package com.revscope.core.obd.perfilcan

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PerfilesCanRepositorio internal constructor(
    private val carpeta: File,
    private val incluidos: () -> List<String>,
) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(
        File(context.filesDir, CARPETA),
        { leerAssets(context) },
    )

    private val _cambios = MutableStateFlow(0)
    val cambios: StateFlow<Int> = _cambios.asStateFlow()

    private val perfilesIncluidos: List<PerfilCan> by lazy { incluidos().mapNotNull { parsear(it, "incluido") } }

    @Synchronized
    fun disponibles(): List<PerfilCan> {
        val propios = propios()
        val idsPropios = propios.map { it.id }.toSet()
        return propios + perfilesIncluidos.filter { it.id !in idsPropios }
    }

    @Synchronized
    fun leer(id: String): PerfilCan? {
        if (!CodecPerfilCan.ID_PERFIL.matches(id)) return null
        return leerPropio(id) ?: incluido(id)
    }

    @Synchronized
    fun adoptar(id: String): PerfilCan {
        exigeIdValido(id)
        leerPropio(id)?.let { return it }
        val base = incluido(id) ?: throw IllegalArgumentException("id: no hay un perfil incluido '$id'")
        val copia = base.copy(senales = base.senales.map { it.copy(estado = EstadoSenal.SIN_VERIFICAR) })
        guardar(copia)
        return copia
    }

    @Synchronized
    fun marcarEstado(id: String, clave: String, estado: EstadoSenal): PerfilCan {
        val perfil = adoptar(id)
        require(perfil.senales.any { it.clave == clave }) { "clave: el perfil '$id' no tiene la señal $clave" }
        val actualizado = perfil.copy(
            senales = perfil.senales.map { if (it.clave == clave) it.copy(estado = estado) else it },
        )
        guardar(actualizado)
        return actualizado
    }

    private fun guardar(perfil: PerfilCan) {
        carpeta.mkdirs()
        archivo(perfil.id).writeText(CodecPerfilCan.escribir(perfil))
        _cambios.update { it + 1 }
    }

    private fun propios(): List<PerfilCan> =
        (carpeta.listFiles { f -> f.extension == "json" } ?: emptyArray())
            .sortedBy { it.name }
            .mapNotNull { leerArchivo(it) }

    private fun leerPropio(id: String): PerfilCan? = archivo(id).takeIf { it.exists() }?.let(::leerArchivo)

    private fun incluido(id: String): PerfilCan? = perfilesIncluidos.firstOrNull { it.id == id }

    private fun leerArchivo(f: File): PerfilCan? =
        runCatching { f.readText() }.getOrNull()?.let { parsear(it, f.name) }

    private fun parsear(json: String, origen: String): PerfilCan? =
        CodecPerfilCan.leer(json)
            .onFailure { Timber.w("PerfilesCanRepositorio: se ignora %s (%s)", origen, it.message) }
            .getOrNull()

    private fun archivo(id: String) = File(carpeta, "$id.json")

    private fun exigeIdValido(id: String) =
        require(CodecPerfilCan.ID_PERFIL.matches(id)) { "id: '$id' no es un id de perfil válido" }

    companion object {
        const val CARPETA = "perfiles_can"

        private fun leerAssets(context: Context): List<String> =
            context.assets.list(CARPETA).orEmpty()
                .filter { it.endsWith(".json") }
                .map { nombre -> context.assets.open("$CARPETA/$nombre").bufferedReader().use { it.readText() } }
    }
}
