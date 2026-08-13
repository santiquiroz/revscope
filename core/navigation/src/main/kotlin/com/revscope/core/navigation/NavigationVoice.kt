package com.revscope.core.navigation

/**
 * Cómo se dicen las instrucciones. Es una interfaz para que este módulo no arrastre el motor
 * de alertas ni un TTS propio: la app ya tiene resuelto que su voz llegue al intercomunicador
 * del casco, y meter un segundo motor con otras reglas de audio sería volver a resolverlo mal.
 */
fun interface NavigationVoice {
    fun say(text: String)
}
