package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val merchant: String,
    val amountStr: String,
    val amountVal: Double,
    val dateDetected: Long,
    val source: String,
    val originalMessage: String = ""
)
