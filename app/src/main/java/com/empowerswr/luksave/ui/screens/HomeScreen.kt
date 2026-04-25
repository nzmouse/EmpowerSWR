package com.empowerswr.luksave.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.empowerswr.luksave.EmpowerViewModel
import com.empowerswr.luksave.PrefsHelper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber

// Current app version - UPDATED EVERY TIME A NEW VERSION IS RELEASED
private const val CURRENT_APP_VERSION = "2.9"   // ← Change this when you upload a new version to Play Store
private suspend fun handleUsernameSubmission(
    usernameInput: String,
    viewModel: EmpowerViewModel,
    context: Context,
    keyboardController: SoftwareKeyboardController?,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    Timber.d("HomeScreen: Username prompt: Starting submission for '$usernameInput'")
    try {
        keyboardController?.hide() // Hide keyboard safely
        val workerId = PrefsHelper.getWorkerId(context)
        if (workerId.isNullOrEmpty()) {
            Timber.e("HomeScreen: Username prompt: No workerId, prompting re-login")
            onError("Session expired. Please log in again.")
            return
        }
        viewModel.updateUsername(
            usernameInput,
            context,
            onSuccess = {
                Timber.d("HomeScreen: Username prompt: Update successful for '$usernameInput'")
                onSuccess()
            },
            onError = { error ->
                Timber.e("HomeScreen: Username prompt: Update failed - $error")
                onError(error)
            }
        )
    } catch (e: Exception) {
        Timber.e(e, "HomeScreen: Username prompt: Unexpected error during submission")
        onError("Failed to update username: ${e.message}")
    }
}

@Suppress("NewApi")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: EmpowerViewModel,
    context: Context,
    navController: NavController
) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarScope = rememberCoroutineScope { Dispatchers.Main }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var latestUpdateUrl by remember { mutableStateOf<String?>(null) }
    var phone by rememberSaveable { mutableStateOf("") }
    var fcmError by remember { mutableStateOf<String?>(null) }
    var refreshError by remember { mutableStateOf<String?>(null) }
    var locationError by remember { mutableStateOf<String?>(null) }
    var contractError by remember { mutableStateOf<String?>(null) }
    var isCheckingIn by remember { mutableStateOf(false) }
    var isFindingMe by remember { mutableStateOf(false) }
    var isSigningContract by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val token by viewModel.token
    val workerDetails by viewModel.workerDetails
    val checkInSuccess by viewModel.checkInSuccess
    val contractSuccess by viewModel.contractSuccess
    var showContractCard by remember { mutableStateOf(true) }
    val showUsernamePrompt by viewModel.showUsernamePrompt
    var usernameInput by rememberSaveable { mutableStateOf("") }
    var usernameError by remember { mutableStateOf<String?>(null) }
    var showUsernameSuccess by remember { mutableStateOf(false) }
    val checkInError by viewModel.checkInError
    val notifications by viewModel.notifications
    val notificationFromIntent by viewModel.notificationFromIntent
    val isPhoneSubmitValid by remember { derivedStateOf { phone.matches(Regex("^\\d{7,15}$")) } }
    var showCheckInSection by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showSettingsPrompt by remember { mutableStateOf(false) }
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    val localContext = LocalContext.current
    var secretAnswerInput by remember { mutableStateOf("") }
    var showSkipWarning by remember { mutableStateOf(false) }
    var nidInput by remember { mutableStateOf("") }
    var nidExpInput by remember { mutableStateOf("") }
    var isSavingNID by remember { mutableStateOf(false) }

    // Log screen usage
    LaunchedEffect(Unit) {
        Timber.i("ScreenUsage: HomeScreen displayed, workerId=${PrefsHelper.getWorkerId(context) ?: "unknown"}, timestamp=${System.currentTimeMillis()}")
    }
    context.findEmpowerActivity() ?: run {
        throw IllegalStateException("HomeScreen must be called within a ComponentActivity")
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        Timber.i("Location Permission result: fineGranted=%b, coarseGranted=%b", fineGranted, coarseGranted)
        if (fineGranted || coarseGranted) {
            locationError = null
        } else {
            showSettingsPrompt = true
            locationError = "Location permission denied. Please enable it in app settings."
            Timber.tag("HomeScreen").e("Location permission denied, showing settings prompt")
        }
    }

    // Settings launcher
    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED) {
            locationError = null
            Timber.i("Location permission granted after settings")
        } else {
            showSettingsPrompt = true
            locationError = "Location permission still denied. Please enable it in settings."
            Timber.tag("HomeScreen").e("Location permission still denied after settings")
        }
    }

    // Check permissions on start
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    // Check permission and request
    fun checkAndRequestLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }
    fun saveNIDAndSecret() {
        Timber.tag("NID_SAVE").d("saveNIDAndSecret() START - nid='$nidInput', motherName='$secretAnswerInput'")

        if (nidInput.length < 4) {
            Toast.makeText(context, "National ID i mas gat least 4 digits", Toast.LENGTH_SHORT).show()
            return
        }
        if (secretAnswerInput.isBlank()) {
            Toast.makeText(context, "Plis putum nem blong mama blong yu", Toast.LENGTH_SHORT).show()
            return
        }

        isSavingNID = true

        coroutineScope.launch {
            val workerId = PrefsHelper.getWorkerId(context)
            if (workerId.isNullOrEmpty()) {
                Timber.tag("NID_SAVE").d("No workerId found")
                Toast.makeText(context, "Session expired. Please log in again.", Toast.LENGTH_LONG).show()
                isSavingNID = false
                return@launch
            }

            Timber.tag("NID_SAVE").d("Calling ViewModel with workerId=$workerId")

            viewModel.updateWorkerNIDAndSecret(
                workerId = workerId,
                nid = nidInput,
                motherName = secretAnswerInput      // <-- this must be passed
            ) { success, message ->
                isSavingNID = false
                nidInput = ""
                secretAnswerInput = ""

                if (success) {
                    viewModel.fetchWorkerDetails(context) { }
                    Toast.makeText(context, message ?: "National ID mo nem blong mama i save finis", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, message ?: "Failed to save. Please try again.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    @Composable
    fun ChecklistItem(label: String) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
    // Settings prompt dialog
    if (showSettingsPrompt) {
        AlertDialog(
            onDismissRequest = { showSettingsPrompt = false },
            title = { Text("Permission Required") },
            text = { Text("Location permission is required for check-in. Please enable it in app settings.") },
            confirmButton = {
                Button(
                    onClick = {
                        showSettingsPrompt = false
                        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        intent.data = Uri.fromParts("package", context.packageName, null)
                        settingsLauncher.launch(intent)
                    }
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                Button(
                    onClick = { showSettingsPrompt = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Force recomposition on workerDetails change
    LaunchedEffect(workerDetails) {
        Timber.i("workerDetails changed")
    }

    LaunchedEffect(token) {
        if (token == null) {
            Timber.tag("HomeScreen").e("No token, redirecting to login")
            navController.navigate("login") {
                popUpTo("home") { inclusive = true }
            }
        } else {
            viewModel.fetchWorkerDetails(context) { error ->
                error?.message ?: "Failed to load worker details"
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(5000)
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val fcmToken = task.result
                PrefsHelper.saveFcmToken(localContext, fcmToken)
                val workerId = PrefsHelper.getWorkerId(localContext)
                if (workerId != null) {
                    viewModel.updateFcmToken(fcmToken, localContext)
                } else {
                    Timber.tag("HomeScreen").e("No workerId available for FCM token update")
                }
            } else {
                fcmError = "Failed to get FCM token: ${task.exception?.message}"
                Timber.tag("HomeScreen").e(task.exception, "FCM Token Error: %s", task.exception?.message)
            }
        }
    }

    // Handle notifications from ViewModel
    LaunchedEffect(notifications) {
        notifications.forEach { notification ->
            snackbarScope.launch {
                try {
                    val result = snackbarHostState.showSnackbar(
                        message = "${notification.title}: ${notification.body}",
                        actionLabel = "Dismiss",
                        duration = SnackbarDuration.Indefinite
                    )
                    viewModel.removeNotification(notification)
                } catch (e: Exception) {
                    Timber.tag("HomeScreen").e(e, "Failed to show notification Snackbar")
                }
            }
        }
    }

    // Handle intent-based notifications
    LaunchedEffect(notificationFromIntent) {
        notificationFromIntent?.let { (title, body) ->
            if (title != null && body != null && title.isNotBlank() && body.isNotBlank()) {
                snackbarScope.launch {
                    try {
                        Timber.i("Showing Snackbar for Title: $title, Body: $body")
                        snackbarHostState.showSnackbar(
                            message = "$title: $body",
                            actionLabel = "Dismiss",
                            duration = SnackbarDuration.Long
                        )
                        viewModel.setNotificationFromIntent(null, null)
                    } catch (e: Exception) {
                        Timber.tag("HomeScreen").e(e, "Failed to show intent notification Snackbar")
                    }
                }
            } else {
                Timber.i("Invalid notification data: Title=$title, Body=$body")
            }
        }
    }

    // Handle errors (refresh, location, contract)
    LaunchedEffect(refreshError, locationError, contractError) {
        val error = refreshError ?: locationError ?: contractError
        error?.let {
            snackbarScope.launch {
                try {
                    snackbarHostState.showSnackbar(
                        message = it,
                        actionLabel = "Dismiss",
                        duration = SnackbarDuration.Long
                    )
                    if (it == refreshError) refreshError = null
                    if (it == locationError) locationError = null
                    if (it == contractError) contractError = null
                } catch (e: Exception) {
                    Timber.tag("HomeScreen").e(e, "Failed to show error Snackbar")
                }
            }
        }
    }

    LaunchedEffect(checkInSuccess) {
        if (checkInSuccess == true) {
            phone = ""
            isCheckingIn = false
        }
    }
    LaunchedEffect(contractSuccess) {
        if (contractSuccess == true) {
            isSigningContract = false
            showContractCard = false
        }
    }
    LaunchedEffect(checkInError) {
        checkInError?.let { message ->
            snackbarScope.launch {
                try {
                    snackbarHostState.showSnackbar(
                        message = message,
                        actionLabel = "Dismiss",
                        duration = SnackbarDuration.Long
                    )
                    showCheckInSection = false
                    viewModel.clearCheckInState()
                    isCheckingIn = false
                } catch (e: Exception) {
                    Timber.tag("HomeScreen").e(e, "Failed to show check-in Snackbar")
                }
            }
        }
    }

    // Show snackbar for username success
    LaunchedEffect(showUsernameSuccess) {
        if (showUsernameSuccess) {
            try {
                snackbarHostState.showSnackbar(
                    message = "Username set successfully.  You can now use your username to log in!",
                    actionLabel = "Dismiss",
                    duration = SnackbarDuration.Short
                )

            } catch (e: Exception) {
                Timber.e(e, "HomeScreen: Username prompt: Failed to show snackbar")
                usernameError = "Failed to show confirmation: ${e.message}"
            }
            showUsernameSuccess = false
        }
    }
    // Check for new app version (runs once when HomeScreen loads)
    LaunchedEffect(Unit) {
        viewModel.checkForUpdate { hasUpdate, latestVersion, updateUrl ->
            if (hasUpdate && latestVersion != null) {
                if (isNewerVersion(CURRENT_APP_VERSION, latestVersion)) {
                    showUpdateDialog = true
                    latestUpdateUrl = updateUrl
                }
            }
        }
    }
    // Helper to check if NID is expired

    fun isNIDExpired(expiryDate: String?): Boolean {
        if (expiryDate == null || expiryDate == "0000-00-00" || expiryDate.isEmpty()) return false
        return try {
            val today = java.time.LocalDate.now()
            val exp = java.time.LocalDate.parse(expiryDate)
            exp.isBefore(today)
        } catch (e: Exception) {
            false
        }
    }
    // Helper to accept dd/mm/yyyy, dd-mm-yyyy, dd mmm yyyy, and convert to yyyy-mm-dd
    fun normaliseDate(input: String): String? {
        val cleaned = input.trim().replace(Regex("[\\s/.-]+"), "-")  // normalise separators

        // Try dd/mm/yyyy or dd-mm-yyyy
        val ddmmyyyy = Regex("^(\\d{1,2})-(\\d{1,2})-(\\d{4})$").find(cleaned)
        if (ddmmyyyy != null) {
            val (d, m, y) = ddmmyyyy.destructured
            val day = d.toIntOrNull() ?: return null
            val month = m.toIntOrNull() ?: return null
            val year = y.toIntOrNull() ?: return null
            if (day in 1..31 && month in 1..12 && year in 1900..2100) {
                return "%04d-%02d-%02d".format(year, month, day)
            }
        }

        // Try dd mmm yyyy (e.g. 15 Dec 2028)
        val ddmmmyyyy = Regex("^(\\d{1,2})-([A-Za-z]{3,9})-(\\d{4})$").find(cleaned)
        if (ddmmmyyyy != null) {
            val (d, m, y) = ddmmmyyyy.destructured
            val day = d.toIntOrNull() ?: return null
            val year = y.toIntOrNull() ?: return null
            val month = when (m.lowercase()) {
                "jan", "january" -> 1
                "feb", "february" -> 2
                "mar", "march" -> 3
                "apr", "april" -> 4
                "may" -> 5
                "jun", "june" -> 6
                "jul", "july" -> 7
                "aug", "august" -> 8
                "sep", "september" -> 9
                "oct", "october" -> 10
                "nov", "november" -> 11
                "dec", "december" -> 12
                else -> return null
            }
            if (day in 1..31 && year in 1900..2100) {
                return "%04d-%02d-%02d".format(year, month, day)
            }
        }

        // Already yyyy-mm-dd
        if (cleaned.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) {
            return cleaned
        }

        return null
    }
    // Save NID + Expiry Date
    // Save when NID number is missing (saves both NID + Expiry)
    fun saveNIDAndExpiry() {
        Timber.tag("NID_EXPIRY").d("saveNIDAndExpiry() START - nid='$nidInput', expiry='$nidExpInput'")

        if (nidInput.length < 4) {
            Toast.makeText(context, "National ID i mas gat least 4 digits", Toast.LENGTH_SHORT).show()
            return
        }

        val normalisedExpiry = normaliseDate(nidExpInput)
        if (normalisedExpiry == null) {
            Toast.makeText(context, "Plis yusum date olsem: 31/12/2028 o 31-12-2028 o 31 Dec 2028", Toast.LENGTH_LONG).show()
            return
        }

        isSavingNID = true

        coroutineScope.launch {
            val workerId = PrefsHelper.getWorkerId(context)
            if (workerId.isNullOrEmpty()) {
                Toast.makeText(context, "Session expired. Please log in again.", Toast.LENGTH_LONG).show()
                isSavingNID = false
                return@launch
            }

            viewModel.updateNIDAndExpiry(workerId, nidInput, normalisedExpiry) { success, message ->
                isSavingNID = false
                nidInput = ""
                nidExpInput = ""

                if (success) {
                    viewModel.fetchWorkerDetails(context) { }
                    Toast.makeText(context, message ?: "National ID mo expiry i save finis", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, message ?: "Failed to save. Please try again.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Update only expiry date (when NID already exists but expired)
    fun saveNIDExpiryOnly() {
        Timber.tag("NID_EXPIRY").d("saveNIDExpiryOnly() START - expiry='$nidExpInput'")

        val normalisedExpiry = normaliseDate(nidExpInput)
        if (normalisedExpiry == null) {
            Toast.makeText(context, "Plis yusum date olsem: 31/12/2028 o 31-12-2028 o 31 Dec 2028", Toast.LENGTH_LONG).show()
            return
        }

        isSavingNID = true

        coroutineScope.launch {
            val workerId = PrefsHelper.getWorkerId(context)
            if (workerId.isNullOrEmpty()) {
                Toast.makeText(context, "Session expired. Please log in again.", Toast.LENGTH_LONG).show()
                isSavingNID = false
                return@launch
            }

            viewModel.updateNIDExpiryOnly(workerId, normalisedExpiry) { success, message ->
                isSavingNID = false
                nidExpInput = ""

                if (success) {
                    viewModel.fetchWorkerDetails(context) { }
                    Toast.makeText(context, message ?: "NID Expiry i update finis", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, message ?: "Failed to update expiry. Please try again.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }


    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                coroutineScope.launch {
                    try {
                        isRefreshing = true
                        viewModel.fetchWorkerDetails(context) { error ->
                            refreshError = error?.message?.let { "Refresh failed: $it" } ?: "Refresh failed"
                            isRefreshing = false
                        }
                        delay(1000)
                    } catch (e: Exception) {
                        refreshError = "Refresh failed: ${e.message}"
                        Timber.tag("HomeScreen").e(e, "Refresh error")
                    } finally {
                        isRefreshing = false
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    Text(
                        text = "Home Screen - Welcome!",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (!isFindingMe) {
                                coroutineScope.launch {
                                    isFindingMe = true
                                    checkAndRequestLocationPermission()
                                    performLocationUpdate(
                                        context = context,
                                        fusedLocationClient = fusedLocationClient,
                                        viewModel = viewModel,
                                        action = "Find-Me",
                                        onError = { error -> locationError = error }
                                    )
                                    isFindingMe = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.inversePrimary
                        ),
                        enabled = !isFindingMe
                    ) {
                        Text("Find Me")
                    }

                    workerDetails?.let { worker ->
                        if ((worker.notices == "Locate" || worker.notices == "Messaged") && showCheckInSection) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .border(
                                        2.dp,
                                        MaterialTheme.colorScheme.error,
                                        MaterialTheme.shapes.medium
                                    ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "IMPORTANT: Mifala traem faendem yu naoia. Kolem ofis long 34357.",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedTextField(
                                        value = phone,
                                        onValueChange = { newValue ->
                                            if (newValue.matches(Regex("^\\d*$"))) {
                                                phone = newValue
                                            }
                                        },
                                        label = { Text("Fon namba blong yu") },
                                        isError = phone.isNotEmpty() && !isPhoneSubmitValid,
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Phone,
                                            imeAction = ImeAction.Done
                                        ),
                                        keyboardActions = KeyboardActions(
                                            onDone = {
                                                if (isPhoneSubmitValid && !isCheckingIn) {
                                                    keyboardController?.hide()
                                                    coroutineScope.launch {
                                                        isCheckingIn = true
                                                        checkAndRequestLocationPermission()
                                                        performCheckInAndSaveLocation(
                                                            context = context,
                                                            fusedLocationClient = fusedLocationClient,
                                                            viewModel = viewModel,
                                                            phone = phone,
                                                            onError = { error: String -> locationError = error }
                                                        )
                                                    }
                                                } else {
                                                    Timber.tag("HomeScreen").e("Invalid phone number or check-in in progress")
                                                }
                                            }
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = TextFieldDefaults.colors(
                                            focusedIndicatorColor = MaterialTheme.colorScheme.error,
                                            unfocusedIndicatorColor = MaterialTheme.colorScheme.error,
                                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                            errorContainerColor = MaterialTheme.colorScheme.surface,
                                            errorTextColor = MaterialTheme.colorScheme.onSurface
                                        ),
                                        enabled = !isCheckingIn
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = {
                                            if (isPhoneSubmitValid && !isCheckingIn) {
                                                keyboardController?.hide()
                                                coroutineScope.launch {
                                                    isCheckingIn = true
                                                    checkAndRequestLocationPermission()
                                                    performCheckInAndSaveLocation(
                                                        context = context,
                                                        fusedLocationClient = fusedLocationClient,
                                                        viewModel = viewModel,
                                                        phone = phone,
                                                        onError = { error: String -> locationError = error }
                                                    )
                                                }
                                            } else {
                                                Timber.tag("HomeScreen").e("Invalid phone number or check-in in progress")
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.inversePrimary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        enabled = isPhoneSubmitValid && !isCheckingIn
                                    ) {
                                        Text("Check In", style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                        // Status Card
                        workerDetails?.let { worker ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .border(
                                        2.dp,
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.shapes.medium
                                    ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    Text(
                                        text = "STATUS",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = worker.notices ?: "",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                        // ==================== NID + MOTHER'S NAME CARD ====================
                        // Only show if NID is missing OR secret answer is missing
                        workerDetails?.let { worker ->
                            if (worker.nid.isNullOrBlank() || worker.secretQuestion.isNullOrBlank()) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                        .border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "Fastaem blong save PIN blong yu",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Putum National ID blong yu mo nem blong mami blong yu, from bae i helpem yu blong resetem PIN sapos yu fogetem.",
                                            style = MaterialTheme.typography.bodyMedium
                                        )

                                        Spacer(modifier = Modifier.height(16.dp))

                                        // NID Field
                                        OutlinedTextField(
                                            value = nidInput,
                                            onValueChange = { if (it.matches(Regex("^\\d*$"))) nidInput = it },
                                            label = { Text("National ID Card Number (NID)") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        Spacer(modifier = Modifier.height(16.dp))

                                        // Secret Question - Mother's first name
                                        Text(
                                            text = "Wanem nem blong mami blong yu?",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        OutlinedTextField(
                                            value = secretAnswerInput,
                                            onValueChange = { secretAnswerInput = it.trim() },
                                            label = { Text("Ansa") },
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        Spacer(modifier = Modifier.height(20.dp))

                                        Button(
                                            onClick = { saveNIDAndSecret() },
                                            modifier = Modifier.fillMaxWidth(),
                                            enabled = nidInput.length >= 4 && secretAnswerInput.isNotBlank() && !isSavingNID
                                        ) {
                                            if (isSavingNID) {
                                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                            } else {
                                                Text("Save")
                                            }
                                        }

                                        TextButton(onClick = { showSkipWarning = true }) {
                                            Text("Skip / Mekem nara taem", color = MaterialTheme.colorScheme.onPrimaryContainer)
                                        }
                                    }
                                }
                            }
                        }
                        if (worker.contract == "Ready to Sign" && showContractCard) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .border(
                                        2.dp,
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.shapes.medium
                                    ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "ACTION REQUIRED: Your contract is ready to sign!",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = {
                                            if (!isSigningContract) {
                                                coroutineScope.launch {
                                                    isSigningContract = true
                                                    viewModel.signContract(context)
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.inversePrimary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        enabled = !isSigningContract
                                    ) {
                                        Text("OK!  Bae mi kam saen wantaem", style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                        // ==================== SMART "THINGS YOU MUST DO" CARD ====================
                        workerDetails?.let { worker ->
                            val country = worker.rsecountry?.trim()?.uppercase() ?: ""
                            val scheme = when {
                                country.contains("NZ") || country == "NEW ZEALAND" -> "RSE"
                                country.contains("AU") || country == "AUSTRALIA" || country == "PALM" -> "PALM"
                                else -> "BOTH"
                            }

                            // Load dynamic tasks from backend
                            var requiredTasks by remember { mutableStateOf<List<String>>(emptyList()) }

                            LaunchedEffect(scheme) {
                                viewModel.loadRequiredTasks(scheme) { tasks ->
                                    requiredTasks = tasks
                                }
                            }

                            if (requiredTasks.isNotEmpty() &&
                                (worker.notices == "App Checkin" ||
                                        worker.notices == "App-Accepted" ||
                                        worker.notices == "Notified" ||
                                        worker.notices == "Reported In" ||
                                        worker.notices == "Underway")) {

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp)
                                        .border(3.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(20.dp)) {
                                        Text(
                                            text = "Ol samting yu mas mekem naoia",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = if (scheme == "RSE") "RSE New Zealand"
                                            else if (scheme == "PALM") "PALM Australia"
                                            else "Seasonal Worker Program",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        Spacer(modifier = Modifier.height(20.dp))

                                        // Dynamic Checklist
                                        requiredTasks.forEach { task ->
                                            ChecklistItem(label = task)
                                        }

                                        Spacer(modifier = Modifier.height(24.dp))

                                        if (worker.notices == "App Checkin") {
                                            Button(
                                                onClick = { navController.navigate("team") },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("View & Accept New Job")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    fcmError?.let { error ->
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "FCM Error: $error",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    // ==================== NATIONAL ID CARD (NID Number + Expiry) ====================
                    workerDetails?.let { worker ->
                        val hasNID = !worker.nid.isNullOrBlank()
                        val hasExpiry = !worker.NIDExp.isNullOrBlank() && worker.NIDExp != "0000-00-00"
                        val isExpired = hasExpiry && isNIDExpired(worker.NIDExp)

                        // 1. Missing NID → Show full card (NID + Expiry)
                        if (!hasNID) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Fastaem blong save PIN blong yu",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Putum National ID blong yu mo expiry date blong i save helpem yu resetim PIN sapos yu fogetem.",
                                        style = MaterialTheme.typography.bodyMedium
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    OutlinedTextField(
                                        value = nidInput,
                                        onValueChange = { if (it.matches(Regex("^\\d*$"))) nidInput = it },
                                        label = { Text("National ID Card Number (NIN)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    OutlinedTextField(
                                        value = nidExpInput,
                                        onValueChange = { newValue ->
                                            // Allow digits, letters (for month names), spaces, /, -, and limit length
                                            if (newValue.matches(Regex("^[\\dA-Za-z\\s/\\-]{0,12}$"))) {
                                                nidExpInput = newValue
                                            }
                                        },
                                        label = { Text("Expiry Date (e.g. 31/12/2028 or 31 Dec 2028)") },
                                        placeholder = { Text("31/12/2028") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),  // Changed to Text
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Button(
                                        onClick = {
                                            if (!hasNID) saveNIDAndExpiry()
                                            else saveNIDExpiryOnly()
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = (hasNID || nidInput.length >= 4) &&
                                                normaliseDate(nidExpInput) != null &&
                                                !isSavingNID
                                    ) {
                                        if (isSavingNID) {
                                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                        } else {
                                            Text(if (!hasNID) "Save NID & Expiry Date" else "Update Expiry Date")
                                        }
                                    }
                                }
                            }
                        }
                        // 2. Has NID but expired → Show warning + expiry update only
                        else if (isExpired) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .border(2.dp, MaterialTheme.colorScheme.error, MaterialTheme.shapes.medium),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "⚠️ National ID blong yu i expaia finis!",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Plis putum niu expiry date blong National ID kad blong yu.",
                                        style = MaterialTheme.typography.bodyMedium
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Improved Expiry Input - accepts dd/mm/yyyy, dd-mm-yyyy, dd mmm yyyy
                                    OutlinedTextField(
                                        value = nidExpInput,
                                        onValueChange = { newValue ->
                                            if (newValue.matches(Regex("^[\\dA-Za-z\\s/\\-]{0,12}$"))) {
                                                nidExpInput = newValue
                                            }
                                        },
                                        label = { Text("Niu Expiry Date") },
                                        placeholder = { Text("31/12/2028 or 31 Dec 2028") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Button(
                                        onClick = { saveNIDExpiryOnly() },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = normaliseDate(nidExpInput) != null && !isSavingNID
                                    ) {
                                        if (isSavingNID) {
                                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                        } else {
                                            Text("Update Expiry Date")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // ==================== MEDICAL CARD - Smart Logic (NZ + HAP ID + Acknowledgement) ====================
                    workerDetails?.let { worker ->
                        val emedStatus = worker.emed?.trim() ?: ""
                        val hapId = worker.hapid?.trim() ?: ""
                        val country = worker.rsecountry?.trim() ?: ""
                        val isNZ = country.equals("NZ", ignoreCase = true)

                        val hasValidHapid = hapId.isNotEmpty() && hapId != "0" && hapId.lowercase() != "null"

                        // Decide whether to show the card at all
                        val showMedicalCard = when {
                            emedStatus.equals("Not required", ignoreCase = true) -> false
                            emedStatus.equals("Required", ignoreCase = true) -> true          // show even without HAP ID
                            isNZ -> true                                                      // NZ workers always see General Medical section
                            hasValidHapid || emedStatus.equals("Not Yet", ignoreCase = true) -> true
                            else -> !emedStatus.isEmpty()
                        }

                        if (!showMedicalCard) return@let

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .border(
                                    2.dp,
                                    if (emedStatus == "ALERT!") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                                    MaterialTheme.shapes.medium
                                ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (emedStatus == "ALERT!")
                                    MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface,
                                contentColor = if (emedStatus == "ALERT!")
                                    MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(
                                    text = if (isNZ) "GENERAL MEDICAL (NZ)" else "eMEDICAL (Australia)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (emedStatus == "ALERT!")
                                        MaterialTheme.colorScheme.error
                                    else
                                        MaterialTheme.colorScheme.primary
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // HAP ID line (only show for non-NZ when relevant)
                                if (!isNZ) {
                                    Text(
                                        text = if (hasValidHapid) "HAP ID: $hapId" else "HAP ID: Not issued yet",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = if (hasValidHapid)
                                            MaterialTheme.colorScheme.onSurface
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                }

                                // Current Status
                                val displayStatus = when {
                                    emedStatus.isEmpty() -> "Pending"
                                    emedStatus.equals("Not required", ignoreCase = true) -> "Not Required"
                                    emedStatus.equals("Required", ignoreCase = true) -> "Required"
                                    else -> emedStatus
                                }

                                Text(
                                    text = "Current Status: $displayStatus",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = if (emedStatus == "ALERT!")
                                        MaterialTheme.colorScheme.error
                                    else
                                        MaterialTheme.colorScheme.onSurface
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // ==================== BUTTON LOGIC ====================

                                when {
                                    // ALERT! case (highest priority)
                                    emedStatus == "ALERT!" -> {
                                        OutlinedButton(
                                            onClick = { /* disabled */ },
                                            modifier = Modifier.fillMaxWidth(),
                                            enabled = false,
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                        ) {
                                            Text("⚠️ PROBLEM: Kalem ofis naoia (ALERT!)")
                                        }

                                        Text(
                                            text = "Medical blo yu i gat wan issue.\nPlis kolem office long 34357, 5534357, o 5534358 naoia.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // 1. Has HAP ID + "Not Yet" → Acknowledgement button only
                                    hasValidHapid && emedStatus.equals("Not Yet", ignoreCase = true) -> {
                                        Button(
                                            onClick = {
                                                coroutineScope.launch {
                                                    viewModel.acknowledgeGoingToMedical(context, worker.ID ?: "")   // use your actual ID field
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                        ) {
                                            Text("✅ Bae mi go blong mekem e-medikel wantaem")
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Text(
                                            text = "• Status will change to 'App-Going'\n• Then you can complete the medical",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    // 2. Has HAP ID + "Sent" or "App-Going" → Mark Done button (sets to App-Clinic)
                                    hasValidHapid && emedStatus in listOf("Sent", "App-Going") -> {
                                        Button(
                                            onClick = {
                                                coroutineScope.launch {
                                                    viewModel.markMedicalDone(context)   // this will set emed = "App-Clinic"
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                        ) {
                                            Text("✅ Mi mekem medikel finis")
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Text(
                                            text = "• Status will change to 'App-Clinic'\n• Must be done at Medical Options or Medical Centre",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    // 3. "Required" or other pending cases without HAP ID
                                    emedStatus.equals("Required", ignoreCase = true) ||
                                            (!hasValidHapid && emedStatus.equals("Not Yet", ignoreCase = true)) -> {
                                        Text(
                                            text = "HAP ID not yet issued. Please wait for the office to provide it.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    // 4. NZ workers - informational only for now
                                    isNZ && emedStatus.isEmpty() -> {
                                        Text(
                                            text = "No eMedical required for NZ unless advised by NZ Immigration.\nGeneral medical still needed.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

// Logout button (unchanged)
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                viewModel.logout(context)
                                navController.navigate("login") {
                                    popUpTo("home") { inclusive = true }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text("Log Out")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    // Add logout button
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                viewModel.logout(context)
                                navController.navigate("login") {
                                    popUpTo("home") { inclusive = true }
                                }
                                Timber.d("HomeScreen: Logged out, navigating to LoginScreen")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text("Log Out")
                    }
                }
            }
        }
    }
    // App Update Dialog
    if (showUpdateDialog) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text("Niu Version i Redi") },
            text = {
                Text("""
                    Niu version blong Luksave i stap long Google Play.
                        
                    • Ol samting we i niu:
                    • Forgetem Login Ditel Skrin (isi blong faendem username o pin)
                    • Infomesen Scrin updet  (Soem ol bank, supamaket, ples blong kaekae we i klosap)
                    • Login mo Home screen i kam mo gud
                        
                    Plis update from i gat ol best features mo fix.
                """.trimIndent())
            },
            confirmButton = {
                Button(onClick = {
                    latestUpdateUrl?.let { url ->
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    }
                    showUpdateDialog = false
                }) {
                    Text("Update Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) {
                    Text("Later")
                }
            }
        )
    }

        // Username prompt dialog (your existing one stays below)
        if (showUsernamePrompt) {
            // ... your existing username dialog code ...
        }

    // Username prompt dialog
    if (showUsernamePrompt) {
        AlertDialog(
            onDismissRequest = { /* Enforce setting username */ },
            title = { Text("Set Your Username") },
            text = {
                Column {
                    Text("Please choose a unique username to use instead of your worker ID.")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = usernameInput,
                        onValueChange = {
                            usernameInput = it.trim()
                            usernameError = null
                        },
                        label = { Text("Username") },
                        isError = usernameError != null,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (usernameInput.isEmpty() || usernameInput.length < 4) {
                                    usernameError = "Username must be at least 4 characters"
                                    Timber.d("HomeScreen: Username prompt: Validation failed - Username too short: '$usernameInput'")
                                } else {
                                    coroutineScope.launch {
                                        handleUsernameSubmission(
                                            usernameInput = usernameInput,
                                            viewModel = viewModel,
                                            context = context,
                                            keyboardController = keyboardController,
                                            onSuccess = { showUsernameSuccess = true },
                                            onError = { error -> usernameError = error }
                                        )
                                    }
                                }
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    usernameError?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (usernameInput.isEmpty() || usernameInput.length < 4) {
                            usernameError = "Username must be at least 4 characters"
                            Timber.d("HomeScreen: Username prompt: Validation failed - Username too short: '$usernameInput'")
                        } else {
                            coroutineScope.launch {
                                handleUsernameSubmission(
                                    usernameInput = usernameInput,
                                    viewModel = viewModel,
                                    context = context,
                                    keyboardController = keyboardController,
                                    onSuccess = { showUsernameSuccess = true },
                                    onError = { error -> usernameError = error }
                                )
                            }
                        }
                    }
                ) {
                    Text("Save")
                }
            }
            // No dismissButton to enforce username setting
        )
    }
}

suspend fun performCheckInAndSaveLocation(
    context: Context,
    fusedLocationClient: FusedLocationProviderClient,
    viewModel: EmpowerViewModel,
    phone: String,
    onError: (String) -> Unit
) {
    try {
        viewModel.checkIn(phone, context)
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED) {
            val location: Location? = fusedLocationClient.lastLocation.await()
            if (location != null) {
                viewModel.saveLocation(
                    context,
                    location.latitude,
                    location.longitude,
                    "Check-In"
                )
            } else {
                onError("Unable to get location")
                Timber.tag("HomeScreen").e("Unable to get location")
            }
        } else {
            onError("Location permission not granted")
            Timber.tag("HomeScreen").e("Location permission not granted")
        }
    } catch (e: Exception) {
        onError("Failed to process check-in or location: ${e.message}")
        Timber.tag("HomeScreen").e(e, "Check-in or location error")
    }
}

suspend fun performLocationUpdate(
    context: Context,
    fusedLocationClient: FusedLocationProviderClient,
    viewModel: EmpowerViewModel,
    action: String,
    onError: (String) -> Unit
) {
    try {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED) {
            val location: Location? = fusedLocationClient.lastLocation.await()
            if (location != null) {
                viewModel.saveLocation(
                    context,
                    location.latitude,
                    location.longitude,
                    action
                )
            } else {
                onError("Unable to get location")
                Timber.tag("HomeScreen").e("Unable to get location")
            }
        } else {
            onError("Location permission not granted")
            Timber.tag("HomeScreen").e("Location permission not granted")
        }
    } catch (e: Exception) {
        onError("Failed to process location update: ${e.message}")
        Timber.tag("HomeScreen").e(e, "Location update error")
    }
}
private fun isNewerVersion(current: String, latest: String): Boolean {
    return try {
        val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
        val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
        latestParts.zip(currentParts).any { (l, c) -> l > c }
    } catch (e: Exception) {
        false
    }
}