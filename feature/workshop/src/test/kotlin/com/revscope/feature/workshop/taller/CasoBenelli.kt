package com.revscope.feature.workshop.taller

import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.taller.dtc.AccionGuia
import com.revscope.core.obd.taller.multimetro.SensorMultimetro
import com.revscope.core.obd.taller.pruebas.TipoPrueba
import com.revscope.core.obd.taller.sesion.AnalisisSesion
import com.revscope.core.obd.taller.sesion.ChequeoRegistrado
import com.revscope.core.obd.taller.sesion.ComparadorChequeo
import com.revscope.core.obd.taller.sesion.EventoTaller
import com.revscope.core.obd.taller.sesion.OrigenEvento
import com.revscope.core.obd.taller.sesion.PruebaSugerida
import com.revscope.core.obd.taller.sesion.SesionTaller
import com.revscope.core.obd.taller.sesion.Sintoma
import com.revscope.core.obd.taller.sesion.TipoEvento
import com.revscope.core.obd.taller.sesion.UmbralesComparacion
import com.revscope.core.obd.taller.sesion.Veredicto
import com.revscope.core.obd.workshop.MetricasChequeo

// Datos del caso real del 25-sep-2026 (Benelli TNT 150i, P0122) tal como quedarían en una sesión.
internal object CasoBenelli {

    private const val MINUTO = 60_000L
    private const val JULIO_11 = 1_783_810_800_000L

    val sesion = SesionTaller(
        id = 12,
        vehiculoId = BENELLI.id,
        claveModelo = BENELLI.claveModelo,
        inicio = SEP25_20H + 5 * MINUTO,
        titulo = "No sostiene el mínimo en frío",
        sintomas = setOf(Sintoma.NO_SOSTIENE_MINIMO_FRIO, Sintoma.SE_APAGA_AL_SOLTAR, Sintoma.SE_AHOGA_AL_ACELERAR),
        sintomasTexto = "El TPS se cambió hace unos meses por uno genérico y desde entonces se apaga al soltar el acelerador",
        notas = "Adaptador vLinker FS. Farola siempre encendida con contacto puesto.",
        odometroKm = 18_420.0,
        chequeoBaseId = 3,
    )

    private fun evento(id: Long, minuto: Long, tipo: TipoEvento, titulo: String, resumen: String, veredicto: Veredicto) =
        EventoTaller(
            id = id,
            sesionId = sesion.id,
            instante = SEP25_20H + minuto * MINUTO,
            tipo = tipo,
            titulo = titulo,
            resumen = resumen,
            veredicto = veredicto,
            origen = if (tipo == TipoEvento.NOTA) OrigenEvento.MCP else OrigenEvento.APP,
            adjunto = if (tipo == TipoEvento.CAPTURA) "/data/taller/12/captura-tps-20260925-2014.csv" else null,
            payloadJson = if (tipo == TipoEvento.DTC_LECTURA) """{"activos":["P0122"],"milEncendida":true}""" else "{}",
        )

    val eventos = listOf(
        evento(
            1, 5, TipoEvento.SINTOMAS, "Síntomas",
            "No sostiene el mínimo en frío · Se apaga al soltar el acelerador · Se ahoga al acelerar · El TPS se cambió " +
                "hace unos meses por uno genérico",
            Veredicto.INFO,
        ),
        evento(2, 8, TipoEvento.DTC_LECTURA, "Lectura de códigos", "Activos: P0122 · testigo encendido", Veredicto.FALLA),
        evento(
            3, 14, TipoEvento.CAPTURA, "Captura rápida · 3 PIDs · 45,0 s",
            "TPS cerrado 2,35 %, medio 9,0–9,4 %, a fondo 17,6–18,0 %, al final 2,75 %. Con el acelerador casi cerrado " +
                "el mínimo bajó de 2010 a 1454 rpm en unos 9 s.",
            Veredicto.ATENCION,
        ),
        evento(
            4, 26, TipoEvento.INSTANTANEA_SENSORES, "Instantánea de sensores",
            "Temperatura del motor 32 °C · Aire de admisión 27 °C · Presión del múltiple 70 kPa · Batería 12,2 V",
            Veredicto.INFO,
        ),
        evento(
            5, 40, TipoEvento.NOTA, "Nota",
            "Multímetro por detrás del conector, contacto puesto: rojo 5,0 V y negro 0 V en todo el recorrido; " +
                "verde-amarillo 0,1 V cerrado, 0,3–0,4 V medio y 0,8 V a fondo. Alimentación y masa bien; la señal es " +
                "unas 5 veces más baja.",
            Veredicto.INFO,
        ),
        evento(6, 52, TipoEvento.CHEQUEO, "Chequeo de salud", "1 falla · 1 atención · 3 OK", Veredicto.FALLA),
    )

    private val base = MetricasChequeo(
        stft = -6.2, ltft = 2.3, voltaje = 13.7, motorEncendido = true, ect = 79.0,
        monitoresCompletos = 5, monitoresTotales = 5, dtcs = emptyList(), dtcsLeidos = true,
    )

    private val ahora = MetricasChequeo(
        voltaje = 14.2, motorEncendido = true, ect = 45.0,
        monitoresCompletos = 5, monitoresTotales = 5, dtcs = listOf("P0122"), dtcsLeidos = true,
    )

    private val analisis = AnalisisSesion(
        codigos = listOf("P0122"),
        chequeoBase = ChequeoRegistrado(3, BENELLI.id, JULIO_11, emptyList(), base),
        metricasAhora = ahora,
        comparacion = ComparadorChequeo.comparar(base, ahora, UmbralesComparacion.desde(emptyMap())),
        sugeridas = listOf(
            PruebaSugerida(AccionGuia.Prueba(TipoPrueba.TPS_BARRIDO), listOf("P0122", "«Se ahoga al acelerar»")),
            PruebaSugerida(AccionGuia.Multimetro(SensorMultimetro.TPS), listOf("P0122", "«Se ahoga al acelerar»")),
            PruebaSugerida(AccionGuia.Prueba(TipoPrueba.MINIMO_RETORNO), listOf("«Se apaga al soltar el acelerador»")),
            PruebaSugerida(AccionGuia.Prueba(TipoPrueba.ARRANQUE_FRIO), listOf("«No sostiene el mínimo en frío»")),
        ),
    )

    fun estadoSesion(ui: UiSesion = UiSesion(eventosAbiertos = setOf(3))): SesionTallerEstado =
        EstadoPantallaSesion.de(DatosSesion(sesion, eventos.shuffled(java.util.Random(4)), analisis, BENELLI, conectado = true), ui, BOGOTA)

    fun datosHub(abierta: Boolean): DatosHub {
        val cerradas = listOf(
            SesionTaller(id = 9, vehiculoId = BENELLI.id, inicio = JULIO_11, cierre = JULIO_11 + 50 * MINUTO, titulo = "Chequeo de rutina"),
            SesionTaller(id = 7, vehiculoId = BENELLI.id, inicio = JULIO_11 - 40L * 86_400_000, cierre = JULIO_11 - 40L * 86_400_000 + 30 * MINUTO, titulo = "Se descarga la batería"),
            SesionTaller(id = 5, vehiculoId = BENELLI.id, inicio = JULIO_11 - 90L * 86_400_000, cierre = JULIO_11 - 90L * 86_400_000 + 20 * MINUTO, titulo = "Testigo de falla encendido"),
            SesionTaller(id = 2, vehiculoId = BENELLI.id, inicio = JULIO_11 - 200L * 86_400_000, cierre = JULIO_11 - 200L * 86_400_000 + 20 * MINUTO, titulo = "Pierde potencia"),
        )
        return if (abierta) DatosHub(BENELLI, cerradas + sesion, eventos) else DatosHub(BENELLI, cerradas, emptyList())
    }

    val conectado = ConnectionState.Connected("vLinker FS")
}
