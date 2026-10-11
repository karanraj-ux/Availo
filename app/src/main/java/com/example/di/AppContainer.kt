package com.example.di

import android.content.Context
import com.example.data.AppDatabase
import com.example.data.repository.ChatRepository
import com.example.data.repository.FinancialRepository
import com.example.data.repository.SmsRepository
import com.example.data.repository.RuleRepository
import com.example.data.repository.SettingsRepository
import com.example.data.repository.PhoneRuleRepository
import com.example.data.repository.ScheduledTaskRepository

interface AppContainer {
    val database: AppDatabase
    val smsRepository: SmsRepository
    val financialRepository: FinancialRepository
    val chatRepository: ChatRepository
    val ruleRepository: RuleRepository
    val settingsRepository: SettingsRepository
    val phoneRuleRepository: PhoneRuleRepository
    val scheduledTaskRepository: ScheduledTaskRepository
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    override val database: AppDatabase by lazy {
        AppDatabase.getDatabase(context)
    }
    
    override val smsRepository: SmsRepository by lazy {
        SmsRepository { database }
    }
    
    override val financialRepository: FinancialRepository by lazy {
        FinancialRepository( { database } )
    }
    
    override val chatRepository: ChatRepository by lazy {
        ChatRepository { database }
    }
    
    override val ruleRepository: RuleRepository by lazy {
        RuleRepository(database.customRuleDao())
    }
    
    override val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(context)
    }
    
    override val phoneRuleRepository: PhoneRuleRepository by lazy {
        PhoneRuleRepository { database }
    }

    override val scheduledTaskRepository: ScheduledTaskRepository by lazy {
        ScheduledTaskRepository(database.scheduledTaskDao())
    }
}
