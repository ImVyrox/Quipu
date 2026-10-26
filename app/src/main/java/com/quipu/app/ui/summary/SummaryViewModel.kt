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
)

class SummaryViewModel(application: Application) : BaseViewModel(application) {

    val state: StateFlow<SummaryUiState> = combine(
        repository.observeDebts(),
        repository.settings.currency,
    ) { debts, currency ->
        SummaryUiState(
            loaded = true,
            summary = FinanceCalculator.summarize(debts),
            currency = currency,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SummaryUiState())
}
