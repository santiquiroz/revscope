# Alerta de radar direccional — diseño

**Fecha:** 2026-07-17
**Problema:** `SpeedCameraAlerter` alerta por puro radio haversine (400 m). Falsos positivos: cámaras en calles perpendiculares/paralelas y cámaras que ya quedaron atrás.

## Decisión (aprobada por el usuario)

Cono direccional ±60° + chequeo de acercamiento. Sin cambios de datos ni migración Room (el tag `direction` de OSM está casi vacío en Colombia y ANSV no lo trae).

## Regla de alerta

Para cada cámara dentro de 400 m, se alerta solo si TODAS se cumplen:

1. Hay rumbo GPS válido (`location.hasBearing()`), es decir, el vehículo se mueve. Parado o sin rumbo → no alerta.
2. La diferencia angular entre el rumbo GPS y el bearing inicial hacia la cámara es ≤ 60°. Cámara atrás (~180°) y calle perpendicular (~90°) quedan excluidas.
3. La distancia a esa cámara disminuyó respecto al fix anterior (se necesita un fix previo dentro del radio; el primer fix solo registra distancia). Con fixes cada ~1 s esto retrasa la alerta ~1 s, aceptable.

Al salir del radio de 400 m se borra la distancia registrada de esa cámara, para que una re-entrada empiece limpia.

Cooldown por cámara (120 s) y gate de voz existentes no cambian.

## Componentes

- `TripStatsCalculator.initialBearingDegrees(lat1, lon1, lat2, lon2)` — bearing inicial de círculo máximo, 0–360°. Matemática geo pura junto al haversine existente.
- `cameras/CameraApproachGate.kt` — objeto puro con `shouldAlert(headingDeg, bearingToCameraDeg, previousDistanceM, distanceM)` y diferencia angular envolvente. Sin dependencias Android → unit-testable.
- `SpeedCameraAlerter.onGpsFix(lat, lon, headingDeg: Float?)` — firma ampliada; mantiene mapa `lastDistanceM` por `osmId`; delega la decisión al gate.
- `GpsTrackRecorder.onLocation` — pasa `location.bearing` si `hasBearing()`, sino `null`.

## Pruebas

- `TripStatsCalculatorTest`: bearing norte=0°, este=90°, sur=180°, oeste=270°, par de referencia real.
- `CameraApproachGateTest`: sin rumbo → no; atrás (Δ≈180°) → no; perpendicular (Δ≈90°) → no; dentro del cono pero alejándose → no; primer fix (sin distancia previa) → no; dentro del cono y acercándose → sí; envolvente 350° vs 10° cuenta como Δ=20°.
