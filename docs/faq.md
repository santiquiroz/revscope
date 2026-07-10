# Preguntas frecuentes (FAQ)

> Índice: [Adaptador](#adaptador-y-conexión) · [Uso diario](#uso-diario) · [Datos e IA](#datos-e-inteligencia-artificial) · [Otras plataformas](#otras-plataformas) · [Proyecto](#el-proyecto)
>
> Ver también: [Instalación](instalacion.md) · [Manual de usuario](manual-usuario.md) · [Configuración](configuracion.md) · [Desarrollo](desarrollo.md)

## Adaptador y conexión

### No me conecta el adaptador, ¿qué hago?

Primero confirma que el adaptador esté **emparejado en el Bluetooth del sistema Android**, no solo dentro de RevScope — sin ese paso previo no aparecerá en la lista. Luego revisa que el adaptador tenga corriente (el ELM327 se alimenta directo del puerto OBD2, así que el vehículo debe tener el switch en posición de contacto/ACC). Si sigue sin conectar, entra al escáner de adaptadores (pastilla superior → Adaptador → Administrar) y toca **"Elegir otro dispositivo"** para reintentar desde cero.

### Se conecta pero no veo ningún dato, ¿por qué?

Puede ser que el vehículo no soporte los PIDs que estás intentando ver (no todos los modelos exponen todos los parámetros), o que el motor esté apagado — muchos adaptadores se quedan "conectados" por Bluetooth aunque el ECU esté dormido sin contacto. Enciende el motor y espera unos segundos; si el problema persiste, prueba el [Escáner Mode 22](manual-usuario.md#taller) para ver si al menos hay respuesta del ECU a comandos crudos.

### ¿Qué adaptador debo comprar?

Cualquier **ELM327 por Bluetooth clásico** (perfil SPP) funciona. RevScope recomienda el **Vgate iCar Pro 2S** por su estabilidad y batería, pero cualquier ELM327 clásico de marca conocida sirve. Evita los adaptadores **BLE (Bluetooth Low Energy)** o **WiFi** — RevScope todavía no los soporta, sin importar cuán "compatible ELM327" digan ser en el empaque.

### ¿Funciona sin comprar ningún adaptador?

Sí, parcialmente: el botón **"Iniciar viaje GPS"** en Conducir graba ruta, velocidad, inclinación y fuerzas G usando solo los sensores del celular, con radares, pico y placa y detección de caída incluidos. Lo que no vas a tener sin adaptador es RPM, temperatura, marcha, códigos de falla ni ninguna herramienta de Taller que dependa del ECU.

## Uso diario

### ¿Mis viajes y perfiles se pierden al actualizar la app?

No debería pasar: cada versión que cambia la estructura de la base de datos incluye una migración real (aditiva, sin borrar tablas), verificada contra el esquema exportado antes de publicarse. Aun así, como buena práctica, activa la [copia de seguridad automática semanal](configuracion.md#copia-de-seguridad) (viene encendida por defecto) o exporta una copia manual antes de actualizar si quieres estar 100% tranquilo.

### ¿Gasta mucha batería tener la app abierta?

RevScope apaga sola la grabación, el GPS y los sensores en cuanto detecta que el motor se apagó (o, en un viaje GPS, tras varios minutos sin movimiento), así que dejar la app abierta durante todo el día sin conducir no debería drenar la batería de forma notable. Mientras hay un viaje activo sí usa GPS/Bluetooth de forma constante, como cualquier app de navegación.

### ¿Puedo ver la presión de las llantas?

No de forma estándar: la presión de llantas (TPMS) **no forma parte** del estándar OBD2 y la mayoría de vehículos no la expone por ahí — depende enteramente del fabricante. Si tu vehículo sí la publica por un PID propietario, puedes descubrirlo con el [Escáner Mode 22](manual-usuario.md#taller), definirlo como [PID personalizado](configuracion.md#pids-personalizados) y luego crearle una [alerta de umbral](configuracion.md#alertas-personalizadas-por-pid) para que te avise si sale de rango.

### ¿El kilometraje que muestra es el real?

RevScope puede leer el **odómetro del ECU** (PID `A6`) en la herramienta "Verificación de kilometraje" de Taller, pero ese PID solo lo exponen vehículos relativamente recientes (estándar J1979-DA, aprox. 2015 en adelante) — muchas motos no lo tienen. Cuando está disponible, RevScope guarda un histórico y avisa si el odómetro retrocede o si avanza mucho menos que la distancia GPS que la app viene registrando, como señal de posible manipulación.

### ¿Por qué el velocímetro del panel (OBD) y el del Mapa (GPS) no coinciden?

Es normal y esperado: casi todos los velocímetros de fábrica están calibrados para marcar **un poco más** de la velocidad real, y el GPS mide la velocidad real por posición. Usa **"Comparar velocímetros"** en Taller para medir en vivo cuánto se desvía el tuyo, en vía recta y velocidad constante.

## Datos e inteligencia artificial

### ¿Cuánto cuesta usar las funciones de IA?

Muy poco: cada explicación de código de falla o pregunta al Mecánico IA cuesta típicamente centavos de dólar, porque usa modelos pequeños y rápidos por defecto (`claude-haiku-4-5`, `gpt-5-mini` o `gemini-2.5-flash`, según el proveedor que elijas). Pagas directamente a tu proveedor con **tu propia API key** — RevScope no cobra nada ni intermedia el pago. Si prefieres no pagar nada, puedes conectar un servidor local (por ejemplo LM Studio en tu PC) como proveedor "Compatible OpenAI" y usar un modelo gratuito que corra en tu propio hardware.

### Activé el servidor MCP pero mi cliente de IA (Claude Desktop, etc.) no responde, ¿qué reviso?

En orden: (1) que el celular y el computador estén en la **misma red WiFi** (el servidor no funciona con datos móviles ni entre redes distintas); (2) que el interruptor **"Servidor MCP activo"** siga encendido — se apaga solo si pierde el WiFi; (3) que el token configurado en tu cliente coincida exactamente con el que muestra Ajustes; y (4) que RevScope siga abierto — el servidor vive dentro del proceso de la app, no como servicio independiente permanente.

### Los radares no aparecen o están desactualizados, ¿cómo los actualizo?

RevScope combina OpenStreetMap y el registro oficial de la ANSV, y se actualiza sola una vez por semana; puedes forzar una descarga inmediata con el botón **"Descargar radares de mi zona"** en Ajustes. Si un radar real no aparece, la forma de arreglarlo para todo el mundo (no solo para ti) es contribuirlo directamente a [OpenStreetMap](https://www.openstreetmap.org) — RevScope lee de esa base de datos abierta, no mantiene la suya propia.

### El pico y placa de mi ciudad no está en la lista, ¿qué hago?

Por ahora RevScope trae reglas listas para Medellín y Bogotá; Cali aparece en el listado pero sin rotación configurada todavía. El motor interno ya soporta reglas 100% personalizadas en formato JSON (ver [Configuración → Pico y placa](configuracion.md#pico-y-placa) para el esquema exacto), pero a la fecha de esta guía **todavía no existe un campo en Ajustes** para pegar ese JSON desde la app — es una limitación conocida, no un error tuyo.

## Otras plataformas

### ¿Tiene detección de caída confiable?

Es una función de apoyo, no un sistema de emergencias certificado: usa umbrales fijos de aceleración e inmovilidad (ver el detalle en [Configuración](configuracion.md#detección-de-caída)) y depende de que haya una sesión de grabación activa y buena señal en el momento del golpe. Puede tener falsos negativos (caídas que no detecta) y falsos positivos (frenadas fuertes que se sienten como impacto). Pruébala con el botón **"Probar (sin enviar SMS real)"** antes de confiar en ella, y nunca la trates como sustituto de conducir con precaución y el equipo de protección adecuado.

### ¿RevScope funciona con Android Auto?

Sí — mediante sideload, ya que la app no está en Google Play. Muestra velocidad, RPM, temperatura del motor y voltaje de batería en la pantalla del carro. Ver el paso a paso en [Instalación](instalacion.md#android-auto-opcional).

### ¿Y con un reloj inteligente?

Con **Galaxy Watch (Wear OS)** sí: hay un módulo aparte que transmite el ritmo cardíaco del reloj al teléfono, donde queda integrado a los gauges y a las vueltas del reporte de viaje. Se instala compilándolo del código y subiéndolo por `adb` — ver [Instalación](instalacion.md#reloj-galaxy-watch-opcional). No hay soporte hoy para Apple Watch ni Wear OS de otras marcas más allá del hardware genérico compatible.

### ¿Hay versión para iPhone?

No. RevScope es una app nativa de Android (Kotlin/Jetpack Compose) y no hay planes publicados de una versión iOS.

### ¿En qué idiomas está disponible?

Solo en **español** por ahora. Toda la interfaz, las alertas de voz y los mensajes de diagnóstico están en español de Colombia.

## El proyecto

### ¿Es gratis y de código abierto?

Sí, completamente. El código está publicado bajo licencia **Apache 2.0** — puedes usarlo, modificarlo y redistribuirlo libremente respetando los términos de esa licencia (incluida la atribución y el aviso de cambios).

### Encontré un error (bug), ¿cómo lo reporto?

Abre un **issue en GitHub** en el repositorio del proyecto, describiendo qué esperabas que pasara, qué pasó en realidad, y si puedes, el modelo de vehículo y adaptador que estabas usando. Entre más detalle, más rápido se puede reproducir y arreglar.

### Quiero ayudar a mejorar RevScope, ¿por dónde empiezo?

Revisa [docs/desarrollo.md](desarrollo.md) para la arquitectura del proyecto, cómo compilarlo y las guías paso a paso para agregar un PID, una regla de diagnóstico, una ciudad de pico y placa, un proveedor de IA o una herramienta MCP nueva. Un pull request con tests (el proyecto usa TDD para toda la lógica pura) es la forma más directa de contribuir.

### ¿Mis datos salen de mi celular?

No, salvo que tú lo decidas explícitamente: RevScope es 100% local — sin cuentas, sin nube propia y sin telemetría a terceros. Las únicas conexiones salientes son las que tú activas: descarga de radares (OpenStreetMap/ANSV), tu proveedor de IA si configuraste una API key, y el servidor MCP si lo enciendes (y ese solo escucha en tu red WiFi local, nunca sale a internet).

---

¿Tu pregunta no está aquí? Revisa el [Manual de usuario](manual-usuario.md) o [Configuración](configuracion.md), o abre un issue en GitHub.
