package com.quipu.app.data

import com.quipu.app.domain.DebtStatus
import com.quipu.app.domain.DebtType
import com.quipu.app.domain.PersonKey
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Datos de ejemplo OPCIONALES. Solo se insertan si el usuario lo pide desde Ajustes,
 * y todos llevan isDemo = true para poder eliminarlos sin tocar los datos reales.
 */
object DemoData {

    fun tasks(now: LocalDateTime): List<TaskEntity> {
        fun at(days: Long, hour: Int) =
            now.toLocalDate().plusDays(days).atTime(hour, 0).toLocalEpochSecond()
        return listOf(
            TaskEntity(title = "Pagar el alquiler (ejemplo)", description = "Transferencia antes del mediodía", dueLocalEpochSecond = at(-1, 10), completed = false, isDemo = true),
            TaskEntity(title = "Llamar al dentista (ejemplo)", description = "", dueLocalEpochSecond = at(0, 18), completed = false, isDemo = true),
            TaskEntity(title = "Comprar víveres (ejemplo)", description = "Pan, huevos, fruta", dueLocalEpochSecond = at(3, 9), completed = false, isDemo = true),
            TaskEntity(title = "Enviar informe (ejemplo)", description = "", dueLocalEpochSecond = at(-2, 15), completed = true, isDemo = true),
        )
    }

    fun debts(today: LocalDate): List<DebtEntity> {
        fun debt(
            type: DebtType,
            person: String,
            minor: Long,
            daysAgo: Long,
            dueInDays: Long?,
            note: String,
            status: DebtStatus = DebtStatus.PENDING,
        ) = DebtEntity(
            type = type,
            personName = PersonKey.displayName(person),
            personKey = PersonKey.keyOf(person),
            amountMinor = minor,
            recordEpochDay = today.minusDays(daysAgo).toEpochDay(),
            dueEpochDay = dueInDays?.let { today.plusDays(it).toEpochDay() },
            note = note,
            status = status,
            settledEpochDay = if (status == DebtStatus.SETTLED) today.toEpochDay() else null,
            isDemo = true,
        )
        return listOf(
            debt(DebtType.OWED_TO_ME, "Ana Rojas (ejemplo)", 15_000, 20, -3, "Préstamo para el taxi"),
            debt(DebtType.OWED_TO_ME, "Luis Mamani (ejemplo)", 8_050, 6, 10, "Almuerzo"),
            debt(DebtType.I_OWE, "Marta Quispe (ejemplo)", 22_000, 12, 5, "Libros de la universidad"),
            debt(DebtType.OWED_TO_ME, "Ana Rojas (ejemplo)", 5_000, 40, null, "Entradas", DebtStatus.SETTLED),
        )
    }
}
