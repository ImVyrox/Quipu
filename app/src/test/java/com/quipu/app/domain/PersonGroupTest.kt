package com.quipu.app.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonGroupTest {
    private val today = LocalDate.of(2026, 10, 17)

    private fun debt(
        id: Long,
        type: DebtType,
        person: String,
        minor: Long,
        status: DebtStatus = DebtStatus.PENDING,
        daysAgo: Long = id,
        dueDate: LocalDate? = null,
    ) = DebtItem(
        id, type, PersonKey.displayName(person), PersonKey.keyOf(person), minor,
        today.minusDays(daysAgo), dueDate, "", status, null,
    )

    private val items = listOf(
        debt(1, DebtType.OWED_TO_ME, "Ana Rojas", 15_000, daysAgo = 10),
        debt(2, DebtType.OWED_TO_ME, "  ana   rojas ", 5_000, daysAgo = 5),
        debt(3, DebtType.OWED_TO_ME, "Ána Rojas", 2_000, DebtStatus.SETTLED, daysAgo = 30),
        debt(4, DebtType.I_OWE, "José Pérez", 20_000, daysAgo = 3),
        debt(5, DebtType.OWED_TO_ME, "jose perez", 3_000, daysAgo = 2),
        debt(6, DebtType.I_OWE, "Luis", 8_000, DebtStatus.SETTLED, daysAgo = 20),
    )

    private fun groups(
        query: String = "",
        type: DebtTypeFilter = DebtTypeFilter.ALL,
        status: DebtStatusFilter = DebtStatusFilter.ALL,
        newestFirst: Boolean = true,
    ) = DebtLogic.groupByPerson(items, query, type, status, newestFirst, today)

    @Test
    fun sameNameWithCaseSpacesOrAccents_isOneGroup_andDifferentPeopleStaySeparate() {
        val result = groups()
        assertEquals(3, result.size)
        val ana = result.single { it.personKey == "ana rojas" }
        assertEquals(3, ana.recordCount)
        assertEquals(setOf("ana rojas", "jose perez", "luis"), result.map { it.personKey }.toSet())
    }

    @Test
    fun groupBalance_onlyCountsPendingRecords() {
        val ana = groups().single { it.personKey == "ana rojas" }
        assertEquals(20_000, ana.balance.owedToMe) // 150 + 50; el cobrado de 20 no cuenta
        assertEquals(0, ana.balance.iOwe)
        assertEquals(2, ana.balance.pendingCount)
    }

    @Test
    fun personWithBothTypes_keepsThemSeparateInBalanceAndRecords() {
        val jose = groups().single { it.personKey == "jose perez" }
        assertEquals(3_000, jose.balance.owedToMe)
        assertEquals(20_000, jose.balance.iOwe)
        assertEquals(-17_000, jose.balance.net)

        val records = DebtLogic.recordsOf(items, "jose perez", newestFirst = true)
        assertEquals(listOf(5L), records.owedToMePending.map { it.id })
        assertEquals(listOf(4L), records.iOwePending.map { it.id })
        assertTrue(records.owedToMeSettled.isEmpty())
        assertTrue(records.iOweSettled.isEmpty())
    }

    @Test
    fun recordsOf_separatesByStatus_andExcludesOtherPeople() {
        val records = DebtLogic.recordsOf(items, "ana rojas", newestFirst = true)
        assertEquals(listOf(2L, 1L), records.owedToMePending.map { it.id })
        assertEquals(listOf(3L), records.owedToMeSettled.map { it.id })
        assertTrue(records.iOwePending.isEmpty())
        assertEquals(listOf(1L, 2L), DebtLogic.recordsOf(items, "ana rojas", newestFirst = false).owedToMePending.map { it.id })
        assertTrue(DebtLogic.recordsOf(items, "nadie", newestFirst = true).isEmpty)
    }

    @Test
    fun groupBalances_addUpToTheGeneralSummary() {
        val summary = FinanceCalculator.summarize(items)
        val result = groups()
        assertEquals(summary.totalOwedToMe, result.sumOf { it.balance.owedToMe })
        assertEquals(summary.totalIOwe, result.sumOf { it.balance.iOwe })
        assertEquals(summary.pendingCount, result.sumOf { it.balance.pendingCount })
        assertEquals(23_000, summary.totalOwedToMe)
        assertEquals(20_000, summary.totalIOwe)
        assertEquals(3_000, summary.net)
        // Cada persona con saldo coincide con su fila en el Resumen.
        summary.perPerson.forEach { p ->
            val g = result.single { it.personKey == p.personKey }
            assertEquals(p.owedToMe, g.balance.owedToMe)
            assertEquals(p.iOwe, g.balance.iOwe)
        }
    }

    @Test
    fun personWithOnlySettledRecords_appearsWithZeroBalance() {
        val luis = groups().single { it.personKey == "luis" }
        assertEquals(1, luis.recordCount)
        assertEquals(0, luis.balance.net)
        assertEquals(0, luis.balance.pendingCount)
    }

    @Test
    fun typeFilter_choosesPeople_butBalanceStaysComplete() {
        val iOwe = groups(type = DebtTypeFilter.I_OWE)
        assertEquals(listOf("jose perez", "luis"), iOwe.map { it.personKey })
        // José aparece por su deuda, pero su saldo incluye también lo que le prestaste.
        assertEquals(3_000, iOwe.first().balance.owedToMe)
    }

    @Test
    fun statusAndQueryFilters_useNormalizedNames() {
        assertEquals(listOf("ana rojas", "luis"), groups(status = DebtStatusFilter.SETTLED).map { it.personKey }.sorted())
        assertEquals(listOf("jose perez"), groups(query = "  JOSÉ ").map { it.personKey })
    }

    @Test
    fun groups_followTheDateOrder() {
        assertEquals(listOf("jose perez", "ana rojas", "luis"), groups(newestFirst = true).map { it.personKey })
        assertEquals(listOf("ana rojas", "luis", "jose perez"), groups(newestFirst = false).map { it.personKey })
    }

    @Test
    fun displayName_comesFromTheMostRecentRecord() {
        assertEquals("ana rojas", groups().single { it.personKey == "ana rojas" }.displayName)
    }

    @Test
    fun hasOverdue_onlyForPendingPastDueDate() {
        val list = listOf(
            debt(1, DebtType.OWED_TO_ME, "Ana", 100, dueDate = today.minusDays(1)),
            debt(2, DebtType.OWED_TO_ME, "Luis", 100, DebtStatus.SETTLED, dueDate = today.minusDays(1)),
        )
        val result = DebtLogic.groupByPerson(list, "", DebtTypeFilter.ALL, DebtStatusFilter.ALL, true, today)
        assertTrue(result.single { it.personKey == "ana" }.hasOverdue)
        assertFalse(result.single { it.personKey == "luis" }.hasOverdue)
    }
}
