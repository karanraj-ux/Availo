package com.example.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.PhoneRuleEntity
import com.example.data.repository.PhoneRuleRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PhoneRuleViewModel(private val repository: PhoneRuleRepository) : ViewModel() {

    val rules: StateFlow<List<PhoneRuleEntity>> = repository.getAllRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addRule(number: String, contactName: String = "", tier: String = "Standard", isDivert: Boolean = false, isForward: Boolean = false) {
        viewModelScope.launch {
            val isVip = (tier == "Inner Circle")
            repository.insert(PhoneRuleEntity(phoneNumber = number, contactName = contactName, relationshipTier = tier, isVip = isVip, isDivert = isDivert, isForward = isForward))
        }
    }

    fun removeRule(rule: PhoneRuleEntity) {
        viewModelScope.launch {
            repository.delete(rule)
        }
    }

    class Factory(private val repository: PhoneRuleRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PhoneRuleViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return PhoneRuleViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
