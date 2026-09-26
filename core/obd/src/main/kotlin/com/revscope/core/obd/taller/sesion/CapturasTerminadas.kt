package com.revscope.core.obd.taller.sesion

import com.revscope.core.obd.telemetry.captura.ResumenCaptura
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull

// El resumen vigente al suscribirse es de una captura vieja: ya se anotó (o no había sesión) cuando terminó.
fun StateFlow<ResumenCaptura?>.terminadasDesdeAhora(): Flow<ResumenCaptura> {
    val previa = value?.id
    return filterNotNull().filter { it.id != previa }.distinctUntilChangedBy { it.id }
}
