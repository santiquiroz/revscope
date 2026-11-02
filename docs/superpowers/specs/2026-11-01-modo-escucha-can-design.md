# Modo escucha CAN (Vgate en monitor) + herramientas MCP

Fecha: 2026-11-01. Origen: Fase 0 de RevDash (ingeniería inversa del tablero de la Apache RTR 160 4V, repo `santiquiroz/revdash`) y el pendiente "modo escucha" de `docs/catalogo-propietario-fuentes.md`. El proceso debe servir para cualquier vehículo con CAN, no solo la Apache.

## Objetivo

Que un agente de IA conectado al MCP de RevScope (por ejemplo, Claude en un portátil en el garaje) pueda:

1. Escuchar el bus CAN por el adaptador ELM327 (Vgate) durante N segundos, con etiqueta ("base", "direccional_izq"…).
2. Guardar cada captura en el teléfono en formato candump (estándar de can-utils; lo leen SavvyCAN, cabana, python-can y `tools/diff_can.py` de RevDash).
3. Listar y leer capturas, y comparar una captura base contra una de acción para encontrar qué IDs, bytes y bits cambian con la acción.

## Reglas

- Solo escuchar: `AT CSM1` (monitor silencioso, sin ACK). Si el adaptador responde `?`, la captura sigue y se marca `silencioso=false` para que el agente lo sepa.
- Nunca durante un viaje grabando: el ELM327 es half-duplex y el monitor detiene el sondeo. Con `EstadoViaje.Grabando` la herramienta responde error.
- Toda la configuración AT del monitor se restaura SIEMPRE al terminar (bloque `finally`), a los valores que deja la inicialización de la app (`ElmCommandBuilder`: `AT E0`, `AT L0`, `AT S0`, `AT H0`; CAF y receive address en su valor por defecto): `AT CAF1`, `AT AR`, `AT S0`, `AT H0`.
- Solo protocolos CAN (números ELM 6, 7, 8, 9). 7 y 9 son de 29 bits.
- Capturas en archivos (no Room): `filesDir/capturas_can/<id>.log` (candump) + `<id>.json` (metadatos). Máximo 200 capturas; al pasar el tope se borran las más viejas.

## Paquete `com.revscope.core.obd.escucha` (lógica pura, sin Android)

```kotlin
data class TramaCan(val tMs: Long, val id: Int, val extendido: Boolean, val datos: List<Int>)

data class CapturaCan(
    val id: String,
    val etiqueta: String,
    val inicioEpochMs: Long,
    val duracionMs: Long,
    val protocolo: String,      // número ELM de AT DPN sin la "A" de automático, p. ej. "6"
    val bits29: Boolean,
    val filtro: String?,        // patrón de AT CRA usado, o null
    val silencioso: Boolean,
    val desbordes: Int,         // líneas BUFFER FULL
    val errores: Int,           // líneas CAN ERROR / <RX ERROR / BUS ERROR
    val tramas: List<TramaCan>,
)

object IdCan { fun texto(id: Int, extendido: Boolean): String } // "%03X" o "%08X"

object LineaMonitor {
    fun parsear(linea: String, tMs: Long, bits29: Boolean): TramaCan?
    fun esDesborde(linea: String): Boolean   // contiene "BUFFER FULL"
    fun esError(linea: String): Boolean      // contiene "CAN ERROR", "<RX ERROR" o "BUS ERROR"
}
```

`LineaMonitor.parsear`:
- Recorta espacios y pasa a mayúsculas. Vacía, `>`, `STOPPED`, `SEARCHING...`, `?`, `OK` o cualquier token no hexadecimal → `null`.
- Con espacios (`AT S1`), 11 bits: primer token de exactamente 3 hex (≤ 0x7FF), resto tokens de 2 hex, de 0 a 8 bytes. 29 bits: los 4 primeros tokens de 2 hex forman el ID (≤ 0x1FFFFFFF), resto de 2 hex, de 0 a 8 bytes.
- Sin espacios (adaptadores que ignoran `AT S1`): un solo token hex; 11 bits: longitud ≥ 3 y `(len-3)` par con `(len-3)/2 ≤ 8`; 29 bits: longitud ≥ 8 y `(len-8)` par con `(len-8)/2 ≤ 8`.
- Cualquier otra forma → `null` (nunca excepción).

```kotlin
data class LineaCandump(val epochMs: Long, val id: Int, val extendido: Boolean, val datos: List<Int>)

object Candump {
    fun linea(trama: TramaCan, inicioEpochMs: Long, interfaz: String = "can0"): String
    fun parsear(linea: String): LineaCandump?
    fun aTrama(linea: LineaCandump, inicioEpochMs: Long): TramaCan
}
```

- Formato exacto: `(1730000000.123000) can0 7E8#03410D00`. Segundos con 6 decimales (`epochMs/1000`, `"%06d"` de `(epochMs%1000)*1000`). ID con `IdCan.texto`. Datos en hex mayúsculas sin separadores; sin datos queda `7E8#`.
- `parsear` acepta lo que produce `linea` y también microsegundos reales (`.123456` → se trunca a ms). ID de 8 dígitos = extendido. Trama remota (`#R`), más de 8 bytes o basura → `null`.

```kotlin
data class ResumenId(val id: String, val tramas: Int, val hz: Double, val largos: Set<Int>, val bytesQueCambian: List<Int>)
object ResumenCaptura { fun calcular(captura: CapturaCan): List<ResumenId> } // ordenado por id; hz = tramas / (duracionMs/1000.0), 0.0 si duracionMs == 0

data class ByteCambiado(val id: String, val byte: Int, val valorBase: Int, val valoresNuevos: List<Int>)
data class BitCambiado(val id: String, val byte: Int, val bit: Int, val valorBase: Int)  // bit 7 = MSB
data class Diferencia(
    val idsNuevos: List<String>,
    val idsAusentes: List<String>,
    val bytes: List<ByteCambiado>,
    val bits: List<BitCambiado>,
)
object DiferenciaCan { fun calcular(base: List<TramaCan>, accion: List<TramaCan>): Diferencia }
```

`DiferenciaCan.calcular` (método de captura base contra captura con acción):
- `idsNuevos`: IDs en acción y no en base, ordenados. `idsAusentes`: al revés.
- `bytes`: por cada (id, índice de byte) presente en ambas, si en la base ese byte tuvo **un único valor** y en la acción aparece algún valor distinto → `ByteCambiado` con `valoresNuevos` = valores distintos del de la base, ordenados. Bytes que ya variaban en la base (contadores, checksums) se ignoran.
- `bits`: igual pero por bit, para los bytes que NO entraron en `bytes` (ya variaban en la base): un bit constante en toda la base que en la acción toma el otro valor → `BitCambiado`. Así aparece un indicador empaquetado en el mismo byte que un contador.
- Orden: por (id, byte, bit).

## Transporte

`Transport` gana un método con implementación por defecto (los fakes existentes siguen compilando):

```kotlin
suspend fun monitorear(comando: String, duracionMs: Long, alLinea: (String) -> Unit): String =
    throw UnsupportedOperationException("este transporte no soporta modo monitor")
```

`ClassicBtTransport` y `BleTransport` lo implementan bajo su `ioMutex`: drenan lo viejo, envían `comando` + `\r`, leen trozos y entregan cada línea completa (separadores `\r` o `\n`) a `alLinea` hasta `duracionMs`. Si llega `>` antes (el ELM se detuvo solo, p. ej. con BUFFER FULL), salen antes. Al cumplirse el tiempo envían `\r` para detener el monitor, leen hasta `>` con el timeout corto de AT, entregan las líneas que quedaban y devuelven el texto final (normalmente `STOPPED`).

## Servicio y repositorio

```kotlin
data class OpcionesEscucha(val etiqueta: String, val duracionMs: Long, val filtro: String?)

class EscuchaCan @Inject constructor(private val reloj: Reloj /* o () -> Long */) {
    suspend fun capturar(bt: Transport, opciones: OpcionesEscucha): CapturaCan
}
```

Secuencia de `capturar`: `AT DPN` → protocolo (quitar `A` inicial); si no es 6-9 → `IllegalStateException("el protocolo X no es CAN…")`. Luego `AT H1`, `AT S1`, `AT CAF0`, `AT CSM1` (`OK` → silencioso; `?` → no), `AT CRA <filtro>` si hay filtro, y `monitorear("AT MA", duracionMs)`. Cada línea: desborde, error o trama (`tMs` = ahora − inicio). En `finally`: `AT CAF1`, `AT AR`, `AT S0`, `AT H0`, aunque algo falle. `id` de la captura: `<inicioEpochMs>-<etiqueta normalizada a [a-z0-9_], máx. 32>`.

`CapturasCanRepositorio` (`@Singleton`, constructor `internal` con `File` para tests, como `RegistroEscrituras`): `guardar(c)`, `listar(): List<CapturaCan>` sin tramas (metadatos, la más nueva primero), `leer(id): CapturaCan?`, tope de 200.

`ObdSessionManager.escucharCan(opciones): Result<CapturaCan>`: falla si `estadoViaje.value is EstadoViaje.Grabando`; si no, `withDiagnosticLease("escucha_can", opciones.duracionMs + 15_000) { escucha.capturar(it, opciones) }` y guarda en el repositorio.

## Herramientas MCP (permiso LECTURA)

| Tool | Entrada | Salida |
|---|---|---|
| `escuchar_can` | `etiqueta` (texto, requerido), `segundos` (1-60, defecto 10), `filtro` (opcional, patrón de AT CRA: `^[0-9A-Fa-fXx]{3}$` u `^[0-9A-Fa-fXx]{8}$`) | metadatos + `resumen` (`ResumenCaptura`, máx. 100 IDs) |
| `listar_capturas_can` | — | metadatos de todas, la más nueva primero |
| `leer_captura_can` | `id`, `desde` (defecto 0), `limite` (1-1000, defecto 500) | metadatos + `lineas` candump de esa página + `total` |
| `diferencia_capturas_can` | `base`, `accion` (ids) | `Diferencia` en JSON, `bytes` y `bits` con tope de 200 cada uno y `truncado` |

Errores con el mismo estilo de `ComandoLecturaTool` (`{"error": …}`, `{"conectado": false, …}`). Registro en `McpModule`.

## Pruebas

JVM, en `core/obd/src/test/kotlin/com/revscope/core/obd/escucha/` y `…/mcp/`. Casos mínimos: parseo 11/29 bits con y sin espacios, basura y líneas de estado; candump ida y vuelta y con microsegundos; resumen con Hz y bytes que cambian; diferencia con contador en la base (ignorado), byte fijo que cambia, bit de indicador junto a un contador, ID nuevo y ausente; `capturar` con un transporte falso (protocolo no CAN, CSM `?`, restauración en `finally` aunque el monitor lance); tools con argumentos inválidos, viaje grabando y caso feliz.
