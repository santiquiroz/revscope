# Catálogo propietario: fuentes y hallazgos (2026-10-02)

Archivo generado: `core/obd/src/main/assets/catalogo_propietario.json` (461 entradas, todas de tipo `lectura`, todas `verificado=false`).

Regla aplicada: ninguna entrada sin URL pública donde aparezca el identificador exacto. Todas las entradas salen de OBDb (JSON por marca/modelo) y se convirtieron por script: `bix/len/mul/div/add/sign` de OBDb pasan a fórmula exp4j sobre los bytes A, B, C... (A = primer byte tras el eco del DID). Los bits sueltos se expresan con `floor(X/2^k)%2`; los valores con signo con `raw-2^n*floor(raw/2^(n-1))`.

## Conteo

| Vehículo | Módulo | Lecturas | Escrituras | Rutinas |
|---|---|---|---|---|
| Mazda CX-30 | PCM (7E0) | 251 | 0 | 0 |
| Mazda CX-30 | TCM (7E1) | 12 | 0 | 0 |
| TVS Apache RTR 160 4V | n/a | 0 | 0 | 0 |
| Renault (genérico de marca, no confirmado en Kardian) | ECM (7E0) | 92 | 0 | 0 |
| Nissan (genérico de marca, no confirmado en March K13) | ECM (7E0) | 53 | 0 | 0 |
| Nissan (genérico de marca) | TCM (7E1, CVT) | 53 | 0 | 0 |

## Licencia: aviso importante

OBDb publica su texto bajo **CC-BY-SA-4.0** (verificado en la API de GitHub: `spdx_id: CC-BY-SA-4.0` en los repos `OBDb/Mazda-CX-30`, `OBDb/Mazda-3`, `OBDb/Renault`, `OBDb/Nissan`; README de OBDb sección "Copyrights"). CC-BY-SA permite redistribuir y adaptar con atribución y misma licencia (ShareAlike). Por tanto:

- `catalogo_propietario.json` es una obra derivada: debe llevar atribución a OBDb (https://github.com/OBDb) y quedar bajo CC-BY-SA-4.0, aunque el resto de RevScope sea Apache-2.0. Conviene tratarlo como un asset de datos separado, con aviso en el README/NOTICE.
- Si el proyecto no quiere esa condición, la alternativa es no empaquetar el archivo y descargarlo/consultarlo de OBDb en tiempo de ejecución. Decisión del dueño.

## Mazda CX-30 (PCM 7E0 / 7E8)

- Fuente: https://github.com/OBDb/Mazda-CX-30/blob/main/signalsets/v3/default.json (CC-BY-SA-4.0). 262 comandos: 220 en 7E0 (PCM), 12 en 7E1 (TCM), el resto en 720 (tablero), 723, 726, 760 y 7E6.
- El PR #869 de OBDb indica que el CX-30 se sembró con las lecturas de motor/carrocería/chasis del Mazda3 (capturas 2020-2026): https://github.com/OBDb/Mazda-CX-30/pull/869. Por eso el alcance de modelo es "CX-30, datos compartidos con Mazda3".
- Muchos PIDs están marcados `dbg: true` en OBDb (decodificación no confirmada); se indica en `notas`. También hay filtros de año (`dbgfilter`) que no se trasladaron; `anios` se dejó en 2020-2024.
- Algunas señales son de turbo (boost, wastegate, VGT) y no aplican al Skyactiv-G atmosférico; el catálogo las conserva porque están en la fuente, pero el vehículo real simplemente no responderá o dará valores nulos.
- Útiles para el dueño: vida de aceite restante, distancia desde cambio de aceite, eventos de dilución de aceite, elongación de cadena de distribución, ángulo aprendido del acelerador electrónico, contadores de fallos de encendido por cilindro.
- Excluidos: señales de los módulos 720/723/726/760/7E6 porque OBDb no nombra el módulo y no se quiso inventar un nombre.
- Otras fuentes consultadas: https://github.com/Mrkvak/cx30-can (señales CAN en bus, no diagnóstico UDS; no se copió nada).
- No existe público (no encontrado): rutinas `$31` ni escrituras `$2E` del PCM Mazda (reset de aceite/dilución, aprendizaje de acelerador, reset de adaptaciones). Los procedimientos públicos de reaprendizaje de acelerador (https://ricksfreeautorepairadvice.com/mazda-throttle-body-relearn/) son manuales (ciclos de contacto/pedal), no comandos UDS; no se catalogaron. El reset de datos de aceite del PCM existe (Mazda lo menciona en sus TSB) pero el identificador de rutina no es público.

## TVS Apache RTR 160 4V FI

- Proveedor del sistema de inyección: **Bosch** ("Fuel Injection: Bosch- Closed loop"), según https://www.bikedunia.com/motorcycles/tvs/apache/apache-rtr-160-4v/. Esa ficha habla del sistema de inyección; no confirma el modelo exacto de ECU (ME17, EMS, etc.) ni que el fabricante de la ECU sea Bosch y no otro. Un catálogo de partes de TVS circula con "Vitesco" en el título (https://www.scribd.com/document/748412039/Apache-1604v-165rp-Parts-Catalogue-Rm-Vitesco), sin que se haya podido leer; sin confirmar. Parte TVS de la ECU: NF060119 (FI estándar BS6) y NF060419 (ISG 2020-2023), según listados de repuestos (ej. https://partonwheels.com/product/ecu-unit-isg-for-tvs-apache-rtr-160-2v-160-4v-180-2v-200-4v-ronin225/), que usan numeración TVS, no de Bosch.
- No hay repo OBDb para TVS (revisadas las 746 repos de la organización; solo hay KTM, Kawasaki, Honda, etc.).
- **No hay DIDs, rutinas ni funciones de servicio públicas de TVS** (TPS reset, aprendizaje de ralentí, reset de ECU, modo de conducción). El manual de servicio (https://www.scribd.com/document/836112543/2-Service-manual-RTR-160-4V-FI-BSVI) y el manual de usuario no documentan comandos UDS/KWP. El vehículo se declara OBD-II tipo B; los DTC estándar sí se pueden leer con modos J1979.

## Renault Kardian (1.0 TCe H4Dt)

- No existe repo OBDb de Kardian ni de Clio V/Captur con datos (su `default.json` está vacío). El repo genérico https://github.com/OBDb/Renault (fallback de marca, CC-BY-SA-4.0) tiene 92 lecturas `$22` en 7E0 (identificadores 20xx-28xx, FD8x). Se incluyeron todas, marcadas "genérico de marca, sin confirmar en Kardian". También hay lecturas en 740/745/748/744/DADA/DADB/DADF no incluidas (módulos no nombrados y mayormente de otros modelos).
- **DDT4All** (https://github.com/cedricp/ddt4all): el código es GPL-3.0-or-later, pero la base de ECUs (`ecu.zip`) proviene de DDT2000, herramienta propietaria de Renault; la discusión https://github.com/cedricp/ddt4all/discussions/1407 indica que el acceso original es restringido y no hay declaración de que sea redistribuible. **No se copió nada de esa base**; queda solo como puntero. Lo mismo para CanZE (orientado a EV Zoe/Twingo; no relevante al Kardian).
- No existe público: escrituras/rutinas verificables para el Kardian.

## Nissan March / Micra K13 (HR12DE / HR15DE)

- No hay repo OBDb para Micra/March (hay Versa, Kicks, Pulsar, etc., sin datos propios). El repo https://github.com/OBDb/Nissan (fallback de marca, CC-BY-SA-4.0) aporta 53 lecturas en 7E0 (mezcla de `$21` y `$22`) y 53 en 7E1. Las de 7E1 son de transmisión CVT (presión de poleas, etc.): solo aplican si el March es CVT. Todas marcadas "genérico de marca, sin confirmar en March K13".
- Búsquedas de Consult/UDS específicos del K13 (HR12DE/HR15DE, aprendizaje de aire de ralentí, etc.) no devolvieron identificadores públicos. Nissan usa CONSULT-III en estos modelos; no se encontraron DIDs documentados.

## Recomendaciones para descubrir más en el vehículo real

1. Barrido de DIDs `$22` en 7E0: 0x0100-0x0FFF, 0x1000-0x1FFF, 0x2000-0x2FFF, 0xA000-0xAFFF, 0xD900-0xDAFF, 0xF400-0xF4FF (rango J1979-UDS estándar F4xx también). En Mazda ya se sabe que responden 02xx, 03xx, 09xx, 11xx, 16xx, 17xx, 1Exx, A2xx, DAxx. Ritmo bajo (p. ej. 50 ms entre consultas), contacto puesto, motor apagado, y con el cargador de batería conectado.
2. Probar también servicio `$21` con PIDs 0x00-0xFF (Mazda lo usa para 0x21, 0x51 y 0x82).
3. Registrar respuestas negativas `7F 22 31` (fuera de rango) frente a `7F 22 33` (requiere seguridad) para separar DIDs inexistentes de protegidos.
4. Para rutinas: enumerar `$31 03` (resultado de rutina) y solo con `10 03` activa; no enviar `$31 01` ni `$2E` a ciegas.
5. Moto TVS: comprobar el protocolo con ATDP (CAN o K-Line) y leer `09 02`/`09 04` (VIN/Cal ID) para identificar el modelo exacto de ECU Bosch antes de cualquier barrido.
6. Contribuir los hallazgos a OBDb (CC-BY-SA) en lugar de guardarlos solo en la app.
