package com.quipu.app.domain

import java.time.LocalDate
import java.time.LocalDateTime

enum class TaskFilter { ALL, PENDING, COMPLETED }

/** Situación temporal de una tarea respecto a "ahora". */
enum class TaskTiming { OVERDUE, SOON, LATER, DONE }

object TaskLogic {
    /** Una tarea pendiente es "próxima" si vence dentro de esta ventana. */
    const val SOON_WINDOW_HOURS = 24L

    /**
     * Estado de una tarea en el día [today]. Las tareas normales no cambian; una diaria
     * está completada solo si se realizó ese mismo día. Como se calcula desde la fecha
     * guardada, no depende de que la app esté abierta a medianoche: tras días cerrada,
     * cualquier realización anterior a hoy cuenta como pendiente.
     */
    fun resolveForDay(task: TaskItem, today: LocalDate): TaskItem =
        if (task.isDaily) task.copy(completed = task.lastCompletedDate == today) else task

    fun resolveForDay(tasks: List<TaskItem>, today: LocalDate): List<TaskItem> =
        tasks.map { resolveForDay(it, today) }

    /** Las tareas diarias no tienen hora límite: nunca son vencidas ni próximas. */
    fun timing(task: TaskItem, now: LocalDateTime): TaskTiming = when {
        task.completed -> TaskTiming.DONE
        task.isDaily -> TaskTiming.LATER
        task.dueAt.isBefore(now) -> TaskTiming.OVERDUE
        !task.dueAt.isAfter(now.plusHours(SOON_WINDOW_HOURS)) -> TaskTiming.SOON
        else -> TaskTiming.LATER
    }

    fun filter(tasks: List<TaskItem>, filter: TaskFilter): List<TaskItem> = when (filter) {
        TaskFilter.ALL -> tasks
        TaskFilter.PENDING -> tasks.filter { !it.completed }
        TaskFilter.COMPLETED -> tasks.filter { it.completed }
    }

    /**
     * Las diarias van primero (por orden de creación) porque son las de hoy; después,
     * las normales ordenadas por fecha y hora, y a igualdad por id para un orden estable.
     */
    fun sort(tasks: List<TaskItem>, ascending: Boolean): List<TaskItem> {
        val (daily, normal) = tasks.partition { it.isDaily }
        val comparator = compareBy<TaskItem>({ it.dueAt }, { it.id })
        return daily.sortedBy { it.id } + normal.sortedWith(if (ascending) comparator else comparator.reversed())
    }

    fun filterAndSort(tasks: List<TaskItem>, filter: TaskFilter, ascending: Boolean): List<TaskItem> =
        sort(filter(tasks, filter), ascending)
}
