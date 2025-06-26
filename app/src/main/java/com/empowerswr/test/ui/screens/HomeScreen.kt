package com.empowerswr.test.ui.screens

import android.content.Context
import android.util.Log
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
import com.empowerswr.test.EmpowerViewModel
import com.empowerswr.test.PrefsHelper
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: EmpowerViewModel,
    context: Context
) {
    Log.d("EmpowerSWR", "HomeScreen composable called")
    val coroutineScope = rememberCoroutineScope()
    var phone by rememberSaveable { mutableStateOf("") }
    var fcmError by remember { mutableStateOf<String?>(null) }
    var refreshError by remember { mutableStateOf<String?>(null) }
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

    // Force recomposition on workerDetails change
    LaunchedEffect(workerDetails) {
        Log.d("EmpowerSWR", "workerDetails changed: notices=${workerDetails?.notices}")
    }

    LaunchedEffect(token) {
        if (token == null) {
            Log.d("EmpowerSWR", "No token, redirecting to login")
            // Navigation handled in MainActivity or WorkerDetailsScreen
        } else {
            viewModel.fetchWorkerDetails { error ->
                Log.e("EmpowerSWR", "fetchWorkerDetails error: ${error?.message}")
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
            try {
                val result = snackbarHostState.showSnackbar(
                    message = "${notification.title}: ${notification.body}",
                    actionLabel = "Dismiss",
                    duration = SnackbarDuration.Indefinite
                )
                Log.d("EmpowerSWR", "Snackbar shown with result: $result")
                viewModel.removeNotification(notification)
            } catch (e: Exception) {
                Log.e("EmpowerSWR", "Failed to show Snackbar: ${e.message}", e)
            }
        }
    }

    LaunchedEffect(notificationFromIntent) {
        val (title, body) = notificationFromIntent
        if (title != null || body != null) {
            Log.d("EmpowerSWR", "Showing Snackbar for intent notification: $title: $body")
            try {
                val result = snackbarHostState.showSnackbar(
                    message = "$title: $body",
                    actionLabel = "Dismiss",
                    duration = SnackbarDuration.Indefinite
                )
                Log.d("EmpowerSWR", "Snackbar shown with result: $result")
                viewModel.setNotificationFromIntent(null, null)
            } catch (e: Exception) {
                Log.e("EmpowerSWR", "Failed to show Snackbar: ${e.message}", e)
            }
        } else {
            Log.d("EmpowerSWR", "No intent notification to display")
        }
    }

    LaunchedEffect(refreshError) {
        refreshError?.let { error ->
            try {
                val result = snackbarHostState.showSnackbar(
                    message = error,
                    actionLabel = "Dismiss",
                    duration = SnackbarDuration.Long
                )
                Log.d("EmpowerSWR", "Refresh error Snackbar shown: $error, result: $result")
                refreshError = null // Clear refresh error
            } catch (e: Exception) {
                Log.e("EmpowerSWR", "Failed to show refresh error Snackbar: ${e.message}")
            }
        }
    }

    // Handle check-in success
    LaunchedEffect(checkInSuccess) {
        if (checkInSuccess == true) {
            phone = "" // Reset phone input
            Log.d("EmpowerSWR", "Check-in successful, phone reset")
        }
    }

    // Handle check-in error or success message
    LaunchedEffect(checkInError) {
        checkInError?.let { message ->
            try {
                val result = snackbarHostState.showSnackbar(
                    message = message,
                    actionLabel = "Dismiss",
                    duration = SnackbarDuration.Long
                )
                Log.d("EmpowerSWR", "Check-in Snackbar shown: $message, result: $result")
                // Hide Check-In section and clear states after dismissal
                showCheckInSection = false
                viewModel.clearCheckInState()
            } catch (e: Exception) {
                Log.e("EmpowerSWR", "Failed to show check-in Snackbar: ${e.message}")
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
                        delay(1000) // Simulate network delay
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
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(
                                    "STATUS",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    worker.notices ?: "",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "Home Screen - Welcome!",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Check-In or App Checkin Section
                    workerDetails?.let { worker ->
                        // Check-In Section
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
                                        "IMPORTANT: Mifala traem faenem yu naoia. Plis calem Dan long 5552351 o Ofis long 34357 NAOIA.",
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
                                        label = { Text("Phone Number") },
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Phone,
                                            imeAction = ImeAction.Done
                                        ),
                                        keyboardActions = KeyboardActions(
                                            onDone = {
                                                if (isPhoneSubmitValid) {
                                                    keyboardController?.hide()
                                                    coroutineScope.launch {
                                                        viewModel.checkIn(phone)
                                                    }
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
                                            if (isPhoneSubmitValid) {
                                                keyboardController?.hide()
                                                coroutineScope.launch {
                                                    viewModel.checkIn(phone)
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error
                                        ),
                                        enabled = isPhoneSubmitValid
                                    ) {
                                        Text("Check In", style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                        // App Checkin Section
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
                                        "You have checked in and the office is notified",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "Please make:",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        "• Police Clearance",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        "• Medical (at Medical Options (Vila/Santo) or Medical Centre (Vila only))",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        "• Letter from Chief or Pastor",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        "• Letter from spouse (if you have one)",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "If you have a new passport, please upload in the documents screen.",
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
                                    "FCM Error: $error",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp)) // Ensure content extends for scrolling
                }
            }
        }
    }
}