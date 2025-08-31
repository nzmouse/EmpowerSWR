package com.empowerswr.luksave

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonParseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.UnknownHostException
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import timber.log.Timber
import kotlinx.coroutines.flow.asStateFlow

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

    private val _flightError = mutableStateOf<String?>(null)
    val flightError: State<String?> = _flightError

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

    private val _pendingFields = mutableStateOf<Set<String>>(emptySet())
    val pendingFields: State<Set<String>> = _pendingFields

    private val _flightDetails = mutableStateOf<FlightDetails?>(null)
    val flightDetails: State<FlightDetails?> = _flightDetails

    private val _pdbDetails = mutableStateOf<PdbDetails?>(null)
    val pdbDetails: State<PdbDetails?> = _pdbDetails

    private val _internalPdbDetails = mutableStateOf<PdbDetails?>(null)

    private val _directoryEntries = MutableStateFlow<List<DirectoryEntry>>(emptyList())
    val directoryEntries: StateFlow<List<DirectoryEntry>> = _directoryEntries

    private val _pdbError = mutableStateOf<String?>(null)

    private val _teamEntries = MutableStateFlow<List<Team>>(emptyList())
    val teamEntries: StateFlow<List<Team>> = _teamEntries.asStateFlow()

    private val _teamLocations = MutableStateFlow<Map<Int, List<TeamLocation>>>(emptyMap())
    val teamLocations: StateFlow<Map<Int, List<TeamLocation>>> = _teamLocations.asStateFlow()

    private val _notices = MutableStateFlow<String?>(null)
    val notices: StateFlow<String?> = _notices

    init {
        val savedToken = PrefsHelper.getToken(getApplication())
        val savedTokenExpiry = PrefsHelper.getTokenExpiry(getApplication())
        if (savedToken != null && savedTokenExpiry != null) {
            val currentTime = System.currentTimeMillis() / 1000
            if (savedTokenExpiry > currentTime && isValidJwt(savedToken)) {
                _token.value = savedToken
            } else {
                PrefsHelper.clearToken(getApplication())
                _token.value = null
            }
        }

        viewModelScope.launch {
            NotificationHandler.notificationFlow.collect { notificationData ->
                val notification = Notification(notificationData.first ?: "", notificationData.second ?: "")
                _notifications.value = _notifications.value.toMutableList().apply { add(notification) }
            }
        }
    }

    private fun isValidJwt(token: String): Boolean {
        return token.split(".").size == 3
    }

    fun register(passport: String, surname: String, pin: String) {
        viewModelScope.launch {
            try {
                val request = RegistrationRequest(passport, surname, pin)
                val response: Response<RegistrationResponse> = api.register(request)
                val errorBody = response.errorBody()?.string()
                if (response.isSuccessful) {
                    response.body()?.let { tokenResponse ->
                        _token.value = tokenResponse.token
                        PrefsHelper.saveWorkerId(getApplication(), tokenResponse.workerId)
                        PrefsHelper.saveToken(getApplication(), tokenResponse.token, tokenResponse.expiry)
                        fetchWorkerDetails()
                        fetchHistory()
                        fetchAlerts()
                    }
                } else {
                    val errorMessage = errorBody?.let {
                        try {
                            val jsonObject = JSONObject(it)
                            jsonObject.optString("error", "Registration failed: Unknown error")
                        } catch (_: Exception) {
                            "Registration failed: Invalid server response"
                        }
                    } ?: "Registration failed: HTTP ${response.code()}"
                    _loginError.value = errorMessage
                    Timber.e("Registration failed: HTTP ${response.code()}")
                }
            } catch (e: JsonParseException) {
                Timber.e(e, "JSON parsing error during registration")
                _loginError.value = "Registration failed: Invalid server response"
            } catch (e: HttpException) {
                Timber.e(e, "HTTP error during registration")
                _loginError.value = "Registration failed: HTTP ${e.code()}"
            } catch (e: UnknownHostException) {
                Timber.e(e, "Network error during registration")
                _loginError.value = "Registration failed: Unable to connect to server"
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error during registration")
                _loginError.value = "Registration failed: ${e.message}"
            }
        }
    }

    fun login(workerId: String, pin: String) {
        viewModelScope.launch {
            try {
                val response = api.login(LoginRequest(workerId, pin))
                if (!isValidJwt(response.token)) {
                    throw IllegalStateException("Invalid JWT token received")
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
                    is HttpException -> "Login failed: HTTP ${e.code()}"
                    else -> "Login failed: ${e.message}"
                }
                Timber.e(e, "Login failed")
            }
        }
    }

    fun updateFcmToken(fcmToken: String, workerId: String) {
        viewModelScope.launch {
            try {
                api.updateFcmToken(workerId, fcmToken)
            } catch (e: Exception) {
                Timber.e(e, "Failed to update FCM token")
            }
        }
    }

    fun setToken(token: String?) {
        if (token != null && isValidJwt(token)) {
            _token.value = token
            val expiry = (System.currentTimeMillis() / 1000) + 24 * 60 * 60 // 24 hours
            PrefsHelper.saveToken(getApplication(), token, expiry)
            fetchWorkerDetails()
            fetchHistory()
            fetchAlerts()
        } else {
            PrefsHelper.clearToken(getApplication())
            _token.value = null
            _workerDetails.value = null
            _history.value = emptyList()
            _alerts.value = emptyList()
        }
    }

    fun setNotificationFromIntent(title: String?, body: String?) {
        _notificationFromIntent.value = title to body
    }

    fun removeNotification(notification: Notification) {
        _notifications.value = _notifications.value.toMutableList().apply { remove(notification) }
    }

    fun fetchWorkerDetails(onError: ((Throwable?) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = _token.value ?: throw IllegalStateException("No token available")
                val response = api.getWorkerDetails(token)
                _workerDetails.value = response
                PrefsHelper.saveWorkerDetails(getApplication(), response.firstName, response.surname)
                onError?.invoke(null)
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch worker details")
                onError?.invoke(e)
                if (e.message?.contains("Invalid JWT") == true) {
                    logout()
                }
            }
        }
    }

    fun fetchHistory(onError: ((Throwable) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val workerId = PrefsHelper.getWorkerId(getApplication())
                    ?: throw IllegalStateException("No workerId available")
                val response = api.getWorkerHistory(workerId)
                _history.value = response
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch history")
                _history.value = emptyList()
                onError?.invoke(e)
            }
        }
    }

    fun fetchAlerts() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = _token.value ?: throw IllegalStateException("No token available")
                val response = api.getAlerts(token)
                _alerts.value = response
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch alerts")
                _alerts.value = emptyList()
                if (e.message?.contains("Invalid JWT") == true) {
                    logout()
                }
            }
        }
    }

    fun removeAlert(alert: Alert) {
        _alerts.value = _alerts.value.toMutableList().apply { remove(alert) }
    }

    fun checkIn(phone: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!phone.matches(Regex("^\\d{7,15}$"))) {
                    throw IllegalArgumentException("Phone number must be 7-15 digits")
                }
                val token = _token.value ?: throw IllegalStateException("No token available")
                val response = api.checkIn(token, CheckInRequest(phone))
                _checkInSuccess.value = response.success
                _checkInError.value = response.message ?: if (response.success) "Check-in successful" else "Check-in failed"
                if (response.success) {
                    fetchWorkerDetails()
                }
            } catch (e: Exception) {
                val errorMessage = when (e) {
                    is HttpException -> "HTTP ${e.code()}: ${e.message()}"
                    else -> e.message ?: "Unknown error"
                }
                _checkInError.value = "Check-in failed: $errorMessage"
                _checkInSuccess.value = false
                Timber.e(e, "Check-in failed")
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
                val response = api.saveLocation(token, LocationRequest(worker_id = workerId, latitude = latitude, longitude = longitude, action = action))
                if (!response.success) {
                    Timber.e("Failed to save location: ${response.message}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to save location")
            }
        }
    }

    fun clearCheckInState() {
        _checkInSuccess.value = null
        _checkInError.value = null
    }

    fun logout() {
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

    // Edit Profile Functions

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
                    _pendingFields.value = response.pendingFields?.toSet() ?: emptySet()
                }
                callback(true, null)
            } catch (e: Exception) {
                Timber.e(e, "Failed to update preferred name")
                callback(false, e.message)
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
                    _pendingFields.value = _pendingFields.value + "contacts"
                }
                callback(true, null)
            } catch (e: Exception) {
                Timber.e(e, "Failed to update contact info")
                callback(false, e.message)
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
                    _pendingFields.value = _pendingFields.value + "passport"
                }
                callback(true, null)
            } catch (e: Exception) {
                Timber.e(e, "Failed to update passport details")
                callback(false, e.message)
            }
        }
    }

    fun fetchFlightDetails(workerId: String, onError: (Throwable?) -> Unit) {
        val currentToken = _token.value
        if (currentToken == null) {
            _flightError.value = "Authentication error. Please log in again."
            Timber.e("No token available for fetching flight details")
            onError(IllegalStateException("No token available"))
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = api.getFlightDetails(workerId, currentToken)
                withContext(Dispatchers.Main) {
                    _flightDetails.value = response
                    _flightError.value = null
                    onError(null)
                }
            } catch (e: HttpException) {
                Timber.e(e, "Failed to fetch flight details")
                withContext(Dispatchers.Main) {
                    val errorMessage = when (e.code()) {
                        404 -> {
                            try {
                                val errorBody = e.response()?.errorBody()?.string()
                                val errorJson = errorBody?.let { JSONObject(it) }
                                if (errorJson?.optString("error") == "No flight details found") {
                                    "No flights scheduled at this time. Please check back later."
                                } else {
                                    "No flight details available."
                                }
                            } catch (jsonException: Exception) {
                                "No flights scheduled at this time. Please check back later."
                            }
                        }
                        else -> "Failed to load flight details: ${e.message()}"
                    }
                    _flightDetails.value = null
                    _flightError.value = errorMessage
                    onError(e)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch flight details")
                withContext(Dispatchers.Main) {
                    _flightError.value = when (e) {
                        is UnknownHostException -> "Network error. Please check your connection and try again."
                        else -> "Failed to load flight details: ${e.message ?: "Unknown error"}"
                    }
                    _flightDetails.value = null
                    onError(e)
                }
            }
        }
    }

    fun fetchPdbDetails(workerId: String, onError: (Throwable?) -> Unit) {
        val currentToken = _token.value
        if (currentToken == null) {
            Timber.e("No token available for fetching PDB details")
            _pdbError.value = "Authentication error. Please log in again."
            onError(IllegalStateException("No token available"))
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = api.getPdbDetails(workerId, currentToken)
                withContext(Dispatchers.Main) {
                    if (response.startDate == null && response.endDate == null && response.pdbLocationLong == null) {
                        _pdbDetails.value = null
                        _pdbError.value = "No pre-departure details available at this time. Please check back later."
                        onError(Throwable("No pre-departure details available"))
                    } else {
                        _pdbDetails.value = response
                        _pdbError.value = null
                        onError(null)
                    }
                }
            } catch (e: HttpException) {
                Timber.e(e, "Failed to fetch PDB details")
                withContext(Dispatchers.Main) {
                    val errorMessage = when (e.code()) {
                        401 -> "Authentication error. Please log in again."
                        else -> "Failed to load pre-departure details: ${e.message()}"
                    }
                    _pdbDetails.value = null
                    _pdbError.value = errorMessage
                    onError(e)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch PDB details")
                withContext(Dispatchers.Main) {
                    _pdbError.value = when (e) {
                        is UnknownHostException -> "Network error. Please check your connection and try again."
                        else -> "Failed to load pre-departure details: ${e.message ?: "Unknown error"}"
                    }
                    _pdbDetails.value = null
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
                    Timber.e("No token available for updating PDB status")
                    onResult(false, "No token available")
                    return@launch
                }
                val response = api.updatePdbStatus(workerId, currentToken)
                withContext(Dispatchers.Main) {
                    if (response.success) {
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
                Timber.e(e, "Failed to update PDB status")
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
                    Timber.e("No token available for updating internal PDB status")
                    onResult(false, "No token available")
                    return@launch
                }
                val response = api.updatePdbInternalStatus(workerId, currentToken)
                withContext(Dispatchers.Main) {
                    if (response.success) {
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
                Timber.e(e, "Failed to update internal PDB status")
                withContext(Dispatchers.Main) {
                    onResult(false, e.message)
                }
            }
        }
    }

    fun updateFlightStatus(workerId: String, onResult: (Boolean, String?) -> Unit) {
        val currentToken = _token.value
        if (currentToken == null) {
            Timber.e("No token available for updating flight status")
            onResult(false, "No token available")
            return
        }
        viewModelScope.launch {
            try {
                val response = api.updateFlightStatus(workerId, currentToken)
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
                Timber.e(e, "Failed to update flight status")
                onResult(false, e.message)
            }
        }
    }

    fun fetchDirectory(token: String, workerId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entries = api.getDirectory(token, workerId)
                _directoryEntries.value = entries
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch directory")
                _directoryEntries.value = emptyList()
                if (e is HttpException && e.message?.contains("Invalid JWT") == true) {
                    logout()
                }
            }
        }
    }

    fun fetchTeams(token: String, workerId: String, limit: Int = 50, offset: Int = 0) {
        viewModelScope.launch {
            try {
                val teams = api.getTeams(token, workerId, limit, offset)
                _teamEntries.value = teams.filter { it.teamName != null && it.teamName.isNotEmpty() }
                _teamEntries.value.forEach { team ->
                    fetchTeamLocations(token, workerId, team.teamId)
                }
            } catch (e: HttpException) {
                Timber.e(e, "Failed to fetch teams")
                throw e
            } catch (e: JsonParseException) {
                Timber.e(e, "Failed to parse teams response")
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch teams")
                throw e
            }
        }
    }

    private fun fetchTeamLocations(token: String, workerId: String, teamId: Int) {
        viewModelScope.launch {
            try {
                val locations = api.getTeamLocations(token, workerId, teamId)
                _teamLocations.value = _teamLocations.value.toMutableMap().apply {
                    put(teamId, locations)
                }
            } catch (e: HttpException) {
                Timber.e(e, "Failed to fetch locations for team $teamId")
            } catch (e: JsonParseException) {
                Timber.e(e, "Failed to parse locations response for team $teamId")
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch locations for team $teamId")
            }
        }
    }

    suspend fun submitFeedback(token: String, workerId: String, teamId: Int, feedbackText: String) {
        val body = mapOf(
            "teamId" to teamId,
            "feedbackText" to feedbackText
        )
        val response = api.submitFeedback(token, workerId, body)
        if (!response.isSuccessful) {
            throw HttpException(response)
        }
    }

    suspend fun acceptApplication(token: String, workerId: String) {
        val body = mapOf("workerId" to workerId)
        val response = api.acceptApplication(token, body)
        if (!response.isSuccessful) {
            throw HttpException(response)
        }
    }

    suspend fun fetchNotices(token: String, workerId: String) {
        try {
            val response = api.getNotices(token, workerId)
            if (response.isSuccessful) {
                val body = response.body()
                _notices.value = body?.get("notices")
            } else {
                Timber.e("Failed to fetch notices: HTTP ${response.code()}")
                throw HttpException(response)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch notices")
            _notices.value = null
        }
    }
}