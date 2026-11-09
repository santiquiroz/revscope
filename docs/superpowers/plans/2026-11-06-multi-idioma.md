# RevScope multi-idioma — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** RevScope en inglés (por defecto) y español, con selector de idioma, voz/IA/notificaciones en el idioma de la app, unidades y etiquetas por región.

**Architecture:** Recursos Android por módulo (`values/` inglés, `values-es/` español literal). El dominio recibe un `StringResolver` explícito; Compose usa `stringResource`. Idioma propio en SharedPreferences + `LocaleManager` (33+) / `attachBaseContext` (26-32), cambio = reinicio. Unidades convertidas solo en presentación por un único `UnitFormatter`.

**Tech Stack:** Kotlin 2.2, Compose, Hilt, Robolectric + Roborazzi 1.75 (capturas JVM), Python 3.12 (guardas), Gradle 8.11.1 (`gradlew.bat`).

**Spec:** `docs/superpowers/specs/2026-11-06-multi-idioma-design.md`

## Global Constraints

- El español sale idéntico al actual: `verifyRoborazziDebug` pasa con las 444 capturas existentes sin regrabar (la matriz captura en `es`).
- Claves con el prefijo de módulo de la tabla del spec; argumentos posicionales `%1$s`; cantidades con `<plurals>`.
- Inglés de EE. UU. en `values/`, glosario del spec; base de redacción: `git show access201010/english-uk:<ruta>` cuando el archivo existe en el fork.
- Sin dependencias nuevas (ni AppCompat ni bibliotecas de i18n).
- No se traducen: logs, claves de DataStore/Room/JSON, encabezados CSV, descripciones y salidas MCP (`core/obd/.../mcp/`), centinelas que se parsean (`NADA`), la llamada al Geocoder de `LocalityDetector`.
- CSV y cualquier texto que se vuelve a parsear usan `Locale.ROOT`.
- Commits en español, sin `Co-Authored-By`. Quien implementa no corre `git commit`; el orquestador commitea tras revisar.
- Cada tarea de extracción se hace en su propio worktree `C:\personal\OBD2-wt\<tarea>` sobre `feat/i18n`.

## Review Focus

1. Texto calculado antes de cambiar de idioma (ViewModel, notificación persistente del servicio, caché de DTC): tras cambiar de idioma no debe verse una pantalla mezclada → el cambio reinicia el proceso (Tarea 3, test de `AppLanguage.set`).
2. Motor TTS sin datos del idioma (Android en español con la app en inglés y voz inglesa no instalada): debe hablar con la voz por defecto, no callar → test en Tarea 8.
3. Formato de números con locale inglés en textos que antes asumían coma (`FormatoTaller`): `12.5 V` en inglés, `12,5 V` en español, nunca `12,5 V` en inglés → test en Tarea 1 y casos en Tarea 9.
4. CSV exportado con la app en español: la coma decimal no debe aparecer en columnas numéricas → test en Tarea 16.
5. Región CO con la app en inglés: pico y placa, SOAT y SIMIT siguen visibles; región GB con inglés: SIMIT oculto y "MOT" en la inspección → tests en Tarea 21.

---

## Fase 1 — cimientos (secuencial, revisión línea por línea)

### Task 1: API de idioma en `:core:common`

**Files:**
- Create: `core/common/src/main/kotlin/com/revscope/core/common/i18n/StringResolver.kt`
- Create: `core/common/src/main/kotlin/com/revscope/core/common/i18n/AndroidStringResolver.kt`
- Create: `core/common/src/main/kotlin/com/revscope/core/common/i18n/AppLanguage.kt`
- Create: `core/common/src/main/kotlin/com/revscope/core/common/i18n/Region.kt`
- Create: `core/common/src/main/kotlin/com/revscope/core/common/i18n/LocaleNumbers.kt`
- Test: `core/common/src/test/kotlin/com/revscope/core/common/i18n/AppLanguageTest.kt`, `LocaleNumbersTest.kt`, `RegionTest.kt`

**Interfaces:**
- Produces:
  - `interface StringResolver { val locale: Locale; fun get(@StringRes id: Int, vararg args: Any): String; fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): String }`
  - `class AndroidStringResolver(context: Context) : StringResolver` — sin anotaciones de Hilt (el binding `@Singleton` lo da `:app` en la Task 3); resuelve con `AppLanguage.localizedContext(context.applicationContext)`, cacheado por tag.
  - `object AppLanguage { val SUPPORTED = listOf("en", "es"); fun resolve(savedTag: String, systemLocale: Locale): Locale; fun savedTag(context: Context): String; fun current(context: Context): Locale; fun localizedContext(context: Context): Context; fun persist(context: Context, tag: String) }` — `persist` escribe con `commit()` y en API 33+ fija `LocaleManager.applicationLocales`; en 33+ `current` lee primero `LocaleManager`.
  - `enum class Region { CO, US, GB, OTHER; companion object { fun fromCountry(country: String): Region; fun system(): Region } }`
  - `object LocaleNumbers { fun decimal(value: Double, decimals: Int, locale: Locale): String; fun signed(value: Double, decimals: Int, locale: Locale): String }` — separador decimal del locale (`DecimalFormatSymbols`), sin separador de miles, redondeo half-up como `FormatoTaller.redondear`.

- [ ] **Step 1: Tests que fallan**

```kotlin
@Test fun `sin preferencia y sistema en español colombiano resuelve español`() =
    assertEquals("es", AppLanguage.resolve("", Locale("es", "CO")).language)
@Test fun `sin preferencia y sistema en alemán resuelve inglés`() =
    assertEquals("en", AppLanguage.resolve("", Locale.GERMANY).language)
@Test fun `preferencia explícita gana al sistema`() =
    assertEquals("en", AppLanguage.resolve("en", Locale("es", "CO")).language)
@Test fun `tag no soportado cae a inglés`() =
    assertEquals("en", AppLanguage.resolve("fr", Locale("es", "CO")).language)
@Test fun `decimal en inglés usa punto`() = assertEquals("12.5", LocaleNumbers.decimal(12.46, 1, Locale.US))
@Test fun `decimal en español usa coma`() = assertEquals("12,5", LocaleNumbers.decimal(12.46, 1, Locale("es")))
@Test fun `signed negativo en español`() = assertEquals("-0,3", LocaleNumbers.signed(-0.3, 1, Locale("es")))
@Test fun `país CO GB US y otro`() {
    assertEquals(Region.CO, Region.fromCountry("CO")); assertEquals(Region.GB, Region.fromCountry("GB"))
    assertEquals(Region.US, Region.fromCountry("US")); assertEquals(Region.OTHER, Region.fromCountry("DE"))
}
```

- [ ] **Step 2:** `gradlew.bat :core:common:testDebugUnitTest` → FAIL (símbolos no existen).
- [ ] **Step 3:** Implementar las firmas de arriba. `Region.system()` usa `Resources.getSystem().configuration.locales[0].country`.
- [ ] **Step 4:** `gradlew.bat :core:common:testDebugUnitTest` → PASS.
- [ ] **Step 5:** Commit `feat: API de idioma, región y números por locale en core:common`.

### Task 2: `XmlStringResolver` y matriz de capturas en español

**Files:**
- Create: `core/ui-testing/src/main/kotlin/com/revscope/core/uitesting/XmlStringResolver.kt`
- Modify: `core/ui-testing/src/main/kotlin/com/revscope/core/uitesting/MatrizCaptura.kt` (calificadores con idioma)
- Test: `core/ui-testing/src/test/kotlin/com/revscope/core/uitesting/XmlStringResolverTest.kt` + fixture `core/ui-testing/src/test/resources/res-fixture/values{,-es}/strings.xml`

**Interfaces:**
- Consumes: `StringResolver` (Task 1).
- Produces:
  - `class XmlStringResolver(resDirs: List<File>, override val locale: Locale) : StringResolver` — lee `values/` y, si `locale.language != "en"`, superpone `values-<lang>/`; `<string>` y `<plurals>` (`one`/`other`); desescapa `\'`, `\"`, `\n`, `\@`; formatea con `String.format(locale, …)`.
  - Constructor completo: `XmlStringResolver(resDirs: List<File>, locale: Locale, rClasses: List<Class<*>> = emptyList())`; `get(@StringRes id)` busca el nombre del id por reflexión en los campos de `R.string`/`R.plurals` de esas clases `R`.
  - `companion object { fun es(vararg rClasses: Class<*>, resDirs: List<String> = listOf("src/main/res")): XmlStringResolver; fun en(...)` mismo — rutas relativas al directorio del módulo (así corre Gradle los tests). Uso: `XmlStringResolver.es(R::class.java)`.
  - `DispositivoCaptura.calificadores(altoMinimoDp, idioma: String = "es")` → `"es-w360dp-h640dp-xhdpi"`; `MatrizCaptura.componente/pantalla/dialogo(..., idioma: String = "es")`; en `idioma != "es"` el archivo lleva sufijo `_<idioma>` (`rutaCaptura`).

- [ ] **Step 1: Tests que fallan** — con el fixture (`values`: `<string name="t_hola">Hello %1$s</string>`, `<plurals name="t_min"><item quantity="one">%d minute</item><item quantity="other">%d minutes</item></plurals>`; `values-es`: `Hola %1$s`, `%d minuto`/`%d minutos`, `<string name="t_apos">Don\'t</string>` solo en `values`):

```kotlin
@Test fun `es superpone values-es`() = assertEquals("Hola Ana", res("es").get("t_hola", "Ana"))
@Test fun `en lee values`() = assertEquals("Hello Ana", res("en").get("t_hola", "Ana"))
@Test fun `plural uno y varios`() { assertEquals("1 minuto", res("es").plural("t_min", 1, 1)); assertEquals("3 minutes", res("en").plural("t_min", 3, 3)) }
@Test fun `clave sin traducción cae a values`() = assertEquals("Don't", res("es").get("t_apos"))
@Test fun `por id usa la clase R`() = assertEquals("Hola Ana", res("es", FakeR::class.java).get(FakeR.string.t_hola, "Ana"))
@Test fun `id inexistente lanza IllegalArgumentException`() { assertThrows(IllegalArgumentException::class.java) { res("es", FakeR::class.java).get(0x7f999999) } }
```

El fixture no tiene clase `R`: el resolutor también expone `fun get(name: String, vararg args: Any)` y `fun plural(name: String, count: Int, vararg args: Any)`, y los tests del fixture usan esos (`res("es").get("t_hola", "Ana")`); la ruta por id se prueba con una clase `R` falsa del test (`object FakeR { object string { @JvmField val t_hola = 0x7f010001 } }`).
- [ ] **Step 2:** `gradlew.bat :core:ui-testing:testDebugUnitTest` → FAIL.
- [ ] **Step 3:** Implementar. Cambiar `MatrizCaptura` para anteponer el idioma a los calificadores.
- [ ] **Step 4:** `gradlew.bat :core:ui-testing:testDebugUnitTest` → PASS, y `gradlew.bat verifyRoborazziDebug` → PASS sin cambios en ninguna de las 444 capturas.
- [ ] **Step 5:** Commit `test: resolutor de strings.xml para tests JVM y capturas en español`.

### Task 3: idioma de la app (plumbing + selector)

**Files:**
- Create: `app/src/main/res/xml/locales_config.xml`
- Modify: `app/src/main/AndroidManifest.xml` (`android:localeConfig`), `app/build.gradle.kts` (`androidResources { localeFilters += listOf("en", "es") }`), `app/src/main/kotlin/com/revscope/app/MainActivity.kt` (`attachBaseContext` en API < 33), `app/src/main/kotlin/com/revscope/app/di/` (binding `StringResolver` → `AndroidStringResolver`, `@Singleton`)
- Create: `feature/settings/src/main/kotlin/com/revscope/feature/settings/IdiomaSection.kt`
- Modify: `feature/settings/src/main/kotlin/com/revscope/feature/settings/SettingsScreen.kt`, `SettingsSearch.kt` (entrada buscable "Idioma / Language")
- Test: `feature/settings/src/test/kotlin/com/revscope/feature/settings/CapturasIdiomaTest.kt`

**Interfaces:**
- Consumes: `AppLanguage`, `StringResolver` (Task 1), `restartApp(context)` (`feature/settings/RestartApp.kt`), `MatrizCaptura` (Task 2).
- Produces: `@Composable fun IdiomaSection(tagActual: String, onElegir: (String) -> Unit)` — tres opciones: "Sistema"/"System", "English", "Español" (los nombres de idioma se muestran siempre en su propio idioma). `onElegir` = `AppLanguage.persist(context, tag)` y luego `restartApp(context)`; elegir el tag actual no hace nada.

- [ ] **Step 1:** Test de captura `IdiomaSection` en `es` y en `en` (el selector ya extraído a recursos `settings_language_*`) y test JVM: `persist` con el tag actual no llama al reinicio (lambda contadora).
- [ ] **Step 2:** `gradlew.bat :feature:settings:testDebugUnitTest` → FAIL.
- [ ] **Step 3:** Implementar plumbing y sección. La sección va primera dentro del grupo "General" de Ajustes.
- [ ] **Step 4:** `gradlew.bat :app:assembleDebug :feature:settings:testDebugUnitTest` → PASS; `recordRoborazziDebug` solo para las capturas nuevas de `IdiomaSection`.
- [ ] **Step 5:** Commit `feat: selector de idioma (sistema, English, Español) con reinicio`.

### Task 4: guardas `hardcoded.py` y `parity.py`

**Files:**
- Create: `scripts/i18n/hardcoded.py`, `scripts/i18n/parity.py`, `scripts/i18n/test_guards.py`

**Interfaces:**
- Produces: `python scripts/i18n/hardcoded.py <ruta-módulo>…` imprime `ruta:línea: "literal"` y sale 1 si hay alguno; `python scripts/i18n/parity.py <ruta-módulo>…` imprime claves faltantes o con distinto número de `%n$` y sale 1 si hay alguna.
- Reglas de `hardcoded.py`: literal con ≥ 2 letras y (espacio o letra acentuada o `¿¡`) en archivos `src/main/**/*.kt`; ignora líneas con `Log.`, `Timber.`, `TAG`, `// i18n-ignore`, `@Query`, `Regex(`, `http`, literales que son solo una clave en `UPPER_SNAKE` o `lower.dotted`, y archivos bajo `/mcp/`.

- [ ] **Step 1:** `test_guards.py` (unittest) con fuentes en un tmpdir: `Text("Hola mundo")` → 1 hallazgo; `Log.d(TAG, "conectado ok")` → 0; `"velocidad_kmh"` → 0; `Text("Hola") // i18n-ignore` → 0; parity: clave solo en `values` → sale 1; `%1$s` vs `%1$d` → sale 1.
- [ ] **Step 2:** `python -m unittest scripts/i18n/test_guards.py` → FAIL.
- [ ] **Step 3:** Implementar con `re` y `xml.etree` (stdlib).
- [ ] **Step 4:** PASS; correr `python scripts/i18n/hardcoded.py feature/workshop` y anotar el conteo como línea base en el commit.
- [ ] **Step 5:** Commit `chore: guardas de textos sin traducir y paridad de strings.xml`.

---

## Fase 2 — extracción por módulo (en paralelo, un worktree por tarea)

### Contrato común de las Tasks 5-18

Cada tarea es la misma operación sobre un alcance distinto:

1. Crear/llenar `src/main/res/values/strings.xml` (inglés) y `values-es/strings.xml` (español **copiado literal** del código) del módulo, claves con su prefijo.
2. Compose: `stringResource`/`pluralStringResource`. Fuera de Compose: recibir `StringResolver` (constructor inyectado por Hilt en clases; parámetro explícito en funciones de `object`). Números con decimales: `LocaleNumbers.decimal(v, n, resolver.locale)` en vez de `FormatoTaller.numero`/`"%.1f".format(Locale("es"))`.
3. Tests existentes que afirman español: construir el sujeto con `XmlStringResolver.es()` (más los `res` de módulos dependientes cuando hagan falta) y **no cambiar ninguna frase esperada**. Agregar al menos un test que afirme la versión inglesa de la frase más compleja del alcance.
4. Sin cambios de comportamiento ni de redacción española.
5. Verificación (todo debe pasar):
   - `gradlew.bat :<módulo>:testDebugUnitTest`
   - `gradlew.bat :<módulo>:verifyRoborazziDebug` (si el módulo tiene capturas)
   - `python scripts/i18n/hardcoded.py <ruta del alcance>` → 0
   - `python scripts/i18n/parity.py <ruta-módulo>` → 0
   - `gradlew.bat :app:assembleDebug`

**Interfaces de todas:** Consumes `StringResolver`, `AppLanguage`, `LocaleNumbers` (Task 1), `XmlStringResolver`, `MatrizCaptura(idioma)` (Task 2). Produces: claves del módulo; las firmas que ganan un parámetro `resolver: StringResolver` quedan listadas en el mensaje de entrega para que las tareas que las llaman las conozcan.

### Task 5: `:core:common`, `:core:designsystem`, `:core:data`, `:core:maps` + capas de mapa en inglés
Alcance extra: `scripts/gen-map-layers.mjs` acepta `--lang en|es` y genera `core/maps/src/main/assets/map-layers-{light,dark}-en.json`; `MapLayersAssets` elige `-en` cuando `locale.language == "en"`, si no el actual. Test: `MapLayersAssets` con `Locale.US` devuelve el nombre del asset `-en`.

### Task 6: `:core:navigation`
Instrucciones de maniobra y frases de voz. Test inglés: "In 300 meters, turn right" (o su equivalente según la plantilla real).

### Task 7: `:core:intelligence`
Además: cada prompt agrega la línea final de idioma del spec. Test: el prompt de `DtcExplainer` con `XmlStringResolver.en()` termina en `Write your entire answer in English.`; `LocalInfoFetcher` sigue reconociendo `NADA`.

### Task 8: `:core:obd` alertas, servicio y sesión
Alcance: `alerts/`, `service/`, `session/`, `voltage`/`mil`/`aggregator`, notificaciones (`TripSummaryNotifier`, `ObdForegroundService`, canales), `CrashDetector` (SMS). TTS: `tts.language = resolver.locale`, y si `isLanguageAvailable(locale) < TextToSpeech.LANG_AVAILABLE` se deja el idioma por defecto del motor. Test: frase de vuelta en inglés `"Lap 3: 1 minute 12.4"` y en español `"Vuelta 3: 1 minuto 12,4"` con plural correcto para 1 y 2 minutos; test del fallback de TTS con un fake del motor.

### Task 9: `:core:obd` `taller/pruebas/`
Todos los `Textos*.kt`, `CatalogoPruebas`, `Precondicion`, `ControladorPruebaGuiada`. `FormatoTaller.numero` queda para lo que se parsea; lo mostrado pasa por `LocaleNumbers`. Test inglés: `TextosBateria.titulo` de `SOBRECARGA` → `"Overcharging: 15.2 V with the engine running"`.

### Task 10: `:core:obd` `taller/multimetro/`, `taller/referencia/`, `taller/informe/`, `taller/sesion/`, `taller/grafica/`, `taller/pid/`
El informe HTML y el SVG salen en el idioma del resolutor (`<html lang="…">`).

### Task 11: `:core:obd` `taller/dtc/`, `diagnostics/` + guía DTC en inglés
Crear `core/obd/src/main/assets/taller/dtc_guide_en.json` desde `git show access201010/english-uk:core/obd/src/main/assets/taller/dtc_guia_es.json` (mismas claves; textos pasados a inglés de EE. UU.). `BaseConocimientoDtc` elige `dtc_guide_<lang>.json`, cae a `dtc_guide_en.json` y por último a `dtc_guia_es.json`. Test: carga inglés con `Locale.US`; con `Locale.FRENCH` carga inglés.

### Task 12: `:core:obd` resto
`legal/`, `trip/`, `track/`, `motion/`, `rest` de paquetes con texto visible, excepto `mcp/`. `LocalityDetector` no se toca.

### Task 13: `:feature:workshop` `taller/`
### Task 14: `:feature:workshop` resto (Al día, chequeo de salud, mantenimiento, mecánico IA, mezcla, onda O2, Mode 06, odómetro, comparador de velocímetros)
### Task 15: `:feature:settings`
Incluye el índice del buscador de Ajustes (`SettingsSearch`): las entradas buscan en el idioma actual; el test NFD existente sigue en español con `XmlStringResolver.es()` y se agrega un caso inglés (`"crash"` encuentra "Crash detection").
### Task 16: `:feature:session` + `:feature:dtc`
Tarjeta compartible PNG y reporte en el idioma de la app; CSV con `Locale.ROOT`. Test del Review Focus 4: exportar con `Locale("es")` deja `12.5` en la columna numérica.
### Task 17: `:feature:map` + `:feature:vehicle`
### Task 18: `:feature:dashboard`, `:feature:sensors`, `:feature:gear`, `:feature:auto`, `:app` (navegación, onboarding, `AiValueScreen`), `:wear`

---

## Fase 3 — región y unidades

### Task 19: `UnitSystem` y `UnitFormatter`

**Files:**
- Create: `core/common/src/main/kotlin/com/revscope/core/common/units/UnitSystem.kt`, `UnitFormatter.kt`
- Modify: `core/data/src/main/kotlin/com/revscope/core/data/datastore/PreferencesKeys.kt` (`UNIT_SYSTEM`), `feature/settings/` (sección Unidades bajo Idioma)
- Test: `core/common/src/test/kotlin/com/revscope/core/common/units/UnitFormatterTest.kt`

**Interfaces:**
- Consumes: `Region`, `LocaleNumbers` (Task 1).
- Produces:
  - `enum class UnitSystem { METRIC, US, UK; companion object { fun defaultFor(region: Region): UnitSystem } }` — US→US, GB→UK, resto→METRIC.
  - `class UnitFormatter(val system: UnitSystem, val locale: Locale)` con `fun speed(kmh: Double): Double`, `fun speedLabel(): String` ("km/h"/"mph"), `fun distance(km: Double): Double`, `fun distanceLabel()`, `fun temperature(c: Double): Double`, `fun temperatureLabel()`, `fun pressure(kpa: Double): Double`, `fun pressureLabel()`, `fun volume(l: Double): Double`, `fun volumeLabel()`, `fun formatSpeed(kmh: Double): String` y equivalentes `format*` (valor + espacio duro + etiqueta), y `fun toMetricTemperature(value: Double): Double` para umbrales que escribe el usuario.
- Factores fijos: 1 mi = 1.609344 km; °F = °C × 9/5 + 32; 1 psi = 6.894757 kPa; 1 gal US = 3.785411784 L. UK: velocidad/distancia imperiales, temperatura °C, presión psi, volumen litros.

- [ ] **Step 1:** Tests: `formatSpeed(100.0)` en US = `"62 mph"`, en METRIC = `"100 km/h"`; `temperature(100.0)` en US = 212.0; UK `temperatureLabel()` = `"°C"`; `pressure(100.0)` en UK ≈ 14.5; `toMetricTemperature(221.0)` en US = 105.0; `defaultFor(Region.GB) == UK`.
- [ ] **Step 2:** FAIL. **Step 3:** implementar. **Step 4:** PASS. **Step 5:** Commit `feat: sistema de unidades por región con conversión única`.

### Task 20: inventario de unidades (solo lectura)
Delegado de solo lectura que devuelve la lista `archivo:línea` de cada lugar que muestra o anuncia velocidad, distancia, temperatura, presión, volumen, consumo o dinero, agrupada por módulo. Con esa lista se parten las Tasks 20a… (una por módulo grande) con el mismo contrato: convertir con `UnitFormatter` en presentación (gauges con escala convertida, TTS, tarjeta y reporte, mapa/ETA, umbrales de Ajustes, mantenimiento por distancia), dejar base de datos y CSV en métrico. Verificación igual a la Fase 2 más un test por superficie en `UnitSystem.US`.

### Task 21: etiquetas por región y moneda
**Files:** `core/obd/src/main/kotlin/com/revscope/core/obd/legal/DocumentStatusCalculator.kt`, `feature/workshop/.../AlDiaScreen.kt`, `core/obd/.../trip/FuelCostCalculator.kt` (+ `FuelPricePrefs`), recursos de ambos módulos.
- Títulos de documentos por región: CO → "Mandatory insurance (SOAT)", "Roadworthiness inspection (RTM)"; GB → "Insurance", "MOT"; US → "Insurance", "Safety inspection"; OTHER → "Insurance", "Roadworthiness inspection". El español conserva los nombres actuales en CO.
- SIMIT y el botón de copiar placa para SIMIT solo con `Region.CO`.
- Moneda: `Currency.getInstance(Locale("", region.name))` salvo `OTHER` (moneda del locale del sistema); precio por defecto 16000 COP/galón solo en CO; en otras regiones el costo no se muestra hasta que el usuario ponga un precio.
- Tests del Review Focus 5 (CO+inglés muestra SOAT y SIMIT; GB+inglés muestra MOT y oculta SIMIT; US sin precio no muestra costo).

---

## Fase 4 — inglés británico y cierre

### Task 22: superposición `values-en-rGB`
Por módulo, solo las claves cuyo texto británico difiere (licence, tyre, motorbike, petrol, registration, headlamp, colour…), tomando la redacción del fork. `parity.py` ignora `values-en-rGB` en la comprobación de completitud (es parcial a propósito) pero sí valida sus argumentos de formato.

### Task 23: verificación integral
- `gradlew.bat testDebugUnitTest verifyRoborazziDebug :app:assembleDebug :wear:assembleDebug :app:lintDebug` → PASS.
- `python scripts/i18n/hardcoded.py app core feature wear` → 0; `parity.py` → 0.
- Capturas en inglés para el README: test nuevo `app/src/test/.../CapturasReadmeTest.kt` que graba Conducir, Taller, Vehículo al día, Mapa (sin tiles), Reporte y Selector en `idioma = "en"`, `GRANDE`, letra 1.0, a `docs/screenshots/en/`.
- Prueba manual en el teléfono (dueño): cambiar idioma desde Ajustes y desde Ajustes del sistema (Android 13+), voz en inglés, notificación de resumen de viaje, informe HTML.

### Task 24: reglas para el futuro
`CLAUDE.md` del repo: sección "Idiomas" (todo texto nuevo a `strings.xml` en `values/` y `values-es/`, prefijo del módulo, `StringResolver` fuera de Compose, correr las guardas). Commit `docs: regla de idiomas para textos nuevos`.

---

## Orden y carriles

- Tasks 1-4 en secuencia (cimientos; todo lo demás las consume).
- Tasks 5-18 en paralelo, máximo 4 a la vez, cada una en su worktree, vía `mcp__bipolar__delegate` con `verify` = los comandos del contrato y `agent` explícito repartido entre `antigravity`, `copilot`, `cursor` (solo las chicas), `openrouter` (solo las chicas) y `deepseek` como último recurso. Muse vuelve el 2026-11-09; Codex sin créditos.
- Fase 3 después de que las Tasks 8, 14, 16, 17 y 18 estén mergeadas (tocan las mismas superficies).
- Tasks 22-24 al final.
