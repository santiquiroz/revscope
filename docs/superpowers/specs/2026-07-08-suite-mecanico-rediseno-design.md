# Diseño: Suite Mecánico, Rediseño de Navegación, Mapa en Vivo y Energía

Fecha: 2026-07-08
Estado: aprobado en conversación, pendiente revisión final del spec

## Contexto

RevScope creció como app enthusiast/racing. Feedback de mecánico real (Diego): los datos
de mezcla y combustión "se tiran crudos" — un mecánico necesita interpretación, no bytes.
Además la navegación actual entierra funciones dentro de Ajustes (Modo Pista, Escáner
Mode 22, Analizador de marchas, Perfiles) y el usuario reportó que la app siguió corriendo
en segundo plano una hora después de apagar la moto, reintentando conexión.

## Objetivos

1. Rediseñar la navegación: 5 pestañas por intención de uso, nada funcional dentro de Ajustes.
2. Sección **Taller**: suite de diagnóstico para mecánicos con interpretación en español.
3. Sección **Mapa**: mapa en vivo con posición, ruta y radares.
4. **Energía**: fin de viaje automático — cero consumo tras apagar el vehículo.
5. Extras aprobados: costo del viaje en COP, mantenimiento por km, eco-score, backup/restore.

Fuera de alcance (documentado para después): onda O2 graficada, pruebas guiadas,
Mode 06 (misfires por cilindro), navegación turn-by-turn propia, detección de caída.

---

## 1. Rediseño de navegación

Barra inferior con 5 pestañas:

| Tab | Ruta | Contenido |
|---|---|---|
| 🏁 Conducir | `dashboard` | Dashboard actual + botón prominente a Modo Pista |
| 🗺️ Mapa | `map` | NUEVO — mapa en vivo (sección 3) |
| 🔧 Taller | `workshop` | NUEVO — hub de diagnóstico (sección 2) |
| 📊 Viajes | `sessions` | Historial/reportes/comparar (sin cambios) |
| ⚙️ Ajustes | `settings` | Solo configuración |

Movimientos:
- `Dtc` y `Sensors` salen de la barra inferior → cards dentro de Taller.
- `TrackMode` sale de Ajustes → botón en Conducir.
- `Mode22Scanner`, `GearAnalyzer`, `VehicleProfile` salen de Ajustes → cards en Taller
  (Perfiles queda accesible también desde Ajustes, es configuración).
- Ajustes queda: adaptador/conexión, perfiles, alertas, radares, PIDs custom, API key,
  precio de gasolina (nuevo), mantenimiento (nuevo), backup (nuevo).

Chip de conexión persistente: componente `ConnectionChip` en top bar de las 5 pestañas
(estado + nombre del adaptador; toca → pantalla de adaptadores).

### Hub Taller (`WorkshopScreen`)

Lista de cards grandes, orden por frecuencia de uso mecánico:
1. **Chequeo de salud** — botón de escaneo de un toque (protagonista)
2. **Códigos de falla (DTC)** — pantalla existente
3. **Mezcla y combustión en vivo** — nueva
4. **Gráficas de sensores** — pantalla existente
5. **Escáner avanzado (Mode 22)** — existente
6. **Analizador de marchas** — existente

Cada card: icono, título, descripción de una línea. Deshabilitadas (gris + "requiere
conexión") cuando no hay adaptador conectado, excepto Chequeo de salud que muestra el
último informe guardado.

---

## 2. Taller Fase 1

### 2.1 PIDs nuevos en `pids_mode01.json`

| PID | Nombre ES | Fórmula | Unidad |
|---|---|---|---|
| 08 | Fuel Trim Corto B2 | `(A-128)*100/128` | % |
| 09 | Fuel Trim Largo B2 | `(A-128)*100/128` | % |
| 0A | Presión de Combustible | `A*3` | kPa |
| 0E | Avance de Encendido | `A/2-64` | ° |
| 15 | Sensor O2 B1S2 | `A/200` | V |
| 18 | Sensor O2 B2S1 | `A/200` | V |
| 19 | Sensor O2 B2S2 | `A/200` | V |
| 2E | Purga EVAP Comandada | `A*100/255` | % |
| 3C | Temp Catalizador B1S1 | `((A*256)+B)/10-40` | °C |
| 44 | Lambda Comandado | `((A*256)+B)/32768` | λ |

Prioridad 4 (nueva): solo se sondean cuando una pantalla de Taller está activa — no
entran al ciclo normal de telemetría (no afectan latencia del dashboard ni batería).
`PidScheduler` gana un flag `workshopMode` que activa/desactiva el grupo 4.
El bitmap de soportados ya filtra por vehículo (moto muestra 1 banco, carro todo).

### 2.2 Monitores de readiness (Mode 01 PID 01)

Nuevo parser en `core/obd/protocol`: `ReadinessParser`.
- Byte A: bit 7 = MIL encendida, bits 0-6 = cantidad de DTCs.
- Byte B: monitores continuos (misfire, fuel system, components) — bits de soporte y estado.
- Bytes C/D: monitores no continuos según tipo de motor (chispa vs compresión).
Salida: `data class ReadinessStatus(milOn, dtcCount, monitors: List<MonitorResult>)` con
`MonitorResult(nombre, soportado, completo)`.

### 2.3 Motor de interpretación (`core/obd/workshop/DiagnosticRules.kt`)

Funciones puras, sin Android, 100% testeables. Cada regla devuelve
`Diagnosis(nivel: OK|ATENCION|FALLA, titulo, causaProbable)`:

| Señal | Regla | Diagnóstico |
|---|---|---|
| LTFT | dentro de ±10% | OK |
| LTFT | +10..+25% | Mezcla pobre — fugas de vacío, inyectores sucios, MAF sucio |
| LTFT | −10..−25% | Mezcla rica — inyector goteando, presión de combustible alta, MAF |
| LTFT | fuera de ±25% | Falla — el ECU no logra compensar |
| STFT+LTFT combinado | \|suma\| > 15% | Corrección total excesiva |
| O2 upstream | fijo <0.2V o >0.8V por >30 muestras | Sensor perezoso o mezcla extrema |
| O2 downstream | oscila igual que upstream | Catalizador degradado (informativo) |
| Voltaje | <11.8V apagado / <13.2V en marcha | Batería / alternador |
| Refrigerante | no alcanza 75°C en 10 min | Termostato pegado abierto |
| Refrigerante | >105°C | Sobrecalentamiento |
| Readiness | monitor soportado incompleto | "No listo para tecnomecánica" |
| MIL | encendida | Falla activa — revisar DTCs |

### 2.4 Chequeo de salud (`HealthCheckScreen` + `HealthCheckViewModel`)

Flujo de un toque:
1. Lee DTCs (modos 03, 07, 0A) — comandos existentes.
2. Lee readiness (01 01).
3. Muestrea 10s de: trims (06-09), O2 (14/15/18/19), voltaje, temperatura.
4. Corre `DiagnosticRules` sobre todo.
5. Presenta lista de resultados con semáforo 🟢🟡🔴 + causa probable en español.
6. Botón "Explicar con IA" por ítem (reusa infra Claude de DTC).
7. Botón 📷 comparte informe como imagen (patrón `TripShareCard` → `HealthReportCard`:
   1080×1350, datos del vehículo del perfil activo, fecha, resultados, footer con logo).

Persistencia: tabla `health_reports` (id, vehicleProfileId, timestamp, resultsJson) —
el hub muestra "último chequeo: hace 3 días, 2 advertencias".

### 2.5 Mezcla y combustión en vivo (`LiveMixtureScreen`)

Activa `workshopMode` en el scheduler al entrar, lo desactiva al salir.
Muestra cada señal con valor + barra de rango + diagnóstico inline de `DiagnosticRules`:
trims por banco, O2 por sensor, MAF/MAP, lambda comandado, avance, EVAP, temp catalizador.
Solo lo que el vehículo soporta.

---

## 3. Mapa en vivo (`feature/map`)

`LiveMapScreen` con osmdroid (dependencia ya presente):
- Posición actual con marcador orientado al bearing GPS.
- Polyline de la ruta del viaje activo dibujándose en tiempo real (fuente:
  mismos fixes GPS del `GpsTrackRecorder` — se expone `StateFlow<List<GeoPoint>>`
  desde un `LiveRouteHolder` @Singleton que el recorder alimenta).
- Radares descargados como marcadores con círculo de 400m.
- Velocidad actual grande (overlay abajo).
- Botón "Navegar a…" → intent `google.navigation:q=` (elige Maps/Waze el sistema).
- Sin viaje activo: mapa centrado en última ubicación conocida + radares.
- Atribución "© OpenStreetMap contributors" obligatoria.

Sin APIs nuevas, sin keys. Tiles se cachean (offline parcial gratis).

---

## 4. Energía: fin de viaje automático

### 4.1 Detección de motor apagado (`EngineOffDetector` en `core/obd/session`)

Clasifica la pérdida de enlace o inactividad:
- **Motor apagado**: (RPM sin lecturas o = 0) Y (velocidad OBD/GPS = 0) sostenido 60s,
  O pérdida de enlace con velocidad 0 en los últimos 30s.
- **Falla en movimiento**: pérdida de enlace con velocidad > 0 reciente → reconexión.

### 4.2 Política de reconexión con tope

Reemplaza el bucle fijo actual (15s × 12):
- Backoff: 15s → 30s → 60s → 60s (máx ~3 min total, cubre semáforos y paradas cortas).
- Si `EngineOffDetector` dice "motor apagado" → ni un solo reintento.
- Al agotar: cierre limpio total.

### 4.3 Cierre limpio

1. `SessionRecorder` flush final + agregados (ya existe con NonCancellable).
2. Detiene GPS, IMU, alerter de radares, `ObdForegroundService`.
3. Notificación resumen (no-ongoing, dismissable): "Viaje guardado: 23,4 km · 82 km/h máx
   · 34 min · $4.200" → tap abre el reporte (deep link `session_detail/{id}`).
4. Reconectar después = abrir la app (el auto-reconnect al abrir ya existe).

Riesgo controlado: paradas largas (almuerzo) cierran el viaje — correcto, son dos viajes.

---

## 5. Extras

### 5.1 Costo del viaje en COP

- Ajustes: campo "Precio galón (COP)" (default 16.000, DataStore).
- Consumo: integración trapezoidal del PID 5E (L/h) sobre el tiempo del viaje → litros
  → galones (÷3.785) → COP. Si 5E no está soportado: estimación por MAF
  (`gramos_aire/14.7/750 g/L`) y se marca "estimado". Sin MAF ni 5E: no se muestra.
- Se guarda en agregados de sesión, aparece en reporte, tarjeta compartible y
  notificación resumen.

### 5.2 Mantenimiento por kilometraje

- `vehicle_profiles` gana `odometerBaseKm` (editable — el usuario pone el odómetro real).
- Odómetro app = base + suma de distancias de sesiones del perfil.
- Tabla `maintenance_items` (id, vehicleProfileId, nombre, intervaloKm, ultimoServicioKm).
- Defaults al crear (editables): aceite 3.000 km, kit de arrastre 15.000 km (moto),
  refrigerante 20.000 km, llantas revisión 10.000 km.
- Card en Taller: "Mantenimiento — próximo: aceite en 420 km". Vencido → 🔴 + aviso
  al abrir la app (banner, no TTS).
- Botón "Registrar servicio" → actualiza `ultimoServicioKm` al odómetro actual.

### 5.3 Eco-score por viaje

`EcoScoreCalculator` (funciones puras) sobre datos ya grabados, nota 0-100:
- Aceleradas bruscas (|accel longitudinal| > 3 m/s² del IMU): −2 c/u.
- Frenadas bruscas (< −4 m/s²): −3 c/u.
- Tiempo con RPM > 80% de la línea roja del perfil: −1 por cada 30s.
- Velocidad sostenida estable (cruise): bonus hasta +10.
Se guarda en agregados, se muestra en reporte con desglose ("−12 por 4 frenadas bruscas").

### 5.4 Backup / restore

- Exportar: `Room checkpoint (wal_checkpoint(TRUNCATE))` → zip de `revscope.db` +
  DataStore prefs (JSON) → SAF `CreateDocument` (`revscope-backup-AAAA-MM-DD.zip`).
- Importar: SAF `OpenDocument` → valida que el zip contenga `revscope.db` con schema
  compatible (misma versión o inferior — Room migra al abrir) → cierra la BD, reemplaza
  archivo, reinicia proceso (`ProcessPhoenix`-style o pide reinicio manual).
- La API key cifrada NO se exporta (EncryptedSharedPreferences no es portable entre
  dispositivos; se documenta en la UI).
- Sección "Copia de seguridad" en Ajustes.

---

## 6. Cambios de datos (Room v9 → v10)

- Nueva tabla `health_reports`.
- Nueva tabla `maintenance_items`.
- `vehicle_profiles`: + `odometerBaseKm REAL DEFAULT 0`.
- `sessions` (agregados): + `fuelCostCop REAL NULL`, + `fuelLiters REAL NULL`,
  + `ecoScore INTEGER NULL`.
- Migración destructiva NO — usuario tiene 107 km de datos reales. Migración manual
  `MIGRATION_9_10` con `ALTER TABLE` / `CREATE TABLE`.

## 7. Testing

- `DiagnosticRulesTest`: cada regla con casos borde (unit, puras).
- `ReadinessParserTest`: payloads reales de moto (1 banco) y carro (2 bancos, diésel).
- `EngineOffDetectorTest`: apagado real vs semáforo vs falla en movimiento.
- `EcoScoreCalculatorTest`, `FuelCostCalculatorTest` (trapezoidal + fallback MAF).
- `MIGRATION_9_10` test con Room testing.
- UI manual en hardware real (Apache + adaptador Vgate).

## 8. Orden de implementación propuesto

1. Rediseño de navegación + hub Taller (destrabar el resto).
2. Energía / fin de viaje automático (bug activo, quema batería hoy).
3. Taller Fase 1 (PIDs, readiness, reglas, chequeo, mezcla en vivo, tarjeta).
4. Mapa en vivo.
5. Extras: costo COP → eco-score → mantenimiento → backup.
