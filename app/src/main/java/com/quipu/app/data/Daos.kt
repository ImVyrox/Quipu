package com.quipu.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.quipu.app.domain.DebtStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): TaskEntity?

    @Query("SELECT COUNT(*) FROM tasks WHERE isDemo = 1")
    fun observeDemoCount(): Flow<Int>

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Insert
    suspend fun insertAll(tasks: List<TaskEntity>)

    @Update
    suspend fun update(task: TaskEntity)

    @Query("UPDATE tasks SET completed = :completed WHERE id = :id")
    suspend fun setCompleted(id: Long, completed: Boolean)

    @Query("UPDATE tasks SET lastCompletedEpochDay = :epochDay WHERE id = :id")
    suspend fun setLastCompletedDay(id: Long, epochDay: Long?)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM tasks WHERE isDemo = 1")
    suspend fun deleteDemo()
}

@Dao
interface DebtDao {
    @Query("SELECT * FROM debts")
    fun observeAll(): Flow<List<DebtEntity>>

    @Query("SELECT * FROM debts WHERE id = :id")
    suspend fun getById(id: Long): DebtEntity?

    @Query("SELECT COUNT(*) FROM debts WHERE isDemo = 1")
    fun observeDemoCount(): Flow<Int>

    @Insert
    suspend fun insert(debt: DebtEntity): Long

    @Insert
    suspend fun insertAll(debts: List<DebtEntity>)

    @Update
    suspend fun update(debt: DebtEntity)

    @Query("UPDATE debts SET status = :status, settledEpochDay = :settledEpochDay WHERE id = :id")
    suspend fun setStatus(id: Long, status: DebtStatus, settledEpochDay: Long?)

    @Query("DELETE FROM debts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM debts WHERE isDemo = 1")
    suspend fun deleteDemo()
}
