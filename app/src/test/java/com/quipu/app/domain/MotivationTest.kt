package com.quipu.app.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotivationTest {
    private val today = LocalDate.of(2026, 10, 25)
    private val goal = SavingsGoal(1, "Viaje", "BOB", targetMinor = 100_000)

    private fun deposit(id: Long, amount: Long, date: LocalDate = today) =
        Movement(id, MovementType.SAVING_DEPOSIT, amount, "BOB", date, goalId = 1)

    @Test
    fun `objetivo cumplido tiene prioridad`() {
        val msg = Motivation.pick(listOf(goal), listOf(deposit(1, 100_000)), today)
        assertEquals(MotivationReason.GOAL_REACHED, msg.reason)
        assertTrue(msg.text.contains("Viaje"))
    }

    @Test
    fun `hito mas alto alcanzado`() {
        val msg = Motivation.pick(listOf(goal), listOf(deposit(1, 60_000, today.minusMonths(2))), today)
        assertEquals(MotivationReason.GOAL_MILESTONE, msg.reason)
        assertTrue(msg.text.contains("50 %"))
    }

    @Test
    fun `ahorro del mes y balance positivo`() {
        val saved = Motivation.pick(listOf(goal), listOf(deposit(1, 1_000)), today)
        assertEquals(MotivationReason.SAVED_THIS_MONTH, saved.reason)

        val movements = listOf(
            Movement(1, MovementType.INCOME, 10_000, "BOB", today),
            Movement(2, MovementType.EXPENSE, 4_000, "BOB", today),
        )
        assertEquals(MotivationReason.EARNED_MORE_THAN_SPENT, Motivation.pick(emptyList(), movements, today).reason)
    }

    @Test
    fun `mensaje generico estable por dia`() {
        val a = Motivation.pick(emptyList(), emptyList(), today)
        assertEquals(MotivationReason.GENERIC, a.reason)
        assertEquals(a, Motivation.pick(emptyList(), emptyList(), today))
    }
}
