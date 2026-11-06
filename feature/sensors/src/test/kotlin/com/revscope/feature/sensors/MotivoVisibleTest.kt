package com.revscope.feature.sensors

import com.revscope.core.obd.R as ObdR
import com.revscope.core.obd.telemetry.captura.CapturaRapida
import com.revscope.core.obd.telemetry.captura.MuestreadorVoltaje
import com.revscope.core.uitesting.XmlStringResolver
import org.junit.Assert.assertEquals
import org.junit.Test

class MotivoVisibleTest {

    private val english = XmlStringResolver.en(
        R::class.java,
        ObdR::class.java,
        resDirs = listOf("src/main/res", "../../core/obd/src/main/res"),
    )
    private val spanish = XmlStringResolver.es(
        R::class.java,
        ObdR::class.java,
        resDirs = listOf("src/main/res", "../../core/obd/src/main/res"),
    )

    @Test
    fun `capture end reasons use the selected language and preserve link details`() {
        assertEquals(
            english.get(R.string.sensors_capture_reason_user),
            motivoVisible(CapturaRapida.MOTIVO_USUARIO, english),
        )
        assertEquals(
            spanish.get(R.string.sensors_capture_reason_user),
            motivoVisible(CapturaRapida.MOTIVO_USUARIO, spanish),
        )
        assertEquals(
            "${english.get(R.string.sensors_capture_reason_link_lost)}: detail",
            motivoVisible("${CapturaRapida.MOTIVO_ENLACE}: detail", english),
        )
        assertEquals(
            "${spanish.get(R.string.sensors_capture_reason_link_lost)}: detail",
            motivoVisible("${CapturaRapida.MOTIVO_ENLACE}: detail", spanish),
        )
        assertEquals(
            english.get(R.string.sensors_capture_reason_duration),
            motivoVisible(MuestreadorVoltaje.MOTIVO_DURACION, english),
        )
        assertEquals(
            english.get(R.string.sensors_capture_reason_no_response),
            motivoVisible(MuestreadorVoltaje.MOTIVO_SIN_RESPUESTA, english),
        )
    }

    @Test
    fun `unrecognized or already localized reasons pass through unchanged`() {
        assertEquals("capture complete", motivoVisible("capture complete", english))
        assertEquals("captura completada", motivoVisible("captura completada", spanish))
    }
}
