package com.revscope.core.uitesting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.util.Locale

class XmlStringResolverTest {

    object FakeR {
        object string {
            @JvmField
            val t_hola = 0x7f010001
        }
    }

    private fun res(lang: String, vararg rClasses: Class<*>) =
        XmlStringResolver(
            listOf(File("src/test/resources/res-fixture")),
            if (lang == "es") Locale("es") else Locale.US,
            rClasses.toList(),
        )

    @Test
    fun `es superpone values-es`() = assertEquals("Hola Ana", res("es").get("t_hola", "Ana"))

    @Test
    fun `en lee values`() = assertEquals("Hello Ana", res("en").get("t_hola", "Ana"))

    @Test
    fun `plural uno y varios`() {
        assertEquals("1 minuto", res("es").plural("t_min", 1, 1))
        assertEquals("3 minutes", res("en").plural("t_min", 3, 3))
    }

    @Test
    fun `clave sin traducción cae a values`() = assertEquals("Don't", res("es").get("t_apos"))

    @Test
    fun `por id usa la clase R`() =
        assertEquals("Hola Ana", res("es", FakeR::class.java).get(FakeR.string.t_hola, "Ana"))

    @Test
    fun `id inexistente lanza IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            res("es", FakeR::class.java).get(0x7f999999)
        }
    }
}
