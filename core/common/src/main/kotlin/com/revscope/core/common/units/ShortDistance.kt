package com.revscope.core.common.units

enum class DistanceUnit(val label: String) { METERS("m"), FEET("ft"), KILOMETERS("km"), MILES("mi") }

data class Measure(val value: Double, val unit: DistanceUnit)
