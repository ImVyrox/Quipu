package com.quipu.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quipu.app.domain.GoalStatus
import com.quipu.app.domain.Ledger
import com.quipu.app.domain.Movement
import com.quipu.app.domain.MovementType
import com.quipu.app.domain.SavingsGoal
import com.quipu.app.domain.TransferError
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Objetivos de ahorro guardados en Room: aportaciones, retiros, estados, borrado y persistencia. */
@RunWith(AndroidJUnit4::class)
class SavingsRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "savings-repository-test.db"
    private val day = LocalDate.of(2026, 10, 25)
    private lateinit var db: QuipuDatabase
    private lateinit var repo: MoneyRepository

    private fun open(): QuipuDatabase =
        Room.databaseBuilder(context, QuipuDatabase::class.java, dbName)
            .addMigrations(*QuipuDatabase.ALL_MIGRATIONS)
            .build()

    @Before
    fun setUp() {
        context.deleteDatabase(dbName)
        db = open()
        repo = MoneyRepository(db) { 1_000L }
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
    }

    private suspend fun newGoal(target: Long = 300_000) =
        repo.saveGoal(SavingsGoal(0, "Laptop", "BOB", target, createdDate = day))

    private suspend fun income(amount: Long) =
        repo.saveMovement(Movement(0, MovementType.INCOME, amount, "BOB", day))

    private suspend fun movements() = repo.observeMovements().first()

    private suspend fun expectRejected(error: TransferError, block: suspend () -> Unit) {
        try {
            block()
            fail("Debía rechazarse con $error")
        } catch (e: SavingRejectedException) {
            assertEquals(error, e.error)
        }
    }

    @Test
    fun aportacionYRetiroConservanElTotal() = runBlocking {
        income(100_000)
        val goalId = newGoal()
        val total = Ledger.overview(movements()).total

        repo.addSaving(goalId, MovementType.SAVING_DEPOSIT, 40_000, day)
        repo.addSaving(goalId, MovementType.SAVING_WITHDRAWAL, 15_000, day)

        val overview = Ledger.overview(movements())
        assertEquals(75_000L, overview.available["BOB"])
        assertEquals(25_000L, overview.saved["BOB"])
        assertEquals(total, overview.total)
    }

    @Test
    fun rechazaSaldosNegativos() = runBlocking {
        income(10_000)
        val goalId = newGoal()
        expectRejected(TransferError.INSUFFICIENT_AVAILABLE) { repo.addSaving(goalId, MovementType.SAVING_DEPOSIT, 10_001, day) }
        repo.addSaving(goalId, MovementType.SAVING_DEPOSIT, 10_000, day)
        expectRejected(TransferError.INSUFFICIENT_SAVINGS) { repo.addSaving(goalId, MovementType.SAVING_WITHDRAWAL, 10_001, day) }
        assertEquals(1, movements().count { it.type.isSaving })
    }

    @Test
    fun ahorroInicialExplicitoYUnico() = runBlocking {
        val goalId = newGoal()
        repo.addSaving(goalId, MovementType.SAVING_OPENING, 50_000, day, "Ya lo tenía")
        expectRejected(TransferError.OPENING_ALREADY_EXISTS) { repo.addSaving(goalId, MovementType.SAVING_OPENING, 1, day) }
        val overview = Ledger.overview(movements())
        assertEquals(0L, overview.available["BOB"])
        assertEquals(50_000L, overview.saved["BOB"])
    }

    @Test
    fun pausarCompletarYReactivar() = runBlocking {
        income(10_000)
        val goalId = newGoal(target = 5_000)
        repo.addSaving(goalId, MovementType.SAVING_DEPOSIT, 5_000, day)

        repo.setGoalStatus(goalId, GoalStatus.PAUSED, day)
        expectRejected(TransferError.GOAL_NOT_ACTIVE) { repo.addSaving(goalId, MovementType.SAVING_DEPOSIT, 100, day) }

        repo.setGoalStatus(goalId, GoalStatus.COMPLETED, day)
        val completed = repo.observeGoals().first().single()
        assertEquals(GoalStatus.COMPLETED, completed.status)
        assertEquals(day, completed.completedDate)
        assertTrue(Ledger.goalProgress(completed, movements()).reached)
        // Completado: se puede retirar el dinero para usarlo.
        repo.addSaving(goalId, MovementType.SAVING_WITHDRAWAL, 5_000, day)

        repo.setGoalStatus(goalId, GoalStatus.ACTIVE, day)
        val active = repo.observeGoals().first().single()
        assertEquals(GoalStatus.ACTIVE, active.status)
        assertNull(active.completedDate)
    }

    @Test
    fun editarConservaEstadoYBloqueaMonedaConMovimientos() = runBlocking {
        income(10_000)
        val goalId = newGoal()
        repo.setGoalStatus(goalId, GoalStatus.PAUSED, day)
        val stored = repo.observeGoals().first().single()
        repo.saveGoal(stored.copy(name = "Laptop nueva", targetMinor = 400_000, description = "Para estudiar", status = GoalStatus.ACTIVE))
        val edited = repo.observeGoals().first().single()
        assertEquals("Laptop nueva", edited.name)
        assertEquals(400_000L, edited.targetMinor)
        assertEquals(GoalStatus.PAUSED, edited.status) // el estado solo cambia con setGoalStatus

        repo.setGoalStatus(goalId, GoalStatus.ACTIVE, day)
        repo.addSaving(goalId, MovementType.SAVING_DEPOSIT, 1_000, day)
        try {
            repo.saveGoal(edited.copy(currencyCode = "USD"))
            fail("No debía cambiar la moneda")
        } catch (e: IllegalStateException) {
            assertEquals("BOB", repo.observeGoals().first().single().currencyCode)
        }
    }

    @Test
    fun eliminarObjetivoDevuelveLoAhorrado() = runBlocking {
        income(100_000)
        val goalId = newGoal()
        repo.addSaving(goalId, MovementType.SAVING_OPENING, 20_000, day)
        repo.addSaving(goalId, MovementType.SAVING_DEPOSIT, 30_000, day)
        val totalBefore = Ledger.overview(movements()).total

        assertEquals(50_000L, repo.deleteGoal(goalId, day))
        assertTrue(repo.observeGoals().first().isEmpty())
        val overview = Ledger.overview(movements())
        assertEquals(120_000L, overview.available["BOB"])
        assertTrue(overview.saved.nonZero().isEmpty)
        assertEquals(totalBefore, overview.total)
        assertEquals(3, movements().count { it.goalId == goalId }) // historial conservado

        // El historial de un objetivo eliminado no se puede cambiar.
        val closing = movements().first { it.goalId == goalId && it.type == MovementType.SAVING_WITHDRAWAL }
        expectRejected(TransferError.GOAL_DELETED) { repo.deleteMovement(closing.id) }
        assertEquals(totalBefore, Ledger.overview(movements()).total)
    }

    @Test
    fun borrarAportacionYaRetiradaSeRechaza() = runBlocking {
        income(50_000)
        val goalId = newGoal()
        val deposit = repo.addSaving(goalId, MovementType.SAVING_DEPOSIT, 10_000, day)
        val withdrawal = repo.addSaving(goalId, MovementType.SAVING_WITHDRAWAL, 8_000, day)
        expectRejected(TransferError.INSUFFICIENT_SAVINGS) { repo.deleteMovement(deposit) }
        repo.deleteMovement(withdrawal)
        repo.deleteMovement(deposit)
        assertEquals(50_000L, Ledger.available(movements())["BOB"])
    }

    @Test
    fun persisteAlReabrir() = runBlocking {
        income(20_000)
        val goalId = newGoal()
        repo.addSaving(goalId, MovementType.SAVING_DEPOSIT, 12_345, day)
        db.close()

        db = open()
        repo = MoneyRepository(db) { 1_000L }
        val goal = repo.observeGoals().first().single()
        assertEquals("Laptop", goal.name)
        assertEquals(day, goal.createdDate)
        assertEquals(12_345L, Ledger.goalBalance(goal.id, movements()))
    }
}
