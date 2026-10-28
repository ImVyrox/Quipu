package com.quipu.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quipu.app.domain.ExpenseCategory
import com.quipu.app.domain.IncomeKind
import com.quipu.app.domain.IncomePlanner
import com.quipu.app.domain.IncomeSource
import com.quipu.app.domain.Ledger
import com.quipu.app.domain.Movement
import com.quipu.app.domain.MovementType
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Altas, ediciones, eliminaciones y persistencia de ingresos y gastos en Room. */
@RunWith(AndroidJUnit4::class)
class MoneyRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "money-repository-test.db"
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

    private fun expense(amount: Long, category: ExpenseCategory = ExpenseCategory.FOOD, description: String = "") =
        Movement(0, MovementType.EXPENSE, amount, "BOB", day, description = description, category = category)

    private suspend fun all() = repo.observeMovements().first()

    @Test
    fun crearEditarYEliminarGasto() = runBlocking {
        val id = repo.saveMovement(expense(4_500, description = "Almuerzo"))
        val saved = all().single()
        assertEquals(id, saved.id)
        assertEquals(4_500L, saved.amountMinor)
        assertEquals(ExpenseCategory.FOOD, saved.category)
        assertEquals("Almuerzo", saved.description)

        repo.saveMovement(saved.copy(amountMinor = 5_000, category = ExpenseCategory.HEALTH, description = ""))
        val edited = all().single()
        assertEquals(5_000L, edited.amountMinor)
        assertEquals(ExpenseCategory.HEALTH, edited.category)
        assertEquals("", edited.description)

        repo.deleteMovement(id)
        assertTrue(all().isEmpty())
    }

    @Test
    fun ingresoPorHorasPendienteYCobrado() = runBlocking {
        val sourceId = repo.saveIncomeSource(IncomePlanner.EXAMPLE_HOURLY)
        val income = Movement(
            0, MovementType.INCOME, IncomePlanner.amountForMinutes(1_800, 6 * 60), "BOB", day,
            pending = true, incomeSourceId = sourceId, workDate = day, workedMinutes = 6 * 60,
        )
        val id = repo.saveMovement(income)
        assertEquals(0L, Ledger.available(all())["BOB"])

        repo.markSettled(id, day.plusDays(3))
        val settled = all().single()
        assertFalse(settled.pending)
        assertEquals(day.plusDays(3), settled.date)
        assertEquals(day, settled.workDate)
        assertEquals(360, settled.workedMinutes)
        assertEquals(10_800L, Ledger.available(all())["BOB"])
    }

    @Test
    fun ingresoFijoNoSeDuplicaEnElMismoMes() = runBlocking {
        val sourceId = repo.saveIncomeSource(IncomeSource(0, "Sueldo", IncomeKind.FIXED, "BOB", fixedAmountMinor = 300_000))
        val october = Movement(0, MovementType.INCOME, 300_000, "BOB", day, incomeSourceId = sourceId)
        val firstId = repo.saveMovement(october)
        assertEquals(YearMonth.of(2026, 10), all().single().period)

        try {
            repo.saveMovement(october.copy(date = day.minusDays(10)))
            fail("Debía rechazar el segundo ingreso de octubre")
        } catch (e: DuplicateIncomeException) {
            assertEquals(YearMonth.of(2026, 10), e.month)
        }
        // Editar el mismo ingreso sí se permite, y el mes siguiente está libre.
        repo.saveMovement(all().single().copy(amountMinor = 310_000))
        repo.saveMovement(october.copy(date = day.plusMonths(1)))
        assertEquals(2, all().size)
        assertEquals(310_000L, all().first { it.id == firstId }.amountMinor)
    }

    @Test
    fun fuenteConIngresosSeArchivaYSinIngresosSeBorra() = runBlocking {
        val used = repo.saveIncomeSource(IncomeSource(0, "Ventas", IncomeKind.VARIABLE, "BOB"))
        val unused = repo.saveIncomeSource(IncomeSource(0, "Otra", IncomeKind.VARIABLE, "BOB"))
        repo.saveMovement(Movement(0, MovementType.INCOME, 1_000, "BOB", day, incomeSourceId = used))

        assertTrue(repo.removeIncomeSource(used))
        assertFalse(repo.removeIncomeSource(unused))

        val sources = repo.observeIncomeSources().first()
        assertEquals(listOf(used), sources.map { it.id })
        assertFalse(sources.single().active)
        assertEquals(1, all().size) // el historial se conserva
    }

    @Test
    fun dineroDeclaradoNoCreaMovimientos() = runBlocking {
        repo.saveMovement(Movement(0, MovementType.INCOME, 10_000, "BOB", day))
        repo.declareBalance("BOB", 12_000, day)
        assertEquals(1, all().size)
        val declared = repo.observeDeclaredBalances().first().single()
        assertEquals(2_000L, Ledger.compareDeclared(declared, all()).differenceMinor)
        repo.clearDeclaredBalance("BOB")
        assertTrue(repo.observeDeclaredBalances().first().isEmpty())
    }

    @Test
    fun losDatosPersistenAlReabrirLaBase() = runBlocking {
        repo.saveMovement(expense(1_200, ExpenseCategory.TRANSPORT))
        repo.saveMovement(Movement(0, MovementType.EXPENSE, 3_000, "USD", day, category = ExpenseCategory.OTHER))
        db.close()

        db = open()
        repo = MoneyRepository(db) { 1_000L }
        val reopened = all()
        assertEquals(2, reopened.size)
        assertEquals(-1_200L, Ledger.available(reopened)["BOB"])
        assertEquals(-3_000L, Ledger.available(reopened)["USD"])
    }

    @Test
    fun datosDeDemostracionSeBorranSinTocarLosReales() = runBlocking {
        repo.saveMovement(expense(999))
        repo.loadDemoData(day)
        assertTrue(repo.observeDemoCount().first() > 0)
        repo.removeDemoData()
        assertEquals(0, repo.observeDemoCount().first())
        assertEquals(999L, all().single().amountMinor)
        assertNull(all().single().incomeSourceId)
    }
}
