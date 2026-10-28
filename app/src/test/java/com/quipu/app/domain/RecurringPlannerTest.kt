package com.quipu.app.domain

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringPlannerTest {
    private val today = LocalDate.of(2026, 10, 25)
    private val internet = RecurringPayment(
        id = 7,
        name = "Internet",
        amountMinor = 25_000,
        currencyCode = "BOB",
        dayOfMonth = 31,
        startDate = LocalDate.of(2026, 1, 1),
    )

    private fun paid(id: Long, period: YearMonth, payment: RecurringPayment = internet) = Movement(
        id, MovementType.EXPENSE, payment.amountMinor, payment.currencyCode, RecurringPlanner.dueDate(payment, period),
        recurringPaymentId = payment.id, recurringPeriod = period,
    )

    @Test
    fun `vence el ultimo dia en meses cortos`() {
        assertEquals(LocalDate.of(2026, 2, 28), RecurringPlanner.dueDate(internet, YearMonth.of(2026, 2)))
        assertEquals(LocalDate.of(2028, 2, 29), RecurringPlanner.dueDate(internet, YearMonth.of(2028, 2)))
        assertEquals(LocalDate.of(2026, 4, 30), RecurringPlanner.dueDate(internet, YearMonth.of(2026, 4)))
        assertEquals(LocalDate.of(2026, 5, 31), RecurringPlanner.dueDate(internet, YearMonth.of(2026, 5)))
    }

    @Test
    fun `un vencimiento por mes sin duplicados`() {
        val list = RecurringPlanner.occurrences(internet, YearMonth.of(2026, 8), YearMonth.of(2026, 12), emptyList(), today)
        assertEquals(5, list.size)
        assertEquals(list.size, list.map { it.key }.distinct().size)
        // Generar otra vez da exactamente lo mismo: no se acumulan registros.
        assertEquals(list, RecurringPlanner.occurrences(internet, YearMonth.of(2026, 8), YearMonth.of(2026, 12), emptyList(), today))
    }

    @Test
    fun `estado de cada mes`() {
        val movements = listOf(paid(1, YearMonth.of(2026, 8)))
        val status = RecurringPlanner.occurrences(internet, YearMonth.of(2026, 8), YearMonth.of(2026, 10), movements, today)
            .map { it.status }
        assertEquals(listOf(OccurrenceStatus.PAID, OccurrenceStatus.OVERDUE, OccurrenceStatus.PENDING), status)
    }

    @Test
    fun `respeta inicio y fin`() {
        val p = internet.copy(dayOfMonth = 15, startDate = LocalDate.of(2026, 3, 20), endDate = LocalDate.of(2026, 6, 15))
        val periods = RecurringPlanner.occurrences(p, YearMonth.of(2026, 1), YearMonth.of(2026, 12), emptyList(), today)
            .map { it.period }
        assertEquals(listOf(YearMonth.of(2026, 4), YearMonth.of(2026, 5), YearMonth.of(2026, 6)), periods)
    }

    @Test
    fun `confirmar dos veces produce una sola salida de dinero`() {
        val october = YearMonth.of(2026, 10)
        val first = RecurringPlanner.confirm(internet, october, today, emptyList())
        assertTrue(first is ConfirmResult.Created)
        val saved = (first as ConfirmResult.Created).movement.copy(id = 10)
        assertEquals(MovementType.EXPENSE, saved.type)
        assertEquals(25_000L, saved.amountMinor)
        assertEquals("BOB", saved.currencyCode)

        val second = RecurringPlanner.confirm(internet, october, today, listOf(saved))
        assertEquals(ConfirmResult.AlreadyPaid(saved), second)

        val income = Movement(1, MovementType.INCOME, 100_000, "BOB", today)
        assertEquals(75_000L, Ledger.available(listOf(income, saved))["BOB"])
    }

    @Test
    fun `no confirma fuera de vigencia ni inactivos`() {
        assertEquals(ConfirmResult.NotDue, RecurringPlanner.confirm(internet, YearMonth.of(2025, 12), today, emptyList()))
        assertEquals(ConfirmResult.NotDue, RecurringPlanner.confirm(internet.copy(active = false), YearMonth.of(2026, 10), today, emptyList()))
    }

    @Test
    fun `pendiente no reduce el disponible`() {
        val income = Movement(1, MovementType.INCOME, 100_000, "BOB", today)
        val october = YearMonth.of(2026, 10)
        assertEquals(CurrencyAmounts.of("BOB" to 25_000), RecurringPlanner.pendingTotal(listOf(internet), listOf(income), october, today))
        assertEquals(100_000L, Ledger.available(listOf(income))["BOB"])
        // Una vez pagado ya no queda pendiente y sí reduce el disponible.
        val movements = listOf(income, paid(2, october))
        assertTrue(RecurringPlanner.pendingTotal(listOf(internet), movements, october, today).isEmpty)
        assertEquals(75_000L, Ledger.available(movements)["BOB"])
    }

    @Test
    fun `inactivo solo muestra lo ya pagado`() {
        val off = internet.copy(active = false)
        val list = RecurringPlanner.occurrences(off, YearMonth.of(2026, 8), YearMonth.of(2026, 10), listOf(paid(1, YearMonth.of(2026, 9), off)), today)
        assertEquals(listOf(YearMonth.of(2026, 9)), list.map { it.period })
    }

    @Test
    fun `avisos de vencimientos sin pagar`() {
        val settings = RecurringReminderSettings(leadDays = 1, minuteOfDay = 9 * 60)
        val from = LocalDate.of(2026, 10, 29).atTime(8, 0)
        val to = LocalDate.of(2026, 10, 30).atTime(10, 0)
        val due = RecurringPlanner.remindersBetween(listOf(internet), emptyList(), settings, from, to)
        assertEquals(listOf(YearMonth.of(2026, 10)), due.map { it.period })

        val paidOctober = listOf(paid(1, YearMonth.of(2026, 10)))
        assertTrue(RecurringPlanner.remindersBetween(listOf(internet), paidOctober, settings, from, to).isEmpty())
        assertTrue(RecurringPlanner.remindersBetween(listOf(internet), emptyList(), settings.copy(enabled = false), from, to).isEmpty())
    }

    @Test
    fun `proximo aviso salta los meses pagados`() {
        val settings = RecurringReminderSettings(leadDays = 1, minuteOfDay = 9 * 60)
        val after = today.atTime(12, 0)
        assertEquals(LocalDate.of(2026, 10, 30).atTime(9, 0), RecurringPlanner.nextReminderAfter(listOf(internet), emptyList(), settings, after))
        val paidOctober = listOf(paid(1, YearMonth.of(2026, 10)))
        assertEquals(LocalDate.of(2026, 11, 29).atTime(9, 0), RecurringPlanner.nextReminderAfter(listOf(internet), paidOctober, settings, after))
        assertNull(RecurringPlanner.nextReminderAfter(listOf(internet.copy(active = false)), emptyList(), settings, after))
    }
}
