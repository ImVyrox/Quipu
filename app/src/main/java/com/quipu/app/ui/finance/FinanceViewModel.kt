package com.quipu.app.ui.finance

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.quipu.app.domain.AppCurrency
import com.quipu.app.domain.DebtDraft
import com.quipu.app.domain.DebtItem
import com.quipu.app.domain.DebtLogic
import com.quipu.app.domain.DebtStatusFilter
import com.quipu.app.domain.DebtTypeFilter
import com.quipu.app.domain.PersonGroup
import com.quipu.app.ui.BaseViewModel
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class DebtFilters(
    val query: String = "",
    val type: DebtTypeFilter = DebtTypeFilter.ALL,
    val status: DebtStatusFilter = DebtStatusFilter.ALL,
    val newestFirst: Boolean = true,
)

data class FinanceUiState(
    val loaded: Boolean = false,
    val all: List<DebtItem> = emptyList(),
    val visible: List<DebtItem> = emptyList(),
    /** Personas con algún registro que coincide con la búsqueda y los filtros. */
    val groups: List<PersonGroup> = emptyList(),
    val filters: DebtFilters = DebtFilters(),
    /** Nombres ya usados (una entrada por persona) para sugerirlos en el formulario. */
    val knownPeople: List<String> = emptyList(),
    val currency: AppCurrency = com.quipu.app.domain.AppCurrencies.default,
)

class FinanceViewModel(application: Application) : BaseViewModel(application) {

    private val filters = MutableStateFlow(DebtFilters())

    val state: StateFlow<FinanceUiState> = combine(
        repository.observeDebts(),
        filters,
        repository.settings.currency,
    ) { all, f, currency ->
        val people = all
            .sortedWith(compareBy<DebtItem>({ it.recordDate }, { it.id }))
            .associateBy { it.personKey }
            .values
            .map { it.personName }
            .sortedBy { it.lowercase() }
        FinanceUiState(
            loaded = true,
            all = all,
            visible = DebtLogic.filterAndSort(all, f.query, f.type, f.status, f.newestFirst),
            groups = DebtLogic.groupByPerson(all, f.query, f.type, f.status, f.newestFirst, LocalDate.now()),
            filters = f,
            knownPeople = people,
            currency = currency,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FinanceUiState())

    fun setQuery(value: String) = filters.update { it.copy(query = value) }

    fun setTypeFilter(value: DebtTypeFilter) = filters.update { it.copy(type = value) }

    fun setStatusFilter(value: DebtStatusFilter) = filters.update { it.copy(status = value) }

    fun toggleSortOrder() = filters.update { it.copy(newestFirst = !it.newestFirst) }

    fun save(draft: DebtDraft) {
        val isNew = draft.id == 0L
        launchAction(if (isNew) "Registro creado" else "Cambios guardados") { repository.saveDebt(draft) }
    }

    fun setSettled(item: DebtItem, settled: Boolean) {
        val message = when {
            !settled -> "Registro devuelto a pendiente"
            item.type == com.quipu.app.domain.DebtType.OWED_TO_ME -> "Marcado como cobrado"
            else -> "Marcado como pagado"
        }
        launchAction(message) { repository.setDebtSettled(item.id, settled) }
    }

    fun delete(id: Long) {
        launchAction("Registro eliminado") { repository.deleteDebt(id) }
    }
}
