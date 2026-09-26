package com.revscope.core.obd.taller.pruebas

sealed interface EstadoPrueba {
    data object Inactiva : EstadoPrueba

    data class Verificando(val tipo: TipoPrueba, val precondiciones: List<ResultadoPrecondicion>) : EstadoPrueba {
        val listas: Boolean get() = precondiciones.all { it.cumple }
    }

    data class EnPaso(
        val tipo: TipoPrueba,
        val paso: PasoPrueba,
        val indice: Int,
        val total: Int,
        val fase: FasePaso,
        val restanteMs: Long?,
    ) : EstadoPrueba

    data class Analizando(val tipo: TipoPrueba) : EstadoPrueba

    data class Terminada(val resultado: ResultadoPrueba, val eventoId: Long?) : EstadoPrueba

    data class Cancelada(val tipo: TipoPrueba, val motivo: String) : EstadoPrueba

    data class Fallida(val tipo: TipoPrueba, val motivo: String, val reintentable: Boolean) : EstadoPrueba
}

enum class FasePaso { POSICIONANDO, SOSTENIENDO, GRABANDO }

val EstadoPrueba.enCurso: Boolean
    get() = this is EstadoPrueba.EnPaso || this is EstadoPrueba.Analizando
