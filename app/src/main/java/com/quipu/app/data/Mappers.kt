package com.quipu.app.data

import com.quipu.app.domain.DebtItem
import com.quipu.app.domain.DebtPayment
import com.quipu.app.domain.TaskItem
import java.time.LocalDate
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
