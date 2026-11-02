# Captura CAN de ruta (desde la app, con GPS)

## Por qué

El modo escucha CAN (v1.23.0) solo se lanza por MCP (`escuchar_can`, 1-60 s), que exige que el
celular esté en la misma Wi-Fi que el agente. Para mapear la velocidad (sensor en la rueda
delantera) y otras señales que solo aparecen rodando, hace falta capturar el bus durante una ruta
normal, sin Wi-Fi, con la velocidad GPS al lado para correlacionar después en el PC.

## Alcance

1. **Parada anticipada del monitor** (`core/obd/connection`)
   - `Transport.monitorear(comando, duracionMs, alLinea)` gana un parámetro
     `detener: () -> Boolean = { false }`. `ClassicBtTransport` y `BleTransport` lo consultan en
     cada vuelta del bucle de lectura; si devuelve `true` salen igual que al vencer el plazo
     (envían `\r`, leen el resto y lo entregan). Comportamiento sin `detener`: idéntico al actual.
   - `OpcionesEscucha` gana `detener: () -> Boolean = { false }`; `EscuchaCan` lo pasa a cada
     `monitorear` y no relanza tras un desborde si `detener()` es `true`. Una captura detenida
     guarda `duracionMs` = tiempo real transcurrido (no el pedido).

2. **GPS durante la captura** (`core/obd/escucha`)
   - Clase nueva `MuestreadorGpsCaptura` (inyectable): `iniciar(inicioEpochMs)` pide
     `LocationManager.GPS_PROVIDER` a 1 Hz (mismo patrón que `GpsTrackRecorder`, sin tocarlo) y
     acumula `MuestraGps(tMs: Long /* relativo al inicio */, velocidadKmh: Float?, precisionM: Float?, rumboGrados: Float?)`;
     `detener(): List<MuestraGps>`. Sin permiso `ACCESS_FINE_LOCATION`: devuelve lista vacía y la
     captura sigue (el resultado lo indica).
   - **No guardar latitud ni longitud** (privacidad; solo hace falta la velocidad).

3. **Persistencia** (`CapturasCanRepositorio`)
   - `guardar(c, gps: List<MuestraGps> = emptyList())`: si hay muestras escribe
     `<id>.gps.csv` con cabecera `t_ms,velocidad_kmh,precision_m,rumbo_grados` (vacío = sin dato).
   - El JSON de metadatos gana `origen` (`"mcp"` | `"app"`) y `muestrasGps` (entero). Lectura de
     JSON viejos sin esos campos: `origen="mcp"`, `muestrasGps=0`.
   - `leerGps(id): List<MuestraGps>`; `podar()` borra también el `.gps.csv`.

4. **Controlador de la captura de ruta** (`core/obd/escucha`)
   - Singleton `CapturaRutaCan` con su propio `CoroutineScope(SupervisorJob() + Dispatchers.Default)`
     (no el de un ViewModel: la captura sigue con la pantalla apagada mientras viva
     `ObdForegroundService`, que ya es foreground `connectedDevice|location`).
   - `estado: StateFlow<EstadoCapturaRuta>`: `Inactiva` | `Capturando(etiqueta, inicioEpochMs, tramas, desbordes, velocidadGpsKmh?)` |
     `Terminada(id, tramas, muestrasGps, duracionMs)` | `Fallo(mensaje)`.
   - `iniciar(etiqueta)`: rechaza (→ `Fallo`) si no hay conexión, si hay un viaje grabando (mismo
     mensaje que `ServicioEscuchaCan`) o si ya hay una captura. Duración máxima 15 min
     (`MAX_RUTA_MS`); al llegar se detiene sola. Reusa `ServicioEscuchaCan`/`EscuchaCan` con
     `detener` y el lease de diagnóstico existente; guarda con `origen="app"` y el GPS.
   - El contador `tramas` del estado se actualiza al menos 1 vez por segundo (no por trama).
   - `detener()`: termina y guarda lo capturado.
   - ponytail: las tramas viven en memoria hasta el final (15 min ≈ 300 k tramas); si la app muere
     a mitad se pierde la captura. Escritura incremental cuando haga falta.

5. **Pantalla** (`feature/workshop`)
   - Entrada nueva en el hub del Taller (`HerramientasTaller.mas`, grupo de diagnóstico avanzado o
     el que tenga más sentido) "Captura CAN de ruta", habilitada solo con conexión; ruta de
     navegación nueva en `Screen`/`RevScopeNavGraph`.
   - Pantalla: campo etiqueta (por defecto `ruta`), botón Iniciar/Detener grande (≥ 48 dp),
     cronómetro, tramas, desbordes, velocidad GPS actual, aviso "no se graba un viaje mientras
     capturas" y, al terminar, el id de la captura. Sigue el estilo de las pantallas del Taller.

6. **MCP**
   - Tool nueva `leer_gps_captura_can` (`id` requerido, `desde`/`limite` como `leer_captura_can`):
     devuelve `{id, total, desde, muestras:[{tMs, velocidadKmh, precisionM, rumboGrados}]}`;
     error claro si la captura no existe o no tiene GPS. Registrarla en `McpModule`.
   - `listar_capturas_can` y `leer_captura_can` exponen `origen` y `muestrasGps`.
   - Documentar en `docs/configuracion.md` (tabla de tools) y en el manual de usuario.

## Tests (JVM, sin dispositivo)

- `EscuchaCan` con transporte falso: `detener` a mitad corta la captura, guarda lo recibido y
  `duracionMs` real; sin `detener` todo igual que antes (los tests existentes siguen verdes).
- `CapturasCanRepositorio`: ida y vuelta del `.gps.csv` (con campos nulos), JSON viejo sin
  `origen`/`muestrasGps`, poda borra el `.gps.csv`.
- `CapturaRutaCan`: rechaza con viaje grabando, sin conexión y doble inicio; se detiene sola al
  tope (reloj inyectable); `detener()` deja `Terminada` con el id guardado.
- `LeerGpsCapturaCanTool`: paginación, captura inexistente, captura sin GPS.

## Fuera de alcance

PIDs simultáneos (el ELM327 en `AT MA` no puede sondear), escritura incremental, mapa de la ruta,
exportar/compartir el archivo.
