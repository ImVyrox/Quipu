package com.quipu.app.data

import com.quipu.app.domain.DebtItem
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

fun DebtEntity.toDomain() = DebtItem(
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
)
