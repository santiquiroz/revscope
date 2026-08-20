# Onboarding Wizard — Implementation Plan (sub-proyecto D)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Instalación limpia sin adaptador ni key de IA deja de ser engorrosa: wizard de 5 pasos (permisos → vehículo → adaptador/modo GPS → IA con propuesta de valor → listo), dashboard en modo GPS con jerarquía invertida, e IA vendida en contexto.

**Architecture:** `OnboardingScreen` se convierte en un wizard por pasos gobernado por un `step` en el ViewModel (máquina trivial 0..4, todo saltable). El paso vehículo crea el primer perfil con los defaults por tipo de C vía `VehicleProfileDao`. El paso IA reusa `SettingsViewModel` (saveAiSettings/testAiConnection ya probados) dentro de una `AiValueScreen` reutilizable que también se registra como ruta para las CTAs "Configurar IA". `GPS_ONLY_MODE` invierte la jerarquía del dashboard; configurar un adaptador lo apaga.

**Tech Stack:** Kotlin, Compose, Hilt, DataStore, Navigation Compose.

**Spec:** docs/superpowers/specs/2026-08-18-onboarding-wizard-design.md

## Global Constraints

- Trabajar desde `C:\personal\OBD2`. NO gradlew: `GRADLE="$HOME/.gradle/wrapper/dists/gradle-8.11.1-bin/bpt9gzteqjrbo1mjrsomdt32c/gradle-8.11.1/bin/gradle"`.
- Commits español `tipo: descripción`, SIN Co-Authored-By.
- **Todos los pasos saltables**; `ONBOARDING_DONE` se marca al completar O saltar el flujo (semántica actual de `markDone()` se conserva). Salir a mitad = done marcado, lo configurado queda.
- Defaults por tipo del paso vehículo = los de C: MOTO 12000/10500/gearCount 5, CAR 8000/6500/6 (usar los MISMOS valores; el form mínimo NO expone PIDs/VIN/ratios/fechas).
- Paleta del onboarding existente: Bg `0xFF0A0A0F`, Surface `0xFF12121A`, Accent `0xFFE8FF00`, TextPrimary `0xFFF0F0F8`, TextMuted `0xFF6B7089`.
- Propuesta de valor IA (4 bullets exactos): chat mecánico especializado en TU vehículo; debrief IA al final de cada viaje; pico y placa por IA en cualquier ciudad; explicación de códigos de falla (DTC). Provider default `AI_PROVIDER_GEMINI`, link "obtener key gratis" → `https://aistudio.google.com/apikey` (abrir con Intent ACTION_VIEW).
- Pref nueva `GPS_ONLY_MODE` (Boolean). Configurar adaptador la desactiva.
- Targets táctiles ≥48dp en botones del wizard.
- Migraciones/Room NO se tocan en este sub-proyecto.

---

### Task 1: Máquina de pasos + shell del wizard (paso permisos migrado)

**Files:**
- Modify: `app/src/main/kotlin/com/revscope/app/onboarding/OnboardingViewModel.kt`
- Modify: `app/src/main/kotlin/com/revscope/app/onboarding/OnboardingScreen.kt`
- Modify: `core/data/src/main/kotlin/com/revscope/core/data/datastore/PreferencesKeys.kt` (`GPS_ONLY_MODE`)

**Interfaces:**
- Produces (tasks 2-4 consumen): en el VM — `step: StateFlow<Int>` (0..4), `fun next()`, `fun back()`, `fun goTo(step: Int)`, `TOTAL_STEPS = 5`; `fun setGpsOnlyMode(enabled: Boolean)` (persiste `PreferencesKeys.GPS_ONLY_MODE`); `markDone()` intacto. En la pantalla — composable contenedor `OnboardingScreen(onFinished: (goToAdapterScan: Boolean) -> Unit)` con slots por paso: cada paso es un composable privado `StepN...` dentro del archivo; barra inferior con "Atrás" (si step>0), "Saltar" y "Siguiente"/"Empezar" (48dp de alto).
- El callback `onFinished` CAMBIA de firma (`() -> Unit` → `(Boolean) -> Unit`): Task 3 lo consume; el call site en `RevScopeNavGraph` se actualiza EN ESTE task pasando `false` fijo (Task 3 lo cablea de verdad).

- [ ] **Step 1: ViewModel — máquina de pasos**

Agregar a `OnboardingViewModel`:

```kotlin
    private val _step = MutableStateFlow(0)
    val step: StateFlow<Int> = _step.asStateFlow()

    fun next() { _step.value = (_step.value + 1).coerceAtMost(TOTAL_STEPS - 1) }

    fun back() { _step.value = (_step.value - 1).coerceAtLeast(0) }

    fun goTo(step: Int) { _step.value = step.coerceIn(0, TOTAL_STEPS - 1) }

    fun setGpsOnlyMode(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { settings.edit { it[PreferencesKeys.GPS_ONLY_MODE] = enabled } }
                .onFailure { Timber.w(it, "OnboardingViewModel: failed to persist gps-only mode") }
        }
    }

    companion object { const val TOTAL_STEPS = 5 }
```

`PreferencesKeys.kt`: `val GPS_ONLY_MODE = booleanPreferencesKey("gps_only_mode")` junto a las keys de modo/onboarding existentes.

- [ ] **Step 2: Pantalla — contenedor por pasos**

Reestructurar `OnboardingScreen.kt`: el `Scaffold` actual pasa a contener un `when (step)` + barra inferior. El contenido actual de permisos (los 3 `PermissionCard` + títulos) se mueve intacto a `Step0Permissions(...)`. Los pasos 1-3 quedan como stubs `StepPlaceholder("...")` que Tasks 2-4 reemplazan; el paso 4 ("Listo") se implementa ya:

```kotlin
@Composable
fun OnboardingScreen(
    onFinished: (goToAdapterScan: Boolean) -> Unit,
    vm: OnboardingViewModel = hiltViewModel(),
) {
    val step by vm.step.collectAsState()

    Scaffold(containerColor = BgColor) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding).padding(24.dp)) {
            StepIndicator(step)
            Box(Modifier.weight(1f)) {
                when (step) {
                    0 -> Step0Permissions()
                    1 -> StepPlaceholder("Tu vehículo")      // Task 2
                    2 -> StepPlaceholder("Adaptador OBD2")   // Task 3
                    3 -> StepPlaceholder("Inteligencia")     // Task 4
                    else -> Step4Done()
                }
            }
            WizardBar(
                step = step,
                onBack = vm::back,
                onSkip = vm::next,
                onNext = {
                    if (step < OnboardingViewModel.TOTAL_STEPS - 1) vm.next()
                    else { vm.markDone(); onFinished(false) }
                },
            )
        }
    }
}
```

`StepIndicator`: fila de 5 puntos (Accent el activo, Muted el resto). `WizardBar`: Row con TextButton "Atrás" (visible si step>0), Spacer weight, TextButton "Saltar" (visible si step<4), Button 48dp "Siguiente" (o "Empezar" en step 4). `Step4Done`: título "Listo" + texto "Podés cambiar todo esto después en Ajustes." `StepPlaceholder(title)`: solo el título (muere en los tasks siguientes).

En `RevScopeNavGraph` (call site de `OnboardingScreen`, ~194-198): la lambda `onFinished` gana el parámetro Boolean; por ahora ignora el flag (mismo pop a Dashboard). Nota exacta del cambio para el implementer: buscar `OnboardingScreen(` en el NavGraph y adaptar la firma.

- [ ] **Step 3: Compile + commit**

Run: `"$GRADLE" :app:compileDebugKotlin :core:data:compileDebugKotlin` → BUILD SUCCESSFUL.

```bash
git add app core/data
git commit -m "feat: wizard de onboarding por pasos — máquina de estados y paso de permisos"
```

---

### Task 2: Paso vehículo — perfil mínimo con defaults por tipo

**Files:**
- Modify: `app/src/main/kotlin/com/revscope/app/onboarding/OnboardingViewModel.kt` (crear perfil)
- Modify: `app/src/main/kotlin/com/revscope/app/onboarding/OnboardingScreen.kt` (Step1Vehicle reemplaza el stub)

**Interfaces:**
- Consumes: `VehicleProfileDao` (insert), `VehicleProfileEntity` (con `gearCount` de C), mecanismo de perfil activo — grep cómo `VehicleViewModel.saveProfile`/el picker marcan el perfil activo (pref/flag) y replicar SOLO eso.
- Produces: en el VM — `fun createFirstProfile(name: String, type: String, plate: String)` (no-op con name en blanco); estado `profileCreated: StateFlow<Boolean>`.

- [ ] **Step 1: VM**

Inyectar `VehicleProfileDao` en el constructor. Agregar:

```kotlin
    private val _profileCreated = MutableStateFlow(false)
    val profileCreated: StateFlow<Boolean> = _profileCreated.asStateFlow()

    /** Perfil mínimo del wizard: defaults por tipo (los de sub-proyecto C), sin campos avanzados. */
    fun createFirstProfile(name: String, type: String, plate: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val motorcycle = type == "MOTORCYCLE"
        viewModelScope.launch {
            runCatching {
                profileDao.insert(
                    VehicleProfileEntity(
                        name = trimmed,
                        type = type,
                        plate = plate.trim().uppercase(),
                        maxRpm = if (motorcycle) 12_000 else 8_000,
                        redlineRpm = if (motorcycle) 10_500 else 6_500,
                        gearCount = if (motorcycle) 5 else 6,
                    ),
                )
            }.onSuccess { _profileCreated.value = true }
                .onFailure { Timber.w(it, "OnboardingViewModel: failed to create first profile") }
        }
    }
```

OJO constructor de la entity: los demás campos tienen defaults — verificar compilando; si algún campo obligatorio falta (p.ej. `enabledPids`), pasar el default que usa `VehicleViewModel.saveProfile` (grep). Activación: replicar el mecanismo mínimo de perfil activo que use el repo (grep `setActiveProfile`/pref de perfil activo desde `VehicleViewModel`); si la activación ocurre vía `ObdSessionManager.setActiveProfile`, inyectar el manager y llamarlo con la entity insertada (el DAO insert devuelve id — copiar con `copy(id = insertedId)`).

- [ ] **Step 2: Step1Vehicle**

Reemplazar el stub: título "Tu vehículo", subtítulo "Crealo ahora y la app se adapta: gauges, marchas y alertas según sea moto o carro.", `OutlinedTextField` nombre, chips Auto/Moto (48dp, estilo `PlaceChip` — Surface redondeada Accent cuando activo), `OutlinedTextField` placa (opcional), botón "Crear" que llama `vm.createFirstProfile(...)` y muestra ✓ "Creado" cuando `profileCreated`. Todo saltable — el WizardBar sigue visible.

- [ ] **Step 3: Compile + commit**

Run: `"$GRADLE" :app:compileDebugKotlin` → BUILD SUCCESSFUL.

```bash
git add app
git commit -m "feat: paso de vehículo del wizard — perfil mínimo con defaults por tipo"
```

---

### Task 3: Paso adaptador / modo GPS explícito

**Files:**
- Modify: `app/src/main/kotlin/com/revscope/app/onboarding/OnboardingScreen.kt` (Step2Adapter)
- Modify: `app/src/main/kotlin/com/revscope/app/navigation/RevScopeNavGraph.kt` (onFinished(true) → AdapterScan)

**Interfaces:**
- Consumes: `vm.setGpsOnlyMode(Boolean)` (Task 1), `onFinished(goToAdapterScan: Boolean)` (Task 1), ruta existente de AdapterScan en el NavGraph (grep `AdapterScan`).
- Produces: nada nuevo para otros tasks.

- [ ] **Step 1: Step2Adapter**

Reemplazar el stub. Título "¿Tenés adaptador OBD2?", dos cards grandes (48dp+ táctiles):

- **"Sí, configurarlo ahora"** → `vm.setGpsOnlyMode(false); vm.markDone(); onFinished(true)` (sale del wizard directo al escaneo — pasar el callback hasta el paso vía parámetro del composable).
- **"Sí, pero después"** → `vm.setGpsOnlyMode(false); vm.next()`.
- **"No tengo — usar solo GPS"** → `vm.setGpsOnlyMode(true)` y expandir debajo la lista de lo que funciona sin adaptador (5 filas con icono ✓ Accent): "Mapa con radares automáticos", "Viajes GPS con telemetría de movimiento", "Detección de caída", "Pico y placa", "Historial y reportes". Botón "Continuar" → `vm.next()`.

- [ ] **Step 2: NavGraph**

En el call site de `OnboardingScreen`: `onFinished = { goToAdapterScan -> ... }` — si `true`, navegar a la ruta de AdapterScan DESPUÉS del pop del onboarding (mirar cómo navega el picker a AdapterScan — `onManageAdapter` ~352-355 — y replicar ese navigate con el mismo popUpTo que ya usa el flujo de onboarding para no dejar el wizard en el back stack).

- [ ] **Step 3: Compile + commit**

Run: `"$GRADLE" :app:compileDebugKotlin` → BUILD SUCCESSFUL.

```bash
git add app
git commit -m "feat: paso de adaptador del wizard — modo solo GPS explícito"
```

---

### Task 4: Paso IA con propuesta de valor + AiValueScreen reutilizable + CTAs

**Files:**
- Create: `app/src/main/kotlin/com/revscope/app/onboarding/AiValueScreen.kt`
- Modify: `app/src/main/kotlin/com/revscope/app/onboarding/OnboardingScreen.kt` (Step3Ai usa el mismo contenido)
- Modify: `app/src/main/kotlin/com/revscope/app/navigation/RevScopeNavGraph.kt` (ruta `ai_value`)
- Modify: `feature/settings/src/main/kotlin/com/revscope/feature/settings/SettingsScreen.kt` (entry re-run wizard + CTA en copy de degradación IA)
- Modify: superficie de degradación IA del workshop (grep `"Sin API key"` / `sin explicación de IA` en feature/workshop — agregar CTA)

**Interfaces:**
- Consumes: `SettingsViewModel` (`@HiltViewModel` — instanciable con `hiltViewModel()` desde cualquier NavBackStackEntry): `_aiProvider` setter (grep el nombre público — hay `setAiProvider` o similar), `aiApiKey`/`setAiApiKey`, `saveAiSettings()`, `testAiConnection()`, estados `aiTesting`/`aiTestResult` (grep nombres exactos y usarlos). `AI_PROVIDER_GEMINI` de `com.revscope.core.intelligence.provider`.
- Produces: composable `AiValueContent(onDone: () -> Unit, compact: Boolean)` (el cuerpo compartido) + pantalla `AiValueScreen(onBack: () -> Unit)` registrada en ruta `"ai_value"` — las CTAs navegan ahí.

- [ ] **Step 1: AiValueContent + AiValueScreen**

`AiValueScreen.kt` (paleta del onboarding):

```kotlin
@Composable
fun AiValueContent(onDone: () -> Unit, compact: Boolean = false, vm: SettingsViewModel = hiltViewModel()) {
    // 4 bullets de valor (constraint global), cada uno Row(icono Accent + texto):
    // "Chat mecánico especializado en TU vehículo"
    // "Debrief IA al final de cada viaje"
    // "Pico y placa por IA en cualquier ciudad"
    // "Explicación de códigos de falla (DTC)"
    // Campo key (OutlinedTextField, visualTransformation = PasswordVisualTransformation()),
    // al montarse: vm fija provider Gemini (setter real por grep).
    // Row: TextButton "Obtener key gratis" -> Intent ACTION_VIEW https://aistudio.google.com/apikey
    //      Button "Probar" -> vm.testAiConnection(); muestra aiTestResult debajo.
    // Button "Guardar" -> vm.saveAiSettings(); onDone()
    // TextButton "Después" -> onDone()   (solo si !compact lo muestra el caller del wizard vía WizardBar)
}

@Composable
fun AiValueScreen(onBack: () -> Unit) {
    // Scaffold con TopAppBar "Inteligencia artificial" + flecha back, body = AiValueContent(onDone = onBack)
}
```

(El código final lo escribe el implementer con los nombres reales de SettingsViewModel — el contrato de arriba es vinculante: provider Gemini por default, key, probar, guardar, link, después.)

`Step3Ai` en el wizard = `AiValueContent(onDone = vm::next, compact = true)` bajo el título "¿Querés la capa de inteligencia?" — sin duplicar el contenido.

- [ ] **Step 2: Ruta + CTAs**

- NavGraph: `composable("ai_value") { AiValueScreen(onBack = { navController.popBackStack() }) }` (agregar objeto a `Screen` si el patrón del repo lo exige — seguir el patrón de las rutas existentes).
- `SettingsScreen` sección IA (~654-710): el texto "Sin API key, esas funciones se muestran sin explicación de IA." gana `TextButton("Ver qué ganás con una key")` → callback nuevo `onOpenAiValue: () -> Unit` cableado desde el NavGraph a `navigate("ai_value")` (seguir cómo SettingsScreen recibe hoy sus callbacks de navegación).
- Sección "Acerca de" (~890): entry nuevo "Volver a ver configuración inicial" → callback `onRerunOnboarding` → `navigate` a la ruta del onboarding (SIN resetear `ONBOARDING_DONE` — es navegación pura; el wizard al terminar hace pop normal).
- Workshop: en la superficie de degradación IA encontrada por grep, mismo `TextButton` "Configurar IA" → navegar a `ai_value` (cablear el callback por la cadena existente de ese screen).

- [ ] **Step 3: Compile + commit**

Run: `"$GRADLE" :app:compileDebugKotlin :feature:settings:compileDebugKotlin :feature:workshop:compileDebugKotlin` → BUILD SUCCESSFUL.

```bash
git add app feature/settings feature/workshop
git commit -m "feat: paso de IA con propuesta de valor, pantalla reutilizable y CTAs en contexto"
```

---

### Task 5: Dashboard en modo GPS

**Files:**
- Modify: `feature/dashboard/src/main/kotlin/com/revscope/feature/dashboard/DashboardScreen.kt` (jerarquía invertida)
- Modify: `feature/dashboard/src/main/kotlin/com/revscope/feature/dashboard/DashboardViewModel.kt` (exponer `gpsOnlyMode`) — o el VM que el screen ya use para prefs (grep DataStore en el módulo; seguir el patrón)
- Modify: donde se persiste el adaptador (`ADAPTER_ADDRESS` — grep) para limpiar `GPS_ONLY_MODE` al configurar uno

**Interfaces:**
- Consumes: `PreferencesKeys.GPS_ONLY_MODE` (Task 1).
- Produces: `gpsOnlyMode: StateFlow<Boolean>` en el VM del dashboard.

- [ ] **Step 1: VM + layout**

- VM: `gpsOnlyMode` desde DataStore (`map { it[GPS_ONLY_MODE] ?: false }` + stateIn, patrón del módulo).
- `DashboardScreen`: con `gpsOnlyMode == true` Y sin conexión OBD activa: reordenar — arriba `GpsTripButton` prominente (el existente, ~275-280) + botón "Ver mapa" (navega a la tab Mapa — usar el callback de navegación que el screen ya tenga o el que provea el NavGraph; si no existe, agregar callback `onOpenMap` cableado en el NavGraph a la ruta "map"); abajo los gauges dimmed agrupados con UNA CTA "Configurar adaptador" (→ AdapterScan vía el callback existente del status-dot). Con `gpsOnlyMode == false`: layout actual intacto.
- Limpieza: en el punto donde se guarda `ADAPTER_ADDRESS` tras vincular un adaptador (grep — AdapterScan/ObdSessionManager), agregar `settings.edit { it[PreferencesKeys.GPS_ONLY_MODE] = false }` con comentario de una línea (configurar adaptador = salir del modo GPS).

- [ ] **Step 2: Compile + commit**

Run: `"$GRADLE" :feature:dashboard:compileDebugKotlin :core:obd:compileDebugKotlin` → BUILD SUCCESSFUL.

```bash
git add feature/dashboard core/obd
git commit -m "feat: dashboard en modo GPS — viaje y mapa protagonistas, gauges con CTA única"
```

---

### Task 6: Verificación integral

**Files:** ninguno.

- [ ] **Step 1:** `"$GRADLE" test :app:assembleDebug` con `echo "EXIT: $?"` (sin pipes). Expected EXIT: 0.
- [ ] **Step 2:** `adb devices` — si hay device, `installDebug`; sin device, skip documentado.
- [ ] **Step 3 (checklist manual para el usuario):** borrar datos → wizard 5 pasos; saltar todo → dashboard normal; crear perfil moto en paso 2 → Garaje muestra 12000/10500/5; "No tengo adaptador" → dashboard invertido con Viaje GPS arriba; paso IA "después" → chat del taller muestra CTA "Configurar IA" → abre pantalla de valor; Ajustes → "Volver a ver configuración inicial" re-abre el wizard; vincular adaptador → dashboard vuelve al layout normal.

---

## Self-review (hecho al escribir)

- Spec coverage: D1 wizard 5 pasos ✅ T1-T4 (paso 1 permisos migrado intacto, todo saltable, `ONBOARDING_DONE` al completar o saltar — semántica de salida a mitad la cubre `markDone()` en "Empezar"/"Sí configurarlo ahora"; nota: salir por back del sistema a mitad NO marca done — igual que hoy, aceptado); D1 paso 2 ✅ T2 (form mínimo, defaults C, resuelve picker-invisible-con-0-perfiles); D1 paso 3 ✅ T3 (GPS_ONLY_MODE persistido, lista de valor sin adaptador); D1 paso 4 ✅ T4 (valor primero, Gemini default, probar, link, sin modelo/URL custom); D2 ✅ T5 (jerarquía invertida + limpieza al configurar adaptador); D3 ✅ T4 (AiValueScreen ruta + CTAs settings/workshop + re-run wizard). Errores del spec: key inválida en Probar → `aiTestResult` inline sin bloquear ✅ T4; permisos denegados avanzan ✅ (WizardBar siempre visible).
- Placeholders: T4 Step 1 delega nombres exactos de SettingsViewModel a grep con contrato vinculante — decisión consciente (el VM tiene ~900 líneas, los nombres existen; contrato completo en el paso). `StepPlaceholder` es scaffolding que muere dentro del mismo plan (T2-T4), no un TBD final.
- Tipos: `onFinished(goToAdapterScan: Boolean)` consistente T1/T3; `setGpsOnlyMode(Boolean)` T1→T3; `AiValueContent(onDone, compact)` T4 interno; `gpsOnlyMode: StateFlow<Boolean>` T5.
- Sin Room/migraciones (constraint) — el paso vehículo usa la entity v18 existente.
