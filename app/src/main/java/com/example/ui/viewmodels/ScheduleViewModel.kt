package com.example.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ScheduledTaskEntity
import com.example.data.repository.ScheduledTaskRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ScheduleViewModel(private val repository: ScheduledTaskRepository) : ViewModel() {
    val scheduledTasks: StateFlow<List<ScheduledTaskEntity>> = repository.getAllTasks()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addTask(type: String, target: String, message: String?, timeMillis: Long, isRecurring: Boolean = false, intervalMillis: Long = 0L, onTaskAdded: (Long) -> Unit) {
        viewModelScope.launch {
            val taskId = repository.insertTask(
                ScheduledTaskEntity(type = type, target = target, message = message, timeMillis = timeMillis, isRecurring = isRecurring, recurringIntervalMillis = intervalMillis)
            )
            onTaskAdded(taskId)
        }
    }

    class Factory(private val repository: ScheduledTaskRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ScheduleViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return ScheduleViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
