package com.quipu.app.domain

import java.time.LocalDate
import java.time.YearMonth

/*
 * Finanzas personales: ingresos, gastos, ahorros y pagos recurrentes.
 *
 * Todo el dinero real pasa por un único libro de movimientos ([Movement]). Lo previsto
 * (ingresos por horas, pagos recurrentes aún no pagados) se calcula a partir de reglas
 * y nunca se guarda como movimiento hasta que el usuario lo confirma.
 *
 * Cada operación conserva su moneda ([Movement.currencyCode]). Los totales se agrupan
 * por moneda ([CurrencyAmounts]) y nunca se suman importes de monedas distintas.
 */

/** Efecto de un movimiento. Los traspasos al ahorro no son ingresos ni gastos externos. */
enum class MovementType {
    /** Dinero recibido (sueldo, cobro de horas, venta...). Suma al disponible. */
    INCOME,

    /** Dinero gastado, incluido el pago confirmado de un pago recurrente. Resta del disponible. */
    EXPENSE,

    /** Traspaso del disponible a un objetivo de ahorro. */
    SAVING_DEPOSIT,

    /** Traspaso de un objetivo de ahorro al disponible. */
    SAVING_WITHDRAWAL,
    ;

    val isTransfer: Boolean get() = this == SAVING_DEPOSIT || this == SAVING_WITHDRAWAL
}

/** Categoría de un gasto. Se guarda por nombre: no renombres las constantes. */
enum class ExpenseCategory(val label: String) {
    FOOD("Alimentación"),
    TRANSPORT("Transporte"),
    STUDIES("Estudios"),
    SUBSCRIPTIONS("Suscripciones"),
    HEALTH("Salud"),
    OTHER("Otros"),
}

/**
 * Movimiento de dinero. [amountMinor] es siempre positivo y en centavos; el signo lo da
 * [type]. Un movimiento nunca se modifica para "deshacer" otro: el historial es auditable.
 *
 * Un movimiento [pending] todavía no ocurrió (ingreso por cobrar o gasto por pagar): se
 * muestra aparte y no cuenta en ningún saldo. Al confirmarlo, [date] pasa a ser el día
 * real en que se recibió o pagó el dinero.
 */
data class Movement(
    val id: Long,
    val type: MovementType,
    val amountMinor: Long,
    val currencyCode: String,
    val date: LocalDate,
    /** Opcional (puede ir vacía). */
    val description: String = "",
    /** Solo en gastos. */
    val category: ExpenseCategory? = null,
    val pending: Boolean = false,
    /** Fuente de un ingreso, si se registró desde una. */
    val incomeSourceId: Long? = null,
    /** Ingresos por horas: día trabajado y tiempo trabajado (minutos) que se cobra. */
    val workDate: LocalDate? = null,
    val workedMinutes: Int? = null,
    /** Objetivo de ahorro de un traspaso (obligatorio en SAVING_*). */
    val goalId: Long? = null,
    /** Pago recurrente que salda este gasto. */
    val recurringPaymentId: Long? = null,
    /**
     * Mes que salda un pago recurrente o un ingreso fijo. Como mucho hay un movimiento por
     * pago recurrente y mes, y uno por fuente fija y mes.
     */
    val period: YearMonth? = null,
)

/** Dinero real que el usuario declara tener en una moneda, en una fecha. No es un movimiento. */
data class DeclaredBalance(
    val id: Long,
    val currencyCode: String,
    val amountMinor: Long,
    val date: LocalDate,
)

enum class IncomeKind {
    /** Importe fijo cada mes (sueldo). */
    FIXED,

    /** Sin importe previsto: se registra lo que llegue. */
    VARIABLE,

    /** Tarifa por hora × horas previstas al mes. */
    HOURLY,
}

/**
 * Fuente de ingresos. Lo que calcula ([IncomePlanner.monthlyProjection]) es solo una
 * previsión: el dinero disponible solo cambia con movimientos INCOME reales.
 */
data class IncomeSource(
    val id: Long,
    val name: String,
    val kind: IncomeKind,
    val currencyCode: String,
    /** FIXED: importe mensual en centavos. */
    val fixedAmountMinor: Long? = null,
    /** HOURLY: tarifa por hora en centavos. */
    val hourlyRateMinor: Long? = null,
    /** HOURLY: horas previstas al mes, en minutos (admite medias horas: 42,5 h = 2550). */
    val plannedMinutesPerMonth: Int? = null,
    val active: Boolean = true,
)

/** Objetivo de ahorro. Su saldo sale de los traspasos ([Ledger.goalBalance]), no se guarda. */
data class SavingsGoal(
    val id: Long,
    val name: String,
    val currencyCode: String,
    val targetMinor: Long,
    val deadline: LocalDate? = null,
)

/**
 * Pago recurrente mensual (servicio, alquiler, suscripción). Genera un vencimiento por
 * mes en [dayOfMonth]; si el mes es más corto, vence el último día (31 -> 28/29/30).
 */
data class RecurringPayment(
    val id: Long,
    val name: String,
    val amountMinor: Long,
    val currencyCode: String,
    val dayOfMonth: Int,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val isSubscription: Boolean = false,
    val active: Boolean = true,
)

/**
 * Importes agrupados por código de moneda. Solo se suman importes de la misma moneda;
 * no hay conversión ni tasas de cambio.
 */
data class CurrencyAmounts(val byCode: Map<String, Long> = emptyMap()) {

    operator fun get(code: String): Long = byCode[code] ?: 0L

    fun plus(code: String, minor: Long): CurrencyAmounts =
        CurrencyAmounts(byCode + (code to this[code] + minor))

    operator fun plus(other: CurrencyAmounts): CurrencyAmounts =
        other.byCode.entries.fold(this) { acc, (code, minor) -> acc.plus(code, minor) }

    operator fun minus(other: CurrencyAmounts): CurrencyAmounts =
        other.byCode.entries.fold(this) { acc, (code, minor) -> acc.plus(code, -minor) }

    /** Sin las monedas que quedan en cero, ordenado por código para mostrar de forma estable. */
    fun nonZero(): CurrencyAmounts = CurrencyAmounts(byCode.filterValues { it != 0L }.toSortedMap())

    val isEmpty: Boolean get() = byCode.values.all { it == 0L }

    companion object {
        val EMPTY = CurrencyAmounts()

        fun of(vararg pairs: Pair<String, Long>): CurrencyAmounts =
            pairs.fold(EMPTY) { acc, (code, minor) -> acc.plus(code, minor) }
    }
}
