# Instalación

> Índice: [Requisitos](#requisitos) · [Instalar el APK](#instalar-el-apk) · [Primer arranque y permisos](#primer-arranque-y-permisos) · [Emparejar y conectar el adaptador](#emparejar-y-conectar-el-adaptador-obd2) · [Galaxy Watch](#reloj-galaxy-watch-opcional) · [Android Auto](#android-auto-opcional)
>
> Ver también: [Manual de usuario](manual-usuario.md) · [Configuración](configuracion.md) · [FAQ](faq.md) · [Desarrollo](desarrollo.md)

## Requisitos

| Requisito | Detalle |
|---|---|
| Sistema operativo | Android 8.0 (Oreo) o superior |
| Adaptador OBD2 | ELM327 por **Bluetooth clásico** (perfil SPP) |
| Recomendado | Vgate iCar Pro 2S — ver [por qué en la FAQ](faq.md) |
| Vehículo | con puerto OBD2 de diagnóstico (la mayoría de carros y motos desde ~2016 en Colombia) |
| Opcional | Galaxy Watch (Wear OS) para ritmo cardíaco, celular con Android Auto para el tablero del carro |

> ⚠️ RevScope solo soporta adaptadores **ELM327 Bluetooth clásico**. Los adaptadores BLE (Bluetooth Low Energy) o WiFi todavía no son compatibles — revisa la [FAQ](faq.md) antes de comprar uno.

## Instalar el APK

RevScope no está en Google Play todavía: se instala directamente desde los releases de GitHub.

1. Entra a [Releases](https://github.com/santiquiroz/revscope/releases/latest) y descarga el `.apk` más reciente.
2. Abre el archivo descargado.
3. Android bloqueará la instalación por venir de "orígenes desconocidos". Toca **Ajustes** en el aviso y activa **Permitir de esta fuente**.
4. Vuelve atrás y toca **Instalar**.

> 💡 Es el mismo aviso que sale con cualquier APK fuera de una tienda oficial — el instalador se genera directo desde el código fuente en GitHub (ver [desarrollo.md](desarrollo.md)).

## Primer arranque y permisos

La primera vez que abres RevScope aparece una pantalla de bienvenida que pide tres permisos, uno por uno, con botón **Permitir** en cada tarjeta:

| Permiso | Para qué lo usa RevScope |
|---|---|
| Ubicación | Grabar tus rutas y avisarte de radares |
| Notificaciones | El resumen de viaje y el aviso de pico y placa |
| Bluetooth | Conectar el adaptador OBD2 |

Puedes tocar **Empezar** aunque no concedas los tres — cada permiso solo desactiva la función que depende de él (sin Ubicación no hay mapa ni radares, sin Bluetooth no hay telemetría OBD). Todos se pueden cambiar después en Ajustes del sistema.

Si ya tienes vehículos guardados, RevScope pregunta enseguida **"¿Qué vehículo vas a usar?"** — la primera vez no aparece porque aún no hay ningún perfil creado (se crea desde **Taller → Perfiles de vehículo**). Ver el detalle del selector en el [manual de usuario](manual-usuario.md#selector-de-vehículo).

## Emparejar y conectar el adaptador OBD2

1. **Enchufa el ELM327** al puerto OBD2 del vehículo (bajo el timón en carros; en motos suele estar cerca del tanque o bajo el asiento).
2. **Empareja el adaptador desde Bluetooth del sistema Android** (Ajustes → Bluetooth del teléfono, NO dentro de RevScope): busca dispositivos, elige el que aparece como "OBDII"/"Vgate..." e ingresa el PIN si lo pide (usualmente `1234` o `0000`).
3. **Abre RevScope** y toca la pastilla superior (punto de color + ícono del vehículo + nombre).
4. En la hoja **"Selecciona"**, toca la fila inferior **"Adaptador: ‹estado› · Administrar"**.
5. Elige el dispositivo emparejado en la lista y espera a que el punto de color pase a verde.

> ⚠️ Si emparejaste el adaptador solo dentro de RevScope, sin pasar primero por el Bluetooth del sistema, no aparecerá en la lista — Android exige el emparejamiento a nivel de sistema.

RevScope recuerda la dirección del adaptador y reintenta la reconexión sola (con espera creciente) si se pierde el enlace mientras conduces, y puede vincularlo a un vehículo específico para activarlo solo al conectarte — ver [Perfiles de vehículo](manual-usuario.md#taller).

## Reloj Galaxy Watch (opcional)

RevScope puede transmitir el ritmo cardíaco desde un Galaxy Watch (Wear OS) al teléfono, donde queda grabado junto a la telemetría OBD/GPS/IMU y se muestra por vuelta en los reportes. La app del reloj **no se distribuye por separado**: se compila y se instala manualmente por `adb`.

1. En el reloj: **Ajustes → Acerca de** → toca la versión de software **5 veces** (activa modo desarrollador).
2. Activa **Depuración ADB** y **Depuración inalámbrica**, y anota la `IP:PUERTO` mostrada.
3. En tu PC, con el repo clonado, compila (el wrapper de Gradle no viene en el repo, ver [desarrollo.md](desarrollo.md#compilar)): `gradle :wear:assembleDebug`.
4. Conecta y sube el APK: `adb connect IP:PUERTO` seguido de `adb install wear/build/outputs/apk/debug/wear-debug.apk`.
5. Abre la app en el reloj — no hace falta configurar nada más: usa el **mismo `applicationId`** (`com.revscope.app`) que el teléfono y se comunica solo por la Wearable Data Layer API. Toca **▶** en el reloj para transmitir el pulso; aparece integrado a los gauges y a las vueltas del reporte de viaje.

## Android Auto (opcional)

Como RevScope no está en Google Play, mostrarlo en la pantalla del carro requiere sideload:

1. En la app **Android Auto** del teléfono, activa el **modo desarrollador** (toca varias veces la versión, igual que en Ajustes de Android) y habilita **"Agregar apps de fuentes desconocidas"**.
2. Con el teléfono conectado al carro (cable o inalámbrico), RevScope aparecerá disponible en la pantalla del vehículo.

En el carro verás un panel simple con **velocidad, RPM, temperatura del motor y voltaje de batería**, actualizado una vez por segundo (la librería de Android Auto no permite gauges libres fuera de apps de navegación). Si no hay conexión con el adaptador, aparece un botón **Reconectar** directamente en la pantalla del carro.

---

Siguiente paso: el [Manual de usuario](manual-usuario.md) recorre las cinco pestañas de la app, o salta directo a [Configuración](configuracion.md) si ya tienes todo conectado.
