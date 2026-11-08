package com.revscope.core.obd.catalogo

import android.content.Context
import androidx.annotation.StringRes
import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CatalogoPropietario internal constructor(
    private val cargarAsset: () -> String?,
    private val archivoUsuario: File,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        { context.assets.open(NOMBRE_ASSET).bufferedReader().use { it.readText() } },
        File(context.filesDir, NOMBRE_USUARIO),
    )

    private val asset by lazy {
        cargarAsset()?.let(CodecCatalogo::leer) ?: (null to emptyList())
    }

    @Volatile
    private var cacheUsuario: List<EntradaCatalogo>? = null

    fun aviso(): String? = asset.first

    @Synchronized
    fun todas(): List<EntradaCatalogo> {
        val usuario = usuario()
        val porId = LinkedHashMap<String, EntradaCatalogo>()
        asset.second.forEach { porId[it.id] = it }
        usuario.forEach { porId[it.id] = it }
        return porId.values.toList()
    }

    fun porId(id: String): EntradaCatalogo? = todas().firstOrNull { it.id == id }

    fun buscar(marca: String?, modelo: String?, tipo: String?, texto: String?): List<EntradaCatalogo> {
        val lista = todas()
        return lista.filter { entrada ->
            coincide(entrada.marca, marca) &&
                (modelo.isNullOrBlank() || entrada.modelos.any { it.contains(modelo.trim(), true) }) &&
                coincide(entrada.tipo, tipo) &&
                coincide(textoBuscable(entrada), texto)
        }
    }

    @Synchronized
    fun guardarUsuario(entrada: EntradaCatalogo, resolver: StringResolver): String? {
        ValidadorCatalogo.validar(entrada, resolver)?.let { return it }
        val actualizadas = usuario().filterNot { it.id == entrada.id } + entrada
        return try {
            archivoUsuario.parentFile?.mkdirs()
            archivoUsuario.writeText(CodecCatalogo.escribir(aviso(), actualizadas))
            cacheUsuario = actualizadas
            null
        } catch (e: IOException) {
            noSePudoGuardar(e, R.string.obd_catalogo_val_error_escritura, resolver)
        } catch (e: SecurityException) {
            noSePudoGuardar(e, R.string.obd_catalogo_val_acceso_denegado, resolver)
        }
    }

    private fun noSePudoGuardar(e: Exception, @StringRes porDefecto: Int, resolver: StringResolver): String =
        resolver.get(R.string.obd_catalogo_val_no_se_pudo_guardar, e.message ?: resolver.get(porDefecto))

    fun resumenPorMarca(): Map<String, Int> =
        todas().groupingBy { it.marca }.eachCount()

    @Synchronized
    private fun usuario(): List<EntradaCatalogo> {
        cacheUsuario?.let { return it }
        val entradas = if (archivoUsuario.isFile) {
            runCatching { CodecCatalogo.leer(archivoUsuario.readText()).second }
                .getOrElse { throw IOException("no se pudo leer el catálogo del usuario", it) } // i18n-ignore
        } else {
            emptyList()
        }
        cacheUsuario = entradas
        return entradas
    }

    private fun coincide(valor: String, filtro: String?): Boolean =
        filtro.isNullOrBlank() || valor.contains(filtro.trim(), ignoreCase = true)

    private fun textoBuscable(entrada: EntradaCatalogo): String =
        listOfNotNull(entrada.id, entrada.descripcion, entrada.modulo, entrada.notas).joinToString(" ")

    private companion object {
        const val NOMBRE_ASSET = "catalogo_propietario.json"
        const val NOMBRE_USUARIO = "catalogo_usuario.json"
    }
}
