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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.empowerswr.test.EmpowerViewModel
import com.empowerswr.test.PrefsHelper
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Login Screen
// Handles worker login with worker ID and PIN
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: EmpowerViewModel,
    context: Context,
    navController: NavHostController
) {
    Log.d("EmpowerSWR", "LoginScreen composable called")
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var workerId by rememberSaveable { mutableStateOf(PrefsHelper.getWorkerId(context) ?: "") }
    var pin by rememberSaveable { mutableStateOf("") }
    var fcmToken by remember { mutableStateOf<String?>(null) }
    var fcmError by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current

    val token by viewModel.token
    val loginError by viewModel.loginError
    val alerts by viewModel.alerts
    val notifications by viewModel.notifications
    val notificationFromIntent by viewModel.notificationFromIntent

    LaunchedEffect(token) {
        if (token != null) {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val newFcmToken = task.result
                    fcmToken = newFcmToken
                    val currentWorkerId = PrefsHelper.getWorkerId(context) ?: return@addOnCompleteListener
                    viewModel.updateFcmToken(newFcmToken, currentWorkerId)
                } else {
                    fcmError = "Failed to get FCM token: ${task.exception?.message}"
                }
            }
            viewModel.fetchWorkerDetails()
            viewModel.fetchAlerts()
            navController.navigate("profile") {
                popUpTo("login") { inclusive = true }
            }
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
            OutlinedTextField(
                value = workerId,
                onValueChange = { newValue -> workerId = newValue },
                label = { Text("Worker ID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = { newValue -> pin = newValue },
                label = { Text("PIN") },
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
                        coroutineScope.launch {
                            Log.d("EmpowerSWR", "Keyboard Done: Attempting login with workerId: $workerId, pin: $pin")
                            viewModel.login(workerId, pin)
                        }
                    }
                )
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    Log.d("EmpowerSWR", "Login button clicked")
                    keyboardController?.hide()
                    coroutineScope.launch {
                        Log.d("EmpowerSWR", "Coroutine launched for login")
                        viewModel.login(workerId, pin)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Login")
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
                text = "Not registered? Register with Passport/Surname",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    navController.navigate("registration")
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Forgot Worker ID or PIN? Call 555-1234 or email support@empowerswr.com",
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