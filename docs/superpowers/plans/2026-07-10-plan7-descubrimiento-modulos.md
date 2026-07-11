# Plan 7: Descubrimiento de módulos por ATSH (escáner dirigido a módulos de carrocería)

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development. EN ORDEN.

**Goal:** permitir que el escáner Modo 22 apunte a módulos distintos de la ECU de motor (por su header CAN de 11 bits), para descubrir DIDs de subsistemas de carrocería (p. ej. sensores de parqueo) — sin romper la telemetría concurrente.

**Architecture:** un exchange atómico a nivel de transporte fija el header destino (`AT SH`), lanza la petición y **restaura** el header funcional (7DF) y `H0` bajo un único lock del `ioMutex`, de modo que ninguna petición de telemetría se emita bajo el header custom. La función se restringe a CAN 11-bit (protocolos 6/8). Sobre esa base, el ViewModel del escáner barre una lista de headers candidatos, lista los que responden, y permite dirigir el escaneo/vigilancia de DIDs a un módulo elegido.

## Global Constraints
- Gradle: `C:\Users\santi\.gradle\wrapper\dists\gradle-8.11.1-bin\bpt9gzteqjrbo1mjrsomdt32c\gradle-8.11.1\bin\gradle.bat` (=$GRADLE) desde c:/personal/OBD2.
- Commits en español, NUNCA `Co-Authored-By`. UI en español. `CancellationException` SIEMPRE se relanza (nunca la traga un `runCatching`/`catch (e: Exception)` sin rethrow).
- **REGLA DE ORO: cero regresiones al camino de telemetría.** Sin objetivo seleccionado, `startScan`/`startWatch` se comportan EXACTAMENTE como hoy (rawExchange a la ECU por defecto). Los 400+ tests existentes intactos.
- **Solo lectura.** El descubrimiento solo emite servicios de diagnóstico de lectura (`22`, `3E`, `09`). NUNCA servicios de escritura (`2E`, `2F`, `31`, `04`, `10` sesión extendida agresiva, etc.).
- **Garaje.** Es una herramienta estacionaria: la UI advierte "vehículo detenido". No bloquea, pero avisa.

---

### Task 1: Transport — exchange atómico dirigido a un módulo (SEGURIDAD)

**Files:**
- Modify: `core/obd/src/main/kotlin/com/revscope/core/obd/connection/Transport.kt`
- Modify: `core/obd/src/main/kotlin/com/revscope/core/obd/connection/ClassicBtTransport.kt`
- Modify: `core/obd/src/main/kotlin/com/revscope/core/obd/session/ObdSessionManager.kt`
- Modify: `core/obd/src/main/kotlin/com/revscope/core/obd/viewmodel/ConnectionViewModel.kt`

**Interfaces:**
- Produces:
  - `Transport.targetedExchange(requestHeader: String, request: String, timeoutMs: Long): String`
  - `ObdSessionManager.probeModule(requestHeader: String, request: String, timeoutMs: Long = MODULE_PROBE_TIMEOUT_MS): Result<String>`
  - `ObdSessionManager.currentProtocolNumber(): String?`
  - `ConnectionViewModel.probeModule(...)` y `ConnectionViewModel.protocolNumber(): String?`

**Por qué atómico (el WHY que el revisor debe verificar):** `exchange` serializa cada par send/receive, pero `AT SH` (fijar header) y la petición son exchanges separados. Entre ambos, un poller de telemetría podría adquirir el `ioMutex` y emitir su `01 0C` bajo el header custom → respuesta equivocada o a un módulo ajeno. Por eso todo el ciclo `H1 → SH target → request → recv → restore SH 7DF → restore H0` va dentro de UN solo `ioMutex.withLock`.

- [ ] **Step 1: Añadir el método a la interfaz `Transport`**

En `Transport.kt`, dentro de la interfaz, tras `exchange(...)`:

```kotlin
    /**
     * Atomic read-only diagnostic exchange to a specific 11-bit CAN module.
     *
     * Under the single I/O lock: enables response headers (AT H1), sets the
     * request header (AT SH), sends [request], reads the reply, then ALWAYS
     * restores the functional broadcast header (7DF) and headers-off (AT H0).
     * Restoring inside the same lock guarantees concurrent telemetry — which
     * relies on the default functional header — is never emitted under
     * [requestHeader].
     *
     * [requestHeader] is an 11-bit CAN id as 3 hex chars (e.g. "7E0", "720").
     * The reply keeps its source-header prefix (headers are on for this call),
     * so the caller can tell which module answered.
     *
     * Only valid on 11-bit CAN protocols; callers must gate on the protocol.
     * Throws [java.io.IOException] on timeout (treat as "no module answered").
     */
    suspend fun targetedExchange(requestHeader: String, request: String, timeoutMs: Long): String
```

- [ ] **Step 2: Implementar en `ClassicBtTransport`**

En `ClassicBtTransport.kt`, añadir constantes junto a las existentes (arriba del archivo, zona `private const`):

```kotlin
private const val AT_RESTORE_TIMEOUT_MS = 1_000L
private const val FUNCTIONAL_HEADER_11BIT = "7DF"
```

Y el método (tras `exchange`):

```kotlin
    override suspend fun targetedExchange(
        requestHeader: String,
        request: String,
        timeoutMs: Long,
    ): String = ioMutex.withLock {
        withContext(Dispatchers.IO) { drainStaleInput() }
        try {
            send("AT H1\r"); receive(AT_RESTORE_TIMEOUT_MS)
            send("AT SH $requestHeader\r"); receive(AT_RESTORE_TIMEOUT_MS)
            val cmd = if (request.endsWith("\r")) request else "$request\r"
            send(cmd)
            receive(timeoutMs)
        } finally {
            // Restaurar SIEMPRE los defaults de telemetría, aun si la petición
            // agota el tiempo o el módulo no responde. Sin esto, el header custom
            // quedaría fijo y la telemetría saldría dirigida al módulo equivocado.
            withContext(Dispatchers.IO) { drainStaleInput() }
            runCatching {
                send("AT SH $FUNCTIONAL_HEADER_11BIT\r"); receive(AT_RESTORE_TIMEOUT_MS)
                send("AT H0\r"); receive(AT_RESTORE_TIMEOUT_MS)
            }
        }
    }
```

Nota: el `runCatching` en el `finally` es intencional — la restauración es best-effort y no debe enmascarar la excepción original del `try` (timeout del módulo). La `CancellationException` original del `try`, si la hubo, se propaga tras el `finally`.

- [ ] **Step 3: Exponer en `ObdSessionManager`**

En `ObdSessionManager.kt`, junto a `rawExchange` (~línea 473), añadir constante de timeout (zona de constantes del archivo) y los métodos:

```kotlin
    // Timeout de sondeo de módulo — corto: un módulo ausente debe fallar rápido.
    // (declarar como const junto al resto, p. ej. private const val MODULE_PROBE_TIMEOUT_MS = 1_500L)

    /** Sondeo de solo lectura a un módulo por su header 11-bit. Result.failure = sin respuesta. */
    suspend fun probeModule(
        requestHeader: String,
        request: String,
        timeoutMs: Long = MODULE_PROBE_TIMEOUT_MS,
    ): Result<String> {
        val bt = transport ?: return Result.failure(IllegalStateException("Not connected"))
        return try {
            Result.success(bt.targetedExchange(requestHeader, request, timeoutMs))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Número de protocolo OBD actual (AT DPN). "6"/"8" = CAN 11-bit; "A6" = auto→6. Null si falla. */
    suspend fun currentProtocolNumber(): String? {
        val bt = transport ?: return null
        return try {
            ResponseParser.cleanResponse(bt.exchange("AT DPN\r", 1_500L)).takeIf { it.isNotEmpty() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }
```

Verificar que `transport` es del tipo con `targetedExchange` (es `ClassicBtTransport`/`Transport`). Usar el mismo nombre de campo que usa `rawExchange` (`transport`).

- [ ] **Step 4: Exponer en `ConnectionViewModel`**

En `ConnectionViewModel.kt`, tras `rawExchange`:

```kotlin
    suspend fun probeModule(
        requestHeader: String,
        request: String,
        timeoutMs: Long = 1_500L,
    ): Result<String> = manager.probeModule(requestHeader, request, timeoutMs)

    suspend fun protocolNumber(): String? = manager.currentProtocolNumber()
```

- [ ] **Step 5: Build**

Run: `$GRADLE :core:obd:assembleDebug`
Expected: BUILD SUCCESSFUL. Sin tests nuevos aquí (lógica de I/O; la lógica pura se prueba en Task 4).

- [ ] **Step 6: Commit**

```bash
git add core/obd/src/main/kotlin/com/revscope/core/obd/connection/Transport.kt \
        core/obd/src/main/kotlin/com/revscope/core/obd/connection/ClassicBtTransport.kt \
        core/obd/src/main/kotlin/com/revscope/core/obd/session/ObdSessionManager.kt \
        core/obd/src/main/kotlin/com/revscope/core/obd/viewmodel/ConnectionViewModel.kt
git commit -m "feat: exchange atómico dirigido a módulos por header CAN 11-bit sin tocar la telemetría"
```

---

### Task 2: Lógica de descubrimiento y escaneo dirigido (ModuleDiscovery puro + ViewModel)

**Files:**
- Create: `feature/settings/src/main/kotlin/com/revscope/feature/settings/ModuleDiscovery.kt` (objeto puro, testeable)
- Modify: `feature/settings/src/main/kotlin/com/revscope/feature/settings/Mode22ScannerViewModel.kt`
- Test: `feature/settings/src/test/kotlin/com/revscope/feature/settings/ModuleDiscoveryTest.kt` (código en Task 4)

**Interfaces:**
- Consumes: `ConnectionViewModel.probeModule`, `ConnectionViewModel.protocolNumber` (Task 1); `ResponseParser.cleanResponse`, `ResponseParser.isErrorResponse`.
- Produces (objeto `ModuleDiscovery`):
  - `fun isValid11BitHeader(header: String): Boolean`
  - `fun isCan11Bit(protocolNumber: String?): Boolean`
  - `fun candidateHeaders(): List<Candidate>` (data class `Candidate(val header: String, val label: String)`)
  - `data class ProbeResult(val requestHeader: String, val replyHeader: String?, val present: Boolean)`
  - `fun interpretProbe(requestHeader: String, raw: String): ProbeResult`

- [ ] **Step 1: Escribir tests (RED)** — ver Task 4 (los tests viven en Task 4 pero se escriben ANTES de este código; si ejecutas en orden estricto, invierte: primero el archivo de test de Task 4, luego este). Para SDD: este implementador crea `ModuleDiscovery.kt` con los tests de `ModuleDiscoveryTest.kt` incluidos en su entrega.

- [ ] **Step 2: Crear `ModuleDiscovery.kt`**

```kotlin
package com.revscope.feature.settings

import com.revscope.core.obd.protocol.ResponseParser

/**
 * Lógica pura para descubrir módulos por header CAN de 11 bits y para dirigir
 * el escaneo Modo 22 a un módulo distinto de la ECU de motor.
 */
object ModuleDiscovery {

    data class Candidate(val header: String, val label: String)
    data class ProbeResult(val requestHeader: String, val replyHeader: String?, val present: Boolean)

    private val HEADER_11BIT = Regex("^[0-9A-Fa-f]{3}$")

    /** Header de petición 11-bit válido = exactamente 3 dígitos hex. */
    fun isValid11BitHeader(header: String): Boolean = HEADER_11BIT.matches(header.trim())

    /**
     * True solo en ISO 15765-4 CAN de 11 bits (protocolos 6 y 8).
     * AT DPN puede devolver "A6" (auto encontró 6) o "6"; tomamos el último dígito.
     * 7/9 = 29-bit; 1-5 = no-CAN. Restauramos a 7DF, válido solo en 11-bit.
     */
    fun isCan11Bit(protocolNumber: String?): Boolean {
        val p = protocolNumber?.trim()?.uppercase()?.removePrefix("A") ?: return false
        return p == "6" || p == "8"
    }

    /**
     * Lista curada de headers 11-bit a sondear. Las ECU OBD estándar viven en
     * 7E0–7E7; los módulos de carrocería/chasis usan direcciones propietarias
     * (varían por marca — etiqueta genérica + hex).
     */
    fun candidateHeaders(): List<Candidate> = buildList {
        add(Candidate("7DF", "Difusión funcional (todas las ECU)"))
        for (i in 0..7) add(Candidate("7E${i}", "ECU física 7E$i (motor/trans/ABS…)"))
        listOf("700", "710", "720", "726", "730", "740", "745", "750", "760", "765", "770", "7A0", "7B0", "7C0")
            .forEach { add(Candidate(it, "Módulo propietario $it")) }
    }

    /**
     * Interpreta la respuesta cruda (con header, porque H1 está activo) de un sondeo.
     * present = el módulo contestó ALGO — incluye una respuesta negativa UDS "7F"
     * (el módulo existe pero rechazó el DID). Solo NO DATA / error / vacío = ausente.
     */
    fun interpretProbe(requestHeader: String, raw: String): ProbeResult {
        val clean = ResponseParser.cleanResponse(raw)
        if (clean.isEmpty() || ResponseParser.isErrorResponse(raw)) {
            return ProbeResult(requestHeader, replyHeader = null, present = false)
        }
        // Con H1, un frame CAN 11-bit empieza por el header de respuesta (3 hex).
        val replyHeader = clean.take(3).takeIf { HEADER_11BIT.matches(it) }
        return ProbeResult(requestHeader, replyHeader, present = true)
    }
}
```

Verificar contra la firma real de `ResponseParser.isErrorResponse` / `cleanResponse` (ya usadas por el scanner). Si `isErrorResponse` ya considera "NO DATA" como error, el chequeo cubre NO DATA; si no, añadir `|| clean.contains("NODATA")` tras normalizar. **Confirmar leyendo `ResponseParser`.**

- [ ] **Step 3: Extender `Mode22ScannerViewModel`**

Añadir estado y funciones SIN romper las existentes:

```kotlin
    // Descubrimiento de módulos
    enum class ProtocolSupport { UNKNOWN, SUPPORTED, UNSUPPORTED }

    private val _protocolSupport = MutableStateFlow(ProtocolSupport.UNKNOWN)
    val protocolSupport: StateFlow<ProtocolSupport> = _protocolSupport.asStateFlow()

    private val _modules = MutableStateFlow<List<ModuleDiscovery.ProbeResult>>(emptyList())
    val modules: StateFlow<List<ModuleDiscovery.ProbeResult>> = _modules.asStateFlow()

    private val _discovering = MutableStateFlow(false)
    val discovering: StateFlow<Boolean> = _discovering.asStateFlow()

    // Objetivo actual: null = ECU por defecto (comportamiento de hoy)
    private val _targetHeader = MutableStateFlow<String?>(null)
    val targetHeader: StateFlow<String?> = _targetHeader.asStateFlow()

    fun selectTarget(header: String?) { _targetHeader.value = header }

    fun discoverModules(connectionVm: ConnectionViewModel) {
        scanJob?.cancel()
        _modules.value = emptyList()
        scanJob = viewModelScope.launch {
            _discovering.value = true
            try {
                if (!ModuleDiscovery.isCan11Bit(connectionVm.protocolNumber())) {
                    _protocolSupport.value = ProtocolSupport.UNSUPPORTED
                    return@launch
                }
                _protocolSupport.value = ProtocolSupport.SUPPORTED
                for (candidate in ModuleDiscovery.candidateHeaders()) {
                    val raw = connectionVm.probeModule(candidate.header, PROBE_REQUEST).getOrNull()
                    val result = raw?.let { ModuleDiscovery.interpretProbe(candidate.header, it) }
                    if (result?.present == true) {
                        _modules.value = _modules.value.filterNot { it.requestHeader == candidate.header } + result
                    }
                }
            } finally {
                _discovering.value = false
            }
        }
    }
```

Y añadir la constante en el `companion`/top: `private const val PROBE_REQUEST = "22 F190"` (leer VIN — DID casi universal). Añadir imports (`ConnectionViewModel` ya está importado).

- [ ] **Step 4: Escaneo/watch dirigido — cero regresión**

Modificar `startScan` y `startWatch` para usar el objetivo cuando exista, manteniendo el camino actual cuando `_targetHeader.value == null`:

En `startScan`, reemplazar la línea del exchange:

```kotlin
                val response = readDid(connectionVm, didHex)?.let { it } ?: continue
```

donde se añade el helper privado:

```kotlin
    // Con objetivo → probeModule (header custom, atómico); sin objetivo → rawExchange (ECU por defecto, como hoy).
    private suspend fun readDid(connectionVm: ConnectionViewModel, didHex: String): String? {
        val target = _targetHeader.value
        return if (target == null) {
            connectionVm.rawExchange("22 $didHex\r", SCAN_TIMEOUT_MS).getOrNull()
        } else {
            connectionVm.probeModule(target, "22 $didHex", SCAN_TIMEOUT_MS).getOrNull()
        }
    }
```

Aplicar el mismo helper en `startWatch` (sustituir su `connectionVm.rawExchange("22 ${hit.did}\r", SCAN_TIMEOUT_MS)` por `readDid(connectionVm, hit.did)`). El resto de ambas funciones intacto. `extractDidData` sigue funcionando: con H1 el reply trae header + `62DIDdata`; `indexOf("62$did")` lo localiza igual.

Añadir a `clearHits`/`stop` el reset de descubrimiento si aplica (no obligatorio). En `onCleared` no hace falta más (scanJob ya se cancela).

- [ ] **Step 5: Build**

Run: `$GRADLE :feature:settings:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add feature/settings/src/main/kotlin/com/revscope/feature/settings/ModuleDiscovery.kt \
        feature/settings/src/main/kotlin/com/revscope/feature/settings/Mode22ScannerViewModel.kt
git commit -m "feat: descubrimiento de módulos CAN 11-bit y escaneo Modo 22 dirigido a un módulo"
```

---

### Task 3: UI — aviso inofensivo, descubrir módulos, selección de objetivo

**Files:**
- Modify: `feature/settings/src/main/kotlin/com/revscope/feature/settings/Mode22ScannerScreen.kt`

**Interfaces:**
- Consumes: los flows nuevos del ViewModel (`protocolSupport`, `modules`, `discovering`, `targetHeader`) y `discoverModules`, `selectTarget`.

- [ ] **Step 1: Aviso de inocuidad (arriba del contenido, siempre visible)**

Tras el `Column` de contenido, como primer elemento, una tarjeta:

```kotlin
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceHighColor, RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text("🛈", fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp))
                Text(
                    "Este escaneo es de SOLO LECTURA: envía peticiones de diagnóstico " +
                        "estándar para leer datos. No escribe, no borra códigos ni modifica " +
                        "nada del vehículo — es seguro. Hazlo con el vehículo detenido.",
                    color = TextMutedColor,
                    fontSize = 12.sp,
                )
            }
            Spacer(Modifier.height(12.dp))
```

- [ ] **Step 2: Bloque "Descubrir módulos"**

Bajo el aviso (y bajo la nota de "Conecta el adaptador" existente), añadir sección:

```kotlin
            val protocolSupport by vm.protocolSupport.collectAsState()
            val modules by vm.modules.collectAsState()
            val discovering by vm.discovering.collectAsState()
            val targetHeader by vm.targetHeader.collectAsState()

            Button(
                onClick = { vm.discoverModules(connectionVm) },
                enabled = connectionState is ConnectionState.Connected && !discovering,
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceHighColor),
            ) {
                Text(if (discovering) "Buscando módulos…" else "Descubrir módulos", color = TextPrimaryColor)
            }

            if (protocolSupport == Mode22ScannerViewModel.ProtocolSupport.UNSUPPORTED) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tu vehículo no usa CAN de 11 bits con este adaptador, así que el " +
                        "descubrimiento de módulos de carrocería no está disponible. El escaneo " +
                        "de la ECU de motor sí funciona.",
                    color = DangerColor,
                    fontSize = 12.sp,
                )
            }

            if (modules.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Módulos que respondieron — toca uno para dirigir el escaneo:", color = TextMutedColor, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    modules.forEach { m ->
                        val selected = targetHeader == m.requestHeader
                        Text(
                            m.requestHeader + (m.replyHeader?.let { " → $it" } ?: ""),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (selected) BgColor else TextPrimaryColor,
                            modifier = Modifier
                                .background(if (selected) AccentColor else SurfaceHighColor, RoundedCornerShape(16.dp))
                                .clickable { vm.selectTarget(if (selected) null else m.requestHeader) }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            targetHeader?.let {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Objetivo del escaneo: ", color = TextMutedColor, fontSize = 12.sp)
                    Text(it, color = AccentColor, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Volver a la ECU",
                        color = TextMutedColor,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .background(SurfaceHighColor, RoundedCornerShape(12.dp))
                            .clickable { vm.selectTarget(null) }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
```

Colocar este bloque ANTES de la fila de chips de rangos existentes, de modo que el flujo sea: aviso → descubrir/objetivo → rangos → botones escanear/vigilar → resultados. No tocar la lógica de rangos ni de hits.

- [ ] **Step 3: Actualizar la instrucción de texto** para mencionar el nuevo flujo (opcional, 1 línea): "También puedes descubrir módulos y dirigir el escaneo a uno (p. ej. buscar un DID de un subsistema de carrocería)."

- [ ] **Step 4: Build**

Run: `$GRADLE :feature:settings:assembleDebug`
Expected: BUILD SUCCESSFUL. Verificar imports (`collectAsState`, `horizontalScroll`, `FontFamily` ya están).

- [ ] **Step 5: Commit**

```bash
git add feature/settings/src/main/kotlin/com/revscope/feature/settings/Mode22ScannerScreen.kt
git commit -m "feat: UI de descubrimiento de módulos con aviso de solo lectura y selección de objetivo"
```

---

### Task 4: Tests puros de `ModuleDiscovery`

**Files:**
- Create: `feature/settings/src/test/kotlin/com/revscope/feature/settings/ModuleDiscoveryTest.kt`

**Interfaces:**
- Consumes: `ModuleDiscovery` (Task 2).

- [ ] **Step 1: Escribir tests**

```kotlin
package com.revscope.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleDiscoveryTest {

    @Test
    fun `header 11-bit valido es exactamente 3 hex`() {
        assertTrue(ModuleDiscovery.isValid11BitHeader("7E0"))
        assertTrue(ModuleDiscovery.isValid11BitHeader("720"))
        assertTrue(ModuleDiscovery.isValid11BitHeader("7df"))
    }

    @Test
    fun `header invalido rechazado`() {
        assertFalse(ModuleDiscovery.isValid11BitHeader(""))
        assertFalse(ModuleDiscovery.isValid11BitHeader("7DF0"))
        assertFalse(ModuleDiscovery.isValid11BitHeader("GG1"))
        assertFalse(ModuleDiscovery.isValid11BitHeader("7E"))
    }

    @Test
    fun `solo protocolos CAN 11-bit soportados`() {
        assertTrue(ModuleDiscovery.isCan11Bit("6"))
        assertTrue(ModuleDiscovery.isCan11Bit("8"))
        assertTrue(ModuleDiscovery.isCan11Bit("A6")) // auto encontró 6
        assertTrue(ModuleDiscovery.isCan11Bit("A8"))
    }

    @Test
    fun `protocolos 29-bit y no-CAN y null no soportados`() {
        assertFalse(ModuleDiscovery.isCan11Bit("7"))
        assertFalse(ModuleDiscovery.isCan11Bit("9"))
        assertFalse(ModuleDiscovery.isCan11Bit("A7"))
        assertFalse(ModuleDiscovery.isCan11Bit("3"))
        assertFalse(ModuleDiscovery.isCan11Bit(null))
        assertFalse(ModuleDiscovery.isCan11Bit(""))
    }

    @Test
    fun `respuesta positiva marca modulo presente y extrae reply header`() {
        // H1 activo: reply 11-bit empieza por el header de respuesta
        val r = ModuleDiscovery.interpretProbe("7E0", "7E8 06 62 F1 90 12 34 56 \r>")
        assertTrue(r.present)
        assertEquals("7E8", r.replyHeader)
    }

    @Test
    fun `respuesta negativa 7F prueba que el modulo existe`() {
        // El módulo contestó rechazando el DID (7F 22 31 = requestOutOfRange) → PRESENTE
        val r = ModuleDiscovery.interpretProbe("720", "728 03 7F 22 31 \r>")
        assertTrue(r.present)
        assertEquals("728", r.replyHeader)
    }

    @Test
    fun `NO DATA marca modulo ausente`() {
        val r = ModuleDiscovery.interpretProbe("7A0", "NO DATA\r>")
        assertFalse(r.present)
        assertNull(r.replyHeader)
    }

    @Test
    fun `respuesta vacia marca ausente`() {
        val r = ModuleDiscovery.interpretProbe("7C0", "\r>")
        assertFalse(r.present)
        assertFalse(r.present)
    }
}
```

**IMPORTANTE:** ajustar las cadenas de ejemplo y las aserciones a lo que realmente devuelven `ResponseParser.cleanResponse` / `isErrorResponse` (leerlos). Si `cleanResponse` quita el header, el `replyHeader` esperado cambia — en ese caso el implementador de Task 2 debe extraer el header ANTES de `cleanResponse` (sobre el crudo) y estos tests deben reflejar el crudo. Alinear implementación y test; NO forzar un test que no corresponde al parser real.

- [ ] **Step 2: Ejecutar**

Run: `$GRADLE :feature:settings:testDebugUnitTest`
Expected: PASS (todos), y los tests existentes de `:feature:settings` intactos.

- [ ] **Step 3: Commit**

```bash
git add feature/settings/src/test/kotlin/com/revscope/feature/settings/ModuleDiscoveryTest.kt
git commit -m "test: cobertura pura de descubrimiento de módulos (header, protocolo, parser de sondeo)"
```

---

### Task 5 (inline): build total + install + release v1.4.0

- `$GRADLE :app:assembleDebug` + suite `:core:obd:testDebugUnitTest :feature:settings:testDebugUnitTest`.
- `adb install -r` cuando el dispositivo esté en línea.
- Bump versión, notas de release v1.4.0 (acumulado desde v1.3.0 + descubrimiento de módulos), tag y push.
