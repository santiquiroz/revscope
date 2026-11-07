package com.revscope.feature.settings

import com.revscope.core.uitesting.XmlStringResolver
import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosticoTextosTest {

    private val es = XmlStringResolver.es(R::class.java)
    private val en = XmlStringResolver.en(R::class.java)

    @Test
    fun `spanish crash detection description keeps the original wording`() {
        assertEquals(
            "Si detecta un posible accidente de moto, suena una alarma de pantalla completa con " +
                "cuenta regresiva de 60 s. Si no respondes \"ESTOY BIEN\", envía un SMS con tu última " +
                "ubicación al contacto de emergencia. Desactivada por defecto.",
            es.get(
                R.string.settings_diagnostico_detect_description,
                es.get(R.string.settings_diagnostico_crash_subject_motorcycle),
            ),
        )
    }

    @Test
    fun `english crash detection description reads naturally`() {
        assertEquals(
            "If it detects a possible crash, it triggers a full-screen alarm with a 60-second countdown. " +
                "If you don't respond \"I'M OK\", it sends an SMS with your last location to the " +
                "emergency contact. Off by default.",
            en.get(
                R.string.settings_diagnostico_detect_description,
                en.get(R.string.settings_diagnostico_crash_subject_car),
            ),
        )
    }

    @Test
    fun `spanish mode 22 scan steps keep the original wording`() {
        assertEquals(
            "1. Escanea un rango con el vehículo encendido.\n" +
                "2. Pulsa Vigilar y cambia el modo de manejo: el identificador que " +
                "cambie de valor en ese momento es el del modo.\n" +
                "También puedes descubrir módulos y dirigir el escaneo a uno (p. ej. " +
                "buscar un DID de un subsistema de carrocería).",
            es.get(R.string.settings_diagnostico_scan_steps),
        )
    }

    @Test
    fun `spanish scan target prefix keeps the trailing space`() {
        assertEquals("Objetivo del escaneo: ", es.get(R.string.settings_diagnostico_target_scan, ""))
    }

    @Test
    fun `did range labels keep the hex range and translate the description`() {
        assertEquals("F180–F1FF (info ECU)", es.get(R.string.settings_diagnostico_range_ecu_info, "F180–F1FF"))
        assertEquals("F400–F4FF (mode 01 mirror)", en.get(R.string.settings_diagnostico_range_mode01_mirror, "F400–F4FF"))
    }

    @Test
    fun `spanish mcp copy toast keeps the original label`() {
        assertEquals(
            "URL MCP copiado",
            es.get(R.string.settings_diagnostico_copied, es.get(R.string.settings_diagnostico_mcp_url_clip)),
        )
    }
}
