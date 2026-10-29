package com.quipu.app.domain

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    /** El ejemplo pedido: Claude, 20 USD, día 5 de cada mes, desde noviembre de 2026. */
    private val claude = RecurringPayment(
        id = 9,
        name = "Claude",
        amountMinor = 2_000,
        currencyCode = "USD",
        dayOfMonth = 5,
        startDate = LocalDate.of(2026, 11, 1),
        category = ExpenseCategory.SUBSCRIPTIONS,
    )

    private fun paid(id: Long, period: YearMonth, payment: RecurringPayment = internet) = Movement(
        id, MovementType.EXPENSE, payment.amountMinor, payment.currencyCode, RecurringPlanner.dueDate(payment, period),
        recurringPaymentId = payment.id, period = period,
    )

    // ---- Generación de vencimientos ----

    @Test
    fun `vence el ultimo dia en meses cortos`() {
        assertEquals(LocalDate.of(2026, 2, 28), RecurringPlanner.dueDate(internet, YearMonth.of(2026, 2)))
        assertEquals(LocalDate.of(2028, 2, 29), RecurringPlanner.dueDate(internet, YearMonth.of(2028, 2)))
        assertEquals(LocalDate.of(2026, 4, 30), RecurringPlanner.dueDate(internet, YearMonth.of(2026, 4)))
        assertEquals(LocalDate.of(2026, 5, 31), RecurringPlanner.dueDate(internet, YearMonth.of(2026, 5)))
    }

    @Test
    fun `dia 29 y 30 en febrero normal y bisiesto`() {
        val day30 = internet.copy(dayOfMonth = 30)
        assertEquals(LocalDate.of(2027, 2, 28), RecurringPlanner.dueDate(day30, YearMonth.of(2027, 2)))
        assertEquals(LocalDate.of(2028, 2, 29), RecurringPlanner.dueDate(day30, YearMonth.of(2028, 2)))
        val day29 = internet.copy(dayOfMonth = 29)
        assertEquals(LocalDate.of(2027, 2, 28), RecurringPlanner.dueDate(day29, YearMonth.of(2027, 2)))
        assertEquals(LocalDate.of(2028, 2, 29), RecurringPlanner.dueDate(day29, YearMonth.of(2028, 2)))
        // En marzo vuelve a su día: el ajuste de febrero no "arrastra" el vencimiento.
        assertEquals(LocalDate.of(2027, 3, 30), RecurringPlanner.dueDate(day30, YearMonth.of(2027, 3)))
    }

    @Test
    fun `ejemplo Claude vence el 5 de cada mes desde noviembre`() {
        val dues = RecurringPlanner.occurrences(claude, YearMonth.of(2026, 9), YearMonth.of(2027, 2), emptyList(), today)
            .map { it.dueDate }
        assertEquals(
            listOf(
                LocalDate.of(2026, 11, 5),
                LocalDate.of(2026, 12, 5),
                LocalDate.of(2027, 1, 5),
                LocalDate.of(2027, 2, 5),
            ),
            dues,
        )
    }

    @Test
    fun `inicio despues del dia de vencimiento empieza el mes siguiente`() {
        val late = claude.copy(startDate = LocalDate.of(2026, 11, 20))
        val first = RecurringPlanner.occurrences(late, YearMonth.of(2026, 11), YearMonth.of(2027, 1), emptyList(), today).first()
        assertEquals(LocalDate.of(2026, 12, 5), first.dueDate)
    }

    @Test
    fun `frecuencias trimestral semestral y anual`() {
        fun dues(f: RecurringFrequency) =
            RecurringPlanner.occurrences(claude.copy(frequency = f), YearMonth.of(2026, 11), YearMonth.of(2028, 1), emptyList(), today)
                .map { it.period }
        assertEquals(listOf("2026-11", "2027-02", "2027-05", "2027-08", "2027-11"), dues(RecurringFrequency.QUARTERLY).map { it.toString() })
        assertEquals(listOf("2026-11", "2027-05", "2027-11"), dues(RecurringFrequency.SEMIANNUAL).map { it.toString() })
        assertEquals(listOf("2026-11", "2027-11"), dues(RecurringFrequency.YEARLY).map { it.toString() })
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
    fun `respeta inicio y fin`() {
        val p = internet.copy(dayOfMonth = 15, startDate = LocalDate.of(2026, 3, 20), endDate = LocalDate.of(2026, 6, 15))
        val periods = RecurringPlanner.occurrences(p, YearMonth.of(2026, 1), YearMonth.of(2026, 12), emptyList(), today)
            .map { it.period }
        assertEquals(listOf(YearMonth.of(2026, 4), YearMonth.of(2026, 5), YearMonth.of(2026, 6)), periods)
    }

    // ---- Estados ----

    @Test
    fun `estado de cada mes`() {
        val movements = listOf(paid(1, YearMonth.of(2026, 8)))
        val status = RecurringPlanner.occurrences(internet, YearMonth.of(2026, 8), YearMonth.of(2026, 10), movements, today)
            .map { it.status }
        assertEquals(listOf(OccurrenceStatus.PAID, OccurrenceStatus.OVERDUE, OccurrenceStatus.PENDING), status)
    }

    @Test
    fun `en pausa los meses sin pagar salen pausados y se conserva lo pagado`() {
        val off = internet.copy(active = false)
        val list = RecurringPlanner.occurrences(off, YearMonth.of(2026, 8), YearMonth.of(2026, 10), listOf(paid(1, YearMonth.of(2026, 9), off)), today)
        assertEquals(listOf(OccurrenceStatus.PAUSED, OccurrenceStatus.PAID, OccurrenceStatus.PAUSED), list.map { it.status })
        assertTrue(RecurringPlanner.pendingTotal(listOf(off), emptyList(), YearMonth.of(2026, 10), today).isEmpty)
    }

    @Test
    fun `agenda con vencidos y proximo`() {
        val p = internet.copy(dayOfMonth = 10, startDate = LocalDate.of(2026, 7, 1))
        // Julio pagado; agosto, septiembre y octubre (10 oct < 25 oct) vencidos; noviembre es el próximo.
        val s = RecurringPlanner.schedule(p, listOf(paid(1, YearMonth.of(2026, 7), p)), today)
        assertEquals(listOf("2026-08", "2026-09", "2026-10"), s.overdue.map { it.period.toString() })
        assertEquals(LocalDate.of(2026, 11, 10), s.next?.dueDate)
        assertEquals(YearMonth.of(2026, 8), s.first?.period)
        assertEquals(listOf(1L), s.paid.map { it.id })

        val paused = RecurringPlanner.schedule(p.copy(active = false), emptyList(), today)
        assertTrue(paused.overdue.isEmpty())
        assertNull(paused.next)
    }

    @Test
    fun `agenda futura antes del inicio`() {
        val s = RecurringPlanner.schedule(claude, emptyList(), today)
        assertTrue(s.overdue.isEmpty())
        assertEquals(LocalDate.of(2026, 11, 5), s.next?.dueDate)
        assertEquals(OccurrenceStatus.PENDING, s.next?.status)
    }

    @Test
    fun `pagar por adelantado salta a los meses siguientes`() {
        val ahead = listOf(paid(1, YearMonth.of(2026, 11), claude), paid(2, YearMonth.of(2026, 12), claude))
        val s = RecurringPlanner.schedule(claude, ahead, today)
        assertEquals(LocalDate.of(2027, 1, 5), s.next?.dueDate)
        assertEquals(
            listOf("2027-01", "2027-02", "2027-03"),
            RecurringPlanner.upcoming(claude, ahead, today).take(3).map { it.period.toString() },
        )
        // El aviso también salta lo ya pagado (1 día antes a las 09:00).
        assertEquals(LocalDate.of(2027, 1, 4).atTime(9, 0), RecurringPlanner.nextReminderAfter(listOf(claude), ahead, today.atTime(12, 0)))
        // Un año entero pagado por adelantado.
        val year = (1..12).map { paid(it.toLong(), YearMonth.of(2026, 10).plusMonths(it.toLong()), claude) }
        assertEquals(YearMonth.of(2027, 11), RecurringPlanner.schedule(claude, year, today).next?.period)
    }

    @Test
    fun `orden de la lista de proximos pagos`() {
        val overdue = internet.copy(id = 1, name = "B", dayOfMonth = 1, startDate = LocalDate.of(2026, 10, 1))
        val soon = internet.copy(id = 2, name = "A", dayOfMonth = 28, startDate = LocalDate.of(2026, 10, 26))
        val paused = internet.copy(id = 3, name = "C", active = false)
        val order = RecurringPlanner.schedules(listOf(paused, soon, overdue), emptyList(), today).map { it.payment.id }
        assertEquals(listOf(1L, 2L, 3L), order)
    }

    // ---- Confirmación ----

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
    fun `confirmar en USD registra el gasto en USD con fecha efectiva y categoria`() {
        val effective = LocalDate.of(2026, 11, 7)
        val result = RecurringPlanner.confirm(claude, YearMonth.of(2026, 11), effective, emptyList()) as ConfirmResult.Created
        val m = result.movement
        assertEquals("USD", m.currencyCode)
        assertEquals(2_000L, m.amountMinor)
        assertEquals(effective, m.date)
        assertEquals(ExpenseCategory.SUBSCRIPTIONS, m.category)
        assertEquals(YearMonth.of(2026, 11), m.period)
        assertEquals(9L, m.recurringPaymentId)
        // Nunca se convierte: el disponible en BOB no cambia y aparece un saldo en USD.
        val income = Movement(1, MovementType.INCOME, 100_000, "BOB", today)
        val available = Ledger.available(listOf(income, m.copy(id = 2)))
        assertEquals(100_000L, available["BOB"])
        assertEquals(-2_000L, available["USD"])
    }

    @Test
    fun `importe pagado distinto al previsto`() {
        val result = RecurringPlanner.confirm(claude, YearMonth.of(2026, 11), today, emptyList(), amountMinor = 2_150)
        assertEquals(2_150L, (result as ConfirmResult.Created).movement.amountMinor)
    }

    @Test
    fun `no confirma fuera de vigencia de frecuencia ni en pausa`() {
        assertEquals(ConfirmResult.NotDue, RecurringPlanner.confirm(internet, YearMonth.of(2025, 12), today, emptyList()))
        assertEquals(ConfirmResult.NotDue, RecurringPlanner.confirm(internet.copy(active = false), YearMonth.of(2026, 10), today, emptyList()))
        val yearly = claude.copy(frequency = RecurringFrequency.YEARLY)
        assertEquals(ConfirmResult.NotDue, RecurringPlanner.confirm(yearly, YearMonth.of(2026, 12), today, emptyList()))
    }

    @Test
    fun `un gasto repetido con el mismo id cuenta una sola vez`() {
        val m = paid(5, YearMonth.of(2026, 10))
        assertEquals(ConfirmResult.AlreadyPaid(m), RecurringPlanner.confirm(internet, YearMonth.of(2026, 10), today, listOf(m, m)))
        assertEquals(-25_000L, Ledger.available(listOf(m, m))["BOB"])
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
    fun `pendiente se agrupa por moneda sin convertir`() {
        val november = YearMonth.of(2026, 11)
        val total = RecurringPlanner.pendingTotal(listOf(internet, claude), emptyList(), november, today)
        assertEquals(CurrencyAmounts.of("BOB" to 25_000, "USD" to 2_000), total)
    }

    // ---- Gasto manual y recurrente a la vez ----

    @Test
    fun `detecta un gasto anotado a mano para no contarlo dos veces`() {
        val occurrence = RecurringPlanner.schedule(claude, emptyList(), today).next!!
        val manual = Movement(20, MovementType.EXPENSE, 2_000, "USD", LocalDate.of(2026, 11, 4), "", ExpenseCategory.SUBSCRIPTIONS)
        val byName = Movement(21, MovementType.EXPENSE, 1_990, "USD", LocalDate.of(2026, 11, 12), "Pago claude pro")
        val otherCurrency = Movement(22, MovementType.EXPENSE, 2_000, "BOB", LocalDate.of(2026, 11, 5))
        val tooFar = Movement(23, MovementType.EXPENSE, 2_000, "USD", LocalDate.of(2026, 12, 1))
        val alreadyLinked = paid(24, YearMonth.of(2026, 10), claude)
        val pending = manual.copy(id = 25, pending = true)
        val matches = RecurringPlanner.manualMatches(occurrence, listOf(manual, byName, otherCurrency, tooFar, alreadyLinked, pending))
        assertEquals(listOf(20L, 21L), matches.map { it.id })
    }

    // ---- Recordatorios ----

    @Test
    fun `avisos de vencimientos sin pagar con el aviso de cada pago`() {
        val from = LocalDate.of(2026, 10, 29).atTime(8, 0)
        val to = LocalDate.of(2026, 10, 30).atTime(10, 0)
        val p = internet.copy(reminder = RecurringReminder(leadDays = 1, minuteOfDay = 9 * 60))
        val due = RecurringPlanner.remindersBetween(listOf(p), emptyList(), from, to)
        assertEquals(listOf(YearMonth.of(2026, 10)), due.map { it.period })

        val paidOctober = listOf(paid(1, YearMonth.of(2026, 10), p))
        assertTrue(RecurringPlanner.remindersBetween(listOf(p), paidOctober, from, to).isEmpty())
        val off = p.copy(reminder = p.reminder.copy(enabled = false))
        assertTrue(RecurringPlanner.remindersBetween(listOf(off), emptyList(), from, to).isEmpty())
        assertTrue(RecurringPlanner.remindersBetween(listOf(p.copy(active = false)), emptyList(), from, to).isEmpty())
    }

    @Test
    fun `proximo aviso salta los meses pagados`() {
        val after = today.atTime(12, 0)
        assertEquals(LocalDate.of(2026, 10, 30).atTime(9, 0), RecurringPlanner.nextReminderAfter(listOf(internet), emptyList(), after))
        val paidOctober = listOf(paid(1, YearMonth.of(2026, 10)))
        assertEquals(LocalDate.of(2026, 11, 29).atTime(9, 0), RecurringPlanner.nextReminderAfter(listOf(internet), paidOctober, after))
        assertNull(RecurringPlanner.nextReminderAfter(listOf(internet.copy(active = false)), emptyList(), after))
    }

    @Test
    fun `aviso del ejemplo Claude dos dias antes a las 20`() {
        val p = claude.copy(reminder = RecurringReminder(leadDays = 2, minuteOfDay = 20 * 60))
        assertEquals(LocalDate.of(2026, 11, 3).atTime(20, 0), RecurringPlanner.nextReminderAfter(listOf(p), emptyList(), today.atTime(12, 0)))
        // Un pago anual también encuentra su próximo aviso aunque falte casi un año.
        val yearly = p.copy(frequency = RecurringFrequency.YEARLY)
        val paidNov = listOf(paid(1, YearMonth.of(2026, 11), yearly))
        assertEquals(LocalDate.of(2027, 11, 3).atTime(20, 0), RecurringPlanner.nextReminderAfter(listOf(yearly), paidNov, today.atTime(12, 0)))
    }

    @Test
    fun `el interruptor general de recordatorios apaga los de pagos`() {
        val settings = ReminderSettings()
        val after = today.atTime(12, 0)
        assertEquals(
            LocalDate.of(2026, 10, 30).atTime(9, 0),
            ReminderPlanner.nextAfter(emptyList(), emptyList(), settings, after, listOf(internet), emptyList()),
        )
        assertNull(ReminderPlanner.nextAfter(emptyList(), emptyList(), settings.copy(enabled = false), after, listOf(internet), emptyList()))
        val hits = ReminderPlanner.dueBetween(
            emptyList(), emptyList(), settings,
            LocalDate.of(2026, 10, 30).atTime(8, 0), LocalDate.of(2026, 10, 30).atTime(9, 0),
            listOf(internet), emptyList(),
        )
        assertEquals(listOf("recurring-7-2026-10"), hits.map { it.key })
        assertEquals(ReminderKind.RECURRING, hits.single().kind)
        assertEquals(LocalDate.of(2026, 10, 31), hits.single().dueDate)
    }

    @Test
    fun `es suscripcion segun su categoria`() {
        assertTrue(claude.isSubscription)
        assertFalse(internet.isSubscription)
    }
}
