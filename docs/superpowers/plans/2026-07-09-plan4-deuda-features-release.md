# Plan 4: Deuda técnica + features nuevos + ejes en gráficas + README/release v1.3.0

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development. Ejecutar tareas EN ORDEN.

**Goal:** Pagar la deuda técnica registrada en el ledger, agregar los features aprobados (backup automático, detección de caída, onda O2 + Mode 06, onboarding de permisos), poner ejes y unidades a TODAS las gráficas, y cerrar con README + screenshots + release v1.3.0.

## Global Constraints
- Gradle: `C:\Users\santi\.gradle\wrapper\dists\gradle-8.11.1-bin\bpt9gzteqjrbo1mjrsomdt32c\gradle-8.11.1\bin\gradle.bat` (=$GRADLE) desde c:/personal/OBD2.
- Commits español, NUNCA Co-Authored-By. UI español. CancellationException se relanza SIEMPRE antes de catch genérico.
- Tests actuales: 255 verdes en :core:obd — deben seguir verdes en cada tarea. Lógica nueva = funciones puras con TDD donde aplique.
- SIN cambios de schema Room salvo que una tarea lo diga explícitamente (ninguna lo dice — todo DataStore).
- Push siempre con `$env:GITHUB_TOKEN = $null` antes (PowerShell) — token de trabajo pisa el keyring.

---

### Task 1: Deuda A — falso "sin códigos", guard ANSV, quitar fallback destructivo

1. **Falso limpio en el chequeo de salud** (`feature/workshop/.../HealthCheckViewModel.kt`, leer primero): hoy `readDtcMode` hace `rawExchange(cmd).getOrNull() ?: emptyList()` — un enlace caído a mitad de escaneo produce "Sin códigos de falla / Memoria de fallas limpia" en un informe COMPARTIBLE. Cambiar: distinguir fallo de lectura por modo; si CUALQUIER lectura DTC falló → el ítem DTC del informe se convierte en `Diagnosis(ATENCION, "DTC", "Lectura de códigos incompleta", "Se perdió el enlace durante el escaneo — repite el chequeo")` y NUNCA "sin códigos". Igual para readiness: si la lectura falla, ítem ATENCION "Readiness no disponible".
2. **Guard de regresión ANSV** (`core/obd/.../cameras/SpeedCameraUpdater.kt` + `AnsvCameraSource/Parser`): si el payload ANSV descarga OK (>100KB) pero el parser produce 0 ubicaciones totales (antes del filtro de radio) → `Timber.e("AnsvCameraParser: payload no vacío con 0 registros — posible cambio de formato")` y tratar la fuente como FALLIDA (no como éxito vacío) para la lógica no-wipe existente. Exponer en el status de Settings el conteo por fuente: "X radares (OSM: a · ANSV: b)".
3. **Quitar `fallbackToDestructiveMigration()`** (`core/data/.../di/DataModule.kt`): todas las migraciones 9→13 son reales; un salto sin ruta debe CRASHEAR (loud) y no borrar. Quitar la línea + actualizar el comentario: "Sin fallback destructivo: toda migración debe ser explícita (incidente 2026-07-08)".

Verificar: `$GRADLE :core:obd:testDebugUnitTest :app:compileDebugKotlin`. Commit: `fix: informe de salud honesto ante fallos de lectura, guard de formato ANSV y fin del fallback destructivo`.

---

### Task 2: Deuda B — descomponer ObdSessionManager (paridad de comportamiento)

`core/obd/.../session/ObdSessionManager.kt` (~700 líneas) concentra conexión, telemetría, watchers y agregados. Extraer SIN cambiar comportamiento (leer TODO el archivo primero):
1. `core/obd/.../session/VoltagePoller.kt` — clase con `start(scope, bt, onReading: (ObdReading) -> Unit)` / `stop()`; mueve startVoltagePolling + parseVoltage (companion se queda como typealias/redirección o se mueve — mantener API pública `ObdSessionManager.parseVoltage` si algún test la usa: buscar usos).
2. `core/obd/.../session/MilWatcher.kt` — mueve milWatchJob (constructor: alertsEngine; `start(scope, bt, onMilReading)` / `stop()`).
3. `core/obd/.../session/SessionAggregator.kt` — mueve el cuerpo de updateSessionEnd (cálculo de maxRpm/maxSpeed/distancia/fuel/eco) como `suspend fun close(sessionId, profile, settings...)` con las mismas dependencias (daos, calculators); el manager solo orquesta.
4. El manager conserva: estados/flows públicos (API intacta — NADA de feature/* debe cambiar), conexión/reconexión/energía, wiring.
Verificar: `$GRADLE :core:obd:testDebugUnitTest` (255 verdes — los tests de EngineOffDetector/TripSummaryNotifier/etc no se tocan) + `:app:assembleDebug`. Commit: `refactor: ObdSessionManager descompuesto en VoltagePoller, MilWatcher y SessionAggregator sin cambios de comportamiento`.

---

### Task 3: Ejes y unidades en TODAS las gráficas + perf de mapa/mezcla

1. **Inventario**: grep de gráficas: Vico (`feature/sensors/.../SensorGraphScreen.kt` u homólogo — buscar `CartesianChartHost|LineChart|chartOf`), y canvas custom en `feature/session/` (curvas del reporte: velocidad/RPM/HR, `ThrottleGScatter.kt`, `FrictionCircle.kt`, `TrackMap.kt` no aplica). Leer cada una.
2. **Vico**: agregar `startAxis` (etiqueta con la unidad del PID: usar PidDefinition.unit via registry, ej. "°C", "RPM") y `bottomAxis` (tiempo relativo mm:ss). Título/leyenda con nombre del PID + unidad. Si la versión de Vico del catálogo usa `rememberStartAxis()/rememberBottomAxis()` — adaptarse a la API real del lockfile.
3. **Canvas custom del reporte**: para cada gráfica dibujada a mano: eje Y con 3-4 ticks etiquetados (min/mid/max con unidad), eje X con ticks de tiempo (inicio/mitad/fin mm:ss), líneas de grid sutiles (alpha 0.15), etiqueta de unidad en la esquina. ThrottleGScatter: ejes "% acelerador" (X) y "G lateral" (Y) con ticks. FrictionCircle ya tiene anillos — agregar etiquetas de G en los anillos (0.5G, 1.0G) si no las tiene.
4. **Perf**: `feature/map/.../LiveMapScreen.kt`: polyline incremental — mantener referencia al Polyline y `addPoint` para los nuevos puntos en vez de reconstruir todo cuando solo creció la ruta (reconstrucción completa solo si cameras cambió o la ruta se ENCOGIÓ/reset). `feature/workshop/.../LiveMixtureScreen.kt`: en el VM, filtrar el StateFlow de readings a solo los PIDs de las filas (`map { it.filterKeys(ROWS_PIDS::contains) }.distinctUntilChanged()` + stateIn) para no recomponer a 10Hz por PIDs ajenos.
Verificar: `$GRADLE :app:compileDebugKotlin`. Commit: `feat: ejes, unidades y grid en todas las gráficas; mapa incremental y mezcla sin recomposición ajena`.

---

### Task 4: Backup automático semanal + onboarding de permisos

1. **Backup automático** (`core/data/.../backup/` + Application): `AutoBackupWorker` (@HiltWorker, patrón CameraRefreshWorker) semanal (KEEP, nombre "auto_backup"): usa BackupManager.export hacia MediaStore Downloads/RevScope (RELATIVE_PATH "Download/RevScope", MediaStore.Downloads insert — API 29+, minSdk 26: gate con `if (Build.VERSION.SDK_INT >= 29)`; en <29 usar getExternalFilesDir fallback) con nombre `revscope-auto-AAAA-MM-DD.zip`; conservar solo los 4 más recientes (query + delete por displayName prefix). Toggle en Ajustes sección Copia de seguridad: "Copia automática semanal" (default ON) — el worker chequea el flag y sale temprano si off.
2. **Onboarding** (`app/.../onboarding/OnboardingScreen.kt` + ruta): primera ejecución (DataStore ONBOARDING_DONE) ANTES del picker de vehículo: pantalla única con 3 cards de permisos (Ubicación "para grabar tus rutas y avisarte de radares", Notificaciones "resumen de viaje y pico y placa", Bluetooth/Nearby "para conectar el adaptador OBD2" — permisos runtime: ACCESS_FINE_LOCATION, POST_NOTIFICATIONS, BLUETOOTH_CONNECT+SCAN API31+) con estado ✓/✗ por card y botón por card que lanza `rememberLauncherForActivityResult(RequestPermission())`; botón "Empezar" al final persiste ONBOARDING_DONE y navega a Dashboard. NavGraph: startDestination condicional (leer ONBOARDING_DONE en MainActivity/NavGraph antes de componer el grafo — patrón como initialSessionId). No re-pedir si ya concedidos (cards en ✓).
Verificar: `$GRADLE :app:assembleDebug`. Commit: `feat: copia de seguridad automática semanal y onboarding de permisos en el primer arranque`.

---

### Task 5: Detección de caída (moto) con SMS de emergencia

1. **Motor puro TDD** `core/obd/.../safety/CrashDetector.kt`: máquina de estados alimentada por muestras IMU (magnitud total de aceleración en G) + velocidad (km/h) + timestamps:
   - Estados: MONITORING → IMPACT_DETECTED → (inmovilidad) → TRIGGERED, o cancelación.
   - Regla: impacto = |a| > 6G (pico) con velocidad previa > 20 km/h en los últimos 5s; luego inmovilidad = velocidad < 3 km/h Y |a| < 1.3G sostenido 30s → TRIGGERED. Movimiento (>10 km/h) tras el impacto → volver a MONITORING (falso positivo).
   - API: `process(accelG: Double, speedKmh: Double, nowMs: Long): State`, `reset()`. Constantes nombradas. Tests: impacto+quieto 30s → TRIGGERED; impacto+sigue rodando → MONITORING; bache a 60km/h (5G) → no dispara; caída desde quieto (0 km/h previo) → no dispara (evita drops del celular en mano).
2. **Integración** (`core/obd/.../safety/CrashResponder.kt` + wiring en ObdForegroundService o MotionMetricsHub — leer cómo fluyen las muestras IMU→hub y velocidad→readings; alimentar el detector solo con sesión activa): al TRIGGERED → notificación FULL-SCREEN de alta prioridad con cuenta regresiva de 60s + alarma sonora máxima (canal propio IMPORTANCE_HIGH, sonido de alarma) y botón grande "ESTOY BIEN" (cancela); si expira → enviar SMS (SmsManager, permiso SEND_SMS runtime pedido al configurar) al contacto configurado: "⚠ RevScope: posible caída detectada de <perfil>. Última ubicación: https://maps.google.com/?q=lat,lon" (última ubicación del GPS del servicio).
3. **Ajustes** sección "Detección de caída": toggle (default OFF — solo activable tras configurar contacto), campo teléfono contacto de emergencia, botón "Probar" (simula la notificación/countdown SIN SMS real). Persistencia DataStore (CRASH_DETECTION_ENABLED, EMERGENCY_PHONE).
4. Manifest: SEND_SMS + USE_FULL_SCREEN_INTENT.
Verificar: `$GRADLE :core:obd:testDebugUnitTest` (255 + nuevos) + `:app:assembleDebug`. Commit: `feat: detección de caída con cuenta regresiva y SMS de emergencia (desactivada por defecto)`.

---

### Task 6: Taller F2 — onda del sensor O2 + visor Mode 06

1. **Onda O2** (`feature/workshop/.../O2WaveScreen.kt` + VM + ruta + card en sección Diagnóstico "Onda sensor O2" icono ShowChart): con workshopMode ON, graficar en vivo los últimos 60s del O2 B1S1 (PID 14) — Vico line chart CON ejes (Y: voltios 0-1.0 con ticks 0.2/0.45/0.8, banda visual rica/pobre; X: segundos relativos). Selector de sensor si hay más (14/15/18/19 presentes en readings). Texto interpretativo en vivo: cruces por 0.45V por minuto ("Sensor sano: >8 cruces/min" — contador simple) reutilizando DiagnosticRules.evaluarO2 para el chip de estado. El VM acumula muestras en ventana deslizante (List capped 240 muestras).
2. **Mode 06 visor** (`feature/workshop/.../Mode06Screen.kt` + VM + parser + ruta + card "Resultados a bordo (Mode 06)" icono FactCheck): comando `06 MID` por CAN: primero `rawExchange("06 00\r")` para MIDs soportados (bitmap igual que PID 00), luego por cada MID soportado `06 <MID>\r` y parsear respuesta estándar CAN: repeticiones de [MID][TID][UASID][2B valor][2B min][2B max]. `Mode06Parser` PURO con TDD (payload sintético con 2 registros, valores/limites, pass/fail = valor dentro de [min,max]); escala/unidad: mostrar RAW + UAS id (tabla de UAS comunes opcional: 0x01 cuentas, 0x0B kPa... solo las 5-6 más comunes, resto "raw"). UI: lista agrupada por MID con nombre conocido (0xA2 "Misfire cilindro 1"... tabla mínima de MIDs de misfire A1-AC y catalizador 21) + valor/min/max + ✓/✗. Nota UI: "Los valores dependen del fabricante — útil para comparar antes/después de una reparación".
Verificar: `$GRADLE :core:obd:testDebugUnitTest` + `:app:assembleDebug`. Commit: `feat: onda del sensor O2 en vivo y visor de resultados Mode 06 en el taller`.

---

### Task 7 (inline, controller): build integral + install + smoke test en dispositivo

Tests completos, assembleDebug, instalar en S25, abrir, capturar screenshots de: Conducir, Taller, Vehículo al día, Mapa, Mezcla en vivo, Onda O2, Historial con filtros, un reporte con gráficas nuevas, Ajustes (alertas de voz). Guardarlas en `docs/screenshots/` (crear dir; nombres kebab: conducir.png, taller.png...). Las capturas necesitan el celular desbloqueado — pedir al usuario si está bloqueado.

---

### Task 8: README + release v1.3.0

1. **README.md** (leer el actual): actualizar secciones con TODO lo nuevo (5 pestañas, garaje multi-vehículo, Vehículo al día + pico y placa + SIMIT, Taller completo con chequeo de salud/mezcla/onda O2/Mode 06, radares ANSV+OSM con refresco semanal, alertas de voz por categoría, detección de caída, backup manual+automático, costo COP, eco-score, mantenimiento por km, mapa en vivo). Insertar screenshots de docs/screenshots/ en una tabla/grid. Mantener el tono "para personas del común" + sección dev.
2. **Release v1.3.0**: `$GRADLE :app:assembleRelease` (firmado con debug keystore como releases anteriores — verificar cómo quedó configurado signingConfig en app/build.gradle.kts; si assembleRelease no firma, usar el APK debug como en releases previas — revisar `gh release view v1.2.0` para replicar el patrón de assets/notas). Notas de release en español agrupadas por área (Energía, Navegación, Taller, Garaje, Al día, Radares, Seguridad, Datos). `git tag v1.3.0` + `gh release create v1.3.0 --title "RevScope v1.3.0 — Taller, Garaje y Vehículo al día" --notes-file <notas> <apk>`. GITHUB_TOKEN=$null antes de gh (PowerShell).
3. Bump versionName a 1.3.0 / versionCode +1 en app/build.gradle.kts ANTES de compilar el APK del release. Commit: `chore: versión 1.3.0 y README con las funciones nuevas`.
