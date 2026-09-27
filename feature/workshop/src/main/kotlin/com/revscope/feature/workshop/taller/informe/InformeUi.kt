package com.revscope.feature.workshop.taller.informe

data class InformeUi(
    val cargando: Boolean = true,
    val titulo: String = "Informe de taller",
    val html: String = "",
    val interpretacion: String = "",
    val editorVisible: Boolean = false,
    val borradorInterpretacion: String = "",
    val error: String? = null,
    val mensaje: String? = null,
)

data class AccionesInforme(
    val onVolver: () -> Unit,
    val onReintentar: () -> Unit = {},
    val onCompartir: () -> Unit = {},
    val onImprimir: () -> Unit = {},
    val onEditarInterpretacion: () -> Unit = {},
    val onCambiarInterpretacion: (String) -> Unit = {},
    val onGuardarInterpretacion: () -> Unit = {},
    val onCancelarEdicion: () -> Unit = {},
)
