package com.empowerswr.luksave.ui.screens

import android.content.Intent
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.navigation.NavHostController
import com.empowerswr.luksave.EmpowerViewModel
import com.empowerswr.luksave.PrefsHelper
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistrationScreen(
    viewModel: EmpowerViewModel,
    navController: NavHostController
) {
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var passport by rememberSaveable { mutableStateOf("") }
    var surname by rememberSaveable { mutableStateOf("") }
    var pin by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var confirmPin by rememberSaveable { mutableStateOf("") }
    var fcmToken by remember { mutableStateOf<String?>(null) }
    var fcmError by remember { mutableStateOf<String?>(null) }
    var showWorkerIdDialog by rememberSaveable { mutableStateOf(false) }
    var registrationComplete by rememberSaveable { mutableStateOf(false) }
    var dialogDismissed by rememberSaveable { mutableStateOf(false) }
    var hasInteracted by remember { mutableStateOf(false) } // Track user interaction
    val snackbarHostState = remember { SnackbarHostState() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val localContext = LocalContext.current

    val token by viewModel.token
    val loginError by viewModel.loginError
    val alerts by viewModel.alerts
    val notifications by viewModel.notifications
    val notificationFromIntent by viewModel.notificationFromIntent

    // Validate PIN and username only after interaction
    fun validatePin(pin: String, confirmPin: String): String? {
        return when {
            pin.length != 4 -> "PIN must be 4 digits"
            pin != confirmPin -> "PINs do not match"
            pin.all { it == pin[0] } -> "PIN cannot be all the same digit"
            else -> null
        }
    }

    fun validateUsername(username: String): String? {
        return when {
            username.isEmpty() -> "Username cannot be empty"
            username.length < 3 -> "Username must be at least 3 characters"
            username.length > 20 -> "Username cannot exceed 20 characters"
            !username.matches(Regex("^[a-zA-Z0-9]+$")) -> "Username can only contain letters and numbers"
            else -> null
        }
    }

    val pinError by remember(hasInteracted, pin, confirmPin) {
        derivedStateOf {
            if (hasInteracted) validatePin(pin, confirmPin) else null
        }
    }
    val usernameError by remember(hasInteracted, username) {
        derivedStateOf {
            if (hasInteracted) validateUsername(username) else null
        }
    }

    // Show Worker ID dialog after registration
    if (showWorkerIdDialog) {
        Dialog(
            onDismissRequest = {
                // Prevent dismissal without OK
            },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Registration Successful",
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Your Worker ID is:",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = PrefsHelper.getWorkerId(localContext) ?: "Unknown",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Please write this down and use it to log in with your PIN.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            showWorkerIdDialog = false
                            dialogDismissed = true
                            navController.navigate("login") {
                                popUpTo("registration") { inclusive = true }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(0.5f)
                    ) {
                        Text("OK")
                    }
                }
            }
        }
    }
    // Log screen usage
    LaunchedEffect(Unit) {
        Timber.i("ScreenUsage: RegistrationScreen displayed, timestamp=${System.currentTimeMillis()}")
    }
    // Handle registration completion
    LaunchedEffect(viewModel.token.value) {
        if (viewModel.token.value != null && !registrationComplete && !showWorkerIdDialog) {
            val existingToken = PrefsHelper.getToken(localContext)
            if (existingToken != null && existingToken != viewModel.token.value) {
                Timber.i("Clearing existing token")
                PrefsHelper.clearToken(localContext)
            }
            val workerId = PrefsHelper.getWorkerId(localContext)
            if (workerId != null) {
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        fcmToken = task.result
                        coroutineScope.launch {
                            viewModel.updateFcmToken(fcmToken!!, localContext)
                        }
                    } else {
                        fcmError = "Failed to get FCM token: ${task.exception?.message}"
                        Timber.e("FCM token fetch error: ${task.exception?.message}")
                    }
                }
            } else {
                fcmError = "Worker ID not found for FCM token update"
                Timber.e("Worker ID not found for FCM token update")
            }
            viewModel.fetchWorkerDetails(localContext)
            viewModel.fetchAlerts(localContext)
            PrefsHelper.setRegistered(localContext, true)
            registrationComplete = true
            showWorkerIdDialog = true
        }
    }

    // Prevent premature navigation
    LaunchedEffect(dialogDismissed) {
        if (dialogDismissed) {
            // Navigation is handled in the dialog's OK button
        } else if (token != null && registrationComplete && !showWorkerIdDialog) {
        }
    }

    LaunchedEffect(Unit) {
        delay(5000)
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                fcmToken = task.result
                Timber.i("Initial FCM Token retrieved")
                PrefsHelper.saveFcmToken(localContext, fcmToken!!)
            } else {
                fcmError = "Failed to get initial FCM token: ${task.exception?.message}"
                Timber.tag("RegistrationScreen").e(task.exception?.message, "Initial FCM Token error")
            }
        }
    }

    LaunchedEffect(alerts) {
        Timber.i("Alerts updated")
        alerts.forEach { alert ->
            snackbarHostState.showSnackbar(alert.message)
        }
    }

    LaunchedEffect(notifications) {
        notifications.forEach { notification ->
            try {
                val result = snackbarHostState.showSnackbar(
                    message = "${notification.title}: ${notification.body}",
                    actionLabel = "Dismiss",
                    duration = SnackbarDuration.Indefinite
                )
                viewModel.removeNotification(notification)
            } catch (e: Exception) {
                Timber.tag("RegistrationScreen").e(e, "Failed to show Snackbar")
            }
        }
    }

    LaunchedEffect(notificationFromIntent) {
        val (title, body) = notificationFromIntent
        if (title != null || body != null) {
            try {
                val result = snackbarHostState.showSnackbar(
                    message = "$title: $body",
                    actionLabel = "Dismiss",
                    duration = SnackbarDuration.Indefinite
                )
                viewModel.setNotificationFromIntent(null, null)
            } catch (e: Exception) {
                Timber.tag("RegistrationScreen").e(e, "Failed to show Snackbar")
            }
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
            Text(
                text = "Register with your Passport and Surname",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Enter your passport number (e.g., RV0127280) and surname as registered",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = passport,
                onValueChange = {
                    passport = it.trim().uppercase()
                    hasInteracted = true // Set on interaction
                },
                label = { Text("Passport Number (e.g., RV0127280)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { hasInteracted = true } // Set on focus change
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = surname,
                onValueChange = {
                    surname = it.trim().uppercase()
                    hasInteracted = true // Set on interaction
                },
                label = { Text("Surname") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { hasInteracted = true } // Set on focus change
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = username,
                onValueChange = {
                    username = it.trim()
                    hasInteracted = true // Set on interaction
                },
                label = { Text("Username / Nickname") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { hasInteracted = true } // Set on focus change
                ),
                isError = usernameError != null
            )
            usernameError?.let { error ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            loginError?.let { error ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = {
                    pin = it.take(4)
                    hasInteracted = true // Set on interaction
                },
                label = { Text("Choose a 4-Digit PIN") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { hasInteracted = true } // Set on focus change
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = confirmPin,
                onValueChange = {
                    confirmPin = it.take(4)
                    hasInteracted = true // Set on interaction
                },
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
                        hasInteracted = true // Set on submit
                        if (pinError == null && usernameError == null && pin.isNotEmpty() && username.isNotEmpty()) {
                            coroutineScope.launch {
                                viewModel.register(passport, surname, username, pin, localContext)
                            }
                        }
                    }
                )
            )
            pinError?.let { error ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            fcmError?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "FCM Error: $error",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    keyboardController?.hide()
                    hasInteracted = true // Set on button click
                    if (pinError == null && usernameError == null && pin.isNotEmpty() && username.isNotEmpty()) {
                        coroutineScope.launch {
                            viewModel.register(passport, surname, username, pin, localContext)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = passport.isNotEmpty() && surname.isNotEmpty() && username.isNotEmpty() && pin.isNotEmpty()
            ) {
                Text("Register")
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
                        data = "tel:5551234".toUri()
                    }
                    localContext.startActivity(intent)
                }
            )
        }
    }
}