package com.quipu.app.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentTest {
    private val today = LocalDate.of(2026, 10, 25)
    private val bob = AppCurrencies.default

    private fun debt(
        id: Long,
        type: DebtType,
        person: String,
        minor: Long,
        paid: List<Long> = emptyList(),
        status: DebtStatus = DebtStatus.PENDING,
    ) = DebtItem(
        id, type, PersonKey.displayName(person), PersonKey.keyOf(person), minor,
        today.minusDays(10), null, "", status, null,
        paid.mapIndexed { i, amount -> DebtPayment(id * 100 + i, id, amount, today.minusDays(5)) },
    )

    @Test
    fun loanOf300_with200Paid_leaves100() {
        val item = debt(1, DebtType.OWED_TO_ME, "Ana", 30_000, paid = listOf(20_000))
        assertEquals(20_000, item.paidMinor)
        assertEquals(10_000, item.remainingMinor)
    }

    @Test
    fun severalPayments_addUp() {
        val item = debt(1, DebtType.I_OWE, "Luis", 30_000, paid = listOf(5_000, 7_550, 2_450))
        assertEquals(15_000, item.paidMinor)
        assertEquals(15_000, item.remainingMinor)
    }

    @Test
    fun settledRecord_hasNothingRemaining_evenWithPartialPayments() {
        val item = debt(1, DebtType.OWED_TO_ME, "Ana", 30_000, paid = listOf(10_000), status = DebtStatus.SETTLED)
        assertEquals(0, item.remainingMinor)
    }

    @Test
    fun remaining_neverGoesBelowZero() {
        val item = debt(1, DebtType.OWED_TO_ME, "Ana", 10_000, paid = listOf(10_000, 500))
        assertEquals(0, item.remainingMinor)
    }

    @Test
    fun summaryAndPersonBalance_countOnlyWhatIsStillOwed() {
        val items = listOf(
            debt(1, DebtType.OWED_TO_ME, "Ana", 30_000, paid = listOf(20_000)), // falta 100
            debt(2, DebtType.OWED_TO_ME, "ana", 5_000), // falta 50
            debt(3, DebtType.I_OWE, "Ana", 8_000, paid = listOf(3_000)), // falta 50
            debt(4, DebtType.I_OWE, "Luis", 4_000, paid = listOf(1_000), status = DebtStatus.SETTLED),
        )
        val summary = FinanceCalculator.summarize(items)
        assertEquals(15_000, summary.totalOwedToMe)
        assertEquals(5_000, summary.totalIOwe)
        assertEquals(10_000, summary.net)
        assertEquals(3, summary.pendingCount)

        val ana = summary.perPerson.single()
        assertEquals(15_000, ana.owedToMe)
        assertEquals(5_000, ana.iOwe)

        val group = DebtLogic.groupByPerson(items, "", DebtTypeFilter.ALL, DebtStatusFilter.ALL, true, today)
            .single { it.personKey == "ana" }
        assertEquals(ana.owedToMe, group.balance.owedToMe)
        assertEquals(ana.iOwe, group.balance.iOwe)
    }

    // ---- Validación del abono ----

    private fun validate(text: String, remaining: Long = 10_000, date: LocalDate? = today) =
        Validators.validatePayment(text, remaining, date, today.minusDays(10), bob)

    @Test
    fun validPayment_upToTheRemaining() {
        assertFalse(validate("50").hasErrors)
        assertFalse(validate("100").hasErrors)
        assertFalse(validate("99,99").hasErrors)
    }

    @Test
    fun payment_cannotExceedWhatIsLeft() {
        assertEquals("Supera lo que falta (Bs 100,00)", validate("100,01").amount)
    }

    @Test
    fun payment_mustBePositiveAndNumeric() {
        assertEquals("Escribe el importe del abono", validate(" ").amount)
        assertEquals("El abono debe ser mayor que cero", validate("0").amount)
        assertTrue(validate("abc").amount!!.startsWith("Importe no válido"))
    }

    @Test
    fun payment_dateRules() {
        assertEquals("Elige la fecha del abono", validate("10", date = null).date)
        assertEquals("No puede ser anterior al registro", validate("10", date = today.minusDays(11)).date)
        assertNull(validate("10", date = today.minusDays(10)).date)
    }

    @Test
    fun editingAmount_cannotGoBelowWhatWasPaid() {
        val errors = Validators.validateDebt("Ana", "150", today, null, "", paidMinor = 20_000)
        assertEquals("No puede ser menor que lo ya abonado", errors.amount)
        assertNull(Validators.validateDebt("Ana", "200", today, null, "", paidMinor = 20_000).amount)
    }
}
