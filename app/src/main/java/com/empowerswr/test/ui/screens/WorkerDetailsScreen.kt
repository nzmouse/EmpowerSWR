package com.empowerswr.test.ui.screens

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.empowerswr.test.Alert
import com.empowerswr.test.EmpowerViewModel
import com.empowerswr.test.PrefsHelper
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// Utility function to format dates to dd-MMM-yyyy
private fun formatDate(dateString: String?): String {
    if (dateString.isNullOrEmpty()) return "N/A"
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val outputFormat = SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault())
        val date = inputFormat.parse(dateString)
        date?.let { outputFormat.format(it) } ?: "N/A"
    } catch (e: Exception) {
        Log.e("EmpowerSWR", "Date format error for $dateString: ${e.message}")
        "N/A"
    }
}

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
    var workerError by remember { mutableStateOf<String?>(null) }
    var historyError by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val token by viewModel.token
    val workerDetails by viewModel.workerDetails
    val history by viewModel.history
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
        } else {
            viewModel.fetchWorkerDetails { error ->
                workerError = error?.message ?: "Failed to load worker details"
            }
            viewModel.fetchHistory { error ->
                historyError = error?.message ?: "Failed to load history"
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

    LaunchedEffect(alerts) {
        Log.d("EmpowerSWR", "Alerts updated: $alerts")
        alerts.forEach { alert: Alert ->
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
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState)
                .imePadding(),
            horizontalAlignment = Alignment.Start
        ) {
            workerDetails?.let { worker ->
                // Personal Information
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            "Personal Information",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Worker ID: ${worker.ID ?: "N/A"}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "First Name: ${worker.firstName ?: "N/A"}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "Surname: ${worker.surname ?: "N/A"}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "Preferred Name: ${worker.prefName ?: "N/A"}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "Date of Birth: ${formatDate(worker.dob)}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "Team: ${worker.teamName ?: "N/A"}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                // Home Address
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            "Home Address",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Village: ${worker.homeVillage ?: "N/A"}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "Island: ${worker.homeIsland ?: "N/A"}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                // Residential Address
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            "Residential Address",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Address: ${worker.residentialAddress ?: "N/A"}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "Island: ${worker.residentialIsland ?: "N/A"}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                // Contact Information
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            "Contact Information",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Primary Phone: ${worker.phone ?: "N/A"}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "Secondary Phone: ${worker.phone2 ?: "N/A"}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "AU/NZ Phone: ${worker.aunzPhone ?: "N/A"}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "Email: ${worker.email ?: "N/A"}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                // Driver's License
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            "Driver's License",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        if (!worker.dLicence.isNullOrEmpty()) {
                            Text(
                                "License Number: ${worker.dLicence}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                "License Class: ${worker.dLClass ?: "N/A"}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                "License Expiry: ${formatDate(worker.dLicenceExp)}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        } else {
                            Text(
                                "No Licence",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Status
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            "Status",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            worker.notices ?: "N/A",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                // Passport Details
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            "Passport Details",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "First Name: ${worker.firstName ?: "N/A"}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "Surname: ${worker.surname ?: "N/A"}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "Passport Number: ${worker.ppno ?: "N/A"}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "Birth Place: ${worker.birthplace ?: "N/A"}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "Date Issued: ${formatDate(worker.ppissued)}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "Date Expiry: ${formatDate(worker.ppexpiry)}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // History
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            "Work History",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        if (history.isNotEmpty()) {
                            history.forEach { record ->
                                Column(
                                    modifier = Modifier.padding(bottom = 8.dp)
                                ) {
                                    Text(
                                        "Team: ${record.team ?: "N/A"}",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        "Employer: ${record.employer ?: "N/A"}",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        "Country: ${record.country ?: "N/A"}",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        "From: ${formatDate(record.dateFrom)}",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        "To: ${formatDate(record.dateTo)}",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                                if (history.last() != record) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                    )
                                }
                            }
                        } else if (historyError != null) {
                            Text(
                                "Failed to load history: $historyError",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Text(
                                "No History Available",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Check-In Section
                if (worker.notices == "Locate") {
                    Spacer(modifier = Modifier.height(16.dp))
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
                                modifier = Modifier.fillMaxWidth(),
                                colors = TextFieldDefaults.colors(
                                    focusedIndicatorColor = MaterialTheme.colorScheme.error,
                                    unfocusedIndicatorColor = MaterialTheme.colorScheme.error
                                )
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    keyboardController?.hide()
                                    coroutineScope.launch {
                                        viewModel.checkIn(phone)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("Check In", style = MaterialTheme.typography.labelLarge)
                            }
                            checkInSuccess?.let { success ->
                                Spacer(modifier = Modifier.height(12.dp))
                                if (success) {
                                    Text(
                                        "Check-in successful!",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            checkInError?.let { error ->
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    error,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            } ?: run {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            workerError ?: "Loading worker details...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
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
                            "FCM Error: $error",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}