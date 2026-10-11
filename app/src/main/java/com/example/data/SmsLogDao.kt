package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsLogDao {
    @Insert
    suspend fun insert(log: SmsLogEntity)

    @Query("SELECT * FROM sms_logs ORDER BY timestamp DESC LIMIT 100")
    fun getRecentLogs(): Flow<List<SmsLogEntity>>

    @Query("SELECT COUNT(*) FROM sms_logs WHERE status IN ('SUCCESS', 'CALL_FORWARDED') AND timestamp >= :todayStart")
    fun getForwardedCountToday(todayStart: Long): Flow<Int>
    
    @Query("SELECT COUNT(*) FROM sms_logs WHERE status IN ('SUCCESS', 'CALL_FORWARDED')")
    fun getTotalForwardedCount(): Flow<Int>

    @Query("DELETE FROM sms_logs")
    suspend fun clearLogs()

    @Query("DELETE FROM sms_logs WHERE id NOT IN (SELECT id FROM sms_logs ORDER BY timestamp DESC LIMIT 500)")
    suspend fun deleteOldLogs()

    @Query("SELECT * FROM sms_logs ORDER BY timestamp DESC")
    suspend fun getAllLogsSync(): List<SmsLogEntity>
}
