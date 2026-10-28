package com.quipu.app.domain

import java.time.LocalDate
import java.time.YearMonth

/** Ingresos y gastos externos de un periodo, y lo que se movió al ahorro (neto). */
data class PeriodTotals(
    val income: CurrencyAmounts,
    val expense: CurrencyAmounts,
    /** Traspasos al ahorro menos retiros. No es un gasto: el dinero sigue siendo del usuario. */
    val savedNet: CurrencyAmounts,
) {
    /** Ingresos menos gastos, por moneda. Los traspasos al ahorro no cuentan. */
    val net: CurrencyAmounts get() = income - expense
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

/** Por qué no se puede registrar un traspaso al ahorro, o null si se puede. */
enum class TransferError {
    INVALID_AMOUNT,
    CURRENCY_MISMATCH,
    INSUFFICIENT_AVAILABLE,
    INSUFFICIENT_SAVINGS,
}

/**
 * Cálculos sobre el libro de movimientos. Solo cuentan movimientos reales: lo previsto
 * (ingresos por horas, pagos recurrentes pendientes) nunca cambia el disponible.
 *
 * Cada movimiento cuenta una sola vez: si una lista trae el mismo id repetido (por
 * ejemplo, al combinar fuentes), se ignoran las copias.
 */
object Ledger {

    /** Quita copias con el mismo id. Los movimientos sin guardar (id 0) se conservan todos. */
    fun distinct(movements: List<Movement>): List<Movement> {
        val seen = HashSet<Long>()
        return movements.filter { it.id == 0L || seen.add(it.id) }
    }

    /** Efecto de un movimiento sobre el dinero disponible (no ahorrado). */
    fun availableEffect(movement: Movement): Long = when (movement.type) {
        MovementType.INCOME, MovementType.SAVING_WITHDRAWAL -> movement.amountMinor
        MovementType.EXPENSE, MovementType.SAVING_DEPOSIT -> -movement.amountMinor
    }

    /** Dinero disponible por moneda con los movimientos hasta [upTo] (incluido); null = todos. */
    fun available(movements: List<Movement>, upTo: LocalDate? = null): CurrencyAmounts =
        distinct(movements)
            .filter { upTo == null || !it.date.isAfter(upTo) }
            .fold(CurrencyAmounts.EMPTY) { acc, m -> acc.plus(m.currencyCode, availableEffect(m)) }

    /** Ahorrado en un objetivo: aportaciones menos retiros (en la moneda del objetivo). */
    fun goalBalance(goalId: Long, movements: List<Movement>): Long =
        distinct(movements)
            .filter { it.goalId == goalId && it.type.isTransfer }
            .sumOf { if (it.type == MovementType.SAVING_DEPOSIT) it.amountMinor else -it.amountMinor }

    fun goalProgress(goal: SavingsGoal, movements: List<Movement>): GoalProgress =
        GoalProgress(goal, goalBalance(goal.id, movements))

    /** Total ahorrado por moneda (suma de todos los objetivos). */
    fun totalSaved(movements: List<Movement>): CurrencyAmounts =
        distinct(movements)
            .filter { it.type.isTransfer }
            .fold(CurrencyAmounts.EMPTY) { acc, m -> acc.plus(m.currencyCode, -availableEffect(m)) }

    /** Totales de un mes. */
    fun periodTotals(movements: List<Movement>, month: YearMonth): PeriodTotals =
        rangeTotals(movements, month.atDay(1), month.atEndOfMonth())

    /** Totales entre dos fechas (ambas incluidas). */
    fun rangeTotals(movements: List<Movement>, from: LocalDate, to: LocalDate): PeriodTotals {
        var income = CurrencyAmounts.EMPTY
        var expense = CurrencyAmounts.EMPTY
        var saved = CurrencyAmounts.EMPTY
        distinct(movements)
            .filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
            .forEach { m ->
                when (m.type) {
                    MovementType.INCOME -> income = income.plus(m.currencyCode, m.amountMinor)
                    MovementType.EXPENSE -> expense = expense.plus(m.currencyCode, m.amountMinor)
                    MovementType.SAVING_DEPOSIT -> saved = saved.plus(m.currencyCode, m.amountMinor)
                    MovementType.SAVING_WITHDRAWAL -> saved = saved.plus(m.currencyCode, -m.amountMinor)
                }
            }
        return PeriodTotals(income, expense, saved)
    }

    /** Historial: del más reciente al más antiguo; a igual fecha, el último registrado primero. */
    fun history(movements: List<Movement>, types: Set<MovementType> = MovementType.entries.toSet()): List<Movement> =
        distinct(movements)
            .filter { it.type in types }
            .sortedWith(compareByDescending<Movement> { it.date }.thenByDescending { it.id })

    /**
     * Comprueba un traspaso entre el disponible y un objetivo. Una aportación no puede
     * superar el disponible de esa moneda y un retiro no puede superar lo ahorrado.
     * El objetivo y el traspaso deben ir en la misma moneda.
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
            type == MovementType.SAVING_DEPOSIT && amountMinor > available(movements)[currencyCode] ->
                TransferError.INSUFFICIENT_AVAILABLE
            type == MovementType.SAVING_WITHDRAWAL && amountMinor > goalBalance(goal.id, movements) ->
                TransferError.INSUFFICIENT_SAVINGS
            else -> null
        }
    }
}
