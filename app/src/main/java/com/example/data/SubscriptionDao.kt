package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions WHERE isNewsletter = 0 ORDER BY dateDetected DESC")
    fun getFinancialSubscriptions(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions WHERE isNewsletter = 1 ORDER BY dateDetected DESC")
    fun getNewsletters(): Flow<List<SubscriptionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(subscription: SubscriptionEntity)

    @Query("DELETE FROM subscriptions WHERE id = :id")
    suspend fun deleteById(id: Long)
    
    @Query("DELETE FROM subscriptions")
    suspend fun clearAll()

    @Query("SELECT * FROM subscriptions")
    suspend fun getAllSubscriptionsSync(): List<SubscriptionEntity>

}
