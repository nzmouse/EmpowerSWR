package com.empowerswr.test.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.empowerswr.test.EmpowerViewModel
import com.empowerswr.test.PrefsHelper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: EmpowerViewModel,
    context: Context
) {
    Log.d("EmpowerSWR", "HomeScreen composable called, Android version: ${Build.VERSION.SDK_INT}, Release: ${Build.VERSION.RELEASE}")
    val coroutineScope = rememberCoroutineScope()
    val snackbarScope = rememberCoroutineScope { Dispatchers.Main }
    var phone by rememberSaveable { mutableStateOf("") }
    var fcmError by remember { mutableStateOf<String?>(null) }
    var refreshError by remember { mutableStateOf<String?>(null) }
    var locationError by remember { mutableStateOf<String?>(null) }
    var isCheckingIn by remember { mutableStateOf(false) }
    var isFindingMe by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val token by viewModel.token
    val workerDetails by viewModel.workerDetails
    val checkInSuccess by viewModel.checkInSuccess
    val checkInError by viewModel.checkInError
    val notifications by viewModel.notifications
    val notificationFromIntent by viewModel.notificationFromIntent
    val isPhoneInputValid by remember { derivedStateOf {
        Log.d("EmpowerSWR", "Checking isPhoneInputValid: phone=$phone")
        phone.matches(Regex("^\\d*$"))
    } }
    val isPhoneSubmitValid by remember { derivedStateOf {
        val valid = phone.matches(Regex("^\\d{7,15}$"))
        Log.d("EmpowerSWR", "Checking isPhoneSubmitValid: phone=$phone, valid=$valid")
        valid
    } }
    var showCheckInSection by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showSettingsPrompt by remember { mutableStateOf(false) }
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    val activity = context.findEmpowerActivity() ?: run {
        Log.e("EmpowerSWR", "Context is not a ComponentActivity")
        throw IllegalStateException("HomeScreen must be called within a ComponentActivity")
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        Log.d("EmpowerSWR", "Permission result: fineGranted=$fineGranted, coarseGranted=$coarseGranted")
        if (fineGranted || coarseGranted) {
            locationError = null
            Log.d("EmpowerSWR", "Location permission granted")
        } else {
            showSettingsPrompt = true
            locationError = "Location permission denied. Please enable it in app settings."
            Log.d("EmpowerSWR", "Permission denied, showing settings prompt")
        }
    }

    // Settings launcher
    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        Log.d("EmpowerSWR", "Returned from settings, rechecking permission")
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            locationError = null
            Log.d("EmpowerSWR", "Location permission granted after settings")
        } else {
            showSettingsPrompt = true
            locationError = "Location permission still denied. Please enable it in settings."
            Log.d("EmpowerSWR", "Location permission still denied after settings")
        }
    }

    // Check permissions on start
    LaunchedEffect(Unit) {
        Log.d("EmpowerSWR", "Checking initial permission state in HomeScreen")
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d("EmpowerSWR", "Initial permission request")
            permissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    // Check permission and request
    fun checkAndRequestLocationPermission() {
        Log.d("EmpowerSWR", "Checking location permission")
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            Log.d("EmpowerSWR", "Location permission already granted")
            snackbarScope.launch {
                snackbarHostState.showSnackbar(
                    message = "Location permission already granted",
                    actionLabel = "Dismiss",
                    duration = SnackbarDuration.Short
                )
            }
        } else {
            Log.d("EmpowerSWR", "Launching permission request")
            permissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    // Settings prompt dialog
    if (showSettingsPrompt) {
        AlertDialog(
            onDismissRequest = {
                showSettingsPrompt = false
                Log.d("EmpowerSWR", "Settings dialog dismissed")
            },
            title = { Text("Permission Required") },
            text = { Text("Location permission is required for check-in. Please enable it in app settings.") },
            confirmButton = {
                Button(
                    onClick = {
                        showSettingsPrompt = false
                        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        intent.data = Uri.fromParts("package", context.packageName, null)
                        settingsLauncher.launch(intent)
                        Log.d("EmpowerSWR", "Opening app settings for permission")
                    }
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        showSettingsPrompt = false
                        Log.d("EmpowerSWR", "Settings dialog cancelled")
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Force recomposition on workerDetails change
    LaunchedEffect(workerDetails) {
        Log.d("EmpowerSWR", "workerDetails changed: notices=${workerDetails?.notices}")
    }

    LaunchedEffect(token) {
        if (token == null) {
            Log.d("EmpowerSWR", "No token, redirecting to login")
        } else {
            viewModel.fetchWorkerDetails { error ->
                Log.e("EmpowerSWR", "fetchWorkerDetails error: ${error.message}")
            }
        }
    }

    LaunchedEffect(Unit) {
        Log.d("EmpowerSWR", "LaunchedEffect for FCM token retrieval started")
        delay(5000)
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val fcmToken = task.result
                Log.d("EmpowerSWR", "FCM Token: $fcmToken")
                PrefsHelper.saveFcmToken(context, fcmToken)
                val workerId = PrefsHelper.getWorkerId(context)
                if (workerId != null) {
                    viewModel.updateFcmToken(fcmToken, workerId)
                } else {
                    Log.w("EmpowerSWR", "No workerId available for FCM token update")
                }
            } else {
                fcmError = "Failed to get FCM token: ${task.exception?.message}"
                Log.e("EmpowerSWR", "FCM Token Error: ${task.exception?.message}")
            }
        }
    }

    LaunchedEffect(notifications) {
        notifications.forEach { notification ->
            Log.d("EmpowerSWR", "Showing Snackbar for notification: ${notification.title}: ${notification.body}")
            snackbarScope.launch {
                try {
                    val result = snackbarHostState.showSnackbar(
                        message = "${notification.title}: ${notification.body}",
                        actionLabel = "Dismiss",
                        duration = SnackbarDuration.Indefinite
                    )
                    Log.d("EmpowerSWR", "Snackbar shown with result: $result")
                    viewModel.removeNotification(notification)
                } catch (e: Exception) {
                    Log.e("EmpowerSWR", "Failed to show notification Snackbar: ${e.message}", e)
                }
            }
        }
    }

    LaunchedEffect(notificationFromIntent) {
        val (title, body) = notificationFromIntent
        if (title != null || body != null) {
            Log.d("EmpowerSWR", "Showing Snackbar for intent notification: $title: $body")
            snackbarScope.launch {
                try {
                    val result = snackbarHostState.showSnackbar(
                        message = "$title: $body",
                        actionLabel = "Dismiss",
                        duration = SnackbarDuration.Indefinite
                    )
                    Log.d("EmpowerSWR", "Snackbar shown with result: $result")
                    viewModel.setNotificationFromIntent(null, null)
                } catch (e: Exception) {
                    Log.e("EmpowerSWR", "Failed to show intent notification Snackbar: ${e.message}")
                }
            }
        } else {
            Log.d("EmpowerSWR", "No intent notification to display")
        }
    }

    LaunchedEffect(refreshError, locationError) {
        val error = refreshError ?: locationError
        error?.let {
            snackbarScope.launch {
                try {
                    val result = snackbarHostState.showSnackbar(
                        message = it,
                        actionLabel = "Dismiss",
                        duration = SnackbarDuration.Long
                    )
                    Log.d("EmpowerSWR", "Error Snackbar shown: $it, result: $result")
                    if (it == refreshError) refreshError = null
                    if (it == locationError) locationError = null
                } catch (e: Exception) {
                    Log.e("EmpowerSWR", "Failed to show error Snackbar: ${e.message}", e)
                }
            }
        }
    }

    LaunchedEffect(checkInSuccess) {
        if (checkInSuccess == true) {
            phone = ""
            isCheckingIn = false
            Log.d("EmpowerSWR", "Check-in successful, phone reset")
        }
    }

    LaunchedEffect(checkInError) {
        checkInError?.let { message ->
            snackbarScope.launch {
                try {
                    val result = snackbarHostState.showSnackbar(
                        message = message,
                        actionLabel = "Dismiss",
                        duration = SnackbarDuration.Long
                    )
                    Log.d("EmpowerSWR", "Check-in Snackbar shown: $message, result: $result")
                    showCheckInSection = false
                    viewModel.clearCheckInState()
                    isCheckingIn = false
                } catch (e: Exception) {
                    Log.e("EmpowerSWR", "Failed to show check-in Snackbar: ${e.message}", e)
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
                Log.d("EmpowerSWR", "Swipe-to-refresh triggered")
                coroutineScope.launch {
                    try {
                        isRefreshing = true
                        viewModel.fetchWorkerDetails {
                            refreshError = "Refresh failed: ${it.message}"
                            Log.e("EmpowerSWR", "Refresh error: ${it.message}")
                        }
                        delay(1000)
                    } catch (e: Exception) {
                        refreshError = "Refresh failed: ${e.message}"
                        Log.e("EmpowerSWR", "Refresh error: ${e.message}")
                    } finally {
                        isRefreshing = false
                        Log.d("EmpowerSWR", "Swipe-to-refresh completed")
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
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
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

                    Spacer(modifier = Modifier.height(16.dp))



                    Button(
                        onClick = {
                            Log.d("EmpowerSWR", "Find Me button pressed")
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
                        enabled = !isFindingMe
                    ) {
                        Text("Find Me")
                    }

                    workerDetails?.let { worker ->
                        if (worker.notices == "Locate" && showCheckInSection) {
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
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "IMPORTANT: Mifala traem faenem yu naoia. Plis calem Dan long 5552351 o Ofis long 34357 NAOIA.",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedTextField(
                                        value = phone,
                                        onValueChange = { newValue ->
                                            Log.d("EmpowerSWR", "Phone input changed: $newValue")
                                            if (newValue.matches(Regex("^\\d*$"))) {
                                                phone = newValue
                                            }
                                        },
                                        label = { Text("Phone Number") },
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Phone,
                                            imeAction = ImeAction.Done
                                        ),
                                        keyboardActions = KeyboardActions(
                                            onDone = {
                                                Log.d("EmpowerSWR", "Keyboard Done pressed, isPhoneSubmitValid=$isPhoneSubmitValid")
                                                if (isPhoneSubmitValid && !isCheckingIn) {
                                                    keyboardController?.hide()
                                                    coroutineScope.launch {
                                                        isCheckingIn = true
                                                        Log.d("EmpowerSWR", "Check In triggered via keyboard")
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
                                                    Log.d("EmpowerSWR", "Invalid phone number or check-in in progress")
                                                }
                                            }
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = TextFieldDefaults.colors(
                                            focusedIndicatorColor = MaterialTheme.colorScheme.error,
                                            unfocusedIndicatorColor = MaterialTheme.colorScheme.error
                                        ),
                                        enabled = isPhoneInputValid || phone.isEmpty()
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = {
                                            Log.d("EmpowerSWR", "Check In button pressed, isPhoneSubmitValid=$isPhoneSubmitValid")
                                            if (isPhoneSubmitValid && !isCheckingIn) {
                                                keyboardController?.hide()
                                                coroutineScope.launch {
                                                    isCheckingIn = true
                                                    Log.d("EmpowerSWR", "Check In triggered via button")
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
                                                Log.d("EmpowerSWR", "Invalid phone number or check-in in progress")
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error
                                        ),
                                        enabled = isPhoneSubmitValid && !isCheckingIn
                                    ) {
                                        Text("Check In", style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                        if (worker.notices == "App Checkin") {
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
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    Text(
                                        text = "You have checked in and the office is notified",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Please make:",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "• Police Clearance",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "• Medical (at Medical Options (Vila/Santo) or Medical Centre (Vila only))",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "• Letter from Chief or Pastor",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "• Letter from spouse (if you have one)",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "If you have a new passport, please upload in the documents screen.",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

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
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
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
        Log.d("EmpowerSWR", "Starting performCheckInAndSaveLocation with phone: $phone")
        viewModel.checkIn(phone)
        Log.d("EmpowerSWR", "checkIn called")

        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            Log.d("EmpowerSWR", "Location permission granted, requesting location")
            val location: Location? = fusedLocationClient.lastLocation.await()
            if (location != null) {
                val workerId = PrefsHelper.getWorkerId(context)
                if (workerId != null) {
                    Log.d("EmpowerSWR", "Location retrieved: lat=${location.latitude}, lon=${location.longitude}, workerId=$workerId")
                    viewModel.saveLocation(
                        workerId = workerId,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        action = "Check-In"
                    )
                    Log.d("EmpowerSWR", "saveLocation called with action=Check-In")
                } else {
                    onError("Worker ID not found")
                    Log.e("EmpowerSWR", "Worker ID not found")
                }
            } else {
                onError("Unable to get location")
                Log.e("EmpowerSWR", "Unable to get location")
            }
        } else {
            onError("Location permission not granted")
            Log.e("EmpowerSWR", "Location permission not granted")
        }
    } catch (e: Exception) {
        onError("Failed to process check-in or location: ${e.message}")
        Log.e("EmpowerSWR", "Check-in or location error: ${e.message}", e)
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
        Log.d("EmpowerSWR", "Starting performLocationUpdate with action: $action")
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            Log.d("EmpowerSWR", "Location permission granted, requesting location")
            val location: Location? = fusedLocationClient.lastLocation.await()
            if (location != null) {
                val workerId = PrefsHelper.getWorkerId(context)
                if (workerId != null) {
                    Log.d("EmpowerSWR", "Location retrieved: lat=${location.latitude}, lon=${location.longitude}, workerId=$workerId, action=$action")
                    viewModel.saveLocation(
                        workerId = workerId,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        action = action
                    )
                    Log.d("EmpowerSWR", "saveLocation called with action=$action")
                } else {
                    onError("Worker ID not found")
                    Log.e("EmpowerSWR", "Worker ID not found")
                }
            } else {
                onError("Unable to get location")
                Log.e("EmpowerSWR", "Unable to get location")
            }
        } else {
            onError("Location permission not granted")
            Log.e("EmpowerSWR", "Location permission not granted")
        }
    } catch (e: Exception) {
        onError("Failed to process location update: ${e.message}")
        Log.e("EmpowerSWR", "Location update error: ${e.message}", e)
    }
}