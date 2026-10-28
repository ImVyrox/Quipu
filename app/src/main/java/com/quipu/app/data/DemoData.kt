package com.quipu.app.data

import com.quipu.app.domain.DebtStatus
import com.quipu.app.domain.DebtType
import com.quipu.app.domain.ExpenseCategory
import com.quipu.app.domain.GoalStatus
import com.quipu.app.domain.IncomePlanner
import com.quipu.app.domain.Movement
import com.quipu.app.domain.MovementType
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

    /** Fuente por horas del ejemplo principal: 18 BOB/h × 42 h = 756 BOB previstos al mes. */
    fun hourlySource(): IncomeSourceEntity = IncomeSourceEntity(
        name = "Clases particulares (ejemplo)",
        kind = IncomePlanner.EXAMPLE_HOURLY.kind,
        currencyCode = IncomePlanner.EXAMPLE_HOURLY.currencyCode,
        fixedAmountMinor = null,
        hourlyRateMinor = IncomePlanner.EXAMPLE_HOURLY.hourlyRateMinor,
        plannedMinutesPerMonth = IncomePlanner.EXAMPLE_HOURLY.plannedMinutesPerMonth,
        isDemo = true,
    )

    /** Movimientos del mes en curso; el primer día del mes como mínimo para no salir del periodo. */
    fun movements(today: LocalDate, hourlySourceId: Long): List<Movement> {
        val first = today.withDayOfMonth(1)
        fun day(offset: Long) = maxOf(first, today.minusDays(offset))
        fun hours(minutes: Int, offset: Long, pending: Boolean) = Movement(
            id = 0,
            type = MovementType.INCOME,
            amountMinor = IncomePlanner.amountForMinutes(1_800, minutes),
            currencyCode = "BOB",
            date = day(offset),
            description = "Clases (ejemplo)",
            pending = pending,
            incomeSourceId = hourlySourceId,
            workDate = day(offset),
            workedMinutes = minutes,
        )
        fun expense(minor: Long, category: ExpenseCategory, offset: Long, note: String) = Movement(
            id = 0,
            type = MovementType.EXPENSE,
            amountMinor = minor,
            currencyCode = "BOB",
            date = day(offset),
            description = note,
            category = category,
        )
        return listOf(
            hours(10 * 60, 9, pending = false),
            hours(8 * 60, 4, pending = false),
            hours(6 * 60, 1, pending = true),
            expense(4_500, ExpenseCategory.FOOD, 8, "Almuerzo (ejemplo)"),
            expense(1_200, ExpenseCategory.TRANSPORT, 5, "Micro (ejemplo)"),
            expense(9_000, ExpenseCategory.STUDIES, 3, "Fotocopias y libro (ejemplo)"),
            expense(3_000, ExpenseCategory.SUBSCRIPTIONS, 2, ""),
        )
    }

    /** Objetivo de ejemplo con ahorro inicial explícito y una aportación del mes. */
    fun goal(today: LocalDate): SavingsGoalEntity = SavingsGoalEntity(
        name = "Laptop (ejemplo)",
        description = "Para la universidad",
        targetMinor = 300_000,
        currencyCode = "BOB",
        createdEpochDay = today.withDayOfMonth(1).toEpochDay(),
        deadlineEpochDay = today.plusMonths(6).toEpochDay(),
        status = GoalStatus.ACTIVE,
        completedEpochDay = null,
        isDemo = true,
    )

    fun goalMovements(today: LocalDate, goalId: Long): List<Movement> {
        val first = today.withDayOfMonth(1)
        return listOf(
            Movement(0, MovementType.SAVING_OPENING, 50_000, "BOB", first, "Lo que ya tenía guardado (ejemplo)", goalId = goalId),
            Movement(0, MovementType.SAVING_DEPOSIT, 5_000, "BOB", maxOf(first, today.minusDays(3)), "", goalId = goalId),
        )
    }
}
