package com.quipu.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quipu.app.domain.ConfirmResult
import com.quipu.app.domain.ExpenseCategory
import com.quipu.app.domain.Ledger
import com.quipu.app.domain.Movement
import com.quipu.app.domain.MovementType
import com.quipu.app.domain.RecurringFrequency
import com.quipu.app.domain.RecurringPayment
import com.quipu.app.domain.RecurringPlanner
import com.quipu.app.domain.RecurringReminder
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

/** Pagos recurrentes guardados en Room: alta, edición, pausa, confirmación sin duplicados y persistencia. */
@RunWith(AndroidJUnit4::class)
class RecurringRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "recurring-repository-test.db"
    private val november = YearMonth.of(2026, 11)
    private lateinit var db: QuipuDatabase
    private lateinit var repo: MoneyRepository

    private val claude = RecurringPayment(
        id = 0,
        name = "Claude",
        amountMinor = 2_000,
        currencyCode = "USD",
        dayOfMonth = 5,
        startDate = LocalDate.of(2026, 11, 1),
        category = ExpenseCategory.SUBSCRIPTIONS,
        description = "Plan Pro",
        reminder = RecurringReminder(enabled = true, leadDays = 2, minuteOfDay = 20 * 60),
    )

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

    private suspend fun movements() = repo.observeMovements().first()

    @Test
    fun guardaYLeeTodosLosCampos() = runBlocking {
        val id = repo.saveRecurring(claude.copy(frequency = RecurringFrequency.QUARTERLY))
        val stored = repo.observeRecurring().first().single()
        assertEquals(claude.copy(id = id, frequency = RecurringFrequency.QUARTERLY), stored)
    }

    @Test
    fun persisteTrasCerrarYAbrirLaBase() = runBlocking {
        val id = repo.saveRecurring(claude)
        repo.confirmRecurring(id, november, LocalDate.of(2026, 11, 6), 2_000)
        db.close()
        db = open()
        repo = MoneyRepository(db) { 1_000L }
        assertEquals("Claude", repo.observeRecurring().first().single().name)
        val paid = movements().single()
        assertEquals(id, paid.recurringPaymentId)
        assertEquals(november, paid.period)
        assertEquals(LocalDate.of(2026, 11, 6), paid.date)
    }

    @Test
    fun confirmarDosVecesCreaUnSoloGasto() = runBlocking {
        val id = repo.saveRecurring(claude)
        val first = repo.confirmRecurring(id, november, LocalDate.of(2026, 11, 5), 2_000)
        assertTrue(first is ConfirmResult.Created)
        val second = repo.confirmRecurring(id, november, LocalDate.of(2026, 11, 6), 2_000)
        assertTrue(second is ConfirmResult.AlreadyPaid)
        val list = movements()
        assertEquals(1, list.size)
        assertEquals("USD", list.single().currencyCode)
        assertEquals(ExpenseCategory.SUBSCRIPTIONS, list.single().category)
        // Una sola salida de dinero, en USD, sin tocar el BOB.
        assertEquals(-2_000L, Ledger.available(list)["USD"])
        assertEquals(0L, Ledger.available(list)["BOB"])
    }

    @Test
    fun elIndiceUnicoImpideDosPagosDelMismoMes() = runBlocking {
        val id = repo.saveRecurring(claude)
        repo.confirmRecurring(id, november, LocalDate.of(2026, 11, 5), 2_000)
        // Aunque alguien se saltara la validación, la base rechaza el segundo pago del mismo mes.
        val dup = Movement(0, MovementType.EXPENSE, 2_000, "USD", LocalDate.of(2026, 11, 5), recurringPaymentId = id, period = november)
        try {
            db.movementDao().insert(dup.toEntity(createdAtEpochSecond = 0))
            fail("Se esperaba una violación del índice único")
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            assertEquals(1, movements().size)
        }
    }

    @Test
    fun pendienteNoReduceElSaldoHastaConfirmar() = runBlocking {
        val id = repo.saveRecurring(claude)
        repo.saveMovement(Movement(0, MovementType.INCOME, 10_000, "USD", LocalDate.of(2026, 11, 1)))
        assertEquals(10_000L, Ledger.available(movements())["USD"])
        repo.confirmRecurring(id, november, LocalDate.of(2026, 11, 5), 2_000)
        assertEquals(8_000L, Ledger.available(movements())["USD"])
    }

    @Test
    fun noConfirmaEnPausaNiFueraDeVigencia() = runBlocking {
        val id = repo.saveRecurring(claude)
        assertEquals(ConfirmResult.NotDue, repo.confirmRecurring(id, YearMonth.of(2026, 10), LocalDate.of(2026, 10, 5), 2_000))
        repo.setRecurringActive(id, false)
        assertFalse(repo.observeRecurring().first().single().active)
        assertEquals(ConfirmResult.NotDue, repo.confirmRecurring(id, november, LocalDate.of(2026, 11, 5), 2_000))
        assertTrue(movements().isEmpty())
    }

    @Test
    fun editarConservaLaPausa() = runBlocking {
        val id = repo.saveRecurring(claude)
        repo.setRecurringActive(id, false)
        repo.saveRecurring(claude.copy(id = id, amountMinor = 2_500, active = true))
        val stored = repo.observeRecurring().first().single()
        assertEquals(2_500L, stored.amountMinor)
        assertFalse(stored.active)
    }

    @Test
    fun vincularUnGastoManualEvitaContarloDosVeces() = runBlocking {
        val id = repo.saveRecurring(claude)
        val manualId = repo.saveMovement(
            Movement(0, MovementType.EXPENSE, 2_000, "USD", LocalDate.of(2026, 11, 4), "Claude", ExpenseCategory.SUBSCRIPTIONS),
        )
        val payment = repo.observeRecurring().first().single()
        val occurrence = RecurringPlanner.schedule(payment, movements(), LocalDate.of(2026, 11, 1)).next!!
        assertEquals(listOf(manualId), RecurringPlanner.manualMatches(occurrence, movements()).map { it.id })

        repo.linkRecurring(manualId, id, november)
        assertTrue(repo.confirmRecurring(id, november, LocalDate.of(2026, 11, 5), 2_000) is ConfirmResult.AlreadyPaid)
        assertEquals(1, movements().size)
        assertEquals(-2_000L, Ledger.available(movements())["USD"])
    }

    @Test
    fun editarElGastoPagadoConservaElVinculo() = runBlocking {
        val id = repo.saveRecurring(claude)
        val created = repo.confirmRecurring(id, november, LocalDate.of(2026, 11, 5), 2_000) as ConfirmResult.Created
        // El formulario de gasto no conoce el vínculo: guardar una edición no debe perderlo.
        repo.saveMovement(created.movement.copy(amountMinor = 2_100, recurringPaymentId = null, period = null, pending = true))
        val stored = movements().single()
        assertEquals(2_100L, stored.amountMinor)
        assertFalse(stored.pending)
        assertEquals(id, stored.recurringPaymentId)
        assertEquals(november, stored.period)
        // Y un gasto nuevo no puede colarse como pago recurrente.
        repo.saveMovement(Movement(0, MovementType.EXPENSE, 2_000, "USD", LocalDate.of(2026, 12, 5), recurringPaymentId = id, period = YearMonth.of(2026, 12)))
        assertNull(movements().first { it.date == LocalDate.of(2026, 12, 5) }.recurringPaymentId)
    }

    @Test
    fun eliminarElPagoConservaSusGastos() = runBlocking {
        val id = repo.saveRecurring(claude)
        repo.confirmRecurring(id, november, LocalDate.of(2026, 11, 5), 2_000)
        repo.deleteRecurring(id)
        assertTrue(repo.observeRecurring().first().isEmpty())
        assertEquals(1, movements().size)
    }

    @Test
    fun deshacerElPagoDejaElMesPendiente() = runBlocking {
        val id = repo.saveRecurring(claude)
        val created = repo.confirmRecurring(id, november, LocalDate.of(2026, 11, 5), 2_000) as ConfirmResult.Created
        repo.deleteMovement(created.movement.id)
        assertTrue(repo.confirmRecurring(id, november, LocalDate.of(2026, 11, 7), 2_000) is ConfirmResult.Created)
        assertEquals(1, movements().size)
    }

    @Test
    fun datosDeDemostracionSeBorranSinTocarLosReales() = runBlocking {
        repo.saveRecurring(claude)
        repo.loadDemoData(LocalDate.of(2026, 11, 20))
        assertEquals(2, repo.observeRecurring().first().size)
        repo.removeDemoData()
        assertEquals(listOf("Claude"), repo.observeRecurring().first().map { it.name })
    }
}
