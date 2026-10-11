package com.example

import com.example.data.repository.RuleRepository
import com.example.db.CustomRule
import com.example.db.CustomRuleDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RuleRepositoryTest {

    // Simple Fake DAO for testing
    class FakeCustomRuleDao : CustomRuleDao {
        val rules = mutableListOf<CustomRule>()
        
        override fun getAllRules(): Flow<List<CustomRule>> = flowOf(rules)
        override suspend fun getAllRulesSync(): List<CustomRule> = rules
        override suspend fun insertRule(rule: CustomRule) { rules.add(rule) }
        override suspend fun deleteRule(rule: CustomRule) { rules.remove(rule) }
    }

    @Test
    fun `test insert and get rules`() = runBlocking {
        val dao = FakeCustomRuleDao()
        val repository = RuleRepository(dao)

        val newRule = CustomRule(trigger = "Bank", action = "Forward via SMS")
        repository.insertRule(newRule)

        val allRules = repository.getAllRulesSync()
        assertEquals(1, allRules.size)
        assertEquals("Bank", allRules[0].trigger)
        assertEquals("Forward via SMS", allRules[0].action)
    }

    @Test
    fun `test delete rule`() = runBlocking {
        val dao = FakeCustomRuleDao()
        val repository = RuleRepository(dao)

        val newRule = CustomRule(trigger = "Scam", action = "Delete")
        repository.insertRule(newRule)
        repository.deleteRule(newRule)

        val allRules = repository.getAllRulesSync()
        assertEquals(0, allRules.size)
    }
}
