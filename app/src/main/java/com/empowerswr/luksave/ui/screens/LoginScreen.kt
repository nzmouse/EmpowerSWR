package com.empowerswr.luksave.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.empowerswr.luksave.EmpowerViewModel
import com.empowerswr.luksave.PrefsHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import com.google.firebase.messaging.FirebaseMessaging

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: EmpowerViewModel,
    context: Context,
    navController: NavHostController,
    onLoginSuccess: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var workerIdOrUsername by rememberSaveable { mutableStateOf("") }
    var pin by rememberSaveable { mutableStateOf("") }
    var loginError by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val loginErrorState by viewModel.loginError
    var showSettingsPrompt by remember { mutableStateOf(false) }
    var hasInteracted by remember { mutableStateOf(false) }

    context.findEmpowerActivity() ?: run {
        throw IllegalStateException("LoginScreen must be called within a ComponentActivity")
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (!fineGranted && !coarseGranted) {
            showSettingsPrompt = true
            loginError = "Location permission denied. Please enable it in app settings."
        }
    }

    // Settings launcher
    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            showSettingsPrompt = true
            loginError = "Location permission still denied. Please enable it in settings."
            Timber.tag("LoginScreen").e("Location permission still denied after settings")
        }
    }
    // Log screen usage
    LaunchedEffect(Unit) {
        Timber.i("ScreenUsage: LoginScreen displayed, workerId=${PrefsHelper.getWorkerId(context) ?: "unknown"}, timestamp=${System.currentTimeMillis()}")
    }
    // Check permissions on start
    LaunchedEffect(Unit) {
        delay(500)
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    // Validate input only after interaction
    fun validateInput(): String? {
        return when {
            workerIdOrUsername.isEmpty() -> "Username cannot be empty"
            pin.length != 4 -> "PIN must be 4 digits"
            else -> null
        }
    }

    val inputError by remember(hasInteracted, workerIdOrUsername, pin) {
        derivedStateOf {
            if (hasInteracted) validateInput() else null
        }
    }

    // Handle login errors
    LaunchedEffect(loginErrorState) {
        loginErrorState?.let { message ->
            loginError = message
            coroutineScope.launch {
                try {
                    snackbarHostState.showSnackbar(
                        message = message,
                        actionLabel = "Dismiss",
                        duration = SnackbarDuration.Long
                    )
                    Timber.tag("LoginScreen").e("Login error Snackbar shown: Message= %s", message)
                    viewModel.clearCheckInState()
                } catch (e: Exception) {
                    Timber.tag("LoginScreen").e(e, "Failed to show login error Snackbar")
                }
            }
        }
    }

    // Navigate on successful login
    LaunchedEffect(viewModel.token.value) {
        if (viewModel.token.value != null) {
            val workerId = PrefsHelper.getWorkerId(context)
            if (workerId != null) {
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val fcmToken = task.result
                        coroutineScope.launch {
                            viewModel.updateFcmToken(fcmToken, context)
                        }
                    } else {
                        Timber.e("Failed to get FCM token: ${task.exception?.message}")
                    }
                }
            }
            onLoginSuccess()
            navController.navigate("home") {
                popUpTo("login") { inclusive = true }
            }
            Timber.d("LoginScreen: Navigating to HomeScreen")
        } else {
            Timber.d("LoginScreen: Navigation blocked, token=${viewModel.token.value}")
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Log in to EmpowerSWR",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Enter your Username (or worker ID) and 4-digit PIN",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = workerIdOrUsername,
                onValueChange = {
                    workerIdOrUsername = it.trim()
                    hasInteracted = true
                },
                label = { Text("Username") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { hasInteracted = true }
                ),
                isError = inputError != null,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = {
                    pin = it.take(4)
                    hasInteracted = true
                },
                label = { Text("4-Digit PIN") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                        hasInteracted = true
                        if (inputError == null) {
                            coroutineScope.launch {
                                viewModel.login(workerIdOrUsername, pin, context)
                            }
                        }
                    }
                ),
                visualTransformation = PasswordVisualTransformation(),
                isError = inputError != null,
                modifier = Modifier.fillMaxWidth()
            )
            inputError?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            loginError?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = it,
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
                    hasInteracted = true
                    if (inputError == null) {
                        coroutineScope.launch {
                            viewModel.login(workerIdOrUsername, pin, context)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = workerIdOrUsername.isNotEmpty() && pin.isNotEmpty()
            ) {
                Text("Log In")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Don't have an account? Register here",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    navController.navigate("registration")
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    coroutineScope.launch {
                        if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) != PackageManager.PERMISSION_GRANTED) {
                            permissionLauncher.launch(arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ))
                        } else {
                            try {
                                snackbarHostState.showSnackbar(
                                    message = "Location permission already granted",
                                    actionLabel = "Dismiss",
                                    duration = SnackbarDuration.Short
                                )
                                Timber.d("Location permission: Snackbar shown successfully")
                            } catch (e: Exception) {
                                Timber.e(e, "Location permission: Failed to show snackbar")
                                loginError = "Failed to show permission status"
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                enabled = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Text("Check Location Permission")
            }
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
}