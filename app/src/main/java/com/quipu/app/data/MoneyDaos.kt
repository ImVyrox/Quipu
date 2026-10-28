package com.quipu.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MovementDao {
    @Query("SELECT * FROM movements ORDER BY epochDay DESC, id DESC")
    fun observeAll(): Flow<List<MovementEntity>>

    @Query("SELECT * FROM movements ORDER BY epochDay DESC, id DESC")
    suspend fun getAll(): List<MovementEntity>

    @Query("SELECT * FROM movements WHERE id = :id")
    suspend fun getById(id: Long): MovementEntity?

    @Query("SELECT * FROM movements WHERE incomeSourceId = :sourceId AND period = :period LIMIT 1")
    suspend fun findIncomeForPeriod(sourceId: Long, period: String): MovementEntity?

    @Query("SELECT COUNT(*) FROM movements WHERE goalId = :goalId AND type = 'SAVING_OPENING'")
    suspend fun countOpenings(goalId: Long): Int

    @Query("SELECT * FROM movements WHERE goalId = :goalId")
    suspend fun getForGoal(goalId: Long): List<MovementEntity>

    @Query("SELECT COUNT(*) FROM movements WHERE incomeSourceId = :sourceId")
    suspend fun countForSource(sourceId: Long): Int

    @Insert
    suspend fun insert(movement: MovementEntity): Long

    @Update
    suspend fun update(movement: MovementEntity)

    @Query("DELETE FROM movements WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM movements WHERE isDemo = 1")
    fun observeDemoCount(): Flow<Int>

    @Query("DELETE FROM movements WHERE isDemo = 1")
    suspend fun deleteDemo()
}

@Dao
interface IncomeSourceDao {
    @Query("SELECT * FROM income_sources ORDER BY id")
    fun observeAll(): Flow<List<IncomeSourceEntity>>

    @Query("SELECT * FROM income_sources WHERE id = :id")
    suspend fun getById(id: Long): IncomeSourceEntity?

    @Insert
    suspend fun insert(source: IncomeSourceEntity): Long

    @Update
    suspend fun update(source: IncomeSourceEntity)

    @Query("UPDATE income_sources SET active = :active WHERE id = :id")
    suspend fun setActive(id: Long, active: Boolean)

    @Query("DELETE FROM income_sources WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM income_sources WHERE isDemo = 1")
    fun observeDemoCount(): Flow<Int>

    @Query("DELETE FROM income_sources WHERE isDemo = 1")
    suspend fun deleteDemo()
}

@Dao
interface DeclaredBalanceDao {
    @Query("SELECT * FROM declared_balances ORDER BY epochDay, id")
    fun observeAll(): Flow<List<DeclaredBalanceEntity>>

    @Insert
    suspend fun insert(balance: DeclaredBalanceEntity): Long

    @Query("DELETE FROM declared_balances WHERE currencyCode = :currencyCode")
    suspend fun deleteForCurrency(currencyCode: String)
}

@Dao
interface SavingsGoalDao {
    @Query("SELECT * FROM savings_goals ORDER BY id")
    fun observeAll(): Flow<List<SavingsGoalEntity>>

    @Query("SELECT * FROM savings_goals WHERE id = :id")
    suspend fun getById(id: Long): SavingsGoalEntity?

    @Insert
    suspend fun insert(goal: SavingsGoalEntity): Long

    @Update
    suspend fun update(goal: SavingsGoalEntity)

    @Query("DELETE FROM savings_goals WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM savings_goals WHERE isDemo = 1")
    fun observeDemoCount(): Flow<Int>

    @Query("DELETE FROM savings_goals WHERE isDemo = 1")
    suspend fun deleteDemo()
}
