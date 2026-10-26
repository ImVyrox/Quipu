package com.quipu.app.data

import androidx.room.withTransaction
import com.quipu.app.domain.DebtDraft
import com.quipu.app.domain.DebtItem
import com.quipu.app.domain.DebtStatus
import com.quipu.app.domain.PersonKey
import com.quipu.app.domain.TaskDraft
import com.quipu.app.domain.TaskItem
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class QuipuRepository(
    private val database: QuipuDatabase,
    val settings: SettingsStore,
) {
    private val taskDao = database.taskDao()
    private val debtDao = database.debtDao()
    private val paymentDao = database.paymentDao()

    // ---- Tareas ----

    fun observeTasks(): Flow<List<TaskItem>> =
        taskDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun saveTask(draft: TaskDraft) {
        val title = draft.title.trim()
        val description = draft.description.trim()
        val dueSecond = draft.dueAt.toLocalEpochSecond()
        if (draft.id == 0L) {
            taskDao.insert(
                TaskEntity(
                    title = title,
                    description = description,
                    dueLocalEpochSecond = dueSecond,
                    completed = false,
                    isDaily = draft.isDaily,
                ),
            )
        } else {
            // Se conserva el estado de completada y la marca de demostración. Si cambia
            // entre normal y diaria, empieza como pendiente en su nueva modalidad.
            val existing = taskDao.getById(draft.id) ?: return
            val typeChanged = existing.isDaily != draft.isDaily
            taskDao.update(
                existing.copy(
                    title = title,
                    description = description,
                    dueLocalEpochSecond = dueSecond,
                    isDaily = draft.isDaily,
                    completed = if (typeChanged) false else existing.completed,
                    lastCompletedEpochDay = if (typeChanged) null else existing.lastCompletedEpochDay,
                ),
            )
        }
    }

    /**
     * Marca o desmarca una tarea. Una diaria guarda el día de realización ([today]),
     * así que al día siguiente vuelve a estar pendiente sin crear otro registro.
     */
    suspend fun setTaskCompleted(id: Long, completed: Boolean, today: LocalDate = LocalDate.now()) {
        val existing = taskDao.getById(id) ?: return
        if (existing.isDaily) {
            taskDao.setLastCompletedDay(id, if (completed) today.toEpochDay() else null)
        } else {
            taskDao.setCompleted(id, completed)
        }
    }

    suspend fun deleteTask(id: Long) = taskDao.deleteById(id)

    // ---- Préstamos y deudas ----

    fun observeDebts(): Flow<List<DebtItem>> =
        combine(debtDao.observeAll(), paymentDao.observeAll()) { debts, payments -> withPayments(debts, payments) }

    private fun withPayments(debts: List<DebtEntity>, payments: List<PaymentEntity>): List<DebtItem> {
        val byDebt = payments.groupBy({ it.debtId }, { it.toDomain() })
        return debts.map { it.toDomain(byDebt[it.id].orEmpty()) }
    }

    /** Foto actual de tareas y registros (para calcular recordatorios fuera de la interfaz). */
    suspend fun snapshot(): Pair<List<TaskItem>, List<DebtItem>> = database.withTransaction {
        taskDao.getAll().map { it.toDomain() } to withPayments(debtDao.getAll(), paymentDao.getAll())
    }

    suspend fun saveDebt(draft: DebtDraft) {
        val displayName = PersonKey.displayName(draft.personName)
        val key = PersonKey.keyOf(draft.personName)
        if (draft.id == 0L) {
            debtDao.insert(
                DebtEntity(
                    type = draft.type,
                    personName = displayName,
                    personKey = key,
                    amountMinor = draft.amountMinor,
                    recordEpochDay = draft.recordDate.toEpochDay(),
                    dueEpochDay = draft.dueDate?.toEpochDay(),
                    note = draft.note.trim(),
                    status = DebtStatus.PENDING,
                    settledEpochDay = null,
                ),
            )
        } else {
            database.withTransaction {
                val existing = debtDao.getById(draft.id) ?: return@withTransaction
                debtDao.update(
                    existing.copy(
                        type = draft.type,
                        personName = displayName,
                        personKey = key,
                        amountMinor = draft.amountMinor,
                        recordEpochDay = draft.recordDate.toEpochDay(),
                        dueEpochDay = draft.dueDate?.toEpochDay(),
                        note = draft.note.trim(),
                    ),
                )
                // Si al bajar el importe los abonos ya lo cubren, el registro queda saldado.
                if (existing.status == DebtStatus.PENDING && paymentDao.paidFor(draft.id) >= draft.amountMinor) {
                    debtDao.setStatus(draft.id, DebtStatus.SETTLED, LocalDate.now().toEpochDay())
                }
            }
        }
    }

    /** Marca como pagado/cobrado (con fecha de hoy) o devuelve a pendiente. */
    suspend fun setDebtSettled(id: Long, settled: Boolean) {
        if (settled) {
            debtDao.setStatus(id, DebtStatus.SETTLED, LocalDate.now().toEpochDay())
        } else {
            debtDao.setStatus(id, DebtStatus.PENDING, null)
        }
    }

    suspend fun deleteDebt(id: Long) = database.withTransaction {
        paymentDao.deleteForDebt(id)
        debtDao.deleteById(id)
    }

    /**
     * Registra un abono en un registro pendiente. Si con él se cubre todo el importe,
     * el registro pasa a pagado/cobrado con la fecha del abono. Devuelve true en ese caso.
     */
    suspend fun addPayment(debtId: Long, amountMinor: Long, date: LocalDate): Boolean =
        database.withTransaction {
            val debt = debtDao.getById(debtId)
            require(debt != null && debt.status == DebtStatus.PENDING) { "El registro no está pendiente" }
            val paid = paymentDao.paidFor(debtId)
            require(amountMinor > 0 && paid + amountMinor <= debt.amountMinor) { "Abono fuera de rango" }
            paymentDao.insert(PaymentEntity(debtId = debtId, amountMinor = amountMinor, epochDay = date.toEpochDay()))
            val settled = paid + amountMinor == debt.amountMinor
            if (settled) debtDao.setStatus(debtId, DebtStatus.SETTLED, date.toEpochDay())
            settled
        }

    /** Elimina un abono. Solo se permite en registros pendientes, para no contradecir un "pagado". */
    suspend fun deletePayment(paymentId: Long) = database.withTransaction {
        val payment = paymentDao.getById(paymentId) ?: return@withTransaction
        val debt = debtDao.getById(payment.debtId)
        require(debt == null || debt.status == DebtStatus.PENDING) { "El registro no está pendiente" }
        paymentDao.deleteById(paymentId)
    }

    // ---- Datos de demostración (opcionales) ----

    /** Cantidad de registros de demostración presentes (tareas + movimientos). */
    fun observeDemoCount(): Flow<Int> =
        combine(taskDao.observeDemoCount(), debtDao.observeDemoCount()) { tasks, debts -> tasks + debts }

    suspend fun loadDemoData(now: LocalDateTime = LocalDateTime.now()) {
        taskDao.insertAll(DemoData.tasks(now))
        debtDao.insertAll(DemoData.debts(now.toLocalDate()))
    }

    suspend fun removeDemoData() = database.withTransaction {
        taskDao.deleteDemo()
        paymentDao.deleteForDemoDebts()
        debtDao.deleteDemo()
    }
}
