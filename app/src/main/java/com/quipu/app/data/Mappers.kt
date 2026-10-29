package com.quipu.app.data

import com.quipu.app.domain.DebtItem
import com.quipu.app.domain.DebtPayment
import com.quipu.app.domain.DeclaredBalance
import com.quipu.app.domain.IncomeSource
import com.quipu.app.domain.Movement
import com.quipu.app.domain.RecurringPayment
import com.quipu.app.domain.RecurringReminder
import com.quipu.app.domain.SavingsGoal
import com.quipu.app.domain.TaskItem
import java.time.LocalDate
import java.time.YearMonth
import java.time.LocalDateTime
import java.time.ZoneOffset

fun LocalDateTime.toLocalEpochSecond(): Long = toEpochSecond(ZoneOffset.UTC)

fun Long.toLocalDateTimeFromEpochSecond(): LocalDateTime =
    LocalDateTime.ofEpochSecond(this, 0, ZoneOffset.UTC)

fun TaskEntity.toDomain() = TaskItem(
    id = id,
    title = title,
    description = description,
    dueAt = dueLocalEpochSecond.toLocalDateTimeFromEpochSecond(),
    completed = completed,
    isDaily = isDaily,
    lastCompletedDate = lastCompletedEpochDay?.let { LocalDate.ofEpochDay(it) },
)

fun PaymentEntity.toDomain() = DebtPayment(
    id = id,
    debtId = debtId,
    amountMinor = amountMinor,
    date = LocalDate.ofEpochDay(epochDay),
)

fun DebtEntity.toDomain(payments: List<DebtPayment> = emptyList()) = DebtItem(
    id = id,
    type = type,
    personName = personName,
    personKey = personKey,
    amountMinor = amountMinor,
    recordDate = LocalDate.ofEpochDay(recordEpochDay),
    dueDate = dueEpochDay?.let { LocalDate.ofEpochDay(it) },
    note = note,
    status = status,
    settledDate = settledEpochDay?.let { LocalDate.ofEpochDay(it) },
    payments = payments,
)

fun MovementEntity.toDomain() = Movement(
    id = id,
    type = type,
    amountMinor = amountMinor,
    currencyCode = currencyCode,
    date = LocalDate.ofEpochDay(epochDay),
    description = description,
    category = category,
    pending = pending,
    incomeSourceId = incomeSourceId,
    workDate = workEpochDay?.let { LocalDate.ofEpochDay(it) },
    workedMinutes = workedMinutes,
    goalId = goalId,
    recurringPaymentId = recurringPaymentId,
    period = period?.let { YearMonth.parse(it) },
)

/** [createdAtEpochSecond] e [isDemo] no están en el dominio: al actualizar se conservan los guardados. */
fun Movement.toEntity(createdAtEpochSecond: Long, isDemo: Boolean = false) = MovementEntity(
    id = id,
    type = type,
    amountMinor = amountMinor,
    currencyCode = currencyCode,
    epochDay = date.toEpochDay(),
    description = description.trim(),
    category = category,
    pending = pending,
    incomeSourceId = incomeSourceId,
    workEpochDay = workDate?.toEpochDay(),
    workedMinutes = workedMinutes,
    goalId = goalId,
    recurringPaymentId = recurringPaymentId,
    period = period?.toString(),
    createdAtEpochSecond = createdAtEpochSecond,
    isDemo = isDemo,
)

fun IncomeSourceEntity.toDomain() = IncomeSource(
    id = id,
    name = name,
    kind = kind,
    currencyCode = currencyCode,
    fixedAmountMinor = fixedAmountMinor,
    hourlyRateMinor = hourlyRateMinor,
    plannedMinutesPerMonth = plannedMinutesPerMonth,
    active = active,
)

fun IncomeSource.toEntity(isDemo: Boolean = false) = IncomeSourceEntity(
    id = id,
    name = name.trim(),
    kind = kind,
    currencyCode = currencyCode,
    fixedAmountMinor = fixedAmountMinor,
    hourlyRateMinor = hourlyRateMinor,
    plannedMinutesPerMonth = plannedMinutesPerMonth,
    active = active,
    isDemo = isDemo,
)

fun DeclaredBalanceEntity.toDomain() = DeclaredBalance(
    id = id,
    currencyCode = currencyCode,
    amountMinor = amountMinor,
    date = LocalDate.ofEpochDay(epochDay),
)

fun SavingsGoalEntity.toDomain() = SavingsGoal(
    id = id,
    name = name,
    currencyCode = currencyCode,
    targetMinor = targetMinor,
    createdDate = LocalDate.ofEpochDay(createdEpochDay),
    deadline = deadlineEpochDay?.let { LocalDate.ofEpochDay(it) },
    description = description,
    status = status,
    completedDate = completedEpochDay?.let { LocalDate.ofEpochDay(it) },
)

fun SavingsGoal.toEntity(isDemo: Boolean = false) = SavingsGoalEntity(
    id = id,
    name = name.trim(),
    description = description.trim(),
    targetMinor = targetMinor,
    currencyCode = currencyCode,
    createdEpochDay = createdDate.toEpochDay(),
    deadlineEpochDay = deadline?.toEpochDay(),
    status = status,
    completedEpochDay = completedDate?.toEpochDay(),
    isDemo = isDemo,
)

fun RecurringPaymentEntity.toDomain() = RecurringPayment(
    id = id,
    name = name,
    amountMinor = amountMinor,
    currencyCode = currencyCode,
    dayOfMonth = dayOfMonth,
    startDate = LocalDate.ofEpochDay(startEpochDay),
    endDate = endEpochDay?.let { LocalDate.ofEpochDay(it) },
    frequency = frequency,
    category = category,
    description = description,
    reminder = RecurringReminder(remindEnabled, remindLeadDays, remindMinuteOfDay),
    active = active,
)

fun RecurringPayment.toEntity(isDemo: Boolean = false) = RecurringPaymentEntity(
    id = id,
    name = name.trim(),
    amountMinor = amountMinor,
    currencyCode = currencyCode,
    dayOfMonth = dayOfMonth,
    frequency = frequency,
    startEpochDay = startDate.toEpochDay(),
    endEpochDay = endDate?.toEpochDay(),
    category = category,
    description = description.trim(),
    active = active,
    remindEnabled = reminder.enabled,
    remindLeadDays = reminder.leadDays,
    remindMinuteOfDay = reminder.minuteOfDay,
    isDemo = isDemo,
)
