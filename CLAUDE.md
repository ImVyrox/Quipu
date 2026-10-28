# CLAUDE.md — Quipu

Instrucciones permanentes para trabajar en este repositorio. Quipu es una app Android personal, **100 % local** (sin Internet, sin cuentas, sin Google Play Services), para gestionar tareas (normales y diarias) y préstamos/deudas por persona, con recordatorios por notificación. El código, los comentarios, los textos de la interfaz, el README y los mensajes de commit están **en español**: mantenlo así.

## Tecnologías y versiones (fuente: `gradle/libs.versions.toml`, `app/build.gradle.kts`)

| Pieza | Versión |
|---|---|
| Android Gradle Plugin | 8.7.3 |
| Gradle (wrapper) | 8.9 |
| Kotlin / plugin Compose de Kotlin | 2.0.21 |
| KSP | 2.0.21-1.0.28 |
| Compose BOM | 2024.12.01 (Material 3, `material-icons-core`) |
| Navigation Compose | 2.8.5 |
| Lifecycle (runtime-compose, viewmodel-compose) | 2.8.7 |
| Activity Compose | 1.9.3 |
| Core KTX / Core SplashScreen | 1.15.0 / 1.0.1 |
| Room (runtime, ktx, compiler vía KSP) | 2.6.1 |
| kotlinx-coroutines-android | 1.9.0 |
| JUnit | 4.13.2 |
| JVM target | 17 (`sourceCompatibility`/`jvmTarget`) |
| SDK | `minSdk 30` (Android 11), `targetSdk 35`, `compileSdk 35` |
| App | `applicationId com.quipu.app`, `versionCode 2`, `versionName 1.1.0`; debug añade `.debug` / `-debug` |

- Todas las dependencias se declaran en el catálogo `gradle/libs.versions.toml`; no pongas versiones sueltas en `build.gradle.kts`.
- No añadas dependencias nuevas (ni Hilt, ni DataStore, ni librerías de red) sin pedirlo antes: el proyecto es deliberadamente pequeño. Nunca añadas el permiso `INTERNET`.
- `isMinifyEnabled = false` en release. La firma de release sale de `keystore.properties` (ver `keystore.properties.example`); `keystore.properties`, `*.jks` y `*.keystore` están en `.gitignore` y **nunca** se suben.

## Arquitectura

Capas simples, sin inyección de dependencias:

```
app/src/main/java/com/quipu/app/
  QuipuApplication.kt   Crea el QuipuRepository (lazy) y un appScope que, al cambiar tareas,
                        registros o ajustes de recordatorios, llama a ReminderEngine.sync (con debounce 500 ms).
  MainActivity.kt       Splash, edge-to-edge, QuipuTheme y QuipuRoot.
  domain/               Kotlin puro, sin Android: modelos (Models.kt, Settings.kt), Money (centavos),
                        PersonKey (normalización de nombres), DebtLogic, TaskLogic, Validation,
                        ReminderPlanner. Es la capa cubierta por pruebas.
  data/                 Room: Entities.kt, Daos.kt, QuipuDatabase.kt (versión + migraciones),
                        Mappers.kt (entidad <-> dominio), QuipuRepository.kt, SettingsStore.kt
                        (SharedPreferences "quipu_settings" expuestas como StateFlow), DemoData.kt.
  reminders/            QuipuNotifications (canal + publicar), ReminderEngine (alarma única del
                        próximo aviso), ReminderReceivers (ReminderReceiver, SystemEventsReceiver).
  ui/                   QuipuRoot (Scaffold + NavHost + NavigationBar), BaseViewModel, Formats,
                        theme/Theme.kt, components/Components.kt y una carpeta por pantalla:
                        tasks/, finance/, summary/, settings/ (Screen + ViewModel).
app/src/test/java/com/quipu/app/domain/   Pruebas unitarias JUnit 4 del dominio.
app/schemas/com.quipu.app.data.QuipuDatabase/   Esquemas Room exportados: 1.json, 2.json, 3.json.
```

- **Flujo de datos**: DAO (`Flow`) → `QuipuRepository` (mapea a modelos de dominio) → ViewModel (`combine(...).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())`) → pantalla (`collectAsStateWithLifecycle()`).
- **ViewModels**: heredan de `ui/BaseViewModel` (`AndroidViewModel`), que obtiene el repositorio desde `QuipuApplication` y ofrece `launchAction(success) { ... }` y `say(...)` para mensajes en el `SnackbarHost` compartido. Las pantallas los obtienen con `viewModel()`. Las acciones de escritura pasan por `launchAction`; usa `viewModelScope.launch` con `try/catch` + `say(...)` solo cuando el mensaje depende del resultado (como `FinanceViewModel.addPayment`).
- **Navegación**: `ui/QuipuRoot.kt`. Rutas como strings: el enum `Destination` (`tasks`, `finance`, `summary`) define la barra inferior; `settings` se abre desde el engranaje de cada pantalla y oculta la barra. La vista de una persona en Finanzas es estado interno de `FinanceScreen`, no una ruta. Destino inicial: Tareas.
- **Lógica de negocio en `domain/`**, no en composables ni en ViewModels: así se puede probar sin Android. Las reglas de integridad que tocan varias tablas (abonos, saldar, borrar en cascada) viven en `QuipuRepository` dentro de `database.withTransaction { }`.

## Comandos

```bash
./gradlew testDebugUnitTest   # pruebas unitarias (JUnit, solo dominio)
./gradlew assembleDebug       # APK debug -> app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease     # APK release (firmado solo si existe keystore.properties)
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- Requiere JDK 17+ y el Android SDK con la plataforma API 35 (`ANDROID_HOME` o `local.properties` con `sdk.dir`). La primera compilación necesita Internet para descargar Gradle y dependencias.
- Una sola prueba: `./gradlew testDebugUnitTest --tests "com.quipu.app.domain.MoneyTest"`.
- No hay pruebas instrumentadas (`androidTest`) ni de UI; tampoco hay ktlint/detekt configurados. `kotlin.code.style=official` en `gradle.properties`.

## Convenciones de Kotlin

- Estilo oficial de Kotlin, comas finales en listas de argumentos multilínea, imports explícitos (sin `*`).
- KDoc/comentarios breves en español que expliquen el **porqué** (la densidad actual: un comentario por clase/función no obvia).
- **Dinero siempre en centavos `Long`** (`amountMinor`), nunca `Double`/`Float`. Parseo y formato solo con `domain/Money.kt`. La moneda (`AppCurrency`) solo cambia el símbolo; nunca convierte importes.
- **Fechas con `java.time`** y hora local de pared: las tareas guardan `dueLocalEpochSecond` (sin zona horaria) y los días se guardan como `epochDay`. No introduzcas `System.currentTimeMillis()`/`Date` en la lógica; pasa `today`/`now` como parámetro para que sea testeable (ver `TaskLogic.resolveForDay`, `ReminderPlanner`).
- Nombres de personas: siempre a través de `PersonKey.displayName` / `PersonKey.keyOf` (ignora mayúsculas, espacios sobrantes y tildes).
- Validaciones de formularios en `domain/Validation.kt`, con mensajes en español.
- Enums persistidos (en Room vía `Converters` y en preferencias) se guardan por `name`: **no renombres ni elimines constantes** de `DebtType`, `DebtStatus`, `ThemeMode`, `AccentColor`, `SoundMode`. Al leer preferencias, usa el patrón `enumOr(..., default)`.
- No cambies las claves de `SettingsStore` (`KEY_*`): son datos guardados en el teléfono del usuario.

## Convenciones de Jetpack Compose y diseño

Objetivo: interfaz **moderna, minimalista y consistente**, pensada para teléfono en vertical y uso con una mano (dispositivo de referencia: Xiaomi Redmi Note 11).

- Usa siempre Material 3 y los tokens del tema: `MaterialTheme.colorScheme`, `MaterialTheme.typography`, `MaterialTheme.shapes`. **No** uses colores hexadecimales ni `fontSize` sueltos en las pantallas (la única excepción actual son las muestras de acento y el indicador de permiso en `SettingsScreen`, que repiten los tonos de `Theme.kt`: si cambias un acento, cámbialo en ambos sitios).
- Colores semánticos de dinero y estado: `QuipuColors.extra` (`positive`, `negative`, `soon` y sus `*Container`), definidos en `ui/theme/Theme.kt` para claro y oscuro. Si necesitas un color nuevo, añádelo ahí en ambas variantes.
- El tema admite modo sistema/claro/oscuro, cinco acentos (`AccentColor`) y color dinámico (Android 12+). Cualquier cambio visual debe verse bien en **claro, oscuro y con cada acento**.
- Formas: `AppShapes` (6/10/14/20/28 dp). Espaciados habituales: 4, 8, 12 y 16 dp (16 dp es el margen estándar). Mantén esa escala.
- Reutiliza `ui/components/Components.kt` antes de crear algo nuevo: `StatusPill`, `EmptyState`, `SectionHeader`, `ConfirmDeleteDialog`, `FormSheet` (ModalBottomSheet), `FormActions`, `DateField`, `TimeField`, `AppDatePickerDialog`, `AppTimePickerDialog`. Los formularios se abren en bottom sheet; los borrados siempre piden confirmación.
- Iconos: `material-icons-core` (`Icons.Default/Filled`) y los drawables vectoriales `ic_nav_*`. No añadas `material-icons-extended` sin pedirlo.
- Edge-to-edge activo (`enableEdgeToEdge`, `windowSoftInputMode="adjustResize"`): respeta insets y usa `imePadding()` en formularios.
- Textos: casi toda la UI está escrita en español directamente en los composables; `res/values/strings.xml` solo tiene el nombre de la app y las etiquetas de navegación. Sigue el patrón existente del archivo que edites y mantén el tono (tuteo, frases cortas).
- Estado: hoisting hacia el ViewModel; en composables usa `remember`/`rememberSaveable` solo para estado de UI efímero. Composables privados para piezas de una sola pantalla (p. ej. `TaskCard`, `TaskFormSheet`).
- Accesibilidad: `contentDescription` en iconos accionables, áreas táctiles ≥ 48 dp.

## Datos locales y migraciones de Room (crítico)

Los datos del usuario solo existen en su teléfono: `allowBackup="false"`, sin nube. **Perder datos es irreversible.**

- Base de datos `quipu.db`, `QuipuDatabase` versión **3**, `exportSchema = true`, esquemas en `app/schemas/` (KSP `room.schemaLocation`). Tablas: `tasks`, `debts`, `debt_payments`.
- Historial: v1 → v2 (`MIGRATION_1_2`: columnas `isDaily`, `lastCompletedEpochDay` en `tasks`); v2 → v3 (`MIGRATION_2_3`: tabla `debt_payments` + índice `index_debt_payments_debtId`).
- Para cualquier cambio en una `@Entity`:
  1. Sube `version` en `@Database`.
  2. Escribe una `Migration(n, n+1)` explícita en `QuipuDatabase.companion`, con KDoc que diga qué cambia y que no borra datos, y regístrala en `.addMigrations(...)`.
  3. El SQL debe coincidir exactamente con lo que Room espera (tipos, `NOT NULL`, `DEFAULT`, índices): compáralo con el JSON de esquema generado.
  4. Compila para regenerar `app/schemas/.../<n+1>.json` y **commitea el nuevo JSON**. No edites ni borres los JSON existentes.
  5. Columnas nuevas no nulas: con `DEFAULT` en SQL y `@ColumnInfo(defaultValue = ...)` en la entidad.
- **Prohibido**: `fallbackToDestructiveMigration()`, borrar/renombrar tablas o columnas sin migración de copia, bajar la versión, o modificar una migración ya publicada.
- Datos de demostración: siempre con `isDemo = true`; `removeDemoData()` borra solo esos (y sus abonos). Nunca mezcles datos demo con reales.
- Integridad de abonos: un abono no puede superar lo que falta; si cubre el total, el registro pasa a `SETTLED`; solo se borran abonos de registros pendientes; al borrar un registro se borran sus abonos en la misma transacción. Mantén estas reglas en `QuipuRepository` dentro de `withTransaction`.
- Actualiza el README (sección «Privacidad y datos») cuando añadas una versión de la base de datos.

## Alarmas, notificaciones y permisos

- Permisos declarados (y los únicos permitidos sin consultarlo): `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`. Sin `INTERNET`.
- **Una sola alarma**: `ReminderEngine` programa solo el próximo aviso (`REQUEST_CODE = 100`, `PendingIntent.FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT`). Al sonar, `sync()` publica lo que tocaba desde `lastReminderCheck` (como máximo `MAX_CATCH_UP_HOURS = 12` atrás) y reprograma el siguiente. No crees alarmas por elemento ni uses WorkManager sin acordarlo.
- `sync()` está protegido con un `Mutex`; los receptores usan `goAsync()` + corrutina y siempre llaman a `pending.finish()` en `finally`. Mantén ese patrón.
- La planificación (qué y cuándo avisar) es lógica pura en `domain/ReminderPlanner.kt` y está probada en `ReminderPlannerTest`. Cambios de reglas → primero ahí, con pruebas.
- Alarmas exactas: comprueba siempre `ReminderEngine.canScheduleExact()` antes de `setExactAndAllowWhileIdle`; si no hay permiso, se usa `setAndAllowWhileIdle` (inexacta). Nunca asumas que el permiso está concedido (Android 12+ puede revocarlo).
- Notificaciones: publica solo vía `QuipuNotifications.post`, que comprueba `canNotify()` (Android 13+ requiere `POST_NOTIFICATIONS` en tiempo de ejecución). No publiques directamente con `NotificationManagerCompat`.
- Canales: Android no permite cambiar sonido/vibración de un canal existente. Por eso el id es `reminders_v<channelVersion>` y `SettingsStore.setSound` sube la versión si cambia algo del canal; `ensureChannel` borra los canales viejos. No cambies el prefijo `reminders_v`.
- `SystemEventsReceiver` (exportado) reprograma tras `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED` y `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`. `ReminderReceiver` no está exportado y debe seguir así.
- Los accesos a ajustes del sistema están en `ui/settings/SystemSettings.kt` y caen a «Información de la app» si la pantalla no existe (capas de fabricante como MIUI/HyperOS). Mantén ese respaldo en cualquier intent nuevo.
- Errores al programar avisos no deben cerrar la app (`QuipuApplication` los captura y reintenta en el próximo cambio); no relances excepciones salvo `CancellationException`.
- Tras tocar recordatorios, prueba en dispositivo/emulador: con la app cerrada, tras reinicio, cambiando la hora, y con el permiso de alarmas exactas concedido y denegado.

## Verificación de cambios

Antes de dar un cambio por terminado:

1. `./gradlew testDebugUnitTest` — todas las pruebas en verde. Toda lógica nueva o modificada en `domain/` lleva pruebas en `app/src/test/java/com/quipu/app/domain/` (JUnit 4, fechas fijas como `LocalDate.of(2026, 10, 25)`, nada de `now()` en las aserciones).
2. `./gradlew assembleDebug` — compila sin errores ni advertencias nuevas.
3. Si cambió una entidad: confirma que se generó el nuevo `app/schemas/.../<versión>.json` y que la app actualiza desde la versión anterior sin perder datos (instalar la versión previa, crear datos, instalar encima con `adb install -r`).
4. Si cambió la UI: revisa claro y oscuro, al menos un acento distinto de TEAL, y la pantalla con teclado abierto.
5. Informa con honestidad lo que no se pudo verificar (p. ej. sin SDK de Android o sin dispositivo).

## Otras reglas

- No cambies funcionalidades no pedidas. Si al revisar ves un problema, coméntalo en lugar de arreglarlo de paso.
- Actualiza el README cuando cambie algo visible para el usuario, y `versionCode`/`versionName` solo cuando se pida una versión nueva.
- `app-debug.apk` en la raíz es un artefacto de compilación versionado en git (no está en `.gitignore`); no lo regeneres ni lo borres salvo que se pida.
