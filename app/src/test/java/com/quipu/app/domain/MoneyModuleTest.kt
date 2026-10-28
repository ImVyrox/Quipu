package com.quipu.app.domain

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ingresos, gastos y saldo de la fase 2: pendientes, horas trabajadas, filtros y dinero declarado. */
class MoneyModuleTest {
    private val today = LocalDate.of(2026, 10, 25)
    private val october = YearMonth.of(2026, 10)
    private val hourly = IncomePlanner.EXAMPLE_HOURLY.copy(id = 1)
    private val salary = IncomeSource(2, "Sueldo", IncomeKind.FIXED, "BOB", fixedAmountMinor = 300_000)

    private fun hours(id: Long, minutes: Int, workDate: LocalDate, pending: Boolean = false, received: LocalDate = workDate) =
        Movement(
            id, MovementType.INCOME, IncomePlanner.amountForMinutes(1_800, minutes), "BOB",
            date = if (pending) workDate else received,
            pending = pending, incomeSourceId = 1, workDate = workDate, workedMinutes = minutes,
        )

    private fun expense(id: Long, amount: Long, category: ExpenseCategory, date: LocalDate = today, pending: Boolean = false) =
        Movement(id, MovementType.EXPENSE, amount, "BOB", date, category = category, pending = pending)

    @Test
    fun `lo pendiente no cuenta en el saldo y se muestra aparte`() {
        val movements = listOf(
            Movement(1, MovementType.INCOME, 50_000, "BOB", today),
            Movement(2, MovementType.INCOME, 20_000, "BOB", today, pending = true),
            expense(3, 10_000, ExpenseCategory.FOOD),
            expense(4, 7_000, ExpenseCategory.HEALTH, pending = true),
        )
        assertEquals(40_000L, Ledger.available(movements)["BOB"])
        val totals = Ledger.periodTotals(movements, october)
        assertEquals(50_000L, totals.income["BOB"])
        assertEquals(10_000L, totals.expense["BOB"])
        val pending = Ledger.pendingTotals(movements, october.atDay(1), october.atEndOfMonth())
        assertEquals(CurrencyAmounts.of("BOB" to 20_000), pending.income)
        assertEquals(CurrencyAmounts.of("BOB" to 7_000), pending.expense)
        // El historial sí muestra los pendientes.
        assertEquals(4, Ledger.history(movements).size)
    }

    @Test
    fun `horas trabajadas y cobro por horas`() {
        val movements = listOf(
            hours(1, 10 * 60, LocalDate.of(2026, 10, 3)),
            hours(2, 8 * 60, LocalDate.of(2026, 10, 10), received = LocalDate.of(2026, 10, 12)),
            hours(3, 6 * 60, LocalDate.of(2026, 10, 24), pending = true),
            hours(4, 5 * 60, LocalDate.of(2026, 9, 28), received = LocalDate.of(2026, 10, 1)), // trabajo de septiembre cobrado en octubre
        )
        val progress = IncomePlanner.progress(listOf(hourly), movements, october).single()
        assertEquals(42 * 60, progress.plannedMinutes)
        assertEquals(24 * 60, progress.workedMinutes) // 10 + 8 + 6 (las de septiembre no)
        assertEquals(75_600L, progress.projectedMinor)
        assertEquals(18_000L + 14_400L + 9_000L, progress.receivedMinor) // cobrado en octubre
        assertEquals(10_800L, progress.toCollectMinor)
        assertEquals(5 * 60, IncomePlanner.workedMinutesIn(hourly, movements, YearMonth.of(2026, 9)))
    }

    @Test
    fun `ingreso fijo una vez por mes`() {
        val paidOctober = Movement(9, MovementType.INCOME, 300_000, "BOB", today, incomeSourceId = 2, period = october)
        assertEquals(paidOctober, IncomePlanner.fixedIncomeFor(salary, october, listOf(paidOctober)))
        // Editar el mismo ingreso no es un duplicado.
        assertNull(IncomePlanner.fixedIncomeFor(salary, october, listOf(paidOctober), exceptId = 9))
        // Noviembre está libre: cambiar de mes no copia el ingreso.
        assertNull(IncomePlanner.fixedIncomeFor(salary, october.plusMonths(1), listOf(paidOctober)))
        assertNull(IncomePlanner.fixedIncomeFor(hourly, october, listOf(paidOctober)))
        assertEquals(300_000L, IncomePlanner.progress(listOf(salary), listOf(paidOctober), october).single().receivedMinor)
        assertEquals(0L, IncomePlanner.progress(listOf(salary), listOf(paidOctober), october.plusMonths(1)).single().receivedMinor)
    }

    @Test
    fun `filtra por periodo tipo y categoria`() {
        val movements = listOf(
            expense(1, 100, ExpenseCategory.FOOD, LocalDate.of(2026, 10, 1)),
            expense(2, 100, ExpenseCategory.TRANSPORT, LocalDate.of(2026, 10, 2)),
            expense(3, 100, ExpenseCategory.FOOD, LocalDate.of(2026, 9, 30)),
            Movement(4, MovementType.INCOME, 100, "BOB", LocalDate.of(2026, 10, 3)),
        )
        fun ids(filter: MovementFilter) = Ledger.filter(movements, filter).map { it.id }
        assertEquals(listOf(4L, 2L, 1L, 3L), ids(MovementFilter()))
        assertEquals(listOf(4L, 2L, 1L), ids(MovementFilter(month = october)))
        assertEquals(listOf(2L, 1L), ids(MovementFilter(month = october, kind = MovementKindFilter.EXPENSE)))
        assertEquals(listOf(1L, 3L), ids(MovementFilter(kind = MovementKindFilter.EXPENSE, category = ExpenseCategory.FOOD)))
        assertEquals(listOf(4L), ids(MovementFilter(kind = MovementKindFilter.INCOME)))
    }

    @Test
    fun `dinero declarado frente al saldo calculado`() {
        val movements = listOf(
            Movement(1, MovementType.INCOME, 50_000, "BOB", today.minusDays(5)),
            expense(2, 10_000, ExpenseCategory.FOOD, today.minusDays(1)),
            expense(3, 5_000, ExpenseCategory.FOOD, today.plusDays(1)), // posterior a la declaración
        )
        val declared = DeclaredBalance(1, "BOB", 42_000, today)
        val comparison = Ledger.compareDeclared(declared, movements)
        assertEquals(40_000L, comparison.calculatedMinor)
        assertEquals(2_000L, comparison.differenceMinor)
        // Declarar no cambia el saldo calculado.
        assertEquals(35_000L, Ledger.available(movements)["BOB"])
    }

    @Test
    fun `valida gasto con categoria obligatoria`() {
        val missing = FinanceValidators.validateExpense("10", today, null, "")
        assertEquals("Elige una categoría", missing.category)
        assertFalse(FinanceValidators.validateExpense("10", today, ExpenseCategory.OTHER, "").hasErrors)
        assertEquals("Escribe el importe", FinanceValidators.validateExpense("", today, ExpenseCategory.OTHER, "").amount)
    }

    @Test
    fun `valida ingreso por horas y normal`() {
        val okHourly = FinanceValidators.validateIncome(true, "", "6", today, received = true, receivedDate = today, description = "")
        assertFalse(okHourly.hasErrors)
        val noHours = FinanceValidators.validateIncome(true, "", "", null, received = true, receivedDate = null, description = "")
        assertEquals("Escribe las horas trabajadas", noHours.hours)
        assertEquals("Elige el día trabajado", noHours.date)
        assertEquals("Elige la fecha de cobro", noHours.receivedDate)
        assertNull(noHours.amount)
        val early = FinanceValidators.validateIncome(true, "", "6", today, received = true, receivedDate = today.minusDays(1), description = "")
        assertEquals("No puede ser anterior al día trabajado", early.receivedDate)
        val pending = FinanceValidators.validateIncome(true, "", "6", today, received = false, receivedDate = null, description = "")
        assertFalse(pending.hasErrors)

        val normal = FinanceValidators.validateIncome(false, "", "", today, received = true, receivedDate = null, description = "")
        assertEquals("Escribe el importe", normal.amount)
        assertNull(normal.hours)
        assertNull(normal.receivedDate)
    }

    @Test
    fun `dinero declarado admite cero`() {
        assertNull(FinanceValidators.validateDeclared("0"))
        assertNull(FinanceValidators.validateDeclared("0,00"))
        assertNull(FinanceValidators.validateDeclared("150,50"))
        assertEquals("Escribe cuánto tienes", FinanceValidators.validateDeclared(""))
        assertTrue(FinanceValidators.validateDeclared("abc") != null)
    }
}
