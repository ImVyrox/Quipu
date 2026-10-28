package com.quipu.app.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Las migraciones conservan tareas, préstamos y abonos y dejan el esquema que Room espera. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        QuipuDatabase::class.java,
    )

    @Test
    fun migra3a4SinPerderDatos() {
        helper.createDatabase(dbName, 3).apply {
            execSQL(
                "INSERT INTO tasks (id, title, description, dueLocalEpochSecond, completed, isDemo, isDaily, lastCompletedEpochDay) " +
                    "VALUES (1, 'Ir al gimnasio', '', 0, 0, 0, 1, 20000)",
            )
            execSQL(
                "INSERT INTO debts (id, type, personName, personKey, amountMinor, recordEpochDay, dueEpochDay, note, status, settledEpochDay, isDemo) " +
                    "VALUES (1, 'OWED_TO_ME', 'Ana', 'ana', 30000, 20000, NULL, 'taxi', 'PENDING', NULL, 0)",
            )
            execSQL("INSERT INTO debt_payments (id, debtId, amountMinor, epochDay) VALUES (1, 1, 20000, 20001)")
            close()
        }

        // Valida que el esquema migrado sea idéntico a schemas/.../4.json.
        helper.runMigrationsAndValidate(dbName, 4, true, QuipuDatabase.MIGRATION_3_4).close()

        val db = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            QuipuDatabase::class.java,
            dbName,
        ).addMigrations(*QuipuDatabase.ALL_MIGRATIONS).build()
        try {
            val repo = QuipuRepository(db, SettingsStore(InstrumentationRegistry.getInstrumentation().targetContext))
            runBlocking {
                val task = repo.observeTasks().first().single()
                assertEquals("Ir al gimnasio", task.title)
                assertTrue(task.isDaily)
                val debt = repo.observeDebts().first().single()
                assertEquals(30_000L, debt.amountMinor)
                assertEquals(10_000L, debt.remainingMinor)
                assertTrue(MoneyRepository(db).observeMovements().first().isEmpty())
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun migra4a5ConservaMovimientos() {
        helper.createDatabase(dbName, 4).apply {
            execSQL(
                "INSERT INTO movements (id, type, amountMinor, currencyCode, epochDay, description, category, pending, " +
                    "incomeSourceId, workEpochDay, workedMinutes, goalId, recurringPaymentId, period, createdAtEpochSecond, isDemo) " +
                    "VALUES (1, 'EXPENSE', 2550, 'BOB', 20000, 'Almuerzo', 'FOOD', 0, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0)",
            )
            close()
        }
        helper.runMigrationsAndValidate(dbName, 5, true, QuipuDatabase.MIGRATION_4_5).close()

        val db = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            QuipuDatabase::class.java,
            dbName,
        ).addMigrations(*QuipuDatabase.ALL_MIGRATIONS).build()
        try {
            runBlocking {
                val repo = MoneyRepository(db)
                assertEquals(2_550L, repo.observeMovements().first().single().amountMinor)
                assertTrue(repo.observeGoals().first().isEmpty())
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun migra1aLaUltima() {
        helper.createDatabase(dbName, 1).apply {
            execSQL(
                "INSERT INTO tasks (id, title, description, dueLocalEpochSecond, completed, isDemo) " +
                    "VALUES (1, 'Antigua', '', 0, 1, 0)",
            )
            close()
        }
        helper.runMigrationsAndValidate(dbName, 5, true, *QuipuDatabase.ALL_MIGRATIONS).close()
    }
}
