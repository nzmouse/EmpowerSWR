package com.empowerswr.test

import android.app.Application
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.UnknownHostException

class EmpowerViewModel(application: Application) : AndroidViewModel(application) {
    private val _token = mutableStateOf<String?>(null)
    val token: State<String?> = _token

    private val _loginError = mutableStateOf<String?>(null)
    val loginError: State<String?> = _loginError

    private val _workerDetails = mutableStateOf<WorkerResponse?>(null)
    val workerDetails: State<WorkerResponse?> = _workerDetails

    private val _alerts = mutableStateOf<List<Alert>>(emptyList())
    val alerts: State<List<Alert>> = _alerts

    private val _checkInSuccess = mutableStateOf<Boolean?>(null)
    val checkInSuccess: State<Boolean?> = _checkInSuccess

    private val _checkInError = mutableStateOf<String?>(null)
    val checkInError: State<String?> = _checkInError

    private val _notifications = mutableStateOf<List<Notification>>(emptyList())
    val notifications: State<List<Notification>> = _notifications

    private val _notificationFromIntent = mutableStateOf(Pair<String?, String?>(null, null))
    val notificationFromIntent: State<Pair<String?, String?>> = _notificationFromIntent

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://db.nougro.com/api/api.php/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val api = retrofit.create(EmpowerApi::class.java)

    init {
        val savedToken = PrefsHelper.getToken(getApplication())
        val savedTokenExpiry = PrefsHelper.getTokenExpiry(getApplication())
        Log.d("EmpowerSWR", "init - Saved token: $savedToken, expiry: $savedTokenExpiry")
        if (savedToken != null && savedTokenExpiry != null) {
            val currentTime = System.currentTimeMillis() / 1000
            if (savedTokenExpiry > currentTime) {
                _token.value = savedToken
                Log.d("EmpowerSWR", "init - Restored valid token: $savedToken")
                fetchWorkerDetails()
                fetchAlerts()
            } else {
                Log.d("EmpowerSWR", "init - Saved token expired, clearing")
                PrefsHelper.clearToken(getApplication())
                _token.value = null
            }
        }

        viewModelScope.launch {
            NotificationHandler.notificationFlow.collect { notificationData ->
                Log.d("EmpowerSWR", "Received notificationData in ViewModel: ${notificationData.first}: ${notificationData.second}")
                val notification = Notification(notificationData.first ?: "", notificationData.second ?: "")
                _notifications.value = _notifications.value.toMutableList().apply { add(notification) }
            }
        }
    }

    fun register(passport: String, surname: String, pin: String) {
        viewModelScope.launch {
            try {
                val response = api.register(RegistrationRequest(passport, surname, pin))
                _token.value = response.token
                PrefsHelper.saveWorkerId(getApplication(), response.workerId)
                PrefsHelper.saveToken(getApplication(), response.token, response.expiry)
                PrefsHelper.setRegistered(getApplication(), true)
                _loginError.value = null
                fetchWorkerDetails()
                fetchAlerts()
            } catch (e: Exception) {
                _loginError.value = when (e) {
                    is UnknownHostException -> "Registration failed: Unable to connect to server."
                    is HttpException -> "Registration failed: HTTP ${e.code()} - ${e.message()}"
                    else -> "Registration failed: ${e.message}"
                }
                Log.e("EmpowerSWR", "Registration error: ${e.message}", e)
            }
        }
    }

    fun login(workerId: String, pin: String) {
        viewModelScope.launch {
            try {
                val response = api.login(LoginRequest(workerId, pin))
                _token.value = response.token
                PrefsHelper.saveWorkerId(getApplication(), response.workerId)
                PrefsHelper.saveToken(getApplication(), response.token, response.expiry)
                _loginError.value = null
                fetchWorkerDetails()
                fetchAlerts()
            } catch (e: Exception) {
                _loginError.value = when (e) {
                    is UnknownHostException -> "Login failed: Unable to connect to server."
                    is HttpException -> "Login failed: HTTP ${e.code()} - ${e.message()}"
                    else -> "Login failed: ${e.message}"
                }
                Log.e("EmpowerSWR", "Login error: ${e.message}", e)
            }
        }
    }

    fun updateFcmToken(fcmToken: String, workerId: String) {
        viewModelScope.launch {
            try {
                api.updateFcmToken(workerId, fcmToken)
                Log.d("EmpowerSWR", "FCM token updated for workerId: $workerId")
            } catch (e: Exception) {
                Log.e("EmpowerSWR", "Failed to update FCM token: ${e.message}", e)
            }
        }
    }

    fun setToken(token: String?) {
        Log.d("EmpowerSWR", "setToken - Setting token: $token")
        _token.value = token
        if (token != null) {
            val expiry = (System.currentTimeMillis() / 1000) + 24 * 60 * 60 // 24 hours
            PrefsHelper.saveToken(getApplication(), token, expiry)
        } else {
            PrefsHelper.clearToken(getApplication())
        }
    }

    fun setNotificationFromIntent(title: String?, body: String?) {
        Log.d("EmpowerSWR", "setNotificationFromIntent - Setting: $title: $body")
        _notificationFromIntent.value = title to body
    }

    fun removeNotification(notification: Notification) {
        _notifications.value = _notifications.value.toMutableList().apply { remove(notification) }
    }

    fun fetchWorkerDetails() {
        viewModelScope.launch {
            try {
                val token = _token.value ?: throw IllegalStateException("No token available")
                val response = api.getWorkerDetails(token)
                _workerDetails.value = response
                Log.d("EmpowerSWR", "fetchWorkerDetails - Success: ${response.givenName} ${response.surname}")
            } catch (e: Exception) {
                Log.e("EmpowerSWR", "fetchWorkerDetails error: ${e.message}", e)
            }
        }
    }

    fun fetchAlerts() {
        viewModelScope.launch {
            try {
                val token = _token.value ?: throw IllegalStateException("No token available")
                val response = api.getAlerts(token)
                _alerts.value = response
                Log.d("EmpowerSWR", "fetchAlerts - Success, alerts count: ${response.size}")
            } catch (e: Exception) {
                Log.e("EmpowerSWR", "fetchAlerts error: ${e.message}", e)
            }
        }
    }

    fun checkIn(phone: String) {
        viewModelScope.launch {
            try {
                val token = _token.value ?: throw IllegalStateException("No token available")
                val response = api.checkIn(CheckInRequest(phone))
                _checkInSuccess.value = response.success
                _checkInError.value = null
                Log.d("EmpowerSWR", "checkIn - Success: ${response.success}")
            } catch (e: Exception) {
                _checkInError.value = "Check-in failed: ${e.message}"
                _checkInSuccess.value = false
                Log.e("EmpowerSWR", "checkIn error: ${e.message}", e)
            }
        }
    }

    fun logout() {
        Log.d("EmpowerSWR", "logout - Clearing token")
        _token.value = null
        _loginError.value = null
        _workerDetails.value = null
        _alerts.value = emptyList()
        _notifications.value = emptyList()
        _notificationFromIntent.value = null to null
        _checkInSuccess.value = null
        _checkInError.value = null
        PrefsHelper.clearToken(getApplication())
    }
}