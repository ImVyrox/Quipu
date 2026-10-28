package com.quipu.app.domain

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomePlannerTest {
    private val october = YearMonth.of(2026, 10)
    private val hourly = IncomePlanner.EXAMPLE_HOURLY.copy(id = 1)

    private fun income(id: Long, amount: Long, date: LocalDate, sourceId: Long? = 1, currency: String = "BOB") =
        Movement(id, MovementType.INCOME, amount, currency, date, incomeSourceId = sourceId)

    @Test
    fun `ejemplo de 18 BOB por 42 horas preve 756 BOB`() {
        assertEquals(1_800L, hourly.hourlyRateMinor)
        assertEquals(42 * 60, hourly.plannedMinutesPerMonth)
        assertEquals(75_600L, IncomePlanner.monthlyProjection(hourly))
        assertEquals("BOB", hourly.currencyCode)
    }

    @Test
    fun `editar tarifa y horas cambia la prevision`() {
        val edited = hourly.copy(hourlyRateMinor = 20_00, plannedMinutesPerMonth = 40 * 60)
        assertEquals(80_000L, IncomePlanner.monthlyProjection(edited))
    }

    @Test
    fun `fracciones de hora se redondean al centavo`() {
        // 18,00 × 42,5 h = 765,00
        assertEquals(76_500L, IncomePlanner.amountForMinutes(1_800, 2_550))
        // 10,01 × 0,5 h = 5,005 -> 5,01 (mitad hacia arriba)
        assertEquals(501L, IncomePlanner.amountForMinutes(1_001, 30))
        // 10,00 × 1 min = 0,1666... -> 0,17
        assertEquals(17L, IncomePlanner.amountForMinutes(1_000, 1))
    }

    @Test
    fun `fijo preve su importe y variable no preve nada`() {
        val fixed = IncomeSource(2, "Sueldo", IncomeKind.FIXED, "BOB", fixedAmountMinor = 300_000)
        val variable = IncomeSource(3, "Ventas", IncomeKind.VARIABLE, "BOB")
        assertEquals(300_000L, IncomePlanner.monthlyProjection(fixed))
        assertNull(IncomePlanner.monthlyProjection(variable))
        assertNull(IncomePlanner.monthlyProjection(hourly.copy(hourlyRateMinor = null)))
    }

    @Test
    fun `lo previsto no es dinero disponible`() {
        // Sin movimientos reales el disponible es cero aunque haya 756 BOB previstos.
        assertEquals(CurrencyAmounts.of("BOB" to 75_600), IncomePlanner.projectedTotal(listOf(hourly)))
        assertTrue(Ledger.available(emptyList()).isEmpty)
    }

    @Test
    fun `previsto y recibido se distinguen`() {
        val movements = listOf(
            income(1, 30_000, LocalDate.of(2026, 10, 10)),
            income(2, 20_000, LocalDate.of(2026, 10, 20)),
            income(3, 99_999, LocalDate.of(2026, 9, 30)), // otro mes
            income(4, 5_000, LocalDate.of(2026, 10, 5), sourceId = 9), // otra fuente
            income(5, 7_000, LocalDate.of(2026, 10, 5), currency = "USD"), // otra moneda
        )
        val progress = IncomePlanner.progress(listOf(hourly), movements, october).single()
        assertEquals(75_600L, progress.projectedMinor)
        assertEquals(50_000L, progress.receivedMinor)
        assertEquals(25_600L, progress.pendingMinor)
        assertEquals(CurrencyAmounts.of("BOB" to 55_000, "USD" to 7_000), Ledger.periodTotals(movements, october).income)
    }

    @Test
    fun `recibir mas de lo previsto no deja pendiente negativo`() {
        val movements = listOf(income(1, 80_000, LocalDate.of(2026, 10, 10)))
        assertEquals(0L, IncomePlanner.progress(listOf(hourly), movements, october).single().pendingMinor)
    }

    @Test
    fun `fuentes inactivas no cuentan en la prevision`() {
        assertTrue(IncomePlanner.projectedTotal(listOf(hourly.copy(active = false))).isEmpty)
        assertTrue(IncomePlanner.progress(listOf(hourly.copy(active = false)), emptyList(), october).isEmpty())
    }

    @Test
    fun `convierte horas escritas a minutos`() {
        assertEquals(2_520, IncomePlanner.parseHoursToMinutes("42"))
        assertEquals(2_550, IncomePlanner.parseHoursToMinutes(" 42,5 "))
        assertEquals(2_535, IncomePlanner.parseHoursToMinutes("42.25"))
        assertNull(IncomePlanner.parseHoursToMinutes(""))
        assertNull(IncomePlanner.parseHoursToMinutes("-3"))
        assertNull(IncomePlanner.parseHoursToMinutes("1.000"))
        assertNull(IncomePlanner.parseHoursToMinutes("abc"))
    }

    @Test
    fun `valida la fuente por horas`() {
        val ok = FinanceValidators.validateIncomeSource("Clases", IncomeKind.HOURLY, "", "18", "42")
        assertFalse(ok.hasErrors)
        val bad = FinanceValidators.validateIncomeSource(" ", IncomeKind.HOURLY, "", "0", "800")
        assertEquals("Escribe un nombre", bad.name)
        assertEquals("El importe debe ser mayor que cero", bad.rate)
        assertEquals("Un mes no tiene tantas horas", bad.hours)
        assertNull(bad.amount)
    }
}
