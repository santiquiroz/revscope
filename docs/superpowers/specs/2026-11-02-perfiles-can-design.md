# Perfiles CAN por vehículo y asistente "Enseñar mi moto"

## Intención

Muchas motos y carros con CAN publican solos (broadcast) datos que el OBD estándar no da o da
peor: marcha real (también con motor apagado), pata lateral, embrague, kill switch, velocidad de
rueda a 100 Hz. El 2026-11-02 se mapearon a mano en la TVS Apache RTR 160 4V con RevScope + MCP
(ver revdash `docs/fase0/hallazgos-can-2026-11-02.md`). Esta funcionalidad convierte ese proceso
en algo que cualquier dueño hace solo desde la app, sin IA, y que una IA cualquiera (cliente MCP)
también puede conducir, y hace que RevScope use lo aprendido en todas sus pantallas.

Hoy la marcha de RevScope se estima (`DerivedMetricsEngine` / `AdaptiveGearLearner`) a partir de
RPM y velocidad, así que no existe con motor apagado ni en neutro.

## Objetivos

1. **Perfil CAN por modelo**: datos (no código) que dicen cómo decodificar cada señal broadcast.
2. **Lectura configurable por vehículo**: modo Estándar (solo PIDs, lo de hoy), Combinado (PIDs +
   escuchas cortas) o Solo bus; y fuente elegible por señal. Por defecto automático.
3. **Asistente "Enseñar mi moto"**: guiado paso a paso, la captura arranca al tocar "Listo",
   detección automática y prueba en vivo antes de guardar.
4. **Operable por cualquier IA vía MCP**: las mismas piezas del asistente como tools; la IA puede
   poner un paso en la pantalla del celular.
5. **Compartir perfiles**: exportar/importar archivo y perfiles incluidos en la app.
6. **Perfil de la Apache incluido** como primer perfil.

## No objetivos

- Escribir en el CAN (p. ej. elegir modo de manejo suplantando al tablero).
- Importar/exportar DBC, sincronizar con revscope-server, protocolos no CAN.
- Probar en buses de 29 bits (el decodificador los soporta genéricamente; sin pruebas reales).

## Anti-objetivos (fallaría aunque "funcione")

- Mostrar un dato congelado o viejo como si fuera actual.
- Que un perfil mal aprendido o ajeno reemplace en silencio un PID que funcionaba.
- Que un perfil compartido lleve VIN, placa u otro dato personal.
- Que el asistente exija IA, o que su texto suponga una IA concreta.
- Lenguaje técnico en el camino principal (IDs y bytes solo en letra pequeña o "avanzado").

## Decisiones del dueño

- Asistente paso a paso estilo tutorial con "Listo"; maquetas aprobadas (4 pantallas: lista,
  paso, escuchando, encontrado + prueba en vivo).
- La IA no es necesariamente Claude: la UI usa el nombre que manda el cliente MCP.
- El usuario elige cómo se leen los datos: como hoy y/o con el perfil.
- Los perfiles se pueden compartir.
- Se construyen las dos cosas: usar el perfil de la Apache en RevScope y el asistente.

Fuentes: hallazgos completos con tramas reales en el repo revdash
`docs/fase0/hallazgos-can-2026-11-02.md`; maquetas en `docs/superpowers/specs/assets/2026-11-02-mapeo-{1-inicio,2-paso,3-escuchando,4-encontrado}.png`
(junto a este documento).

## Diseño

### 1. Formato del perfil (`PerfilCan`, JSON, versión 1)

```json
{
  "version": 1,
  "id": "tvs.apache_rtr_160_4v.2024",
  "vehiculo": {"marca": "TVS", "modelo": "Apache RTR 160 4V", "anios": "2024", "tipo": "MOTORCYCLE"},
  "calibracionesEcu": ["U507EBS6B1A1a706"],
  "protocolo": "6",
  "licencia": "CC-BY-SA-4.0",
  "senales": [
    {"clave": "GEAR", "nombre": "Marcha", "id": "500", "bitInicio": 32, "largo": 8,
     "orden": "big", "firmado": false, "factor": 1, "offset": 0, "unidad": "",
     "tipo": "enum", "valores": {"0": "N", "1": "1", "2": "2", "3": "3", "4": "4", "5": "5"},
     "invalidos": [7], "periodoMs": 100, "estado": "verificada", "origen": "asistente"}
  ]
}
```

- `bitInicio`: bit 0 = bit menos significativo del byte 0; para `orden: big` el campo de varios
  bytes empieza en el byte de `bitInicio/8` y sigue hacia los bytes siguientes (lo mismo que hoy
  leímos a mano: RPM = byte 1 << 8 | byte 2). El constructor puede elegir otra convención si la
  documenta, la prueba y deja la exportación a DBC trivial (no se implementa ahora).
- `tipo`: `numero` (valor × factor + offset), `enum` (tabla `valores`) o `bandera` (largo 1).
- `estado`: `verificada` (confirmada en vivo en este vehículo), `sin_verificar` (comunidad o
  importada) o `aprendida` (detectada por el asistente, pendiente de la prueba en vivo).
  **Solo `verificada` alimenta las lecturas.**
- `clave`: si coincide con una lectura existente de RevScope (`0C` RPM, `0D` velocidad, `05`
  temperatura motor, `0F` aire de admisión, `11` acelerador, `GEAR`), la señal puede ser su
  fuente; claves nuevas (constantes en un solo lugar): `PATA_LATERAL`, `EMBRAGUE`, `KILL_SWITCH`,
  `MOTOR_EN_MARCHA`, `VELOCIDAD_RUEDA`.
- Sin VIN, placa ni nada personal. Validador al importar (rangos de bits, ID hexadecimal válido,
  claves conocidas, tamaño máximo) con mensajes claros; inválido = se rechaza entero.
- Ubicación: incluidos en `core/obd/src/main/assets/perfiles_can/*.json`; propios e importados en
  `filesDir/perfiles_can/`. Licencia de los datos CC-BY-SA-4.0 como `catalogo_propietario.json`
  (decisión de Claude).

### 2. Decodificador

Función pura: `(PerfilCan.Senal, bytes de la trama) -> valor | null` (null si la trama es corta o
el valor está en `invalidos`). Tests con los valores reales de la Apache (tabla al final).

### 3. Lectura por vehículo

`VehicleProfileEntity` gana (migración Room): `perfilCanId: String?`, `fuenteDatos`
(`ESTANDAR` | `COMBINADO` | `SOLO_BUS`) y `fuentesPorSenal` (JSON clave → `AUTO|PID|BUS|GPS|CALCULADA`).

| Modo | Comportamiento |
|---|---|
| Estándar | Lo de hoy, sin cambios. Default si no hay perfil. |
| Combinado | Default cuando el vehículo tiene perfil. Tras cada ronda del `PidScheduler`, una ventana corta `AT MA` (con los comandos de `EscuchaCan`) que guarda la última trama de cada ID del perfil. Ventana ≈ 1.5 × el mayor `periodoMs` de las señales en uso, con tope (orientativo 200 ms); el constructor la ajusta midiendo. |
| Solo bus | `AT MA` continuo; publica RPM/velocidad/etc. a la frecuencia del bus; los PIDs que no estén en el perfil dejan de leerse (la UI lo dice). Relanza tras BUFFER FULL como hoy. |

- `AUTO` por señal: bus si existe y está `verificada`; si no, PID; si no, cálculo actual
  (marcha estimada). El interruptor actual de velocidad GPS/PID 0D se integra en esta misma lista.
- **Caducidad**: una señal de bus sin trama nueva en 3 × `periodoMs` deja de publicarse y la
  lectura vuelve a su fuente estándar; un aviso único por sesión ("No llegan datos del bus para
  Marcha; usando la estimación"). Nunca se publica un valor viejo.
- Las lecturas de bus se publican con las mismas claves (`GEAR`, `0C`...), así que tablero,
  viajes, alertas y MCP las usan sin cambios. Señales nuevas (pata, embrague, kill switch) se
  muestran en el tablero de conducción de forma discreta (diseño de esa tarjeta: del constructor,
  siguiendo el estilo existente; pata abajo con motor en marcha = aviso visible).
- Grabar viajes sigue funcionando en Combinado y Solo bus: las ventanas las ejecuta el propio
  ciclo de sondeo con su acceso al transporte (no pasan por `withDiagnosticLease`), igual que
  hoy pide los PIDs. El `escuchar_can` de MCP y la captura de ruta siguen exigiendo que no haya
  viaje grabando, y mientras corren el ciclo pausa sus ventanas (decisión de Claude).
- `GEAR` del bus usa 0 = neutro, compatible con el 0 actual de `DerivedMetricsEngine`.
- La distancia y estadísticas de viaje (`SessionAggregator`) siguen leyendo `0D`; si la fuente
  efectiva de `0D` pasa a ser el bus, eso es lo buscado. `VELOCIDAD_RUEDA` es una clave aparte que
  nada agrega hasta que se decida usarla. El interruptor `SPEED_SOURCE_GPS` se migra a la fuente
  por señal de `0D` (`GPS` ↔ true) sin perder la preferencia guardada (decisión de Claude).

### 4. Asistente "Enseñar mi moto" (Taller)

Pantallas según las maquetas: lista de señales (automáticas / con tu ayuda / no disponibles,
progreso, cualquier orden, saltables), paso (instrucción grande, consejo, "Listo", "No lo logro ·
saltar"), escuchando (3 s, "no muevas nada"), encontrado (resultado en lenguaje simple, prueba en
vivo, "Sí, guardar" / "No, repetir"; ID/byte en letra pequeña). Antes de empezar: escucha 3 s; si
el vehículo no publica nada o no es CAN, lo dice y no sigue.

**Recetas** (datos, no código por señal): lista de estados con instrucción y consejo.

| Señal | Estados |
|---|---|
| Marchas | N, 1, 2, 3, 4, 5 (cantidad desde el perfil del vehículo o preguntada) |
| Pata lateral | arriba, abajo, arriba |
| Embrague | suelto, apretado, suelto |
| Kill switch | RUN, OFF, RUN |
| Acelerador | barrido continuo: cerrado → fondo → cerrado (5 s) |
| Velocidad de rueda | continuo: empujar o rodar (referencia GPS o PID 0D) |
| RPM, temperaturas | automáticas, sin pasos |

**Detección discreta** (pura, testeable): por cada captura de un estado, los campos (bits sueltos,
bytes, palabras de 16 bits big/little) **constantes dentro del estado**; candidatos = campos que
toman un valor distinto en cada estado distinto y el mismo valor al repetir un estado. Se descarta
lo que varía dentro de un estado (contadores, checksums, temperaturas). Se prefiere el campo más
chico (bit para 2 estados, byte para varios). Confianza alta = un solo candidato consistente.

**Detección continua**: correlación entre cada campo y la referencia (PID o GPS) en el tiempo;
ajuste lineal → `factor`/`offset`; se acepta con correlación alta (orientativo r ≥ 0.98) y se
redondea a escalas típicas si están cerca (0.01, 0.125, 0.25, 0.5, 1). Las automáticas usan esto
en segundo plano con la conexión normal (RPM vs `0C`, temperatura vs `05`, aire vs `0F`).

**Sin resultado**: "Esta señal no viaja por el bus en tu vehículo" → `No disponible` (reintentable).

**Seguridad de los pasos** (decisión de Claude): ningún paso pide motor encendido y moto en
movimiento a la vez. Marchas, pata, embrague, kill switch y acelerador se hacen con motor apagado y
llave en ON (verificado el 2026-11-02: el acelerador reporta con motor apagado). La velocidad se
aprende empujando la moto caminando (referencia: GPS si hay fix, o solo forma de la curva y
escala a confirmar después con una captura de ruta). Cada paso con la moto fuera del gato lo dice.

**Escucha inicial** distingue: protocolo no CAN (`AT DPN`), adaptador sin modo monitor (`AT MA`
falla o responde `?`) y bus silencioso (0 tramas en 3 s); cada caso con su texto.

### 5. MCP (cualquier cliente)

- Estado en memoria de la app (singleton, sin persistir; decisión de Claude): el último
  `clientInfo.name` recibido en `initialize` y los pasos pedidos (vencen a los 5 min sin "Listo";
  `cancelado` si el usuario cierra el paso). La UI muestra "<nombre> te pide…" o "Tu asistente de
  IA" si no vino. Un paso pedido por MCP aparece como notificación si la app está en segundo plano.
- Tools nuevas (lectura salvo que se diga):
  - `get_perfil_can`: perfil del vehículo activo, estado y fuente efectiva de cada señal.
  - `listar_recetas_mapeo`: recetas disponibles con sus estados.
  - `pedir_paso_mapeo` (permiso de control, el mismo de `escuchar`/viaje): muestra en el celular una instrucción (texto libre o
    estado de receta) y devuelve `pasoId` al instante; la captura arranca cuando el usuario toca
    "Listo".
  - `estado_paso_mapeo(pasoId, espera_s ≤ 50)`: `esperando_usuario | capturando | listo(capturaId)
    | cancelado | vencido`.
  - `analizar_mapeo(senal, capturas: {estado → capturaId})`: candidatos con confianza (misma
    detección del asistente). Para continuas acepta capturas de ruta con GPS.
  - `guardar_senal_perfil` (permiso de control): agrega o reemplaza una señal; entra como
    `aprendida` hasta que el usuario la confirme en la prueba en vivo del celular.
  - `probar_senal(definicion, segundos)`: valores decodificados en vivo.
  - `exportar_perfil_can`: el JSON compartible.
- Documentar todo en `docs/configuracion.md` y el manual.

### 6. Compartir

- "Compartir perfil" → archivo `<id>.revscope-can.json` por la hoja de compartir de Android;
  abrirlo en RevScope (intent `VIEW`/`SEND` de JSON) abre una pantalla de importación que muestra
  el resultado de la validación. Los perfiles incluidos, importados o sugeridos **siempre** entran
  con todas sus señales `sin_verificar`, también el de la Apache: el dueño las confirma con la
  prueba en vivo (rápida, sin repetir el asistente). Hasta entonces la fuente efectiva sigue
  siendo la estándar.
- El JSON lleva `autor` y `atribucion` (CC-BY-SA pide atribución); la pantalla del perfil los
  muestra.
- Sugerencia al conectar un vehículo sin perfil: coincide si la calibración de ECU (09 04) es
  igual, o si marca+modelo normalizados (minúsculas, sin espacios ni guiones) coinciden con el
  nombre del vehículo; si no hay coincidencia, el usuario puede elegir de la lista a mano.
- `filesDir/perfiles_can/` entra en la copia de seguridad que ya haga la app (como el resto de
  datos del usuario).
- Perfiles de la comunidad: llegan por PR al repo (`assets/perfiles_can/`) con cada versión.

### 7. Perfil incluido de la Apache

Del hallazgo del 2026-11-02 (todas confirmadas por el dueño salvo velocidad). Periodos medidos:
ID 300 = 50 ms, 301 = 10 ms, 500 = 100 ms. Tramas reales capturadas (payload hex, 8 bytes) para
los tests del decodificador:

| ID | payload | significado |
|---|---|---|
| 500 | `0000450000004604` | llave ON, motor apagado: 29 °C motor, N, pata arriba, aire 30 °C, kill RUN |
| 500 | `0000450000404604` | igual con pata abajo (b5 = 40; armada desde el diff, no copiada de una trama) |
| 500 | `0000450000004D0C` | motor en marcha (b7 = 0C: RUN + en marcha), aire 37 °C |
| 500 | `00005C000000540C` | motor caliente 52 °C, aire 44 °C |
| 500 | `002C7E0000005B04` | empujando: velocidad filtrada b1 = 2C, motor 86 °C |
| 300 | `0C0DF8000000D200` | acelerador 6 % (0C), 3576 rpm (0DF8) |
| 300 | `000000010000D340` | embrague apretado (b3 = 01), motor apagado |
| 301 | `0240FFFF02010000` | velocidad de rueda cruda 0x0240 = 576 (≈ 5.8 km/h) |

Marcha: b4 de 500 tomó 00, 01, 02, 03, 04, 05 en N y 1ª-5ª y 07 entre marchas (ver hallazgos).

| clave | ID | campo | conversión |
|---|---|---|---|
| `0C` RPM | 300 | bytes 1-2 big | ×1 |
| `11` acelerador | 300 | byte 0 | ×0.5 % |
| `EMBRAGUE` | 300 | byte 3 bit 0 | 1 = apretado |
| `GEAR` | 500 | byte 4 | enum 0=N, 1-5; 7 inválido |
| `PATA_LATERAL` | 500 | byte 5 bit 6 | 1 = abajo |
| `KILL_SWITCH` | 500 | byte 7 bit 2 | 1 = RUN |
| `MOTOR_EN_MARCHA` | 500 | byte 7 bit 3 | 1 = en marcha |
| `05` temp. motor | 500 | byte 2 | −40 °C |
| `0F` aire admisión | 500 | byte 6 | −40 °C |
| `VELOCIDAD_RUEDA` | 301 | bytes 0-1 big | ≈ ×0.01 km/h, `sin_verificar` hasta la ruta con GPS |
| `0D` velocidad | 500 | byte 1 | ≈ ×0.125 km/h, `sin_verificar` hasta la ruta con GPS |

Ojo: el `11` estándar de esta moto marca ~11 % con acelerador cerrado; el del bus marca 0. Al
usar el del bus como fuente de `11`, la UI muestra el del bus (es el correcto para el usuario).

## Dependencias y orden

- Se construye sobre la rama `feat/captura-can-ruta` (parada anticipada del monitor, GPS en
  capturas), que debe estar mergeada antes.
- Orden sugerido: (1) formato + decodificador + perfil Apache + lectura Combinado/Solo bus +
  ajustes; (2) asistente; (3) MCP; (4) compartir.

## Queda al constructor

Diseño visual exacto de pantallas y tarjeta del tablero (siguiendo las maquetas y el estilo del
Taller), ventana exacta del modo Combinado, estructura de clases, nombres internos, umbrales finos
de detección (documentados en el código donde no sean obvios).
