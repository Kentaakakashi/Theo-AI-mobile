package com.example.viewmodel

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.AccountProfile
import com.example.data.firebase.FirebaseAccountRepository
import com.example.data.firebase.UsernameAlreadyTakenException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class AccountUiState(
    val isConfigured: Boolean = false,
    val isLoading: Boolean = true,
    val isBusy: Boolean = false,
    val userId: String? = null,
    val email: String? = null,
    val username: String? = null,
    val displayName: String = "",
    val error: String? = null
) {
    val isSignedIn: Boolean get() = !userId.isNullOrBlank()
    val isReady: Boolean get() = isSignedIn && !username.isNullOrBlank()
}

class AccountViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FirebaseAccountRepository.createIfConfigured(application)
    private val _uiState = MutableStateFlow(
        AccountUiState(
            isConfigured = repository != null,
            isLoading = repository != null
        )
    )
    val uiState = _uiState.asStateFlow()

    init {
        if (repository != null) {
            viewModelScope.launch {
                repository.authState.collectLatest { user ->
                    if (user == null) {
                        _uiState.value = AccountUiState(isConfigured = true, isLoading = false)
                    } else {
                        _uiState.value = AccountUiState(
                            isConfigured = true,
                            isLoading = true,
                            userId = user.uid,
                            email = user.email,
                            displayName = user.displayName.orEmpty()
                        )
                        try {
                            val profile = withTimeout(15_000) { repository.ensureUserProfile(user) }
                            _uiState.value = profile.toUiState()
                        } catch (error: TimeoutCancellationException) {
                            _uiState.value = AccountUiState(
                                isConfigured = true,
                                isLoading = false,
                                userId = user.uid,
                                email = user.email,
                                displayName = user.displayName.orEmpty(),
                                error = "Theo couldn't reach Firebase in time. Check your connection and tap retry."
                            )
                        } catch (error: Exception) {
                            if (error is CancellationException) throw error
                            _uiState.value = AccountUiState(
                                isConfigured = true,
                                isLoading = false,
                                userId = user.uid,
                                email = user.email,
                                displayName = user.displayName.orEmpty(),
                                error = readableError(error)
                            )
                        }
                    }
                }
            }
        }
    }

    fun signIn(activity: Activity) {
        val backend = repository ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, error = null)
            try {
                backend.signInWithGoogle(activity)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    error = readableError(error)
                )
            } finally {
                _uiState.value = _uiState.value.copy(isBusy = false)
            }
        }
    }

    fun saveProfile(username: String, displayName: String) {
        val backend = repository ?: return
        val uid = _uiState.value.userId ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, error = null)
            try {
                val profile = backend.completeProfile(uid, username, displayName)
                _uiState.value = profile.toUiState()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    error = readableError(error)
                )
            }
        }
    }

    private fun AccountProfile.toUiState(): AccountUiState = AccountUiState(
        isConfigured = true,
        isLoading = false,
        userId = uid,
        email = email,
        username = username,
        displayName = displayName
    )

    private fun readableError(error: Exception): String = when (error) {
        is UsernameAlreadyTakenException -> "That username is already taken. Try another one."
        is IllegalArgumentException -> error.message ?: "Please check the account details."
        else -> error.localizedMessage
            ?.takeIf { it.isNotBlank() }
            ?.take(180)
            ?: "Theo couldn't connect to your account. Check your connection and Firebase setup."
    }
}
