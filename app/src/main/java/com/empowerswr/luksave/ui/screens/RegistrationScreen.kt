package com.empowerswr.luksave.ui.screens

<<<<<<< HEAD:app/src/main/java/com/empowerswr/test/ui/screens/RegistrationScreen.kt
import android.content.Context
import android.util.Log
=======
import android.content.Intent
import androidx.compose.foundation.clickable
>>>>>>> v1.0.0-release:app/src/main/java/com/empowerswr/luksave/ui/screens/RegistrationScreen.kt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
<<<<<<< HEAD:app/src/main/java/com/empowerswr/test/ui/screens/RegistrationScreen.kt
import androidx.navigation.NavHostController
import com.empowerswr.test.EmpowerViewModel
import com.empowerswr.test.PrefsHelper
=======
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.navigation.NavHostController
import com.empowerswr.luksave.EmpowerViewModel
import com.empowerswr.luksave.PrefsHelper
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.delay
>>>>>>> v1.0.0-release:app/src/main/java/com/empowerswr/luksave/ui/screens/RegistrationScreen.kt
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistrationScreen(
    viewModel: EmpowerViewModel,
    navController: NavHostController
) {
    val coroutineScope = rememberCoroutineScope()
<<<<<<< HEAD:app/src/main/java/com/empowerswr/test/ui/screens/RegistrationScreen.kt
    var passport by remember { mutableStateOf("") }
    var surname by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var registerError by remember { mutableStateOf<String?>(null) }
    val keyboardController = LocalSoftwareKeyboardController.current
=======
    val scrollState = rememberScrollState()
    var passport by rememberSaveable { mutableStateOf("") }
    var surname by rememberSaveable { mutableStateOf("") }
    var pin by rememberSaveable { mutableStateOf("") }
    var confirmPin by rememberSaveable { mutableStateOf("") }
    var fcmToken by remember { mutableStateOf<String?>(null) }
    var fcmError by remember { mutableStateOf<String?>(null) }
    var showWorkerIdDialog by rememberSaveable { mutableStateOf(false) }
    var registrationComplete by rememberSaveable { mutableStateOf(false) }
    var dialogDismissed by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val localContext = LocalContext.current
>>>>>>> v1.0.0-release:app/src/main/java/com/empowerswr/luksave/ui/screens/RegistrationScreen.kt

    LaunchedEffect(pin, confirmPin) {
        pinError = when {
            pin != confirmPin -> "PINs do not match"
            pin.length < 4 -> "PIN must be at least 4 digits"
            else -> null
        }
    }

<<<<<<< HEAD:app/src/main/java/com/empowerswr/test/ui/screens/RegistrationScreen.kt
    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "Register", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = passport,
            onValueChange = { passport = it },
            label = { Text("Passport Number") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = surname,
            onValueChange = { surname = it },
            label = { Text("Surname") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = { newValue -> pin = newValue.take(4) },
            label = { Text("PIN") },
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
                            try {
                                viewModel.register(passport, surname, pin)
                                PrefsHelper.setRegistered(context, true)
                                navController.navigate("login") {
                                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                                }
                            } catch (e: Exception) {
                                registerError = e.message
                                Log.e("EmpowerSWR", "Registration failed: ${e.message}", e)
                            }
                        }
                    }
=======
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

    // Handle registration completion
    LaunchedEffect(token) {
        if (token != null && !registrationComplete && !showWorkerIdDialog) {
            // Clear existing token to prevent navigation conflicts
            val existingToken = PrefsHelper.getToken(localContext)
            if (existingToken != null && existingToken != token) {
                Timber.i("Clearing existing token")
                PrefsHelper.clearToken(localContext)
            }
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val newFcmToken = task.result
                    fcmToken = newFcmToken
                    val workerId = PrefsHelper.getWorkerId(localContext)
                    if (workerId != null) {
                        Timber.i("FCM token retrieved")
                        try {
                            viewModel.updateFcmToken(newFcmToken, workerId)
                        } catch (e: Exception) {
                            fcmError = "Failed to update FCM token: ${e.message}"
                            Timber.tag("RegistrationScreen").e(e, "updateFcmToken error")
                        }
                    } else {
                        fcmError = "Worker ID not found for FCM token update"
                        Timber.tag("RegistrationScreen").e("Worker ID not found for FCM token update")
                    }
                } else {
                    fcmError = "Failed to get FCM token: ${task.exception?.message}"
                    Timber.tag("RegistrationScreen").e(task.exception?.message, "FCM token fetch error")
                }
            }
            viewModel.fetchWorkerDetails()
            viewModel.fetchAlerts()
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
                Timber.tag("RegistrationScreen").e(e,"Failed to show Snackbar")
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
                onValueChange = { newValue -> passport = newValue.trim().uppercase() },
                label = { Text("Passport Number (e.g., RV0127280)") },
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
                onValueChange = { newValue -> surname = newValue.trim().uppercase() },
                label = { Text("Surname") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                )
            )
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
                onValueChange = { newValue -> pin = newValue.take(4) },
                label = { Text("Choose a 4-Digit PIN") },
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
                                Timber.i("Attempting registration")
                                viewModel.register(passport, surname, pin)
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
                    if (pinError == null && pin.isNotEmpty()) {
                        coroutineScope.launch {
                            viewModel.register(passport, surname, pin)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = pinError == null && pin.isNotEmpty()
            ) {
                Text("Register")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Already registered? Log in with Worker ID",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    navController.navigate("login")
>>>>>>> v1.0.0-release:app/src/main/java/com/empowerswr/luksave/ui/screens/RegistrationScreen.kt
                }
            )
        )
        pinError?.let { error ->
            Spacer(modifier = Modifier.height(8.dp))
<<<<<<< HEAD:app/src/main/java/com/empowerswr/test/ui/screens/RegistrationScreen.kt
            Text(error, color = MaterialTheme.colorScheme.error)
        }
        registerError?.let { error ->
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
                        try {
                            viewModel.register(passport, surname, pin)
                            PrefsHelper.setRegistered(context, true)
                            navController.navigate("login") {
                                popUpTo(navController.graph.startDestinationId) { inclusive = true }
                            }
                        } catch (e: Exception) {
                            registerError = e.message
                            Log.e("EmpowerSWR", "Registration failed: ${e.message}", e)
                        }
                    }
=======
            Text(
                text = "Need help? Call 555-1234 or email support@empowerswr.com",
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.clickable {
                    val intent = Intent(Intent.ACTION_DIAL).apply {
                        data = "tel:5551234".toUri()
                    }
                    localContext.startActivity(intent)
>>>>>>> v1.0.0-release:app/src/main/java/com/empowerswr/luksave/ui/screens/RegistrationScreen.kt
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = pinError == null && pin.isNotEmpty()
        ) {
            Text("Register")
        }
    }
<<<<<<< HEAD:app/src/main/java/com/empowerswr/test/ui/screens/RegistrationScreen.kt
=======
}

// PIN Validation
private fun validatePin(pin: String, confirmPin: String): String? {
    return when {
        pin.length != 4 -> "PIN must be 4 digits"
        pin != confirmPin -> "PINs do not match"
        pin.all { it == pin[0] } -> "PIN cannot be all the same digit"
        else -> null
    }
>>>>>>> v1.0.0-release:app/src/main/java/com/empowerswr/luksave/ui/screens/RegistrationScreen.kt
}