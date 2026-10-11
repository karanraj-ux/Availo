package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PhoneRuleDao {
    @Query("SELECT * FROM phone_rules")
    fun getAllRules(): Flow<List<PhoneRuleEntity>>
    @Query("SELECT * FROM phone_rules")
    fun getAllRulesSync(): List<PhoneRuleEntity>
    
    @Query("SELECT * FROM phone_rules WHERE phoneNumber = :number LIMIT 1")
    fun getRuleByNumber(number: String): PhoneRuleEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: PhoneRuleEntity)
    
    @Delete
    suspend fun delete(rule: PhoneRuleEntity)
}
