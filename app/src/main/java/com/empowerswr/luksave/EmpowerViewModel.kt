package com.empowerswr.luksave

import android.app.Application
import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonParseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.empowerswr.luksave.EmpowerApi
import com.empowerswr.luksave.PdbUpdateResponse
import org.json.JSONObject
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import timber.log.Timber
import java.net.UnknownHostException
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

    private val _contractSuccess = mutableStateOf<Boolean?>(null)
    val contractSuccess: State<Boolean?> = _contractSuccess

    private val _contractError = mutableStateOf<String?>(null)
    val contractError: State<String?> = _contractError

    private val _flightError = mutableStateOf<String?>(null)
    val flightError: State<String?> = _flightError

    private val _notifications = mutableStateOf<List<Notification>>(emptyList())
    val notifications: State<List<Notification>> = _notifications

    private val _notificationFromIntent = mutableStateOf(Pair<String?, String?>(null, null))
    val notificationFromIntent: State<Pair<String?, String?>> = _notificationFromIntent

    private val _pendingFields = mutableStateOf<Set<String>>(emptySet())
    val pendingFields: State<Set<String>> = _pendingFields

    private val _flightDetails = mutableStateOf<FlightDetails?>(null)
    val flightDetails: State<FlightDetails?> = _flightDetails

    private val _pdbDetails = mutableStateOf<PdbDetails?>(null)
    val pdbDetails: State<PdbDetails?> = _pdbDetails

    private val _internalPdbDetails = mutableStateOf<PdbDetails?>(null)

    private val _directoryEntries = MutableStateFlow<List<DirectoryEntry>>(emptyList())
    val directoryEntries: StateFlow<List<DirectoryEntry>> = _directoryEntries.asStateFlow()

    private val _pdbError = mutableStateOf<String?>(null)
    val pdbError: State<String?> = _pdbError

    private val _teamEntries = MutableStateFlow<List<Team>>(emptyList())
    val teamEntries: StateFlow<List<Team>> = _teamEntries.asStateFlow()

    private val _teamLocations = MutableStateFlow<Map<Int, List<TeamLocation>>>(emptyMap())
    val teamLocations: StateFlow<Map<Int, List<TeamLocation>>> = _teamLocations.asStateFlow()

    private val _notices = MutableStateFlow<String?>(null)
    val notices: StateFlow<String?> = _notices.asStateFlow()

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

    private val _showUsernamePrompt = mutableStateOf(false)
    val showUsernamePrompt: State<Boolean> get() = _showUsernamePrompt

    private val _loginComplete = mutableStateOf(false)
    val loginComplete: State<Boolean> get() = _loginComplete

    init {
        val savedToken = PrefsHelper.getToken(getApplication())
        val savedTokenExpiry = PrefsHelper.getTokenExpiry(getApplication())
        if (savedToken != null && savedTokenExpiry != null) {
            val currentTime = System.currentTimeMillis() / 1000
            if (savedTokenExpiry > currentTime && isValidJwt(savedToken)) {
                _token.value = savedToken
                viewModelScope.launch {
                    fetchWorkerDetails(getApplication())
                    fetchHistory(getApplication())
                    fetchAlerts(getApplication())
                }
            } else {
                PrefsHelper.clearToken(getApplication())
                _token.value = null
            }
        }

        viewModelScope.launch {
            NotificationHandler.notificationFlow.collect { notificationData ->
                val notification =
                    Notification(notificationData.first ?: "", notificationData.second ?: "")
                _notifications.value =
                    _notifications.value.toMutableList().apply { add(notification) }
            }
        }
    }

    private fun isValidJwt(token: String): Boolean {
        return token.split(".").size == 3
    }

    fun register(
        passport: String,
        surname: String,
        username: String,
        pin: String,
        context: Context
    ) {
        viewModelScope.launch {
            try {
                val request = RegistrationRequest(passport, surname, username, pin)
                val response: Response<RegistrationResponse> = api.register(request)
                val errorBody = response.errorBody()?.string()
                if (response.isSuccessful) {
                    response.body()?.let { tokenResponse ->
                        _token.value = tokenResponse.token
                        PrefsHelper.saveWorkerId(context, tokenResponse.workerId)
                        PrefsHelper.saveToken(context, tokenResponse.token, tokenResponse.expiry)
                        PrefsHelper.setRegistered(context, true)
                        fetchWorkerDetails(context)
                        fetchHistory(context)
                        fetchAlerts(context)
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

    fun login(workerIdOrUsername: String, pin: String, context: Context) {
        viewModelScope.launch {
            try {
                val request = LoginRequest(workerIdOrUsername, pin)
                val loginResponse = api.login(request)
                if (!isValidJwt(loginResponse.token)) {
                    throw IllegalStateException("Invalid JWT token received")
                }
                if (loginResponse.workerId.isNullOrEmpty()) {
                    Timber.e("Login: No workerId in response: $loginResponse")
                    throw IllegalStateException("No workerId in login response")
                }
                _token.value = loginResponse.token
                PrefsHelper.saveWorkerId(context, loginResponse.workerId)
                PrefsHelper.saveToken(context, loginResponse.token, loginResponse.expiry)
                PrefsHelper.saveUsername(context, loginResponse.username)
                _showUsernamePrompt.value = loginResponse.username.isNullOrEmpty()
                Timber.d("Login: workerId=${loginResponse.workerId}, username=${loginResponse.username}, prompt=${_showUsernamePrompt.value}")
                _loginError.value = null
                fetchWorkerDetails(context)
                fetchHistory(context)
                fetchAlerts(context)
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error during login")
                _loginError.value = "Login failed: ${e.message}"
            }
        }
    }

    fun updateFcmToken(fcmToken: String, context: Context) {
        viewModelScope.launch {
            val workerId = PrefsHelper.getWorkerId(context) ?: run {
                Timber.e("No workerId available for updating FCM token")
                return@launch
            }
            try {
                api.updateFcmToken(workerId, fcmToken)
                PrefsHelper.saveFcmToken(context, fcmToken)
                Timber.i("FCM token updated for workerId=$workerId")
            } catch (e: Exception) {
                Timber.e(e, "Failed to update FCM token")
            }
        }
    }

    fun setToken(token: String?, context: Context) {
        if (token != null && isValidJwt(token)) {
            _token.value = token
            val expiry = (System.currentTimeMillis() / 1000) + 24 * 60 * 60 // 24 hours
            PrefsHelper.saveToken(context, token, expiry)
            fetchWorkerDetails(context)
            fetchHistory(context)
            fetchAlerts(context)
        } else {
            PrefsHelper.clearToken(context)
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

    fun fetchWorkerDetails(context: Context, onError: ((Throwable?) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val workerId = PrefsHelper.getWorkerId(context) ?: run {
                Timber.e("No workerId available for fetching worker details")
                onError?.invoke(IllegalStateException("No workerId available"))
                return@launch
            }
            try {
                val token = _token.value ?: throw IllegalStateException("No token available")
                val response = api.getWorkerDetails(token)
                withContext(Dispatchers.Main) {
                    _workerDetails.value = response
                    PrefsHelper.saveWorkerDetails(context, response.firstName, response.surname)
                    onError?.invoke(null)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch worker details")
                withContext(Dispatchers.Main) {
                    onError?.invoke(e)
                    if (e.message?.contains("Invalid JWT") == true) {
                        logout(context)
                    }
                }
            }
        }
    }

    fun fetchHistory(context: Context, onError: ((Throwable) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val workerId = PrefsHelper.getWorkerId(context) ?: run {
                Timber.e("No workerId available for fetching history")
                onError?.invoke(IllegalStateException("No workerId available"))
                return@launch
            }
            try {
                val response = api.getWorkerHistory(workerId)
                withContext(Dispatchers.Main) {
                    _history.value = response
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch history")
                withContext(Dispatchers.Main) {
                    _history.value = emptyList()
                    onError?.invoke(e)
                }
            }
        }
    }

    fun fetchAlerts(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = _token.value ?: throw IllegalStateException("No token available")
                val response = api.getAlerts(token)
                withContext(Dispatchers.Main) {
                    _alerts.value = response
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch alerts")
                withContext(Dispatchers.Main) {
                    _alerts.value = emptyList()
                    if (e.message?.contains("Invalid JWT") == true) {
                        logout(context)
                    }
                }
            }
        }
    }

    fun removeAlert(alert: Alert) {
        _alerts.value = _alerts.value.toMutableList().apply { remove(alert) }
    }

    fun checkIn(phone: String, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!phone.matches(Regex("^\\d{7,15}$"))) {
                    throw IllegalArgumentException("Phone number must be 7-15 digits")
                }
                val token = _token.value ?: throw IllegalStateException("No token available")
                val workerId = PrefsHelper.getWorkerId(context)
                    ?: throw IllegalStateException("No workerId available")
                Timber.d("checkIn: token=$token, workerId=$workerId")
                val response = api.checkIn(token, CheckInRequest(phone, workerId))
                _checkInSuccess.value = response.success
                _checkInError.value = response.message
                    ?: if (response.success) "Check-in successful" else "Check-in failed"
                if (response.success) {
                    fetchWorkerDetails(context)
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
                    logout(context)
                }
            }
        }
    }
    fun signContract(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = _token.value ?: throw IllegalStateException("No token available")
                val workerId = PrefsHelper.getWorkerId(context)
                    ?: throw IllegalStateException("No workerId available")
                Timber.d("signContract: token=$token, workerId=$workerId")
                val response = api.signContract(token, workerId)
                _contractSuccess.value = response.success
                _contractError.value = response.message
                    ?: if (response.success) "Plis bae yu traem kam saen lo wik ia" else "Contract signing failed"
                if (response.success) {
                    fetchWorkerDetails(context)
                }
            } catch (e: Exception) {
                val errorMessage = when (e) {
                    is HttpException -> "HTTP ${e.code()}: ${e.message()}"
                    else -> e.message ?: "Unknown error"
                }
                _contractError.value = "Contract signing failed: $errorMessage"
                _contractSuccess.value = false
                Timber.e(e, "Contract signing failed: $errorMessage")
                if (errorMessage.contains("Invalid JWT")) {
                    logout(context)
                }
            }
        }
    }
    fun saveLocation(context: Context, latitude: Double, longitude: Double, action: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val workerId = PrefsHelper.getWorkerId(context) ?: run {
                Timber.e("No workerId available for saving location")
                return@launch
            }
            try {
                val token = _token.value ?: throw IllegalStateException("No token available")
                val response =
                    api.saveLocation(token, LocationRequest(workerId, latitude, longitude, action))
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

    fun logout(context: Context) {
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
        _flightDetails.value = null
        _pdbDetails.value = null
        _pdbError.value = null
        _directoryEntries.value = emptyList()
        _teamEntries.value = emptyList()
        _teamLocations.value = emptyMap()
        _notices.value = null
        _showUsernamePrompt.value = false
        PrefsHelper.clearPrefs(context)
    }

    fun updatePreferredName(
        newName: String,
        context: Context,
        callback: (Boolean, String?) -> Unit
    ) {
        val workerId =
            PrefsHelper.getWorkerId(context) ?: return callback(false, "No workerId available")
        val currentToken = _token.value ?: return callback(false, "No token available")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = api.submitPendingUpdate(
                    token = currentToken,
                    pendingData = mapOf(
                        "worker_id" to workerId,
                        "field_key" to "prefName",
                        "new_value" to newName
                    )
                )
                withContext(Dispatchers.Main) {
                    _workerDetails.value = response
                    _pendingFields.value = response.pendingFields?.toSet() ?: emptySet()
                    callback(true, null)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to update preferred name")
                withContext(Dispatchers.Main) {
                    callback(false, e.message)
                }
            }
        }
    }

    fun updateContactInfo(
        primary: String,
        secondary: String,
        aunz: String,
        email: String,
        context: Context,
        callback: (Boolean, String?) -> Unit
    ) {
        val workerId =
            PrefsHelper.getWorkerId(context) ?: return callback(false, "No workerId available")
        val currentToken = _token.value ?: return callback(false, "No token available")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = api.submitPendingUpdate(
                    token = currentToken,
                    pendingData = mapOf(
                        "worker_id" to workerId,
                        "field_key" to "contacts",
                        "new_value" to "{\"phone\":\"$primary\",\"phone2\":\"$secondary\",\"aunzPhone\":\"$aunz\",\"email\":\"$email\"}"
                    )
                )
                withContext(Dispatchers.Main) {
                    _workerDetails.value = response
                    _pendingFields.value = _pendingFields.value + "contacts"
                    callback(true, null)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to update contact info")
                withContext(Dispatchers.Main) {
                    callback(false, e.message)
                }
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
        context: Context,
        callback: (Boolean, String?) -> Unit
    ) {
        val workerId =
            PrefsHelper.getWorkerId(context) ?: return callback(false, "No workerId available")
        val currentToken = _token.value ?: return callback(false, "No token available")
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
                        "worker_id" to workerId,
                        "field_key" to "passport",
                        "new_value" to passportData
                    )
                )
                withContext(Dispatchers.Main) {
                    _workerDetails.value = response
                    _pendingFields.value = _pendingFields.value + "passport"
                    callback(true, null)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to update passport details")
                withContext(Dispatchers.Main) {
                    callback(false, e.message)
                }
            }
        }
    }


    // Update fetchFlightDetails
    fun fetchFlightDetails(context: Context, onError: (Throwable?) -> Unit) {
        val workerId = PrefsHelper.getWorkerId(context) ?: run {
            _flightError.value = "No workerId available"
            Timber.e("No workerId available for fetching flight details")
            onError(IllegalStateException("No workerId available"))
            return
        }
        val currentToken = _token.value ?: run {
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

    // Update fetchPdbDetails
    fun fetchPdbDetails(context: Context, onError: (Throwable?) -> Unit) {
        val workerId = PrefsHelper.getWorkerId(context) ?: run {
            _pdbError.value = "No workerId available"
            Timber.e("No workerId available for fetching PDB details")
            onError(IllegalStateException("No workerId available"))
            return
        }
        val currentToken = _token.value ?: run {
            _pdbError.value = "Authentication error. Please log in again."
            Timber.e("No token available for fetching PDB details")
            onError(IllegalStateException("No token available"))
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = api.getPdbDetails(workerId, currentToken)
                withContext(Dispatchers.Main) {
                    if (response.startDate == null && response.endDate == null && response.pdbLocationLong == null) {
                        _pdbDetails.value = null
                        _pdbError.value =
                            "No pre-departure details available at this time. Please check back later."
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

    // Update updatePdbStatus
    fun updatePdbStatus(context: Context, onResult: (Boolean, String?) -> Unit) {
        val workerId = PrefsHelper.getWorkerId(context) ?: run {
            Timber.e("No workerId available for updating PDB status")
            onResult(false, "No workerId available")
            return
        }
        val currentToken = _token.value ?: run {
            Timber.e("No token available for updating PDB status")
            onResult(false, "No token available")
            return
        }
        viewModelScope.launch {
            try {
                val response = api.updatePdbStatus(workerId, currentToken)
                withContext(Dispatchers.Main) {
                    if (response.success) {
                        fetchPdbDetails(context) { error ->
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

    // Update updatePdbInternalStatus
    fun updatePdbInternalStatus(context: Context, onResult: (Boolean, String?) -> Unit) {
        val workerId = PrefsHelper.getWorkerId(context) ?: run {
            Timber.e("No workerId available for updating internal PDB status")
            onResult(false, "No workerId available")
            return
        }
        val currentToken = _token.value ?: run {
            Timber.e("No token available for updating internal PDB status")
            onResult(false, "No token available")
            return
        }
        viewModelScope.launch {
            try {
                val response = api.updatePdbInternalStatus(workerId, currentToken)
                withContext(Dispatchers.Main) {
                    if (response.success) {
                        fetchPdbDetails(context) { error ->
                            if (error != null) {
                                onResult(false, "Failed to refresh PDB details: ${error.message}")
                            } else {
                                _pdbDetails.value =
                                    _pdbDetails.value?.copy(internalPdbStatus = "App OK")
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

    // Update updateFlightStatus
    fun updateFlightStatus(context: Context, onResult: (Boolean, String?) -> Unit) {
        val workerId = PrefsHelper.getWorkerId(context) ?: run {
            Timber.e("No workerId available for updating flight status")
            onResult(false, "No workerId available")
            return
        }
        val currentToken = _token.value ?: run {
            Timber.e("No token available for updating flight status")
            onResult(false, "No token available")
            return
        }
        viewModelScope.launch {
            try {
                val response = api.updateFlightStatus(workerId, currentToken)
                withContext(Dispatchers.Main) {
                    if (response.success) {
                        fetchFlightDetails(context) { error ->
                            if (error != null) {
                                onResult(
                                    false,
                                    "Failed to refresh flight details: ${error.message}"
                                )
                            } else {
                                onResult(true, response.message)
                            }
                        }
                    } else {
                        onResult(false, response.message)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to update flight status")
                withContext(Dispatchers.Main) {
                    onResult(false, e.message)
                }
            }
        }
    }

    // Update fetchDirectory
    fun fetchDirectory(context: Context) {
        val workerId = PrefsHelper.getWorkerId(context) ?: run {
            Timber.e("No workerId available for fetching directory")
            return
        }
        val token = _token.value ?: run {
            Timber.e("No token available for fetching directory")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entries = api.getDirectory(token, workerId)
                withContext(Dispatchers.Main) {
                    _directoryEntries.value = entries
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch directory")
                withContext(Dispatchers.Main) {
                    _directoryEntries.value = emptyList()
                    if (e is HttpException && e.message?.contains("Invalid JWT") == true) {
                        logout(context)
                    }
                }
            }
        }
    }

    // Update fetchTeams
    fun fetchTeams(context: Context, limit: Int = 50, offset: Int = 0) {
        val workerId = PrefsHelper.getWorkerId(context) ?: run {
            Timber.e("No workerId available for fetching teams")
            return
        }
        val token = _token.value ?: run {
            Timber.e("No token available for fetching teams")
            return
        }
        viewModelScope.launch {
            try {
                val teams = api.getTeams(token, workerId, limit, offset)
                withContext(Dispatchers.Main) {
                    _teamEntries.value =
                        teams.filter { it.teamName != null && it.teamName.isNotEmpty() }
                    _teamEntries.value.forEach { team ->
                        fetchTeamLocations(token, workerId, team.teamId)
                    }
                }
            } catch (e: HttpException) {
                Timber.e(e, "Failed to fetch teams")
            } catch (e: JsonParseException) {
                Timber.e(e, "Failed to parse teams response")
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch teams")
            }
        }
    }

    // Update fetchTeamLocations
    private fun fetchTeamLocations(token: String, workerId: String, teamId: Int) {
        viewModelScope.launch {
            try {
                val locations = api.getTeamLocations(token, workerId, teamId)
                withContext(Dispatchers.Main) {
                    _teamLocations.value = _teamLocations.value.toMutableMap().apply {
                        put(teamId, locations)
                    }
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

    // Update submitFeedback
    suspend fun submitFeedback(
        context: Context,
        teamId: Int,
        feedbackText: String,
        screen: String?
    ) {
        val workerId = PrefsHelper.getWorkerId(context)
            ?: throw IllegalArgumentException("No workerId available")
        val token = _token.value ?: throw IllegalStateException("No token available")
        if (workerId.isBlank()) {
            Timber.w("Worker ID is blank")
            throw IllegalArgumentException("Worker ID cannot be blank")
        }
        val trimmedFeedback = feedbackText.trim()
        if (trimmedFeedback.isEmpty()) {
            Timber.w("Feedback text is empty after trimming")
            throw IllegalArgumentException("Feedback text cannot be empty")
        }
        val body = FeedbackRequest(
            workerId = workerId,
            teamId = if (teamId == 0) null else teamId,
            feedbackText = trimmedFeedback,
            screen = screen
        )
        Timber.d(
            "Submitting feedback to %s: %s",
            if (teamId > 0) "team.php/teams/feedback" else "feedback.php/submit",
            body
        )
        val response = if (teamId > 0) {
            api.submitTeamFeedback(token, body)
        } else {
            api.submitFeedback(token, body)
        }
        if (!response.isSuccessful) {
            Timber.e(
                "Feedback submission failed with code %d: %s",
                response.code(),
                response.errorBody()?.string()
            )
            throw HttpException(response)
        }
    }

    // Update acceptApplication
    suspend fun acceptApplication(context: Context) {
        val workerId = PrefsHelper.getWorkerId(context)
            ?: throw IllegalArgumentException("No workerId available")
        val token = _token.value ?: throw IllegalStateException("No token available")
        val body = mapOf("workerId" to workerId)
        val response = api.acceptApplication(token, body)
        if (!response.isSuccessful) {
            throw HttpException(response)
        }
    }

    // Update fetchNotices
    suspend fun fetchNotices(context: Context) {
        val workerId = PrefsHelper.getWorkerId(context) ?: run {
            Timber.e("No workerId available for fetching notices")
            _notices.value = null
            return
        }
        val token = _token.value ?: run {
            Timber.e("No token available for fetching notices")
            _notices.value = null
            return
        }
        try {
            val response = api.getNotices(token, workerId)
            if (response.isSuccessful) {
                val body = response.body()
                withContext(Dispatchers.Main) {
                    _notices.value = body?.get("notices")
                }
            } else {
                Timber.e("Failed to fetch notices: HTTP ${response.code()}")
                throw HttpException(response)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch notices")
            withContext(Dispatchers.Main) {
                _notices.value = null
            }
        }
    }

    fun updateUsername(username: String, context: Context, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val workerId = PrefsHelper.getWorkerId(context)
                if (workerId.isNullOrEmpty()) {
                    Timber.e("UpdateUsername: No workerId in SharedPreferences")
                    onError("Session expired. Please log in again.")
                    return@launch
                }
                val token = PrefsHelper.getToken(context)
                if (token.isNullOrEmpty()) {
                    Timber.e("UpdateUsername: No token in SharedPreferences")
                    onError("Session expired. Please log in again.")
                    return@launch
                }
                Timber.d("UpdateUsername: Calling API with workerId=$workerId, username=$username, token=$token")
                val request = UpdateUsernameRequest(workerId, username)
                val response = api.updateUsername(token, request)
                if (response.success) {
                    PrefsHelper.saveUsername(context, username)
                    _showUsernamePrompt.value = false
                    Timber.d("UpdateUsername: Success for workerId=$workerId, username=$username")
                    onSuccess()
                } else {
                    Timber.e("UpdateUsername: Failed with message: ${response.message}")
                    onError(response.message ?: "Failed to update username")
                }
            } catch (e: HttpException) {
                Timber.e(e, "UpdateUsername: HTTP error ${e.code()}: ${e.message}")
                if (e.code() == 401) {
                    onError("Unauthorized: Invalid or expired session. Please log in again.")
                } else {
                    onError("Server error: ${e.code()}. Please try again.")
                }
            } catch (e: Exception) {
                Timber.e(e, "UpdateUsername: Unexpected error")
                onError("Failed to update username: ${e.message}")
            }
        }
    }
    fun resetUsernamePrompt() {
        _showUsernamePrompt.value = false
        Timber.d("ResetUsernamePrompt: Cleared showUsernamePrompt")
    }
}

