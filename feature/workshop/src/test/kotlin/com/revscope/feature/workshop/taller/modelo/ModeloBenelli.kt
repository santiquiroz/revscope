package com.revscope.feature.workshop.taller.modelo

import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.obd.taller.modelo.CableModelo
import com.revscope.core.obd.taller.modelo.CableadoSensor
import com.revscope.core.obd.taller.modelo.ConocimientoModelo
import com.revscope.core.obd.taller.modelo.NotaModelo
import com.revscope.core.obd.taller.modelo.RepuestoModelo
import com.revscope.core.obd.taller.modelo.TipoNota
import com.revscope.core.obd.taller.modelo.TipoRepuesto
import com.revscope.core.obd.taller.multimetro.FuncionCable
import com.revscope.core.obd.taller.referencia.BandaReferencia
import com.revscope.core.obd.taller.referencia.ClavesBanda
import com.revscope.core.obd.taller.referencia.OrigenBanda

object ModeloBenelli {
    val conocimiento = ConocimientoModelo(
        clave = "benelli-tnt150i-2022",
        nombre = "Benelli TNT 150i (2022)",
        tipo = VehicleType.MOTORCYCLE,
        ecu = "Delphi, tipo MT05 · referencia 96C00P100D01",
        fuenteEcu = "Catálogo de partes Auteco TNT 150i, figura 34, ítem 14 (referencia); tipo MT05 del informe del caso",
        notasProtocolo = "K-line: la ECU expone pocos PIDs. Observado con RevScope en el caso del 25-sep-2026.",
        notas = listOf(
            NotaModelo(
                TipoNota.EJEMPLO,
                "Ejemplo de TPS en ECUs Delphi MT05: 0,7 V suelto y 4,5 V a fondo. No es el valor oficial de Benelli.",
                "HUD ECU Hacker, documentación de ECUs Delphi MT05",
            ),
            NotaModelo(
                TipoNota.PENDIENTE,
                "Voltajes oficiales del TPS de la TNT 150i: el manual de servicio no se consultó.",
                "Manual de servicio TNT 150i (no consultado)",
            ),
            NotaModelo(
                TipoNota.NOTA,
                "Con el contacto puesto la farola queda siempre encendida y baja el voltaje de la batería.",
                "Observado en el caso del 25-sep-2026",
            ),
        ),
        cableado = listOf(
            CableadoSensor(
                sensor = "TPS",
                titulo = "Sensor de posición del acelerador (3 cables)",
                cables = listOf(
                    CableModelo(FuncionCable.REF_5V, "Rojo"),
                    CableModelo(FuncionCable.MASA, "Negro"),
                    CableModelo(FuncionCable.SENAL, "Verde-amarillo"),
                ),
                fuente = "Medición del dueño con multímetro, 25-sep-2026 (editable)",
            ),
        ),
        repuestos = listOf(
            repuesto("ECU", "96C00P100D01", "E.C.U", TipoRepuesto.OEM, "14"),
            repuesto("TPS", "280756030001", "Sensor de posición del acelerador (TPS)", TipoRepuesto.OEM, "35"),
            repuesto("CUERPO_INYECCION", "289004320030", "Cuerpo de inyección completo", TipoRepuesto.OEM, "36"),
            repuesto("VALVULA_AIRE", "280024320000", "Válvula de control de aire", TipoRepuesto.OEM, "37"),
            repuesto("SENSOR_TEMPERATURA", "280013320000", "Sensor de temperatura", TipoRepuesto.OEM, "26"),
            RepuestoModelo(
                "TPS",
                "280023130000",
                "TPS de Keeway",
                TipoRepuesto.NO_EQUIVALENTE,
                "Puede encajar en el cuerpo de inyección sin ser equivalente al original.",
                "Anuncio de repuesto citado en el informe del caso (Amazon.es)",
            ),
        ),
        bandas = emptyList(),
    )

    val bandas = listOf(
        BandaReferencia(ClavesBanda.TPS_CERRADO_V, 0.3, 1.0, "V", OrigenBanda.TIPICO),
        BandaReferencia(
            ClavesBanda.TPS_FONDO_V,
            3.8,
            4.8,
            "V",
            OrigenBanda.FUENTE,
            "Ejemplo sintético de la interfaz; no es una especificación Benelli ni se usa para diagnosticar",
        ),
        BandaReferencia(ClavesBanda.MINIMO_RPM, 1_350.0, 1_650.0, "rpm", OrigenBanda.USUARIO),
        BandaReferencia(ClavesBanda.REF_5V_V, 4.8, 5.2, "V", OrigenBanda.TIPICO),
    )

    private fun repuesto(rol: String, referencia: String, descripcion: String, tipo: TipoRepuesto, item: String) =
        RepuestoModelo(
            rol,
            referencia,
            descripcion,
            tipo,
            nota = "",
            fuente = "Catálogo de partes Auteco TNT 150i, figura 34, ítem $item",
        )
}
