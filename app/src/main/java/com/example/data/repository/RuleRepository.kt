package com.example.data.repository

import com.example.db.CustomRule
import com.example.db.CustomRuleDao
import kotlinx.coroutines.flow.Flow

class RuleRepository(private val ruleDao: CustomRuleDao) {
    val allRules: Flow<List<CustomRule>> = ruleDao.getAllRules()

    suspend fun getAllRulesSync(): List<CustomRule> = ruleDao.getAllRulesSync()

    suspend fun insertRule(rule: CustomRule) {
        ruleDao.insertRule(rule)
    }

    suspend fun deleteRule(rule: CustomRule) {
        ruleDao.deleteRule(rule)
    }
}
