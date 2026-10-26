package com.quipu.app.data

import android.content.Context
import com.quipu.app.domain.AccentColor
import com.quipu.app.domain.AppCurrencies
import com.quipu.app.domain.AppCurrency
import com.quipu.app.domain.Appearance
import com.quipu.app.domain.ReminderSettings
import com.quipu.app.domain.ThemeMode
import java.time.LocalDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Sonido de los avisos: el del sistema, ninguno o un tono elegido ([NotificationSound.customUri]). */
enum class SoundMode { DEFAULT, SILENT, CUSTOM }

data class NotificationSound(
    val mode: SoundMode = SoundMode.DEFAULT,
    val customUri: String? = null,
    /** Título del tono elegido, para mostrarlo en Ajustes. */
    val customTitle: String? = null,
    val vibrate: Boolean = true,
    /**
     * Android no deja cambiar el sonido de un canal ya creado: cada cambio crea un canal
     * nuevo con esta versión en su id y borra el anterior.
     */
    val channelVersion: Int = 1,
)

/** Preferencias locales guardadas en el dispositivo. */
class SettingsStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("quipu_settings", Context.MODE_PRIVATE)

    private val _currency = MutableStateFlow(AppCurrencies.byCode(prefs.getString(KEY_CURRENCY, null)))
    val currency: StateFlow<AppCurrency> = _currency.asStateFlow()

    private val _appearance = MutableStateFlow(readAppearance())
    val appearance: StateFlow<Appearance> = _appearance.asStateFlow()

    private val _reminders = MutableStateFlow(readReminders())
    val reminders: StateFlow<ReminderSettings> = _reminders.asStateFlow()

    private val _sound = MutableStateFlow(readSound())
    val sound: StateFlow<NotificationSound> = _sound.asStateFlow()

    fun setCurrency(currency: AppCurrency) {
        prefs.edit().putString(KEY_CURRENCY, currency.code).apply()
        _currency.value = currency
    }

    fun setAppearance(value: Appearance) {
        prefs.edit()
            .putString(KEY_THEME_MODE, value.themeMode.name)
            .putString(KEY_ACCENT, value.accent.name)
            .putBoolean(KEY_DYNAMIC_COLOR, value.dynamicColor)
            .apply()
        _appearance.value = value
    }

    fun setReminders(value: ReminderSettings) {
        prefs.edit()
            .putBoolean(KEY_REM_ENABLED, value.enabled)
            .putBoolean(KEY_REM_TASKS, value.tasksEnabled)
            .putInt(KEY_REM_TASK_LEAD, value.taskLeadMinutes)
            .putBoolean(KEY_REM_DAILY, value.dailyEnabled)
            .putInt(KEY_REM_DAILY_MINUTE, value.dailyMinuteOfDay)
            .putBoolean(KEY_REM_DEBTS, value.debtsEnabled)
            .putInt(KEY_REM_DEBT_DAYS, value.debtLeadDays)
            .putInt(KEY_REM_DEBT_MINUTE, value.debtMinuteOfDay)
            .apply()
        _reminders.value = value
    }

    /** Guarda el sonido; si cambia algo que vive en el canal, sube su versión. */
    fun setSound(value: NotificationSound) {
        val old = _sound.value
        val channelChanged = old.mode != value.mode || old.customUri != value.customUri || old.vibrate != value.vibrate
        val saved = if (channelChanged) value.copy(channelVersion = old.channelVersion + 1) else value
        prefs.edit()
            .putString(KEY_SOUND_MODE, saved.mode.name)
            .putString(KEY_SOUND_URI, saved.customUri)
            .putString(KEY_SOUND_TITLE, saved.customTitle)
            .putBoolean(KEY_VIBRATE, saved.vibrate)
            .putInt(KEY_CHANNEL_VERSION, saved.channelVersion)
            .apply()
        _sound.value = saved
    }

    /** Última vez que se revisaron los avisos pendientes (hora local), o null si nunca. */
    var lastReminderCheck: LocalDateTime?
        get() = prefs.getString(KEY_LAST_CHECK, null)?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
        set(value) {
            prefs.edit().putString(KEY_LAST_CHECK, value?.toString()).apply()
        }

    private fun readAppearance() = Appearance(
        themeMode = enumOr(prefs.getString(KEY_THEME_MODE, null), ThemeMode.SYSTEM),
        accent = enumOr(prefs.getString(KEY_ACCENT, null), AccentColor.TEAL),
        dynamicColor = prefs.getBoolean(KEY_DYNAMIC_COLOR, false),
    )

    private fun readReminders(): ReminderSettings {
        val d = ReminderSettings()
        return ReminderSettings(
            enabled = prefs.getBoolean(KEY_REM_ENABLED, d.enabled),
            tasksEnabled = prefs.getBoolean(KEY_REM_TASKS, d.tasksEnabled),
            taskLeadMinutes = prefs.getInt(KEY_REM_TASK_LEAD, d.taskLeadMinutes),
            dailyEnabled = prefs.getBoolean(KEY_REM_DAILY, d.dailyEnabled),
            dailyMinuteOfDay = prefs.getInt(KEY_REM_DAILY_MINUTE, d.dailyMinuteOfDay),
            debtsEnabled = prefs.getBoolean(KEY_REM_DEBTS, d.debtsEnabled),
            debtLeadDays = prefs.getInt(KEY_REM_DEBT_DAYS, d.debtLeadDays),
            debtMinuteOfDay = prefs.getInt(KEY_REM_DEBT_MINUTE, d.debtMinuteOfDay),
        )
    }

    private fun readSound() = NotificationSound(
        mode = enumOr(prefs.getString(KEY_SOUND_MODE, null), SoundMode.DEFAULT),
        customUri = prefs.getString(KEY_SOUND_URI, null),
        customTitle = prefs.getString(KEY_SOUND_TITLE, null),
        vibrate = prefs.getBoolean(KEY_VIBRATE, true),
        channelVersion = prefs.getInt(KEY_CHANNEL_VERSION, 1),
    )

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default

    private companion object {
        const val KEY_CURRENCY = "currency_code"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_ACCENT = "accent_color"
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
        const val KEY_REM_ENABLED = "reminders_enabled"
        const val KEY_REM_TASKS = "reminders_tasks"
        const val KEY_REM_TASK_LEAD = "reminders_task_lead_minutes"
        const val KEY_REM_DAILY = "reminders_daily"
        const val KEY_REM_DAILY_MINUTE = "reminders_daily_minute"
        const val KEY_REM_DEBTS = "reminders_debts"
        const val KEY_REM_DEBT_DAYS = "reminders_debt_lead_days"
        const val KEY_REM_DEBT_MINUTE = "reminders_debt_minute"
        const val KEY_SOUND_MODE = "sound_mode"
        const val KEY_SOUND_URI = "sound_uri"
        const val KEY_SOUND_TITLE = "sound_title"
        const val KEY_VIBRATE = "vibrate"
        const val KEY_CHANNEL_VERSION = "channel_version"
        const val KEY_LAST_CHECK = "reminders_last_check"
    }
}
