package com.quipu.app.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth

/**
 * Situación de una fuente en un mes: lo previsto (dinero y horas), lo trabajado, lo
 * recibido y lo que falta cobrar. [projectedMinor] es null si no hay previsión (VARIABLE).
 */
data class IncomeProgress(
    val source: IncomeSource,
    val projectedMinor: Long?,
    /** Dinero ya recibido en el mes (por fecha de cobro). */
    val receivedMinor: Long,
    /** Horas previstas al mes (solo HOURLY), en minutos. */
    val plannedMinutes: Int? = null,
    /** Horas trabajadas en el mes (por día trabajado), cobradas o no, en minutos. */
    val workedMinutes: Int = 0,
    /** Ingresos anotados del mes que aún no se cobraron. */
    val toCollectMinor: Long = 0,
) {
    /** Lo que falta por recibir de lo previsto (nunca negativo). */
    val pendingMinor: Long? get() = projectedMinor?.let { (it - receivedMinor).coerceAtLeast(0L) }
}

/**
 * Previsión de ingresos. Lo previsto es informativo: nunca se cuenta como dinero
 * disponible. Lo recibido sale de los movimientos INCOME vinculados a la fuente.
 */
object IncomePlanner {
    /** Máximo de horas previstas al mes (31 días × 24 h). */
    const val MAX_MINUTES_PER_MONTH = 31 * 24 * 60

    /** Ejemplo inicial: 18 BOB por hora, 42 horas al mes = 756 BOB previstos. */
    val EXAMPLE_HOURLY = IncomeSource(
        id = 0,
        name = "Trabajo por horas",
        kind = IncomeKind.HOURLY,
        currencyCode = AppCurrencies.BOB.code,
        hourlyRateMinor = 18_00,
        plannedMinutesPerMonth = 42 * 60,
    )

    /**
     * Tarifa por hora × tiempo, en centavos. El tiempo va en minutos para admitir
     * fracciones de hora; se redondea al centavo (mitad hacia arriba).
     */
    fun amountForMinutes(hourlyRateMinor: Long, minutes: Int): Long =
        BigDecimal.valueOf(hourlyRateMinor)
            .multiply(BigDecimal.valueOf(minutes.toLong()))
            .divide(BigDecimal.valueOf(60), 0, RoundingMode.HALF_UP)
            .toLong()

    /** Ingreso mensual previsto, o null si la fuente no tiene previsión (VARIABLE) o está incompleta. */
    fun monthlyProjection(source: IncomeSource): Long? = when (source.kind) {
        IncomeKind.FIXED -> source.fixedAmountMinor
        IncomeKind.VARIABLE -> null
        IncomeKind.HOURLY -> {
            val rate = source.hourlyRateMinor
            val minutes = source.plannedMinutesPerMonth
            if (rate == null || minutes == null) null else amountForMinutes(rate, minutes)
        }
    }

    /** Recibido de una fuente en un mes (ingresos ya cobrados, por fecha de cobro, en la moneda de la fuente). */
    fun receivedIn(source: IncomeSource, movements: List<Movement>, month: YearMonth): Long =
        Ledger.settled(movements)
            .filter { isIncomeOf(it, source) && YearMonth.from(it.date) == month }
            .sumOf { it.amountMinor }

    /** Ingresos de la fuente anotados en el mes que aún no se cobraron. */
    fun toCollectIn(source: IncomeSource, movements: List<Movement>, month: YearMonth): Long =
        Ledger.distinct(movements)
            .filter { it.pending && isIncomeOf(it, source) && YearMonth.from(it.workDate ?: it.date) == month }
            .sumOf { it.amountMinor }

    /** Minutos trabajados en el mes según el día trabajado de cada cobro (cobrado o no). */
    fun workedMinutesIn(source: IncomeSource, movements: List<Movement>, month: YearMonth): Int =
        Ledger.distinct(movements)
            .filter { isIncomeOf(it, source) && YearMonth.from(it.workDate ?: it.date) == month }
            .sumOf { it.workedMinutes ?: 0 }

    /** Situación de cada fuente activa en [month]. */
    fun progress(sources: List<IncomeSource>, movements: List<Movement>, month: YearMonth): List<IncomeProgress> =
        sources.filter { it.active }.map { source ->
            IncomeProgress(
                source = source,
                projectedMinor = monthlyProjection(source),
                receivedMinor = receivedIn(source, movements, month),
                plannedMinutes = if (source.kind == IncomeKind.HOURLY) source.plannedMinutesPerMonth else null,
                workedMinutes = workedMinutesIn(source, movements, month),
                toCollectMinor = toCollectIn(source, movements, month),
            )
        }

    /**
     * Ingreso fijo ya anotado para ese mes, si existe. Un ingreso fijo se anota una vez
     * por mes: cambiar de mes no crea copias y no se puede anotar dos veces el mismo mes.
     */
    fun fixedIncomeFor(source: IncomeSource, month: YearMonth, movements: List<Movement>, exceptId: Long = 0): Movement? =
        if (source.kind != IncomeKind.FIXED) {
            null
        } else {
            Ledger.distinct(movements).firstOrNull {
                it.id != exceptId && isIncomeOf(it, source) && it.period == month
            }
        }

    private fun isIncomeOf(m: Movement, source: IncomeSource) =
        m.type == MovementType.INCOME && m.incomeSourceId == source.id && m.currencyCode == source.currencyCode

    /** Total previsto por moneda de las fuentes activas. Es una previsión, no dinero disponible. */
    fun projectedTotal(sources: List<IncomeSource>): CurrencyAmounts =
        sources.filter { it.active }.fold(CurrencyAmounts.EMPTY) { acc, source ->
            monthlyProjection(source)?.let { acc.plus(source.currencyCode, it) } ?: acc
        }

    /**
     * Convierte "42", "42,5" o "42.5" horas a minutos. Admite como mucho dos decimales y
     * redondea al minuto. Devuelve null si el formato no es válido.
     */
    fun parseHoursToMinutes(input: String): Int? {
        val text = input.trim()
        if (!HOURS_REGEX.matches(text)) return null
        return BigDecimal(text.replace(',', '.'))
            .multiply(BigDecimal.valueOf(60))
            .setScale(0, RoundingMode.HALF_UP)
            .toInt()
    }

    private val HOURS_REGEX = Regex("""\d{1,3}([.,]\d{1,2})?""")
}
