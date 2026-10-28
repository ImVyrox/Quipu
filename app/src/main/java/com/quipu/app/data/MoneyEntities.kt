package com.quipu.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.quipu.app.domain.ExpenseCategory
import com.quipu.app.domain.GoalStatus
import com.quipu.app.domain.IncomeKind
import com.quipu.app.domain.MovementType

/**
 * Movimiento de dinero (ingreso, gasto o traspaso al ahorro). Las relaciones con fuentes,
 * objetivos y pagos recurrentes son lógicas, sin claves foráneas: borrar una fuente no
 * borra su historial.
 *
 * Los índices únicos impiden dos ingresos fijos de la misma fuente en el mismo mes y dos
 * pagos del mismo recurrente en el mismo mes. SQLite admite varios NULL en un índice único,
 * así que los movimientos sin mes ([period] null) no se ven afectados.
 */
@Entity(
    tableName = "movements",
    indices = [
        Index(value = ["epochDay"]),
        Index(value = ["goalId"]),
        Index(value = ["incomeSourceId", "period"], unique = true),
        Index(value = ["recurringPaymentId", "period"], unique = true),
    ],
)
data class MovementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: MovementType,
    /** Importe en centavos, siempre positivo. */
    val amountMinor: Long,
    val currencyCode: String,
    /** Día del dinero: cuándo se recibió o pagó (o la fecha prevista si está pendiente). */
    val epochDay: Long,
    val description: String,
    val category: ExpenseCategory?,
    val pending: Boolean,
    val incomeSourceId: Long?,
    val workEpochDay: Long?,
    val workedMinutes: Int?,
    val goalId: Long?,
    val recurringPaymentId: Long?,
    /** Mes saldado ("2026-10") por un ingreso fijo o un pago recurrente. */
    val period: String?,
    val createdAtEpochSecond: Long,
    @ColumnInfo(defaultValue = "0") val isDemo: Boolean = false,
)

@Entity(tableName = "income_sources")
data class IncomeSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: IncomeKind,
    val currencyCode: String,
    val fixedAmountMinor: Long?,
    val hourlyRateMinor: Long?,
    val plannedMinutesPerMonth: Int?,
    /** Una fuente con historial no se borra: se archiva (active = false). */
    @ColumnInfo(defaultValue = "1") val active: Boolean = true,
    @ColumnInfo(defaultValue = "0") val isDemo: Boolean = false,
)

/** Dinero real declarado por el usuario. Se guarda el historial; vale el más reciente por moneda. */
@Entity(
    tableName = "declared_balances",
    indices = [Index(value = ["currencyCode"])],
)
data class DeclaredBalanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val currencyCode: String,
    val amountMinor: Long,
    val epochDay: Long,
)

/**
 * Objetivo de ahorro. Lo acumulado no se guarda: se calcula con sus movimientos
 * (aportaciones, retiros y ahorro inicial) para que historial y saldo coincidan siempre.
 */
@Entity(tableName = "savings_goals")
data class SavingsGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    /** Importe objetivo en centavos. */
    val targetMinor: Long,
    val currencyCode: String,
    val createdEpochDay: Long,
    val deadlineEpochDay: Long?,
    val status: GoalStatus,
    val completedEpochDay: Long?,
    @ColumnInfo(defaultValue = "0") val isDemo: Boolean = false,
)
