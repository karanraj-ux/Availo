package com.example.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomRuleDao {
    @Query("SELECT * FROM custom_rules")
    fun getAllRules(): Flow<List<CustomRule>>
    
    @Query("SELECT * FROM custom_rules")
    suspend fun getAllRulesSync(): List<CustomRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: CustomRule)

    @Delete
    suspend fun deleteRule(rule: CustomRule)
}
