package com.quipu.app.domain

import java.time.LocalDate

data class MovementFormErrors(
    val amount: String? = null,
    val date: String? = null,
    val description: String? = null,
    val category: String? = null,
) {
    val hasErrors: Boolean get() = amount != null || date != null || description != null || category != null
}

data class IncomeFormErrors(
    val amount: String? = null,
    val hours: String? = null,
    /** Fecha del ingreso (o día trabajado en los ingresos por horas). */
    val date: String? = null,
    val receivedDate: String? = null,
    val description: String? = null,
) {
    val hasErrors: Boolean
        get() = amount != null || hours != null || date != null || receivedDate != null || description != null
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

    /** Gasto: importe, fecha y categoría obligatorios; descripción opcional. */
    fun validateExpense(
        amountText: String,
        date: LocalDate?,
        category: ExpenseCategory?,
        description: String,
    ): MovementFormErrors =
        validateMovement(amountText, date, description).copy(
            category = if (category == null) "Elige una categoría" else null,
        )

    /**
     * Ingreso. Por horas ([hourly]) se piden las horas y el día trabajado (el importe se
     * calcula); si no, el importe y la fecha. Si ya se cobró ([received]) se pide la fecha
     * de cobro, que no puede ser anterior al día trabajado.
     */
    fun validateIncome(
        hourly: Boolean,
        amountText: String,
        hoursText: String,
        date: LocalDate?,
        received: Boolean,
        receivedDate: LocalDate?,
        description: String,
    ): IncomeFormErrors = IncomeFormErrors(
        amount = if (hourly) null else amountError(amountText),
        hours = if (hourly) hoursError(hoursText, "Escribe las horas trabajadas") else null,
        date = if (date == null) (if (hourly) "Elige el día trabajado" else "Elige la fecha") else null,
        receivedDate = when {
            !hourly || !received -> null
            receivedDate == null -> "Elige la fecha de cobro"
            date != null && receivedDate.isBefore(date) -> "No puede ser anterior al día trabajado"
            else -> null
        },
        description = if (description.length > Limits.NOTE_MAX) "Máximo ${Limits.NOTE_MAX} caracteres" else null,
    )

    /** Dinero declarado: admite cero (por ejemplo, "hoy no tengo nada en dólares"). */
    fun validateDeclared(amountText: String): String? =
        if (Money.parseToMinor(amountText) == 0L) null else amountError(amountText, "Escribe cuánto tienes")

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

    private fun hoursError(text: String, emptyMessage: String = "Escribe las horas al mes"): String? {
        if (text.isBlank()) return emptyMessage
        val minutes = IncomePlanner.parseHoursToMinutes(text)
        return when {
            minutes == null -> "Horas no válidas. Usa números, por ejemplo 42 o 42,5"
            minutes <= 0 -> "Las horas deben ser mayores que cero"
            minutes > IncomePlanner.MAX_MINUTES_PER_MONTH -> "Un mes no tiene tantas horas"
            else -> null
        }
    }
}
