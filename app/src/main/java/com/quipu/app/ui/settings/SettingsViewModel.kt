package com.quipu.app.ui.settings

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.quipu.app.data.NotificationSound
import com.quipu.app.domain.AppCurrencies
import com.quipu.app.domain.AppCurrency
import com.quipu.app.domain.Appearance
import com.quipu.app.domain.MotivationSettings
import com.quipu.app.domain.ReminderSettings
import com.quipu.app.reminders.MotivationEngine
import com.quipu.app.reminders.ReminderEngine
import com.quipu.app.ui.BaseViewModel
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SettingsUiState(
    val currency: AppCurrency = AppCurrencies.default,
    val appearance: Appearance = Appearance(),
    val reminders: ReminderSettings = ReminderSettings(),
    val sound: NotificationSound = NotificationSound(),
    val motivation: MotivationSettings = MotivationSettings(),
    /** Cantidad de registros de demostración presentes. */
    val demoCount: Int = 0,
)

class SettingsViewModel(application: Application) : BaseViewModel(application) {
    private val settings = repository.settings

    val state: StateFlow<SettingsUiState> = combine(
        settings.currency,
        settings.appearance,
        settings.reminders,
        combine(settings.sound, settings.motivation) { s, m -> s to m },
        combine(repository.observeDemoCount(), money.observeDemoCount()) { a, b -> a + b },
    ) { currency, appearance, reminders, (sound, motivation), demoCount ->
        SettingsUiState(currency, appearance, reminders, sound, motivation, demoCount)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SettingsUiState(
            settings.currency.value,
            settings.appearance.value,
            settings.reminders.value,
            settings.sound.value,
            settings.motivation.value,
        ),
    )

    fun setCurrency(currency: AppCurrency) {
        settings.setCurrency(currency)
        say("Moneda cambiada a ${currency.code}")
    }

    fun setAppearance(value: Appearance) = settings.setAppearance(value)

    /** Los avisos se reprograman solos: QuipuApplication observa estos ajustes. */
    fun setReminders(value: ReminderSettings) = settings.setReminders(value)

    fun setSound(value: NotificationSound) = settings.setSound(value)

    fun sendTestNotification() = ReminderEngine.sendTest(getApplication())

    /** Guarda los ajustes y vuelve a sortear lo que queda de hoy (sin superar el límite diario). */
    fun setMotivation(value: MotivationSettings) {
        settings.setMotivation(value)
        launchAction(null) { MotivationEngine.replan(getApplication()) }
    }

    fun sendTestMotivation() {
        launchAction(null) { MotivationEngine.sendTest(getApplication()) }
    }

    fun loadDemoData() {
        launchAction("Datos de demostración cargados") {
            repository.loadDemoData()
            money.loadDemoData(LocalDate.now())
        }
    }

    fun removeDemoData() {
        launchAction("Datos de demostración eliminados") {
            repository.removeDemoData()
            money.removeDemoData()
        }
    }
}
