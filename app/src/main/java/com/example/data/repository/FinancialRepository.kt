package com.example.data.repository

import com.example.data.ExpenseDao
import com.example.data.ExpenseEntity
import com.example.data.SubscriptionDao
import com.example.data.SubscriptionEntity
import kotlinx.coroutines.flow.Flow

class FinancialRepository(
    private val dbProvider: () -> com.example.data.AppDatabase
) {
    private val expenseDao get() = dbProvider().expenseDao()
    private val subscriptionDao get() = dbProvider().subscriptionDao()
    
    fun getAllExpenses(): Flow<List<ExpenseEntity>> = expenseDao.getAllExpenses()
    
    fun getTotalSpentFlow(): Flow<Double?> = expenseDao.getTotalSpentFlow()
    
    suspend fun insertExpense(expense: ExpenseEntity) = expenseDao.insert(expense)
    
    suspend fun clearExpenses() = expenseDao.clearAll()
    
    suspend fun clearSubscriptions() = subscriptionDao.clearAll()
    
    fun getFinancialSubscriptions(): Flow<List<SubscriptionEntity>> = subscriptionDao.getFinancialSubscriptions()
    
    suspend fun insertSubscription(sub: SubscriptionEntity) = subscriptionDao.insert(sub)
    
    suspend fun deleteSubscriptionById(id: Long) = subscriptionDao.deleteById(id)

    suspend fun getAllExpensesSync(): List<com.example.data.ExpenseEntity> = expenseDao.getAllExpensesSync()
    suspend fun getAllSubscriptionsSync(): List<com.example.data.SubscriptionEntity> = subscriptionDao.getAllSubscriptionsSync()

}
