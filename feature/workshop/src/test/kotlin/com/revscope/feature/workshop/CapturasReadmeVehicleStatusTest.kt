package com.revscope.feature.workshop

import com.github.takahirom.roborazzi.captureRoboImage
import com.revscope.core.common.i18n.Region
import com.revscope.core.data.db.entities.MaintenanceItemEntity
import com.revscope.core.designsystem.RevScopeTheme
import com.revscope.core.obd.legal.DocumentStatusCalculator
import com.revscope.core.obd.trip.MaintenanceCalculator
import com.revscope.core.uitesting.XmlStringResolver
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Captura en inglés de «Vehículo al día» para el README (docs/screenshots/en/vehicle-status.png).
 * Los mismos documentos que CapturasTallerTest.alDiaContent (SOAT al día, RTM en 12 días, todo
 * riesgo sin configurar, licencia vencida hace 3 días), pero calculados con los motores reales y
 * los textos en inglés en vez de escritos a mano en español.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CapturasReadmeVehicleStatusTest {

    private val resolver = XmlStringResolver.en(
        com.revscope.core.obd.R::class.java,
        resDirs = listOf("../../core/obd/src/main/res"),
    )

    // Lunes 28 sep 2026, 10:00 en Bogotá.
    private val ahora = 1_790_607_600_000L

    private fun fecha(anio: Int, mes: Int, dia: Int): Long =
        LocalDate.of(anio, mes, dia).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private val documentos = DocumentStatusCalculator.VehicleDocuments(
        plate = "NZO28H",
        picoPlacaCity = "bogota",
        isMotorcycle = true,
        soatExpiresAt = fecha(2027, 3, 14),
        rtmExpiresAt = fecha(2026, 10, 10),
        insuranceExpiresAt = null,
        licenseExpiresAt = fecha(2026, 9, 25),
    )

    private val mantenimiento = MaintenanceCalculator.calculate(
        odometroActual = 18_420.0,
        items = listOf(
            MaintenanceItemEntity(vehicleProfileId = 7, nombre = "Oil change", intervaloKm = 3_000.0, ultimoServicioKm = 15_720.0),
            MaintenanceItemEntity(vehicleProfileId = 7, nombre = "Chain kit", intervaloKm = 15_000.0, ultimoServicioKm = 12_000.0),
        ),
    )

    @Test
    fun vehicleStatus() {
        val estados = DocumentStatusCalculator.calculate(
            documents = documentos,
            overrideRules = null,
            nowMs = ahora,
            resolver = resolver,
            region = Region.CO,
        )
        RuntimeEnvironment.setQualifiers("en-w412dp-h915dp-xxhdpi")
        RuntimeEnvironment.setFontScale(1.0f)
        captureRoboImage(filePath = "../../docs/screenshots/en/vehicle-status.png") {
            RevScopeTheme {
                AlDiaContent(
                    vehiculo = VehiculoAlDia(nombre = "Benelli TNT 150i", placa = "NZO28H"),
                    estados = estados,
                    licenciaVenceEn = documentos.licenseExpiresAt,
                    mantenimientoNivel = MaintenanceCalculator.peorNivel(mantenimiento),
                    mantenimientoDetalle = MaintenanceCalculator.detalleTexto(mantenimiento, resolver),
                    zoneBrief = null,
                    region = Region.CO,
                    acciones = AccionesAlDia(),
                )
            }
        }
    }
}
