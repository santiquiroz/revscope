# Manual de usuario

> Índice: [Selector de vehículo](#selector-de-vehículo) · [Conducir](#conducir) · [Mapa](#mapa) · [Taller](#taller) · [Viajes](#viajes) · [Ajustes](#ajustes)
>
> Ver también: [Instalación](instalacion.md) · [Configuración](configuracion.md) · [FAQ](faq.md) · [Desarrollo](desarrollo.md)

RevScope se organiza en cinco pestañas en la barra inferior: **Conducir**, **Mapa**, **Taller**, **Viajes** y **Ajustes**. Cada pestaña muestra en su encabezado el selector del vehículo activo.

## Selector de vehículo

El selector va en el encabezado de cada pestaña, debajo del título (en el **Mapa**, arriba al centro, y se oculta mientras navegas). Muestra un punto de color (estado de conexión del adaptador), un ícono de carro o moto según el tipo del vehículo activo, su nombre (con un nombre largo usa dos líneas) y una flecha hacia abajo. Al tocarlo se abre la hoja **"Selecciona"**:

- Lista de vehículos guardados (ícono + nombre + placa si la tiene), con una marca ✓ en el activo — tocar uno lo activa de inmediato y cierra la hoja.
- Botón **"Agregar otro vehículo"** que lleva al formulario de perfiles.
- Fila inferior **"Adaptador: ‹estado› · Administrar"** para emparejar o cambiar de adaptador Bluetooth.

Si es el primer arranque del proceso (la app se acaba de abrir) y tienes uno o más vehículos guardados, esta misma hoja aparece sola con un checkbox extra: **"No volver a preguntar al inicio"**. Márcalo si siempre usas el mismo vehículo — puedes revertirlo después en **Ajustes → Vehículo → Preguntar vehículo al inicio**.

## Conducir

La pantalla principal. Arriba a la izquierda, el ícono de Bluetooth cambia de forma y de color según el estado del adaptador (gris = sin conexión, ámbar = conectando, verde = conectado, rojo = error; TalkBack lo lee en palabras); tocarlo abre el escáner de adaptadores. Debajo de la barra va el [selector de vehículo](#selector-de-vehículo). A la derecha están el voltaje de batería (si hay lectura), el botón de **Modo Pista** (bandera a cuadros) y el de **Ajustes**.

Contenido, de arriba hacia abajo:

- **Banner de "Vehículo al día"** (rojo, tocable): solo aparece cuando el vehículo activo tiene algo que requiere tu atención hoy — un documento vencido o pico y placa vigente ahora mismo. Tocarlo lleva directo a la tarjeta correspondiente en Taller. Si todo está en orden, no aparece nada.
- **Banner de alerta activa**: mensajes puntuales de anomalías detectadas o de resultados de 0-100/0-60 recién logrados ("🏁 0-100 en 6.42s"), visibles por 5 segundos.
- **Botón "Iniciar viaje GPS"**: solo se ofrece cuando no hay ningún adaptador conectado ni conectándose. Arranca un viaje grabado únicamente con GPS + sensores del celular (ruta, velocidad, inclinación, fuerzas G) — sin necesitar el ELM327. Con el viaje GPS activo el botón cambia a **"Finalizar viaje GPS"** y los gauges que dependen del ECU (RPM, marcha, temperatura, boost) se ven atenuados con la nota "requieren adaptador", mientras que el velocímetro usa la velocidad GPS.
- **Pastilla de viaje** (con el adaptador conectado): **"● Grabando viaje"** o **"● Conectado · sin viaje"**. Tocarla muestra las acciones:
  - **Finalizar viaje (seguir conectado)**, con confirmación: guarda el viaje en el historial y deja el adaptador conectado. Los gauges, el voltaje y las alertas siguen en vivo, y puedes leer los códigos de falla (Taller o `get_dtc` por MCP) sin reconectar. La notificación pasa a "sin viaje (diagnóstico)" y trae el botón **Iniciar viaje**.
  - **Iniciar viaje**: abre un viaje nuevo sobre la misma conexión (también aparece como botón grande mientras no hay viaje).
  - **Desconectar adaptador**: suelta el Bluetooth y cierra el viaje, como antes.
  - Conectar el adaptador sigue iniciando un viaje, igual que siempre. Con **Iniciar viaje automáticamente al moverse** (Ajustes → Vehículo y garage → Herramientas, activo por defecto), si hay conexión sin viaje y mantienes 10 km/h durante 5 s se abre uno solo y lo avisa ("Viaje iniciado automáticamente"). Si finalizas el viaje andando, primero hay que detenerse para que se vuelva a armar.
- **Gauge de RPM**, con el borde de toda la pantalla iluminándose como *shift light*: color acento cuando llegas al 95% de la línea roja configurada para el vehículo activo, y rojo pleno al cruzarla.
- **Velocímetro**, con un chip debajo que indica la fuente: **OBD** o **GPS** (con ✓ cuando usa GPS). Se puede tocar para alternar manualmente entre la velocidad que reporta el ECU y la del GPS del celular — útil si el adaptador da lecturas erráticas o para comparar con el [comparador de velocímetros](#taller) de Taller.
- **Marcha calculada**, **temperatura del motor** y **barra de boost** (turbo), lado a lado.
- **Barra de puntaje del viaje**: un estilo de conducción (con emoji y etiqueta, ej. "🌿 Eco", "🔥 Agresivo") calculado en vivo por el motor de eficiencia, junto con un **Puntaje** 0-100.
- Sin adaptador (modo solo GPS), una tarjeta explica que RPM, temperatura, marcha y boost necesitan un adaptador OBD2, con el botón **Configurar adaptador**.

## Mapa

Tu posición y ruta en vivo sobre un mapa de OpenStreetMap (offline una vez descargados los radares de tu zona):

- **Ruta recorrida** dibujada como línea, con un marcador "Tú" en la posición actual.
- **Follow inteligente**: el mapa te sigue solo; al panear con el dedo deja de pelear contigo, y el botón de **mi posición** (⌖) lo reactiva.
- **Rumbo arriba** (🧭): rota el mapa según tu dirección de marcha, como un navegador; tócalo de nuevo para volver a norte-arriba.
- **Mapa nocturno** (🌙): oscurece el mapa para manejar de noche sin encandilarte.
- **Búsqueda de direcciones**: el campo de arriba busca calles y lugares (Photon, sobre datos de OpenStreetMap, sin cuenta ni API key). Los resultados se **sesgan por tu ubicación**, así que buscar "medellín" te da Medellín, Antioquia y no la de Filipinas. Tocar un resultado lo fija como destino.
- **Ruta a destino**: además de la búsqueda, mantén presionado cualquier punto del mapa. RevScope calcula la ruta en carro (OSRM), la dibuja y muestra **distancia y tiempo estimado** en un chip; la ✕ del chip la quita. Con destino fijado, el botón de navegación lanza turn-by-turn en Google Maps hacia ese punto.
- **Navegación paso a paso propia**: el botón **Navegar** del chip de ruta arranca la guía dentro de RevScope, sin salir a otra app.
  - Arriba aparece la **maniobra que viene**: una flecha grande, la distancia y el nombre de la calle.
  - Abajo, lo que **falta del viaje**: distancia, tiempo y hora estimada de llegada.
  - Las instrucciones se **dicen en voz alta en español** por el mismo canal que el resto de los avisos, así que se oyen en el intercomunicador del casco. Cada giro se anuncia a 400 m, a 150 m y justo al llegar, sin repetirse entre medio.
  - Si te sales de la ruta, avisa una vez. Todavía **no recalcula solo**: hay que volver a fijar el destino.
  - Necesita un **viaje activo** (OBD o GPS), porque de ahí sale la señal. Si no hay viaje, la app te lo dice en vez de quedarse muda.
  - La ✕ del banner termina la navegación.
- **Radares de velocidad** cercanos: cada uno con un marcador y un círculo semitransparente del **radio de aviso configurado** (default 250 m — ajustable en [Configuración → Radares](configuracion.md#radares-de-velocidad)).
- **Velocidad actual** en una insignia en la esquina inferior izquierda.
- Botón flotante **"Abrir en Maps"** (ícono de navegación): sin destino fijado, abre tu app de navegación externa en tu última posición conocida.

Sin ningún viaje activo, el mapa se centra en tu última ubicación conocida por el sistema, a la espera de que arranques un viaje (OBD o GPS).

## Taller

El centro de diagnóstico y mantenimiento, organizado en tres secciones. Las herramientas que necesitan el adaptador conectado aparecen atenuadas y con la leyenda "Requiere conexión" cuando no hay enlace; las que no lo necesitan (perfiles, informes guardados, mantenimiento) siempre están disponibles.

### Estado

- **Vehículo al día** — grid con el estado de SOAT, tecnomecánica, pico y placa de hoy, multas, todo riesgo, mantenimiento y licencia de conducción del vehículo activo, cada uno con su estado en ícono y texto («OK», «Atención», «Vencido», «Sin configurar»), no solo con color. Las fechas de vencimiento y la ciudad de pico y placa se configuran en **Perfiles de vehículo** (sección Vehículo, más abajo); el detalle de las reglas de pico y placa está en [Configuración → Pico y placa](configuracion.md#pico-y-placa).
- **Chequeo de salud** — un botón, "Escanear ahora": en 10-15 segundos con el motor encendido lee códigos de falla, los monitores de readiness (qué tan lista está la tecnomecánica), fuel trims, sensor de oxígeno, batería y temperatura, y devuelve un diagnóstico interpretado en español con semáforo por ítem. El resultado se guarda como histórico y se puede compartir como imagen (📷, para enviarle al mecánico) o exportar en CSV.

### Diagnóstico

- **Códigos de falla (DTC)** — lee los códigos activos, pendientes y permanentes (cada uno con su etiqueta), el estado del testigo de falla (MIL) y el freeze frame con el código que lo guardó; los explica con IA (si configuraste una API key) y permite borrarlos de la memoria del ECU. Funciona con el viaje grabando o después de finalizarlo, sin desconectar: el sondeo se pausa solo mientras dura la lectura.
  - **Borrar DTCs** pide confirmación y avisa qué se pierde: los monitores de readiness quedan incompletos (la tecnomecánica puede rechazar el vehículo hasta completar ciclos de manejo), el freeze frame se borra y el código vuelve si la falla sigue. Solo borra con el vehículo detenido, con la misma regla que la herramienta `borrar_dtc` del MCP (velocidad 0 con una lectura de menos de 2 s). Si la ECU no reporta velocidad reciente (pasa en muchas motos o sin sondeo), la app pide marcar «Confirmo que el vehículo está detenido». Después del borrado relee la ECU y muestra los códigos de antes y de después: si el código sigue, lo dice; si la ECU rechaza el borrado (7F 04 22), indica apagar el motor y dejar el contacto puesto.
- **Mezcla y combustión** — fuel trims cortos y largos, hasta cuatro sensores de oxígeno, lambda comandado y flujo de aire (MAF), todo interpretado en vivo con un diagnóstico por regla (mezcla pobre/rica, sensor perezoso, etc.) sin necesitar IA ni conexión a internet.
- **Gráficas de sensores** — cualquier PID que el vehículo soporte, en una curva en tiempo real de los últimos 30 s con ejes y unidades. El botón de la barra abre la **Captura rápida**: eliges de 1 a 6 PIDs (o el atajo «Pedal y mariposa (49, 4A, 11)») y la app los lee lo más rápido que permita el adaptador, con la tasa medida en Hz, una gráfica de los últimos 10 s y exportación a CSV con tiempos en milisegundos. Sirve para diagnosticar un sensor que cambia rápido, como el pedal del acelerador: con el contacto puesto y el motor apagado (o en ralentí), barre el pedal despacio de 0 a 100 %. Mientras dura, los demás gauges quedan en pausa. Detalle y salvaguardas en [Configuración → Muestreo OBD](configuracion.md#muestreo-obd).
- **Escáner avanzado (Mode 22)** — barre direcciones para descubrir PIDs propietarios del fabricante que no están documentados en el estándar OBD2 (por ejemplo, modos de manejo o sensores adicionales de motos).
- **Onda sensor O2** — gráfica en vivo de los últimos 60 segundos de voltaje del sensor de oxígeno, con la banda visual de mezcla pobre/rica y un contador de cruces por minuto por el umbral de conmutación (un sensor sano cruza con frecuencia).
- **Resultados a bordo (Mode 06)** — las pruebas de monitoreo interno que corre el propio fabricante, agrupadas por identificador (MID) con su valor, límites y si pasó o falló — útil para comparar antes y después de una reparación.
- **Mecánico IA** — un chat con contexto real: cada pregunta se envía junto con los datos actuales de tu vehículo (perfil, último chequeo de salud, códigos activos si hay, lecturas en vivo si estás conectado, y tus últimos viajes). Requiere una API key de IA configurada.

### Vehículo

- **Analizador de marchas** — calibra la relación entre RPM y velocidad para cada marcha del vehículo, para que el indicador de marcha del panel de Conducir sea preciso.
- **Perfiles de vehículo** — todos los vehículos guardados: tipo (carro/moto), combustible, línea roja y RPM máximo del gauge, VIN, placa, ciudad de pico y placa, fechas de documentos y el adaptador Bluetooth vinculado. Eliminar un vehículo pide confirmación; sus viajes se conservan en el historial (filtro «Todos»).
- **Mantenimiento** — ítems por kilometraje (aceite, llantas, batería, kit de arrastre en motos…) con el odómetro del vehículo editable, barra de progreso del intervalo y botón "Registrar servicio" por ítem.
- **Verificación de kilometraje** — lee el odómetro real reportado por el ECU (solo vehículos que lo expongan por OBD2 estándar) y guarda un histórico para detectar manipulación: alerta si el odómetro retrocede o si avanza mucho menos que la distancia GPS registrada por la app.
- **Comparar velocímetros** — velocidad del OBD contra la del GPS lado a lado, en vivo, con el promedio acumulado de la sesión — para medir cuánto sobre-marca tu velocímetro de fábrica.

## Viajes

El historial de sesiones grabadas, con filtros y comparación.

- **Filtros por vehículo**: fila de chips ("Todos", uno por cada perfil guardado, "Sin vehículo") para ver solo los viajes de una moto o carro en particular.
- Cada viaje en la lista muestra fecha, adaptador (o "GPS" si fue un viaje sin adaptador), duración, RPM máximo, velocidad máxima y distancia.
- **Comparar A/B**: toca el botón «Comparar» (flechas) en un viaje para marcarlo como "A", luego tócalo en otro para abrir la comparación lado a lado. En la gráfica de velocidad, A va en línea continua y B en línea discontinua.
- **Borrar un viaje** pide confirmación con su fecha y distancia: se borran también su telemetría, la ruta GPS y las vueltas.
- Sin viajes guardados, la pestaña lo dice y ofrece «Iniciar un viaje», que lleva a Conducir.

Al abrir un viaje se muestra el **reporte completo**:

- **Estadísticas**: duración, distancia, puntos registrados, velocidad máxima/promedio, temperatura máxima, RPM máximo/promedio, aceleración máxima, y mejores tiempos de 0-60/0-100 si el viaje los tuvo.
- **Costo estimado en pesos** del combustible consumido, cuando hay datos suficientes para calcularlo.
- **Tarjeta Eco** con el puntaje 0-100 del viaje y su desglose: aceleradas y frenadas bruscas, segundos con RPM alto sostenido, y el bonus por conducción en crucero.
- **Mapa real del recorrido** sobre OpenStreetMap, coloreado por velocidad (azul lento, amarillo medio, rojo rápido).
- **Racing line**: la misma ruta dibujada como trazo con puntos rojos marcando las frenadas fuertes.
- **Círculo de fricción**: cuánta fuerza G lateral y longitudinal usaste, con anillos de referencia en 0.5G y 1.0G.
- **Dispersión acelerador × G**: cada muestra de porcentaje de acelerador contra la fuerza G resultante, para ver qué tan pareja fue la entrega de potencia.
- **Vueltas** (si el viaje tuvo Modo Pista activo): tiempo de cada vuelta, con la mejor marcada con ★, y para cada una el pico de G, la inclinación máxima (lean) y el pulso máximo si el reloj estaba transmitiendo.
- **Curvas con ejes**: RPM, velocidad y — si el reloj transmitió pulso — ritmo cardíaco, cada una en su propia gráfica con eje de tiempo en mm:ss.
- **Exportar por métrica**: menú con Velocidad, RPM, Temperatura, Ritmo cardíaco, IMU, GPS o "Todo", cada uno como su propio CSV.
- **Reasignar vehículo**: ícono de carro en la barra superior para cambiar a qué perfil quedó ligado el viaje (útil si olvidaste activar el vehículo correcto antes de salir).
- **Tarjeta 📷**: genera una imagen resumen del viaje para compartir por WhatsApp/redes.

## Ajustes

Cada fila navega o ajusta una función; el detalle completo de cada sección — con los valores por defecto — está en [docs/configuracion.md](configuracion.md).

| Sección | Qué controla |
|---|---|
| Vehículo | Vehículo activo y si se pregunta al iniciar la app |
| Herramientas | Acceso rápido a Perfiles de vehículo |
| Alertas de audio y vibración | Interruptor general, TTS y qué categorías se anuncian por voz |
| Alertas personalizadas por PID | Umbrales propios sobre cualquier parámetro (JSON) |
| Combustible | Precios por tipo de gasolina/diésel para calcular el costo del viaje |
| Radares de velocidad | Descarga y actualización de la base de radares |
| Inteligencia artificial | Proveedor de IA, API key, modelo y prueba de conexión |
| Detección de caída | Contacto de emergencia y activación de la alarma con SMS |
| Muestreo OBD | Frecuencia de lectura de sensores (Estándar 2 s, 1 s, 500 ms, 250 ms o Máximo) y duración máxima de la captura rápida |
| Servidor MCP (red local) | Exponer el estado del vehículo a asistentes de IA de tu red WiFi |
| Copia de seguridad | Exportar/importar todos tus datos, y respaldo automático semanal |
| PIDs personalizados | Definiciones extra de parámetros del fabricante |

---

Siguiente: ajusta cada función a tu gusto en [Configuración](configuracion.md), o revisa la [FAQ](faq.md) si algo no funciona como esperabas.
