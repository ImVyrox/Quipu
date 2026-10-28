package com.quipu.app.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

enum class OccurrenceStatus { PAID, PENDING, OVERDUE }

/**
 * Vencimiento de un pago recurrente en un mes. No se guarda: se calcula de la regla.
 * Solo existe como dinero real cuando se paga ([movementId]).
 */
data class RecurringOccurrence(
    val payment: RecurringPayment,
    val period: YearMonth,
    val dueDate: LocalDate,
    val status: OccurrenceStatus,
    val movementId: Long? = null,
) {
    /** Clave estable del vencimiento: un pago y un mes. */
    val key: String get() = "recurring-${payment.id}-$period"
}

/** Resultado de confirmar un vencimiento. */
sealed interface ConfirmResult {
    /** Gasto a guardar (id 0). */
    data class Created(val movement: Movement) : ConfirmResult

    /** Ese mes ya estaba pagado: no se crea otra salida de dinero. */
    data class AlreadyPaid(val existing: Movement) : ConfirmResult

    /** El pago no tiene vencimiento ese mes (antes del inicio, después del fin o inactivo). */
    data object NotDue : ConfirmResult
}

/** Ajustes de los avisos de pagos recurrentes (mismo formato que los de deudas). */
data class RecurringReminderSettings(
    val enabled: Boolean = true,
    val leadDays: Int = 1,
    val minuteOfDay: Int = 9 * 60,
)

/**
 * Vencimientos mensuales de pagos recurrentes. Se generan a partir de la regla (día del
 * mes, inicio y fin), así que no hay registros que duplicar: el pago de un mes es el
 * único gasto con ese [Movement.recurringPaymentId] y [Movement.recurringPeriod].
 * Un vencimiento pendiente no reduce el disponible hasta que se confirma.
 */
object RecurringPlanner {

    /** Vencimiento del mes: el día elegido o, si el mes es más corto, su último día. */
    fun dueDate(payment: RecurringPayment, period: YearMonth): LocalDate =
        period.atDay(payment.dayOfMonth.coerceIn(1, period.lengthOfMonth()))

    /** Si el pago vence en ese mes (dentro de su vigencia). */
    fun isDueIn(payment: RecurringPayment, period: YearMonth): Boolean {
        val due = dueDate(payment, period)
        return !due.isBefore(payment.startDate) && (payment.endDate == null || !due.isAfter(payment.endDate))
    }

    /** Movimiento que paga ese mes, si existe. */
    fun paymentFor(payment: RecurringPayment, period: YearMonth, movements: List<Movement>): Movement? =
        Ledger.distinct(movements).firstOrNull {
            it.type == MovementType.EXPENSE && it.recurringPaymentId == payment.id && it.recurringPeriod == period
        }

    /**
     * Vencimientos entre dos meses (ambos incluidos), uno por mes como máximo. Un pago
     * inactivo solo muestra los meses ya pagados.
     */
    fun occurrences(
        payment: RecurringPayment,
        from: YearMonth,
        to: YearMonth,
        movements: List<Movement>,
        today: LocalDate,
    ): List<RecurringOccurrence> =
        generateSequence(from) { it.plusMonths(1) }
            .takeWhile { !it.isAfter(to) }
            .filter { isDueIn(payment, it) }
            .mapNotNull { period ->
                val due = dueDate(payment, period)
                val paid = paymentFor(payment, period, movements)
                when {
                    paid != null -> RecurringOccurrence(payment, period, due, OccurrenceStatus.PAID, paid.id)
                    !payment.active -> null
                    due.isBefore(today) -> RecurringOccurrence(payment, period, due, OccurrenceStatus.OVERDUE)
                    else -> RecurringOccurrence(payment, period, due, OccurrenceStatus.PENDING)
                }
            }
            .toList()

    /**
     * Confirma el pago de un mes. Si ya estaba pagado devuelve el existente en vez de
     * crear otro: confirmar dos veces nunca produce dos salidas de dinero.
     */
    fun confirm(
        payment: RecurringPayment,
        period: YearMonth,
        paidOn: LocalDate,
        movements: List<Movement>,
        amountMinor: Long = payment.amountMinor,
    ): ConfirmResult {
        paymentFor(payment, period, movements)?.let { return ConfirmResult.AlreadyPaid(it) }
        if (!payment.active || !isDueIn(payment, period)) return ConfirmResult.NotDue
        return ConfirmResult.Created(
            Movement(
                id = 0,
                type = MovementType.EXPENSE,
                amountMinor = amountMinor,
                currencyCode = payment.currencyCode,
                date = paidOn,
                description = payment.name,
                recurringPaymentId = payment.id,
                recurringPeriod = period,
            ),
        )
    }

    /** Lo que falta pagar en [month] por moneda (previsto: no se resta del disponible). */
    fun pendingTotal(
        payments: List<RecurringPayment>,
        movements: List<Movement>,
        month: YearMonth,
        today: LocalDate,
    ): CurrencyAmounts =
        payments.flatMap { occurrences(it, month, month, movements, today) }
            .filter { it.status != OccurrenceStatus.PAID }
            .fold(CurrencyAmounts.EMPTY) { acc, o -> acc.plus(o.payment.currencyCode, o.payment.amountMinor) }

    // ---- Recordatorios ----

    /** Hora del aviso de un vencimiento: [RecurringReminderSettings.leadDays] antes, a la hora elegida. */
    fun reminderAt(dueDate: LocalDate, settings: RecurringReminderSettings): LocalDateTime {
        val minute = settings.minuteOfDay.coerceIn(0, 1439)
        return dueDate.minusDays(settings.leadDays.toLong()).atTime(LocalTime.of(minute / 60, minute % 60))
    }

    /**
     * Avisos de vencimientos sin pagar con hora en (from, to]. Como mucho uno por pago:
     * el del vencimiento más reciente de la ventana.
     */
    fun remindersBetween(
        payments: List<RecurringPayment>,
        movements: List<Movement>,
        settings: RecurringReminderSettings,
        from: LocalDateTime,
        to: LocalDateTime,
    ): List<RecurringOccurrence> {
        if (!settings.enabled || !to.isAfter(from)) return emptyList()
        return payments.filter { it.active }.mapNotNull { payment ->
            // Un aviso cae a lo sumo [leadDays] antes del vencimiento: basta con revisar esos meses.
            windowPeriods(from, to, settings)
                .filter { isDueIn(payment, it) && paymentFor(payment, it, movements) == null }
                .map { RecurringOccurrence(payment, it, dueDate(payment, it), OccurrenceStatus.PENDING) }
                .lastOrNull { reminderAt(it.dueDate, settings).let { at -> at.isAfter(from) && !at.isAfter(to) } }
        }.sortedBy { reminderAt(it.dueDate, settings) }
    }

    /** Hora del próximo aviso estrictamente posterior a [after], o null. */
    fun nextReminderAfter(
        payments: List<RecurringPayment>,
        movements: List<Movement>,
        settings: RecurringReminderSettings,
        after: LocalDateTime,
    ): LocalDateTime? {
        if (!settings.enabled) return null
        val first = YearMonth.from(after.toLocalDate())
        // El próximo aviso de un pago activo está como mucho en el mes siguiente al actual,
        // salvo que empiece más tarde: se revisan hasta dos meses más allá de su inicio.
        return payments.filter { it.active }.mapNotNull { payment ->
            val start = maxOf(first, YearMonth.from(payment.startDate))
            generateSequence(start) { it.plusMonths(1) }
                .take(3)
                .filter { isDueIn(payment, it) && paymentFor(payment, it, movements) == null }
                .map { reminderAt(dueDate(payment, it), settings) }
                .firstOrNull { it.isAfter(after) }
        }.minOrNull()
    }

    private fun windowPeriods(
        from: LocalDateTime,
        to: LocalDateTime,
        settings: RecurringReminderSettings,
    ): Sequence<YearMonth> {
        val first = YearMonth.from(from.toLocalDate())
        val last = YearMonth.from(to.toLocalDate().plusDays(settings.leadDays.toLong()))
        return generateSequence(first) { it.plusMonths(1) }.takeWhile { !it.isAfter(last) }
    }
}
