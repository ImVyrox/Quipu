package com.quipu.app.domain

import java.time.LocalDate

data class MovementFormErrors(
    val amount: String? = null,
    val date: String? = null,
    val description: String? = null,
) {
    val hasErrors: Boolean get() = amount != null || date != null || description != null
}

data class IncomeSourceFormErrors(
    val name: String? = null,
    val amount: String? = null,
    val rate: String? = null,
    val hours: String? = null,
) {
    val hasErrors: Boolean get() = name != null || amount != null || rate != null || hours != null
}

data class GoalFormErrors(
    val name: String? = null,
    val target: String? = null,
    val deadline: String? = null,
) {
    val hasErrors: Boolean get() = name != null || target != null || deadline != null
}

data class RecurringFormErrors(
    val name: String? = null,
    val amount: String? = null,
    val day: String? = null,
    val endDate: String? = null,
) {
    val hasErrors: Boolean get() = name != null || amount != null || day != null || endDate != null
}

/** Validaciones de los formularios de finanzas personales. Mensajes en español. */
object FinanceValidators {

    /** Ingreso o gasto: importe y fecha obligatorios; descripción opcional. */
    fun validateMovement(amountText: String, date: LocalDate?, description: String): MovementFormErrors =
        MovementFormErrors(
            amount = amountError(amountText),
            date = if (date == null) "Elige la fecha" else null,
            description = if (description.length > Limits.NOTE_MAX) "Máximo ${Limits.NOTE_MAX} caracteres" else null,
        )

    fun validateIncomeSource(
        name: String,
        kind: IncomeKind,
        amountText: String,
        rateText: String,
        hoursText: String,
    ): IncomeSourceFormErrors = IncomeSourceFormErrors(
        name = nameError(name),
        amount = if (kind == IncomeKind.FIXED) amountError(amountText) else null,
        rate = if (kind == IncomeKind.HOURLY) amountError(rateText, "Escribe la tarifa por hora") else null,
        hours = if (kind == IncomeKind.HOURLY) hoursError(hoursText) else null,
    )

    fun validateGoal(name: String, targetText: String, deadline: LocalDate?, today: LocalDate): GoalFormErrors =
        GoalFormErrors(
            name = nameError(name),
            target = amountError(targetText, "Escribe la meta"),
            deadline = if (deadline != null && deadline.isBefore(today)) "La fecha límite ya pasó" else null,
        )

    fun validateRecurring(
        name: String,
        amountText: String,
        dayOfMonth: Int?,
        startDate: LocalDate,
        endDate: LocalDate?,
    ): RecurringFormErrors = RecurringFormErrors(
        name = nameError(name),
        amount = amountError(amountText),
        day = if (dayOfMonth == null || dayOfMonth !in 1..31) "Elige un día del 1 al 31" else null,
        endDate = if (endDate != null && endDate.isBefore(startDate)) "El fin no puede ser anterior al inicio" else null,
    )

    private fun nameError(name: String): String? {
        val clean = name.trim()
        return when {
            clean.isEmpty() -> "Escribe un nombre"
            clean.length > Limits.TITLE_MAX -> "Máximo ${Limits.TITLE_MAX} caracteres"
            else -> null
        }
    }

    private fun amountError(text: String, emptyMessage: String = "Escribe el importe"): String? {
        if (text.isBlank()) return emptyMessage
        val minor = Money.parseToMinor(text)
        return when {
            minor == null -> "Importe no válido. Usa números, por ejemplo 150 o 150,50"
            minor <= 0L -> "El importe debe ser mayor que cero"
            minor > Money.MAX_MINOR -> "El importe es demasiado grande"
            else -> null
        }
    }

    private fun hoursError(text: String): String? {
        if (text.isBlank()) return "Escribe las horas al mes"
        val minutes = IncomePlanner.parseHoursToMinutes(text)
        return when {
            minutes == null -> "Horas no válidas. Usa números, por ejemplo 42 o 42,5"
            minutes <= 0 -> "Las horas deben ser mayores que cero"
            minutes > IncomePlanner.MAX_MINUTES_PER_MONTH -> "Un mes no tiene tantas horas"
            else -> null
        }
    }
}
