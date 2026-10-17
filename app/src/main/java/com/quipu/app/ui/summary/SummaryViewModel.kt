package com.quipu.app.ui.summary

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.quipu.app.domain.AppCurrencies
import com.quipu.app.domain.AppCurrency
import com.quipu.app.domain.FinanceCalculator
import com.quipu.app.domain.FinanceSummary
import com.quipu.app.ui.BaseViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SummaryUiState(
    val loaded: Boolean = false,
    val summary: FinanceSummary = FinanceCalculator.summarize(emptyList()),
    val currency: AppCurrency = AppCurrencies.default,
    /** Cantidad de registros de demostración presentes. */
    val demoCount: Int = 0,
)

class SummaryViewModel(application: Application) : BaseViewModel(application) {

    val state: StateFlow<SummaryUiState> = combine(
        repository.observeDebts(),
        repository.settings.currency,
        repository.observeDemoCount(),
    ) { debts, currency, demoCount ->
        SummaryUiState(
            loaded = true,
            summary = FinanceCalculator.summarize(debts),
            currency = currency,
            demoCount = demoCount,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SummaryUiState())

    fun setCurrency(currency: AppCurrency) {
        repository.settings.setCurrency(currency)
        say("Moneda cambiada a ${currency.code}")
    }

    fun loadDemoData() {
        launchAction("Datos de demostración cargados") { repository.loadDemoData() }
    }

    fun removeDemoData() {
        launchAction("Datos de demostración eliminados") { repository.removeDemoData() }
    }
}
