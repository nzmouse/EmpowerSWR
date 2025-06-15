package com.empowerswr.test.ui.screens

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.empowerswr.test.EmpowerViewModel
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Worker Details Screen
// Shows worker profile details and check-in functionality
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkerDetailsScreen(
    viewModel: EmpowerViewModel,
    context: Context,
    navController: NavHostController
) {
    Log.d("EmpowerSWR", "WorkerDetailsScreen composable called")
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var phone by rememberSaveable { mutableStateOf("") }
    var fcmError by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val token by viewModel.token

    val workerDetails by viewModel.workerDetails
    val alerts by viewModel.alerts
    val checkInSuccess by viewModel.checkInSuccess
    val checkInError by viewModel.checkInError
    val notifications by viewModel.notifications
    val notificationFromIntent by viewModel.notificationFromIntent

    LaunchedEffect(token) {
        if (token == null) {
            navController.navigate("login") {
                popUpTo(navController.graph.startDestinationId) { inclusive = true }
            }
        }
    }

    LaunchedEffect(Unit) {
        Log.d("EmpowerSWR", "LaunchedEffect for FCM token retrieval started")
        delay(5000)
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d("EmpowerSWR", "FCM Token: ${task.result}")
            } else {
                fcmError = "Failed to get FCM token: ${task.exception?.message}"
                Log.e("EmpowerSWR", "FCM Token Error: ${task.exception?.message}")
            }
        }
    }

    LaunchedEffect(alerts) {
        Log.d("EmpowerSWR", "Alerts updated: $alerts")
        alerts.forEach { alert ->
            Log.d("EmpowerSWR", "Showing Snackbar for alert: ${alert.message}")
            snackbarHostState.showSnackbar(alert.message)
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(scrollState)
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            workerDetails?.let { worker ->
                Text("Name: ${worker.givenName ?: "N/A"} ${worker.surname ?: "N/A"}")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Team: ${worker.teamName ?: "N/A"}")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Notices: ${worker.notices ?: "N/A"}")
                Spacer(modifier = Modifier.height(16.dp))

                if (worker.notices == "Locate") {
                    Text(
                        "IMPORTANT: Mifala traem faenem yu naoia. Plis calem Dan long 5552351 o Ofis long 34357 NAOIA.",
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { newValue -> phone = newValue },
                        label = { Text("Phone Number") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Phone,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                keyboardController?.hide()
                                coroutineScope.launch {
                                    viewModel.checkIn(phone)
                                }
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            keyboardController?.hide()
                            coroutineScope.launch {
                                viewModel.checkIn(phone)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Check In")
                    }
                    checkInSuccess?.let { success ->
                        Spacer(modifier = Modifier.height(8.dp))
                        if (success) {
                            Text("Check-in successful!", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    checkInError?.let { error ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(error, color = MaterialTheme.colorScheme.error)
                    }
                }
            } ?: run {
                Text("Loading worker details...")
            }
            fcmError?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text("FCM Error: $error", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}