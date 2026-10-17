package com.quipu.app.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinanceCalculatorTest {
    private val day = LocalDate.of(2026, 10, 1)
    private var nextId = 1L

    private fun debt(
        type: DebtType,
        person: String,
        minor: Long,
        status: DebtStatus = DebtStatus.PENDING,
        date: LocalDate = day,
    ) = DebtItem(
        id = nextId++,
        type = type,
        personName = PersonKey.displayName(person),
        personKey = PersonKey.keyOf(person),
        amountMinor = minor,
        recordDate = date,
        dueDate = null,
        note = "",
        status = status,
        settledDate = if (status == DebtStatus.SETTLED) day else null,
    )

    @Test
    fun emptyList_givesZeros() {
        val summary = FinanceCalculator.summarize(emptyList())
        assertEquals(0L, summary.totalOwedToMe)
        assertEquals(0L, summary.totalIOwe)
        assertEquals(0L, summary.net)
        assertEquals(0, summary.peopleCount)
        assertEquals(0, summary.pendingCount)
        assertTrue(summary.perPerson.isEmpty())
    }

    @Test
    fun totalsAndNet_useOnlyPendingRecords() {
        val items = listOf(
            debt(DebtType.OWED_TO_ME, "Ana", 10_000),
            debt(DebtType.OWED_TO_ME, "Luis", 5_050),
            debt(DebtType.I_OWE, "Marta", 3_000),
            debt(DebtType.OWED_TO_ME, "Ana", 99_999, status = DebtStatus.SETTLED),
            debt(DebtType.I_OWE, "Pedro", 88_888, status = DebtStatus.SETTLED),
        )
        val summary = FinanceCalculator.summarize(items)
        assertEquals(15_050L, summary.totalOwedToMe)
        assertEquals(3_000L, summary.totalIOwe)
        assertEquals(12_050L, summary.net)
        assertEquals(3, summary.pendingCount)
        assertEquals(3, summary.peopleCount)
    }

    @Test
    fun netCanBeNegative() {
        val items = listOf(
            debt(DebtType.OWED_TO_ME, "Ana", 1_000),
            debt(DebtType.I_OWE, "Luis", 4_000),
        )
        assertEquals(-3_000L, FinanceCalculator.summarize(items).net)
    }

    @Test
    fun peopleWithDifferentCaseSpacesOrAccents_areMergedIntoOne() {
        val items = listOf(
            debt(DebtType.OWED_TO_ME, "José Pérez", 1_000),
            debt(DebtType.OWED_TO_ME, "  jose   perez ", 2_000),
            debt(DebtType.I_OWE, "JOSE PEREZ", 500),
        )
        val summary = FinanceCalculator.summarize(items)
        assertEquals(1, summary.peopleCount)
        val person = summary.perPerson.single()
        assertEquals(3_000L, person.owedToMe)
        assertEquals(500L, person.iOwe)
        assertEquals(2_500L, person.net)
        assertEquals(3, person.pendingCount)
    }

    @Test
    fun similarNamesWithDetail_stayAsDifferentPeople() {
        val items = listOf(
            debt(DebtType.OWED_TO_ME, "Juan", 1_000),
            debt(DebtType.OWED_TO_ME, "Juan (primo)", 2_000),
        )
        assertEquals(2, FinanceCalculator.summarize(items).peopleCount)
    }

    @Test
    fun personWithOnlySettledRecords_isNotCountedAsInvolved() {
        val items = listOf(
            debt(DebtType.OWED_TO_ME, "Ana", 1_000, status = DebtStatus.SETTLED),
            debt(DebtType.I_OWE, "Luis", 2_000),
        )
        val summary = FinanceCalculator.summarize(items)
        assertEquals(1, summary.peopleCount)
        assertEquals("Luis", summary.perPerson.single().displayName)
    }

    @Test
    fun displayName_comesFromTheMostRecentRecord() {
        val items = listOf(
            debt(DebtType.OWED_TO_ME, "ana", 1_000, date = LocalDate.of(2026, 1, 1)),
            debt(DebtType.OWED_TO_ME, "Ana García", 1_000, date = LocalDate.of(2026, 5, 1)),
        )
        // "ana" y "ana garcia" son claves distintas: dos personas.
        assertEquals(2, FinanceCalculator.summarize(items).peopleCount)

        val sameKey = listOf(
            debt(DebtType.OWED_TO_ME, "ana garcia", 1_000, date = LocalDate.of(2026, 1, 1)),
            debt(DebtType.OWED_TO_ME, "Ana García", 1_000, date = LocalDate.of(2026, 5, 1)),
        )
        assertEquals("Ana García", FinanceCalculator.summarize(sameKey).perPerson.single().displayName)
    }

    @Test
    fun perPerson_isSortedByLargestAbsoluteNet() {
        val items = listOf(
            debt(DebtType.OWED_TO_ME, "Ana", 1_000),
            debt(DebtType.I_OWE, "Luis", 9_000),
            debt(DebtType.OWED_TO_ME, "Marta", 5_000),
        )
        val names = FinanceCalculator.summarize(items).perPerson.map { it.displayName }
        assertEquals(listOf("Luis", "Marta", "Ana"), names)
    }

    @Test
    fun largeAmounts_doNotOverflowOrLosePrecision() {
        val items = List(1000) { debt(DebtType.OWED_TO_ME, "Ana", Money.MAX_MINOR) }
        assertEquals(Money.MAX_MINOR * 1000, FinanceCalculator.summarize(items).totalOwedToMe)
    }
}
