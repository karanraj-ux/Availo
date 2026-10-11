package com.example.data.repository

import com.example.data.SmsLogDao
import com.example.data.SmsLogEntity
import kotlinx.coroutines.flow.Flow

class SmsRepository(private val dbProvider: () -> com.example.data.AppDatabase) {
    private val dao get() = dbProvider().smsLogDao()
    
    fun getRecentLogs(): Flow<List<SmsLogEntity>> = dao.getRecentLogs()
    
    fun getForwardedCountToday(todayStart: Long): Flow<Int> = dao.getForwardedCountToday(todayStart)
    
    fun getTotalForwardedCount(): Flow<Int> = dao.getTotalForwardedCount()
    
    suspend fun clearLogs() = dao.clearLogs()
    
    suspend fun getAllLogsSync(): List<SmsLogEntity> = dao.getAllLogsSync()

    suspend fun insertLog(log: SmsLogEntity) {
        dao.insert(log)
        try {
            dao.deleteOldLogs()
        } catch (e: Exception) {
            // Ignore if method not yet available
        }
    }
}
