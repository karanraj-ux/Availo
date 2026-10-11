package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduledTaskDao {
    @Query("SELECT * FROM scheduled_tasks ORDER BY timeMillis ASC")
    fun getAllTasks(): Flow<List<ScheduledTaskEntity>>

    @Insert
    suspend fun insertTask(task: ScheduledTaskEntity): Long

    @Update
    suspend fun updateTask(task: ScheduledTaskEntity)
    
    @Query("UPDATE scheduled_tasks SET completed = 1 WHERE id = :taskId")
    suspend fun markCompleted(taskId: Int)
    
    @Query("SELECT COUNT(*) FROM scheduled_tasks WHERE timeMillis >= :todayStart")
    fun getTasksTodayCount(todayStart: Long): Flow<Int>

    @Query("SELECT * FROM scheduled_tasks WHERE id = :taskId")
    suspend fun getTaskById(taskId: Int): ScheduledTaskEntity?
}
