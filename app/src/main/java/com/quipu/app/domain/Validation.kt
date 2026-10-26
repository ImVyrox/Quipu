package com.quipu.app.domain

import java.time.LocalDate
import java.time.LocalTime

object Limits {
    const val TITLE_MAX = 100
    const val DESCRIPTION_MAX = 500
    const val PERSON_MAX = 60
    const val NOTE_MAX = 300
}

data class TaskFormErrors(
    val title: String? = null,
    val date: String? = null,
    val time: String? = null,
) {
    val hasErrors: Boolean get() = title != null || date != null || time != null
}

data class DebtFormErrors(
    val person: String? = null,
    val amount: String? = null,
    val recordDate: String? = null,
    val dueDate: String? = null,
    val note: String? = null,
) {
    val hasErrors: Boolean
        get() = person != null || amount != null || recordDate != null || dueDate != null || note != null
}

data class PaymentFormErrors(
    val amount: String? = null,
    val date: String? = null,
) {
    val hasErrors: Boolean get() = amount != null || date != null
}

/** Validaciones de formularios. Devuelven mensajes en español listos para mostrar. */
object Validators {

    fun validateTask(
        title: String,
        description: String,
        date: LocalDate?,
        time: LocalTime?,
        isDaily: Boolean = false,
    ): TaskFormErrors {
        val cleanTitle = title.trim()
        return TaskFormErrors(
            title = when {
                cleanTitle.isEmpty() -> "Escribe un título"
                cleanTitle.length > Limits.TITLE_MAX -> "Máximo ${Limits.TITLE_MAX} caracteres"
                description.length > Limits.DESCRIPTION_MAX ->
                    "La descripción admite máximo ${Limits.DESCRIPTION_MAX} caracteres"
                else -> null
            },
            // Una tarea diaria se puede hacer a cualquier hora: no exige fecha ni hora.
            date = if (!isDaily && date == null) "Elige una fecha" else null,
            time = if (!isDaily && time == null) "Elige una hora" else null,
        )
    }

    fun validateDebt(
        personName: String,
        amountText: String,
        recordDate: LocalDate?,
        dueDate: LocalDate?,
        note: String,
        /** Lo ya abonado al editar un registro: el importe no puede quedar por debajo. */
        paidMinor: Long = 0L,
    ): DebtFormErrors {
        val name = PersonKey.displayName(personName)
        val amountError = when {
            amountText.isBlank() -> "Escribe el importe"
            else -> {
                val minor = Money.parseToMinor(amountText)
                when {
                    minor == null -> "Importe no válido. Usa números, por ejemplo 150 o 150,50"
                    minor <= 0L -> "El importe debe ser mayor que cero"
                    minor > Money.MAX_MINOR -> "El importe es demasiado grande"
                    minor < paidMinor -> "No puede ser menor que lo ya abonado"
                    else -> null
                }
            }
        }
        return DebtFormErrors(
            person = when {
                name.isEmpty() -> "Escribe el nombre de la persona"
                name.length > Limits.PERSON_MAX -> "Máximo ${Limits.PERSON_MAX} caracteres"
                else -> null
            },
            amount = amountError,
            recordDate = if (recordDate == null) "Elige la fecha del registro" else null,
            dueDate = if (recordDate != null && dueDate != null && dueDate.isBefore(recordDate)) {
                "El vencimiento no puede ser anterior al registro"
            } else {
                null
            },
            note = if (note.length > Limits.NOTE_MAX) "Máximo ${Limits.NOTE_MAX} caracteres" else null,
        )
    }

    /**
     * Abono a un registro pendiente: debe ser mayor que cero, no superar lo que falta
     * ([remainingMinor]) y no ser anterior a la fecha del registro.
     */
    fun validatePayment(
        amountText: String,
        remainingMinor: Long,
        date: LocalDate?,
        recordDate: LocalDate,
        currency: AppCurrency,
    ): PaymentFormErrors {
        val amountError = when {
            amountText.isBlank() -> "Escribe el importe del abono"
            else -> {
                val minor = Money.parseToMinor(amountText)
                when {
                    minor == null -> "Importe no válido. Usa números, por ejemplo 150 o 150,50"
                    minor <= 0L -> "El abono debe ser mayor que cero"
                    minor > remainingMinor -> "Supera lo que falta (${Money.format(remainingMinor, currency)})"
                    else -> null
                }
            }
        }
        return PaymentFormErrors(
            amount = amountError,
            date = when {
                date == null -> "Elige la fecha del abono"
                date.isBefore(recordDate) -> "No puede ser anterior al registro"
                else -> null
            },
        )
    }
}
