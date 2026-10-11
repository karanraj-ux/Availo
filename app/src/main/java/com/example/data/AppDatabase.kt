package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import com.example.utils.SecurityUtils

class DeferredSupportFactory(private val context: Context) : SupportSQLiteOpenHelper.Factory {
    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
        SQLiteDatabase.loadLibs(context.applicationContext)
        val passphrase = SecurityUtils.getDatabasePassphrase(context.applicationContext)
        val factory = SupportFactory(passphrase)
        return factory.create(configuration)
    }
}

@Database(entities = [com.example.data.PhoneRuleEntity::class, SmsLogEntity::class, SubscriptionEntity::class, ExpenseEntity::class, ChatMessageEntity::class, com.example.db.CustomRule::class, ScheduledTaskEntity::class], version = 13, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun smsLogDao(): SmsLogDao
    
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun customRuleDao(): com.example.db.CustomRuleDao
    abstract fun phoneRuleDao(): com.example.data.PhoneRuleDao
    abstract fun scheduledTaskDao(): ScheduledTaskDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val factory = DeferredSupportFactory(context)
                
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sms_forwarder_database"
                )
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration(true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
