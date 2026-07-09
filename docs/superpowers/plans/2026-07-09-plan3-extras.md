# Plan 3: Backup/restore, costo del viaje en COP, eco-score y mantenimiento por km

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development.

**Goal:** (1) Copia de seguridad completa exportable/importable (lección del incidente de datos del 2026-07-08). (2) Cada viaje muestra su costo estimado en COP. (3) Eco-score 0-100 por viaje guardado con desglose. (4) Mantenimiento por kilometraje (aceite, llantas, batería, kit de arrastre) integrado a "Vehículo al día" — completa las cards de R5.

## Global Constraints
- Gradle: `C:\Users\santi\.gradle\wrapper\dists\gradle-8.11.1-bin\bpt9gzteqjrbo1mjrsomdt32c\gradle-8.11.1\bin\gradle.bat` (=$GRADLE) desde c:/personal/OBD2.
- Commits español, NUNCA Co-Authored-By. UI español. CancellationException se relanza.
- **Room v12→v13 con MIGRACIÓN REAL aditiva** verificada vs 13.json (patrón establecido). Una sola migración con TODO el schema nuevo del plan.
- Lógica de cálculo = funciones puras testeables en core (patrón DiagnosticRules/PicoYPlacaEngine).

## Schema v13 (Task 2 lo implementa completo)
- `vehicle_profiles` + `odometerBaseKm REAL NOT NULL DEFAULT 0`.
- `sessions` + `fuelLiters REAL NULL`, `fuelCostCop REAL NULL`, `ecoScore INTEGER NULL`.
- Tabla nueva `maintenance_items` (id PK autogen, vehicleProfileId INTEGER NOT NULL, nombre TEXT NOT NULL, intervaloKm REAL NOT NULL, ultimoServicioKm REAL NOT NULL).

---

### Task 1: Backup y restauración (sin cambios de schema)

**Files:**
- Create: `core/data/src/main/kotlin/com/revscope/core/data/backup/BackupManager.kt` — @Singleton, inyecta @ApplicationContext + AppDatabase + DataStore:
  - `suspend fun export(target: OutputStream)`: 1) `db.query("PRAGMA wal_checkpoint(TRUNCATE)")` (vía openHelper.writableDatabase — checkpoint para que revscope.db contenga todo); 2) ZipOutputStream: entrada `revscope.db` (copiar el archivo `context.getDatabasePath("revscope.db")`), entrada `preferences.json` (dump de TODAS las prefs DataStore como JSON `{key: {type, value}}` — tipos: boolean/int/long/float/double/string). NO exportar la API key cifrada (EncryptedSharedPreferences no es portable) — documentado.
  - `suspend fun import(source: InputStream): Result<Unit>`: descomprimir a archivos temporales; validar que el zip trae `revscope.db` y que `PRAGMA user_version` del db ≤ versión actual (abrir con SQLiteDatabase.openDatabase readonly); cerrar `AppDatabase` (`db.close()`), borrar revscope.db/-wal/-shm y copiar el nuevo; restaurar prefs al DataStore; devolver éxito. El caller reinicia el proceso.
- Modify: `feature/settings/.../SettingsScreen.kt` + `SettingsViewModel.kt` — sección "Copia de seguridad" (después de la sección de radares): texto explicativo ("Incluye viajes, perfiles, informes y ajustes. La API key no se incluye."), botón "Exportar copia" → SAF `CreateDocument("application/zip")` con nombre `revscope-backup-AAAA-MM-DD.zip` → vm.export(uri) (contentResolver.openOutputStream, Dispatchers.IO) → snackbar éxito/error; botón "Importar copia" → `OpenDocument(zip)` → AlertDialog de confirmación "Reemplaza TODOS los datos actuales" → vm.import(uri) → al éxito: dialog "Copia restaurada — la app se reiniciará" → reinicio de proceso: `context.packageManager.getLaunchIntentForPackage` + `Runtime.getRuntime().exit(0)` tras programar el intent con AlarmManager +100ms, o el patrón ProcessPhoenix manual equivalente — implementar helper `restartApp(context)` simple.
- El BackupManager necesita acceso al archivo DataStore para restaurar: restaurar escribiendo TODAS las claves del JSON con `settings.edit` (mapear tipos con las Preferences.Key correctas por tipo) — más simple y seguro que copiar el archivo binario.

**Steps:** leer DataModule/SettingsScreen → implementar → `$GRADLE :app:compileDebugKotlin` → commit `feat: copia de seguridad completa exportable e importable con reinicio seguro`.

---

### Task 2: Room v13 + FuelCostCalculator + EcoScoreCalculator (TDD) + agregados de sesión

**Files:**
- Modify: entidades + `Migrations.kt` (`MIGRATION_12_13` con los 3 ALTER + CREATE TABLE del schema v13 arriba), `AppDatabase` v13 + `MaintenanceDao` (insert, update, `@Query("SELECT * FROM maintenance_items WHERE vehicleProfileId = :profileId")` observeForProfile Flow + suspend listForProfile), DataModule provider + addMigrations. Verificar 13.json vs SQL byte a byte.
- Create: `core/obd/src/main/kotlin/com/revscope/core/obd/trip/FuelCostCalculator.kt` (puro):
```kotlin
object FuelCostCalculator {
    data class FuelResult(val liters: Double, val costCop: Double, val estimado: Boolean)
    // points: pares (timestampMs, litrosPorHora) del PID 5E ordenados por tiempo → integración trapezoidal
    fun fromFuelRate(points: List<Pair<Long, Double>>, precioGalonCop: Double): FuelResult?
    // fallback: pares (timestampMs, gramosAirePorSegundo) del MAF (PID 10): combustible g/s = maf/14.7; densidad 750 g/L
    fun fromMaf(points: List<Pair<Long, Double>>, precioGalonCop: Double): FuelResult?
    const val LITERS_PER_GALLON = 3.78541
}
```
  Tests: tasa constante 1.8 L/h por 30 min = 0.9 L; precio 16000/galón → costo = 0.9/3.78541*16000; lista vacía/1 punto → null; MAF constante conocido.
- Create: `core/obd/src/main/kotlin/com/revscope/core/obd/trip/EcoScoreCalculator.kt` (puro):
```kotlin
object EcoScoreCalculator {
    data class Desglose(val score: Int, val aceleradasBruscas: Int, val frenadasBruscas: Int, val tiempoAltasRpmSeg: Int, val bonusCrucero: Int)
    // accelLongitudinal: m/s2 muestreados (~50Hz ya filtrados a lo que haya en BD); rpmPoints: (timestampMs, rpm); redline del perfil
    fun calculate(accelLongitudinal: List<Double>, rpmPoints: List<Pair<Long, Double>>, redlineRpm: Int): Desglose
}
```
  Reglas del spec: acelerada brusca > 3 m/s² → −2 c/u; frenada < −4 m/s² → −3 c/u; cada 30 s con rpm > 80% redline → −1; bonus crucero hasta +10 (fracción del tiempo con rpm entre 20-60% redline y estable ±10%); clamp 0..100 desde base 100. Eventos = cruces de umbral (no muestras individuales: contar transiciones de bajo→sobre umbral). Tests: viaje suave = 100 con bonus; 4 frenadas bruscas = −12; rpm alto sostenido resta; listas vacías → score 100 sin bonus.
- Modify: `ObdSessionManager.updateSessionEnd` — tras calcular distancia: leer precio galón de DataStore (key nueva `FUEL_PRICE_COP_PER_GALLON`, default 16000.0), puntos 5E de telemetría (`telemetryDao.pointsForSessionAndPid(id, "5E")` — verificar tipo devuelto: (timestamp, value)); si vacío → MAF "10". Calcular fuel + leer IMU longitudinal (`imuDao` — revisar entidad ImuPointEntity para el eje correcto: usar el campo de aceleración longitudinal existente) + rpm "0C" → ecoScore con redline del perfil activo (o 10500 default). Guardar en session.copy(fuelLiters, fuelCostCop, ecoScore). Todo en runCatching — el cierre de sesión NUNCA falla por esto.
- Modify: `TripSummaryNotifier.summaryText` — agregar `· $X.XXX` (formato es-CO sin decimales) si fuelCostCop != null y `· Eco NN` si ecoScore != null. Ajustar tests existentes si cambia el formato (mantener las 3 partes originales primero).
- Modify: `feature/settings` — campo "Precio galón corriente (COP)" en una sección "Combustible" (OutlinedTextField numérico + guardar → DataStore).
- Modify: `feature/session/.../SessionDetailScreen.kt` + VM — en las stats del reporte: fila costo estimado ("$4.200 estimado" con nota si estimado por MAF... el flag estimado no se persiste: mostrar solo el valor) y card Eco con score + desglose (recalcular desglose al abrir el reporte con EcoScoreCalculator sobre los datos ya cargados del detalle — el VM ya carga IMU/telemetry para las gráficas; verificar y reusar).

**Steps:** TDD calculators → schema v13 + verificación → wiring manager/notifier/UI → `$GRADLE :core:obd:testDebugUnitTest :app:compileDebugKotlin` → commit `feat: costo del viaje en COP y eco-score por viaje con desglose (Room v13)`.

---

### Task 3: Mantenimiento por kilometraje integrado a Vehículo al día

**Files:**
- Create: `core/obd/src/main/kotlin/com/revscope/core/obd/trip/MaintenanceCalculator.kt` (puro): dado odómetro actual (baseKm + suma distanceKm de sesiones del perfil) e items → por item `kmRestantes = (ultimoServicioKm + intervaloKm) - odometroActual`; nivel VENCIDO si ≤0, ATENCION si ≤ 10% del intervalo, OK; próximo = mínimo kmRestantes. Tests básicos.
- Create: `feature/workshop/src/main/kotlin/com/revscope/feature/workshop/MaintenanceScreen.kt` + `MaintenanceViewModel.kt`:
  - VM: perfil activo + `sessionDao` (suma de distanceKm por perfil — agregar `@Query("SELECT COALESCE(SUM(distanceKm),0) FROM sessions WHERE vehicleProfileId = :profileId")` si no existe) + MaintenanceDao. Odómetro editable (campo "Kilometraje actual del vehículo" → recalcula odometerBaseKm = valorIngresado − sumaSesiones y persiste en el perfil). Crear defaults la primera vez que se abre para un perfil sin items: Aceite 3000 km, Llantas (revisión) 10000, Batería (revisión) 20000, y si type==MOTORCYCLE: Kit de arrastre 15000, Refrigerante 20000. `registrarServicio(item)` → ultimoServicioKm = odómetro actual.
  - Screen: odómetro arriba (editable), lista de items con barra de progreso del intervalo, km restantes con color por nivel, botón "Registrar servicio" por item, edición de intervalo (dialog), agregar item custom.
- Modify: `AlDiaScreen.kt` + `AlDiaViewModel.kt` — card nueva "Mantenimiento" en el grid (nivel = peor item; detalle "Aceite en 420 km" o "Por configurar" si sin odómetro/items) → tap navega a MaintenanceScreen. VM inyecta MaintenanceDao + sessionDao para el cálculo (reusar MaintenanceCalculator).
- Modify: `Screen.kt` (+ Maintenance("maintenance")), `RevScopeNavGraph.kt` (ruta + lambda desde AlDia), WorkshopScreen sección Vehículo gana card "Mantenimiento" también (mismo destino).

**Steps:** TDD calculator → VM/Screen → integración AlDia/Taller → `$GRADLE :core:obd:testDebugUnitTest :app:compileDebugKotlin` → commit `feat: mantenimiento por kilometraje con odómetro por vehículo integrado a vehículo al día`.

---

### Task 4 (inline): build + tests + install + verificar migración v13 (sesiones intactas) + push
