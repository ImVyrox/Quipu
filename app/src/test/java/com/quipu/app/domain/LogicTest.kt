package com.quipu.app.domain

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {
    private val now = LocalDateTime.of(2026, 10, 16, 12, 0)
    private val today = LocalDate.of(2026, 10, 16)

    private fun task(id: Long, dueAt: LocalDateTime, completed: Boolean = false) =
        TaskItem(id, "t$id", "", dueAt, completed)

    private fun debt(
        id: Long,
        type: DebtType,
        person: String,
        status: DebtStatus,
        recordDate: LocalDate,
        dueDate: LocalDate? = null,
    ) = DebtItem(
        id, type, PersonKey.displayName(person), PersonKey.keyOf(person), 1_000,
        recordDate, dueDate, "", status, null,
    )

    // ---- Tareas ----

    @Test
    fun timing_classifiesTasks() {
        assertEquals(TaskTiming.OVERDUE, TaskLogic.timing(task(1, now.minusMinutes(1)), now))
        assertEquals(TaskTiming.SOON, TaskLogic.timing(task(2, now.plusHours(3)), now))
        assertEquals(TaskTiming.SOON, TaskLogic.timing(task(3, now.plusHours(24)), now))
        assertEquals(TaskTiming.LATER, TaskLogic.timing(task(4, now.plusHours(24).plusMinutes(1)), now))
        assertEquals(TaskTiming.DONE, TaskLogic.timing(task(5, now.minusDays(3), completed = true), now))
    }

    @Test
    fun filter_splitsPendingAndCompleted() {
        val tasks = listOf(task(1, now), task(2, now, completed = true), task(3, now))
        assertEquals(3, TaskLogic.filter(tasks, TaskFilter.ALL).size)
        assertEquals(listOf(1L, 3L), TaskLogic.filter(tasks, TaskFilter.PENDING).map { it.id })
        assertEquals(listOf(2L), TaskLogic.filter(tasks, TaskFilter.COMPLETED).map { it.id })
    }

    @Test
    fun sort_ordersByDateTimeBothDirections() {
        val tasks = listOf(
            task(1, now.plusDays(2)),
            task(2, now.plusHours(1)),
            task(3, now.plusDays(1)),
        )
        assertEquals(listOf(2L, 3L, 1L), TaskLogic.sort(tasks, ascending = true).map { it.id })
        assertEquals(listOf(1L, 3L, 2L), TaskLogic.sort(tasks, ascending = false).map { it.id })
    }

    // ---- Deudas ----

    private val debts = listOf(
        debt(1, DebtType.OWED_TO_ME, "Ana", DebtStatus.PENDING, LocalDate.of(2026, 10, 1)),
        debt(2, DebtType.I_OWE, "José Pérez", DebtStatus.PENDING, LocalDate.of(2026, 10, 5)),
        debt(3, DebtType.OWED_TO_ME, "Ana", DebtStatus.SETTLED, LocalDate.of(2026, 9, 1)),
        debt(4, DebtType.I_OWE, "Luis", DebtStatus.SETTLED, LocalDate.of(2026, 10, 10)),
    )

    private fun query(
        q: String = "",
        type: DebtTypeFilter = DebtTypeFilter.ALL,
        status: DebtStatusFilter = DebtStatusFilter.ALL,
        newest: Boolean = true,
    ) = DebtLogic.filterAndSort(debts, q, type, status, newest).map { it.id }

    @Test
    fun debts_sortByRecordDate() {
        assertEquals(listOf(4L, 2L, 1L, 3L), query(newest = true))
        assertEquals(listOf(3L, 1L, 2L, 4L), query(newest = false))
    }

    @Test
    fun debts_searchByNameIgnoresCaseAccentsAndSpaces() {
        assertEquals(listOf(3L, 1L), query(q = "ana", newest = false))
        assertEquals(listOf(2L), query(q = "  JOSE "))
        assertEquals(listOf(2L), query(q = "pérez"))
        assertTrue(query(q = "nadie").isEmpty())
    }

    @Test
    fun debts_filterByTypeAndStatus() {
        assertEquals(listOf(3L, 1L), query(type = DebtTypeFilter.OWED_TO_ME, newest = false))
        assertEquals(listOf(4L, 2L), query(type = DebtTypeFilter.I_OWE))
        assertEquals(listOf(2L, 1L), query(status = DebtStatusFilter.PENDING))
        assertEquals(listOf(4L, 3L), query(status = DebtStatusFilter.SETTLED))
        assertEquals(
            listOf(2L),
            query(type = DebtTypeFilter.I_OWE, status = DebtStatusFilter.PENDING),
        )
    }

    @Test
    fun overdue_onlyForPendingWithPastDueDate() {
        val pastDue = debt(1, DebtType.I_OWE, "A", DebtStatus.PENDING, today.minusDays(10), today.minusDays(1))
        val dueToday = debt(2, DebtType.I_OWE, "A", DebtStatus.PENDING, today.minusDays(10), today)
        val settled = debt(3, DebtType.I_OWE, "A", DebtStatus.SETTLED, today.minusDays(10), today.minusDays(1))
        val noDue = debt(4, DebtType.I_OWE, "A", DebtStatus.PENDING, today.minusDays(10), null)
        assertTrue(DebtLogic.isOverdue(pastDue, today))
        assertFalse(DebtLogic.isOverdue(dueToday, today))
        assertFalse(DebtLogic.isOverdue(settled, today))
        assertFalse(DebtLogic.isOverdue(noDue, today))
    }
}
