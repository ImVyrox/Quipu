package com.quipu.app.ui

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Formatos de fecha y hora en español, independientes de la configuración del teléfono. */
object Formats {
    private val locale: Locale = Locale.forLanguageTag("es-BO")
    private val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", locale)
    private val monthFormatter = DateTimeFormatter.ofPattern("LLLL yyyy", locale)

    fun date(value: LocalDate): String = dateFormatter.format(value)

    fun time(value: LocalTime): String = timeFormatter.format(value)

    /** "Octubre 2026". */
    fun month(value: YearMonth): String = monthFormatter.format(value).replaceFirstChar { it.uppercase(locale) }

    /** Minutos como horas: 2520 -> "42 h", 2550 -> "42,5 h", 2535 -> "42,25 h". */
    fun hours(minutes: Int): String {
        val whole = minutes / 60
        val rest = minutes % 60
        if (rest == 0) return "$whole h"
        val decimals = (rest * 100 / 60).toString().padStart(2, '0').trimEnd('0')
        return "$whole,$decimals h"
    }

    /** "Hoy", "Mañana", "Ayer" o la fecha completa. */
    fun relativeDate(value: LocalDate, today: LocalDate): String =
        when (ChronoUnit.DAYS.between(today, value)) {
            0L -> "Hoy"
            1L -> "Mañana"
            -1L -> "Ayer"
            else -> date(value)
        }

    fun dateTime(value: LocalDateTime, today: LocalDate): String =
        relativeDate(value.toLocalDate(), today) + " · " + time(value.toLocalTime())
}
