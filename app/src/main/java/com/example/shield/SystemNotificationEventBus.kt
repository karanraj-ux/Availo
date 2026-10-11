package com.example.shield

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object SystemNotificationEventBus {
    private val _events = MutableSharedFlow<SystemEvent>(replay = 1)
    val events: SharedFlow<SystemEvent> = _events.asSharedFlow()

    suspend fun emitEvent(event: SystemEvent) {
        _events.emit(event)
    }
}

sealed class SystemEvent {
    data class IncomingCallSuspicious(val phoneNumber: String, val reason: String) : SystemEvent()
    data class IncomingSmsSuspicious(val sender: String, val message: String) : SystemEvent()
}
