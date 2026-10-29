package com.quipu.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.quipu.app.domain.DebtStatus
import com.quipu.app.domain.DebtType
import com.quipu.app.domain.ExpenseCategory
import com.quipu.app.domain.GoalStatus
import com.quipu.app.domain.IncomeKind
import com.quipu.app.domain.MovementType
import com.quipu.app.domain.RecurringFrequency

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    /** Hora local de pared como segundos desde 1970-01-01T00:00 (sin zona horaria). */
    val dueLocalEpochSecond: Long,
    /** Solo para tareas normales; en las diarias se usa [lastCompletedEpochDay]. */
    val completed: Boolean,
    val isDemo: Boolean = false,
    /** Tarea diaria recurrente: un único registro que vuelve a estar pendiente cada día. */
    @ColumnInfo(defaultValue = "0") val isDaily: Boolean = false,
    /** Día (epoch day, hora local) de la última realización de una tarea diaria. */
    val lastCompletedEpochDay: Long? = null,
)

@Entity(
    tableName = "debts",
    indices = [Index(value = ["personKey"])],
)
data class DebtEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: DebtType,
    val personName: String,
    val personKey: String,
    /** Importe en centavos. */
    val amountMinor: Long,
    val recordEpochDay: Long,
    val dueEpochDay: Long?,
    val note: String,
    val status: DebtStatus,
    val settledEpochDay: Long?,
    val isDemo: Boolean = false,
)

/** Abono (pago parcial) de un registro de [DebtEntity]. Se borra junto con su registro. */
@Entity(
    tableName = "debt_payments",
    indices = [Index(value = ["debtId"])],
)
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val debtId: Long,
    /** Importe en centavos. */
    val amountMinor: Long,
    val epochDay: Long,
)

class Converters {
    @TypeConverter
    fun debtTypeToString(value: DebtType): String = value.name

    @TypeConverter
    fun stringToDebtType(value: String): DebtType = DebtType.valueOf(value)

    @TypeConverter
    fun debtStatusToString(value: DebtStatus): String = value.name

    @TypeConverter
    fun stringToDebtStatus(value: String): DebtStatus = DebtStatus.valueOf(value)

    @TypeConverter
    fun movementTypeToString(value: MovementType): String = value.name

    @TypeConverter
    fun stringToMovementType(value: String): MovementType = MovementType.valueOf(value)

    @TypeConverter
    fun incomeKindToString(value: IncomeKind): String = value.name

    @TypeConverter
    fun stringToIncomeKind(value: String): IncomeKind = IncomeKind.valueOf(value)

    @TypeConverter
    fun expenseCategoryToString(value: ExpenseCategory?): String? = value?.name

    @TypeConverter
    fun stringToExpenseCategory(value: String?): ExpenseCategory? = value?.let { ExpenseCategory.valueOf(it) }

    @TypeConverter
    fun recurringFrequencyToString(value: RecurringFrequency): String = value.name

    @TypeConverter
    fun stringToRecurringFrequency(value: String): RecurringFrequency = RecurringFrequency.valueOf(value)

    @TypeConverter
    fun goalStatusToString(value: GoalStatus): String = value.name

    @TypeConverter
    fun stringToGoalStatus(value: String): GoalStatus = GoalStatus.valueOf(value)
}
