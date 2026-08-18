# Onboarding wizard — sub-proyecto D (2026-08-18)

Cuarto de cuatro sub-proyectos (A mapa base → B UX navegación → C tipo de vehículo → D onboarding). Depende de C (defaults por tipo de vehículo) y se beneficia de A (auto-descarga de radares elimina un paso).

## Problema

Instalación limpia sin adaptador OBD ni key de IA es engorrosa (las cuatro fricciones reportadas):

1. Radares exigen descubrir Ajustes → Radares → botón descargar. (Lo resuelve A; aquí no se toca.)
2. No hay camino claro "usar sin adaptador": el dashboard abre con gauges muertos dominando la pantalla.
3. Config de IA en Ajustes es un muro (provider/key/modelo/URL) y nadie explica qué se gana con la key.
4. Crear perfil de vehículo es invisible: el `VehiclePickerSheet` solo aparece si ya existen perfiles (`vehicleProfiles.isNotEmpty()`), así que el usuario nuevo nunca lo ve.

El onboarding actual es una sola pantalla de permisos con "Empezar" incondicional.

## Diseño

### D1. Wizard multi-paso

Reemplaza `OnboardingScreen` por un wizard (pager) de 5 pasos. **Todos skippables** — el gate `ONBOARDING_DONE` se marca al completar o saltar el flujo, como hoy.

1. **Bienvenida + permisos** — las 3 cards actuales (Ubicación, Notificaciones, Bluetooth) como paso 1.
2. **Tu vehículo** — form mínimo embebido: nombre + chip Auto/Moto + placa opcional. Crea el primer perfil aplicando los defaults por tipo de C (maxRpm/redline/gearCount). No expone PIDs, VIN, ratios ni fechas legales — eso queda en el form completo de Ajustes.
3. **¿Tenés adaptador OBD2?**
   - Sí → navegar a `AdapterScan` (o "configurar después").
   - No → **modo GPS explícito**: pantalla que enumera qué funciona sin adaptador (mapa + radares automáticos, viajes GPS con telemetría IMU, detección de caída, pico y placa, historial). Persiste preferencia `GPS_ONLY_MODE`.
4. **IA opcional — propuesta de valor primero**: qué ganás con una key —
   - chat mecánico especializado en TU vehículo,
   - debrief IA al final de cada viaje,
   - pico y placa por IA en cualquier ciudad,
   - explicación de códigos de falla (DTC).
   Luego: provider default Gemini + campo key + link "obtener key gratis" (Google AI Studio) + botón "Probar" + "después". Sin modelo custom ni base URL (eso sigue en Ajustes).
5. **Listo** → dashboard.

### D2. Dashboard en modo GPS

Con `GPS_ONLY_MODE` activo (elegido en paso 3 o inferido por ausencia de adaptador configurado):

- Jerarquía invertida: botón "Viaje GPS" y acceso al mapa protagonistas arriba.
- Gauges dimmed agrupados abajo con una sola CTA "Configurar adaptador" (→ `AdapterScan`), no N gauges muertos dominando.
- Configurar un adaptador desactiva el modo automáticamente.

### D3. IA vendida en contexto

- Cada superficie donde una feature IA degrada ("Sin API key, esas funciones se muestran sin explicación de IA") gana botón "Configurar IA" que abre la misma pantalla de valor del paso 4 (reutilizable como destino de navegación propio).
- El wizard completo es re-ejecutable desde Ajustes → "Volver a ver configuración inicial" (resetea solo la navegación, no los datos).

## Errores

- Key IA inválida en "Probar": mensaje inline, no bloquea avanzar.
- Permisos denegados: el paso avanza igual (como hoy); el mapa tiene su propio banner (A5).
- Salir del wizard a mitad: `ONBOARDING_DONE` se marca; lo configurado queda, lo saltado queda pendiente.

## Archivos

| Archivo | Cambio |
|---|---|
| `app/.../onboarding/OnboardingScreen.kt` | → wizard pager 5 pasos |
| `app/.../onboarding/OnboardingViewModel.kt` | máquina de pasos, creación de perfil, guardado key |
| `app/.../onboarding/AiValueScreen.kt` | nuevo — pantalla de valor IA reutilizable |
| `app/.../navigation/RevScopeNavGraph.kt` | ruta AiValue, re-run wizard |
| `feature/dashboard/.../DashboardScreen.kt` | layout modo GPS |
| `feature/settings/.../SettingsScreen.kt` | entry re-run wizard, CTA "Configurar IA" en copy de degradación |
| `core/data/.../PreferencesKeys.kt` | `GPS_ONLY_MODE` |
| Superficies con degradación IA (workshop chat, debrief, DTC) | botón "Configurar IA" |

## Tests

- Máquina de estados del wizard: avanzar, saltar cada paso, salir a mitad → `ONBOARDING_DONE` correcto.
- Paso vehículo crea perfil con defaults por tipo (moto 12000/10500/5, auto 8000/6500/6).
- Paso adaptador "No" persiste `GPS_ONLY_MODE`; configurar adaptador después lo desactiva.
- Key guardada vía `SecureKeyStore` y `testAiConnection` invocado por "Probar".
- Deep-link "Configurar IA" desde una superficie degradada abre `AiValueScreen`.

## Fuera de alcance

Cuentas/login, tour guiado del mapa, onboarding de Wear/Android Auto, migración de usuarios existentes al wizard (solo lo ven instalaciones nuevas o re-run manual).
