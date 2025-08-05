package com.empowerswr.test

import android.app.Application
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.JsonParseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.UnknownHostException
import java.util.Base64
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

class EmpowerViewModel(application: Application) : AndroidViewModel(application) {
    private val _token = mutableStateOf<String?>(null)
    val token: State<String?> = _token

    private val _loginError = mutableStateOf<String?>(null)
    val loginError: State<String?> = _loginError

    private val _workerDetails = mutableStateOf<WorkerResponse?>(null)
    val workerDetails: State<WorkerResponse?> = _workerDetails

    private val _history = mutableStateOf<List<HistoryResponse>>(emptyList())
    val history: State<List<HistoryResponse>> = _history

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

    private val logging = HttpLoggingInterceptor().apply {
        setLevel(HttpLoggingInterceptor.Level.BODY)
    }
    private val client = OkHttpClient.Builder()
        .addInterceptor(logging)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://db.nougro.com/api/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    private val api = retrofit.create(EmpowerApi::class.java)

    private val gson = Gson()

    private val _pendingFields = mutableStateOf<Set<String>>(emptySet())
    val pendingFields: State<Set<String>> = _pendingFields  // Expose for UI

    private val _flightDetails = mutableStateOf<FlightDetails?>(null)
    val flightDetails: State<FlightDetails?> = _flightDetails

    private val _pdbDetails = mutableStateOf<PdbDetails?>(null)
    val pdbDetails: State<PdbDetails?> = _pdbDetails

    private val _internalPdbDetails = mutableStateOf<PdbDetails?>(null)
    val internalPbDetails: State<PdbDetails?> = _internalPdbDetails

    private val _directoryEntries = MutableStateFlow<List<DirectoryEntry>>(emptyList())
    val directoryEntries: StateFlow<List<DirectoryEntry>> = _directoryEntries

    init {
        val savedToken = PrefsHelper.getToken(getApplication())
        val savedTokenExpiry = PrefsHelper.getTokenExpiry(getApplication())
        Log.d("EmpowerSWR", "init - Saved token: $savedToken, expiry: $savedTokenExpiry")
        if (savedToken != null && savedTokenExpiry != null) {
            val currentTime = System.currentTimeMillis() / 1000
            if (savedTokenExpiry > currentTime && isValidJwt(savedToken)) {
                _token.value = savedToken
                _token.value = savedToken
                Log.d("EmpowerSWR", "init - Restored valid token: $savedToken")

            } else {
                Log.d("EmpowerSWR", "init - Saved token expired or invalid, clearing")
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

    private fun isValidJwt(token: String): Boolean {
        return token.split(".").size == 3
    }

    private fun getWorkerIdFromJwt(token: String?): String? {
        if (token == null || !isValidJwt(token)) return null
        try {
            val payload = token.split(".")[1]
            val decodedPayload = String(Base64.getUrlDecoder().decode(payload))
            val json = gson.fromJson(decodedPayload, Map::class.java)
            return json["worker_id"]?.toString()
        } catch (e: Exception) {
            Log.e("EmpowerSWR", "Failed to decode JWT: ${e.message}")
            return null
        }
    }

    fun register(passport: String, surname: String, pin: String) {
        viewModelScope.launch {
            try {
                val request = RegistrationRequest(passport, surname, pin)
                Log.d("EmpowerSWR", "Sending registration request: $request")
                val response: Response<RegistrationResponse> = api.register(request)
                Log.d("EmpowerSWR", "Registration response: code=${response.code()}, body=${response.body()}")
                val errorBody = response.errorBody()?.string() // Store errorBody once
                Log.d("EmpowerSWR", "Raw error body: $errorBody")
                if (response.isSuccessful) {
                    response.body()?.let { tokenResponse ->
                        _token.value = tokenResponse.token
                        PrefsHelper.saveWorkerId(getApplication(), tokenResponse.workerId)
                        PrefsHelper.saveToken(getApplication(), tokenResponse.token, tokenResponse.expiry)
                        Log.d("EmpowerSWR", "Registration successful: workerId=${tokenResponse.workerId}")
                        fetchWorkerDetails()
                        fetchHistory()
                        fetchAlerts()
                    }
                } else {
                    val errorMessage = errorBody?.let {
                        try {
                            val jsonObject = JSONObject(it)
                            jsonObject.optString("error", "Registration failed: Unknown error")
                        } catch (e: Exception) {
                            Log.e("EmpowerSWR", "Failed to parse error JSON: ${e.message}, errorBody=$errorBody")
                            "Registration failed: Invalid server response"
                        }
                    } ?: "Registration failed: HTTP ${response.code()}"
                    _loginError.value = errorMessage
                    Log.e("EmpowerSWR", "Registration error: HTTP ${response.code()}, errorMessage=$errorMessage")
                }
            } catch (e: JsonParseException) {
                _loginError.value = "Registration failed: Invalid server response"
                Log.e("EmpowerSWR", "JSON parsing error: ${e.message}", e)
            } catch (e: HttpException) {
                _loginError.value = "Registration failed: HTTP ${e.code()} - ${e.message()}"
                Log.e("EmpowerSWR", "Registration failed: ${e.message}", e)
            } catch (e: UnknownHostException) {
                _loginError.value = "Registration failed: Unable to connect to server"
                Log.e("EmpowerSWR", "Registration failed: ${e.message}", e)
            } catch (e: Exception) {
                _loginError.value = "Registration failed: ${e.message}"
                Log.e("EmpowerSWR", "Registration failed: ${e.message}", e)
            }
        }
    }

    fun login(workerId: String, pin: String) {
        viewModelScope.launch {
            try {
                val response = api.login(LoginRequest(workerId, pin))
                Log.d("EmpowerSWR", "login - Received token: ${response.token}")
                if (!isValidJwt(response.token)) {
                    throw IllegalStateException("Invalid JWT token received from login")
                }
                _token.value = response.token
                PrefsHelper.saveWorkerId(getApplication(), response.workerId)
                PrefsHelper.saveToken(getApplication(), response.token, response.expiry)
                _loginError.value = null
                fetchWorkerDetails()
                fetchHistory()
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
        if (token != null && isValidJwt(token)) {
            _token.value = token
            val expiry = (System.currentTimeMillis() / 1000) + 24 * 60 * 60 // 24 hours
            PrefsHelper.saveToken(getApplication(), token, expiry)
            fetchWorkerDetails()
            fetchHistory()
            fetchAlerts()
        } else {
            Log.w("EmpowerSWR", "Invalid or null token, clearing")
            PrefsHelper.clearToken(getApplication())
            _token.value = null
            _workerDetails.value = null
            _history.value = emptyList()
            _alerts.value = emptyList()
        }
    }

    fun setNotificationFromIntent(title: String?, body: String?) {
        Log.d("EmpowerSWR", "setNotificationFromIntent - Setting: $title: $body")
        _notificationFromIntent.value = title to body
    }

    fun removeNotification(notification: Notification) {
        _notifications.value = _notifications.value.toMutableList().apply { remove(notification) }
    }

    fun fetchWorkerDetails(onError: ((Throwable?) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = _token.value ?: throw IllegalStateException("No token available")
                Log.d("EmpowerSWR", "fetchWorkerDetails - Sending token: $token")
                val response = api.getWorkerDetails(token)
                _workerDetails.value = response
                PrefsHelper.saveWorkerDetails(getApplication(), response.firstName, response.surname)
                Log.d("EmpowerSWR", "fetchWorkerDetails - Success: ${response.firstName ?: "Unknown"} ${response.surname ?: "Unknown"}, notices=${response.notices}")
                onError?.invoke(null)
            } catch (e: Exception) {
                Log.e("EmpowerSWR", "fetchWorkerDetails error: ${e.message}", e)
                onError?.invoke(e)
                if (e.message?.contains("Invalid JWT") == true) {
                    logout()
                }
                // Do not set _workerDetails.value = null -- keep old data
            }
        }
    }


    fun fetchHistory(onError: ((Throwable) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val workerId = PrefsHelper.getWorkerId(getApplication())
                    ?: throw IllegalStateException("No workerId available")
                Log.d("EmpowerSWR", "fetchHistory - Sending workerId: $workerId")
                val response = api.getWorkerHistory(workerId)
                _history.value = response
                Log.d("EmpowerSWR", "fetchHistory - Success, history count: ${response.size}")
            } catch (e: Exception) {
                Log.e("EmpowerSWR", "fetchHistory error: ${e.message}", e)
                _history.value = emptyList()
                onError?.invoke(e)
            }
        }
    }

    fun fetchAlerts() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = _token.value ?: throw IllegalStateException("No token available")
                Log.d("EmpowerSWR", "fetchAlerts - sending token: $token")
                val response = api.getAlerts(token)
                _alerts.value = response
                Log.d("EmpowerSWR", "fetchAlerts - Success, alerts count: ${response.size}")
            } catch (e: Exception) {
                Log.e("EmpowerSWR", "fetchAlerts error: ${e.message}", e)
                _alerts.value = emptyList()
                if (e.message?.contains("Invalid JWT") == true) {
                    logout()
                }
            }
        }
    }

    fun checkIn(phone: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!phone.matches(Regex("^\\d{7,15}$"))) {
                    throw IllegalArgumentException("Phone number must be 7-15 digits")
                }
                val token = _token.value ?: throw IllegalStateException("No token available")
                Log.d("EmpowerSWR", "checkIn - Sending token: $token, phone: $phone")
                val response = api.checkIn(token, CheckInRequest(phone))
                _checkInSuccess.value = response.success
                _checkInError.value = response.message ?: if (response.success) "Check-in successful" else "Check-in failed"
                if (response.success) {
                    fetchWorkerDetails()
                }
                Log.d("EmpowerSWR", "checkIn - Success: ${response.success}, Message: ${response.message}")
            } catch (e: Exception) {
                val errorMessage = when (e) {
                    is HttpException -> {
                        val responseBody = e.response()?.errorBody()?.string() ?: "No response body"
                        "HTTP ${e.code()}: ${e.message()}, Body: $responseBody"
                    }
                    else -> e.message ?: "Unknown error"
                }
                _checkInError.value = "Check-in failed: $errorMessage"
                _checkInSuccess.value = false
                Log.e("EmpowerSWR", "checkIn error: $errorMessage", e)
                if (errorMessage.contains("Invalid JWT")) {
                    logout()
                }
            }
        }
    }

    fun saveLocation(workerId: String, latitude: Double, longitude: Double, action: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = _token.value ?: throw IllegalStateException("No token available")
                Log.d("EmpowerSWR", "saveLocation - Sending token: $token, worker_id: $workerId, latitude: $latitude, longitude: $longitude, action: $action")
                val response = api.saveLocation(token, LocationRequest(worker_id = workerId, latitude = latitude, longitude = longitude, action = action))
                if (response.success) {
                    Log.d("EmpowerSWR", "saveLocation - Success: Location saved/updated for worker_id: $workerId, action: $action")
                } else {
                    Log.e("EmpowerSWR", "saveLocation - Failed: ${response.message}")
                }
            } catch (e: Exception) {
                Log.e("EmpowerSWR", "saveLocation error: ${e.message}", e)
            }
        }
    }

    fun clearCheckInState() {
        _checkInSuccess.value = null
        _checkInError.value = null
        Log.d("EmpowerSWR", "clearCheckInState - Cleared check-in states")
    }

    fun logout() {
        Log.d("EmpowerSWR", "logout - Clearing token")
        _token.value = null
        _loginError.value = null
        _workerDetails.value = null
        _history.value = emptyList()
        _alerts.value = emptyList()
        _notifications.value = emptyList()
        _notificationFromIntent.value = null to null
        _checkInSuccess.value = null
        _checkInError.value = null
        _pendingFields.value = emptySet()
        PrefsHelper.clearPrefs(getApplication())
    }

    //Edit Profile Functions

    fun updatePreferredName(newName: String, callback: (Boolean, String?) -> Unit) {
        val currentWorker = _workerDetails.value ?: return callback(false, "No worker data")
        val currentToken = _token.value ?: return callback(false, "No token")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = api.submitPendingUpdate(
                    token = currentToken,
                    pendingData = mapOf(
                        "worker_id" to currentWorker.ID.toString(),
                        "field_key" to "prefName",
                        "new_value" to newName
                    )
                )
                withContext(Dispatchers.Main) {
                    _workerDetails.value = response
                    _pendingFields.value = response.pendingFields?.toSet() ?: emptySet()  // Sync from response, not manual +
                }
                callback(true, null)
                Log.d("EmpowerSWR", "Submitted prefName change for review")
            } catch (e: Exception) {
                callback(false, e.message)
                Log.e("EmpowerSWR", "Submit prefName error: ${e.message}", e)
            }
        }
    }

    fun updateContactInfo(primary: String, secondary: String, aunz: String, email: String, callback: (Boolean, String?) -> Unit) {
        val currentWorker = _workerDetails.value ?: return callback(false, "No worker data")
        val currentToken = _token.value ?: return callback(false, "No token")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = api.submitPendingUpdate(
                    token = currentToken,
                    pendingData = mapOf(
                        "worker_id" to currentWorker.ID.toString(),
                        "field_key" to "contacts",
                        "new_value" to "{\"phone\":\"$primary\",\"phone2\":\"$secondary\",\"aunzPhone\":\"$aunz\",\"email\":\"$email\"}"
                    )
                )
                withContext(Dispatchers.Main) {
                    _workerDetails.value = response
                    _pendingFields.value = _pendingFields.value + "contacts"  // Safe on main
                }
                callback(true, null)
                Log.d("EmpowerSWR", "Submitted contact changes for review")
            } catch (e: Exception) {
                callback(false, e.message)
                Log.e("EmpowerSWR", "Submit contacts error: ${e.message}", e)
            }
        }
    }
    fun updatePassportDetails(
        firstName: String,
        surname: String,
        ppno: String,
        birthplace: String,
        ppexpiry: String,
        birthProvince: String,
        callback: (Boolean, String?) -> Unit
    ) {
        val currentWorker = _workerDetails.value ?: return callback(false, "No worker data")
        val currentToken = _token.value ?: return callback(false, "No token")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val passportData = buildString {
                    append("{\"ppno\":\"$ppno\",\"birthplace\":\"$birthplace\",\"ppexpiry\":\"$ppexpiry\",\"birthProvince\":\"$birthProvince\"")
                    if (firstName.isNotBlank()) append(",\"firstName\":\"$firstName\"")
                    if (surname.isNotBlank()) append(",\"surname\":\"$surname\"")
                    append("}")
                }

                val response = api.submitPendingUpdate(
                    token = currentToken,
                    pendingData = mapOf(
                        "worker_id" to currentWorker.ID.toString(),
                        "field_key" to "passport",
                        "new_value" to passportData
                    )
                )
                withContext(Dispatchers.Main) {
                    _workerDetails.value = response
                    _pendingFields.value = _pendingFields.value + "passport"  // Optional if pending badge needed
                }
                callback(true, null)
                Log.d("EmpowerSWR", "Updated passport details")
            } catch (e: Exception) {
                callback(false, e.message)
                Log.e("EmpowerSWR", "Update passport error: ${e.message}", e)
            }
        }
    }
    fun fetchFlightDetails(workerId: String, onError: (Throwable?) -> Unit) {
        val currentToken = _token.value
        if (currentToken == null) {
            onError(IllegalStateException("No token available"))
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d("EmpowerSWR", "fetchFlightDetails - Sending workerId: $workerId, token: $currentToken")
                val response = api.getFlightDetails(workerId, currentToken)
                withContext(Dispatchers.Main) {
                    _flightDetails.value = response
                }
            } catch (e: Exception) {
                Log.e("EmpowerSWR", "Error fetching flight details: ${e.message}")
                withContext(Dispatchers.Main) {
                    onError(e)
                }
            }
        }
    }
    fun fetchPdbDetails(workerId: String, onError: (Throwable?) -> Unit) {
        val currentToken = _token.value
        if (currentToken == null) {
            Log.e("EmpowerSWR", "fetchPdbDetails - No token available")
            onError(IllegalStateException("No token available"))
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d("EmpowerSWR", "fetchPdbDetails - Sending workerId: $workerId, token: $currentToken")
                val response = api.getPdbDetails(workerId, currentToken)
                withContext(Dispatchers.Main) {
                    _pdbDetails.value = response
                }
            } catch (e: Exception) {
                Log.e("EmpowerSWR", "Error fetching PDB details: ${e.message}")
                withContext(Dispatchers.Main) {
                    onError(e)
                }
            }
        }
    }
    fun updatePdbStatus(workerId: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val currentToken = _token.value
                if (currentToken == null) {
                    Log.e("EmpowerSWR", "updatePdbStatus - No token available")
                    onResult(false, "No token available")
                    return@launch
                }
                Log.d("EmpowerSWR", "updatePdbStatus - Sending workerId: $workerId, token: $currentToken")
                val response = api.updatePdbStatus(workerId, currentToken)
                withContext(Dispatchers.Main) {
                    if (response.success) {
                        // Refresh PDB details to reflect updated status
                        fetchPdbDetails(workerId) { error ->
                            if (error != null) {
                                onResult(false, "Failed to refresh PDB details: ${error.message}")
                            } else {
                                _pdbDetails.value = _pdbDetails.value?.copy(pdbStatus = "App OK")
                                onResult(true, response.message)
                            }
                        }
                    } else {
                        onResult(false, response.message ?: "Failed to update PDB status")
                    }
                }
            } catch (e: Exception) {
                Log.e("EmpowerSWR", "Update PDB status error: ${e.message}")
                withContext(Dispatchers.Main) {
                    onResult(false, e.message)
                }
            }
        }
    }

    fun updatePdbInternalStatus(workerId: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val currentToken = _token.value
                if (currentToken == null) {
                    Log.e("EmpowerSWR", "updatePdbInternalStatus - No token available")
                    onResult(false, "No token available")
                    return@launch
                }
                Log.d("EmpowerSWR", "updatePdbInternalStatus - Sending workerId: $workerId, token: $currentToken")
                val response = api.updatePdbInternalStatus(workerId, currentToken)
                withContext(Dispatchers.Main) {
                    if (response.success) {
                        // Refresh PDB details to reflect updated status
                        fetchPdbDetails(workerId) { error ->
                            if (error != null) {
                                onResult(false, "Failed to refresh PDB details: ${error.message}")
                            } else {
                                _pdbDetails.value = _pdbDetails.value?.copy(internalPdbStatus = "App OK")
                                onResult(true, response.message)
                            }
                        }
                    } else {
                        onResult(false, response.message ?: "Failed to update internal PDB status")
                    }
                }
            } catch (e: Exception) {
                Log.e("EmpowerSWR", "Update internal PDB status error: ${e.message}")
                withContext(Dispatchers.Main) {
                    onResult(false, e.message)
                }
            }
        }
    }
    fun updateFlightStatus(workerId: String, onResult: (Boolean, String?) -> Unit) {
        val currentToken = _token.value
        if (currentToken == null) {
            Log.e("EmpowerSWR", "updateFlightStatus - No token available")
            onResult(false, "No token available")
            return
        }
        viewModelScope.launch {
            try {
                val response = api.updateFlightStatus(workerId, currentToken) // Assume this endpoint exists
                Log.d("EmpowerSWR", "Update flight status response: $response")
                if (response.success) {
                    fetchFlightDetails(workerId) { error ->
                        if (error != null) {
                            onResult(false, "Failed to refresh flight details: ${error.message}")
                        } else {
                            onResult(true, response.message)
                        }
                    }
                } else {
                    onResult(false, response.message)
                }
            } catch (e: Exception) {
                Log.e("EmpowerSWR", "Update flight status error: ${e.message}")
                onResult(false, e.message)
            }
        }
    }
    fun fetchDirectory(token: String, workerId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d("EmpowerSWR", "fetchDirectory - Sending token: $token, workerId: $workerId")
                val entries = api.getDirectory(token, workerId)
                _directoryEntries.value = entries
            } catch (e: Exception) {
                _directoryEntries.value = emptyList()
                Log.e("EmpowerSWR", "Fetch directory error: ${e.message}")
                if (e is HttpException && e.message?.contains("Invalid JWT") == true) {
                    logout()
                }
            }
        }
    }

}