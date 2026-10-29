package com.quipu.app.data

import androidx.room.withTransaction
import com.quipu.app.domain.ConfirmResult
import com.quipu.app.domain.DeclaredBalance
import com.quipu.app.domain.GoalStatus
import com.quipu.app.domain.IncomeKind
import com.quipu.app.domain.IncomeSource
import com.quipu.app.domain.Ledger
import com.quipu.app.domain.Money
import com.quipu.app.domain.Movement
import com.quipu.app.domain.MovementType
import com.quipu.app.domain.RecurringPayment
import com.quipu.app.domain.RecurringPlanner
import com.quipu.app.domain.SavingsGoal
import com.quipu.app.domain.TransferError
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** Ya hay un ingreso de esa fuente fija en ese mes: se rechaza para no contarlo dos veces. */
class DuplicateIncomeException(val month: YearMonth) : IllegalStateException("Ingreso fijo duplicado en $month")

/** Movimiento de ahorro rechazado por la política contable ([Ledger]). */
class SavingRejectedException(val error: TransferError) : IllegalStateException(error.message)

/**
 * Ingresos, gastos, objetivos de ahorro y dinero declarado. Separado de [QuipuRepository] (tareas y préstamos)
 * porque son datos independientes: los préstamos no cambian el saldo.
 */
class MoneyRepository(
    private val database: QuipuDatabase,
    private val nowEpochSecond: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    private val movementDao = database.movementDao()
    private val sourceDao = database.incomeSourceDao()
    private val declaredDao = database.declaredBalanceDao()
    private val goalDao = database.savingsGoalDao()
    private val recurringDao = database.recurringPaymentDao()

    fun observeMovements(): Flow<List<Movement>> =
        movementDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeIncomeSources(): Flow<List<IncomeSource>> =
        sourceDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeDeclaredBalances(): Flow<List<DeclaredBalance>> =
        declaredDao.observeAll().map { list -> list.map { it.toDomain() } }

    /**
     * Crea (id 0) o actualiza un ingreso o gasto. Un ingreso de una fuente fija queda
     * asociado al mes de su fecha y no puede haber dos en el mismo mes
     * ([DuplicateIncomeException]). Devuelve el id guardado.
     */
    suspend fun saveMovement(movement: Movement): Long = database.withTransaction {
        require(movement.type == MovementType.INCOME || movement.type == MovementType.EXPENSE) {
            "Los traspasos al ahorro se registran desde los objetivos"
        }
        require(movement.amountMinor in 1..Money.MAX_MINOR) { "Importe fuera de rango" }

        val source = movement.incomeSourceId?.let { sourceDao.getById(it) }
        val period = if (movement.type == MovementType.INCOME && source?.kind == IncomeKind.FIXED) {
            YearMonth.from(movement.date)
        } else {
            null
        }
        if (period != null) {
            val existing = movementDao.findIncomeForPeriod(source!!.id, period.toString())
            if (existing != null && existing.id != movement.id) throw DuplicateIncomeException(period)
        }
        // El vínculo con un pago recurrente solo se crea al confirmarlo o vincularlo
        // (confirmRecurring, linkRecurring): editar el gasto lo conserva y un gasto nuevo no lo trae.
        val stored = if (movement.id == 0L) null else (movementDao.getById(movement.id) ?: return@withTransaction 0L)
        val keepsRecurring = movement.type == MovementType.EXPENSE && stored?.recurringPaymentId != null
        val clean = movement.copy(
            period = if (keepsRecurring) stored!!.period?.let(YearMonth::parse) else period,
            recurringPaymentId = if (keepsRecurring) stored!!.recurringPaymentId else null,
            category = if (movement.type == MovementType.EXPENSE) movement.category else null,
            incomeSourceId = if (movement.type == MovementType.INCOME) movement.incomeSourceId else null,
        )

        if (stored == null) {
            movementDao.insert(clean.toEntity(createdAtEpochSecond = nowEpochSecond()))
        } else {
            movementDao.update(clean.toEntity(stored.createdAtEpochSecond, stored.isDemo))
            movement.id
        }
    }

    /** Confirma un ingreso por cobrar o un gasto por pagar con la fecha real del dinero. */
    suspend fun markSettled(id: Long, date: LocalDate) = database.withTransaction {
        val stored = movementDao.getById(id) ?: return@withTransaction
        if (!stored.pending) return@withTransaction
        // Un ingreso por horas conserva su día trabajado; el resto pasa a la fecha real.
        movementDao.update(stored.copy(pending = false, epochDay = date.toEpochDay()))
    }

    /**
     * Borra un movimiento. Un movimiento de ahorro solo se borra si su objetivo existe y lo
     * ahorrado en él no queda en negativo ([SavingRejectedException]).
     */
    suspend fun deleteMovement(id: Long) = database.withTransaction {
        val stored = movementDao.getById(id) ?: return@withTransaction
        if (stored.type.isSaving) {
            // El historial de un objetivo eliminado queda fijo: borrar su retiro de cierre
            // haría reaparecer dinero ahorrado en un objetivo que ya no existe.
            if (stored.goalId == null || goalDao.getById(stored.goalId) == null) {
                throw SavingRejectedException(TransferError.GOAL_DELETED)
            }
            Ledger.validateRemoval(stored.toDomain(), allMovements())?.let { throw SavingRejectedException(it) }
        }
        movementDao.deleteById(id)
    }

    private suspend fun allMovements(): List<Movement> = movementDao.getAll().map { it.toDomain() }

    // ---- Pagos recurrentes y suscripciones ----

    fun observeRecurring(): Flow<List<RecurringPayment>> =
        recurringDao.observeAll().map { list -> list.map { it.toDomain() } }

    /** Pagos recurrentes y movimientos actuales, para calcular avisos fuera de la interfaz. */
    suspend fun recurringSnapshot(): Pair<List<RecurringPayment>, List<Movement>> = database.withTransaction {
        recurringDao.getAll().map { it.toDomain() } to allMovements()
    }

    /**
     * Crea (id 0) o actualiza un pago recurrente. Al editar se conserva si está en pausa
     * (eso se cambia con [setRecurringActive]). Los pagos ya hechos no cambian: son gastos
     * guardados en su moneda original.
     */
    suspend fun saveRecurring(payment: RecurringPayment): Long = database.withTransaction {
        require(payment.amountMinor in 1..Money.MAX_MINOR) { "Importe fuera de rango" }
        require(payment.dayOfMonth in 1..31) { "Día fuera de rango" }
        if (payment.id == 0L) {
            recurringDao.insert(payment.copy(active = true).toEntity())
        } else {
            val stored = recurringDao.getById(payment.id) ?: return@withTransaction 0L
            recurringDao.update(payment.copy(active = stored.active).toEntity(isDemo = stored.isDemo))
            payment.id
        }
    }

    suspend fun setRecurringActive(id: Long, active: Boolean) = recurringDao.setActive(id, active)

    /** Borra un pago recurrente. Sus pagos hechos se conservan como gastos en el historial. */
    suspend fun deleteRecurring(id: Long) = recurringDao.deleteById(id)

    /**
     * Confirma el vencimiento de [period] con la fecha efectiva [paidOn] y el importe pagado,
     * en la moneda original del pago. Se comprueba en una transacción con los datos guardados:
     * si ese mes ya estaba pagado devuelve [ConfirmResult.AlreadyPaid] y no crea otro gasto
     * (además, el índice único de movements lo impide).
     */
    suspend fun confirmRecurring(
        paymentId: Long,
        period: YearMonth,
        paidOn: LocalDate,
        amountMinor: Long,
    ): ConfirmResult = database.withTransaction {
        require(amountMinor in 1..Money.MAX_MINOR) { "Importe fuera de rango" }
        val payment = recurringDao.getById(paymentId)?.toDomain() ?: return@withTransaction ConfirmResult.NotDue
        val result = RecurringPlanner.confirm(payment, period, paidOn, allMovements(), amountMinor)
        if (result is ConfirmResult.Created) {
            val id = movementDao.insert(result.movement.toEntity(createdAtEpochSecond = nowEpochSecond()))
            ConfirmResult.Created(result.movement.copy(id = id))
        } else {
            result
        }
    }

    /**
     * Usa un gasto ya anotado a mano como pago de [period], en vez de registrar otro: así
     * el mismo pago no cuenta dos veces. El gasto debe estar pagado, sin otro pago
     * recurrente y en la misma moneda, y ese mes no puede estar ya pagado.
     */
    suspend fun linkRecurring(movementId: Long, paymentId: Long, period: YearMonth) = database.withTransaction {
        val payment = recurringDao.getById(paymentId)?.toDomain() ?: throw IllegalArgumentException("El pago no existe")
        val stored = movementDao.getById(movementId) ?: throw IllegalArgumentException("El gasto no existe")
        check(stored.type == MovementType.EXPENSE && !stored.pending && stored.recurringPaymentId == null) {
            "Solo se vincula un gasto pagado sin pago recurrente"
        }
        check(stored.currencyCode == payment.currencyCode) { "La moneda no coincide" }
        check(RecurringPlanner.isDueIn(payment, period)) { "El pago no vence ese mes" }
        check(movementDao.findRecurring(paymentId, period.toString()) == null) { "Ese mes ya está pagado" }
        movementDao.update(stored.copy(recurringPaymentId = paymentId, period = period.toString()))
    }

    // ---- Objetivos de ahorro ----

    fun observeGoals(): Flow<List<SavingsGoal>> =
        goalDao.observeAll().map { list -> list.map { it.toDomain() } }

    /**
     * Crea (id 0) o actualiza un objetivo. Su moneda no puede cambiar si ya tiene
     * movimientos: lo ahorrado quedaría en una moneda distinta a la del objetivo.
     */
    suspend fun saveGoal(goal: SavingsGoal): Long = database.withTransaction {
        require(goal.targetMinor in 1..Money.MAX_MINOR) { "Importe fuera de rango" }
        if (goal.id == 0L) {
            goalDao.insert(goal.copy(status = GoalStatus.ACTIVE, completedDate = null).toEntity())
        } else {
            val stored = goalDao.getById(goal.id) ?: return@withTransaction 0L
            val hasMovements = movementDao.getForGoal(goal.id).isNotEmpty()
            check(stored.currencyCode == goal.currencyCode || !hasMovements) { "La moneda de un objetivo con movimientos no cambia" }
            // El estado y la creación se cambian con setGoalStatus; aquí se conservan.
            goalDao.update(
                goal.toEntity(isDemo = stored.isDemo).copy(
                    createdEpochDay = stored.createdEpochDay,
                    status = stored.status,
                    completedEpochDay = stored.completedEpochDay,
                ),
            )
            goal.id
        }
    }

    /** Activa, pausa o completa un objetivo. Completar guarda la fecha; los otros estados la quitan. */
    suspend fun setGoalStatus(id: Long, status: GoalStatus, today: LocalDate) = database.withTransaction {
        val stored = goalDao.getById(id) ?: return@withTransaction
        goalDao.update(
            stored.copy(
                status = status,
                completedEpochDay = if (status == GoalStatus.COMPLETED) today.toEpochDay() else null,
            ),
        )
    }

    /**
     * Registra una aportación, un retiro o el ahorro inicial de un objetivo, tras comprobar
     * la política contable con los datos guardados (en la misma transacción, así dos
     * registros seguidos no pueden saltarse los límites). Devuelve el id del movimiento.
     */
    suspend fun addSaving(
        goalId: Long,
        type: MovementType,
        amountMinor: Long,
        date: LocalDate,
        description: String = "",
    ): Long = database.withTransaction {
        require(type.isSaving) { "No es un movimiento de ahorro: $type" }
        val goal = goalDao.getById(goalId)?.toDomain() ?: throw IllegalArgumentException("El objetivo no existe")
        val movements = allMovements()
        val error = if (type == MovementType.SAVING_OPENING) {
            Ledger.validateOpening(amountMinor, goal, movements)
        } else {
            Ledger.validateTransfer(type, amountMinor, goal.currencyCode, goal, movements)
        }
        error?.let { throw SavingRejectedException(it) }
        val movement = Movement(
            id = 0,
            type = type,
            amountMinor = amountMinor,
            currencyCode = goal.currencyCode,
            date = date,
            description = description.trim(),
            goalId = goalId,
        )
        movementDao.insert(movement.toEntity(createdAtEpochSecond = nowEpochSecond()))
    }

    /**
     * Elimina un objetivo. Si tiene dinero ahorrado, antes se registra un retiro que lo
     * devuelve al disponible: eliminar un objetivo nunca hace desaparecer dinero. Su
     * historial se conserva. Devuelve el importe devuelto al disponible.
     */
    suspend fun deleteGoal(id: Long, today: LocalDate): Long = database.withTransaction {
        val goal = goalDao.getById(id)?.toDomain() ?: return@withTransaction 0L
        val closing = Ledger.closingWithdrawal(goal, allMovements(), today)
        closing?.let { movementDao.insert(it.toEntity(createdAtEpochSecond = nowEpochSecond())) }
        goalDao.deleteById(id)
        closing?.amountMinor ?: 0L
    }

    suspend fun saveIncomeSource(source: IncomeSource): Long =
        if (source.id == 0L) {
            sourceDao.insert(source.toEntity())
        } else {
            val stored = sourceDao.getById(source.id)
            sourceDao.update(source.toEntity(isDemo = stored?.isDemo ?: false))
            source.id
        }

    /**
     * Quita una fuente. Si tiene ingresos se archiva (deja de aparecer y de prever ingresos,
     * pero su historial se conserva); si no, se borra. Devuelve true si se archivó.
     */
    suspend fun removeIncomeSource(id: Long): Boolean = database.withTransaction {
        if (movementDao.countForSource(id) > 0) {
            sourceDao.setActive(id, false)
            true
        } else {
            sourceDao.deleteById(id)
            false
        }
    }

    /** Guarda lo que el usuario declara tener en una moneda. No crea ningún movimiento. */
    suspend fun declareBalance(currencyCode: String, amountMinor: Long, date: LocalDate) {
        require(amountMinor in 0..Money.MAX_MINOR) { "Importe fuera de rango" }
        declaredDao.insert(DeclaredBalanceEntity(currencyCode = currencyCode, amountMinor = amountMinor, epochDay = date.toEpochDay()))
    }

    suspend fun clearDeclaredBalance(currencyCode: String) = declaredDao.deleteForCurrency(currencyCode)

    // ---- Datos de demostración (opcionales) ----

    fun observeDemoCount(): Flow<Int> =
        combine(
            movementDao.observeDemoCount(),
            sourceDao.observeDemoCount(),
            goalDao.observeDemoCount(),
            recurringDao.observeDemoCount(),
        ) { m, s, g, r -> m + s + g + r }

    suspend fun loadDemoData(today: LocalDate) = database.withTransaction {
        val sourceId = sourceDao.insert(DemoData.hourlySource())
        DemoData.movements(today, sourceId).forEach {
            movementDao.insert(it.toEntity(createdAtEpochSecond = nowEpochSecond(), isDemo = true))
        }
        val goalId = goalDao.insert(DemoData.goal(today))
        DemoData.goalMovements(today, goalId).forEach {
            movementDao.insert(it.toEntity(createdAtEpochSecond = nowEpochSecond(), isDemo = true))
        }
        val paymentId = recurringDao.insert(DemoData.recurringPayment(today))
        DemoData.recurringMovements(today, paymentId).forEach {
            movementDao.insert(it.toEntity(createdAtEpochSecond = nowEpochSecond(), isDemo = true))
        }
    }

    suspend fun removeDemoData() = database.withTransaction {
        movementDao.deleteDemo()
        sourceDao.deleteDemo()
        goalDao.deleteDemo()
        recurringDao.deleteDemo()
    }
}
