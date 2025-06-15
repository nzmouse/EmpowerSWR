package com.empowerswr.test.ui.screens

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.empowerswr.test.EmpowerViewModel
import com.empowerswr.test.PrefsHelper
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Registration Screen
// Handles user registration with passport, surname, and PIN input, displays Worker ID dialog
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistrationScreen(
    viewModel: EmpowerViewModel,
    context: Context,
    navController: NavHostController
) {
    Log.d("EmpowerSWR", "RegistrationScreen composable called")
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var passport by rememberSaveable { mutableStateOf("") }
    var surname by rememberSaveable { mutableStateOf("") }
    var pin by rememberSaveable { mutableStateOf("") }
    var confirmPin by rememberSaveable { mutableStateOf("") }
    var fcmToken by remember { mutableStateOf<String?>(null) }
    var fcmError by remember { mutableStateOf<String?>(null) }
    var showWorkerIdDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current

    val token by viewModel.token
    val loginError by viewModel.loginError
    val alerts by viewModel.alerts
    val notifications by viewModel.notifications
    val notificationFromIntent by viewModel.notificationFromIntent

    // Validate PIN reactively
    val pinError by remember(pin, confirmPin) {
        derivedStateOf { validatePin(pin, confirmPin) }
    }

    // Show Worker ID dialog after registration
    if (showWorkerIdDialog) {
        AlertDialog(
            onDismissRequest = {}, // Non-dismissible without OK
            title = {
                Text(
                    "Registration Successful",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Your Worker ID is:",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        PrefsHelper.getWorkerId(context) ?: "Unknown",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = 36.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Please write this down and use it to log in with your PIN.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showWorkerIdDialog = false
                    viewModel.setToken(null) // Clear token to force login
                    navController.navigate("login") {
                        popUpTo("registration") { inclusive = true }
                    }
                }) {
                    Text("OK")
                }
            },
            dismissButton = null // No dismiss button
        )
    }

    LaunchedEffect(token) {
        if (token != null) {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val newFcmToken = task.result
                    fcmToken = newFcmToken
                    val workerId = PrefsHelper.getWorkerId(context) ?: return@addOnCompleteListener
                    viewModel.updateFcmToken(newFcmToken, workerId)
                } else {
                    fcmError = "Failed to get FCM token: ${task.exception?.message}"
                }
            }
            viewModel.fetchWorkerDetails()
            viewModel.fetchAlerts()
            PrefsHelper.setRegistered(context, true)
            showWorkerIdDialog = true
        }
    }

    LaunchedEffect(Unit) {
        Log.d("EmpowerSWR", "LaunchedEffect for FCM token retrieval started")
        delay(5000)
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                fcmToken = task.result
                Log.d("EmpowerSWR", "FCM Token: $fcmToken")
                PrefsHelper.saveFcmToken(context, fcmToken!!)
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Register with your Passport and Surname")
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = passport,
                onValueChange = { newValue -> passport = newValue },
                label = { Text("Passport Number") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = surname,
                onValueChange = { newValue -> surname = newValue },
                label = { Text("Surname") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = { newValue -> pin = newValue.take(4) },
                label = { Text("4-Digit PIN") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Next
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = confirmPin,
                onValueChange = { newValue -> confirmPin = newValue.take(4) },
                label = { Text("Re-enter PIN") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                        if (pinError == null && pin.isNotEmpty()) {
                            coroutineScope.launch {
                                Log.d("EmpowerSWR", "Keyboard Done: Attempting registration with passport: $passport, surname: $surname, pin: $pin")
                                viewModel.register(passport, surname, pin)
                            }
                        }
                    }
                )
            )
            pinError?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(error, color = MaterialTheme.colorScheme.error)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    Log.d("EmpowerSWR", "Register button clicked")
                    keyboardController?.hide()
                    if (pinError == null && pin.isNotEmpty()) {
                        coroutineScope.launch {
                            Log.d("EmpowerSWR", "Coroutine launched for registration")
                            viewModel.register(passport, surname, pin)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = pinError == null && pin.isNotEmpty()
            ) {
                Text("Register")
            }
            loginError?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(error, color = MaterialTheme.colorScheme.error)
            }
            fcmError?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text("FCM Error: $error", color = MaterialTheme.colorScheme.error)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Already registered? Log in with Worker ID",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    navController.navigate("login")
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Need help? Call 555-1234 or email support@empowerswr.com",
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.clickable {
                    val intent = Intent(Intent.ACTION_DIAL).apply {
                        data = android.net.Uri.parse("tel:5551234")
                    }
                    context.startActivity(intent)
                }
            )
        }
    }
}

// PIN Validation
// Validates PIN input for registration
private fun validatePin(pin: String, confirmPin: String): String? {
    return when {
        pin.length != 4 -> "PIN must be 4 digits"
        pin != confirmPin -> "PINs do not match"
        pin.all { it == pin[0] } -> "PIN cannot be all the same digit"
        else -> null
    }
}