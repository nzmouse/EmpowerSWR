package com.empowerswr.test

import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.empowerswr.test.WorkerHistory

class EmpowerViewModel(private val api: EmpowerApi) : ViewModel() {
    private val _token = mutableStateOf<String?>(null)
    private val _workerId = mutableStateOf<String?>(null)
    private val _workerDetails = mutableStateOf<WorkerResponse?>(null)
    private val _workerHistory = mutableStateOf<List<WorkerHistory>>(emptyList())
    private val _errorMessage = mutableStateOf<String?>(null)
    private val _loginError = mutableStateOf<String?>(null)
    private val _alerts = mutableStateOf<List<Alert>>(emptyList())
    private val _notifications = mutableStateOf<List<Notification>>(emptyList())
    private val _notificationFromIntent = mutableStateOf(Pair<String?, String?>(null, null))

    val token: State<String?> = _token
    val workerId: State<String?> = _workerId
    val workerDetails: State<WorkerResponse?> = _workerDetails
    val workerHistory: State<List<WorkerHistory>> = _workerHistory
    val errorMessage: State<String?> = _errorMessage
    val loginError: State<String?> = _loginError
    val alerts: State<List<Alert>> = _alerts
    val notifications: State<List<Notification>> = _notifications
    val notificationFromIntent: State<Pair<String?, String?>> = _notificationFromIntent

    fun setToken(token: String?) {
        _token.value = token
        Log.d("EmpowerSWR", "Token set: $token")
    }

    fun setWorkerId(id: String) {
        _workerId.value = id
        Log.d("EmpowerSWR", "WorkerId set: $id")
    }

    fun setErrorMessage(message: String?) {
        _errorMessage.value = message
        Log.d("EmpowerSWR", "Error message set: $message")
    }

    fun login(workerId: String, pin: String) {
        viewModelScope.launch {
            try {
                Log.d("EmpowerSWR", "Attempting login with workerId: $workerId")
                val response = api.login(LoginRequest(workerId, pin))
                _token.value = response.token
                _workerId.value = response.workerId
                _loginError.value = null
                Log.d("EmpowerSWR", "Login successful: token=${response.token}, workerId=${response.workerId}")
            } catch (e: Exception) {
                _loginError.value = e.message
                Log.e("EmpowerSWR", "Login error: ${e.message}", e)
            }
        }
    }

    fun register(passport: String, surname: String, pin: String) {
        viewModelScope.launch {
            try {
                Log.d("EmpowerSWR", "Attempting registration with passport: $passport, surname: $surname")
                val response = api.register(RegistrationRequest(passport, surname, pin))
                _token.value = response.token
                _workerId.value = response.workerId
                Log.d("EmpowerSWR", "Registration successful: token=${response.token}, workerId=${response.workerId}")
            } catch (e: Exception) {
                Log.e("EmpowerSWR", "Registration error: ${e.message}", e)
            }
        }
    }

    fun fetchWorkerDetails() {
        viewModelScope.launch {
            try {
                val token = _token.value ?: throw IllegalStateException("No token available")
                Log.d("EmpowerSWR", "Fetching worker details with token: $token")
                val response = api.getWorkerDetails(token)
                Log.d("EmpowerSWR", "WorkerResponse raw: $response")
                _workerDetails.value = response
                _workerId.value = response.id
                Log.d("EmpowerSWR", "fetchWorkerDetails - Success: ${response.firstName} ${response.surname}")
            } catch (e: Exception) {
                _errorMessage.value = e.message
                Log.e("EmpowerSWR", "fetchWorkerDetails error: ${e.message}", e)
            }
        }
    }

    fun fetchWorkerHistory() {
        viewModelScope.launch {
            try {
                val workerId = _workerId.value ?: throw IllegalStateException("No workerId available")
                Log.d("EmpowerSWR", "Fetching history for workerId: $workerId")
                _workerHistory.value = emptyList()
                val response = api.getWorkerHistory(workerId)
                Log.d("EmpowerSWR", "WorkerHistory raw: $response")
                _workerHistory.value = response
                Log.d("EmpowerSWR", "fetchWorkerHistory - Success: Fetched ${response.size} history records")
            } catch (e: Exception) {
                _errorMessage.value = e.message
                Log.e("EmpowerSWR", "fetchWorkerHistory error: ${e.message}", e)
            }
        }
    }

    fun updateFcmToken(fcmToken: String, workerId: String) {
        viewModelScope.launch {
            try {
                api.updateFcmToken(workerId, fcmToken)
                Log.d("EmpowerSWR", "FCM token updated for workerId: $workerId")
            } catch (e: Exception) {
                Log.e("EmpowerSWR", "FCM token update error: ${e.message}", e)
            }
        }
    }

    fun fetchAlerts() {
        viewModelScope.launch {
            try {
                val token = _token.value ?: throw IllegalStateException("No token available")
                val response = api.getAlerts(token)
                _alerts.value = response
                Log.d("EmpowerSWR", "fetchAlerts - Success: Fetched ${response.size} alerts")
            } catch (e: Exception) {
                _errorMessage.value = e.message
                Log.e("EmpowerSWR", "fetchAlerts error: ${e.message}", e)
            }
        }
    }

    fun removeNotification(notification: Notification) {
        _notifications.value = _notifications.value - notification
    }

    fun setNotificationFromIntent(title: String?, body: String?) {
        _notificationFromIntent.value = Pair(title, body)
    }
}