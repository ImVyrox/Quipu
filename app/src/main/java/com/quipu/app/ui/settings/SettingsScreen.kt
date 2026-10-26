@file:OptIn(ExperimentalMaterial3Api::class)

package com.quipu.app.ui.settings

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quipu.app.data.NotificationSound
import com.quipu.app.data.SoundMode
import com.quipu.app.domain.AccentColor
import com.quipu.app.domain.AppCurrencies
import com.quipu.app.domain.ReminderOptions
import com.quipu.app.domain.ThemeMode
import com.quipu.app.reminders.QuipuNotifications
import com.quipu.app.reminders.ReminderEngine
import com.quipu.app.ui.Formats
import com.quipu.app.ui.components.AppTimePickerDialog
import com.quipu.app.ui.components.SectionHeader
import java.time.LocalTime

/** Diálogos de la pantalla (uno a la vez). */
private enum class SettingsDialog { THEME, TASK_LEAD, DAILY_TIME, DEBT_LEAD, DEBT_TIME, SOUND, CURRENCY }

@Composable
fun SettingsScreen(
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var dialog by rememberSaveable { mutableStateOf<SettingsDialog?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    // Los permisos se cambian fuera de la app: se vuelven a leer al regresar a esta pantalla.
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumes++
        onPauseOrDispose {}
    }
    val canNotify = remember(resumes) { QuipuNotifications.canNotify(context) }
    val canExact = remember(resumes) { ReminderEngine.canScheduleExact(context) }
    val batteryFree = remember(resumes) { SystemSettings.isIgnoringBatteryOptimizations(context) }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) SystemSettings.openAppNotificationSettings(context)
        resumes++
    }
    val requestNotifications = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            SystemSettings.openAppNotificationSettings(context)
        }
    }

    val ringtonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = result.data?.let {
            IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        }
        val sound = state.sound
        viewModel.setSound(
            when {
                uri == null -> sound.copy(mode = SoundMode.SILENT)
                uri == Settings.System.DEFAULT_NOTIFICATION_URI -> sound.copy(mode = SoundMode.DEFAULT)
                else -> sound.copy(
                    mode = SoundMode.CUSTOM,
                    customUri = uri.toString(),
                    customTitle = RingtoneManager.getRingtone(context, uri)?.getTitle(context),
                )
            },
        )
    }

    val appearance = state.appearance
    val reminders = state.reminders
    val sound = state.sound

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets.navigationBars,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ---- Apariencia ----
            item { SectionHeader("Apariencia") }
            item {
                SettingsCard {
                    SettingRow(
                        title = "Tema",
                        detail = appearance.themeMode.label,
                        onClick = { dialog = SettingsDialog.THEME },
                    )
                    HorizontalDivider()
                    val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    SwitchRow(
                        title = "Colores del fondo de pantalla",
                        detail = if (dynamicSupported) "Usa los colores de tu fondo (Material You)" else "Requiere Android 12 o superior",
                        checked = appearance.dynamicColor && dynamicSupported,
                        enabled = dynamicSupported,
                        onChange = { viewModel.setAppearance(appearance.copy(dynamicColor = it)) },
                    )
                    HorizontalDivider()
                    AccentPicker(
                        selected = appearance.accent,
                        enabled = !(appearance.dynamicColor && dynamicSupported),
                        onSelect = { viewModel.setAppearance(appearance.copy(accent = it)) },
                    )
                }
            }

            // ---- Recordatorios ----
            item { SectionHeader("Recordatorios") }
            if (reminders.enabled && !canNotify) {
                item {
                    WarningCard(
                        text = "Las notificaciones de Quipu están desactivadas: no verás los recordatorios.",
                        action = "Permitir notificaciones",
                        onAction = requestNotifications,
                    )
                }
            }
            item {
                SettingsCard {
                    SwitchRow(
                        title = "Activar recordatorios",
                        detail = "Avisos de tareas y pagos por vencer, aunque la app esté cerrada",
                        checked = reminders.enabled,
                        onChange = { on ->
                            viewModel.setReminders(reminders.copy(enabled = on))
                            if (on && !canNotify) requestNotifications()
                        },
                    )
                    if (reminders.enabled) {
                        HorizontalDivider()
                        SwitchRow(
                            title = "Tareas con fecha y hora",
                            checked = reminders.tasksEnabled,
                            onChange = { viewModel.setReminders(reminders.copy(tasksEnabled = it)) },
                        )
                        if (reminders.tasksEnabled) {
                            SettingRow(
                                title = "Avisar",
                                detail = ReminderOptions.taskLeadLabel(reminders.taskLeadMinutes),
                                onClick = { dialog = SettingsDialog.TASK_LEAD },
                                indent = true,
                            )
                        }
                        HorizontalDivider()
                        SwitchRow(
                            title = "Tareas diarias pendientes",
                            checked = reminders.dailyEnabled,
                            onChange = { viewModel.setReminders(reminders.copy(dailyEnabled = it)) },
                        )
                        if (reminders.dailyEnabled) {
                            SettingRow(
                                title = "Hora del aviso",
                                detail = "${minuteLabel(reminders.dailyMinuteOfDay)} · solo si aún no la completaste ese día",
                                onClick = { dialog = SettingsDialog.DAILY_TIME },
                                indent = true,
                            )
                        }
                        HorizontalDivider()
                        SwitchRow(
                            title = "Préstamos y deudas por vencer",
                            detail = "Registros pendientes con fecha de vencimiento",
                            checked = reminders.debtsEnabled,
                            onChange = { viewModel.setReminders(reminders.copy(debtsEnabled = it)) },
                        )
                        if (reminders.debtsEnabled) {
                            SettingRow(
                                title = "Avisar",
                                detail = ReminderOptions.debtLeadLabel(reminders.debtLeadDays),
                                onClick = { dialog = SettingsDialog.DEBT_LEAD },
                                indent = true,
                            )
                            SettingRow(
                                title = "Hora del aviso",
                                detail = minuteLabel(reminders.debtMinuteOfDay),
                                onClick = { dialog = SettingsDialog.DEBT_TIME },
                                indent = true,
                            )
                        }
                    }
                }
            }

            // ---- Sonido ----
            item { SectionHeader("Sonido y vibración") }
            item {
                SettingsCard {
                    SettingRow(
                        title = "Sonido de notificación",
                        detail = soundLabel(sound),
                        onClick = { dialog = SettingsDialog.SOUND },
                    )
                    HorizontalDivider()
                    SwitchRow(
                        title = "Vibrar",
                        checked = sound.vibrate,
                        onChange = { viewModel.setSound(sound.copy(vibrate = it)) },
                    )
                    HorizontalDivider()
                    SettingRow(
                        title = "Más opciones en Android",
                        detail = "Pantalla de bloqueo, globos, prioridad…",
                        onClick = {
                            SystemSettings.openChannelSettings(context, QuipuNotifications.ensureChannel(context, sound))
                        },
                    )
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        OutlinedButton(
                            onClick = { if (canNotify) viewModel.sendTestNotification() else requestNotifications() },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) { Text("Probar notificación") }
                    }
                }
            }

            // ---- Permisos ----
            item { SectionHeader("Permisos") }
            item {
                SettingsCard {
                    SettingRow(
                        title = "Notificaciones",
                        detail = if (canNotify) "Permitidas" else "Bloqueadas · toca para permitir",
                        status = canNotify,
                        onClick = { if (canNotify) SystemSettings.openAppNotificationSettings(context) else requestNotifications() },
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        HorizontalDivider()
                        SettingRow(
                            title = "Alarmas a la hora exacta",
                            detail = if (canExact) {
                                "Permitidas"
                            } else {
                                "No permitidas · los avisos pueden llegar unos minutos tarde"
                            },
                            status = canExact,
                            onClick = { SystemSettings.openExactAlarmSettings(context) },
                        )
                    }
                    HorizontalDivider()
                    SettingRow(
                        title = "Uso de batería",
                        detail = if (batteryFree) {
                            "Sin restricciones"
                        } else {
                            "Optimizado · Android podría retrasar los avisos. Elige «Sin restricciones»."
                        },
                        status = batteryFree,
                        onClick = { SystemSettings.openBatteryOptimizationSettings(context) },
                    )
                    HorizontalDivider()
                    SettingRow(
                        title = "Información de la app",
                        detail = "En Xiaomi/Redmi activa aquí «Inicio automático» para que los avisos lleguen con la app cerrada",
                        onClick = { SystemSettings.openAppDetails(context) },
                    )
                }
            }

            // ---- Moneda ----
            item { SectionHeader("Moneda") }
            item {
                SettingsCard {
                    SettingRow(
                        title = "${state.currency.code} · ${state.currency.name} (${state.currency.symbol})",
                        detail = "Solo cambia el símbolo; no convierte los montos guardados",
                        onClick = { dialog = SettingsDialog.CURRENCY },
                    )
                }
            }

            // ---- Datos de demostración ----
            item { SectionHeader("Datos de demostración") }
            item {
                SettingsCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            if (state.demoCount == 0) {
                                "Carga unos pocos ejemplos para ver cómo funciona la app. Se pueden eliminar en un toque sin afectar tus datos reales."
                            } else {
                                "Hay ${state.demoCount} registros de ejemplo en la app. Eliminarlos no afecta tus datos reales."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (state.demoCount == 0) {
                            OutlinedButton(
                                onClick = viewModel::loadDemoData,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            ) { Text("Cargar datos de demostración") }
                        } else {
                            Button(
                                onClick = viewModel::removeDemoData,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            ) { Text("Eliminar datos de demostración") }
                        }
                    }
                }
            }

            // ---- Acerca de ----
            item { SectionHeader("Acerca de") }
            item {
                val version = remember {
                    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
                }
                SettingsCard {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Quipu ${version.orEmpty()}", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Quipu guarda todo solo en este teléfono y no usa Internet. Si desinstalas la app, los datos se pierden.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    when (dialog) {
        SettingsDialog.THEME -> ChoiceDialog(
            title = "Tema",
            options = ThemeMode.entries,
            selected = appearance.themeMode,
            label = { it.label },
            onSelect = { viewModel.setAppearance(appearance.copy(themeMode = it)) },
            onDismiss = { dialog = null },
        )
        SettingsDialog.TASK_LEAD -> ChoiceDialog(
            title = "Avisar antes de cada tarea",
            options = ReminderOptions.taskLeadMinutes,
            selected = reminders.taskLeadMinutes,
            label = ReminderOptions::taskLeadLabel,
            onSelect = { viewModel.setReminders(reminders.copy(taskLeadMinutes = it)) },
            onDismiss = { dialog = null },
        )
        SettingsDialog.DEBT_LEAD -> ChoiceDialog(
            title = "Avisar antes del vencimiento",
            options = ReminderOptions.debtLeadDays,
            selected = reminders.debtLeadDays,
            label = ReminderOptions::debtLeadLabel,
            onSelect = { viewModel.setReminders(reminders.copy(debtLeadDays = it)) },
            onDismiss = { dialog = null },
        )
        SettingsDialog.DAILY_TIME -> AppTimePickerDialog(
            initial = timeOf(reminders.dailyMinuteOfDay),
            onDismiss = { dialog = null },
            onConfirm = {
                viewModel.setReminders(reminders.copy(dailyMinuteOfDay = it.hour * 60 + it.minute))
                dialog = null
            },
        )
        SettingsDialog.DEBT_TIME -> AppTimePickerDialog(
            initial = timeOf(reminders.debtMinuteOfDay),
            onDismiss = { dialog = null },
            onConfirm = {
                viewModel.setReminders(reminders.copy(debtMinuteOfDay = it.hour * 60 + it.minute))
                dialog = null
            },
        )
        SettingsDialog.SOUND -> ChoiceDialog(
            title = "Sonido de notificación",
            options = SoundMode.entries,
            selected = sound.mode,
            label = {
                when (it) {
                    SoundMode.DEFAULT -> "Predeterminado del sistema"
                    SoundMode.SILENT -> "Silencioso"
                    SoundMode.CUSTOM -> sound.customTitle?.let { title -> "Elegir tono… ($title)" } ?: "Elegir tono…"
                }
            },
            onSelect = {
                when (it) {
                    SoundMode.CUSTOM -> ringtonePicker.launch(ringtonePickerIntent(sound))
                    else -> viewModel.setSound(sound.copy(mode = it))
                }
            },
            onDismiss = { dialog = null },
        )
        SettingsDialog.CURRENCY -> ChoiceDialog(
            title = "Moneda",
            options = AppCurrencies.all,
            selected = state.currency,
            label = { "${it.code} · ${it.name} (${it.symbol})" },
            onSelect = viewModel::setCurrency,
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

private fun minuteLabel(minuteOfDay: Int) = Formats.time(timeOf(minuteOfDay))

private fun timeOf(minuteOfDay: Int) = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)

private fun soundLabel(sound: NotificationSound) = when (sound.mode) {
    SoundMode.DEFAULT -> "Predeterminado del sistema"
    SoundMode.SILENT -> "Silencioso"
    SoundMode.CUSTOM -> sound.customTitle ?: "Tono personalizado"
}

private fun ringtonePickerIntent(sound: NotificationSound): Intent {
    val existing: Uri? = when (sound.mode) {
        SoundMode.DEFAULT -> Settings.System.DEFAULT_NOTIFICATION_URI
        SoundMode.SILENT -> null
        SoundMode.CUSTOM -> sound.customUri?.let(Uri::parse)
    }
    return Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Sonido de los avisos")
        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_NOTIFICATION_URI)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
}

/** Grupo de opciones con el mismo borde que las tarjetas del resto de la app. */
@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, scheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
        content = content,
    )
}

@Composable
private fun WarningCard(text: String, action: String, onAction: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(
        colors = CardDefaults.cardColors(containerColor = scheme.errorContainer, contentColor = scheme.onErrorContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

/**
 * Fila que abre algo. [status] muestra un punto verde (true) o rojo (false) para los
 * permisos; [indent] se usa para las opciones que dependen de un interruptor.
 */
@Composable
private fun SettingRow(
    title: String,
    detail: String? = null,
    onClick: () -> Unit,
    status: Boolean? = null,
    indent: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = detail?.let { { Text(it) } },
        trailingContent = status?.let { ok ->
            {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(if (ok) Color(0xFF16A34A) else scheme.error, CircleShape),
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(start = if (indent) 16.dp else 0.dp),
    )
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    detail: String? = null,
    enabled: Boolean = true,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = detail?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
    )
}

/** Círculos de color para elegir el color principal. */
@Composable
private fun AccentPicker(selected: AccentColor, enabled: Boolean, onSelect: (AccentColor) -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            if (enabled) "Color principal" else "Color principal (desactiva los colores del fondo para elegirlo)",
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AccentColor.entries.forEach { accent ->
                val color = accentSwatch(accent, dark)
                val isSelected = accent == selected
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .background(color, CircleShape)
                        .border(
                            width = if (isSelected) 3.dp else 0.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                            shape = CircleShape,
                        )
                        .selectable(selected = isSelected, enabled = enabled, role = Role.RadioButton) { onSelect(accent) }
                        .semantics { contentDescription = accent.label },
                ) {
                    if (isSelected) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = if (dark) Color.Black else Color.White)
                    }
                }
            }
        }
    }
}

/** Muestra de cada acento (el color principal claro u oscuro). */
private fun accentSwatch(accent: AccentColor, dark: Boolean): Color = when (accent) {
    AccentColor.TEAL -> if (dark) Color(0xFF5EEAD4) else Color(0xFF0F766E)
    AccentColor.BLUE -> if (dark) Color(0xFF93C5FD) else Color(0xFF1D4ED8)
    AccentColor.PURPLE -> if (dark) Color(0xFFD8B4FE) else Color(0xFF7E22CE)
    AccentColor.ORANGE -> if (dark) Color(0xFFFDBA74) else Color(0xFFC2410C)
    AccentColor.PINK -> if (dark) Color(0xFFF9A8D4) else Color(0xFFBE185D)
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = option == selected, role = Role.RadioButton) {
                                onSelect(option)
                                onDismiss()
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Text(label(option), modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}
