# Configuración

> Índice: [Vehículo](#vehículo) · [Alertas de audio y vibración](#alertas-de-audio-y-vibración) · [Alertas de voz por categoría](#alertas-de-voz-por-categoría) · [Alertas personalizadas por PID](#alertas-personalizadas-por-pid) · [Radares](#radares-de-velocidad) · [Combustible](#combustible) · [Inteligencia artificial](#inteligencia-artificial) · [Detección de caída](#detección-de-caída) · [Servidor MCP](#servidor-mcp-red-local) · [Copia de seguridad](#copia-de-seguridad) · [PIDs personalizados](#pids-personalizados) · [Pico y placa](#pico-y-placa)
>
> Ver también: [Instalación](instalacion.md) · [Manual de usuario](manual-usuario.md) · [FAQ](faq.md) · [Desarrollo](desarrollo.md)

Esta página describe cada sección de la pestaña **Ajustes**, en el mismo orden en que aparecen en la app, con sus valores por defecto.

## Vehículo

| Opción | Qué hace | Default |
|---|---|---|
| Vehículo activo | Fila de navegación a Perfiles de vehículo — muestra el nombre del vehículo activo o "Ninguno" | — |
| Preguntar vehículo al inicio | Si está activo, la app pregunta qué vehículo usar cada vez que abre desde cero | Activado |

## Alertas de audio y vibración

Dos interruptores generales antes de las categorías:

| Opción | Qué hace | Default |
|---|---|---|
| Alertas activas | Interruptor maestro — si está apagado, ninguna alerta sonará ni vibrará, sin importar las categorías | Activado |
| Voz (TTS) | Si está apagado, las alertas siguen sonando (tono/vibración) pero sin hablar el mensaje | Activado |

### Alertas de voz por categoría

Cada categoría se puede apagar sin afectar a las demás:

| Categoría | Qué anuncia | Default |
|---|---|---|
| Temperatura | Sobrecalentamiento del motor | **Activado** |
| Batería y carga | Voltaje bajo / problema de carga | **Activado** |
| Radares de velocidad | Solo al ir HACIA un radar registrado (rumbo GPS a ±60° del radar y acercándote) — cámaras atrás o en calles perpendiculares ya no suenan | **Activado** |
| Umbrales personalizados | Cualquier PID fuera del rango que definas en [Alertas personalizadas por PID](#alertas-personalizadas-por-pid) | **Activado** |
| Tiempos 0-100 y vueltas | Resultados de aceleración y vueltas en Modo Pista | **Activado** |
| Pico y placa al entrar a otra ciudad | Aviso al detectar por GPS que entraste a una ciudad con restricción vigente para tu placa | **Activado** |
| Anomalías inteligentes | Comportamientos anómalos detectados por el motor de inteligencia | Desactivado |
| Testigo del motor (MIL) | Cuando se encienda el testigo de check-engine en marcha | Desactivado |
| Zona roja | Aviso al acercarte a la línea roja (además del *shift light* visual) | Desactivado |
| Información local al cambiar de ciudad | Usa tu proveedor de IA con búsqueda web para avisar de eventos/cierres relevantes al llegar a un municipio nuevo | Desactivado |
| Pico y placa por IA en cualquier ciudad | Tu proveedor de IA (con búsqueda web) investiga la restricción vehicular de la ciudad donde estés — pico y placa, hoy no circula, rodízio… — y la guarda hasta que venza (cache local, ~1-2 consultas al mes) | Desactivado |

> 💡 "Información local" es la única categoría que consume tu proveedor de IA (con búsqueda web) cada vez que cambias de municipio — por eso viene apagada por defecto y su subtítulo en la app aclara el costo aproximado. No está disponible con el proveedor "Compatible OpenAI" genérico (LM Studio, etc.) porque no ofrece búsqueda web administrada.

Debajo de las categorías hay tres campos numéricos y un botón **Guardar alertas**:

| Campo | Uso |
|---|---|
| Temp máx °C | Umbral de sobrecalentamiento (60-150°C válido) |
| Volt mín | Umbral de voltaje bajo (8-15V válido) |
| Zona roja RPM | RPM de la línea roja — también controla el *shift light* visual del panel de Conducir (3000-20000 RPM válido) |

## Alertas personalizadas por PID

Editor de texto con un arreglo JSON de umbrales sobre cualquier parámetro:

```json
[{"pid":"0A","min":200,"nombre":"Presión de combustible"}]
```

Cada objeto acepta `pid`, `min` y/o `max` (omite el que no quieras vigilar) y `nombre` (el texto que se anuncia). Para sensores propietarios del fabricante (por ejemplo, presión de llantas si tu vehículo la expone), primero define el [PID personalizado](#pids-personalizados) por Modo 22 y luego crea aquí su alerta. Botón **Validar y aplicar**.

## Radares de velocidad

Descarga los radares en 50 km a la redonda de tu ubicación actual, combinando dos fuentes:

| Fuente | Qué aporta |
|---|---|
| OpenStreetMap | Radares reportados por la comunidad |
| ANSV (registro oficial) | Radares fijos oficiales de la Agencia Nacional de Seguridad Vial |

Se actualiza **una vez por semana automáticamente** y funciona sin conexión después de la primera descarga. El botón **"Descargar radares de mi zona"** dispara una descarga manual inmediata; debajo aparece el conteo total y por fuente una vez completada.

Además, durante un viaje la app detecta cuando te alejas **más de 35 km** del centro de la última descarga (por ejemplo, un viaje Medellín → Bogotá) y **re-descarga sola** los radares alrededor de tu nueva posición — silencioso, con reintento cada 30 minutos si no hay señal de datos. El refresco semanal sigue a ese nuevo centro automáticamente. Fuera de Colombia solo aplica la fuente OpenStreetMap.

## Combustible

Tres campos numéricos, uno por tipo de combustible, usados para estimar el costo de cada viaje según el tipo de combustible configurado en el vehículo activo:

| Campo | Default (COP/galón) |
|---|---|
| Precio galón corriente | 16.000 |
| Precio galón extra | 20.000 |
| Precio galón diésel / ACPM | 10.500 |

> No existe hoy una fuente oficial en línea vigente en datos.gov.co para precios de combustible por municipio — los tres valores se ajustan manualmente según tu estación. Botón **Guardar precios**.

## Inteligencia artificial

Función opcional que explica códigos de falla, alimenta el Mecánico IA y — si el proveedor lo soporta — el aviso de información local. Sin API key, esas funciones simplemente no ofrecen explicación de IA.

| Proveedor | Modelo por defecto | Búsqueda web |
|---|---|---|
| Claude (Anthropic) | `claude-haiku-4-5-20251001` | Sí |
| OpenAI | `gpt-5-mini` | Sí |
| Gemini (Google) | `gemini-2.5-flash` | Sí |
| Compatible OpenAI (LM Studio, DeepSeek, Groq, OpenRouter…) | según tu servidor | No |

Campos:
- **Proveedor de IA**: menú desplegable con las cuatro opciones de la tabla.
- **API key**: campo tipo contraseña — cambia de valor según el proveedor elegido (cada proveedor guarda su propia llave cifrada en el dispositivo).
- **Modelo**: opcional; si lo dejas vacío usa el default de la tabla.
- **Base URL**: solo aparece con "Compatible OpenAI" — apunta a tu servidor, por ejemplo un LM Studio en tu red local: `http://192.168.1.20:1234/v1`.

Botones **Guardar configuración de IA** y **Probar conexión** (hace una llamada mínima y muestra si respondió correctamente).

## Detección de caída

Pensada para motociclistas. Está **desactivada por defecto**.

**Requisitos:**
- Teléfono de contacto de emergencia guardado.
- Permiso de envío de SMS (`SEND_SMS`) y, en Android 13+, de notificaciones — la app los pide en el momento de activar el interruptor si faltan.
- Una sesión activa grabando (OBD o [viaje GPS](manual-usuario.md#conducir)): el detector se alimenta de las muestras de movimiento y velocidad del servicio de grabación, así que **sin un viaje en curso no hay vigilancia**.

**Cómo funciona:**
1. Se detecta un impacto cuando la aceleración total supera **6G** y la velocidad venía siendo mayor a **20 km/h** en los 5 segundos anteriores.
2. Si después del impacto el vehículo queda inmóvil (velocidad menor a 3 km/h y aceleración por debajo de 1.3G) durante **30 segundos sostenidos**, se dispara la alarma. Si en cambio detecta movimiento normal después del golpe (por ejemplo, seguiste rodando), lo interpreta como falso positivo y vuelve a vigilar sin avisar nada.
3. Se muestra una notificación de pantalla completa con alarma sonora y una cuenta regresiva de **60 segundos** con el botón **"ESTOY BIEN"**.
4. Si nadie responde a tiempo, se envía un **SMS** (partido en varios mensajes si es largo) al contacto de emergencia con tu última ubicación conocida: *"⚠ RevScope: posible caída detectada de ‹vehículo›. Última ubicación: https://maps.google.com/?q=lat,lon"*.

Campos y controles: **Teléfono de emergencia** + botón **Guardar teléfono**, interruptor **Activar detección de caída**, y botón **"Probar (sin enviar SMS real)"** que simula la alarma completa sin enviar ningún mensaje.

> ⚠️ Esta función es un apoyo adicional, no un sistema de emergencias certificado: depende de que haya una sesión de grabación activa, de la señal GPS/celular en el momento del incidente, y de umbrales fijos que pueden no cubrir todos los tipos de caída o accidente. No sustituye el sentido común ni el equipo de protección al conducir.

## Servidor MCP (red local)

Expone el estado de tu vehículo a asistentes de IA de escritorio (Claude Desktop, LM Studio…) conectados a la misma red WiFi, usando el protocolo [MCP](https://modelcontextprotocol.io) (Model Context Protocol) sobre HTTP. **Apagado por defecto.**

Al activar el interruptor **"Servidor MCP activo"** aparecen la URL (con tu IP local, puerto `8765`) y un token generado automáticamente, ambos copiables con un botón.

Configuración de ejemplo para Claude Desktop (`claude_desktop_config.json` o el equivalente de tu cliente MCP):

```json
{
  "mcpServers": {
    "revscope": {
      "type": "streamable-http",
      "url": "http://192.168.1.20:8765/mcp",
      "headers": {
        "Authorization": "Bearer <tu-token>"
      }
    }
  }
}
```

Herramientas (*tools*) que expone, todas de solo lectura:

| Tool | Qué devuelve |
|---|---|
| `get_estado` | Estado actual del vehículo: conexión, perfil activo y lecturas en vivo |
| `get_viajes` | Últimos viajes del vehículo activo con sus estadísticas (distancia, velocidad, eco score) |
| `get_viaje_detalle` | Detalle agregado de un viaje puntual por su id (distancia, velocidad, combustible, lanzamientos) |
| `get_chequeo_salud` | Último chequeo de salud del vehículo — hallazgos por área con su nivel (OK/ATENCION/FALLA) |
| `get_dtc` | Códigos de falla (DTC) activos leídos en vivo del vehículo — requiere adaptador conectado |
| `get_mantenimiento` | Ítems de mantenimiento configurados y kilómetros restantes para cada uno |
| `get_documentos` | Estado de documentos del vehículo activo: SOAT, tecnomecánica, pico y placa, seguro y licencia |

**Seguridad**: el servidor solo se enlaza a tu IP de **WiFi** (nunca datos móviles) y un vigilante interno revisa cada 60 segundos que esa IP siga siendo la misma — si cambias de red o pierdes el WiFi, se apaga solo. Actívalo únicamente en redes de confianza (tu casa, tu taller).

## Copia de seguridad

Incluye viajes, perfiles, informes de chequeo y ajustes en un único archivo `.zip`. **La API key de IA nunca se incluye** (queda cifrada en el dispositivo) — hay que volver a ingresarla después de restaurar en un equipo nuevo.

| Acción | Detalle |
|---|---|
| Exportar copia | Guarda `revscope-backup-AAAA-MM-DD.zip` donde elijas (selector de archivos del sistema) |
| Importar copia | Reemplaza **todos** los datos actuales por los del archivo elegido, con diálogo de confirmación; al terminar la app se reinicia sola |
| Copia automática semanal | Interruptor, **activado por defecto** — guarda una copia cada semana en `Descargas/RevScope`, conservando las últimas **4** |

## PIDs personalizados

Editor JSON con el mismo esquema que usa RevScope internamente (`pids_mode01.json`), para agregar parámetros propios del fabricante — típicamente descubiertos con el [Escáner Mode 22](manual-usuario.md#taller) del Taller. Incluye botones **Validar y aplicar**, **Compartir pack** (lo manda por cualquier app de mensajería/correo como texto) e **Importar** (desde un archivo `.json` o `.txt` que te compartan).

## Pico y placa

RevScope trae reglas incorporadas para dos ciudades y reconoce una tercera sin reglas aún:

| Ciudad | Esquema | Horario | Restringe por | Vigencia |
|---|---|---|---|---|
| **Medellín** | Rotación semanal (S1-2026) | 5:00–20:00 | Carros: **último** dígito de la placa · Motos: **primer** dígito | Hasta el **31 de julio de 2026** |
| **Bogotá** | Par/impar por día del mes | 6:00–21:00 | Último dígito de la placa (motos **exentas** siempre) | Todo 2026 |
| **Cali** | Sin confirmar | 6:00–19:00 (fuente) | — | — |

Rotación vigente de Medellín (S1-2026, días sin restricción sábados/domingos/festivos):

| Día | Dígitos restringidos |
|---|---|
| Lunes | 1, 7 |
| Martes | 0, 3 |
| Miércoles | 4, 6 |
| Jueves | 5, 9 |
| Viernes | 2, 8 |

Bogotá alterna por el **día del mes**: en día impar circulan las placas terminadas en 1-5 (restringidas 6,7,8,9,0) y en día par lo inverso — las motos nunca tienen restricción en Bogotá.

> ⚠️ Al llegar el 1 de agosto de 2026 (o la fecha de vigencia que corresponda), la app deja de aplicar la regla vencida de Medellín. Si tienes activado **"Pico y placa por IA en cualquier ciudad"** (Ajustes → Alertas por voz), la app le pide a tu proveedor de IA la rotación nueva y la aplica sola — con una notificación mostrando la rotación aplicada para que la verifiques. Sin ese toggle, la tarjeta de Vehículo al día muestra "reglas vencidas" hasta que configures la rotación manualmente (JSON abajo) o llegue una actualización de la app.

**Cali** aparece en el listado de ciudades pero sin una rotación incorporada — la tarjeta de Vehículo al día mostrará "sin datos" con la indicación de configurar la rotación vigente. El motor interno (`PicoYPlacaEngine`) soporta reglas de ciudad totalmente personalizadas en JSON, con este esquema (verificado en el código, campos con default cuando se omiten):

```json
{
  "cityId": "cali",
  "displayName": "Cali",
  "scheme": "WEEKDAY_ROTATION",
  "rotation": { "2": [1, 2], "3": [3, 4], "4": [5, 6], "5": [7, 8], "6": [9, 0] },
  "startHour": 6,
  "endHour": 19,
  "carDigit": "LAST",
  "motoDigit": "FIRST",
  "motosExentas": false,
  "validFromMs": 1767243600000,
  "validUntilMs": 1798779599000
}
```

Para un esquema tipo Bogotá (par/impar por día del mes) se usa `"scheme": "DATE_PARITY"` con `dateParityRestricted: {"ODD_DAY": [...], "EVEN_DAY": [...]}` en vez de `rotation`.

> ⚠️ A la fecha de esta guía, el motor y el formato JSON de arriba ya funcionan de punta a punta (se guardan en `PICO_PLACA_RULES_JSON` y los consumen tanto la tarjeta de Vehículo al día como el aviso por voz al cambiar de ciudad), pero **todavía no hay un campo en Ajustes para pegar este JSON** — a diferencia de PIDs y Alertas personalizadas, que sí tienen su editor en la app. Hasta que se agregue esa pantalla, es una capacidad de la plataforma sin superficie de usuario terminada.

La ciudad de pico y placa de cada vehículo se elige en **Taller → Perfiles de vehículo**, junto con la placa, con un menú desplegable ("Ninguna" + las ciudades del listado).

---

¿Algo no se comporta como se describe aquí? Revisa la [FAQ](faq.md) o mira el detalle de implementación en [Desarrollo](desarrollo.md).
