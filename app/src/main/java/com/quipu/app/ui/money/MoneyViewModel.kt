package com.quipu.app.ui.money

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.quipu.app.data.DuplicateIncomeException
import com.quipu.app.data.SavingRejectedException
import com.quipu.app.domain.AppCurrencies
import com.quipu.app.domain.AppCurrency
import com.quipu.app.domain.BalanceOverview
import com.quipu.app.domain.ConfirmResult
import com.quipu.app.domain.CurrencyAmounts
import com.quipu.app.domain.DeclaredComparison
import com.quipu.app.domain.ExpenseCategory
import com.quipu.app.domain.GoalProgress
import com.quipu.app.domain.GoalStatus
import com.quipu.app.domain.IncomePlanner
import com.quipu.app.domain.IncomeProgress
import com.quipu.app.domain.IncomeSource
import com.quipu.app.domain.Ledger
import com.quipu.app.domain.Money
import com.quipu.app.domain.Movement
import com.quipu.app.domain.MovementFilter
import com.quipu.app.domain.MovementKindFilter
import com.quipu.app.domain.MovementType
import com.quipu.app.domain.PendingTotals
import com.quipu.app.domain.PeriodTotals
import com.quipu.app.domain.RecurringPayment
import com.quipu.app.domain.RecurringPlanner
import com.quipu.app.domain.RecurringSchedule
import com.quipu.app.domain.SavingsGoal
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
    /** Disponible, ahorrado y total con todos los movimientos hasta hoy, por moneda. */
    val overview: BalanceOverview = BalanceOverview(CurrencyAmounts.EMPTY, CurrencyAmounts.EMPTY),
    /** Objetivos de ahorro con lo acumulado: activos primero, luego en pausa y completados. */
    val goals: List<GoalProgress> = emptyList(),
    /** Ingresos previstos al mes de las fuentes activas (no es dinero disponible). */
    val projected: CurrencyAmounts = CurrencyAmounts.EMPTY,
    /** Último dinero declarado de cada moneda frente al saldo calculado. */
    val declared: List<DeclaredComparison> = emptyList(),
    /** Pagos recurrentes: vencidos primero, luego por próximo vencimiento, en pausa al final. */
    val recurring: List<RecurringSchedule> = emptyList(),
    /** Lo que falta pagar de los recurrentes en [progressMonth] (previsto: no reduce el saldo). */
    val recurringPending: CurrencyAmounts = CurrencyAmounts.EMPTY,
)

class MoneyViewModel(application: Application) : BaseViewModel(application) {

    private val filter = MutableStateFlow(MovementFilter(month = YearMonth.now()))

    val state: StateFlow<MoneyUiState> = combine(
        money.observeMovements(),
        money.observeIncomeSources(),
        money.observeDeclaredBalances(),
        combine(money.observeGoals(), money.observeRecurring()) { g, r -> g to r },
        combine(filter, repository.settings.currency) { f, c -> f to c },
    ) { movements, sources, declared, (goals, recurring), (f, currency) ->
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
            overview = Ledger.overview(movements, upTo = today),
            goals = goals
                .map { Ledger.goalProgress(it, movements) }
                .sortedWith(compareBy<GoalProgress>({ it.goal.status.ordinal }, { it.goal.id })),
            projected = IncomePlanner.projectedTotal(sources),
            declared = declared
                .groupBy { it.currencyCode }
                .map { (_, list) -> Ledger.compareDeclared(list.maxWith(compareBy({ it.date }, { it.id })), movements) }
                .sortedBy { it.declared.currencyCode },
            recurring = RecurringPlanner.schedules(recurring, movements, today),
            recurringPending = RecurringPlanner.pendingTotal(recurring, movements, progressMonth, today),
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
        val message = when (movement.type) {
            MovementType.INCOME -> "Ingreso eliminado"
            MovementType.EXPENSE -> "Gasto eliminado"
            else -> "Movimiento de ahorro eliminado"
        }
        runSaving(message) { money.deleteMovement(movement.id) }
    }

    // ---- Objetivos de ahorro ----

    /** Crea o edita un objetivo; si es nuevo y trae ahorro inicial, lo registra explícitamente. */
    fun saveGoal(goal: SavingsGoal, openingMinor: Long?) {
        runSaving(if (goal.id == 0L) "Objetivo creado" else "Cambios guardados") {
            val id = money.saveGoal(goal)
            if (goal.id == 0L && openingMinor != null && openingMinor > 0) {
                money.addSaving(id, MovementType.SAVING_OPENING, openingMinor, goal.createdDate)
            }
        }
    }

    fun setGoalStatus(goal: SavingsGoal, status: GoalStatus) {
        val message = when (status) {
            GoalStatus.ACTIVE -> "Objetivo reanudado"
            GoalStatus.PAUSED -> "Objetivo en pausa"
            GoalStatus.COMPLETED -> "¡Objetivo completado!"
        }
        launchAction(message) { money.setGoalStatus(goal.id, status, LocalDate.now()) }
    }

    fun addSaving(goal: SavingsGoal, type: MovementType, amountMinor: Long, date: LocalDate, description: String) {
        val message = when (type) {
            MovementType.SAVING_DEPOSIT -> "Aportación registrada"
            MovementType.SAVING_WITHDRAWAL -> "Retiro registrado: el dinero volvió a tu disponible"
            else -> "Ahorro inicial registrado"
        }
        runSaving(message) { money.addSaving(goal.id, type, amountMinor, date, description) }
    }

    fun deleteGoal(goal: SavingsGoal) {
        viewModelScope.launch {
            try {
                val returned = money.deleteGoal(goal.id, LocalDate.now())
                say(
                    if (returned > 0) {
                        "Objetivo eliminado: ${Money.format(returned, AppCurrencies.byCode(goal.currencyCode))} volvió a tu disponible"
                    } else {
                        "Objetivo eliminado"
                    },
                )
            } catch (e: Exception) {
                say("No se pudo completar la acción. Inténtalo de nuevo.")
            }
        }
    }

    /** Como launchAction, pero muestra el motivo cuando la política de ahorro rechaza la operación. */
    private fun runSaving(success: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                say(success)
            } catch (e: SavingRejectedException) {
                say(e.error.message)
            } catch (e: Exception) {
                say("No se pudo completar la acción. Inténtalo de nuevo.")
            }
        }
    }

    // ---- Pagos recurrentes ----

    fun saveRecurring(payment: RecurringPayment) {
        launchAction(if (payment.id == 0L) "Pago recurrente creado" else "Cambios guardados") { money.saveRecurring(payment) }
    }

    fun setRecurringActive(payment: RecurringPayment, active: Boolean) {
        launchAction(if (active) "Pago reanudado" else "Pago en pausa: no generará pendientes ni avisos") {
            money.setRecurringActive(payment.id, active)
        }
    }

    fun deleteRecurring(payment: RecurringPayment) {
        launchAction("Pago recurrente eliminado. Sus pagos se conservan en el historial") { money.deleteRecurring(payment.id) }
    }

    /** Confirma un vencimiento. Confirmarlo dos veces no crea otro gasto: lo avisa. */
    fun confirmRecurring(payment: RecurringPayment, period: YearMonth, amountMinor: Long, date: LocalDate) {
        viewModelScope.launch {
            try {
                when (money.confirmRecurring(payment.id, period, date, amountMinor)) {
                    is ConfirmResult.Created -> say("Pago de ${Formats.month(period).lowercase()} registrado")
                    is ConfirmResult.AlreadyPaid -> say("Ese mes ya estaba pagado: no se registró otro gasto")
                    ConfirmResult.NotDue -> say("Ese pago no vence en ${Formats.month(period).lowercase()}")
                }
            } catch (e: Exception) {
                say("No se pudo completar la acción. Inténtalo de nuevo.")
            }
        }
    }

    /** Usa un gasto ya anotado como pago del vencimiento, en vez de registrar otro. */
    fun linkRecurring(movement: Movement, payment: RecurringPayment, period: YearMonth) {
        launchAction("Gasto vinculado al pago de ${Formats.month(period).lowercase()}") {
            money.linkRecurring(movement.id, payment.id, period)
        }
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
