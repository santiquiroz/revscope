# RevScope 🏍️🚗

> Convierte tu teléfono Android en el tablero digital de tu carro o moto — gratis, sin suscripciones y sin internet.
>
> *English: RevScope is a free, open-source OBD2 racing telemetry suite for Android. Technical docs below and in [PLAN.md](PLAN.md).*

[![Release](https://img.shields.io/github/v/release/santiquiroz/revscope?color=E8FF00&label=descargar)](https://github.com/santiquiroz/revscope/releases)
[![License](https://img.shields.io/badge/licencia-Apache%202.0-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://developer.android.com)
[![Tests](https://img.shields.io/badge/tests-124%20✓-brightgreen.svg)]()

---

## ¿Qué es esto?

Tu carro o moto tiene un computador interno que sabe todo lo que pasa en el motor: revoluciones, velocidad, temperatura, fallas. RevScope se conecta a ese computador usando un **adaptador económico** que se consigue por internet (~$10–20 USD) y te muestra todo en el teléfono, en tiempo real, con pantalla estilo tablero de carreras.

Fue creada y probada en el mundo real con una **TVS Apache 160 4V** y un **Mazda CX-30**.

## ¿Qué necesitas?

1. **Un teléfono Android** (Android 8 o más nuevo)
2. **Un adaptador OBD2 Bluetooth** — busca "ELM327 Bluetooth" o el recomendado: **Vgate iCar Pro**. Se conecta en un puerto que casi todos los carros desde ~2007 (y muchas motos con inyección) tienen bajo el tablero
3. Nada más. No necesitas internet, ni cuenta, ni pagar nada

## ¿Qué hace por ti?

**Para el día a día:**
- 📊 **Tablero en vivo**: revoluciones, velocidad, temperatura del motor, voltaje de la batería
- 🔊 **Te avisa con voz** (por el parlante o el intercom del casco) si el motor se está calentando, si la batería está baja o si te pasaste de revoluciones
- 🔧 **Te explica las fallas**: si se prende el "check engine", la app lee el código, te dice qué significa en palabras simples y qué estaba haciendo el motor en el momento exacto de la falla
- 🔋 **Te salva de quedarte varado**: vigila el voltaje de la batería y avisa antes de que muera
- 🗺️ **Guarda todos tus viajes**: distancia, velocidades, ruta en mapa, todo automático

**Para los que les gusta la velocidad:**
- ⏱️ **Cronómetro 0-100 automático**: arrancas y él solo mide cuánto te demoraste — y te lo dice por voz
- 🏁 **Modo pista**: marca una línea de meta y cronometra tus vueltas solas, anunciándolas por voz
- 📐 **Fuerzas G e inclinación**: usa los sensores del teléfono para medir qué tan fuerte frenas, qué tan rápido tomas las curvas y cuánto inclinas la moto
- 🎨 **Mapa de tu ruta coloreado por velocidad** y análisis de frenadas, como los equipos de carreras profesionales
- 🚗 **Android Auto**: mira los datos en la pantalla del carro

**Extra:** la app aprende tu vehículo — detecta en qué marcha vas sin que se lo digas, reconoce tu carro por el número de serie y ajusta los relojes a cada vehículo.

## Instalación (5 minutos)

1. **Descarga la app**: entra a [Releases](https://github.com/santiquiroz/revscope/releases), baja el archivo `revscope-vX.X.X.apk` y ábrelo (el teléfono te pedirá permitir "instalar de origen desconocido" — acepta; la app es de código abierto, puedes revisar cada línea)
2. **Conecta el adaptador** en el puerto OBD2 del vehículo (usualmente bajo el volante; en motos suele estar bajo el asiento o cerca de la batería)
3. **Enciende el vehículo** (al menos el switch)
4. **Empareja el adaptador** en el Bluetooth del teléfono, como cualquier audífono (el Vgate aparece como `Android-Vlink`, clave `1234`)
5. **Abre RevScope** → toca el ícono de Bluetooth → toca tu adaptador → listo

La próxima vez la app se conecta sola al abrirla, y se reconecta sola si apagas el vehículo para tanquear.

## Preguntas frecuentes

**¿Necesito internet?** No. Todo funciona sin conexión. Solo la explicación de fallas con inteligencia artificial (opcional) usa internet.

**¿Sirve para moto?** Sí — de hecho se desarrolló sobre una moto. Necesita ser de inyección electrónica con puerto de diagnóstico.

**¿Puede dañar mi vehículo?** No. La app solo **lee** información. La única acción que escribe es borrar códigos de falla, y solo si tú lo pides con confirmación.

**¿Cuánto cuesta?** Nada. Código abierto, sin publicidad, sin suscripciones, sin recolección de datos — todo se queda en tu teléfono.

**¿Por qué no está en Play Store?** Por ahora se instala directo (es un proyecto personal de código abierto). La pantalla para Android Auto requiere activar "orígenes desconocidos" en la configuración de desarrollador de Android Auto (instrucciones en las [notas de la versión 1.0.0](https://github.com/santiquiroz/revscope/releases/tag/v1.0.0)).

**Consejo si vas a medir fuerzas G en moto:** monta el teléfono en un soporte **firme** — si el soporte vibra, los datos salen sucios.

---

## Para desarrolladores 👩‍💻

Suite de telemetría OBD2 en Kotlin: Jetpack Compose + Material 3, Hilt, Room, Coroutines/Flow, Vico, Car App Library. Min SDK 26.

```
core/
├── obd/            # Transporte BT SPP, protocolo ELM327, PID registry, telemetría,
│   ├── session/    #   ObdSessionManager (conexión a nivel app), foreground service
│   ├── motion/     #   IMU: G en marco vehículo (rotación+bearing GPS), lean auto-calibrado
│   ├── track/      #   Lap timer: cruce de línea de meta por intersección de segmentos
│   └── alerts/     #   Audio/TTS/vibración con umbrales por vehículo
├── intelligence/   # Gear learning (EMA), anomalías (Welford 3σ), DTC con Claude (opcional)
└── data/           # Room v7 (sesiones, telemetría, GPS, IMU, vueltas, perfiles), DataStore
feature/            # dashboard, sensors, dtc, session (reportes), vehicle, settings, auto
```

Decisiones clave: conexión y pipeline viven a nivel aplicación (sobreviven a la UI y alimentan Android Auto); un solo mutex serializa el half-duplex del ELM327; batching multi-PID por frame CAN; todo degrada con gracia (K-line sin batching, IA por tiers de RAM, GPS/IMU opcionales por permisos).

```bash
git clone https://github.com/santiquiroz/revscope.git
cd revscope && ./gradlew assembleDebug   # 124 tests: ./gradlew :core:obd:testDebugUnitTest
```

### Roadmap
- [x] Todo lo listado arriba (v1.2.0)
- [ ] Modo de manejo del fabricante (vía escáner Modo 22 incluido)
- [ ] Comparación de viajes (misma ruta, run A vs B)
- [ ] Correlación acelerador×G para tuning
- [ ] Botón multimedia del manubrio/casco (MediaSession)
- [ ] Transportes BLE y WiFi
- [ ] Pre-Play-Store: keystore, migraciones Room, cifrado de API key, capa Repository

Contribuciones bienvenidas — especialmente tablas de PIDs propietarios descubiertas con el escáner Modo 22 y pruebas en más vehículos.

## Licencia

Apache 2.0 — ver [LICENSE](LICENSE).

*Agradecimientos: [AndrOBD](https://github.com/fr3ts0n/AndrOBD) · [Vico](https://github.com/patrykandpatrick/vico) · SAE J1979 · Welford (1962)*
