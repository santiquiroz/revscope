package com.revscope.core.obd.taller.modelo

import com.revscope.core.obd.taller.sesion.TallerRepository
import javax.inject.Inject

data class EstadoSemilla(val version: Int, val editadoPorUsuario: Boolean)

enum class AccionSemilla { INSERTAR, ACTUALIZAR, OMITIR_EDITADO, OMITIR_AL_DIA }

fun decidirAccionSemilla(existente: EstadoSemilla?, versionSemilla: Int): AccionSemilla = when {
    existente == null -> AccionSemilla.INSERTAR
    existente.editadoPorUsuario -> AccionSemilla.OMITIR_EDITADO
    existente.version >= versionSemilla -> AccionSemilla.OMITIR_AL_DIA
    else -> AccionSemilla.ACTUALIZAR
}

class SembradorConocimiento @Inject constructor(private val repositorio: TallerRepository) {

    suspend fun sembrar(catalogoJson: String): Map<String, AccionSemilla> =
        CodecConocimiento.leerCatalogo(catalogoJson).associate { semilla ->
            semilla.conocimiento.clave to sembrarModelo(semilla)
        }

    private suspend fun sembrarModelo(semilla: ModeloSemilla): AccionSemilla {
        val estado = repositorio.estadoSemilla(semilla.conocimiento.clave)
        val accion = decidirAccionSemilla(estado, semilla.version)
        if (accion == AccionSemilla.INSERTAR || accion == AccionSemilla.ACTUALIZAR) {
            repositorio.aplicarSemilla(semilla)
        }
        return accion
    }

    companion object {
        const val ASSET_CATALOGO = "taller/conocimiento_modelos.json"
    }
}
