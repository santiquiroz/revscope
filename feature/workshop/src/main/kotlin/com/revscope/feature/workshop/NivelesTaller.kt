package com.revscope.feature.workshop

import androidx.annotation.StringRes
import com.revscope.core.designsystem.NivelEstado
import com.revscope.core.obd.legal.DocumentStatusCalculator
import com.revscope.core.obd.workshop.DiagnosticRules

internal data class NivelConTexto(val nivel: NivelEstado, @StringRes val textoRes: Int)

internal fun nivelDocumento(nivel: DocumentStatusCalculator.Nivel): NivelConTexto = when (nivel) {
    DocumentStatusCalculator.Nivel.OK -> NivelConTexto(NivelEstado.OK, R.string.workshop_health_level_ok)
    DocumentStatusCalculator.Nivel.ATENCION -> NivelConTexto(NivelEstado.ATENCION, R.string.workshop_health_level_attention)
    DocumentStatusCalculator.Nivel.VENCIDO -> NivelConTexto(NivelEstado.FALLA, R.string.workshop_health_level_expired)
    DocumentStatusCalculator.Nivel.SIN_CONFIGURAR -> NivelConTexto(
        NivelEstado.SIN_DATO,
        R.string.workshop_health_level_not_configured,
    )
}

internal fun nivelDiagnostico(nivel: DiagnosticRules.Nivel): NivelEstado = when (nivel) {
    DiagnosticRules.Nivel.OK -> NivelEstado.OK
    DiagnosticRules.Nivel.ATENCION -> NivelEstado.ATENCION
    DiagnosticRules.Nivel.FALLA -> NivelEstado.FALLA
}
