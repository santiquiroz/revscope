package com.revscope.feature.settings

import com.revscope.core.uitesting.XmlStringResolver
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsSectionsStringsTest {

    @Test
    fun `bypass warning is available in English and retains the current Spanish text`() {
        assertEquals(
            "Any client with the MCP token could clear codes, reset modules, and send manufacturer commands to the ECU without your approval. A wrong command can leave a module unusable until it is serviced by a dealer. The stopped-vehicle safeguards and flash blocking remain active.",
            XmlStringResolver.en(R::class.java).get(R.string.settings_mcp_bypass_warning),
        )
        assertEquals(
            "Cualquier cliente con el token del MCP podrá borrar códigos, reiniciar módulos y mandar comandos de fabricante a la ECU sin que lo apruebes. Un comando equivocado puede dejar un módulo inservible hasta llevarlo al concesionario. Las guardas de vehículo detenido y el bloqueo de flasheo siguen activos.",
            XmlStringResolver.es(R::class.java).get(R.string.settings_mcp_bypass_warning),
        )
    }
}
