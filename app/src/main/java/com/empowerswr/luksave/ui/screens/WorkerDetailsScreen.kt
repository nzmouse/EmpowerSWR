package com.empowerswr.luksave.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.empowerswr.luksave.EmpowerViewModel
import com.empowerswr.luksave.PrefsHelper
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.text.font.FontStyle
import kotlinx.coroutines.Dispatchers
import timber.log.Timber

fun formatDate(dateString: String?): String {
    if (dateString.isNullOrEmpty()) return "N/A"
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val outputFormat = SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault())
        val date = inputFormat.parse(dateString)
        date?.let { outputFormat.format(it) } ?: "N/A"
    } catch (_: Exception) {
        Timber.tag("WorkerDetailsScreen").e("Date format error for, %s", dateString)
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
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var fcmError by remember { mutableStateOf<String?>(null) }
    var workerError by remember { mutableStateOf<String?>(null) }
    var historyError by remember { mutableStateOf<String?>(null) }
    val snackbarScope = rememberCoroutineScope { Dispatchers.Main }
    val snackbarHostState = remember { SnackbarHostState() }
    val token by viewModel.token
    val workerDetails by viewModel.workerDetails
    val history by viewModel.history
    val alerts by viewModel.alerts
    val notifications by viewModel.notifications
    val notificationFromIntent by viewModel.notificationFromIntent
    val pendingFields by viewModel.pendingFields

    var isRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(token) {
        if (token == null) {
            navController.navigate("login") {
                popUpTo(navController.graph.startDestinationId) { inclusive = true }
            }
        } else if (workerDetails == null) {  // Fetch only if data is missing (initial or reset)
            viewModel.fetchWorkerDetails { error ->
                if (error != null) {
                    coroutineScope.launch { snackbarHostState.showSnackbar("Failed to load worker details: ${error.message}") }
                }
            }
            viewModel.fetchHistory { error ->
                historyError = error.message ?: "Failed to load history"
            }
        }
    }

    // Refresh signal from edit screens (with delay for backend commit)
    LaunchedEffect(Unit) {
        navController.currentBackStackEntry?.savedStateHandle?.getLiveData<Boolean>("refresh_profile")
            ?.observe(navController.currentBackStackEntry!!) { refresh ->
                if (refresh) {
                    coroutineScope.launch {
                        delay(2000)  // Wait for backend commit
                        viewModel.fetchWorkerDetails { error ->
                            workerError = error?.message ?: "Failed to load worker details"
                        }
                    }
                    navController.currentBackStackEntry?.savedStateHandle?.remove<Boolean>("refresh_profile")
                }
            }
    }

    LaunchedEffect(Unit) {
        delay(5000)
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val fcmToken = task.result
                Timber.i("FCM Token loaded")
                PrefsHelper.saveFcmToken(context, fcmToken)
                val workerId = PrefsHelper.getWorkerId(context)
                if (workerId != null) {
                    viewModel.updateFcmToken(fcmToken, workerId)
                } else {
                    Timber.i("No workerId available for FCM token update")
                }
            } else {
                fcmError = "Failed to get FCM token: ${task.exception?.message}"
                Timber.tag("WorkerDetailsScreen").e(task.exception?.message, "FCM Token Error")
            }
        }
    }

    LaunchedEffect(alerts) {
        alerts.forEach { alert ->
            snackbarScope.launch {
                try {
                    snackbarHostState.showSnackbar(
                        message = alert.message,
                        actionLabel = "Dismiss",
                        duration = SnackbarDuration.Long
                    )
                    viewModel.removeAlert(alert)
                    Timber.tag("WorkerDetailsScreen").i("Displayed alert: %s", alert.message)
                } catch (e: Exception) {
                    Timber.tag("WorkerDetailsScreen").e(e, "Failed to show alert Snackbar")
                }
            }
        }
    }

    LaunchedEffect(notifications) {
        notifications.forEach { notification ->

            coroutineScope.launch {
                try {
                    val result = snackbarHostState.showSnackbar(
                        message = "${notification.title}: ${notification.body}",
                        actionLabel = "Dismiss",
                        duration = SnackbarDuration.Indefinite
                    )
                    viewModel.removeNotification(notification)
                } catch (e: Exception) {
                    Timber.tag("WorkerDetailsScreen").e(e, "Failed to show Snackbar")
                }
            }
        }
    }

    LaunchedEffect(notificationFromIntent) {
        val (title, body) = notificationFromIntent
        if (title != null || body != null) {
            coroutineScope.launch {
                try {
                    val result = snackbarHostState.showSnackbar(
                        message = "$title: $body",
                        actionLabel = "Dismiss",
                        duration = SnackbarDuration.Indefinite
                    )
                    viewModel.setNotificationFromIntent(null, null)
                } catch (e: Exception) {
                    Timber.tag("WorkerDetailsScreen").e(e, "Failed to show Snackbar")
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
                            Timber.tag("WorkerDetailsScreen").e("Refresh error")
                            isRefreshing = false  // Ensure reset after callback
                        }
                        delay(1000)
                    } catch (e: Exception) {
                        Timber.tag("WorkerDetailsScreen").e(e, "Refresh error")
                    } finally {
                        isRefreshing = false
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(scrollState)
                    .imePadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Profael blong yu",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                workerDetails?.let { worker ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Personal Information",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (pendingFields.contains("prefName")) {
                                        Icon(
                                            Icons.Default.Pending,
                                            "Pending Review",
                                            tint = Color.Yellow
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            if (!pendingFields.contains("prefName")) navController.navigate(
                                                "edit_personal"
                                            )
                                        },
                                        enabled = !pendingFields.contains("prefName")
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            "Edit",
                                            tint = if (pendingFields.contains("prefName")) Color.Gray else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Worker ID: ${worker.ID ?: "N/A"}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "First Name: ${worker.firstName ?: "N/A"}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Surname: ${worker.surname ?: "N/A"}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Preferred Name:",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = when {
                                        !worker.prefName.isNullOrEmpty() -> worker.prefName
                                        else -> "Plis edit nem"
                                    },
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontStyle = if (worker.prefName.isNullOrEmpty()) FontStyle.Italic else FontStyle.Normal
                                    ),
                                    color = if (worker.prefName.isNullOrEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = "Date of Birth: ${formatDate(worker.dob)}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Team: ${worker.teamName ?: "N/A"}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }

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
                                text = "Home Address",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Village: ${worker.homeVillage ?: "N/A"}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Island: ${worker.homeIsland ?: "N/A"}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }

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
                                text = "Residential Address",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Address: ${worker.residentialAddress ?: "N/A"}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Island: ${worker.residentialIsland ?: "N/A"}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Contact Information",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (pendingFields.contains("contacts")) {
                                        Icon(
                                            Icons.Default.Pending,
                                            "Pending Review",
                                            tint = Color.Yellow
                                        )
                                    }
                                    IconButton(
                                        onClick = { if (!pendingFields.contains("contacts")) navController.navigate("edit_contact") },
                                        enabled = !pendingFields.contains("contacts")
                                    ) {
                                        Icon(Icons.Default.Edit, "Edit", tint = if (pendingFields.contains("contacts")) Color.Gray else MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Primary Phone: ${worker.phone ?: "N/A"}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Secondary Phone: ${worker.phone2 ?: "N/A"}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "AU/NZ Phone: ${worker.aunzPhone ?: "N/A"}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Email: ${worker.email ?: "N/A"}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }

                    // Drivers Licence Card
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
                                text = "Driver's License",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            if (!worker.dLicence.isNullOrEmpty()) {
                                Text(
                                    text = "License Number: ${worker.dLicence}",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "License Class: ${worker.dLClass ?: "N/A"}",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "License Expiry: ${formatDate(worker.dLicenceExp)}",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            } else {
                                Text(
                                    text = "No Licence",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

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
                                text = "Status",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = worker.notices ?: "N/A",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }

                    //Passport Details Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Passport Details",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(
                                    onClick = { navController.navigate("edit_passport") }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Passport Details",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "First Name: ${worker.firstName ?: "N/A"}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Surname: ${worker.surname ?: "N/A"}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Passport Number: ${worker.ppno ?: "N/A"}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Birth Place: ${worker.birthplace ?: "N/A"}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Date Issued: ${formatDate(worker.ppissued)}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Date Expiry: ${formatDate(worker.ppexpiry)}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

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
                                text = "Work History",
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
                                            text = "Team: ${record.team ?: "N/A"}",
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Text(
                                            text = "Employer: ${record.employer ?: "N/A"}",
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Text(
                                            text = "Country: ${record.country ?: "N/A"}",
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Text(
                                            text = "From: ${formatDate(record.dateFrom)}",
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Text(
                                            text = "To: ${formatDate(record.dateTo)}",
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
                                    text = "Failed to load history: $historyError",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                Text(
                                    text = "No History Available",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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
                                text = workerError ?: "Loading worker details...",
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