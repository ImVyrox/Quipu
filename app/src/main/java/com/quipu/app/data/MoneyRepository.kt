package com.quipu.app.data

import androidx.room.withTransaction
import com.quipu.app.domain.DeclaredBalance
import com.quipu.app.domain.IncomeKind
import com.quipu.app.domain.IncomeSource
import com.quipu.app.domain.Money
import com.quipu.app.domain.Movement
import com.quipu.app.domain.MovementType
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** Ya hay un ingreso de esa fuente fija en ese mes: se rechaza para no contarlo dos veces. */
class DuplicateIncomeException(val month: YearMonth) : IllegalStateException("Ingreso fijo duplicado en $month")

/**
 * Ingresos, gastos y dinero declarado. Separado de [QuipuRepository] (tareas y préstamos)
 * porque son datos independientes: los préstamos no cambian el saldo.
 */
class MoneyRepository(
    private val database: QuipuDatabase,
    private val nowEpochSecond: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    private val movementDao = database.movementDao()
    private val sourceDao = database.incomeSourceDao()
    private val declaredDao = database.declaredBalanceDao()

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
        val clean = movement.copy(
            period = period,
            category = if (movement.type == MovementType.EXPENSE) movement.category else null,
            incomeSourceId = if (movement.type == MovementType.INCOME) movement.incomeSourceId else null,
        )

        if (movement.id == 0L) {
            movementDao.insert(clean.toEntity(createdAtEpochSecond = nowEpochSecond()))
        } else {
            val stored = movementDao.getById(movement.id) ?: return@withTransaction 0L
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

    suspend fun deleteMovement(id: Long) = movementDao.deleteById(id)

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
        combine(movementDao.observeDemoCount(), sourceDao.observeDemoCount()) { m, s -> m + s }

    suspend fun loadDemoData(today: LocalDate) = database.withTransaction {
        val sourceId = sourceDao.insert(DemoData.hourlySource())
        DemoData.movements(today, sourceId).forEach {
            movementDao.insert(it.toEntity(createdAtEpochSecond = nowEpochSecond(), isDemo = true))
        }
    }

    suspend fun removeDemoData() = database.withTransaction {
        movementDao.deleteDemo()
        sourceDao.deleteDemo()
    }
}
