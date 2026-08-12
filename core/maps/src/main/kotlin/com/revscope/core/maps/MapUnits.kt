package com.revscope.core.maps

/**
 * osmdroid aplicaba `strokeWidth` directo al Canvas: eran píxeles FÍSICOS. MapLibre
 * interpreta `lineWidth` de forma densidad-independiente, así que copiar el número
 * crudo dibujaría la línea 3-4x más gruesa en una pantalla 3x o 4x.
 */
fun physicalPxToDp(physicalPx: Float, density: Float): Float =
    if (density <= 0f) physicalPx else physicalPx / density
