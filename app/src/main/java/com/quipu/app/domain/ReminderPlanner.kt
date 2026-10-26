package com.quipu.app.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class ReminderKind { TASK, DAILY_TASK, DEBT }

/**
 * Un aviso concreto. [key] identifica al elemento (no a la ocurrencia), así un mismo
 * elemento nunca genera dos avisos a la vez. Todas las horas son locales de pared.
 */
data class Reminder(
    val key: String,
    val kind: ReminderKind,
    val itemId: Long,
    val at: LocalDateTime,
)

/**
 * Calcula cuándo avisar a partir de los datos guardados. No depende de Android: el
 * programador de alarmas pregunta "qué avisos tocaban entre la última revisión y
 * ahora" y "cuál es el siguiente", así funciona aunque la app esté cerrada o el
 * teléfono se haya apagado (al volver, se avisa de lo pendiente reciente).
 */
object ReminderPlanner {

    /** Avisos con hora en el intervalo (from, to], uno como máximo por elemento. */
    fun dueBetween(
        tasks: List<TaskItem>,
        debts: List<DebtItem>,
        settings: ReminderSettings,
        from: LocalDateTime,
        to: LocalDateTime,
    ): List<Reminder> {
        if (!settings.enabled || !to.isAfter(from)) return emptyList()
        fun inWindow(at: LocalDateTime) = at.isAfter(from) && !at.isAfter(to)
        val result = mutableListOf<Reminder>()

        if (settings.tasksEnabled) {
            tasks.filter { !it.isDaily && !it.completed }.forEach { task ->
                val at = taskReminderAt(task, settings)
                if (inWindow(at)) result += Reminder("task-${task.id}", ReminderKind.TASK, task.id, at)
            }
        }

        if (settings.dailyEnabled) {
            val time = timeOf(settings.dailyMinuteOfDay)
            tasks.filter { it.isDaily }.forEach { task ->
                // Solo el día más reciente del intervalo: no tiene sentido avisar de días pasados.
                val day = generateSequence(to.toLocalDate()) { it.minusDays(1) }
                    .takeWhile { !it.isBefore(from.toLocalDate()) }
                    .firstOrNull { inWindow(it.atTime(time)) }
                if (day != null && !doneOn(task, day)) {
                    result += Reminder("daily-${task.id}", ReminderKind.DAILY_TASK, task.id, day.atTime(time))
                }
            }
        }

        if (settings.debtsEnabled) {
            debts.filter { needsDebtReminder(it) }.forEach { debt ->
                val at = debtReminderAt(debt, settings)!!
                if (inWindow(at)) result += Reminder("debt-${debt.id}", ReminderKind.DEBT, debt.id, at)
            }
        }
        return result.sortedBy { it.at }
    }

    /** Hora del próximo aviso estrictamente posterior a [after], o null si no hay ninguno. */
    fun nextAfter(
        tasks: List<TaskItem>,
        debts: List<DebtItem>,
        settings: ReminderSettings,
        after: LocalDateTime,
    ): LocalDateTime? {
        if (!settings.enabled) return null
        val candidates = mutableListOf<LocalDateTime>()

        if (settings.tasksEnabled) {
            tasks.filter { !it.isDaily && !it.completed }
                .map { taskReminderAt(it, settings) }
                .filterTo(candidates) { it.isAfter(after) }
        }
        if (settings.dailyEnabled) {
            val time = timeOf(settings.dailyMinuteOfDay)
            val today = after.toLocalDate()
            tasks.filter { it.isDaily }.forEach { task ->
                listOf(today, today.plusDays(1))
                    .firstOrNull { it.atTime(time).isAfter(after) && !doneOn(task, it) }
                    ?.let { candidates += it.atTime(time) }
            }
        }
        if (settings.debtsEnabled) {
            debts.filter { needsDebtReminder(it) }
                .map { debtReminderAt(it, settings)!! }
                .filterTo(candidates) { it.isAfter(after) }
        }
        return candidates.minOrNull()
    }

    fun taskReminderAt(task: TaskItem, settings: ReminderSettings): LocalDateTime =
        task.dueAt.minusMinutes(settings.taskLeadMinutes.toLong())

    fun debtReminderAt(debt: DebtItem, settings: ReminderSettings): LocalDateTime? =
        debt.dueDate?.minusDays(settings.debtLeadDays.toLong())?.atTime(timeOf(settings.debtMinuteOfDay))

    /** Pendiente, con vencimiento y con algo por pagar o cobrar. */
    private fun needsDebtReminder(debt: DebtItem) =
        debt.status == DebtStatus.PENDING && debt.dueDate != null && debt.remainingMinor > 0

    /**
     * Si la diaria ya se hizo ese día. Solo se guarda la última realización: para un
     * día anterior a ella no se sabe, así que se considera hecha (no se avisa del pasado).
     */
    private fun doneOn(task: TaskItem, day: LocalDate): Boolean =
        task.lastCompletedDate?.let { !it.isBefore(day) } == true

    private fun timeOf(minuteOfDay: Int): LocalTime =
        LocalTime.of(minuteOfDay.coerceIn(0, 1439) / 60, minuteOfDay.coerceIn(0, 1439) % 60)
}
