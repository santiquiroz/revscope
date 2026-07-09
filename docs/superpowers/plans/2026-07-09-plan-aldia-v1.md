# Plan "Vehículo al día" v1: documentos, pico y placa y notificaciones (estilo R5)

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development.

**Goal:** Grid de estado documental del vehículo (SOAT, tecnomecánica, pico y placa, multas SIMIT, todo riesgo, licencia) con semáforos, "Por configurar" con CTA, banner contextual en Conducir solo cuando hay acción requerida, y notificación diaria a las 5:30am. Taller se reorganiza en secciones.

**Investigación:** Sin API pública para RUNT/SOAT ni SIMIT → fechas manuales + botón a fcm.org.co/simit. Pico y placa Medellín 2026-S1 (vigente 2 feb–31 jul): L 1-7, M 0-3, X 4-6, J 5-9, V 2-8; 5:00-20:00; carros = ÚLTIMO dígito, motos = PRIMER dígito; sábados/domingos/festivos libres. Rotación cambia cada semestre → reglas por defecto + JSON editable.

## Global Constraints

- Gradle: `C:\Users\santi\.gradle\wrapper\dists\gradle-8.11.1-bin\bpt9gzteqjrbo1mjrsomdt32c\gradle-8.11.1\bin\gradle.bat` (=$GRADLE) desde c:/personal/OBD2.
- Commits español, NUNCA Co-Authored-By. UI español. CancellationException se relanza.
- **Room v11→v12 con MIGRACIÓN REAL aditiva** verificada vs schema exportado 12.json (patrón 9→10/10→11; datos reales en el dispositivo).
- Semáforos: reutilizar los colores del chequeo (verde 0xFF4CAF50 / ámbar 0xFFFFC107 / rojo 0xFFFF5252). Umbrales: rojo = vencido o pico y placa AHORA; ámbar = vence en ≤30 días o pico y placa hoy fuera de horario; verde = ok; gris = por configurar.
- Paleta y patrones de feature/workshop (colores locales, cards Surface 0xFF12121A, radius 12-14dp).

---

### Task 1: Room v12 + PicoYPlacaEngine (TDD) + campos en el perfil

**Files:**
- Modify: `core/data/.../entities/VehicleProfileEntity.kt` — agregar `val plate: String? = null`, `val picoPlacaCity: String? = null` (null = sin pico y placa; valor = "medellin" por ahora), `val soatExpiresAt: Long? = null`, `val rtmExpiresAt: Long? = null`, `val insuranceExpiresAt: Long? = null` (epoch ms, todo riesgo).
- Modify: `core/data/.../db/Migrations.kt` — `MIGRATION_11_12` con 5 `ALTER TABLE vehicle_profiles ADD COLUMN ...` (plate TEXT, picoPlacaCity TEXT, soatExpiresAt INTEGER, rtmExpiresAt INTEGER, insuranceExpiresAt INTEGER — todos nullable sin default). AppDatabase version 12; DataModule addMigrations(..., MIGRATION_11_12). Verificar 12.json vs SQL.
- Create: `core/obd/src/main/kotlin/com/revscope/core/obd/legal/PicoYPlacaEngine.kt` — objeto puro:
```kotlin
object PicoYPlacaEngine {
    enum class DigitSource { FIRST, LAST }
    data class CityRules(
        val cityId: String,
        val displayName: String,
        val rotation: Map<Int, List<Int>>, // Calendar.DAY_OF_WEEK (2=lunes..6=viernes) → dígitos restringidos
        val startHour: Int, val endHour: Int,
        val carDigit: DigitSource, val motoDigit: DigitSource,
        val validFromMs: Long, val validUntilMs: Long,
    )
    enum class Status { SIN_RESTRICCION, RESTRINGIDO_AHORA, RESTRINGIDO_HOY_FUERA_DE_HORARIO, REGLAS_VENCIDAS, SIN_DATOS }
    data class Result(val status: Status, val endHour: Int?, val digitos: List<Int>)

    val MEDELLIN_2026_S1 = CityRules(
        cityId = "medellin", displayName = "Medellín",
        rotation = mapOf(2 to listOf(1, 7), 3 to listOf(0, 3), 4 to listOf(4, 6), 5 to listOf(5, 9), 6 to listOf(2, 8)),
        startHour = 5, endHour = 20,
        carDigit = DigitSource.LAST, motoDigit = DigitSource.FIRST,
        validFromMs = 1770000000000, // 2026-02-02 aprox — usar epoch exacto en la implementación
        validUntilMs = 1785542399000, // 2026-07-31 23:59:59 GMT-5 — calcular exacto
    )

    fun check(plate: String, isMotorcycle: Boolean, rules: CityRules, nowMs: Long, timeZoneId: String = "America/Bogota"): Result
    fun parseRulesJson(json: String): CityRules? // mismo espíritu que los custom PIDs
}
```
  `check`: extrae el primer dígito numérico (FIRST) o el último (LAST) de la placa (placas moto tipo "NZO28H": dígitos "28" → FIRST=2, LAST=8; carro "ABC123" → LAST=3); si nowMs fuera de vigencia → REGLAS_VENCIDAS; sábado/domingo → SIN_RESTRICCION (festivos: fuera de alcance v1, documentar); si el dígito está en la rotación del día: dentro de horario → RESTRINGIDO_AHORA, fuera → RESTRINGIDO_HOY_FUERA_DE_HORARIO.
- Test: `core/obd/src/test/.../legal/PicoYPlacaEngineTest.kt` — TDD: moto NZO28H lunes 10am (primer dígito 2 → lunes es 1,7 → SIN_RESTRICCION), viernes 10am (2 está en viernes → RESTRINGIDO_AHORA), viernes 21:30 (→ RESTRINGIDO_HOY_FUERA_DE_HORARIO), sábado (SIN_RESTRICCION), fecha 2026-09-01 (REGLAS_VENCIDAS), carro "ABC123" martes (LAST=3 → RESTRINGIDO), placa sin dígitos (SIN_DATOS). Usar timestamps fijos calculados para America/Bogota.
- Modify: `feature/vehicle/.../VehicleProfileScreen.kt` + su ViewModel — sección nueva "Documentos y normativa" en el form: campo Placa (texto, uppercase), selector ciudad pico y placa (chips: "Medellín" / "Ninguna"), tres campos de fecha (SOAT vence / Tecnomecánica vence / Todo riesgo vence) con DatePickerDialog de material3 (mostrar dd/MM/yyyy, guardar epoch ms, botón limpiar).

**Steps:** leer archivos → TDD engine → campos UI → `$GRADLE :core:obd:testDebugUnitTest :app:compileDebugKotlin` → verificar 12.json → commit `feat: motor de pico y placa y documentos del vehículo con vencimientos (Room v12)`.

---

### Task 2: Pantalla "Vehículo al día" + secciones en Taller + banner en Conducir

**Files:**
- Create: `feature/workshop/src/main/kotlin/com/revscope/feature/workshop/AlDiaViewModel.kt` — inyecta ObdSessionManager (activeProfile), settings DataStore (licencia: nueva key `LICENSE_EXPIRES_AT` Long). Expone `data class DocStatus(nivel, titulo, detalle, configurado: Boolean)` por card calculados con: fechas del perfil (rojo vencido / ámbar ≤30 días / verde), PicoYPlacaEngine (usa profile.plate + picoPlacaCity + type=="MOTORCYCLE"; reglas: MEDELLIN_2026_S1 o JSON custom de DataStore key `PICO_PLACA_RULES_JSON`), licencia de DataStore. Sin perfil activo o sin dato → gris "Por configurar".
- Create: `feature/workshop/src/main/kotlin/com/revscope/feature/workshop/AlDiaScreen.kt` — grid 2 columnas estilo R5 (LazyVerticalGrid): cards SOAT, Tecnomecánica (con TextButton "Chequeo mecánico" → onOpenHealthCheck), Pico y placa ("Puedes salir" / "No salgas hasta las 20:00" / "Hoy tienes restricción"), Multas (siempre CTA "Consultar en SIMIT" → copia placa al portapapeles + Intent ACTION_VIEW https://www.fcm.org.co/simit/ + Toast "Placa copiada"), Todo riesgo, Licencia. Cards no configuradas: gris + "Por configurar" → onOpenProfile (o diálogo de fecha para licencia, editable inline con DatePicker → DataStore). Header con nombre del vehículo activo; sin perfil activo → empty state con CTA a perfiles.
- Modify: `WorkshopScreen.kt` — reorganizar en secciones con subtítulos (Text muted 12sp): "Estado" (Vehículo al día [icono Icons.Default.Verified], Chequeo de salud), "Diagnóstico" (DTC, Mezcla, Sensores, Escáner Mode 22), "Vehículo" (Analizador de marchas, Perfiles). Nuevo parámetro `onOpenAlDia`.
- Modify: `Screen.kt` (+ `AlDia : Screen("al_dia")`), `RevScopeNavGraph.kt` (ruta + lambda).
- Modify: `feature/dashboard/.../DashboardScreen.kt` + DashboardViewModel — banner contextual: si el estado agregado del vehículo activo tiene algo rojo o pico y placa hoy → banner delgado clickeable bajo el TopAppBar ("⚠ SOAT vencido · Pico y placa hasta las 20:00") → onNavigateToAlDia (nuevo parámetro, wired en NavGraph). Cálculo en el VM inyectando lo mismo que AlDiaViewModel — extraer el cálculo común a `core/obd/legal/DocumentStatusCalculator.kt` (puro, testeable) para no duplicar. Todo verde/sin datos → sin banner.

**Steps:** leer archivos → implementar → `$GRADLE :app:compileDebugKotlin` → commit `feat: pantalla vehículo al día con semáforos, taller por secciones y banner contextual`.

---

### Task 3: Notificación diaria (WorkManager)

**Files:**
- Modify: `gradle/libs.versions.toml` + `core/obd/build.gradle.kts` — agregar androidx.work:work-runtime-ktx (verificar si ya existe en el catálogo).
- Create: `core/obd/src/main/kotlin/com/revscope/core/obd/legal/DailyStatusWorker.kt` — CoroutineWorker (HiltWorker — verificar si el proyecto ya tiene androidx.hilt:hilt-work; si no, agregarlo + kapt compiler y el WorkerFactory en la clase Application — leer app/src/main/.../RevScopeApp*.kt primero): consulta todos los perfiles con plate/fechas, calcula con DocumentStatusCalculator/PicoYPlacaEngine, y postea UNA notificación resumen (canal nuevo "revscope_documentos", IMPORTANCE_DEFAULT) solo si hay algo que decir: pico y placa hoy para algún vehículo, o vencimientos a 30/15/7/1/0 días. Texto ej: "🏍 Apache: pico y placa hoy hasta las 20:00 · SOAT vence en 7 días". Tap → MainActivity extra `open_al_dia=true` → NavGraph navega a AlDia (mismo patrón del deep link de resumen).
- Modify: `app/.../RevScopeApp*.kt` (Application) — encolar `PeriodicWorkRequest` diario con `KEEP`, initial delay calculado hasta las 5:30am siguientes (America/Bogota).
- Modify: `MainActivity.kt` + `RevScopeNavGraph.kt` — extra `open_al_dia` → navegar a Screen.AlDia (patrón consumeSessionId existente).

**Steps:** leer archivos → implementar → `$GRADLE :app:compileDebugKotlin` → commit `feat: notificación diaria de pico y placa y vencimientos de documentos`.

---

### Task 4 (inline): build + tests + install + verificar migración v12 (12 sesiones intactas) + push
