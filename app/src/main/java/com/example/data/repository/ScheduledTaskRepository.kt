package com.example.data.repository

import com.example.data.ScheduledTaskDao
import com.example.data.ScheduledTaskEntity
import kotlinx.coroutines.flow.Flow

class ScheduledTaskRepository(private val dao: ScheduledTaskDao) {
    fun getTasksTodayCount(todayStart: Long): Flow<Int> = dao.getTasksTodayCount(todayStart)
    fun getAllTasks(): Flow<List<ScheduledTaskEntity>> = dao.getAllTasks()

    suspend fun insertTask(task: ScheduledTaskEntity): Long = dao.insertTask(task)

    suspend fun updateTask(task: ScheduledTaskEntity) = dao.updateTask(task)
    
    suspend fun markCompleted(taskId: Int) = dao.markCompleted(taskId)
    
    suspend fun getTaskById(taskId: Int): ScheduledTaskEntity? = dao.getTaskById(taskId)
}
