package com.revscope.core.maps

/** Resultado de intentar promover el `.part` completo al nombre final. */
enum class MapDownloadPromoteOutcome { PROMOTED, RENAME_FAILED }

/**
 * Decisiones puras del ciclo de vida de la descarga del mapa offline — sin Android ni IO, para
 * poder testearlas con valores arbitrarios. [MapDownloadService] mide espacio real, conectividad
 * real y ejecuta el rename; este object solo decide qué hacer con esos datos ya medidos.
 */
object MapDownloadDecider {

    /** Margen sobre el tamaño esperado: cubre journaling del filesystem y el margen de error
     * del tamaño estimado antes de conocer el Content-Length real. */
    private const val SPACE_MARGIN_FACTOR = 1.2

    /** true si el espacio libre alcanza para [totalSizeBytes] más el margen de seguridad. */
    fun canStart(usableSpaceBytes: Long, totalSizeBytes: Long): Boolean =
        usableSpaceBytes > totalSizeBytes * SPACE_MARGIN_FACTOR

    /** true si la descarga debe frenar por venir de datos móviles sin permiso explícito. */
    fun shouldBlockOnCellular(allowCellular: Boolean, isOnWifi: Boolean): Boolean =
        !isOnWifi && !allowCellular

    /** Qué hacer tras intentar el rename atómico `.part` → nombre final. */
    fun promoteOutcome(renameOk: Boolean): MapDownloadPromoteOutcome =
        if (renameOk) MapDownloadPromoteOutcome.PROMOTED else MapDownloadPromoteOutcome.RENAME_FAILED
}
