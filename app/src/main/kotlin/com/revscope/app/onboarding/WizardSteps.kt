package com.revscope.app.onboarding

/** Aritmética de pasos del wizard — pura para poder testearla sin Android. */
object WizardSteps {
    const val TOTAL = 5
    fun next(current: Int): Int = (current + 1).coerceAtMost(TOTAL - 1)
    fun back(current: Int): Int = (current - 1).coerceAtLeast(0)
    fun clamp(step: Int): Int = step.coerceIn(0, TOTAL - 1)
}
