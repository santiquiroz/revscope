# Plan 2: Taller Fase 1 — Chequeo de salud, mezcla en vivo e interpretación para mecánicos

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** El hub Taller gana sus dos herramientas estrella: Chequeo de salud de un toque (DTCs + readiness pre-tecnomecánica + trims/O2/voltaje interpretados en español, compartible como imagen) y vista Mezcla y combustión en vivo. Los PIDs de diagnóstico se sondean solo cuando una pantalla de Taller está activa.

**Architecture:** PIDs nuevos con prioridad 4 gateados por `workshopMode` en PidScheduler (no tocan la latencia del dashboard). `ReadinessParser` y `DiagnosticRules` son lógica pura 100% testeable. Room sube a v10 con MIGRACIÓN REAL (los 107 km de datos del usuario NO se pueden perder). El informe se comparte como PNG con el patrón de TripShareCard.

**Tech Stack:** Kotlin, Compose, Hilt, Room 10, exp4j (fórmulas PID), FileProvider.

## Global Constraints

- Gradle NO está en el repo: `C:\Users\santi\.gradle\wrapper\dists\gradle-8.11.1-bin\bpt9gzteqjrbo1mjrsomdt32c\gradle-8.11.1\bin\gradle.bat` (abrevio `$GRADLE`), desde la raíz c:/personal/OBD2.
- Commits en español `tipo: descripción`. NUNCA `Co-Authored-By`.
- `CancellationException` SIEMPRE se relanza antes de catch genérico.
- Textos UI en español. Tests con org.junit.Assert (convención del módulo).
- **CRÍTICO**: `DataModule` usa `fallbackToDestructiveMigration()`. El bump a Room v10 DEBE ir acompañado de `MIGRATION_9_10` real en el MISMO commit — si no, la instalación borra los viajes reales del usuario.
- Spec: `docs/superpowers/specs/2026-07-08-suite-mecanico-rediseno-design.md` §2.

---

### Task 1: PIDs de diagnóstico (prioridad 4) + workshopMode

**Files:**
- Modify: `core/obd/src/main/assets/pids_mode01.json`
- Modify: `core/obd/src/main/kotlin/com/revscope/core/obd/telemetry/PidScheduler.kt`
- Modify: `core/obd/src/main/kotlin/com/revscope/core/obd/session/ObdSessionManager.kt`
- Test: ampliar el test existente de PidRegistry (buscar `PidRegistry*Test` en core/obd/src/test) o crear `core/obd/src/test/kotlin/com/revscope/core/obd/pid/WorkshopPidsTest.kt`

**Interfaces:**
- Produces: PIDs 08,09,0A,0E,15,18,19,2E,3C,44 con `priority=4` en el registro; `PidScheduler.setWorkshopMode(enabled: Boolean)`; `ObdSessionManager.setWorkshopMode(enabled: Boolean)` (passthrough). Tasks 5 y 7 los consumen.

- [ ] **Step 1: Test que falla — los PIDs nuevos evalúan bien**

```kotlin
package com.revscope.core.obd.pid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class WorkshopPidsTest {

    private val registry = PidRegistry(TestPids.load())

    @Test
    fun `fuel trim B2 centrado en cero`() {
        val reading = registry.evaluate("08", byteArrayOf(128.toByte()))
        assertNotNull(reading)
        assertEquals(0.0, reading!!.value, 0.01)
    }

    @Test
    fun `lambda comandado uno es estequiometrico`() {
        // 0x8000 = 32768 → 32768/32768 = 1.0
        val reading = registry.evaluate("44", byteArrayOf(0x80.toByte(), 0x00))
        assertEquals(1.0, reading!!.value, 0.001)
    }

    @Test
    fun `temperatura catalizador con offset`() {
        // A=1, B=194 → (256+194)/10 - 40 = 5.0 °C
        val reading = registry.evaluate("3C", byteArrayOf(0x01, 0xC2.toByte()))
        assertEquals(5.0, reading!!.value, 0.01)
    }

    @Test
    fun `avance de encendido negativo posible`() {
        val reading = registry.evaluate("0E", byteArrayOf(0))
        assertEquals(-64.0, reading!!.value, 0.01)
    }

    @Test
    fun `los pids de taller tienen prioridad 4`() {
        listOf("08", "09", "0A", "0E", "15", "18", "19", "2E", "3C", "44").forEach { pid ->
            assertEquals("PID $pid", 4, registry.getDefinition(pid)!!.priority)
        }
    }
}
```

Nota `TestPids.load()`: revisar cómo los tests existentes de PidRegistry cargan el JSON (helper, resource o string inline) y usar el mismo mecanismo; si cargan el asset real, usarlo — los asserts están escritos contra el JSON de producción.

- [ ] **Step 2: Verificar que falla**

Run: `$GRADLE :core:obd:testDebugUnitTest --tests "*WorkshopPidsTest"`
Expected: FAIL (PIDs no existen aún → getDefinition null / evaluate null)

- [ ] **Step 3: Agregar los PIDs al JSON**

Añadir al array de `pids_mode01.json` (respetando el estilo de las entradas existentes):

```json
{"mode":"01","pid":"08","name":"Short Fuel Trim B2","nameEs":"Fuel Trim Corto B2","bytes":1,"formula":"(A-128)*100/128","unit":"%","min":-100,"max":99.2,"priority":4},
{"mode":"01","pid":"09","name":"Long Fuel Trim B2","nameEs":"Fuel Trim Largo B2","bytes":1,"formula":"(A-128)*100/128","unit":"%","min":-100,"max":99.2,"priority":4},
{"mode":"01","pid":"0A","name":"Fuel Pressure","nameEs":"Presión de Combustible","bytes":1,"formula":"A*3","unit":"kPa","min":0,"max":765,"priority":4},
{"mode":"01","pid":"0E","name":"Timing Advance","nameEs":"Avance de Encendido","bytes":1,"formula":"A/2-64","unit":"°","min":-64,"max":63.5,"priority":4},
{"mode":"01","pid":"15","name":"O2 Sensor B1S2","nameEs":"Sensor O2 B1S2","bytes":2,"formula":"A/200","unit":"V","min":0,"max":1.275,"priority":4},
{"mode":"01","pid":"18","name":"O2 Sensor B2S1","nameEs":"Sensor O2 B2S1","bytes":2,"formula":"A/200","unit":"V","min":0,"max":1.275,"priority":4},
{"mode":"01","pid":"19","name":"O2 Sensor B2S2","nameEs":"Sensor O2 B2S2","bytes":2,"formula":"A/200","unit":"V","min":0,"max":1.275,"priority":4},
{"mode":"01","pid":"2E","name":"Commanded EVAP Purge","nameEs":"Purga EVAP","bytes":1,"formula":"A*100/255","unit":"%","min":0,"max":100,"priority":4},
{"mode":"01","pid":"3C","name":"Catalyst Temp B1S1","nameEs":"Temp Catalizador","bytes":2,"formula":"((A*256)+B)/10-40","unit":"°C","min":-40,"max":6513.5,"priority":4},
{"mode":"01","pid":"44","name":"Commanded Lambda","nameEs":"Lambda Comandado","bytes":2,"formula":"((A*256)+B)/32768","unit":"λ","min":0,"max":2,"priority":4}
```

- [ ] **Step 4: Verificar que pasa**

Run: `$GRADLE :core:obd:testDebugUnitTest --tests "*WorkshopPidsTest"`
Expected: PASS (5 tests)

- [ ] **Step 5: workshopMode en PidScheduler**

Campo nuevo junto a `batchingSupported`:

```kotlin
    // Priority-4 diagnostics only poll while a workshop screen is on screen
    private val workshopMode = AtomicBoolean(false)

    fun setWorkshopMode(enabled: Boolean) {
        workshopMode.set(enabled)
    }
```

En `observeReadings`, agregar el cuarto grupo:

```kotlin
            launch { pollGroup(WORKSHOP_PRIORITY, 1_000L) { producer.trySend(it) } }
```

En `pollGroup`, al inicio del `while (true)`:

```kotlin
            if (priority == WORKSHOP_PRIORITY && !workshopMode.get()) {
                delay(baseIntervalMs)
                continue
            }
```

Constante al tope del archivo: `private const val WORKSHOP_PRIORITY = 4`.

- [ ] **Step 6: Passthrough en ObdSessionManager**

El scheduler se crea localmente en `startTelemetry` — guardarlo:

Campo junto a `derivedEngine`:

```kotlin
    private var activeScheduler: PidScheduler? = null
```

En `startTelemetry`, cambiar la creación:

```kotlin
                    val scheduler = PidScheduler(bt, registry).also { activeScheduler = it }
                    val rawFlow = scheduler
                        .observeReadings()
                        .shareIn(this, SharingStarted.Eagerly, replay = 0)
```

API pública (junto a `setGearTable`):

```kotlin
    fun setWorkshopMode(enabled: Boolean) {
        activeScheduler?.setWorkshopMode(enabled)
    }
```

En `finalShutdown` y en `stopTelemetry`, agregar `activeScheduler = null` (higiene).

- [ ] **Step 7: Suite completa y commit**

Run: `$GRADLE :core:obd:testDebugUnitTest`
Expected: PASS (141 previos + 5 nuevos)

```bash
git add core/obd/src/main/assets/pids_mode01.json core/obd/src/main/kotlin/com/revscope/core/obd/telemetry/PidScheduler.kt core/obd/src/main/kotlin/com/revscope/core/obd/session/ObdSessionManager.kt core/obd/src/test/
git commit -m "feat: PIDs de diagnóstico de taller con prioridad 4 activada bajo demanda"
```

---

### Task 2: ReadinessParser (Mode 01 PID 01)

**Files:**
- Create: `core/obd/src/main/kotlin/com/revscope/core/obd/protocol/ReadinessParser.kt`
- Test: `core/obd/src/test/kotlin/com/revscope/core/obd/protocol/ReadinessParserTest.kt`

**Interfaces:**
- Consumes: `ResponseParser.parsePidResponse(raw, "01")` (existente — devuelve los 4 bytes A-D o null).
- Produces: `ReadinessParser.parse(raw: String): ReadinessStatus?`; `data class ReadinessStatus(milOn: Boolean, dtcCount: Int, isDiesel: Boolean, monitors: List<MonitorResult>)`; `data class MonitorResult(nombre: String, soportado: Boolean, completo: Boolean)`. Task 5 lo consume.

- [ ] **Step 1: Test que falla**

```kotlin
package com.revscope.core.obd.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadinessParserTest {

    @Test
    fun `mil encendida y conteo de dtcs`() {
        // A=0x82: MIL on, 2 DTCs. B=0x07: misfire/fuel/comp soportados, completos. C=0x00 D=0x00
        val status = ReadinessParser.parse("41 01 82 07 00 00")!!
        assertTrue(status.milOn)
        assertEquals(2, status.dtcCount)
        assertFalse(status.isDiesel)
    }

    @Test
    fun `todo listo para tecnomecanica`() {
        // A=0x00 sin MIL. B=0x07 continuos ok. C=0xE5 varios soportados, D=0x00 todos completos
        val status = ReadinessParser.parse("410100 07 E5 00")!!
        assertFalse(status.milOn)
        assertTrue(status.monitors.filter { it.soportado }.all { it.completo })
    }

    @Test
    fun `monitor soportado incompleto se reporta`() {
        // C bit0 catalizador soportado, D bit0 = incompleto
        val status = ReadinessParser.parse("41 01 00 07 01 01")!!
        val cat = status.monitors.first { it.nombre == "Catalizador" }
        assertTrue(cat.soportado)
        assertFalse(cat.completo)
    }

    @Test
    fun `motor diesel usa nombres de compresion`() {
        // B bit3 = 1 → compresión (diésel)
        val status = ReadinessParser.parse("41 01 00 0F 41 00")!!
        assertTrue(status.isDiesel)
        assertTrue(status.monitors.any { it.nombre == "Catalizador NMHC" })
    }

    @Test
    fun `respuesta invalida devuelve null`() {
        assertNull(ReadinessParser.parse("NO DATA"))
    }

    @Test
    fun `misfire incompleto en byte B`() {
        // B: bit0 misfire soportado, bit4 misfire incompleto
        val status = ReadinessParser.parse("41 01 00 11 00 00")!!
        val misfire = status.monitors.first { it.nombre == "Encendido (misfire)" }
        assertTrue(misfire.soportado)
        assertFalse(misfire.completo)
    }
}
```

- [ ] **Step 2: Verificar que falla**

Run: `$GRADLE :core:obd:testDebugUnitTest --tests "*ReadinessParserTest"`
Expected: FAIL — Unresolved reference

- [ ] **Step 3: Implementación**

```kotlin
package com.revscope.core.obd.protocol

/**
 * Parses Mode 01 PID 01 — MIL status, DTC count and I/M readiness monitors.
 * SAE J1979: A = MIL(bit7) + count(bits0-6); B = continuous monitors
 * (bits0-2 supported, bit3 = compression ignition, bits4-6 incomplete);
 * C = non-continuous supported; D = non-continuous incomplete.
 */
object ReadinessParser {

    data class MonitorResult(val nombre: String, val soportado: Boolean, val completo: Boolean)

    data class ReadinessStatus(
        val milOn: Boolean,
        val dtcCount: Int,
        val isDiesel: Boolean,
        val monitors: List<MonitorResult>,
    )

    private val CONTINUOUS = listOf("Encendido (misfire)", "Sistema de combustible", "Componentes")

    private val SPARK_MONITORS = listOf(
        "Catalizador", "Catalizador calefactado", "Sistema EVAP", "Aire secundario",
        "Refrigerante A/C", "Sensor O2", "Calefactor O2", "EGR/VVT",
    )

    private val DIESEL_MONITORS = listOf(
        "Catalizador NMHC", "Catalizador NOx", "Reservado", "Presión de sobrealimentación",
        "Reservado", "Sensor de gases", "Filtro de partículas", "EGR/VVT",
    )

    fun parse(raw: String): ReadinessStatus? {
        val bytes = ResponseParser.parsePidResponse(raw, "01") ?: return null
        if (bytes.size < 4) return null
        val a = bytes[0].toInt() and 0xFF
        val b = bytes[1].toInt() and 0xFF
        val c = bytes[2].toInt() and 0xFF
        val d = bytes[3].toInt() and 0xFF

        val isDiesel = (b shr 3) and 0x01 == 1
        val monitors = buildList {
            CONTINUOUS.forEachIndexed { i, nombre ->
                add(MonitorResult(
                    nombre = nombre,
                    soportado = (b shr i) and 0x01 == 1,
                    completo = (b shr (i + 4)) and 0x01 == 0,
                ))
            }
            val names = if (isDiesel) DIESEL_MONITORS else SPARK_MONITORS
            names.forEachIndexed { i, nombre ->
                if (nombre == "Reservado") return@forEachIndexed
                add(MonitorResult(
                    nombre = nombre,
                    soportado = (c shr i) and 0x01 == 1,
                    completo = (d shr i) and 0x01 == 0,
                ))
            }
        }
        return ReadinessStatus(
            milOn = (a shr 7) and 0x01 == 1,
            dtcCount = a and 0x7F,
            isDiesel = isDiesel,
            monitors = monitors,
        )
    }
}
```

Nota: si `ResponseParser.parsePidResponse` exige longitud exacta según la definición del PID (01 no está en el JSON), revisar su implementación; si no sirve para el PID 01, parsear el hex directamente en este objeto reutilizando `ResponseParser.stripTransientPrefixes` y buscando el prefijo "4101". Documentar la decisión en el reporte.

- [ ] **Step 4: Verificar que pasa**

Run: `$GRADLE :core:obd:testDebugUnitTest --tests "*ReadinessParserTest"`
Expected: PASS (6 tests)

- [ ] **Step 5: Commit**

```bash
git add core/obd/src/main/kotlin/com/revscope/core/obd/protocol/ReadinessParser.kt core/obd/src/test/kotlin/com/revscope/core/obd/protocol/ReadinessParserTest.kt
git commit -m "feat: parser de monitores de readiness para el chequeo pre-tecnomecánica"
```

---

### Task 3: DiagnosticRules — motor de interpretación

**Files:**
- Create: `core/obd/src/main/kotlin/com/revscope/core/obd/workshop/DiagnosticRules.kt`
- Test: `core/obd/src/test/kotlin/com/revscope/core/obd/workshop/DiagnosticRulesTest.kt`

**Interfaces:**
- Consumes: `ReadinessParser.ReadinessStatus` (Task 2).
- Produces: `DiagnosticRules` object, `enum Nivel { OK, ATENCION, FALLA }`, `data class Diagnosis(nivel, area, titulo, causaProbable)` y las funciones de evaluación listadas abajo. Tasks 5 y 7 lo consumen.

- [ ] **Step 1: Test que falla**

```kotlin
package com.revscope.core.obd.workshop

import com.revscope.core.obd.protocol.ReadinessParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticRulesTest {

    @Test
    fun `ltft dentro de rango es ok`() {
        assertEquals(DiagnosticRules.Nivel.OK, DiagnosticRules.evaluarFuelTrimLargo(5.0).nivel)
        assertEquals(DiagnosticRules.Nivel.OK, DiagnosticRules.evaluarFuelTrimLargo(-9.9).nivel)
    }

    @Test
    fun `ltft positivo alto es mezcla pobre`() {
        val d = DiagnosticRules.evaluarFuelTrimLargo(18.0)
        assertEquals(DiagnosticRules.Nivel.ATENCION, d.nivel)
        assertTrue(d.causaProbable.contains("pobre", ignoreCase = true))
    }

    @Test
    fun `ltft negativo alto es mezcla rica`() {
        val d = DiagnosticRules.evaluarFuelTrimLargo(-15.0)
        assertEquals(DiagnosticRules.Nivel.ATENCION, d.nivel)
        assertTrue(d.causaProbable.contains("rica", ignoreCase = true))
    }

    @Test
    fun `ltft extremo es falla`() {
        assertEquals(DiagnosticRules.Nivel.FALLA, DiagnosticRules.evaluarFuelTrimLargo(30.0).nivel)
        assertEquals(DiagnosticRules.Nivel.FALLA, DiagnosticRules.evaluarFuelTrimLargo(-26.0).nivel)
    }

    @Test
    fun `correccion combinada excesiva`() {
        val d = DiagnosticRules.evaluarTrimCombinado(stft = 10.0, ltft = 8.0)
        assertEquals(DiagnosticRules.Nivel.ATENCION, d.nivel)
    }

    @Test
    fun `o2 clavado bajo es sensor perezoso o mezcla extrema`() {
        val muestras = List(35) { 0.1 }
        assertEquals(DiagnosticRules.Nivel.ATENCION, DiagnosticRules.evaluarO2(muestras).nivel)
    }

    @Test
    fun `o2 oscilando es ok`() {
        val muestras = List(40) { if (it % 2 == 0) 0.2 else 0.8 }
        assertEquals(DiagnosticRules.Nivel.OK, DiagnosticRules.evaluarO2(muestras).nivel)
    }

    @Test
    fun `o2 sin muestras suficientes no diagnostica`() {
        assertEquals(DiagnosticRules.Nivel.OK, DiagnosticRules.evaluarO2(List(5) { 0.1 }).nivel)
    }

    @Test
    fun `voltaje bajo en marcha apunta al alternador`() {
        val d = DiagnosticRules.evaluarVoltaje(12.8, motorEncendido = true)
        assertEquals(DiagnosticRules.Nivel.ATENCION, d.nivel)
        assertTrue(d.causaProbable.contains("alternador", ignoreCase = true))
    }

    @Test
    fun `voltaje sano en marcha es ok`() {
        assertEquals(DiagnosticRules.Nivel.OK, DiagnosticRules.evaluarVoltaje(14.2, true).nivel)
    }

    @Test
    fun `sobrecalentamiento es falla`() {
        assertEquals(DiagnosticRules.Nivel.FALLA, DiagnosticRules.evaluarTemperatura(108.0).nivel)
    }

    @Test
    fun `temperatura normal ok`() {
        assertEquals(DiagnosticRules.Nivel.OK, DiagnosticRules.evaluarTemperatura(88.0).nivel)
    }

    @Test
    fun `readiness con mil encendida es falla`() {
        val status = ReadinessParser.ReadinessStatus(
            milOn = true, dtcCount = 2, isDiesel = false,
            monitors = emptyList(),
        )
        val ds = DiagnosticRules.evaluarReadiness(status)
        assertTrue(ds.any { it.nivel == DiagnosticRules.Nivel.FALLA })
    }

    @Test
    fun `monitor incompleto avisa no listo para tecnomecanica`() {
        val status = ReadinessParser.ReadinessStatus(
            milOn = false, dtcCount = 0, isDiesel = false,
            monitors = listOf(ReadinessParser.MonitorResult("Catalizador", true, false)),
        )
        val ds = DiagnosticRules.evaluarReadiness(status)
        assertTrue(ds.any { it.nivel == DiagnosticRules.Nivel.ATENCION && it.titulo.contains("Catalizador") })
    }

    @Test
    fun `readiness completo es ok`() {
        val status = ReadinessParser.ReadinessStatus(
            milOn = false, dtcCount = 0, isDiesel = false,
            monitors = listOf(ReadinessParser.MonitorResult("Catalizador", true, true)),
        )
        val ds = DiagnosticRules.evaluarReadiness(status)
        assertTrue(ds.all { it.nivel == DiagnosticRules.Nivel.OK })
    }
}
```

- [ ] **Step 2: Verificar que falla**

Run: `$GRADLE :core:obd:testDebugUnitTest --tests "*DiagnosticRulesTest"`
Expected: FAIL — Unresolved reference

- [ ] **Step 3: Implementación**

```kotlin
package com.revscope.core.obd.workshop

import com.revscope.core.obd.protocol.ReadinessParser
import kotlin.math.abs

/** Deterministic, offline interpretation rules for workshop diagnostics. */
object DiagnosticRules {

    enum class Nivel { OK, ATENCION, FALLA }

    data class Diagnosis(
        val nivel: Nivel,
        val area: String,
        val titulo: String,
        val causaProbable: String,
    )

    fun evaluarFuelTrimLargo(ltft: Double): Diagnosis = when {
        abs(ltft) <= LTFT_OK -> Diagnosis(
            Nivel.OK, "Mezcla", "Fuel trim largo %.1f%%".format(ltft),
            "El ECU compensa dentro del rango normal",
        )
        ltft > LTFT_FALLA || ltft < -LTFT_FALLA -> Diagnosis(
            Nivel.FALLA, "Mezcla", "Fuel trim largo %.1f%% fuera de control".format(ltft),
            "El ECU no logra compensar la mezcla — revisar sistema de combustible completo",
        )
        ltft > 0 -> Diagnosis(
            Nivel.ATENCION, "Mezcla", "Mezcla pobre (LTFT +%.1f%%)".format(ltft),
            "Fugas de vacío, inyectores sucios o sensor MAF sucio",
        )
        else -> Diagnosis(
            Nivel.ATENCION, "Mezcla", "Mezcla rica (LTFT %.1f%%)".format(ltft),
            "Inyector goteando, presión de combustible alta o MAF descalibrado",
        )
    }

    fun evaluarTrimCombinado(stft: Double, ltft: Double): Diagnosis {
        val total = stft + ltft
        return if (abs(total) > TRIM_TOTAL_MAX) Diagnosis(
            Nivel.ATENCION, "Mezcla", "Corrección total %.1f%% excesiva".format(total),
            "La suma de trims corto y largo supera ±$TRIM_TOTAL_MAX% — condición activa de mezcla",
        ) else Diagnosis(
            Nivel.OK, "Mezcla", "Corrección total %.1f%%".format(total),
            "Trims combinados dentro del rango",
        )
    }

    fun evaluarO2(voltajes: List<Double>): Diagnosis {
        if (voltajes.size < O2_MIN_MUESTRAS) return Diagnosis(
            Nivel.OK, "Sensor O2", "Muestras insuficientes",
            "Se necesitan más lecturas para diagnosticar el sensor",
        )
        val clavadoBajo = voltajes.all { it < O2_BAJO }
        val clavadoAlto = voltajes.all { it > O2_ALTO }
        return when {
            clavadoBajo -> Diagnosis(
                Nivel.ATENCION, "Sensor O2", "Sensor O2 clavado bajo (<%.1fV)".format(O2_BAJO),
                "Sensor perezoso/agotado o mezcla extremadamente pobre",
            )
            clavadoAlto -> Diagnosis(
                Nivel.ATENCION, "Sensor O2", "Sensor O2 clavado alto (>%.1fV)".format(O2_ALTO),
                "Sensor contaminado o mezcla extremadamente rica",
            )
            else -> Diagnosis(
                Nivel.OK, "Sensor O2", "Sensor O2 oscilando",
                "El sensor conmuta — comportamiento sano",
            )
        }
    }

    fun evaluarVoltaje(volts: Double, motorEncendido: Boolean): Diagnosis = when {
        motorEncendido && volts < VOLT_MIN_MARCHA -> Diagnosis(
            Nivel.ATENCION, "Eléctrico", "Voltaje %.1fV bajo en marcha".format(volts),
            "El alternador/estator no está cargando bien",
        )
        !motorEncendido && volts < VOLT_MIN_REPOSO -> Diagnosis(
            Nivel.ATENCION, "Eléctrico", "Batería baja (%.1fV)".format(volts),
            "Batería descargada o al final de su vida",
        )
        else -> Diagnosis(
            Nivel.OK, "Eléctrico", "Voltaje %.1fV".format(volts),
            "Sistema de carga dentro del rango",
        )
    }

    fun evaluarTemperatura(tempC: Double): Diagnosis = when {
        tempC > TEMP_MAX -> Diagnosis(
            Nivel.FALLA, "Refrigeración", "Sobrecalentamiento (%.0f°C)".format(tempC),
            "Detener el motor — revisar refrigerante, bomba, ventilador y termostato",
        )
        else -> Diagnosis(
            Nivel.OK, "Refrigeración", "Temperatura %.0f°C".format(tempC),
            "Dentro del rango de operación",
        )
    }

    fun evaluarReadiness(status: ReadinessParser.ReadinessStatus): List<Diagnosis> = buildList {
        if (status.milOn) add(Diagnosis(
            Nivel.FALLA, "Readiness", "Testigo de motor (MIL) encendido — ${status.dtcCount} códigos",
            "Hay fallas activas — revisar los códigos DTC antes de la tecnomecánica",
        )) else add(Diagnosis(
            Nivel.OK, "Readiness", "Sin testigo de motor",
            "No hay fallas activas reportadas",
        ))
        status.monitors.filter { it.soportado }.forEach { m ->
            add(
                if (m.completo) Diagnosis(Nivel.OK, "Readiness", "${m.nombre}: listo", "Monitor completado")
                else Diagnosis(
                    Nivel.ATENCION, "Readiness", "${m.nombre}: NO listo",
                    "Monitor incompleto — conducir 20-30 min variados antes de la tecnomecánica",
                )
            )
        }
    }

    private const val LTFT_OK = 10.0
    private const val LTFT_FALLA = 25.0
    private const val TRIM_TOTAL_MAX = 15.0
    private const val O2_MIN_MUESTRAS = 30
    private const val O2_BAJO = 0.2
    private const val O2_ALTO = 0.8
    private const val VOLT_MIN_MARCHA = 13.2
    private const val VOLT_MIN_REPOSO = 11.8
    private const val TEMP_MAX = 105.0
}
```

- [ ] **Step 4: Verificar que pasa**

Run: `$GRADLE :core:obd:testDebugUnitTest --tests "*DiagnosticRulesTest"`
Expected: PASS (15 tests)

- [ ] **Step 5: Commit**

```bash
git add core/obd/src/main/kotlin/com/revscope/core/obd/workshop/ core/obd/src/test/kotlin/com/revscope/core/obd/workshop/
git commit -m "feat: motor de reglas de diagnóstico en español para el modo taller"
```

---

### Task 4: Room v10 — health_reports con migración real

**Files:**
- Create: `core/data/src/main/kotlin/com/revscope/core/data/db/entities/HealthReportEntity.kt`
- Create: `core/data/src/main/kotlin/com/revscope/core/data/db/dao/HealthReportDao.kt`
- Create: `core/data/src/main/kotlin/com/revscope/core/data/db/Migrations.kt`
- Modify: `core/data/src/main/kotlin/com/revscope/core/data/db/AppDatabase.kt` (version 10, entity, dao)
- Modify: `core/data/src/main/kotlin/com/revscope/core/data/di/DataModule.kt` (addMigrations + provide dao)

**Interfaces:**
- Produces: `HealthReportEntity(id, vehicleProfileId, timestamp, resultsJson)`, `HealthReportDao.insert(report): Long`, `HealthReportDao.latest(): HealthReportEntity?`. Task 5 los consume.

- [ ] **Step 1: Entity**

```kotlin
package com.revscope.core.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "health_reports")
data class HealthReportEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val vehicleProfileId: Long,
    val timestamp: Long,
    /** JSON array of {area, nivel, titulo, causa} produced by the health check */
    val resultsJson: String,
)
```

- [ ] **Step 2: Dao**

```kotlin
package com.revscope.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.revscope.core.data.db.entities.HealthReportEntity

@Dao
interface HealthReportDao {

    @Insert
    suspend fun insert(report: HealthReportEntity): Long

    @Query("SELECT * FROM health_reports ORDER BY timestamp DESC LIMIT 1")
    suspend fun latest(): HealthReportEntity?
}
```

- [ ] **Step 3: Migración**

```kotlin
package com.revscope.core.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `health_reports` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`vehicleProfileId` INTEGER NOT NULL, " +
                "`timestamp` INTEGER NOT NULL, " +
                "`resultsJson` TEXT NOT NULL)"
        )
    }
}
```

- [ ] **Step 4: AppDatabase v10 + DataModule**

`AppDatabase`: agregar `HealthReportEntity::class` a entities, `version = 10`, `abstract fun healthReportDao(): HealthReportDao`.

`DataModule`:

```kotlin
        Room.databaseBuilder(context, AppDatabase::class.java, "revscope.db")
            .addMigrations(MIGRATION_9_10)
            // Pre-1.0: unknown jumps still wipe; 9→10 preserves real user data.
            .fallbackToDestructiveMigration()
            .build()
```

y el provider:

```kotlin
    @Provides
    fun provideHealthReportDao(db: AppDatabase): HealthReportDao = db.healthReportDao()
```

IMPORTANTE: verificar que el schema exportado (`core/data/schemas/` si existe) se regenere; el CREATE TABLE de la migración debe coincidir EXACTAMENTE con lo que Room espera para la entity (tipos NOT NULL, autoincrement). Si Room valida y truena en runtime, el error dice qué columna difiere.

- [ ] **Step 5: Compilar y correr tests de data + obd**

Run: `$GRADLE :core:data:compileDebugKotlin :core:obd:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, 146 tests verdes

- [ ] **Step 6: Commit**

```bash
git add core/data/
git commit -m "feat: tabla de informes de salud con migración real 9-10 — preserva los datos existentes"
```

---

### Task 5: Chequeo de salud — ViewModel, pantalla, ruta y card en el hub

**Files:**
- Create: `feature/workshop/src/main/kotlin/com/revscope/feature/workshop/HealthCheckViewModel.kt`
- Create: `feature/workshop/src/main/kotlin/com/revscope/feature/workshop/HealthCheckScreen.kt`
- Modify: `feature/workshop/src/main/kotlin/com/revscope/feature/workshop/WorkshopScreen.kt` (card nueva al tope)
- Modify: `feature/workshop/build.gradle.kts` (+ `:core:data`)
- Modify: `app/src/main/kotlin/com/revscope/app/navigation/Screen.kt` (+ `HealthCheck : Screen("health_check")`)
- Modify: `app/src/main/kotlin/com/revscope/app/navigation/RevScopeNavGraph.kt` (ruta + lambda del hub)

**Interfaces:**
- Consumes: `ObdSessionManager.rawExchange(cmd, timeout): Result<String>`, `ObdSessionManager.parseDtcResponse` (companion — revisar si acepta prefijos 47/4A para modos 07/0A; DtcViewModel ya lee pendientes/permanentes: reusar su mecanismo), `readings`, `setWorkshopMode` (Task 1), `ReadinessParser` (Task 2), `DiagnosticRules` (Task 3), `HealthReportDao` (Task 4), `activeProfile`.
- Produces: `HealthCheckScreen(onNavigateBack: () -> Unit, onShare: ...)` y estado `HealthCheckViewModel.UiState`. Task 6 agrega el compartir.

- [ ] **Step 1: ViewModel**

```kotlin
package com.revscope.feature.workshop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.data.db.dao.HealthReportDao
import com.revscope.core.data.db.entities.HealthReportEntity
import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.model.DtcMode
import com.revscope.core.obd.protocol.ReadinessParser
import com.revscope.core.obd.session.ObdSessionManager
import com.revscope.core.obd.workshop.DiagnosticRules
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class HealthCheckViewModel @Inject constructor(
    private val sessionManager: ObdSessionManager,
    private val reportDao: HealthReportDao,
) : ViewModel() {

    sealed interface UiState {
        data object Idle : UiState
        data class Running(val paso: String) : UiState
        data class Done(val items: List<DiagnosticRules.Diagnosis>, val dtcCodes: List<String>, val timestamp: Long) : UiState
        data class Error(val mensaje: String) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { reportDao.latest() }.getOrNull()?.let { last ->
                _state.value = UiState.Done(parseStoredItems(last.resultsJson), emptyList(), last.timestamp)
            }
        }
    }

    fun runHealthCheck() {
        if (_state.value is UiState.Running) return
        if (sessionManager.connectionState.value !is ConnectionState.Connected) {
            _state.value = UiState.Error("Conecta el adaptador primero")
            return
        }
        viewModelScope.launch {
            try {
                val items = mutableListOf<DiagnosticRules.Diagnosis>()

                _state.value = UiState.Running("Leyendo códigos de falla…")
                val dtcCodes = readAllDtcs()
                items += if (dtcCodes.isEmpty()) {
                    DiagnosticRules.Diagnosis(DiagnosticRules.Nivel.OK, "DTC", "Sin códigos de falla", "Memoria de fallas limpia")
                } else {
                    DiagnosticRules.Diagnosis(
                        DiagnosticRules.Nivel.FALLA, "DTC",
                        "${dtcCodes.size} códigos: ${dtcCodes.joinToString()}",
                        "Ábrelos en Códigos de falla para explicación con IA",
                    )
                }

                _state.value = UiState.Running("Consultando monitores de readiness…")
                sessionManager.rawExchange("01 01\r").getOrNull()
                    ?.let { ReadinessParser.parse(it) }
                    ?.let { items += DiagnosticRules.evaluarReadiness(it) }

                _state.value = UiState.Running("Muestreando mezcla y sensores (10 s)…")
                sessionManager.setWorkshopMode(true)
                val o2Samples = mutableListOf<Double>()
                repeat(SAMPLE_SECONDS) {
                    delay(1_000)
                    sessionManager.readings.value["14"]?.let { o2Samples += it.value }
                }
                val readings = sessionManager.readings.value
                sessionManager.setWorkshopMode(false)

                readings["07"]?.let { items += DiagnosticRules.evaluarFuelTrimLargo(it.value) }
                readings["09"]?.let { items += DiagnosticRules.evaluarFuelTrimLargo(it.value) }
                if (readings["06"] != null && readings["07"] != null) {
                    items += DiagnosticRules.evaluarTrimCombinado(readings["06"]!!.value, readings["07"]!!.value)
                }
                items += DiagnosticRules.evaluarO2(o2Samples)
                readings[ObdSessionManager.VBAT_PID]?.let {
                    val encendido = (readings["0C"]?.value ?: 0.0) > 400
                    items += DiagnosticRules.evaluarVoltaje(it.value, encendido)
                }
                readings["05"]?.let { items += DiagnosticRules.evaluarTemperatura(it.value) }

                val now = System.currentTimeMillis()
                persist(items, now)
                _state.value = UiState.Done(items, dtcCodes, now)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "HealthCheck failed")
                sessionManager.setWorkshopMode(false)
                _state.value = UiState.Error("Falló el chequeo: ${e.message}")
            }
        }
    }

    private suspend fun readAllDtcs(): List<String> {
        // Reuse the same command/parsing path DtcViewModel uses — check it first.
        val active = sessionManager.readActiveDtc().getOrNull().orEmpty().map { it.code }
        return active
    }

    private suspend fun persist(items: List<DiagnosticRules.Diagnosis>, timestamp: Long) {
        val json = JSONArray().apply {
            items.forEach {
                put(JSONObject()
                    .put("area", it.area)
                    .put("nivel", it.nivel.name)
                    .put("titulo", it.titulo)
                    .put("causa", it.causaProbable))
            }
        }
        runCatching {
            reportDao.insert(HealthReportEntity(
                vehicleProfileId = sessionManager.activeProfile.value?.id ?: 0L,
                timestamp = timestamp,
                resultsJson = json.toString(),
            ))
        }.onFailure { Timber.w(it, "HealthCheck: persist failed") }
    }

    private fun parseStoredItems(json: String): List<DiagnosticRules.Diagnosis> = try {
        val array = JSONArray(json)
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            DiagnosticRules.Diagnosis(
                nivel = DiagnosticRules.Nivel.valueOf(o.getString("nivel")),
                area = o.getString("area"),
                titulo = o.getString("titulo"),
                causaProbable = o.getString("causa"),
            )
        }
    } catch (_: Exception) {
        emptyList()
    }

    companion object {
        private const val SAMPLE_SECONDS = 10
    }
}
```

Nota `readAllDtcs`: ANTES de dejarlo solo con activos, leer `feature/dtc/.../DtcViewModel.kt`. Si ya existe lectura de pendientes (07) y permanentes (0A) reutilizable (vía `rawExchange` + un parser generalizado), incluirlas y concatenar los códigos con sufijo " (pendiente)" / " (permanente)". Si `parseDtcResponse` está clavado al prefijo 43, generalizarlo en el companion del manager con un parámetro `expectedPrefix` (default "43") sin romper la firma existente (overload). Documentar lo encontrado.

- [ ] **Step 2: Screen**

```kotlin
package com.revscope.feature.workshop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.revscope.core.obd.workshop.DiagnosticRules
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val AccentColor = Color(0xFFE8FF00)
private val SurfaceColor = Color(0xFF12121A)
private val TextColor = Color(0xFFE6E8F0)
private val TextMutedColor = Color(0xFF6B7089)

@Composable
fun HealthCheckScreen(
    onNavigateBack: () -> Unit,
    viewModel: HealthCheckViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = TextColor)
            }
            Text("Chequeo de salud", color = TextColor, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        Button(
            onClick = viewModel::runHealthCheck,
            enabled = state !is HealthCheckViewModel.UiState.Running,
            colors = ButtonDefaults.buttonColors(containerColor = AccentColor, contentColor = Color.Black),
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        ) {
            Text(if (state is HealthCheckViewModel.UiState.Running) "Escaneando…" else "Escanear ahora")
        }

        when (val s = state) {
            is HealthCheckViewModel.UiState.Idle ->
                Text("Un toque y RevScope revisa códigos de falla, readiness para la tecnomecánica, mezcla, sensor O2, batería y temperatura.", color = TextMutedColor, fontSize = 13.sp)
            is HealthCheckViewModel.UiState.Running -> {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = AccentColor)
                Text(s.paso, color = TextMutedColor, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            }
            is HealthCheckViewModel.UiState.Error ->
                Text(s.mensaje, color = Color(0xFFFF5252), fontSize = 14.sp)
            is HealthCheckViewModel.UiState.Done -> {
                val fecha = SimpleDateFormat("d MMM yyyy, HH:mm", Locale("es")).format(Date(s.timestamp))
                Text("Último chequeo: $fecha", color = TextMutedColor, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(s.items) { item -> DiagnosisRow(item) }
                }
            }
        }
    }
}

@Composable
private fun DiagnosisRow(d: DiagnosticRules.Diagnosis) {
    val color = when (d.nivel) {
        DiagnosticRules.Nivel.OK -> Color(0xFF4CAF50)
        DiagnosticRules.Nivel.ATENCION -> Color(0xFFFFC107)
        DiagnosticRules.Nivel.FALLA -> Color(0xFFFF5252)
    }
    Surface(shape = RoundedCornerShape(12.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 5.dp).size(10.dp).background(color, CircleShape))
            Column(Modifier.padding(start = 12.dp)) {
                Text(d.titulo, color = TextColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(d.causaProbable, color = TextMutedColor, fontSize = 12.sp)
                Text(d.area, color = TextMutedColor, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}
```

- [ ] **Step 3: Card en el hub + navegación**

En `WorkshopScreen`: nuevo parámetro `onOpenHealthCheck: () -> Unit` (primero), y card al TOPE de la lista de tools:

```kotlin
        WorkshopTool(Icons.Default.MonitorHeart, "Chequeo de salud",
            "Escaneo completo con diagnóstico en español — DTCs, readiness, mezcla, batería", false, onOpenHealthCheck),
```

(`needsConnection = false` para poder ver el último informe sin adaptador; import `androidx.compose.material.icons.filled.MonitorHeart`)

`Screen.kt`: `object HealthCheck : Screen("health_check")`.

`RevScopeNavGraph`: en la ruta Workshop agregar `onOpenHealthCheck = { navController.navigate(Screen.HealthCheck.route) }` y registrar:

```kotlin
            composable(Screen.HealthCheck.route) {
                HealthCheckScreen(onNavigateBack = { navController.popBackStack() })
            }
```

`feature/workshop/build.gradle.kts`: agregar `implementation(project(":core:data"))`.

- [ ] **Step 4: Compilar**

Run: `$GRADLE :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add feature/workshop/ app/src/main/kotlin/com/revscope/app/navigation/
git commit -m "feat: chequeo de salud de un toque con diagnóstico interpretado y persistencia"
```

---

### Task 6: Informe compartible como imagen (HealthReportCard)

**Files:**
- Create: `feature/workshop/src/main/kotlin/com/revscope/feature/workshop/HealthReportCard.kt`
- Modify: `feature/workshop/src/main/kotlin/com/revscope/feature/workshop/HealthCheckViewModel.kt` (función share)
- Modify: `feature/workshop/src/main/kotlin/com/revscope/feature/workshop/HealthCheckScreen.kt` (botón 📷)

**Interfaces:**
- Consumes: patrón de `feature/session/src/main/kotlin/com/revscope/feature/session/TripShareCard.kt` — LEERLO PRIMERO y replicar: tamaño 1080×1350, render con Canvas/Paint, escritura a cache dir, FileProvider authority, Intent chooser. Usa el MISMO FileProvider ya declarado en el manifest.
- Produces: `HealthReportCard.render(context, items, dtcCodes, vehicleName, timestamp): Uri?` y `HealthCheckViewModel.share(context)`.

- [ ] **Step 1: HealthReportCard**

Replicar la estructura de TripShareCard adaptando el contenido (dibujo con android.graphics.Canvas sobre Bitmap 1080×1350, fondo oscuro 0xFF0A0A0F):
- Header: "REVSCOPE" (accent 0xFFE8FF00) + "Chequeo de salud" + nombre del vehículo + fecha.
- Cuerpo: hasta 12 ítems — círculo de color por nivel (verde/ámbar/rojo) + título (16-18px scaled) + causa en gris; si hay más ítems, línea final "… y N más".
- Resumen grande arriba: "X OK · Y atención · Z fallas".
- Footer: "github.com/santiquiroz/revscope".
- Guardar PNG en el mismo cache dir/subcarpeta que TripShareCard y devolver el Uri del FileProvider con los mismos flags.

- [ ] **Step 2: share en el ViewModel + botón en la pantalla**

ViewModel:

```kotlin
    fun share(context: android.content.Context) {
        val done = _state.value as? UiState.Done ?: return
        viewModelScope.launch {
            val uri = HealthReportCard.render(
                context, done.items, done.dtcCodes,
                sessionManager.activeProfile.value?.name ?: "Mi vehículo", done.timestamp,
            ) ?: return@launch
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "Compartir informe"))
        }
    }
```

(ajustar al patrón exacto que use TripShareCard/SessionDetailViewModel para compartir — mismo chooser, mismos flags)

Screen: `IconButton` con `Icons.Default.PhotoCamera` en la fila del título, visible solo cuando el estado es `Done`, llamando `viewModel.share(context)` con `val context = LocalContext.current`.

- [ ] **Step 3: Compilar y commit**

Run: `$GRADLE :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

```bash
git add feature/workshop/
git commit -m "feat: informe de salud compartible como imagen para enviar al cliente"
```

---

### Task 7: Mezcla y combustión en vivo (LiveMixtureScreen)

**Files:**
- Create: `feature/workshop/src/main/kotlin/com/revscope/feature/workshop/LiveMixtureViewModel.kt`
- Create: `feature/workshop/src/main/kotlin/com/revscope/feature/workshop/LiveMixtureScreen.kt`
- Modify: `WorkshopScreen.kt` (card), `Screen.kt` (+ `LiveMixture : Screen("live_mixture")`), `RevScopeNavGraph.kt` (ruta)

**Interfaces:**
- Consumes: `ObdSessionManager.readings` + `setWorkshopMode` (Task 1), `DiagnosticRules` (Task 3).

- [ ] **Step 1: ViewModel**

```kotlin
package com.revscope.feature.workshop

import androidx.lifecycle.ViewModel
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.session.ObdSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class LiveMixtureViewModel @Inject constructor(
    private val sessionManager: ObdSessionManager,
) : ViewModel() {

    val readings: StateFlow<Map<String, ObdReading>> = sessionManager.readings

    fun setWorkshopMode(enabled: Boolean) = sessionManager.setWorkshopMode(enabled)
}
```

- [ ] **Step 2: Screen**

Estructura (usar los mismos colores locales del módulo):
- `DisposableEffect(Unit) { viewModel.setWorkshopMode(true); onDispose { viewModel.setWorkshopMode(false) } }`
- `val readings by viewModel.readings.collectAsState()`
- LazyColumn de filas; cada fila se muestra SOLO si el PID tiene lectura. Definición de filas (label → pid → diagnóstico opcional):

```kotlin
private data class MixtureRow(
    val pid: String,
    val label: String,
    val diagnose: ((Double, Map<String, com.revscope.core.obd.model.ObdReading>) -> com.revscope.core.obd.workshop.DiagnosticRules.Diagnosis?)? = null,
)

private val ROWS = listOf(
    MixtureRow("06", "Fuel trim corto B1"),
    MixtureRow("07", "Fuel trim largo B1", { v, _ -> com.revscope.core.obd.workshop.DiagnosticRules.evaluarFuelTrimLargo(v) }),
    MixtureRow("08", "Fuel trim corto B2"),
    MixtureRow("09", "Fuel trim largo B2", { v, _ -> com.revscope.core.obd.workshop.DiagnosticRules.evaluarFuelTrimLargo(v) }),
    MixtureRow("14", "Sensor O2 B1S1"),
    MixtureRow("15", "Sensor O2 B1S2"),
    MixtureRow("18", "Sensor O2 B2S1"),
    MixtureRow("19", "Sensor O2 B2S2"),
    MixtureRow("44", "Lambda comandado"),
    MixtureRow("10", "Flujo de aire (MAF)"),
    MixtureRow("0B", "Presión múltiple (MAP)"),
    MixtureRow("0A", "Presión de combustible"),
    MixtureRow("0E", "Avance de encendido"),
    MixtureRow("2E", "Purga EVAP"),
    MixtureRow("3C", "Temp catalizador"),
)
```

- Cada fila: label izquierda, `"%.1f %s".format(value, unit)` derecha en accent; debajo, si `diagnose != null`, chip con el color del nivel y `titulo` del Diagnosis.
- Barra de progreso simple: `LinearProgressIndicator(progress = ((value - min) / (max - min)).toFloat().coerceIn(0f, 1f))` usando min/max de la `PidDefinition` — obtener via un `PidRegistry` inyectado en el ViewModel (`registry.getDefinition(pid)`), exponer `fun definition(pid: String) = registry.getDefinition(pid)`.
- Encabezado con back button igual que HealthCheckScreen + texto "Los valores se interpretan en tiempo real. Motor encendido para ver la mezcla trabajar."
- Si NINGUNA fila tiene datos: mensaje "Esperando datos del vehículo… (requiere conexión y motor encendido)".

Card en WorkshopScreen (después de "Códigos de falla"): parámetro `onOpenLiveMixture`, icono `Icons.Default.Science`, título "Mezcla y combustión", descripción "Trims, O2, lambda y MAF interpretados en vivo", `needsConnection = true`.

Ruta en Screen.kt y NavGraph igual que HealthCheck.

- [ ] **Step 3: Compilar y commit**

Run: `$GRADLE :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

```bash
git add feature/workshop/ app/src/main/kotlin/com/revscope/app/navigation/
git commit -m "feat: vista de mezcla y combustión en vivo con interpretación por regla"
```

---

### Task 8: Build integral, deploy y push

- [ ] **Step 1**: `$GRADLE :core:obd:testDebugUnitTest` → todos verdes (141 + ~26 nuevos)
- [ ] **Step 2**: `$GRADLE :app:assembleDebug` → BUILD SUCCESSFUL
- [ ] **Step 3**: `adb install -r app/build/outputs/apk/debug/app-debug.apk` + launch. **Verificación crítica de la migración**: tras instalar, abrir Viajes y confirmar que el historial sigue ahí (la migración 9→10 preservó datos). Si el historial está vacío → la migración falló → REPORTAR INMEDIATAMENTE, no continuar.
- [ ] **Step 4**: push con `$env:GITHUB_TOKEN = $null; git push` (PowerShell — el token de trabajo pisa el keyring).
