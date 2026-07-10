# RevScope 🏍️🚗

> Convierte tu teléfono Android en el tablero digital de tu carro o moto — gratis, sin suscripciones y sin internet.
>
> *English: RevScope is a free, open-source OBD2 racing telemetry suite for Android. Technical docs below and in [PLAN.md](PLAN.md).*

[![Release](https://img.shields.io/github/v/release/santiquiroz/revscope?color=E8FF00&label=descargar)](https://github.com/santiquiroz/revscope/releases)
[![License](https://img.shields.io/badge/licencia-Apache%202.0-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://developer.android.com)
[![Tests](https://img.shields.io/badge/tests-305%20✓-brightgreen.svg)]()

---

## ¿Qué es esto?

Tu carro o moto tiene un computador interno que sabe todo lo que pasa en el motor: revoluciones, velocidad, temperatura, fallas. RevScope se conecta a ese computador usando un **adaptador económico** que se consigue por internet (~$10–20 USD) y te muestra todo en el teléfono, en tiempo real, con pantalla estilo tablero de carreras.

Fue creada y probada en el mundo real con una **TVS Apache 160 4V** y un **Mazda CX-30**.

Ya no es solo un tablero: es un taller de bolsillo con historial completo — SOAT, tecnomecánica, pico y placa, radares, salud del motor y más. Y si tienes más de un vehículo (carro y moto, o dos motos), cada uno lleva su propio historial por separado dentro de la misma app.

## ¿Qué necesitas?

1. **Un teléfono Android** (Android 8 o más nuevo)
2. **Un adaptador OBD2 Bluetooth** — busca "ELM327 Bluetooth" o el recomendado: **Vgate iCar Pro**. Se conecta en un puerto que casi todos los carros desde ~2007 (y muchas motos con inyección) tienen bajo el tablero
3. Nada más. No necesitas internet, ni cuenta, ni pagar nada

## ¿Qué hace por ti?

La app se organiza en **5 pestañas**: Conducir, Mapa, Taller, Viajes y Ajustes. Cada una tiene un trabajo claro.

**🏍️🚗 Garaje — varios vehículos, una sola app**
- La primera vez te pregunta qué vehículo vas a usar (o puedes decirle que no vuelva a preguntar)
- Cambia de vehículo en dos toques con el selector flotante — cada uno guarda su propio historial, alertas, kilometraje y documentos por separado
- Si solo tienes un vehículo, el selector simplemente no estorba

**📋 Vehículo al día**
- Semáforo con SOAT, tecnomecánica, licencia de conducción y seguro todo riesgo — verde, amarillo o rojo según cuánto falta para el vencimiento
- **Pico y placa de Medellín** integrado: te dice si hoy puedes circular con tu placa según las reglas de rotación vigentes
- Consulta directa de **multas SIMIT** por placa
- Una notificación al día con lo que vence pronto o el pico y placa de hoy, sin tener que abrir la app

**🔧 Taller — diagnóstico de verdad, no solo un lector de códigos**
- **Chequeo de salud de un toque**: revisa readiness, DTCs y valores clave, interpreta con reglas en español y arma un informe que puedes **compartir como imagen** con tu mecánico
- **Mezcla en vivo**: corrección de combustible a corto y largo plazo interpretada en tiempo real (rico, pobre, normal)
- **Onda del sensor O2 en vivo**, con conteo de cruces por minuto, para ver si el sensor sigue sano sin desarmar nada
- **Mode 06**: resultados de los monitores a bordo del fabricante (pass/fail), útil para comparar antes y después de una reparación
- **DTC explicado con IA** (opcional, requiere internet): qué significa el código y qué estaba haciendo el motor en el momento exacto de la falla
- **Escáner Modo 22**: descubre PIDs propietarios del fabricante que el estándar no cubre
- **Analizador de marchas**: la app aprende en qué marcha vas sin que se lo digas
- **Mantenimiento por kilometraje**: aceite, filtros, lo que programes — avisa cuando se acerca el km, con odómetro propio por vehículo

**📡 Radares**
- Base de datos de cámaras de velocidad combinando **OpenStreetMap** (mapeo comunitario) y el **registro oficial de la ANSV**, sin duplicados
- Se actualiza sola cada semana alrededor de por dónde más manejas; ya descargada, los avisos funcionan **sin internet**
- Aviso por voz al acercarte ("Radar a 300 metros, límite 60")

**🔊 Alertas de voz por categoría**
- Elige qué te avisa por voz — sobrecalentamiento, batería baja, zona roja de RPM, radares, anomalías del motor — cada categoría se prende o apaga por separado en Ajustes

**🆘 Detección de caída (moto) — beta**
- Si detecta un impacto fuerte seguido de inmovilidad, cuenta 60 segundos con alarma y un botón grande "Estoy bien" para cancelar
- Si nadie cancela, manda un SMS con tu última ubicación al contacto que configures
- Apagada por defecto — actívala solo después de configurar el contacto de emergencia, y pruébala bien antes de confiar en ella (ver FAQ)

**🗺️ Mapa en vivo y viajes**
- Tu posición y ruta se dibujan sobre calles reales (OpenStreetMap) mientras manejas, con los radares guardados encima
- El viaje se cierra solo cuando apagas el motor, con reconexión inteligente para no drenar la batería del teléfono en segundo plano
- **Costo del viaje en pesos colombianos** (combustible estimado) y **eco-score** con el desglose de qué lo subió o bajó
- Compara dos viajes lado a lado (misma ruta, vuelta A vs B) y revisa la relación acelerador↔G para afinar tu manejo

**💾 Copias de seguridad**
- Automática cada semana — queda en tu carpeta de Descargas, conservando solo las últimas 4
- Manual cuando quieras, guardada donde tú elijas
- Ninguna de las dos incluye tu clave de API, por seguridad

**📤 Para compartir**
- Tarjeta de resumen de viaje lista para redes sociales (recorrido + estadísticas grandes)
- Informe de salud del taller como imagen

**⌚ Galaxy Watch y 🚗 Android Auto**
- Con un Galaxy Watch cerca, el pulso del piloto queda grabado junto a la telemetría del viaje
- Android Auto básico: mira los datos principales en la pantalla del carro

**Para los que les gusta la velocidad:**
- ⏱️ **Cronómetro 0-100 automático**: arrancas y él solo mide cuánto te demoraste — y te lo dice por voz
- 🏁 **Modo pista**: marca una línea de meta y cronometra tus vueltas solas, anunciándolas por voz
- 📐 **Fuerzas G e inclinación**: usa los sensores del teléfono para medir qué tan fuerte frenas, qué tan rápido tomas las curvas y cuánto inclinas la moto
- 🎨 **Mapa de tu ruta coloreado por velocidad** y análisis de frenadas, como los equipos de carreras profesionales

## Capturas de pantalla

<!-- TODO: screenshots — pendientes de capturar en dispositivo (ver docs/screenshots/) -->

## Instalación (5 minutos)

1. **Descarga la app**: entra a [Releases](https://github.com/santiquiroz/revscope/releases), baja el archivo `revscope-vX.X.X.apk` y ábrelo (el teléfono te pedirá permitir "instalar de origen desconocido" — acepta; la app es de código abierto, puedes revisar cada línea)
2. **Conecta el adaptador** en el puerto OBD2 del vehículo (usualmente bajo el volante; en motos suele estar bajo el asiento o cerca de la batería)
3. **Enciende el vehículo** (al menos el switch)
4. **Empareja el adaptador** en el Bluetooth del teléfono, como cualquier audífono (el Vgate aparece como `Android-Vlink`, clave `1234`)
5. **Abre RevScope** → toca el ícono de Bluetooth → toca tu adaptador → listo

La primera vez la app también te explica y pide los permisos que necesita (ubicación, notificaciones, Bluetooth) y te pregunta qué vehículo vas a usar.

La próxima vez la app se conecta sola al abrirla, y se reconecta sola si apagas el vehículo para tanquear.

## Preguntas frecuentes

**¿Necesito internet?** No. Todo funciona sin conexión. Solo la explicación de fallas con inteligencia artificial (opcional) usa internet.

**¿Sirve para moto?** Sí — de hecho se desarrolló sobre una moto. Necesita ser de inyección electrónica con puerto de diagnóstico.

**¿Puede dañar mi vehículo?** No. La app solo **lee** información. La única acción que escribe es borrar códigos de falla, y solo si tú lo pides con confirmación.

**¿Cuánto cuesta?** Nada. Código abierto, sin publicidad, sin suscripciones, sin recolección de datos — todo se queda en tu teléfono.

**¿Por qué no está en Play Store?** Por ahora se instala directo (es un proyecto personal de código abierto). La pantalla para Android Auto requiere activar "orígenes desconocidos" en la configuración de desarrollador de Android Auto (instrucciones en las [notas de la versión 1.0.0](https://github.com/santiquiroz/revscope/releases/tag/v1.0.0)).

**¿El pico y placa sirve en mi ciudad?** Por ahora viene cargado con las reglas de Medellín. Las reglas son un archivo editable, así que si vives en otra ciudad puedes cargar las tuyas mientras se agregan más ciudades de fábrica.

**¿De dónde salen los radares?** De dos fuentes combinadas: mapeo comunitario de OpenStreetMap y el registro oficial de la ANSV. Se actualizan solas una vez por semana; después de la primera descarga, los avisos funcionan sin internet.

**¿Cómo pruebo la detección de caída sin que le llegue un SMS a alguien?** En Ajustes hay un botón "Probar" que simula toda la alarma y la cuenta regresiva igual que una caída real, pero nunca envía el mensaje. Es la forma recomendada de probarla — de todos modos sigue siendo beta: pruébala bien en la vía antes de confiar en ella.

**¿Dónde quedan las copias de seguridad automáticas?** En la carpeta Descargas/RevScope del teléfono, una por semana, conservando solo las últimas 4. Las manuales quedan donde tú elijas al exportarlas. Ninguna de las dos incluye tu clave de API.

**Consejo si vas a medir fuerzas G en moto:** monta el teléfono en un soporte **firme** — si el soporte vibra, los datos salen sucios.

---

## Para desarrolladores 👩‍💻

Suite de telemetría OBD2 en Kotlin: Jetpack Compose + Material 3, Hilt, Room, Coroutines/Flow, Vico, Car App Library. Min SDK 26.

```
core/
├── common/         # Utilidades compartidas
├── obd/            # Transporte BT SPP, protocolo ELM327, PID registry, telemetría,
│   ├── session/    #   ObdSessionManager (conexión a nivel app) + VoltagePoller/MilWatcher/SessionAggregator
│   ├── motion/     #   IMU: G en marco vehículo (rotación+bearing GPS), lean auto-calibrado
│   ├── track/      #   Lap timer: cruce de línea de meta por intersección de segmentos
│   ├── cameras/    #   Radares OSM + ANSV, dedupe y refresco semanal
│   ├── safety/     #   Detección de caída (máquina de estados IMU+velocidad) y respuesta con SMS
│   ├── legal/      #   Motor de pico y placa por reglas de ciudad (JSON editable)
│   └── alerts/     #   Audio/TTS/vibración con umbrales por vehículo y categoría
├── intelligence/   # Gear learning (EMA), anomalías (Welford 3σ), estilo de manejo, DTC con Claude (opcional)
└── data/           # Room v13 (perfiles, sesiones, telemetría, GPS, IMU, HR, vueltas, documentos, radares...), backup, DataStore
feature/            # dashboard, gear, sensors, dtc, session (reportes/comparación), vehicle (garaje + al día), settings, auto, workshop, map
wear/               # App Wear OS standalone — streaming de pulso al teléfono
```

Decisiones clave: conexión y pipeline viven a nivel aplicación (sobreviven a la UI y alimentan Android Auto); un solo mutex serializa el half-duplex del ELM327; batching multi-PID por frame CAN; todo degrada con gracia (K-line sin batching, IA por tiers de RAM, GPS/IMU opcionales por permisos).

```bash
git clone https://github.com/santiquiroz/revscope.git
cd revscope && ./gradlew assembleDebug   # 305 tests: ./gradlew :core:obd:testDebugUnitTest
```

### Roadmap
- [x] Todo lo listado arriba (v1.3.0)
- [ ] Modo de manejo del fabricante (vía escáner Modo 22 incluido)
- [ ] Botón multimedia del manubrio/casco (MediaSession)
- [ ] Transportes BLE y WiFi
- [ ] Más ciudades de fábrica para pico y placa (hoy: Medellín + reglas editables)
- [ ] Pre-Play-Store: keystore de firma real, capa Repository

Contribuciones bienvenidas — especialmente tablas de PIDs propietarios descubiertas con el escáner Modo 22 y pruebas en más vehículos.

## Licencia

Apache 2.0 — ver [LICENSE](LICENSE).

*Agradecimientos: [AndrOBD](https://github.com/fr3ts0n/AndrOBD) · [Vico](https://github.com/patrykandpatrick/vico) · SAE J1979 · Welford (1962)*
