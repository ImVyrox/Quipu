package com.quipu.app.ui

import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatsTest {
    @Test
    fun `horas en formato corto`() {
        assertEquals("42 h", Formats.hours(42 * 60))
        assertEquals("42,5 h", Formats.hours(2_550))
        assertEquals("42,25 h", Formats.hours(2_535))
        assertEquals("0 h", Formats.hours(0))
    }

    @Test
    fun `mes con mayuscula inicial`() {
        assertEquals("Octubre 2026", Formats.month(YearMonth.of(2026, 10)))
    }
}
