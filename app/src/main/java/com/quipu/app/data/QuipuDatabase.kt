package com.quipu.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TaskEntity::class,
        DebtEntity::class,
        PaymentEntity::class,
        MovementEntity::class,
        IncomeSourceEntity::class,
        DeclaredBalanceEntity::class,
        SavingsGoalEntity::class,
        RecurringPaymentEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class QuipuDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun debtDao(): DebtDao
    abstract fun paymentDao(): PaymentDao
    abstract fun movementDao(): MovementDao
    abstract fun incomeSourceDao(): IncomeSourceDao
    abstract fun declaredBalanceDao(): DeclaredBalanceDao
    abstract fun savingsGoalDao(): SavingsGoalDao
    abstract fun recurringPaymentDao(): RecurringPaymentDao

    companion object {
        /**
         * v2: tareas diarias. Solo añade columnas; las tareas existentes quedan como
         * normales (isDaily = 0) y no se borra ni se modifica ningún dato.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN isDaily INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tasks ADD COLUMN lastCompletedEpochDay INTEGER")
            }
        }

        /** v3: abonos (pagos parciales). Solo crea una tabla nueva; no toca los datos existentes. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `debt_payments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`debtId` INTEGER NOT NULL, `amountMinor` INTEGER NOT NULL, `epochDay` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_debt_payments_debtId` ON `debt_payments` (`debtId`)")
            }
        }

        /**
         * v4: ingresos, gastos y dinero declarado. Solo crea tablas nuevas (movements,
         * income_sources, declared_balances) y sus índices; no toca tareas, préstamos ni abonos.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_3_4_SQL.forEach(db::execSQL)
            }
        }

        /**
         * v5: objetivos de ahorro. Solo crea la tabla savings_goals; sus aportaciones y
         * retiros ya caben en movements. No toca ningún dato existente.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_4_5_SQL.forEach(db::execSQL)
            }
        }

        /**
         * v6: pagos recurrentes y suscripciones. Solo crea la tabla recurring_payments; sus
         * pagos ya caben en movements (recurringPaymentId + period, con índice único). No toca
         * ningún dato existente.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_5_6_SQL.forEach(db::execSQL)
            }
        }

        @Volatile
        private var instance: QuipuDatabase? = null

        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)

        fun get(context: Context): QuipuDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    QuipuDatabase::class.java,
                    "quipu.db",
                ).addMigrations(*ALL_MIGRATIONS).build().also { instance = it }
            }
    }
}

/** SQL de la migración 3 -> 4, idéntico al que Room genera en schemas/.../4.json. */
private val MIGRATION_3_4_SQL = listOf(
    "CREATE TABLE IF NOT EXISTS `movements` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, `amountMinor` INTEGER NOT NULL, `currencyCode` TEXT NOT NULL, `epochDay` INTEGER NOT NULL, `description` TEXT NOT NULL, `category` TEXT, `pending` INTEGER NOT NULL, `incomeSourceId` INTEGER, `workEpochDay` INTEGER, `workedMinutes` INTEGER, `goalId` INTEGER, `recurringPaymentId` INTEGER, `period` TEXT, `createdAtEpochSecond` INTEGER NOT NULL, `isDemo` INTEGER NOT NULL DEFAULT 0)",
    "CREATE INDEX IF NOT EXISTS `index_movements_epochDay` ON `movements` (`epochDay`)",
    "CREATE INDEX IF NOT EXISTS `index_movements_goalId` ON `movements` (`goalId`)",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_movements_incomeSourceId_period` ON `movements` (`incomeSourceId`, `period`)",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_movements_recurringPaymentId_period` ON `movements` (`recurringPaymentId`, `period`)",
    "CREATE TABLE IF NOT EXISTS `income_sources` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `kind` TEXT NOT NULL, `currencyCode` TEXT NOT NULL, `fixedAmountMinor` INTEGER, `hourlyRateMinor` INTEGER, `plannedMinutesPerMonth` INTEGER, `active` INTEGER NOT NULL DEFAULT 1, `isDemo` INTEGER NOT NULL DEFAULT 0)",
    "CREATE TABLE IF NOT EXISTS `declared_balances` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `currencyCode` TEXT NOT NULL, `amountMinor` INTEGER NOT NULL, `epochDay` INTEGER NOT NULL)",
    "CREATE INDEX IF NOT EXISTS `index_declared_balances_currencyCode` ON `declared_balances` (`currencyCode`)",
)

/** SQL de la migración 4 -> 5, idéntico al que Room genera en schemas/.../5.json. */
private val MIGRATION_4_5_SQL = listOf(
    "CREATE TABLE IF NOT EXISTS `savings_goals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `targetMinor` INTEGER NOT NULL, `currencyCode` TEXT NOT NULL, `createdEpochDay` INTEGER NOT NULL, `deadlineEpochDay` INTEGER, `status` TEXT NOT NULL, `completedEpochDay` INTEGER, `isDemo` INTEGER NOT NULL DEFAULT 0)",
)

/** SQL de la migración 5 -> 6, idéntico al que Room genera en schemas/.../6.json. */
private val MIGRATION_5_6_SQL = listOf(
    "CREATE TABLE IF NOT EXISTS `recurring_payments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `amountMinor` INTEGER NOT NULL, `currencyCode` TEXT NOT NULL, `dayOfMonth` INTEGER NOT NULL, `frequency` TEXT NOT NULL, `startEpochDay` INTEGER NOT NULL, `endEpochDay` INTEGER, `category` TEXT NOT NULL, `description` TEXT NOT NULL, `active` INTEGER NOT NULL DEFAULT 1, `remindEnabled` INTEGER NOT NULL DEFAULT 1, `remindLeadDays` INTEGER NOT NULL DEFAULT 1, `remindMinuteOfDay` INTEGER NOT NULL DEFAULT 540, `isDemo` INTEGER NOT NULL DEFAULT 0)",
)
