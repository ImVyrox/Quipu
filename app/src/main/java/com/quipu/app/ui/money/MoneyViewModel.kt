package com.quipu.app.ui.money

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.quipu.app.data.DuplicateIncomeException
import com.quipu.app.domain.AppCurrencies
import com.quipu.app.domain.AppCurrency
import com.quipu.app.domain.CurrencyAmounts
import com.quipu.app.domain.DeclaredComparison
import com.quipu.app.domain.ExpenseCategory
import com.quipu.app.domain.IncomePlanner
import com.quipu.app.domain.IncomeProgress
import com.quipu.app.domain.IncomeSource
import com.quipu.app.domain.Ledger
import com.quipu.app.domain.MovementFilter
import com.quipu.app.domain.MovementKindFilter
import com.quipu.app.domain.Movement
import com.quipu.app.domain.MovementType
import com.quipu.app.domain.PendingTotals
import com.quipu.app.domain.PeriodTotals
import com.quipu.app.ui.BaseViewModel
import com.quipu.app.ui.Formats
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MoneyUiState(
    val loaded: Boolean = false,
    /** Moneda de Ajustes: la predeterminada para operaciones nuevas. */
    val defaultCurrency: AppCurrency = AppCurrencies.default,
    val all: List<Movement> = emptyList(),
    /** Historial con el filtro actual, del más reciente al más antiguo. */
    val visible: List<Movement> = emptyList(),
    val filter: MovementFilter = MovementFilter(),
    /** Todas las fuentes, también las archivadas (para mostrar el nombre en el historial). */
    val sources: List<IncomeSource> = emptyList(),
    /** Situación de las fuentes activas en [progressMonth]. */
    val progress: List<IncomeProgress> = emptyList(),
    val progressMonth: YearMonth = YearMonth.now(),
    /** Ingresos recibidos y gastos pagados del periodo (mes elegido o todo). */
    val totals: PeriodTotals = PeriodTotals(CurrencyAmounts.EMPTY, CurrencyAmounts.EMPTY, CurrencyAmounts.EMPTY),
    /** Por cobrar y por pagar del periodo: se muestran aparte. */
    val pending: PendingTotals = PendingTotals(CurrencyAmounts.EMPTY, CurrencyAmounts.EMPTY),
    /** Saldo calculado con todos los movimientos hasta hoy, por moneda. */
    val available: CurrencyAmounts = CurrencyAmounts.EMPTY,
    /** Ingresos previstos al mes de las fuentes activas (no es dinero disponible). */
    val projected: CurrencyAmounts = CurrencyAmounts.EMPTY,
    /** Último dinero declarado de cada moneda frente al saldo calculado. */
    val declared: List<DeclaredComparison> = emptyList(),
)

class MoneyViewModel(application: Application) : BaseViewModel(application) {

    private val filter = MutableStateFlow(MovementFilter(month = YearMonth.now()))

    val state: StateFlow<MoneyUiState> = combine(
        money.observeMovements(),
        money.observeIncomeSources(),
        money.observeDeclaredBalances(),
        filter,
        repository.settings.currency,
    ) { movements, sources, declared, f, currency ->
        val today = LocalDate.now()
        val month = f.month
        val from = month?.atDay(1)
        val to = month?.atEndOfMonth()
        val progressMonth = month ?: YearMonth.from(today)
        MoneyUiState(
            loaded = true,
            defaultCurrency = currency,
            all = movements,
            visible = Ledger.filter(movements, f),
            filter = f,
            sources = sources,
            progress = IncomePlanner.progress(sources, movements, progressMonth),
            progressMonth = progressMonth,
            totals = Ledger.rangeTotals(movements, from ?: LocalDate.MIN, to ?: LocalDate.MAX),
            pending = Ledger.pendingTotals(movements, from, to),
            available = Ledger.available(movements, upTo = today),
            projected = IncomePlanner.projectedTotal(sources),
            declared = declared
                .groupBy { it.currencyCode }
                .map { (_, list) -> Ledger.compareDeclared(list.maxWith(compareBy({ it.date }, { it.id })), movements) }
                .sortedBy { it.declared.currencyCode },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoneyUiState())

    // ---- Filtros ----

    fun previousMonth() = filter.update { it.copy(month = (it.month ?: YearMonth.now()).minusMonths(1)) }

    fun nextMonth() = filter.update { it.copy(month = (it.month ?: YearMonth.now()).plusMonths(1)) }

    /** Alterna entre el mes actual y todo el historial. */
    fun setAllTime(allTime: Boolean) = filter.update { it.copy(month = if (allTime) null else YearMonth.now()) }

    fun setKind(kind: MovementKindFilter) = filter.update {
        it.copy(kind = kind, category = if (kind == MovementKindFilter.INCOME) null else it.category)
    }

    fun setCategory(category: ExpenseCategory?) = filter.update { it.copy(category = category) }

    // ---- Movimientos ----

    fun save(movement: Movement) {
        val noun = if (movement.type == MovementType.INCOME) "Ingreso" else "Gasto"
        viewModelScope.launch {
            try {
                money.saveMovement(movement)
                say(if (movement.id == 0L) "$noun registrado" else "Cambios guardados")
            } catch (e: DuplicateIncomeException) {
                say("Ya anotaste el ingreso de ${Formats.month(e.month).lowercase()} de esta fuente")
            } catch (e: Exception) {
                say("No se pudo guardar. Inténtalo de nuevo.")
            }
        }
    }

    fun markSettled(movement: Movement, date: LocalDate) {
        val message = if (movement.type == MovementType.INCOME) "Marcado como cobrado" else "Marcado como pagado"
        launchAction(message) { money.markSettled(movement.id, date) }
    }

    fun delete(movement: Movement) {
        val noun = if (movement.type == MovementType.INCOME) "Ingreso" else "Gasto"
        launchAction("$noun eliminado") { money.deleteMovement(movement.id) }
    }

    // ---- Fuentes de ingreso ----

    fun saveSource(source: IncomeSource) {
        launchAction(if (source.id == 0L) "Fuente creada" else "Cambios guardados") { money.saveIncomeSource(source) }
    }

    fun removeSource(source: IncomeSource) {
        viewModelScope.launch {
            try {
                val archived = money.removeIncomeSource(source.id)
                say(if (archived) "Fuente archivada: sus ingresos se conservan" else "Fuente eliminada")
            } catch (e: Exception) {
                say("No se pudo completar la acción. Inténtalo de nuevo.")
            }
        }
    }

    // ---- Dinero declarado ----

    fun declare(currencyCode: String, amountMinor: Long, date: LocalDate) {
        launchAction("Dinero real guardado") { money.declareBalance(currencyCode, amountMinor, date) }
    }

    fun clearDeclared(currencyCode: String) {
        launchAction("Dinero declarado quitado") { money.clearDeclaredBalance(currencyCode) }
    }
}
