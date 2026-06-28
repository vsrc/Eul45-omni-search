package com.omnisearch.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.omnisearch.app.data.PreferencesManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SecurityViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesManager(application.applicationContext)

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val _isPinSet = MutableStateFlow(false)
    val isPinSet: StateFlow<Boolean> = _isPinSet.asStateFlow()

    private val _isBiometricEnabled = MutableStateFlow(false)
    val isBiometricEnabled: StateFlow<Boolean> = _isBiometricEnabled.asStateFlow()

    private val _enteredPin = MutableStateFlow("")
    val enteredPin: StateFlow<String> = _enteredPin.asStateFlow()

    private val _pinValidationState = MutableStateFlow<PinValidationState>(PinValidationState.Idle)
    val pinValidationState: StateFlow<PinValidationState> = _pinValidationState.asStateFlow()

    var savedPin: String = ""
        private set

    private var hasInitializedAppLock = false

    init {
        viewModelScope.launch {
            combine(
                prefs.appLockPinFlow,
                prefs.appLockBiometricFlow
            ) { pin, bioEnabled ->
                savedPin = pin
                _isPinSet.value = pin.isNotEmpty()
                _isBiometricEnabled.value = bioEnabled
                
                // If PIN is enabled and it is first launch, ensure we lock
                if (!hasInitializedAppLock) {
                    if (pin.isNotEmpty()) {
                        _isLocked.value = true
                    }
                    hasInitializedAppLock = true
                }
            }.collect()
        }
    }

    fun triggerLockOnResume() {
        if (_isPinSet.value) {
            _isLocked.value = true
            _enteredPin.value = ""
            _pinValidationState.value = PinValidationState.Idle
        }
    }

    fun enterPinDigit(digit: String) {
        if (_enteredPin.value.length < 4) {
            _enteredPin.value += digit
            if (_enteredPin.value.length == 4) {
                validatePin()
            }
        }
    }

    fun removeLastPinDigit() {
        if (_enteredPin.value.isNotEmpty()) {
            _enteredPin.value = _enteredPin.value.dropLast(1)
            _pinValidationState.value = PinValidationState.Idle
        }
    }

    private fun validatePin() {
        if (_enteredPin.value == savedPin) {
            _isLocked.value = false
            _pinValidationState.value = PinValidationState.Success
            _enteredPin.value = ""
        } else {
            _pinValidationState.value = PinValidationState.Error("Incorrect PIN")
            _enteredPin.value = ""
        }
    }

    fun bypassWithBiometrics() {
        _isLocked.value = false
        _pinValidationState.value = PinValidationState.Success
        _enteredPin.value = ""
    }

    // Setters for Screen Settings Toggle
    fun setAppLockPin(pin: String) {
        viewModelScope.launch {
            prefs.saveAppLockPin(pin)
        }
    }

    fun disableAppLock() {
        viewModelScope.launch {
            prefs.removeAppLockPin()
        }
    }

    fun toggleBiometric(enabled: Boolean) {
        viewModelScope.launch {
            prefs.saveAppLockBiometric(enabled)
        }
    }

    fun resetValidation() {
        _pinValidationState.value = PinValidationState.Idle
    }
}

sealed interface PinValidationState {
    object Idle : PinValidationState
    object Success : PinValidationState
    data class Error(val message: String) : PinValidationState
}
