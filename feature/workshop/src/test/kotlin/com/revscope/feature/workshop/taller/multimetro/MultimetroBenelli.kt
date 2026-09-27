package com.revscope.feature.workshop.taller.multimetro

import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.obd.taller.modelo.CableModelo
import com.revscope.core.obd.taller.modelo.CableadoSensor
import com.revscope.core.obd.taller.modelo.ConocimientoModelo
import com.revscope.core.obd.taller.multimetro.CondicionesMultimetro
import com.revscope.core.obd.taller.multimetro.ContextoMultimetro
import com.revscope.core.obd.taller.multimetro.FuncionCable
import com.revscope.core.obd.taller.multimetro.LecturasEcu
import com.revscope.core.obd.taller.multimetro.ModeloReferencia
import com.revscope.core.obd.taller.multimetro.ResolutorPlantilla
import com.revscope.core.obd.taller.multimetro.SensorMultimetro
import com.revscope.core.obd.taller.referencia.BandasTipicas
import com.revscope.core.obd.taller.sesion.SesionTaller
import com.revscope.core.obd.taller.sesion.TipoEvento
import com.revscope.core.obd.taller.sesion.NuevoEvento
import com.revscope.feature.workshop.taller.BENELLI
import com.revscope.feature.workshop.taller.SEP25_20H
import org.json.JSONObject

// El caso del 25-sep-2026: cableado del TPS medido por el dueño y lo que marcó el multímetro por detrás del conector.
object MultimetroBenelli {

    val cableadoTps = CableadoSensor(
        sensor = "TPS",
        titulo = "Sensor de posición del acelerador (3 cables)",
        cables = listOf(
            CableModelo(FuncionCable.REF_5V, "Rojo"),
            CableModelo(FuncionCable.MASA, "Negro"),
            CableModelo(FuncionCable.SENAL, "Verde-amarillo"),
        ),
        fuente = "Medición del dueño con multímetro, 25-sep-2026 (editable)",
    )

    val conocimiento = ConocimientoModelo(
        clave = BENELLI.claveModelo!!,
        nombre = "Benelli TNT 150i (2022)",
        tipo = VehicleType.MOTORCYCLE,
        ecu = null,
        fuenteEcu = null,
        notasProtocolo = "",
        notas = emptyList(),
        cableado = listOf(cableadoTps),
        repuestos = emptyList(),
        bandas = emptyList(),
    )

    val textosCaso: Map<Pair<FuncionCable, String>, String> =
        listOf(CondicionesMultimetro.CERRADO, CondicionesMultimetro.MEDIO, CondicionesMultimetro.FONDO)
            .flatMap { c -> listOf((FuncionCable.REF_5V to c) to "5,0", (FuncionCable.MASA to c) to "0") }
            .toMap() + mapOf(
            (FuncionCable.SENAL to CondicionesMultimetro.CERRADO) to "0,1",
            (FuncionCable.SENAL to CondicionesMultimetro.MEDIO) to "0,35",
            (FuncionCable.SENAL to CondicionesMultimetro.FONDO) to "0,8",
        )

    val ecuDelBarrido = LecturasEcu(
        "Prueba guiada «Barrido del TPS» de esta sesión",
        mapOf(CondicionesMultimetro.CERRADO to 0.12, CondicionesMultimetro.MEDIO to 0.46, CondicionesMultimetro.FONDO to 0.89),
    )

    val sesion = SesionTaller(id = 1, vehiculoId = BENELLI.id, claveModelo = BENELLI.claveModelo, inicio = SEP25_20H, titulo = "Señal del TPS fuera de rango")

    fun barridoTps() = NuevoEvento(
        tipo = TipoEvento.PRUEBA_GUIADA,
        titulo = "Barrido del TPS",
        payload = JSONObject().put("prueba", "TPS_BARRIDO").put("estado", "TERMINADA")
            .put("detalle", JSONObject().put("cerradoV", 0.12).put("medioV", 0.46).put("fondoV", 0.89)),
    )

    fun contexto(sensor: SensorMultimetro = SensorMultimetro.TPS, conModelo: Boolean = true, conSesion: Boolean = true) =
        ContextoMultimetro(
            vehiculo = BENELLI,
            modelo = if (conModelo) ModeloReferencia(conocimiento.clave, conocimiento.nombre) else null,
            plantilla = ResolutorPlantilla.para(sensor, VehicleType.MOTORCYCLE, if (conModelo) conocimiento.cableado else emptyList()),
            bandas = BandasTipicas.para(VehicleType.MOTORCYCLE),
            sesion = if (conSesion) sesion else null,
            lecturasSesion = emptyList(),
            ecu = if (conSesion && sensor == SensorMultimetro.TPS) ecuDelBarrido else null,
        )

    fun ui(
        textos: Map<Pair<FuncionCable, String>, String> = textosCaso,
        contexto: ContextoMultimetro = contexto(),
        colores: Map<FuncionCable, String?> = contexto.plantilla.colores(),
    ): MultimetroUi = MapeoMultimetro.ui(
        LocalMultimetro(sensor = contexto.plantilla.sensor, contexto = contexto, cargando = false, textos = textos, colores = colores),
    )
}
