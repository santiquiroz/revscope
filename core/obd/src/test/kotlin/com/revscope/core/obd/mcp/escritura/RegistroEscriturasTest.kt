package com.revscope.core.obd.mcp.escritura

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RegistroEscriturasTest {

    @get:Rule
    val temporal = TemporaryFolder()

    @Test
    fun `registrar conserva campos y entrega primero la entrada mas reciente`() {
        val registro = registro()
        val anterior = entrada(ts = 1, header = "7E0", error = "timeout")
        val reciente = entrada(ts = 2, header = null, error = null)

        registro.registrar(anterior)
        registro.registrar(reciente)

        assertEquals(listOf(reciente, anterior), registro.ultimas(10))
    }

    @Test
    fun `conserva solo las ultimas quinientas entradas`() {
        val registro = registro()
        repeat(RegistroEscrituras.MAX_ENTRADAS + 1) { registro.registrar(entrada(ts = it.toLong())) }

        val entradas = registro.ultimas(RegistroEscrituras.MAX_ENTRADAS + 1)

        assertEquals(RegistroEscrituras.MAX_ENTRADAS, entradas.size)
        assertEquals(RegistroEscrituras.MAX_ENTRADAS.toLong(), entradas.first().ts)
        assertEquals(1L, entradas.last().ts)
    }

    @Test
    fun `omite lineas corruptas`() {
        val archivo = File(temporal.root, "escrituras.jsonl")
        val registro = RegistroEscrituras(archivo)
        val valida = entrada(ts = 1)
        registro.registrar(valida)
        archivo.appendText("{linea corrupta}\n")
        registro.registrar(entrada(ts = 2))

        assertEquals(listOf(entrada(ts = 2), valida), registro.ultimas(10))
    }

    @Test
    fun `ultimas con cero devuelve una lista vacia`() {
        val registro = registro().apply { registrar(entrada()) }

        assertTrue(registro.ultimas(0).isEmpty())
    }

    private fun registro() = RegistroEscrituras(File(temporal.root, "escrituras.jsonl"))

    private fun entrada(
        ts: Long = 1,
        header: String? = null,
        error: String? = null,
    ) = EntradaRegistro(
        ts = ts,
        tool = "comando_escritura",
        resumen = "prueba $ts",
        header = header,
        pasos = listOf("14 FF FF FF", "11 03"),
        autorizacion = "TOQUE",
        respuestas = listOf("54", "51 03"),
        error = error,
    )
}
