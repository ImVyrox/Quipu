package com.quipu.app.domain

import java.time.LocalDate
import java.time.LocalDateTime

/** Dirección del dinero en un registro de préstamo/deuda. */
enum class DebtType { OWED_TO_ME, I_OWE }

/** Estado de un registro: PENDING = pendiente, SETTLED = pagado (deuda) o cobrado (préstamo). */
enum class DebtStatus { PENDING, SETTLED }

/**
 * Tarea del usuario. [dueAt] es hora local de pared, sin zona horaria.
 *
 * Una tarea diaria ([isDaily]) es un único registro recurrente: no tiene hora límite
 * (su [dueAt] se ignora) y solo cuenta como completada el día de [lastCompletedDate].
 * Usa [TaskLogic.resolveForDay] para obtener su [completed] efectivo en una fecha.
 */
data class TaskItem(
    val id: Long,
    val title: String,
    val description: String,
    val dueAt: LocalDateTime,
    val completed: Boolean,
    val isDaily: Boolean = false,
    val lastCompletedDate: LocalDate? = null,
)

/**
 * Registro de dinero. [amountMinor] está en centavos (2 decimales) para evitar
 * errores de coma flotante. [personKey] es la clave normalizada de la persona.
 */
data class DebtItem(
    val id: Long,
    val type: DebtType,
    val personName: String,
    val personKey: String,
    val amountMinor: Long,
    val recordDate: LocalDate,
    val dueDate: LocalDate?,
    val note: String,
    val status: DebtStatus,
    val settledDate: LocalDate?,
)

/** Datos para crear o actualizar una tarea (id == 0 significa nueva). */
data class TaskDraft(
    val id: Long = 0,
    val title: String,
    val description: String,
    val dueAt: LocalDateTime,
    val isDaily: Boolean = false,
)

/** Datos para crear o actualizar un registro (id == 0 significa nuevo). */
data class DebtDraft(
    val id: Long = 0,
    val type: DebtType,
    val personName: String,
    val amountMinor: Long,
    val recordDate: LocalDate,
    val dueDate: LocalDate?,
    val note: String,
)
