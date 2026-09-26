package com.revscope.core.obd.taller.pruebas

import com.revscope.core.obd.taller.pruebas.AnalizadorBarridoTps.Pasos

// Las pruebas guiadas que ya tienen analizador; las demás de TipoPrueba llegan en sus propias tareas.
object CatalogoPruebas {

    private const val SOSTENER_MS = 5_000L
    private const val BARRIDO_LENTO_MS = 8_000L
    private const val PID_TPS_RELATIVO = "45"

    val barridoTps = DefinicionPrueba(
        tipo = TipoPrueba.TPS_BARRIDO,
        pids = listOf(AnalizadorBarridoTps.PID_TPS),
        pidsOpcionales = listOf(PID_TPS_RELATIVO),
        precondiciones = listOf(
            Precondicion.AdaptadorConectado,
            Precondicion.MotorApagado,
            Precondicion.MotoDetenida,
            Precondicion.PidDisponible(
                AnalizadorBarridoTps.PID_TPS,
                "TPS",
                "Mide la señal del TPS con el multímetro (plantilla TPS)",
            ),
        ),
        pasos = listOf(
            sostener(Pasos.CERRADO_1, "Suelta el acelerador por completo y no lo toques"),
            sostener(Pasos.MEDIO, "Lleva el acelerador a la mitad del recorrido y sostenlo quieto"),
            sostener(Pasos.A_FONDO, "Abre el acelerador hasta el tope y sostenlo"),
            sostener(Pasos.CERRADO_2, "Suelta el acelerador por completo otra vez"),
            PasoPrueba(
                clave = Pasos.BARRIDO_LENTO,
                titulo = TextosBarridoTps.TITULOS_PASO.getValue(Pasos.BARRIDO_LENTO),
                instruccion = "De cerrado a fondo y de vuelta, despacio y parejo",
                modo = ModoPaso.Grabar(BARRIDO_LENTO_MS),
                descartarInicioMs = 0,
            ),
        ),
        analizar = AnalizadorBarridoTps::analizar,
    )

    private val definiciones = listOf(barridoTps).associateBy { it.tipo }

    val disponibles: List<TipoPrueba> get() = TipoPrueba.entries.filter { it in definiciones }

    fun definicion(tipo: TipoPrueba): DefinicionPrueba? = definiciones[tipo]

    private fun sostener(clave: String, instruccion: String) = PasoPrueba(
        clave = clave,
        titulo = TextosBarridoTps.TITULOS_PASO.getValue(clave),
        instruccion = instruccion,
        modo = ModoPaso.Sostener(SOSTENER_MS),
    )
}
