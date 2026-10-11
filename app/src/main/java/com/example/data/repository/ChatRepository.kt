package com.example.data.repository

import com.example.data.ChatMessageDao
import com.example.data.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

class ChatRepository(private val dbProvider: () -> com.example.data.AppDatabase) {
    private val dao get() = dbProvider().chatMessageDao()
    
    fun getAllMessages(): Flow<List<ChatMessageEntity>> = dao.getAllMessages()
    
    suspend fun insertMessage(message: ChatMessageEntity) = dao.insertMessage(message)
    
    suspend fun clearAllMessages() = dao.clearAllMessages()

    suspend fun getAllMessagesSync(): List<com.example.data.ChatMessageEntity> = dao.getAllMessagesSync()

}
