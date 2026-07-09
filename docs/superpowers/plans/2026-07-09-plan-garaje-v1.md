# Plan Garaje v1: selección de vehículo al inicio, historial y personalización por vehículo

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development. Checkboxes por paso.

**Goal:** El usuario alterna entre motos y carros: la app pregunta al inicio qué vehículo va a usar (con "no volver a preguntar"), todo se scopea al vehículo activo (historial, gauges, redline), los viajes quedan ligados al vehículo, y al conectar un adaptador conocido el vehículo se activa solo.

**Referencias investigadas:** Car Scanner ELM (My cars + connection profiles, cambio manual), Torque Pro (logs/ajustes por perfil), Drivvo/Fuelio (garaje con switcher que scopea todo). Mejora sobre ellas: picker al inicio + auto-match por adaptador.

## Global Constraints

- Gradle: `C:\Users\santi\.gradle\wrapper\dists\gradle-8.11.1-bin\bpt9gzteqjrbo1mjrsomdt32c\gradle-8.11.1\bin\gradle.bat` (=$GRADLE) desde c:/personal/OBD2.
- Commits español, NUNCA Co-Authored-By. UI español. CancellationException se relanza siempre.
- **Room v10→v11 con MIGRACIÓN REAL** (ALTER TABLE, aditiva). Verificar SQL de migración vs schema exportado `11.json` byte a byte (patrón de la 9→10). Los datos del usuario son reales — el 2026-07-08 un salto sin migración los borró.
- El chip de conexión flotante y los insets actuales NO se tocan.

## Escalabilidad documentada (v2, NO implementar ahora)
Custom PIDs por vehículo, umbrales de alerta por vehículo, dashboards por vehículo, precio de combustible/calibración de consumo por vehículo (lo usará el plan de extras), foto/color del vehículo.

---

### Task 1: Room v11 + vínculo sesión-vehículo + auto-activación por adaptador

**Files:**
- Modify: `core/data/src/main/kotlin/com/revscope/core/data/db/entities/VehicleProfileEntity.kt` — agregar `val vehicleType: String = "CAR"` (valores "CAR" | "MOTORCYCLE") y `val adapterAddress: String? = null`.
- Modify: `core/data/src/main/kotlin/com/revscope/core/data/db/Migrations.kt` — agregar:
```kotlin
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `vehicle_profiles` ADD COLUMN `vehicleType` TEXT NOT NULL DEFAULT 'CAR'")
        db.execSQL("ALTER TABLE `vehicle_profiles` ADD COLUMN `adapterAddress` TEXT")
    }
}
```
- Modify: `AppDatabase.kt` version = 11; `DataModule.kt` `.addMigrations(MIGRATION_9_10, MIGRATION_10_11)`.
- Modify: `core/data/.../dao/VehicleProfileDao.kt` — agregar si no existen: `@Query("SELECT * FROM vehicle_profiles WHERE adapterAddress = :address LIMIT 1") suspend fun getByAdapter(address: String): VehicleProfileEntity?` y un `@Update suspend fun update(profile)` (revisar métodos existentes primero).
- Modify: `core/obd/.../session/ObdSessionManager.kt`:
  1. `createSession`: `vehicleProfileId = _activeProfile.value?.id ?: 0L` (hoy está clavado en 0L — bug).
  2. En `startTelemetry`, tras `resolveProfileByVin(bt)`: si `_activeProfile.value == null` o el VIN no resolvió, intentar `profileDao.getByAdapter(currentDeviceAddress)` → `setActiveProfile(match)` (runCatching, log Timber).
  3. En `setActiveProfile`: si hay conexión activa (`currentDeviceAddress != null` y estado Connected) y `profile != null` y `profile.adapterAddress != currentDeviceAddress` → persistir `profileDao.update(profile.copy(adapterAddress = currentDeviceAddress))` (runCatching; el flujo `_activeProfile` guarda la copia actualizada).
- Modify: `feature/vehicle/.../VehicleProfileScreen.kt` (leer primero): en el formulario de crear/editar perfil, selector de tipo (dos FilterChips: 🚗 Carro / 🏍 Moto) que setea `vehicleType`. Mostrar el emoji del tipo en la lista de perfiles.

**Steps:** leer archivos → editar → `$GRADLE :core:data:compileDebugKotlin :app:compileDebugKotlin` → verificar `core/data/schemas/.../11.json` createSql de vehicle_profiles coincide con el resultado de la migración (columnas nuevas al final con DEFAULT correcto) → `$GRADLE :core:obd:testDebugUnitTest` (172+ verdes) → commit `feat: perfiles con tipo de vehículo y adaptador asociado — viajes ligados al vehículo activo (Room v11)`.

---

### Task 2: Selector de vehículo al inicio + sección en Ajustes

**Files:**
- Modify: `core/data/.../datastore/PreferencesKeys.kt` — agregar `ASK_VEHICLE_ON_START` (boolean).
- Create: `app/src/main/kotlin/com/revscope/app/navigation/VehiclePickerSheet.kt` — ModalBottomSheet composable:
  - Título "¿Qué vehículo vas a usar?".
  - Lista de perfiles (emoji por tipo + nombre + check en el activo) → tap: activa (callback) y cierra.
  - Fila con Checkbox "No volver a preguntar al inicio" → persiste `ASK_VEHICLE_ON_START = false` al cerrar con selección.
  - TextButton "Administrar vehículos" → navega a Screen.VehicleProfile y cierra.
- Modify: `RevScopeNavGraph.kt`: estado `showVehiclePicker` inicializado con LaunchedEffect(Unit): leer DataStore `ASK_VEHICLE_ON_START` (default true) y perfiles (vía un ViewModel pequeño `VehiclePickerViewModel` @HiltViewModel en app module con profileDao + settings + sessionManager); mostrar sheet solo si askOnStart && hay ≥1 perfil. Selección → `sessionManager.setActiveProfile(p)`. Mostrar una sola vez por proceso (remember flag).
- Modify: `feature/settings/.../SettingsScreen.kt` + `SettingsViewModel.kt`: sección nueva "Vehículo" arriba de "Herramientas": fila "Vehículo activo: <emoji nombre | Ninguno>" (tap → onNavigateToVehicleProfiles) + Switch "Preguntar al inicio" (lee/escribe ASK_VEHICLE_ON_START; el VM ya tiene DataStore inyectado; para el nombre del perfil activo inyectar ObdSessionManager en SettingsViewModel y exponer `activeProfile`).

**Steps:** leer archivos → implementar → `$GRADLE :app:compileDebugKotlin` → commit `feat: selector de vehículo al inicio con opción de no volver a preguntar y vehículo activo en ajustes`.

---

### Task 3: Historial filtrado por vehículo + reasignar viaje

**Files:**
- Modify: `feature/session/.../SessionViewModel.kt` (leer primero): exponer perfiles (`profileDao` — agregar dep si falta), filtro seleccionado (`StateFlow<Long?>` null=Todos, -1=Sin vehículo, id=perfil) con default = perfil activo si existe; `sessions` filtradas en combine.
- Modify: `feature/session/.../SessionHistoryScreen.kt`: fila de FilterChips horizontal scrolleable bajo el TopAppBar: "Todos", cada perfil (emoji+nombre), "Sin vehículo". Chip seleccionado = filtro del VM.
- Modify: `feature/session/.../SessionDetailViewModel.kt` + `SessionDetailScreen.kt`: acción "Asignar vehículo" en el topBar (icono DirectionsCar) → dialog con lista de perfiles → `sessionDao.update(session.copy(vehicleProfileId = elegido))` (agregar método al VM; verificar SessionDao tiene @Update — existe, lo usa el manager). Mostrar el vehículo asignado en el encabezado del reporte.

**Steps:** leer archivos → implementar → `$GRADLE :app:compileDebugKotlin` → commit `feat: historial filtrado por vehículo y reasignación de viajes`.

---

### Task 4 (inline, controller): build + tests + install + verificación migración v11 + push

Igual que el patrón v10: instalar, abrir app (forzar apertura de BD), pull de la BD y verificar `user_version=11`, columnas nuevas presentes y `sessions` intactas (12+). Push con `$env:GITHUB_TOKEN = $null`.
