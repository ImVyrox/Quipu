package com.quipu.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TaskEntity::class, DebtEntity::class, PaymentEntity::class],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class QuipuDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun debtDao(): DebtDao
    abstract fun paymentDao(): PaymentDao

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

        @Volatile
        private var instance: QuipuDatabase? = null

        fun get(context: Context): QuipuDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    QuipuDatabase::class.java,
                    "quipu.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }
    }
}
