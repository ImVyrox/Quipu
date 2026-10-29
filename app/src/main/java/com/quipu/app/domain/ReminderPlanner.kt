package com.quipu.app.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class ReminderKind { TASK, DAILY_TASK, DEBT, RECURRING }

/**
 * Un aviso concreto. [key] identifica al elemento (no a la ocurrencia), así un mismo
 * elemento nunca genera dos avisos a la vez. Todas las horas son locales de pared.
 */
data class Reminder(
    val key: String,
    val kind: ReminderKind,
    val itemId: Long,
    val at: LocalDateTime,
    /** Pagos recurrentes: el vencimiento del que se avisa. */
    val dueDate: LocalDate? = null,
)

/**
 * Calcula cuándo avisar a partir de los datos guardados. No depende de Android: el
 * programador de alarmas pregunta "qué avisos tocaban entre la última revisión y
 * ahora" y "cuál es el siguiente", así funciona aunque la app esté cerrada o el
 * teléfono se haya apagado (al volver, se avisa de lo pendiente reciente).
 */
object ReminderPlanner {

    /**
     * Cuánto se recupera hacia atrás. Sin permiso de alarmas exactas, Android puede diferir
     * la alarma hasta un día entero (cuota de App Standby «rare»/«restricted», habitual en
     * Xiaomi): con 26 h los avisos atrasados llegan todos juntos en vez de perderse.
     */
    const val CATCH_UP_HOURS = 26L

    /** Inicio de la ventana a revisar: la última revisión, pero no más de [CATCH_UP_HOURS] atrás. */
    fun catchUpFrom(lastCheck: LocalDateTime?, now: LocalDateTime): LocalDateTime? =
        lastCheck?.let { maxOf(it, now.minusHours(CATCH_UP_HOURS)) }

    /**
     * Si un aviso que llega tarde sigue teniendo sentido [today]. Una tarea diaria solo se
     * recuerda el mismo día ("aún no completas … hoy"); el resto, aunque llegue tarde.
     */
    fun stillRelevant(reminder: Reminder, today: LocalDate): Boolean =
        reminder.kind != ReminderKind.DAILY_TASK || reminder.at.toLocalDate() == today

    /**
     * Avisos con hora en el intervalo (from, to], uno como máximo por elemento. Los pagos
     * recurrentes ([recurring], con sus [movements] para saber qué está pagado) usan el
     * recordatorio de cada pago; el interruptor general ([ReminderSettings.enabled]) los apaga todos.
     */
    fun dueBetween(
        tasks: List<TaskItem>,
        debts: List<DebtItem>,
        settings: ReminderSettings,
        from: LocalDateTime,
        to: LocalDateTime,
        recurring: List<RecurringPayment> = emptyList(),
        movements: List<Movement> = emptyList(),
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

        RecurringPlanner.remindersBetween(recurring, movements, from, to).forEach { o ->
            val at = RecurringPlanner.reminderAt(o.dueDate, o.payment.reminder)
            result += Reminder(o.key, ReminderKind.RECURRING, o.payment.id, at, o.dueDate)
        }
        return result.sortedBy { it.at }
    }

    /** Hora del próximo aviso estrictamente posterior a [after], o null si no hay ninguno. */
    fun nextAfter(
        tasks: List<TaskItem>,
        debts: List<DebtItem>,
        settings: ReminderSettings,
        after: LocalDateTime,
        recurring: List<RecurringPayment> = emptyList(),
        movements: List<Movement> = emptyList(),
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
        RecurringPlanner.nextReminderAfter(recurring, movements, after)?.let { candidates += it }
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
