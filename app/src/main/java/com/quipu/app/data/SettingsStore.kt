package com.quipu.app.data

import android.content.Context
import com.quipu.app.domain.AppCurrencies
import com.quipu.app.domain.AppCurrency
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Preferencias locales (solo la moneda). Guardadas en el dispositivo. */
class SettingsStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("quipu_settings", Context.MODE_PRIVATE)

    private val _currency = MutableStateFlow(AppCurrencies.byCode(prefs.getString(KEY_CURRENCY, null)))
    val currency: StateFlow<AppCurrency> = _currency.asStateFlow()

    fun setCurrency(currency: AppCurrency) {
        prefs.edit().putString(KEY_CURRENCY, currency.code).apply()
        _currency.value = currency
    }

    private companion object {
        const val KEY_CURRENCY = "currency_code"
    }
}
