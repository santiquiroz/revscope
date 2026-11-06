# RevScope multi-idioma (inglés + español), región y unidades — diseño

Fecha: 2026-11-06 · Rama: `feat/i18n` (sale de `feat/perfiles-can-a`, que aún espera prueba en la moto) · Autor de las decisiones: Claude (el dueño pidió saltar las preguntas; todas las decisiones de abajo son de Claude y el dueño puede revertirlas).

## Por qué

- Un usuario del Reino Unido (`access201010`) hizo un fork y tradujo la app al inglés reemplazando el español directamente en el código (rama `english-uk`, 146 archivos, 17-18 oct). Hay interés real fuera de Colombia y hoy la única forma de usar RevScope en inglés es perder el español.
- El dueño quiere soporte multi-idioma real y el README principal en inglés.
- Competencia: en la última semana salieron en r/OBD2 dos apps GPL para ELM327 (OpenOBD, un tablero para adaptadores baratos). Son más simples que RevScope, pero están en inglés. RevScope en español solo es invisible para el resto del mundo.

## Metas

1. Toda la interfaz, la voz (TTS), las notificaciones, las tarjetas PNG, el informe HTML del taller, el SMS de caída y las respuestas de la IA salen en el idioma de la app.
2. Idiomas: inglés (por defecto, `values/`) y español (`values-es/`). Inglés de EE. UU. en `values/`; superposición `values-en-rGB` con las diferencias británicas tomadas del fork.
3. Selector de idioma dentro de la app (Sistema / English / Español). Sin elegir nada, se sigue el idioma del teléfono: `es-*` → español, cualquier otro → inglés.
4. Unidades según la región: EE. UU. → mph, millas, °F, psi, galón US; Reino Unido → mph, millas, °C, psi, litros; resto → km/h, km, °C, kPa, litros. El usuario puede cambiarlas.
5. Lo colombiano sigue entero para Colombia y se muestra con nombres genéricos al resto: SOAT → "Mandatory insurance (SOAT)", tecnomecánica → "Roadworthiness inspection (MOT / safety inspection)", SIMIT solo en región CO. El costo de combustible usa la moneda de la región; el precio por defecto de 16 000 COP/galón solo existe en región CO.
6. Agregar un idioma nuevo = agregar `values-xx/strings.xml` en cada módulo + `dtc_guide_xx.json` + capas de mapa; nada más.

## No metas

- Traducir las descripciones y salidas de las tools MCP (las lee un modelo de lenguaje, el idioma no le importa). Queda en backlog.
- Traducir los encabezados de los CSV exportados ni las claves de JSON/DataStore/Room.
- Plataforma de traducción comunitaria (Weblate/Crowdin): se documenta cómo traducir, la cuenta la abre el dueño si quiere.
- Más idiomas además de inglés y español en esta entrega.

## Anti-metas (si pasa esto, falló aunque compile)

- Cualquier texto en español que cambie de redacción: el español sale **idéntico** al actual. Las 444 capturas Roborazzi en español deben seguir pasando `verifyRoborazziDebug` sin regrabar.
- Lógica que dependa del texto y se rompa al traducir (centinelas de la IA como `NADA`, nombres de ciudad que devuelve el Geocoder para pico y placa, formatos que se parsean de vuelta).
- Un CSV con coma decimal por culpa del idioma.
- Mezclar idiomas en una misma pantalla después de cambiar el idioma.

## Arquitectura

### Recursos y claves

- Cada módulo tiene `src/main/res/values/strings.xml` (inglés) y `src/main/res/values-es/strings.xml` (español actual, literal). `android.nonTransitiveRClass=true`: cada módulo usa su propio `R`.
- Claves en snake_case con prefijo de módulo para que la fusión de recursos nunca pise una clave de otro módulo:

| Módulo | Prefijo |
|---|---|
| `:app` | `app_` |
| `:core:common` | `common_` |
| `:core:designsystem` | `ds_` |
| `:core:data` | `data_` |
| `:core:maps` | `maps_` |
| `:core:navigation` | `nav_` |
| `:core:intelligence` | `ai_` |
| `:core:obd` | `obd_` |
| `:feature:dashboard` | `drive_` |
| `:feature:map` | `map_` |
| `:feature:workshop` | `workshop_` |
| `:feature:session` | `trips_` |
| `:feature:vehicle` | `vehicle_` |
| `:feature:settings` | `settings_` |
| `:feature:dtc` | `dtc_` |
| `:feature:sensors` | `sensors_` |
| `:feature:gear` | `gear_` |
| `:feature:auto` | `auto_` |
| `:wear` | `wear_` |

- Argumentos posicionales (`%1$s`, `%2$d`). Cantidades con `<plurals>` (`one`/`other`). Los números se formatean antes de pasar como `%s` con el locale del resolutor (ver abajo), salvo enteros simples (`%d`).
- Apóstrofos y comillas escapados según las reglas de Android (`\'`, `\"`).

### Resolución de textos fuera de Compose: `StringResolver`

El dominio (`core:obd` taller, alertas, legal…) arma frases con datos (`TextosBateria`, `DocumentStatusCalculator`, `AlertsEngine`). Decisión: siguen devolviendo `String`, pero reciben un `StringResolver` explícito en lugar de tener el español escrito.

```kotlin
interface StringResolver {
    val locale: Locale
    fun get(@StringRes id: Int, vararg args: Any): String
    fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): String
}
```

- Producción: `AndroidStringResolver` (`@Singleton`, Hilt) resuelve contra un `Context` configurado con el idioma de la app (`createConfigurationContext`). Sirve igual en Activity, Service, Worker, TTS y notificaciones, en cualquier API.
- Tests JVM: `XmlStringResolver` lee los `strings.xml` reales del módulo desde disco. Los tests que hoy afirman frases en español pasan `XmlStringResolver.es(...)` y siguen afirmando el mismo español: verifican el recurso real, no una copia.
- Compose usa `stringResource` / `pluralStringResource` directamente.
- Por qué no `UiText` perezoso: cambia el tipo de cientos de funciones y obliga a reescribir 67 archivos de tests. Con el resolutor el cambio es mecánico y los tests no cambian de significado. El costo (texto ya calculado en un ViewModel queda en el idioma viejo) se elimina reiniciando la app al cambiar de idioma.

### Idioma de la app: `AppLanguage`

- Preferencia propia en `SharedPreferences` (`revscope_language`, clave `tag`: `""` = sistema, `"en"`, `"es"`), escrita con `commit()` antes de reiniciar.
- API 33+: además se fija `LocaleManager.applicationLocales` (así Ajustes del sistema → Idioma de la app también funciona; si el usuario lo cambió desde el sistema, `LocaleManager` manda). `res/xml/locales_config.xml` con `en` y `es`, `android:localeConfig` en el manifest.
- API 26-32: `MainActivity.attachBaseContext` envuelve el contexto con el idioma guardado. Sin AppCompat (no se agrega dependencia).
- Cambiar el idioma = guardar + reiniciar el proceso con el `restartApp` existente.
- `androidResources.localeFilters` = `en`, `es` en `:app` para no empaquetar traducciones de bibliotecas en idiomas que la app no tiene.

### Región

`Region { CO, US, GB, OTHER }` sale del país del locale del sistema (`Resources.getSystem().configuration.locales[0].country`), no del idioma elegido: un colombiano con la app en inglés sigue siendo CO. No hay ajuste de región; las unidades y la moneda sí se pueden cambiar a mano.

### Voz, IA, geocoder

- TTS: `tts.language = resolver.locale`; si el motor no tiene ese idioma (`isLanguageAvailable` < `LANG_AVAILABLE`), usa el locale por defecto del motor en vez de callar.
- IA: el texto de cada prompt no cambia; al final se agrega una línea con el idioma de respuesta (`Write your entire answer in English.` / `Escribe toda tu respuesta en español.`). Los centinelas que se parsean (p. ej. `NADA` en `LocalInfoFetcher`) se mantienen tal cual.
- `LocalityDetector` sigue llamando al Geocoder con `es-CO`: su salida alimenta la detección de ciudad de pico y placa, no se muestra.

### Mapa y DTC

- `scripts/gen-map-layers.mjs` genera también `map-layers-{light,dark}-en.json` (`lang: "en"`); `MapLayersAssets` elige por idioma con caída a la versión en español.
- `dtc_guide_en.json` junto a `dtc_guia_es.json` (base: la traducción del fork); `BaseConocimientoDtc` elige por idioma y cae al inglés si falta el archivo del idioma.

### Unidades y moneda

- `UnitSystem { METRIC, US, UK }` en DataStore (`UNIT_SYSTEM`; sin valor = por región). Un único `UnitFormatter` en `core:common` convierte y formatea velocidad, distancia, temperatura, presión, volumen y consumo; nadie más multiplica por 0,621371.
- Se convierte en la capa de presentación: la base de datos, los cálculos y los CSV siguen en métrico.
- Los umbrales que el usuario escribe (temperatura de alerta) se muestran y se editan en su unidad y se guardan en métrico.
- Moneda: `Currency.getInstance(Locale("", region))` salvo override del usuario; precio del combustible por litro o por galón según `UnitSystem`.

### Guardas contra regresiones

- `scripts/i18n/hardcoded.py <módulo…>`: lista literales de Kotlin en `src/main` que parecen texto para el usuario. Excluye logs, claves, rutas, regex, SQL, URLs y líneas con `// i18n-ignore`. Sale con 1 si encuentra alguno.
- `scripts/i18n/parity.py`: toda clave de `values/` existe en `values-es/` y al revés, con los mismos argumentos de formato.
- `CLAUDE.md` del repo: regla "todo texto nuevo va a `strings.xml` en los dos idiomas".

## Glosario (inglés de EE. UU.)

| Español | English | Nota |
|---|---|---|
| Conducir | Drive | pestaña |
| Mapa | Map | pestaña |
| Taller | Workshop | pestaña |
| Viajes | Trips | pestaña |
| Ajustes | Settings | pestaña |
| Vehículo al día | Vehicle status | GB: igual |
| Garaje | Garage | |
| Perfil (de vehículo) | Vehicle profile | |
| Adaptador | Adapter | |
| Viaje GPS | GPS trip | |
| Modo Pista | Track mode | |
| Vuelta | Lap | |
| Marcha | Gear | |
| Zona roja | Redline | |
| Chequeo de salud | Health check | |
| Código de falla / DTC | Trouble code (DTC) | |
| Testigo de motor (MIL) | Check engine light (MIL) | |
| Mezcla | Fuel mixture | |
| Sensor O2 / onda O2 | O2 sensor / O2 waveform | |
| Readiness | Readiness monitors | |
| Mínimo | Idle | |
| Rpm altas | Higher revs | |
| Contacto puesto | Ignition on | |
| Moto | Motorcycle | GB: motorbike |
| Carro | Car | |
| Placa | License plate | GB: registration |
| Licencia de conducción | Driver's license | GB: driving licence |
| SOAT | Mandatory insurance (SOAT) | solo región CO lleva "(SOAT)" |
| Tecnomecánica (RTM) | Roadworthiness inspection | US: safety inspection; GB: MOT |
| Todo riesgo | Comprehensive insurance | |
| Pico y placa | Pico y placa (driving restriction) | nombre propio |
| Radar / fotomulta | Speed camera | |
| Detección de caída | Crash detection | |
| Mecánico IA | AI mechanic | |
| Llanta | Tire | GB: tyre |
| Combustible / gasolina | Fuel / gas | GB: petrol |
| Corriente / Extra / Diésel | Regular / Premium / Diesel | |
| Kit de arrastre | Chain and sprockets | |
| Farola | Headlight | GB: headlamp |
| Pata lateral | Side stand | GB: side stand |
| Rodada en grupo / sala | Group ride / room | |

Tono: frases cortas, segunda persona, sin signos de exclamación salvo los que ya existen. Las unidades llevan espacio duro como hoy.

## Lo que queda a criterio de quien implementa

- Partir o no un `strings.xml` grande en varios archivos (`strings_workshop_tests.xml`…).
- Nombres exactos de las claves dentro del prefijo.
- Redacción inglesa cuando el glosario no cubre un término, partiendo de la traducción del fork cuando el archivo existe allí (`git show access201010/english-uk:<ruta>`), pasada a inglés de EE. UU.
