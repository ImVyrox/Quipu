package com.quipu.app.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit

enum class OccurrenceStatus(val label: String) {
    PAID("Pagado"),
    PENDING("Pendiente"),
    OVERDUE("Vencido"),

    /** El pago está en pausa: el vencimiento no se cobra ni se avisa. */
    PAUSED("En pausa"),
}

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

/**
 * Situación de un pago para la lista de próximos pagos: los vencimientos sin pagar ya
 * pasados ([overdue]), el próximo vencimiento ([next]) y los pagos hechos ([paid]).
 */
data class RecurringSchedule(
    val payment: RecurringPayment,
    val overdue: List<RecurringOccurrence>,
    val next: RecurringOccurrence?,
    /** Gastos que saldaron este pago, del más reciente al más antiguo. */
    val paid: List<Movement>,
) {
    /** Lo primero que hay que atender: el vencido más antiguo o, si no hay, el próximo. */
    val first: RecurringOccurrence? get() = overdue.firstOrNull() ?: next
}

/** Resultado de confirmar un vencimiento. */
sealed interface ConfirmResult {
    /** Gasto a guardar (id 0). */
    data class Created(val movement: Movement) : ConfirmResult

    /** Ese mes ya estaba pagado: no se crea otra salida de dinero. */
    data class AlreadyPaid(val existing: Movement) : ConfirmResult

    /** El pago no tiene vencimiento ese mes (antes del inicio, después del fin, otra frecuencia o en pausa). */
    data object NotDue : ConfirmResult
}

/**
 * Vencimientos de pagos recurrentes. Se generan a partir de la regla (día del mes,
 * frecuencia, inicio y fin), así que no hay registros que duplicar: el pago de un mes es
 * el único gasto con ese [Movement.recurringPaymentId] y [Movement.period] (lo respalda
 * un índice único en la base de datos). Un vencimiento pendiente no reduce el disponible
 * hasta que se confirma.
 */
object RecurringPlanner {

    /** Cuántos meses hacia atrás se buscan vencimientos sin pagar. */
    const val OVERDUE_LOOKBACK_MONTHS = 24L

    /**
     * Cuántos vencimientos hacia adelante se revisan para hallar el próximo sin pagar. Cubre
     * pagar por adelantado (varios meses ya pagados) sin recorrer un futuro infinito.
     */
    const val LOOKAHEAD_OCCURRENCES = 24L

    /** Margen en días, antes y después del vencimiento, para detectar un gasto ya anotado a mano. */
    const val MANUAL_MATCH_DAYS = 15L

    /** Vencimiento del mes: el día elegido o, si el mes es más corto, su último día. */
    fun dueDate(payment: RecurringPayment, period: YearMonth): LocalDate =
        period.atDay(payment.dayOfMonth.coerceIn(1, period.lengthOfMonth()))

    /**
     * Si el pago vence en ese mes: el mes cae en su frecuencia contando desde el mes de
     * inicio, y el vencimiento está dentro de la vigencia (no antes de [RecurringPayment.startDate]
     * ni después de [RecurringPayment.endDate]).
     */
    fun isDueIn(payment: RecurringPayment, period: YearMonth): Boolean {
        val offset = ChronoUnit.MONTHS.between(YearMonth.from(payment.startDate), period)
        if (offset < 0 || offset % payment.frequency.months != 0L) return false
        val due = dueDate(payment, period)
        return !due.isBefore(payment.startDate) && (payment.endDate == null || !due.isAfter(payment.endDate))
    }

    /** Movimiento que paga ese mes, si existe. */
    fun paymentFor(payment: RecurringPayment, period: YearMonth, movements: List<Movement>): Movement? =
        Ledger.distinct(movements).firstOrNull {
            it.type == MovementType.EXPENSE && it.recurringPaymentId == payment.id && it.period == period
        }

    /**
     * Vencimientos entre dos meses (ambos incluidos), uno por mes como máximo. Los meses
     * sin pagar de un pago en pausa salen como [OccurrenceStatus.PAUSED].
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
            .map { period -> occurrence(payment, period, movements, today) }
            .toList()

    private fun occurrence(
        payment: RecurringPayment,
        period: YearMonth,
        movements: List<Movement>,
        today: LocalDate,
    ): RecurringOccurrence {
        val due = dueDate(payment, period)
        val paid = paymentFor(payment, period, movements)
        val status = when {
            paid != null -> OccurrenceStatus.PAID
            !payment.active -> OccurrenceStatus.PAUSED
            due.isBefore(today) -> OccurrenceStatus.OVERDUE
            else -> OccurrenceStatus.PENDING
        }
        return RecurringOccurrence(payment, period, due, status, paid?.id)
    }

    /**
     * Vencimientos sin pagar ya pasados (como mucho [OVERDUE_LOOKBACK_MONTHS] atrás), el
     * próximo vencimiento sin pagar desde [today] y el historial de pagos. En pausa no
     * hay vencidos ni próximo.
     */
    fun schedule(payment: RecurringPayment, movements: List<Movement>, today: LocalDate): RecurringSchedule {
        val paid = Ledger.distinct(movements)
            .filter { it.type == MovementType.EXPENSE && it.recurringPaymentId == payment.id }
            .sortedWith(compareByDescending<Movement> { it.period }.thenByDescending { it.date })
        if (!payment.active) return RecurringSchedule(payment, emptyList(), null, paid)

        val thisMonth = YearMonth.from(today)
        val start = maxOf(YearMonth.from(payment.startDate), thisMonth.minusMonths(OVERDUE_LOOKBACK_MONTHS))
        val overdue = occurrences(payment, start, thisMonth, movements, today)
            .filter { it.status == OccurrenceStatus.OVERDUE }
        val next = upcoming(payment, movements, today).firstOrNull()
        return RecurringSchedule(payment, overdue, next, paid)
    }

    /**
     * Próximos vencimientos sin pagar desde [today] (sin los vencidos), en orden. Salta los
     * meses ya pagados por adelantado; revisa como mucho [LOOKAHEAD_OCCURRENCES] vencimientos.
     */
    fun upcoming(payment: RecurringPayment, movements: List<Movement>, today: LocalDate): List<RecurringOccurrence> {
        if (!payment.active) return emptyList()
        val from = maxOf(YearMonth.from(today), YearMonth.from(payment.startDate))
        val to = from.plusMonths(payment.frequency.months * LOOKAHEAD_OCCURRENCES)
        return occurrences(payment, from, to, movements, today).filter { it.status == OccurrenceStatus.PENDING }
    }

    /**
     * Agenda de todos los pagos: primero los que tienen vencidos, luego por fecha del
     * próximo vencimiento, y al final los que están en pausa o ya no vencen.
     */
    fun schedules(payments: List<RecurringPayment>, movements: List<Movement>, today: LocalDate): List<RecurringSchedule> =
        payments.map { schedule(it, movements, today) }.sortedWith(
            compareBy<RecurringSchedule>(
                { if (it.overdue.isNotEmpty()) 0 else if (it.next != null) 1 else 2 },
                { it.first?.dueDate ?: LocalDate.MAX },
                { it.payment.name.lowercase() },
            ),
        )

    /**
     * Confirma el pago de un mes con la fecha efectiva [paidOn]. Si ya estaba pagado
     * devuelve el existente en vez de crear otro: confirmar dos veces nunca produce dos
     * salidas de dinero. El gasto va en la moneda original del pago.
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
                category = payment.category,
                recurringPaymentId = payment.id,
                period = period,
            ),
        )
    }

    /**
     * Gastos anotados a mano que podrían ser este mismo vencimiento: pagados, sin pago
     * recurrente, en la misma moneda, a [MANUAL_MATCH_DAYS] días o menos del vencimiento y
     * con el mismo importe o el nombre del pago en la descripción. Sirven para vincularlos
     * en vez de registrar el pago dos veces. Los más cercanos al vencimiento, primero.
     */
    fun manualMatches(occurrence: RecurringOccurrence, movements: List<Movement>): List<Movement> {
        val payment = occurrence.payment
        val nameKey = PersonKey.keyOf(payment.name)
        val from = occurrence.dueDate.minusDays(MANUAL_MATCH_DAYS)
        val to = occurrence.dueDate.plusDays(MANUAL_MATCH_DAYS)
        return Ledger.distinct(movements)
            .filter {
                it.type == MovementType.EXPENSE && !it.pending && it.recurringPaymentId == null &&
                    it.currencyCode == payment.currencyCode && !it.date.isBefore(from) && !it.date.isAfter(to) &&
                    (it.amountMinor == payment.amountMinor || (nameKey.isNotEmpty() && PersonKey.keyOf(it.description).contains(nameKey)))
            }
            .sortedBy { kotlin.math.abs(ChronoUnit.DAYS.between(occurrence.dueDate, it.date)) }
    }

    /** Lo que falta pagar en [month] por moneda (previsto: no se resta del disponible). */
    fun pendingTotal(
        payments: List<RecurringPayment>,
        movements: List<Movement>,
        month: YearMonth,
        today: LocalDate,
    ): CurrencyAmounts =
        payments.flatMap { occurrences(it, month, month, movements, today) }
            .filter { it.status == OccurrenceStatus.PENDING || it.status == OccurrenceStatus.OVERDUE }
            .fold(CurrencyAmounts.EMPTY) { acc, o -> acc.plus(o.payment.currencyCode, o.payment.amountMinor) }

    // ---- Recordatorios ----

    /** Hora del aviso de un vencimiento: [RecurringReminder.leadDays] antes, a la hora elegida. */
    fun reminderAt(dueDate: LocalDate, reminder: RecurringReminder): LocalDateTime {
        val minute = reminder.minuteOfDay.coerceIn(0, 1439)
        return dueDate.minusDays(reminder.leadDays.toLong()).atTime(LocalTime.of(minute / 60, minute % 60))
    }

    /** Pagos con aviso: activos y con su recordatorio encendido. */
    private fun remindable(payments: List<RecurringPayment>) = payments.filter { it.active && it.reminder.enabled }

    /**
     * Avisos de vencimientos sin pagar con hora en (from, to]. Como mucho uno por pago:
     * el del vencimiento más reciente de la ventana. Cada pago usa su propio recordatorio.
     */
    fun remindersBetween(
        payments: List<RecurringPayment>,
        movements: List<Movement>,
        from: LocalDateTime,
        to: LocalDateTime,
    ): List<RecurringOccurrence> {
        if (!to.isAfter(from)) return emptyList()
        return remindable(payments).mapNotNull { payment ->
            // Un aviso cae a lo sumo [leadDays] antes del vencimiento: basta con revisar esos meses.
            val first = YearMonth.from(from.toLocalDate())
            val last = YearMonth.from(to.toLocalDate().plusDays(payment.reminder.leadDays.toLong()))
            generateSequence(first) { it.plusMonths(1) }
                .takeWhile { !it.isAfter(last) }
                .filter { isDueIn(payment, it) && paymentFor(payment, it, movements) == null }
                .map { RecurringOccurrence(payment, it, dueDate(payment, it), OccurrenceStatus.PENDING) }
                .lastOrNull { reminderAt(it.dueDate, payment.reminder).let { at -> at.isAfter(from) && !at.isAfter(to) } }
        }.sortedBy { reminderAt(it.dueDate, it.payment.reminder) }
    }

    /** Hora del próximo aviso estrictamente posterior a [after], o null. */
    fun nextReminderAfter(
        payments: List<RecurringPayment>,
        movements: List<Movement>,
        after: LocalDateTime,
    ): LocalDateTime? {
        val first = YearMonth.from(after.toLocalDate())
        return remindable(payments).mapNotNull { payment ->
            // Revisa los próximos vencimientos (saltando los pagados por adelantado).
            val start = maxOf(first, YearMonth.from(payment.startDate))
            generateSequence(start) { it.plusMonths(1) }
                .take((payment.frequency.months * LOOKAHEAD_OCCURRENCES + 2).toInt())
                .filter { isDueIn(payment, it) && paymentFor(payment, it, movements) == null }
                .map { reminderAt(dueDate(payment, it), payment.reminder) }
                .firstOrNull { it.isAfter(after) }
        }.minOrNull()
    }
}
