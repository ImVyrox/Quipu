package com.quipu.app.domain

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidatorsTest {
    private val today = LocalDate.of(2026, 10, 16)

    @Test
    fun task_validWhenTitleDateAndTimePresent() {
        val errors = Validators.validateTask("Pagar luz", "", today, LocalTime.of(9, 0))
        assertFalse(errors.hasErrors)
    }

    @Test
    fun task_requiresTitleDateAndTime() {
        val errors = Validators.validateTask("   ", "", null, null)
        assertNotNull(errors.title)
        assertNotNull(errors.date)
        assertNotNull(errors.time)
        assertTrue(errors.hasErrors)
    }

    @Test
    fun task_limitsLength() {
        val longTitle = "a".repeat(Limits.TITLE_MAX + 1)
        assertNotNull(Validators.validateTask(longTitle, "", today, LocalTime.NOON).title)
        val longDescription = "a".repeat(Limits.DESCRIPTION_MAX + 1)
        assertNotNull(Validators.validateTask("ok", longDescription, today, LocalTime.NOON).title)
    }

    @Test
    fun debt_validWithPositiveAmount() {
        val errors = Validators.validateDebt("Ana", "150,50", today, null, "")
        assertFalse(errors.hasErrors)
    }

    @Test
    fun debt_requiresName() {
        assertNotNull(Validators.validateDebt("   ", "10", today, null, "").person)
    }

    @Test
    fun debt_amountMustBePositiveAndWellFormed() {
        assertNotNull(Validators.validateDebt("Ana", "", today, null, "").amount)
        assertNotNull(Validators.validateDebt("Ana", "0", today, null, "").amount)
        assertNotNull(Validators.validateDebt("Ana", "0,00", today, null, "").amount)
        assertNotNull(Validators.validateDebt("Ana", "-5", today, null, "").amount)
        assertNotNull(Validators.validateDebt("Ana", "abc", today, null, "").amount)
        assertNull(Validators.validateDebt("Ana", "0,01", today, null, "").amount)
    }

    @Test
    fun debt_dueDateCannotPrecedeRecordDate() {
        val before = today.minusDays(1)
        assertNotNull(Validators.validateDebt("Ana", "10", today, before, "").dueDate)
        assertNull(Validators.validateDebt("Ana", "10", today, today, "").dueDate)
        assertNull(Validators.validateDebt("Ana", "10", today, today.plusDays(5), "").dueDate)
    }

    @Test
    fun debt_requiresRecordDate() {
        assertNotNull(Validators.validateDebt("Ana", "10", null, null, "").recordDate)
    }

    @Test
    fun debt_errorsAreIndependent() {
        val errors = Validators.validateDebt("", "x", today, null, "")
        assertNotNull(errors.person)
        assertNotNull(errors.amount)
        assertNull(errors.recordDate)
        assertEquals(null, errors.note)
    }
}
