package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY dateDetected DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expense: ExpenseEntity)

    @Query("DELETE FROM expenses")
    suspend fun clearAll()
    
    @Query("SELECT SUM(amountVal) FROM expenses")
    fun getTotalSpentFlow(): Flow<Double?>

    @Query("SELECT * FROM expenses")
    suspend fun getAllExpensesSync(): List<ExpenseEntity>

}
