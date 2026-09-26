package com.revscope.feature.dashboard.gauges

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.min

// Un gauge nunca más ancho que su columna: en 360 dp la del velocímetro mide ~110 dp, no 160.
internal fun ladoAcotado(preferido: Dp, disponible: Dp): Dp =
    if (disponible == Dp.Infinity) preferido else min(preferido, disponible)
