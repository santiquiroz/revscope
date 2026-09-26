package com.revscope.feature.workshop

import com.revscope.core.designsystem.NivelEstado
import com.revscope.core.obd.legal.DocumentStatusCalculator
import com.revscope.core.obd.workshop.DiagnosticRules

internal data class NivelConTexto(val nivel: NivelEstado, val texto: String)

internal fun nivelDocumento(nivel: DocumentStatusCalculator.Nivel): NivelConTexto = when (nivel) {
    DocumentStatusCalculator.Nivel.OK -> NivelConTexto(NivelEstado.OK, "OK")
    DocumentStatusCalculator.Nivel.ATENCION -> NivelConTexto(NivelEstado.ATENCION, "Atención")
    DocumentStatusCalculator.Nivel.VENCIDO -> NivelConTexto(NivelEstado.FALLA, "Vencido")
    DocumentStatusCalculator.Nivel.SIN_CONFIGURAR -> NivelConTexto(NivelEstado.SIN_DATO, "Sin configurar")
}

internal fun nivelDiagnostico(nivel: DiagnosticRules.Nivel): NivelEstado = when (nivel) {
    DiagnosticRules.Nivel.OK -> NivelEstado.OK
    DiagnosticRules.Nivel.ATENCION -> NivelEstado.ATENCION
    DiagnosticRules.Nivel.FALLA -> NivelEstado.FALLA
}
