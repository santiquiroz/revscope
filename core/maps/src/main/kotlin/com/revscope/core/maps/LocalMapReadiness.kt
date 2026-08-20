package com.revscope.core.maps

/**
 * Deriva si el `.pmtiles` local sigue usable a partir de las transiciones de
 * [MapDownloadState] — pura y testeada sin Android, separada de [MapDownloadService] a
 * propósito para poder testear la regla sin tocar disco ni corrutinas.
 *
 * El bug que esto corrige: leer `state is Idle && exists` en cada emisión hacía que, apenas
 * arrancaba una RE-descarga (`Downloading`), el mapa cayera a ráster durante los ~911 MB de la
 * descarga — aunque el `.pmtiles` VIEJO seguía intacto en disco (la escritura va a `.part`, el
 * rename atómico recién reemplaza el archivo al completar). `Downloading`/`Error` no cambian lo
 * que hay en disco respecto de antes de empezar: conservan [prev]. Solo `Idle` es autoritativo,
 * porque sale de `idleStateFromDisk()`, que sí leyó el filesystem.
 */
object LocalMapReadiness {
    fun step(prev: Boolean, state: MapDownloadState): Boolean = when (state) {
        is MapDownloadState.Idle -> state.exists
        is MapDownloadState.Downloading -> prev
        is MapDownloadState.Error -> prev
    }
}
