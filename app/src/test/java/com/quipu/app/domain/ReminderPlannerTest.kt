package com.quipu.app.domain

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderPlannerTest {
    private val today = LocalDate.of(2026, 10, 25)
    private val now = today.atTime(12, 0)
    private val settings = ReminderSettings(
        taskLeadMinutes = 30,
        dailyMinuteOfDay = 20 * 60,
        debtLeadDays = 1,
        debtMinuteOfDay = 9 * 60,
    )

    private fun task(id: Long, dueAt: LocalDateTime, completed: Boolean = false) =
        TaskItem(id, "t$id", "", dueAt, completed)

    private fun daily(id: Long, lastCompleted: LocalDate? = null) =
        TaskItem(id, "d$id", "", now.minusDays(30), false, isDaily = true, lastCompletedDate = lastCompleted)

    private fun debt(
        id: Long,
        due: LocalDate?,
        status: DebtStatus = DebtStatus.PENDING,
        amount: Long = 10_000,
        paid: Long = 0,
    ) = DebtItem(
        id, DebtType.OWED_TO_ME, "Ana", "ana", amount, today.minusDays(20), due, "", status, null,
        if (paid > 0) listOf(DebtPayment(1, id, paid, today.minusDays(2))) else emptyList(),
    )

    private fun due(
        tasks: List<TaskItem> = emptyList(),
        debts: List<DebtItem> = emptyList(),
        from: LocalDateTime,
        to: LocalDateTime,
        s: ReminderSettings = settings,
    ) = ReminderPlanner.dueBetween(tasks, debts, s, from, to)

    // ---- Tareas normales ----

    @Test
    fun task_isRemindedLeadMinutesBeforeDue() {
        val t = task(1, today.atTime(15, 0))
        assertEquals(today.atTime(14, 30), ReminderPlanner.nextAfter(listOf(t), emptyList(), settings, now))
        val hits = due(listOf(t), from = today.atTime(14, 29), to = today.atTime(14, 30))
        assertEquals(listOf("task-1"), hits.map { it.key })
    }

    @Test
    fun window_excludesStartAndIncludesEnd() {
        val t = task(1, today.atTime(15, 0)) // aviso 14:30
        assertTrue(due(listOf(t), from = today.atTime(14, 30), to = today.atTime(15, 0)).isEmpty())
        assertEquals(1, due(listOf(t), from = today.atTime(14, 0), to = today.atTime(14, 30)).size)
    }

    @Test
    fun completedTask_isNotReminded() {
        val t = task(1, today.atTime(15, 0), completed = true)
        assertNull(ReminderPlanner.nextAfter(listOf(t), emptyList(), settings, now))
        assertTrue(due(listOf(t), from = now, to = today.atTime(23, 0)).isEmpty())
    }

    @Test
    fun leadOfZero_remindsAtDueTime() {
        val t = task(1, today.atTime(15, 0))
        assertEquals(
            today.atTime(15, 0),
            ReminderPlanner.nextAfter(listOf(t), emptyList(), settings.copy(taskLeadMinutes = 0), now),
        )
    }

    // ---- Tareas diarias ----

    @Test
    fun daily_pendingToday_isRemindedTonight() {
        assertEquals(today.atTime(20, 0), ReminderPlanner.nextAfter(listOf(daily(1)), emptyList(), settings, now))
        val hits = due(listOf(daily(1, lastCompleted = today.minusDays(1))), from = now, to = today.atTime(20, 0))
        assertEquals(listOf("daily-1"), hits.map { it.key })
    }

    @Test
    fun daily_doneToday_skipsToTomorrow() {
        val d = daily(1, lastCompleted = today)
        assertEquals(today.plusDays(1).atTime(20, 0), ReminderPlanner.nextAfter(listOf(d), emptyList(), settings, now))
        assertTrue(due(listOf(d), from = now, to = today.atTime(21, 0)).isEmpty())
    }

    @Test
    fun daily_afterTonightsReminder_nextIsTomorrow() {
        val late = today.atTime(20, 0)
        assertEquals(today.plusDays(1).atTime(20, 0), ReminderPlanner.nextAfter(listOf(daily(1)), emptyList(), settings, late))
    }

    @Test
    fun daily_afterSeveralDaysOff_remindsOnlyOnce() {
        // Teléfono apagado tres días: un solo aviso, el del día más reciente.
        val hits = due(listOf(daily(1)), from = today.minusDays(3).atTime(12, 0), to = today.atTime(21, 0))
        assertEquals(1, hits.size)
        assertEquals(today.atTime(20, 0), hits.single().at)
    }

    // ---- Préstamos y deudas ----

    @Test
    fun debt_isRemindedLeadDaysBeforeAtConfiguredTime() {
        val d = debt(1, due = today.plusDays(2))
        assertEquals(today.plusDays(1).atTime(9, 0), ReminderPlanner.nextAfter(emptyList(), listOf(d), settings, now))
        assertEquals(
            listOf("debt-1"),
            due(debts = listOf(d), from = today.plusDays(1).atTime(8, 0), to = today.plusDays(1).atTime(9, 0)).map { it.key },
        )
    }

    @Test
    fun debt_settledOrWithoutDueDate_isNotReminded() {
        val debts = listOf(
            debt(1, due = today.plusDays(2), status = DebtStatus.SETTLED),
            debt(2, due = null),
            debt(3, due = today.plusDays(2), amount = 10_000, paid = 10_000), // abonos cubren todo
        )
        assertNull(ReminderPlanner.nextAfter(emptyList(), debts, settings, now))
    }

    @Test
    fun debt_withPartialPayment_isStillReminded() {
        val d = debt(1, due = today.plusDays(2), amount = 30_000, paid = 20_000)
        assertEquals(today.plusDays(1).atTime(9, 0), ReminderPlanner.nextAfter(emptyList(), listOf(d), settings, now))
    }

    // ---- Ajustes ----

    @Test
    fun switches_disableEachKind() {
        val tasks = listOf(task(1, today.atTime(15, 0)), daily(2))
        val debts = listOf(debt(3, due = today.plusDays(1)))
        assertNull(ReminderPlanner.nextAfter(tasks, debts, settings.copy(enabled = false), now))
        val onlyDebts = settings.copy(tasksEnabled = false, dailyEnabled = false)
        // Vence mañana, aviso 1 día antes a las 09:00 = hoy 09:00.
        assertEquals(today.atTime(9, 0), ReminderPlanner.debtReminderAt(debts[0], onlyDebts))
        assertEquals(
            listOf("debt-3"),
            due(tasks, debts, from = today.minusDays(1).atTime(23, 0), to = today.atTime(23, 0), s = onlyDebts).map { it.key },
        )
    }

    @Test
    fun nextAfter_picksTheEarliestOfAllKinds() {
        val tasks = listOf(task(1, today.atTime(18, 0)), daily(2))
        val debts = listOf(debt(3, due = today.plusDays(1)))
        // tarea 17:30, diaria 20:00, deuda hoy 09:00 (ya pasó) -> 17:30
        assertEquals(today.atTime(17, 30), ReminderPlanner.nextAfter(tasks, debts, settings, now))
    }

    @Test
    fun labels() {
        assertEquals("A la hora exacta", ReminderOptions.taskLeadLabel(0))
        assertEquals("30 minutos antes", ReminderOptions.taskLeadLabel(30))
        assertEquals("1 hora antes", ReminderOptions.taskLeadLabel(60))
        assertEquals("3 horas antes", ReminderOptions.taskLeadLabel(180))
        assertEquals("1 día antes", ReminderOptions.taskLeadLabel(1440))
        assertEquals("El mismo día del vencimiento", ReminderOptions.debtLeadLabel(0))
        assertEquals("7 días antes", ReminderOptions.debtLeadLabel(7))
    }
}
