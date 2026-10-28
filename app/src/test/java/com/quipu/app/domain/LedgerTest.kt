package com.quipu.app.domain

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerTest {
    private val today = LocalDate.of(2026, 10, 25)
    private val october = YearMonth.of(2026, 10)
    private val goal = SavingsGoal(1, "Viaje", "BOB", targetMinor = 100_000, createdDate = LocalDate.of(2026, 1, 1))

    private fun m(
        id: Long,
        type: MovementType,
        amount: Long,
        currency: String = "BOB",
        date: LocalDate = today,
        goalId: Long? = null,
    ) = Movement(id, type, amount, currency, date, goalId = goalId)

    @Test
    fun `disponible es ingresos menos gastos menos lo apartado`() {
        val movements = listOf(
            m(1, MovementType.INCOME, 100_000),
            m(2, MovementType.EXPENSE, 25_050),
            m(3, MovementType.SAVING_DEPOSIT, 30_000, goalId = 1),
            m(4, MovementType.SAVING_WITHDRAWAL, 10_000, goalId = 1),
        )
        assertEquals(CurrencyAmounts.of("BOB" to 54_950), Ledger.available(movements).nonZero())
        assertEquals(20_000L, Ledger.goalBalance(1, movements))
        assertEquals(CurrencyAmounts.of("BOB" to 20_000), Ledger.totalSaved(movements))
    }

    @Test
    fun `traspasos al ahorro no son ingresos ni gastos`() {
        val movements = listOf(
            m(1, MovementType.INCOME, 100_000),
            m(2, MovementType.SAVING_DEPOSIT, 30_000, goalId = 1),
            m(3, MovementType.SAVING_WITHDRAWAL, 5_000, goalId = 1),
            m(4, MovementType.EXPENSE, 1_000),
        )
        val totals = Ledger.periodTotals(movements, october)
        assertEquals(CurrencyAmounts.of("BOB" to 100_000), totals.income)
        assertEquals(CurrencyAmounts.of("BOB" to 1_000), totals.expense)
        assertEquals(CurrencyAmounts.of("BOB" to 25_000), totals.savedNet)
        assertEquals(CurrencyAmounts.of("BOB" to 99_000), totals.net)
    }

    @Test
    fun `no mezcla monedas`() {
        val movements = listOf(
            m(1, MovementType.INCOME, 100_000, "BOB"),
            m(2, MovementType.INCOME, 5_000, "USD"),
            m(3, MovementType.EXPENSE, 2_000, "USD"),
        )
        val available = Ledger.available(movements)
        assertEquals(100_000L, available["BOB"])
        assertEquals(3_000L, available["USD"])
        assertEquals(0L, available["EUR"])
        assertEquals(setOf("BOB", "USD"), available.byCode.keys)
    }

    @Test
    fun `cada movimiento cuenta una sola vez aunque llegue repetido`() {
        val expense = m(2, MovementType.EXPENSE, 4_000)
        val movements = listOf(m(1, MovementType.INCOME, 10_000), expense, expense.copy())
        assertEquals(6_000L, Ledger.available(movements)["BOB"])
        assertEquals(CurrencyAmounts.of("BOB" to 4_000), Ledger.periodTotals(movements, october).expense)
        assertEquals(2, Ledger.history(movements).size)
    }

    @Test
    fun `disponible hasta una fecha ignora lo posterior`() {
        val movements = listOf(
            m(1, MovementType.INCOME, 10_000, date = today.minusDays(1)),
            m(2, MovementType.EXPENSE, 3_000, date = today.plusDays(1)),
        )
        assertEquals(10_000L, Ledger.available(movements, upTo = today)["BOB"])
        assertEquals(7_000L, Ledger.available(movements)["BOB"])
    }

    @Test
    fun `totales del mes excluyen otros meses`() {
        val movements = listOf(
            m(1, MovementType.EXPENSE, 1_000, date = LocalDate.of(2026, 9, 30)),
            m(2, MovementType.EXPENSE, 2_000, date = LocalDate.of(2026, 10, 1)),
            m(3, MovementType.EXPENSE, 3_000, date = LocalDate.of(2026, 10, 31)),
            m(4, MovementType.EXPENSE, 4_000, date = LocalDate.of(2026, 11, 1)),
        )
        assertEquals(5_000L, Ledger.periodTotals(movements, october).expense["BOB"])
    }

    @Test
    fun `historial del mas reciente al mas antiguo`() {
        val movements = listOf(
            m(1, MovementType.INCOME, 1, date = today.minusDays(2)),
            m(2, MovementType.EXPENSE, 1, date = today),
            m(3, MovementType.EXPENSE, 1, date = today),
            m(4, MovementType.SAVING_DEPOSIT, 1, date = today.minusDays(1), goalId = 1),
        )
        assertEquals(listOf(3L, 2L, 4L, 1L), Ledger.history(movements).map { it.id })
        assertEquals(listOf(3L, 2L), Ledger.history(movements, setOf(MovementType.EXPENSE)).map { it.id })
    }

    @Test
    fun `aportacion no puede superar el disponible ni un retiro lo ahorrado`() {
        val movements = listOf(
            m(1, MovementType.INCOME, 50_000),
            m(2, MovementType.SAVING_DEPOSIT, 20_000, goalId = 1),
        )
        assertNull(Ledger.validateTransfer(MovementType.SAVING_DEPOSIT, 30_000, "BOB", goal, movements))
        assertEquals(
            TransferError.INSUFFICIENT_AVAILABLE,
            Ledger.validateTransfer(MovementType.SAVING_DEPOSIT, 30_001, "BOB", goal, movements),
        )
        assertNull(Ledger.validateTransfer(MovementType.SAVING_WITHDRAWAL, 20_000, "BOB", goal, movements))
        assertEquals(
            TransferError.INSUFFICIENT_SAVINGS,
            Ledger.validateTransfer(MovementType.SAVING_WITHDRAWAL, 20_001, "BOB", goal, movements),
        )
        assertEquals(
            TransferError.CURRENCY_MISMATCH,
            Ledger.validateTransfer(MovementType.SAVING_DEPOSIT, 100, "USD", goal, movements),
        )
        assertEquals(
            TransferError.INVALID_AMOUNT,
            Ledger.validateTransfer(MovementType.SAVING_DEPOSIT, 0, "BOB", goal, movements),
        )
    }

    @Test
    fun `progreso del objetivo`() {
        val movements = listOf(m(1, MovementType.SAVING_DEPOSIT, 99_999, goalId = 1))
        val progress = Ledger.goalProgress(goal, movements)
        assertEquals(99, progress.percent)
        assertEquals(1L, progress.remainingMinor)
        assertFalse(progress.reached)
        val done = Ledger.goalProgress(goal, movements + m(2, MovementType.SAVING_DEPOSIT, 5_000, goalId = 1))
        assertEquals(100, done.percent)
        assertEquals(0L, done.remainingMinor)
        assertTrue(done.reached)
    }

    @Test
    fun `gasto exige importe y fecha pero no descripcion`() {
        assertFalse(FinanceValidators.validateMovement("12,50", today, "").hasErrors)
        val errors = FinanceValidators.validateMovement("", null, "")
        assertEquals("Escribe el importe", errors.amount)
        assertEquals("Elige la fecha", errors.date)
        assertNull(errors.description)
        assertEquals("El importe debe ser mayor que cero", FinanceValidators.validateMovement("0", today, "").amount)
        assertTrue(FinanceValidators.validateMovement("1", today, "x".repeat(Limits.NOTE_MAX + 1)).hasErrors)
    }

    @Test
    fun `suma y resta por moneda`() {
        val a = CurrencyAmounts.of("BOB" to 1_000, "USD" to 50)
        val b = CurrencyAmounts.of("BOB" to 1_000, "EUR" to 20)
        assertEquals(CurrencyAmounts.of("BOB" to 2_000, "USD" to 50, "EUR" to 20), a + b)
        assertEquals(CurrencyAmounts.of("USD" to 50, "EUR" to -20), (a - b).nonZero())
        assertTrue((a - a).isEmpty)
    }

    @Test
    fun `cada moneda se formatea con su simbolo`() {
        assertEquals("Bs 1.234,50", Money.format(123_450, AppCurrencies.byCode("BOB")))
        assertEquals("US$ 12,00", Money.format(1_200, AppCurrencies.byCode("USD")))
    }
}
