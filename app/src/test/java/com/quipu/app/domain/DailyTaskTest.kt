package com.quipu.app.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyTaskTest {
    private val today = LocalDate.of(2026, 10, 17)
    private val now = today.atTime(12, 0)

    private fun daily(id: Long, lastCompleted: LocalDate?, dueAt: LocalDateTime = now.minusDays(30)) =
        TaskItem(id, "d$id", "", dueAt, completed = false, isDaily = true, lastCompletedDate = lastCompleted)

    private fun normal(id: Long, dueAt: LocalDateTime, completed: Boolean = false) =
        TaskItem(id, "t$id", "", dueAt, completed)

    // ---- Cambio de día ----

    @Test
    fun completedToday_countsAsCompleted() {
        assertTrue(TaskLogic.resolveForDay(daily(1, today), today).completed)
    }

    @Test
    fun completedYesterday_isPendingAgainToday() {
        val task = daily(1, lastCompleted = today.minusDays(1))
        assertTrue(TaskLogic.resolveForDay(task, today.minusDays(1)).completed)
        assertFalse(TaskLogic.resolveForDay(task, today).completed)
    }

    @Test
    fun appClosedForSeveralDays_dailyIsPendingOnReopen() {
        // Completada hace 5 días y la app no se abrió desde entonces.
        val task = daily(1, lastCompleted = today.minusDays(5))
        assertFalse(TaskLogic.resolveForDay(task, today).completed)
    }

    @Test
    fun neverCompletedDaily_isPending() {
        assertFalse(TaskLogic.resolveForDay(daily(1, lastCompleted = null), today).completed)
    }

    @Test
    fun resolveForDay_keepsNormalTasksExactlyAsStored() {
        val tasks = listOf(
            normal(1, now.minusDays(2), completed = true),
            normal(2, now.plusDays(1), completed = false),
        )
        assertEquals(tasks, TaskLogic.resolveForDay(tasks, today))
        assertEquals(tasks, TaskLogic.resolveForDay(tasks, today.plusDays(10)))
    }

    // ---- Vencimientos ----

    @Test
    fun pendingDaily_isNeverOverdueOrSoon() {
        // Su dueAt guardado es antiguo, pero una diaria no tiene hora límite.
        val task = TaskLogic.resolveForDay(daily(1, today.minusDays(1), dueAt = now.minusDays(100)), today)
        assertEquals(TaskTiming.LATER, TaskLogic.timing(task, now))
        val late = today.atTime(23, 59)
        assertEquals(TaskTiming.LATER, TaskLogic.timing(task, late))
    }

    @Test
    fun completedDaily_isDone() {
        val task = TaskLogic.resolveForDay(daily(1, today), today)
        assertEquals(TaskTiming.DONE, TaskLogic.timing(task, now))
    }

    @Test
    fun normalTasks_keepOverdueAndSoon() {
        assertEquals(TaskTiming.OVERDUE, TaskLogic.timing(normal(1, now.minusMinutes(1)), now))
        assertEquals(TaskTiming.SOON, TaskLogic.timing(normal(2, now.plusHours(2)), now))
    }

    // ---- Filtros y orden ----

    @Test
    fun filters_useTodaysStateOfDailyTasks() {
        val stored = listOf(
            daily(1, lastCompleted = today.minusDays(1)), // ayer: hoy pendiente
            daily(2, lastCompleted = today), // hoy: completada
            normal(3, now.plusHours(1)),
        )
        val resolved = TaskLogic.resolveForDay(stored, today)
        assertEquals(listOf(1L, 3L), TaskLogic.filterAndSort(resolved, TaskFilter.PENDING, true).map { it.id })
        assertEquals(listOf(2L), TaskLogic.filterAndSort(resolved, TaskFilter.COMPLETED, true).map { it.id })
    }

    @Test
    fun sort_putsDailyFirstAndKeepsNormalOrderInBothDirections() {
        val tasks = listOf(
            normal(1, now.plusDays(2)),
            daily(5, null),
            normal(2, now.plusHours(1)),
            daily(4, null),
            normal(3, now.plusDays(1)),
        )
        assertEquals(listOf(4L, 5L, 2L, 3L, 1L), TaskLogic.sort(tasks, ascending = true).map { it.id })
        assertEquals(listOf(4L, 5L, 1L, 3L, 2L), TaskLogic.sort(tasks, ascending = false).map { it.id })
    }

    // ---- Validación ----

    @Test
    fun dailyTask_doesNotRequireDateOrTime() {
        val errors = Validators.validateTask("Leer", "", null, null, isDaily = true)
        assertFalse(errors.hasErrors)
        assertNull(errors.date)
        assertNull(errors.time)
    }

    @Test
    fun dailyTask_stillRequiresTitle() {
        assertEquals("Escribe un título", Validators.validateTask("  ", "", null, null, isDaily = true).title)
    }

    @Test
    fun normalTask_stillRequiresDateAndTime() {
        val errors = Validators.validateTask("Pagar", "", null, null)
        assertEquals("Elige una fecha", errors.date)
        assertEquals("Elige una hora", errors.time)
        assertFalse(Validators.validateTask("Pagar", "", today, LocalTime.NOON).hasErrors)
    }
}
