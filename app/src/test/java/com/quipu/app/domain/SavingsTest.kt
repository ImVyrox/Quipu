package com.quipu.app.domain

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Objetivos de ahorro: aportaciones, retiros, ahorro inicial, estados y balance general. */
class SavingsTest {
    private val today = LocalDate.of(2026, 10, 25)
    private val goal = SavingsGoal(1, "Laptop", "BOB", targetMinor = 300_000, createdDate = LocalDate.of(2026, 10, 1))

    private fun income(id: Long, amount: Long, currency: String = "BOB") =
        Movement(id, MovementType.INCOME, amount, currency, today.minusDays(10))

    private fun saving(id: Long, type: MovementType, amount: Long, goalId: Long = 1, date: LocalDate = today) =
        Movement(id, type, amount, "BOB", date, goalId = goalId)

    @Test
    fun `aportar y retirar mueven dinero sin cambiar el total`() {
        val base = listOf(income(1, 100_000))
        val before = Ledger.overview(base)
        val afterDeposit = base + saving(2, MovementType.SAVING_DEPOSIT, 30_000)
        val afterWithdrawal = afterDeposit + saving(3, MovementType.SAVING_WITHDRAWAL, 10_000)

        val o1 = Ledger.overview(afterDeposit)
        assertEquals(70_000L, o1.available["BOB"])
        assertEquals(30_000L, o1.saved["BOB"])
        assertEquals(before.total, o1.total)

        val o2 = Ledger.overview(afterWithdrawal)
        assertEquals(80_000L, o2.available["BOB"])
        assertEquals(20_000L, o2.saved["BOB"])
        assertEquals(before.total, o2.total)
        assertEquals(20_000L, Ledger.goalBalance(1, afterWithdrawal))
    }

    @Test
    fun `las aportaciones no son ingresos ni gastos`() {
        val movements = listOf(
            income(1, 100_000),
            saving(2, MovementType.SAVING_DEPOSIT, 30_000),
            saving(3, MovementType.SAVING_WITHDRAWAL, 5_000),
            saving(4, MovementType.SAVING_OPENING, 50_000),
        )
        val totals = Ledger.periodTotals(movements, YearMonth.from(today))
        assertEquals(100_000L, totals.income["BOB"])
        assertTrue(totals.expense.isEmpty)
        // El ahorro inicial no salió del disponible del periodo.
        assertEquals(25_000L, totals.savedNet["BOB"])
    }

    @Test
    fun `ahorro inicial suma al ahorro sin tocar el disponible`() {
        val movements = listOf(income(1, 10_000), saving(2, MovementType.SAVING_OPENING, 50_000))
        val overview = Ledger.overview(movements)
        assertEquals(10_000L, overview.available["BOB"])
        assertEquals(50_000L, overview.saved["BOB"])
        assertEquals(60_000L, overview.total["BOB"])
        assertEquals(50_000L, Ledger.goalBalance(1, movements))
    }

    @Test
    fun `solo un ahorro inicial por objetivo`() {
        assertNull(Ledger.validateOpening(50_000, goal, emptyList()))
        val withOpening = listOf(saving(1, MovementType.SAVING_OPENING, 50_000))
        assertEquals(TransferError.OPENING_ALREADY_EXISTS, Ledger.validateOpening(1_000, goal, withOpening))
        // Otro objetivo sí puede tener el suyo.
        assertNull(Ledger.validateOpening(1_000, goal.copy(id = 2), withOpening))
        assertEquals(TransferError.INVALID_AMOUNT, Ledger.validateOpening(0, goal, emptyList()))
    }

    @Test
    fun `no se aporta mas que el disponible ni se retira mas que lo ahorrado`() {
        val movements = listOf(income(1, 50_000), saving(2, MovementType.SAVING_OPENING, 40_000))
        assertNull(Ledger.validateTransfer(MovementType.SAVING_DEPOSIT, 50_000, "BOB", goal, movements))
        assertEquals(
            TransferError.INSUFFICIENT_AVAILABLE,
            Ledger.validateTransfer(MovementType.SAVING_DEPOSIT, 50_001, "BOB", goal, movements),
        )
        assertNull(Ledger.validateTransfer(MovementType.SAVING_WITHDRAWAL, 40_000, "BOB", goal, movements))
        assertEquals(
            TransferError.INSUFFICIENT_SAVINGS,
            Ledger.validateTransfer(MovementType.SAVING_WITHDRAWAL, 40_001, "BOB", goal, movements),
        )
    }

    @Test
    fun `objetivo en pausa o completado no admite aportaciones pero si retiros`() {
        val movements = listOf(income(1, 50_000), saving(2, MovementType.SAVING_DEPOSIT, 10_000))
        listOf(GoalStatus.PAUSED, GoalStatus.COMPLETED).forEach { status ->
            val g = goal.copy(status = status)
            assertEquals(TransferError.GOAL_NOT_ACTIVE, Ledger.validateTransfer(MovementType.SAVING_DEPOSIT, 100, "BOB", g, movements))
            assertNull(Ledger.validateTransfer(MovementType.SAVING_WITHDRAWAL, 10_000, "BOB", g, movements))
        }
    }

    @Test
    fun `objetivo completado y progreso sin errores de redondeo`() {
        val movements = listOf(
            saving(1, MovementType.SAVING_OPENING, 100_000),
            saving(2, MovementType.SAVING_DEPOSIT, 199_999),
        )
        val almost = Ledger.goalProgress(goal, movements)
        assertEquals(99, almost.percent) // 99,9996 % no se redondea a 100
        assertEquals(1L, almost.remainingMinor)
        assertFalse(almost.reached)

        val done = Ledger.goalProgress(goal, movements + saving(3, MovementType.SAVING_DEPOSIT, 1))
        assertEquals(100, done.percent)
        assertEquals(0L, done.remainingMinor)
        assertTrue(done.reached)

        // Pasarse de la meta no rompe el porcentaje ni deja un restante negativo.
        val over = Ledger.goalProgress(goal, movements + saving(4, MovementType.SAVING_DEPOSIT, 50_000))
        assertEquals(100, over.percent)
        assertEquals(0L, over.remainingMinor)
    }

    @Test
    fun `borrar un movimiento no deja el objetivo en negativo`() {
        val deposit = saving(1, MovementType.SAVING_DEPOSIT, 10_000)
        val withdrawal = saving(2, MovementType.SAVING_WITHDRAWAL, 8_000)
        val movements = listOf(income(9, 50_000), deposit, withdrawal)
        assertEquals(TransferError.INSUFFICIENT_SAVINGS, Ledger.validateRemoval(deposit, movements))
        assertNull(Ledger.validateRemoval(withdrawal, movements))
        assertNull(Ledger.validateRemoval(income(9, 50_000), movements))
    }

    @Test
    fun `eliminar un objetivo devuelve lo ahorrado al disponible`() {
        val movements = listOf(
            income(1, 100_000),
            saving(2, MovementType.SAVING_OPENING, 20_000),
            saving(3, MovementType.SAVING_DEPOSIT, 30_000),
        )
        val closing = Ledger.closingWithdrawal(goal, movements, today)!!
        assertEquals(MovementType.SAVING_WITHDRAWAL, closing.type)
        assertEquals(50_000L, closing.amountMinor)

        val after = movements + closing.copy(id = 4)
        assertEquals(0L, Ledger.goalBalance(1, after))
        assertEquals(120_000L, Ledger.overview(after).available["BOB"])
        assertEquals(Ledger.overview(movements).total, Ledger.overview(after).total)
        assertNull(Ledger.closingWithdrawal(goal, after, today))
    }

    @Test
    fun `movimientos repetidos cuentan una vez`() {
        val deposit = saving(2, MovementType.SAVING_DEPOSIT, 30_000)
        val movements = listOf(income(1, 100_000), deposit, deposit.copy())
        assertEquals(30_000L, Ledger.goalBalance(1, movements))
        assertEquals(70_000L, Ledger.available(movements)["BOB"])
    }

    @Test
    fun `historial y filtro de ahorros`() {
        val movements = listOf(
            income(1, 100_000),
            saving(2, MovementType.SAVING_OPENING, 1_000, date = today.minusDays(2)),
            saving(3, MovementType.SAVING_DEPOSIT, 1_000, date = today),
            saving(4, MovementType.SAVING_DEPOSIT, 1_000, goalId = 2),
        )
        assertEquals(listOf(3L, 2L), Ledger.goalHistory(1, movements).map { it.id })
        assertEquals(
            setOf(2L, 3L, 4L),
            Ledger.filter(movements, MovementFilter(kind = MovementKindFilter.SAVINGS)).map { it.id }.toSet(),
        )
    }

    @Test
    fun `objetivo cumplido solo se celebra siete dias`() {
        val reached = listOf(saving(1, MovementType.SAVING_DEPOSIT, 300_000, date = today.minusDays(7)))
        assertEquals(MotivationReason.GOAL_REACHED, Motivation.pick(listOf(goal), reached, today).reason)
        val old = listOf(saving(1, MovementType.SAVING_DEPOSIT, 300_000, date = today.minusDays(8)))
        assertTrue(Motivation.pick(listOf(goal), old, today).reason != MotivationReason.GOAL_REACHED)
        // Un objetivo en pausa no genera mensajes de hito.
        val half = listOf(saving(1, MovementType.SAVING_DEPOSIT, 160_000, date = today.minusMonths(1)))
        assertEquals(MotivationReason.GOAL_MILESTONE, Motivation.pick(listOf(goal), half, today).reason)
        assertTrue(Motivation.pick(listOf(goal.copy(status = GoalStatus.PAUSED)), half, today).reason != MotivationReason.GOAL_MILESTONE)
    }

    @Test
    fun `valida el formulario del objetivo`() {
        val created = LocalDate.of(2026, 10, 1)
        assertFalse(FinanceValidators.validateGoal("Viaje", "1500", null, created).hasErrors)
        val errors = FinanceValidators.validateGoal(" ", "", created.minusDays(1), created)
        assertEquals("Escribe un nombre", errors.name)
        assertEquals("Escribe la meta", errors.target)
        assertEquals("No puede ser anterior a la creación del objetivo", errors.deadline)
        // Una fecha límite ya pasada no impide editar un objetivo antiguo.
        assertNull(FinanceValidators.validateGoal("Viaje", "1500", created.plusDays(5), created).deadline)
    }
}
