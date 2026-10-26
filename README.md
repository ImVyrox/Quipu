# Quipu

App Android personal para gestionar **tareas** y **préstamos/deudas**, 100 % local: sin Internet y sin cuentas. Avisa con notificaciones cuando una tarea o un pago está por vencer.

> Un *quipu* era el sistema de cuerdas y nudos con el que en los Andes se llevaban las cuentas.

Hecha con **Kotlin + Jetpack Compose (Material 3) + Room**.

---

## Qué hace

### Tareas

- **Tareas normales**: título, descripción opcional, fecha y hora. Se pueden editar, marcar como completadas y eliminar (con confirmación).
  - **Vencida**: pendiente y con la fecha ya pasada.
  - **Próxima**: pendiente y vence dentro de las próximas 24 h.
- **Tareas diarias**: hábitos o pendientes que se repiten cada día (por ejemplo «Ir al gimnasio»).
  - No tienen hora: se pueden completar en cualquier momento del día y nunca aparecen como vencidas.
  - Al marcarla, cuenta como hecha **solo ese día**. Al día siguiente vuelve a estar pendiente sola, sin crearla otra vez.
  - Es un único registro que guarda la fecha de su última realización. Así el cálculo es correcto aunque la app pase días cerrada, porque no depende de que esté abierta a medianoche.
  - Se distinguen con la etiqueta **Diaria** y aparecen primero en la lista.
- Filtros **Todas / Pendientes / Completadas** y orden por fecha (ascendente o descendente).

### Finanzas (préstamos y deudas)

- Registros **«Me deben»** y **«Yo debo»** con persona, importe, fecha, vencimiento opcional y nota.
- **Organizadas por persona**: la pantalla principal muestra una lista de personas, cada una como una carpeta, con su saldo pendiente («Te debe… · Le debes…» y el neto).
- Al tocar una persona se abren todos sus registros, separados en:
  - *Me debe · pendientes* / *Me debe · cobrados*
  - *Yo le debo · pendientes* / *Yo le debo · pagados*
- Desde la vista de una persona se puede crear (con su nombre ya puesto), editar, eliminar y marcar como cobrado/pagado o devolver a pendiente. Se vuelve a la lista con la flecha ← o el botón Atrás.
- Búsqueda por nombre y filtros por tipo (Todos / Me deben / Debo) y estado.
- Aviso **Vencido** cuando un registro pendiente pasó su fecha de vencimiento.
- **Abonos (pagos parciales)**: si alguien te pidió Bs 300 y te devuelve Bs 200, toca **Registrar abono** y queda **Falta Bs 100**.
  - Se pueden registrar varios abonos; la tarjeta muestra «Abonado Bs 200 de Bs 300 · Falta Bs 100».
  - Un abono no puede superar lo que falta. Cuando los abonos cubren todo, el registro pasa solo a cobrado/pagado.
  - El historial de abonos aparece al editar el registro y se puede corregir mientras siga pendiente.
  - **Marcar cobrado/pagado** sigue disponible para saldar de una vez lo que falte.

**Personas sin duplicados**: dos nombres son la misma persona si coinciden ignorando mayúsculas, espacios sobrantes y tildes (`  José  Pérez ` = `jose perez`). Para distinguir personas parecidas basta con añadir un detalle: `Juan (primo)` y `Juan P.` son personas distintas. El formulario avisa cuando un nombre se va a agrupar con uno existente.

### Resumen

- Totales pendientes: **me deben**, **debo** y **balance neto**.
- Número de personas con saldo y de registros pendientes.
- Saldo por persona.
- Todo se calcula a partir de los registros guardados: de cada pendiente cuenta lo que falta (importe menos abonos); los ya pagados o cobrados no cuentan.

### Recordatorios (notificaciones)

Llegan aunque la app esté cerrada y se vuelven a programar solos tras reiniciar el teléfono o cambiar la hora.

- **Tareas con fecha y hora**: aviso a la hora exacta o 5, 10, 15 o 30 minutos, 1, 2 o 3 horas, o 1 día antes (predeterminado: 30 minutos).
- **Tareas diarias**: aviso a la hora que elijas (predeterminado: 20:00), solo si ese día todavía no la completaste.
- **Préstamos y deudas** pendientes con vencimiento: aviso el mismo día o 1, 2, 3 o 7 días antes, a la hora que elijas (predeterminado: 1 día antes a las 09:00). Indica lo que falta por pagar o cobrar.

### Ajustes (⚙ en cualquier pantalla)

- **Tema**: igual que el sistema, claro u oscuro; **color principal** (verde azulado, azul, morado, naranja o rosa) o los colores del fondo de pantalla (Material You, Android 12+).
- **Recordatorios**: activar o desactivar cada tipo, anticipación y horas.
- **Sonido y vibración**: sonido predeterminado, silencioso o un tono a elección; vibrar sí/no; botón **Probar notificación**.
- **Permisos**: estado de notificaciones, alarmas exactas y batería, con acceso directo a cada ajuste de Android.
- **Moneda**: boliviano (BOB, predeterminada), dólar, euro y pesos/monedas de Argentina, Brasil, Chile, Colombia, México, Perú, Paraguay y Uruguay. Cambiar la moneda solo cambia el símbolo; **no convierte** importes.
- **Datos de demostración** opcionales. Llevan una marca interna y se borran con un botón sin tocar tus datos reales.

### Importes

Se guardan en centavos (enteros), así que no hay errores de redondeo. Se escriben como `150`, `150.50` o `150,50`, sin separador de miles, y se muestran como `Bs 1.234,50`.

---

## Dispositivos compatibles

| Requisito | Valor |
|---|---|
| Versión mínima | **Android 11** (API 30) |
| Versión objetivo | Android 15 (API 35) |
| Tipo de dispositivo | Teléfonos Android (interfaz pensada para uso vertical con una mano) |
| Permisos | Notificaciones, alarmas exactas y arranque tras reinicio (solo para los recordatorios) |
| Conexión a Internet | No se necesita |
| Google Play Services | No se necesita |

Funciona en cualquier teléfono con **Android 11 o superior**: Xiaomi/Redmi/POCO (MIUI y HyperOS), Samsung (One UI), Motorola, Pixel, etc. Se diseñó pensando en el **Xiaomi Redmi Note 11**.

Probado en el emulador de Android Studio (teléfono mediano, Android API 37). En tablets debería abrirse, pero la interfaz no está optimizada para pantallas grandes. Los teléfonos con Android 10 o anterior no pueden instalarla.

---

## Privacidad y datos

- Sin acceso a Internet. Los únicos permisos son para los recordatorios:
  - `POST_NOTIFICATIONS`: mostrar los avisos (Android 13+ lo pide al activarlos).
  - `SCHEDULE_EXACT_ALARM`: avisar al minuto exacto. Sin él, Android puede retrasar los avisos unos minutos.
  - `RECEIVE_BOOT_COMPLETED`: volver a programar los avisos tras reiniciar el teléfono.
- Los datos se guardan solo en el teléfono, en una base de datos local (Room/SQLite).
- `allowBackup=false`: los datos no se copian a la nube. **Si desinstalas la app, se pierden.**
- Las actualizaciones de la app migran la base de datos sin borrar datos (la v2 añadió las tareas diarias y la v3 los abonos, conservando todo lo anterior).

### Que los avisos lleguen en Xiaomi/Redmi (MIUI/HyperOS)

MIUI cierra las apps en segundo plano de forma agresiva. Para que los recordatorios lleguen con la app cerrada:

1. Abre Quipu → ⚙ Ajustes → **Permisos** y deja en verde las notificaciones y las alarmas exactas.
2. Toca **Información de la app** y activa **Inicio automático**.
3. En **Ahorro de batería**, elige **Sin restricciones**.

---

## Compilar e instalar

### Requisitos

- **Android Studio** (incluye el JDK y el SDK que hacen falta), o bien JDK 17+ y el Android SDK con la plataforma **API 35**.
- Internet solo para la primera compilación (descarga Gradle 8.9 y las dependencias).

### Abrir en Android Studio

1. **File → Open** y elige la carpeta del proyecto (la que contiene `settings.gradle.kts`).
2. Espera al *Gradle Sync*. Si pide instalar la plataforma o las Build-Tools, acepta.
3. Conecta el teléfono o abre un emulador y pulsa ▶ **Run 'app'**.

### Por terminal

```bash
./gradlew testDebugUnitTest   # pruebas unitarias
./gradlew assembleDebug       # genera el APK
```

APK resultante: `app/build/outputs/apk/debug/app-debug.apk`. Su paquete es `com.quipu.app.debug`, así que puede convivir con una versión de lanzamiento.

### Instalar en el teléfono

**Con cable USB (adb):**

1. En el teléfono: *Ajustes → Información del teléfono* → toca 7 veces *Versión de MIUI/HyperOS* (o *Número de compilación*) para activar las **Opciones de desarrollador**.
2. En *Opciones de desarrollador* activa **Depuración USB**. En Xiaomi activa también **Instalar mediante USB**; puede pedir iniciar sesión con una cuenta Mi.
3. Conecta el cable, acepta el aviso «Permitir depuración USB» y ejecuta:

```bash
adb devices                                        # el teléfono debe aparecer como "device"
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**Copiando el APK:** pásalo al teléfono, ábrelo desde la app *Archivos* y permite «Instalar desde esta fuente» si Android lo pide. Si MIUI muestra un aviso de seguridad, elige *Instalar de todos modos*.

### APK de lanzamiento firmado (opcional)

```bash
keytool -genkeypair -v -keystore quipu-release.jks -alias quipu -keyalg RSA -keysize 2048 -validity 10000
cp keystore.properties.example keystore.properties   # edita las claves
./gradlew assembleRelease
```

`keystore.properties` y los `.jks` están en `.gitignore`: **nunca los subas al repositorio**.

---

## Estructura

```
app/src/main/java/com/quipu/app/
  domain/     Modelos y lógica pura: importes, abonos, validaciones, personas, tareas
              diarias, agrupación por persona, totales y planificación de recordatorios
              (cubierta por pruebas)
  data/       Room (entidades, DAO, base de datos y migraciones), preferencias,
              repositorio y datos de demostración
  reminders/  Notificaciones, alarma del próximo aviso y receptores del sistema
  ui/         Tema, navegación, componentes, pantallas (Tareas, Finanzas, Resumen,
              Ajustes) y ViewModels
app/src/test/java/com/quipu/app/domain/   Pruebas unitarias (JUnit)
app/schemas/                              Esquemas exportados de Room (uno por versión)
```

**Tecnologías**: Kotlin 2.0, Jetpack Compose con Material 3, Navigation Compose, Room 2.6 (KSP), Coroutines/Flow, AlarmManager, AndroidX SplashScreen y AGP 8.7.

---

## Licencia

[MIT](LICENSE) © 2026 ImVyrox
