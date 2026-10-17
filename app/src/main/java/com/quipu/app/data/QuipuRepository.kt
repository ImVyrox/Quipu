package com.quipu.app.data

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
        debtDao.observeAll().map { list -> list.map { it.toDomain() } }

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
            val existing = debtDao.getById(draft.id) ?: return
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

    suspend fun deleteDebt(id: Long) = debtDao.deleteById(id)

    // ---- Datos de demostración (opcionales) ----

    /** Cantidad de registros de demostración presentes (tareas + movimientos). */
    fun observeDemoCount(): Flow<Int> =
        combine(taskDao.observeDemoCount(), debtDao.observeDemoCount()) { tasks, debts -> tasks + debts }

    suspend fun loadDemoData(now: LocalDateTime = LocalDateTime.now()) {
        taskDao.insertAll(DemoData.tasks(now))
        debtDao.insertAll(DemoData.debts(now.toLocalDate()))
    }

    suspend fun removeDemoData() {
        taskDao.deleteDemo()
        debtDao.deleteDemo()
    }
}
