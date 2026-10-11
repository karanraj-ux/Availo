package com.example.data.repository

import com.example.data.PhoneRuleEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

class PhoneRuleRepository(private val dbProvider: () -> com.example.data.AppDatabase) {
    private val dao get() = dbProvider().phoneRuleDao()
    
    @Volatile
    private var rulesCache: List<PhoneRuleEntity>? = null
    
    fun getAllRules(): Flow<List<PhoneRuleEntity>> = dao.getAllRules().onEach { rulesCache = it }
    
    fun getAllRulesSync(): List<PhoneRuleEntity> {
        return rulesCache ?: run {
            val list = dao.getAllRulesSync()
            rulesCache = list
            list
        }
    }
    
    suspend fun getRuleByNumber(number: String): PhoneRuleEntity? = dao.getRuleByNumber(number)
    
    suspend fun insert(rule: PhoneRuleEntity) {
        dao.insert(rule)
        rulesCache = null
    }
    
    suspend fun delete(rule: PhoneRuleEntity) {
        dao.delete(rule)
        rulesCache = null
    }
}
