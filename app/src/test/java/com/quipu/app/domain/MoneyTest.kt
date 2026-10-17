package com.quipu.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {

    @Test
    fun parse_acceptsIntegersAndBothDecimalSeparators() {
        assertEquals(15000L, Money.parseToMinor("150"))
        assertEquals(15050L, Money.parseToMinor("150,50"))
        assertEquals(15050L, Money.parseToMinor("150.50"))
        assertEquals(150L, Money.parseToMinor("1.5"))
        assertEquals(5L, Money.parseToMinor("0,05"))
        assertEquals(1250L, Money.parseToMinor("  12,5  "))
    }

    @Test
    fun parse_rejectsInvalidText() {
        assertNull(Money.parseToMinor(""))
        assertNull(Money.parseToMinor("abc"))
        assertNull(Money.parseToMinor("-5"))
        assertNull(Money.parseToMinor("1.234,50"))
        assertNull(Money.parseToMinor("12,345"))
        assertNull(Money.parseToMinor("1e3"))
        assertNull(Money.parseToMinor(",5"))
    }

    @Test
    fun parse_avoidsFloatingPointErrors() {
        assertEquals(1010L, Money.parseToMinor("10.10"))
        assertEquals(2999L, Money.parseToMinor("29.99"))
    }

    @Test
    fun format_usesGroupingAndTwoDecimals() {
        assertEquals("Bs 0,00", Money.format(0, AppCurrencies.BOB))
        assertEquals("Bs 0,05", Money.format(5, AppCurrencies.BOB))
        assertEquals("Bs 150,50", Money.format(15050, AppCurrencies.BOB))
        assertEquals("Bs 1.234,50", Money.format(123450, AppCurrencies.BOB))
        assertEquals("Bs 1.234.567,89", Money.format(123456789, AppCurrencies.BOB))
    }

    @Test
    fun format_handlesNegativesAndOtherCurrencies() {
        assertEquals("-Bs 20,00", Money.format(-2000, AppCurrencies.BOB))
        assertEquals("US$ 10,00", Money.format(1000, AppCurrencies.byCode("USD")))
    }

    @Test
    fun currency_defaultsToBolivianoWhenUnknown() {
        assertEquals("BOB", AppCurrencies.byCode(null).code)
        assertEquals("BOB", AppCurrencies.byCode("XXX").code)
        assertEquals("EUR", AppCurrencies.byCode("EUR").code)
    }

    @Test
    fun toInputText_roundTripsThroughParse() {
        assertEquals("12,50", Money.toInputText(1250))
        assertEquals("0,05", Money.toInputText(5))
        assertEquals(1250L, Money.parseToMinor(Money.toInputText(1250)))
    }
}
