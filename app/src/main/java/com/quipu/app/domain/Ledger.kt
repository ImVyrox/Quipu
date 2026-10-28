package com.quipu.app.domain

import java.time.LocalDate
import java.time.YearMonth

/** Ingresos y gastos externos de un periodo, y lo que se movió al ahorro (neto). */
data class PeriodTotals(
    val income: CurrencyAmounts,
    val expense: CurrencyAmounts,
    /**
     * Aportaciones al ahorro menos retiros. No es un gasto: el dinero sigue siendo del
     * usuario. El ahorro inicial no cuenta aquí (no salió del disponible del periodo).
     */
    val savedNet: CurrencyAmounts,
) {
    /** Ingresos menos gastos, por moneda. Los traspasos al ahorro no cuentan. */
    val net: CurrencyAmounts get() = income - expense
}

/** Ingresos por cobrar y gastos por pagar: previstos, no cuentan en el saldo. */
data class PendingTotals(
    val income: CurrencyAmounts,
    val expense: CurrencyAmounts,
)

enum class MovementKindFilter { ALL, INCOME, EXPENSE, SAVINGS }

/** Filtro del historial: mes ([month] null = todo), tipo y categoría de gasto. */
data class MovementFilter(
    val month: YearMonth? = null,
    val kind: MovementKindFilter = MovementKindFilter.ALL,
    val category: ExpenseCategory? = null,
)

/** Dinero declarado frente al total calculado (disponible + ahorros) hasta esa fecha. */
data class DeclaredComparison(
    val declared: DeclaredBalance,
    val calculatedMinor: Long,
) {
    /** Positivo: el usuario tiene más de lo que dicen sus movimientos (algún ingreso sin anotar). */
    val differenceMinor: Long get() = declared.amountMinor - calculatedMinor
}

/** Progreso de un objetivo de ahorro. */
data class GoalProgress(
    val goal: SavingsGoal,
    val savedMinor: Long,
) {
    val remainingMinor: Long get() = (goal.targetMinor - savedMinor).coerceAtLeast(0L)

    /** 0..100, redondeado hacia abajo (99,9 % no se muestra como 100 %). */
    val percent: Int
        get() = if (goal.targetMinor <= 0L) 100 else (savedMinor.coerceAtLeast(0L) * 100 / goal.targetMinor).coerceAtMost(100L).toInt()

    val reached: Boolean get() = savedMinor >= goal.targetMinor
}

/**
 * Dinero por moneda: el disponible, lo apartado en objetivos de ahorro y el total.
 * Aportar o retirar mueve dinero entre los dos primeros sin cambiar el total.
 */
data class BalanceOverview(
    val available: CurrencyAmounts,
    val saved: CurrencyAmounts,
) {
    val total: CurrencyAmounts get() = available + saved
}

/** Por qué no se puede registrar (o borrar) un movimiento de ahorro, o null si se puede. */
enum class TransferError(val message: String) {
    INVALID_AMOUNT("El importe debe ser mayor que cero"),
    CURRENCY_MISMATCH("La moneda no coincide con la del objetivo"),
    INSUFFICIENT_AVAILABLE("No tienes tanto disponible en esa moneda"),
    INSUFFICIENT_SAVINGS("Supera lo ahorrado en este objetivo"),
    GOAL_NOT_ACTIVE("El objetivo está en pausa o completado: no admite aportaciones"),
    OPENING_ALREADY_EXISTS("Este objetivo ya tiene un ahorro inicial"),
    GOAL_DELETED("El objetivo fue eliminado: su historial ya no se puede cambiar"),
}

/**
 * Cálculos sobre el libro de movimientos. Solo cuentan movimientos reales: lo previsto
 * (ingresos por horas, pagos recurrentes pendientes) nunca cambia el disponible.
 *
 * Cada movimiento cuenta una sola vez: si una lista trae el mismo id repetido (por
 * ejemplo, al combinar fuentes), se ignoran las copias. Los movimientos pendientes
 * (por cobrar o por pagar) aparecen en el historial pero no en los saldos.
 */
object Ledger {

    /** Quita copias con el mismo id. Los movimientos sin guardar (id 0) se conservan todos. */
    fun distinct(movements: List<Movement>): List<Movement> {
        val seen = HashSet<Long>()
        return movements.filter { it.id == 0L || seen.add(it.id) }
    }

    /** Movimientos que ya ocurrieron (sin copias). Los pendientes no cuentan en ningún saldo. */
    fun settled(movements: List<Movement>): List<Movement> = distinct(movements).filter { !it.pending }

    /** Efecto de un movimiento sobre el dinero disponible (no ahorrado). */
    fun availableEffect(movement: Movement): Long = when (movement.type) {
        MovementType.INCOME, MovementType.SAVING_WITHDRAWAL -> movement.amountMinor
        MovementType.EXPENSE, MovementType.SAVING_DEPOSIT -> -movement.amountMinor
        MovementType.SAVING_OPENING -> 0L
    }

    /** Efecto de un movimiento sobre lo ahorrado en su objetivo. */
    fun savingEffect(movement: Movement): Long = when (movement.type) {
        MovementType.SAVING_DEPOSIT, MovementType.SAVING_OPENING -> movement.amountMinor
        MovementType.SAVING_WITHDRAWAL -> -movement.amountMinor
        MovementType.INCOME, MovementType.EXPENSE -> 0L
    }

    /** Dinero disponible por moneda con los movimientos hasta [upTo] (incluido); null = todos. */
    fun available(movements: List<Movement>, upTo: LocalDate? = null): CurrencyAmounts =
        settled(movements)
            .filter { upTo == null || !it.date.isAfter(upTo) }
            .fold(CurrencyAmounts.EMPTY) { acc, m -> acc.plus(m.currencyCode, availableEffect(m)) }

    /** Ahorrado en un objetivo: ahorro inicial más aportaciones menos retiros. */
    fun goalBalance(goalId: Long, movements: List<Movement>): Long =
        settled(movements)
            .filter { it.goalId == goalId && it.type.isSaving }
            .sumOf { savingEffect(it) }

    /** Movimientos de un objetivo, del más reciente al más antiguo. */
    fun goalHistory(goalId: Long, movements: List<Movement>): List<Movement> =
        history(movements).filter { it.goalId == goalId && it.type.isSaving }

    fun goalProgress(goal: SavingsGoal, movements: List<Movement>): GoalProgress =
        GoalProgress(goal, goalBalance(goal.id, movements))

    /** Total ahorrado por moneda (suma de todos los objetivos). */
    fun totalSaved(movements: List<Movement>, upTo: LocalDate? = null): CurrencyAmounts =
        settled(movements)
            .filter { it.type.isSaving && (upTo == null || !it.date.isAfter(upTo)) }
            .fold(CurrencyAmounts.EMPTY) { acc, m -> acc.plus(m.currencyCode, savingEffect(m)) }

    /** Disponible, ahorrado y total por moneda hasta [upTo] (null = todos los movimientos). */
    fun overview(movements: List<Movement>, upTo: LocalDate? = null): BalanceOverview =
        BalanceOverview(available(movements, upTo), totalSaved(movements, upTo))

    /** Totales de un mes. */
    fun periodTotals(movements: List<Movement>, month: YearMonth): PeriodTotals =
        rangeTotals(movements, month.atDay(1), month.atEndOfMonth())

    /** Totales entre dos fechas (ambas incluidas). */
    fun rangeTotals(movements: List<Movement>, from: LocalDate, to: LocalDate): PeriodTotals {
        var income = CurrencyAmounts.EMPTY
        var expense = CurrencyAmounts.EMPTY
        var saved = CurrencyAmounts.EMPTY
        settled(movements)
            .filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
            .forEach { m ->
                when (m.type) {
                    MovementType.INCOME -> income = income.plus(m.currencyCode, m.amountMinor)
                    MovementType.EXPENSE -> expense = expense.plus(m.currencyCode, m.amountMinor)
                    MovementType.SAVING_DEPOSIT -> saved = saved.plus(m.currencyCode, m.amountMinor)
                    MovementType.SAVING_WITHDRAWAL -> saved = saved.plus(m.currencyCode, -m.amountMinor)
                    MovementType.SAVING_OPENING -> Unit
                }
            }
        return PeriodTotals(income, expense, saved)
    }

    /**
     * Ingresos por cobrar y gastos por pagar con fecha entre [from] y [to] (incluidas;
     * null = sin límite). Se muestran aparte: no son dinero disponible.
     */
    fun pendingTotals(movements: List<Movement>, from: LocalDate? = null, to: LocalDate? = null): PendingTotals {
        var income = CurrencyAmounts.EMPTY
        var expense = CurrencyAmounts.EMPTY
        distinct(movements)
            .filter { it.pending && (from == null || !it.date.isBefore(from)) && (to == null || !it.date.isAfter(to)) }
            .forEach { m ->
                when (m.type) {
                    MovementType.INCOME -> income = income.plus(m.currencyCode, m.amountMinor)
                    MovementType.EXPENSE -> expense = expense.plus(m.currencyCode, m.amountMinor)
                    else -> Unit
                }
            }
        return PendingTotals(income, expense)
    }

    /**
     * Movimientos del historial que cumplen el filtro, del más reciente al más antiguo.
     * La categoría solo se aplica a gastos.
     */
    fun filter(movements: List<Movement>, filter: MovementFilter): List<Movement> =
        history(movements).filter { m ->
            (filter.month == null || YearMonth.from(m.date) == filter.month) &&
                when (filter.kind) {
                    MovementKindFilter.ALL -> filter.category == null || m.category == filter.category
                    MovementKindFilter.INCOME -> m.type == MovementType.INCOME
                    MovementKindFilter.EXPENSE ->
                        m.type == MovementType.EXPENSE && (filter.category == null || m.category == filter.category)
                    MovementKindFilter.SAVINGS -> m.type.isSaving
                }
        }

    /**
     * Compara el dinero que el usuario declara tener con el total calculado de esa moneda
     * (disponible + ahorros: lo apartado sigue siendo suyo) en la misma fecha. La
     * diferencia es informativa: no crea ningún movimiento.
     */
    fun compareDeclared(declared: DeclaredBalance, movements: List<Movement>): DeclaredComparison {
        val calculated = overview(movements, upTo = declared.date).total[declared.currencyCode]
        return DeclaredComparison(declared, calculated)
    }

    /** Historial: del más reciente al más antiguo; a igual fecha, el último registrado primero. */
    fun history(movements: List<Movement>, types: Set<MovementType> = MovementType.entries.toSet()): List<Movement> =
        distinct(movements)
            .filter { it.type in types }
            .sortedWith(compareByDescending<Movement> { it.date }.thenByDescending { it.id })

    /**
     * Comprueba un traspaso entre el disponible y un objetivo. Una aportación no puede
     * superar el disponible de esa moneda (el disponible nunca queda en negativo por
     * ahorrar) y solo se admite en objetivos activos; un retiro no puede superar lo
     * ahorrado. El objetivo y el traspaso deben ir en la misma moneda.
     */
    fun validateTransfer(
        type: MovementType,
        amountMinor: Long,
        currencyCode: String,
        goal: SavingsGoal,
        movements: List<Movement>,
    ): TransferError? {
        require(type.isTransfer) { "No es un traspaso: $type" }
        return when {
            amountMinor <= 0L || amountMinor > Money.MAX_MINOR -> TransferError.INVALID_AMOUNT
            currencyCode != goal.currencyCode -> TransferError.CURRENCY_MISMATCH
            type == MovementType.SAVING_DEPOSIT && goal.status != GoalStatus.ACTIVE -> TransferError.GOAL_NOT_ACTIVE
            type == MovementType.SAVING_DEPOSIT && amountMinor > available(movements)[currencyCode] ->
                TransferError.INSUFFICIENT_AVAILABLE
            type == MovementType.SAVING_WITHDRAWAL && amountMinor > goalBalance(goal.id, movements) ->
                TransferError.INSUFFICIENT_SAVINGS
            else -> null
        }
    }

    /** Comprueba el ahorro inicial de un objetivo: positivo, en su moneda y solo uno. */
    fun validateOpening(amountMinor: Long, goal: SavingsGoal, movements: List<Movement>): TransferError? = when {
        amountMinor <= 0L || amountMinor > Money.MAX_MINOR -> TransferError.INVALID_AMOUNT
        distinct(movements).any { it.goalId == goal.id && it.type == MovementType.SAVING_OPENING } ->
            TransferError.OPENING_ALREADY_EXISTS
        else -> null
    }

    /**
     * Si se puede borrar un movimiento de ahorro: lo ahorrado en su objetivo no puede
     * quedar en negativo (por ejemplo, borrar una aportación ya retirada).
     */
    fun validateRemoval(movement: Movement, movements: List<Movement>): TransferError? {
        val goalId = movement.goalId ?: return null
        if (!movement.type.isSaving) return null
        val after = goalBalance(goalId, movements.filter { it.id != movement.id })
        return if (after < 0) TransferError.INSUFFICIENT_SAVINGS else null
    }

    /**
     * Retiro que devuelve al disponible todo lo ahorrado en un objetivo, o null si no hay
     * nada. Se usa al eliminar un objetivo para no hacer desaparecer dinero.
     */
    fun closingWithdrawal(goal: SavingsGoal, movements: List<Movement>, date: LocalDate): Movement? {
        val balance = goalBalance(goal.id, movements)
        if (balance <= 0L) return null
        return Movement(
            id = 0,
            type = MovementType.SAVING_WITHDRAWAL,
            amountMinor = balance,
            currencyCode = goal.currencyCode,
            date = date,
            description = "Cierre de «${goal.name}»",
            goalId = goal.id,
        )
    }
}
