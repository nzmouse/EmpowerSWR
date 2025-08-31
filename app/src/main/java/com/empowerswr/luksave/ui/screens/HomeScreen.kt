package com.empowerswr.luksave.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: EmpowerViewModel,
    context: Context,
    navController: NavController
) {
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
    val isPhoneInputValid by remember { derivedStateOf { phone.matches(Regex("^\\d*$")) } }
    val isPhoneSubmitValid by remember { derivedStateOf { phone.matches(Regex("^\\d{7,15}$")) } }
    var showCheckInSection by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showSettingsPrompt by remember { mutableStateOf(false) }
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    context.findEmpowerActivity() ?: run {
        throw IllegalStateException("HomeScreen must be called within a ComponentActivity")
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        Timber.i("Location Permission result: fineGranted= %b, coarseGranted= %b", fineGranted, coarseGranted)
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
            ) == PackageManager.PERMISSION_GRANTED
        ) {
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
            ) != PackageManager.PERMISSION_GRANTED
        ) {
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
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
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
        } else {
            viewModel.fetchWorkerDetails { error ->
                error?.message ?: "Failed to load worker details"
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(5000)
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val fcmToken = task.result
                PrefsHelper.saveFcmToken(context, fcmToken)
                val workerId = PrefsHelper.getWorkerId(context)
                if (workerId != null) {
                    viewModel.updateFcmToken(fcmToken, workerId)
                } else {
                    Timber.tag("HomeScreen").e("No workerId available for FCM token update")
                }
            } else {
                fcmError = "Failed to get FCM token: ${task.exception?.message}"
                Timber.tag("HomeScreen").e(task.exception, "FCM Token Error: %s", task.exception?.message)
            }
        }
    }

    // Handle notifications from ViewModel (likely from NotificationHandler)
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
        Timber.i("notificationFromIntent: $notificationFromIntent")
        notificationFromIntent?.let { (title, body) ->
            if (title != null && body != null && title.isNotBlank() && body.isNotBlank()) {
                snackbarScope.launch {
                    try {
                        Timber.i("Showing Snackbar for Title: $title, Body: $body")
                        snackbarHostState.showSnackbar(
                            message = "$title: $body",
                            actionLabel = "Dismiss",
                            duration = SnackbarDuration.Long // Auto-dismiss for testing
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

    LaunchedEffect(refreshError, locationError) {
        val error = refreshError ?: locationError
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                coroutineScope.launch {
                    try {
                        isRefreshing = true
                        viewModel.fetchWorkerDetails { error ->
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
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "IMPORTANT: Mifala traem faendem yu naoia. Kolem ofis long 34357 NAOIA.",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold
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
                                            unfocusedIndicatorColor = MaterialTheme.colorScheme.error
                                        ),
                                        enabled = isPhoneInputValid || phone.isEmpty()
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
                                            containerColor = MaterialTheme.colorScheme.error
                                        ),
                                        enabled = isPhoneSubmitValid && !isCheckingIn
                                    ) {
                                        Text("Check In", style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                        if (worker.notices == "App Checkin" || worker.notices == "App-Accepted" || worker.notices == "Notified" || worker.notices == "Reported In" || worker.notices == "Underway") {
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
                                    if (worker.notices == "App Checkin") {
                                        Button(
                                            onClick = {
                                                navController.navigate("team")
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary
                                            )
                                        ) {
                                            Text(
                                                "View and Accept your New Job!",
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = {
                                            navController.navigate("team")
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        Text("View and Accept your New Job!", style = MaterialTheme.typography.labelLarge)
                                    }
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
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
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
        viewModel.checkIn(phone)
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val location: Location? = fusedLocationClient.lastLocation.await()
            if (location != null) {
                val workerId = PrefsHelper.getWorkerId(context)
                if (workerId != null) {
                    viewModel.saveLocation(
                        workerId = workerId,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        action = "Check-In"
                    )
                } else {
                    onError("Worker ID not found")
                    Timber.tag("HomeScreen").e("Worker ID not found")
                }
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
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val location: Location? = fusedLocationClient.lastLocation.await()
            if (location != null) {
                val workerId = PrefsHelper.getWorkerId(context)
                if (workerId != null) {
                    viewModel.saveLocation(
                        workerId = workerId,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        action = action
                    )
                } else {
                    onError("Worker ID not found")
                    Timber.tag("HomeScreen").e("Worker ID not found")
                }
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