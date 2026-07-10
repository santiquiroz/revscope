# Plan 5: Exportación individual de métricas + pico y placa multi-ciudad con detección GPS

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development. Ejecutar EN ORDEN.

**Goal:** (1) Todo lo que la app mide y muestra es exportable individualmente (CSV, para casos de estudio). (2) Pico y placa multi-ciudad: dropdown de ciudades en el perfil, motor con esquemas por rotación semanal Y par/impar calendario (Bogotá), y detección por GPS: al entrar a una ciudad con restricción vigente para tu placa, aviso por voz.

**Investigación (2026-07-10):** Bogotá: L-V 6:00-21:00, días IMPARES circulan placas terminadas 1-5 (restringidas 6,7,8,9,0), días PARES circulan 6-0 (restringidas 1,2,3,4,5), MOTOS EXENTAS. Medellín S1 vigente hasta 31 jul (ya implementado); S2 sin publicar. Cali: 6:00-19:00 L-V, rotación semestral con dígitos no confirmados → ciudad presente pero con rotación editable/por confirmar.

## Global Constraints
- Gradle: `C:\Users\santi\.gradle\wrapper\dists\gradle-8.11.1-bin\bpt9gzteqjrbo1mjrsomdt32c\gradle-8.11.1\bin\gradle.bat` (=$GRADLE) desde c:/personal/OBD2.
- Commits español, NUNCA Co-Authored-By. UI español. CancellationException se relanza. SIN cambios de schema Room (picoPlacaCity ya es String — los ids nuevos caben).
- Motor de pico y placa: TODO con TDD (es lógica legal). Tests actuales: 299.
- Exports: patrón compartido único (helper) + FileProvider cache/exports existente; CSV con encabezado y unidades.

---

### Task 1: Motor multi-ciudad (TDD) + dropdown en el perfil

1. `core/obd/.../legal/PicoYPlacaEngine.kt` (leer + extender, NO romper API existente — hay consumidores):
   - `enum class Scheme { WEEKDAY_ROTATION, DATE_PARITY }` en CityRules (default WEEKDAY_ROTATION para compat con MEDELLIN_2026_S1 y parseRulesJson existente).
   - Para DATE_PARITY: `dateParityRestricted: Map<String, List<Int>>` con claves "ODD_DAY"/"EVEN_DAY" (día del mes impar/par en zona horaria dada) — restringidos ese día.
   - `motosExentas: Boolean = false` — si true e isMotorcycle → SIN_RESTRICCION siempre.
   - `check(...)`: rama por scheme; DATE_PARITY usa el ÚLTIMO dígito SIEMPRE (Bogotá) y el día del mes local.
   - Registry: `object CityRegistry { data class City(val id: String, val nombre: String, val lat: Double, val lon: Double, val radiusKm: Double, val rules: CityRules?) ; val CITIES: List<City> }` con: medellin (6.2442,-75.5812, r 18.0, MEDELLIN_2026_S1), bogota (4.7110,-74.0721, r 22.0, BOGOTA_2026: DATE_PARITY, 6-21h, motosExentas=true, vigencia amplia hasta 2026-12-31), cali (3.4516,-76.5320, r 14.0, rules = null → "rotación por confirmar": el estado para cali sin override JSON = SIN_DATOS con detalle "Configura la rotación vigente de Cali en Ajustes (JSON)"). `fun nearest(lat, lon): City?` (haversine, dentro de radiusKm).
   - parseRulesJson: soportar los campos nuevos (scheme, dateParityRestricted, motosExentas) con defaults compat.
   - TDD: Bogotá día impar placa termina 7 → RESTRINGIDO en horario / fuera de horario; día par placa 7 → SIN_RESTRICCION; moto en Bogotá → SIN_RESTRICCION siempre; nearest() dentro/fuera de radio; Cali sin rules → null rules manejado por el consumidor.
2. `DocumentStatusCalculator` (leer): resolver las rules por cityId vía CityRegistry (hoy asume medellin/MEDELLIN_2026_S1 + override JSON — generalizar: override JSON aplica solo si su cityId coincide; Cali sin rules → SIN_CONFIGURAR con el detalle de arriba).
3. Perfil (`feature/vehicle/.../VehicleProfileScreen.kt`): reemplazar los chips Medellín/Ninguna por dropdown (ExposedDropdownMenuBox) con "Ninguna" + CityRegistry.CITIES (nombres). Guarda el id.
4. AlDia card de pico y placa: ya consume el calculator — verificar que muestra bien Bogotá (moto exenta → "Motos exentas en Bogotá" detalle OK) y Cali.

Verificar: `$GRADLE :core:obd:testDebugUnitTest :app:compileDebugKotlin` (299+nuevos). Commit: `feat: pico y placa multi-ciudad — Bogotá par-impar con motos exentas, registro de ciudades y dropdown en el perfil`.

---

### Task 2: Detección GPS de ciudad con restricción + aviso por voz

1. `core/obd/.../legal/CityEnforcementAlerter.kt` (@Singleton, patrón de SpeedCameraAlerter — leerlo): `onGpsFix(lat, lon)` throttled (evalúa máx 1 vez/60s): `CityRegistry.nearest(lat,lon)` → si ciudad con rules (o override JSON del usuario para esa ciudad) y hay perfil activo con placa → `PicoYPlacaEngine.check(...)` → si RESTRINGIDO_AHORA o RESTRINGIDO_HOY_FUERA_DE_HORARIO y no anunciada esa ciudad hoy (cooldown por ciudad+día, en memoria) → `alertsEngine.announcePicoPlaca(ciudad, status, endHour)`: "Atención: entraste a <ciudad> y hoy hay pico y placa para tu placa hasta las <h>" (o "...aplica de <inicio> a <fin>" si fuera de horario).
2. `AlertsEngine`: método `announcePicoPlaca(...)` con categoría de voz NUEVA `VOICE_PICO_PLACA` default **true** (es aviso legal puntual, 1 vez/ciudad/día) — agregar el switch al menú de categorías en Ajustes.
3. Wiring: `GpsTrackRecorder` gana `cityAlerter: CityEnforcementAlerter? = null` (mismo patrón que cameraAlerter) llamado en onLocation; `ObdForegroundService` lo inyecta y pasa. El alerter necesita el perfil activo → inyectar ObdSessionManager (leer activeProfile.value; cuidado ciclo DI: SpeedCameraAlerter cómo obtiene sus deps — seguir su patrón; si hay ciclo manager↔alerter, pasar un provider lambda desde el service).
4. La ciudad DETECTADA prevalece sobre la del perfil solo para el AVISO (el perfil no cambia). Si la ciudad detectada == la del perfil, no anunciar (ya la ve en Al día/banner) — anunciar solo ciudad DISTINTA a la del perfil (el caso Medellín→Bogotá del usuario).
5. Test puro del throttle/cooldown si la lógica se extrae (extraer `CityAlertPolicy` puro: decide(anuncioPrevio, ciudadDetectada, ciudadPerfil, status, nowMs): Boolean — TDD 4-5 casos).

Verificar: `$GRADLE :core:obd:testDebugUnitTest :app:assembleDebug`. Commit: `feat: aviso por voz al entrar a una ciudad con pico y placa vigente para tu placa`.

---

### Task 3: Exportación individual de métricas (CSV para estudio)

1. Helper compartido `core/common/.../export/CsvShare.kt` (o feature-common si core:common no tiene deps Android — verificar; puede vivir en core:data): `fun shareCsv(context, fileName, header: List<String>, rows: Sequence<List<Any?>>)` → escribe a cache/exports (mismo FileProvider) en Dispatchers.IO → ACTION_SEND chooser "Exportar CSV". Separador coma, decimales con punto, timestamps ISO-8601 y epoch ms (ambas columnas donde aplique).
2. Botones de export (icono Icons.Default.Download o FileDownload, esquina de cada superficie):
   - `SensorGraphScreen`: exporta las muestras visibles del PID seleccionado (timestamp_iso, epoch_ms, pid, nombre, valor, unidad).
   - `O2WaveScreen`: ventana actual (epoch_ms, segundos_relativos, voltios) + fila de metadatos (# cruces/min) como comentario `# cruces_por_minuto=X`.
   - `Mode06Screen`: resultados (mid, mid_nombre, tid, uas_id, valor_escalado, unidad, valor_crudo, min_crudo, max_crudo, resultado).
   - `SessionDetailScreen`: menú "Exportar…" (DropdownMenu en el topBar junto al CSV completo existente — leer cómo exporta hoy) con opciones por métrica: Velocidad, RPM, Temperatura, Ritmo cardíaco, IMU (G long/lat/lean), GPS (lat,lon,vel), Todo (el existente). Cada una filtra del dataset ya cargado en el VM.
   - `HealthCheckScreen`: además del 📷, botón export CSV del informe (area, nivel, titulo, causa, timestamp).
   - `LiveMixtureScreen`: botón "snapshot" que exporta los valores actuales de las filas visibles (pid, nombre, valor, unidad, diagnostico).
   - `MaintenanceScreen`: export de items (nombre, intervalo_km, ultimo_servicio_km, km_restantes, nivel, odometro_actual).
3. Nombres de archivo: `revscope-<tipo>-<AAAAMMDD-HHmm>.csv`.

Verificar: `$GRADLE :app:assembleDebug`. Commit: `feat: exportación CSV individual de cada métrica y superficie de datos`.

---

### Task 4: Tipos de combustible + precio en línea por ciudad (datos.gov.co)

1. **Room v13→v14**: `vehicle_profiles` + `fuelType TEXT NOT NULL DEFAULT 'CORRIENTE'` (valores CORRIENTE|EXTRA|DIESEL). MIGRATION_13_14 real verificada vs 14.json (patrón establecido, @ColumnInfo(defaultValue="CORRIENTE")). Selector en el formulario del perfil (chips o dropdown: Corriente/Extra/Diésel).
2. **Precios por tipo**: DataStore keys `FUEL_PRICE_CORRIENTE/EXTRA/DIESEL` (double); migrar la key vieja FUEL_PRICE_COP_PER_GALLON → CORRIENTE la primera vez. Defaults: corriente 16000, extra 20000, diésel 10500. Ajustes sección Combustible: 3 campos + lo de abajo.
3. **Precio en línea (fuente oficial, sin scraping)**: PROBAR el API Socrata de datos.gov.co: buscar el dataset SICOM/MinEnergía de precios de combustibles por municipio (probe con curl: `https://www.datos.gov.co/resource/<id>.json?$limit=5` — descubrir el id vía `https://www.datos.gov.co/api/catalog/v1?q=precios%20combustibles%20sicom` o similar; el implementador DEBE verificar el dataset real, sus columnas (municipio, producto, precio, periodo) y documentar el id en código). `FuelPriceUpdater` (core/obd o core/data): fetch por municipio (ciudad del perfil o detectada por GPS vía CityRegistry.nearest — mapear id→nombre municipio) y producto → actualizar los 3 precios en DataStore + guardar `FUEL_PRICE_SOURCE_DATE`. Botón en Ajustes "Actualizar precios en línea (<ciudad>)" con estado/fecha de fuente; y refresh mensual silencioso en el worker semanal existente de radares (o worker propio mensual — elegir lo más simple). Si el dataset no resulta utilizable (columnas inservibles/desactualizado >6 meses), documentarlo y dejar solo manual — decir la verdad en el reporte.
4. `SessionAggregator`/FuelCost: usar el precio del fuelType del perfil activo (fallback CORRIENTE).
5. Nota UI: "Precio de referencia oficial (SICOM) — puede variar por estación".

Verificar: `$GRADLE :core:obd:testDebugUnitTest :app:assembleDebug` + verificación de migración v14 en el Task final. Commit: `feat: tipos de combustible por vehículo y precio oficial en línea por ciudad (Room v14)`.

---

### Task 5 (inline): build + tests + install + verificar migración v14 + push (+screenshots si el celular está desbloqueado)
