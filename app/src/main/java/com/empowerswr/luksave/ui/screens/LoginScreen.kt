package com.empowerswr.luksave.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
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
import androidx.core.content.edit
import androidx.navigation.NavHostController
import com.empowerswr.luksave.EmpowerViewModel
import com.empowerswr.luksave.PrefsHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.google.firebase.messaging.FirebaseMessaging

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: EmpowerViewModel,
    context: Context,
    navController: NavHostController,
    onLoginSuccess: () -> Unit
) {
    // =================================================================
    // STATE VARIABLES
    // =================================================================
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var workerIdOrUsername by rememberSaveable { mutableStateOf("") }
    var pin by rememberSaveable { mutableStateOf("") }
    var loginError by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val loginErrorState by viewModel.loginError
    var showSettingsPrompt by remember { mutableStateOf(false) }

    // New User Detection State
    var isNewUser by remember { mutableStateOf(false) }
    var showHint by remember { mutableStateOf(false) }
    var hintStep by remember { mutableStateOf(0) }
    var showAnimatedHint by remember { mutableStateOf(false) }
    var permissionsHandled by remember { mutableStateOf(false) }

    context.findEmpowerActivity() ?: run {
        throw IllegalStateException("LoginScreen must be called within a ComponentActivity")
    }

    // =================================================================
    // NEW USER DETECTION
    // =================================================================
    fun detectNewUser(context: Context): Boolean {
        val prefs = context.getSharedPreferences("empower_prefs", Context.MODE_PRIVATE)
        return when {
            !prefs.getBoolean("has_logged_in", false) &&
                    prefs.getString("last_username", null).isNullOrEmpty() &&
                    prefs.getBoolean("has_registered", false) == false -> {
                if (prefs.getLong("install_timestamp", 0L) == 0L) {
                    prefs.edit {
                        putLong("install_timestamp", System.currentTimeMillis())
                    }
                }
                true
            }
            else -> false
        }
    }

    // =================================================================
    // PERMISSION LAUNCHERS
    // =================================================================
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (!fineGranted && !coarseGranted) {
            showSettingsPrompt = true
            loginError = "Location permission denied. Please enable it in app settings."
        }
        permissionsHandled = true
    }

    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            showSettingsPrompt = true
            loginError = "Location permission still denied. Please enable it in settings."
        }
        permissionsHandled = true
    }

    LaunchedEffect(Unit) {
        delay(200)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        } else {
            permissionsHandled = true
        }
    }

    LaunchedEffect(permissionsHandled) {
        if (permissionsHandled) {
            delay(100)
            isNewUser = detectNewUser(context)
            if (isNewUser) {
                showHint = true
                showAnimatedHint = true
                coroutineScope.launch {
                    delay(3000)
                    hintStep = 1
                    delay(5000)
                    hintStep = 2
                }
            }
        }
    }

    // =================================================================
    // VALIDATION & ERROR HANDLING
    // =================================================================
    fun handleLoginFail(username: String) {
        val prefs = context.getSharedPreferences("empower_prefs", Context.MODE_PRIVATE)
        if (isNewUser && username.isNotEmpty()) {
            prefs.edit { putString("attempted_username", username) }
            hintStep = 2
            loginError = "Account not found. Make sure you have registered first or check username"
        } else {
            loginError = "Invalid username or PIN"
        }
    }

    fun validateInput(): String? = when {
        workerIdOrUsername.isEmpty() -> "Username cannot be empty"
        pin.length != 4 -> "PIN must be 4 digits"
        else -> null
    }

    var inputError by remember { mutableStateOf<String?>(null) }

    fun onLoginClick() {
        inputError = validateInput()
        if (inputError == null) {
            coroutineScope.launch {
                viewModel.login(workerIdOrUsername, pin, context)
            }
        }
    }

    // =================================================================
    // SIDE EFFECTS
    // =================================================================
    LaunchedEffect(loginErrorState) {
        loginErrorState?.let { message ->
            loginError = message
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    message = message,
                    actionLabel = "Dismiss",
                    duration = SnackbarDuration.Long
                )
                viewModel.clearCheckInState()
            }
        }
    }

    LaunchedEffect(viewModel.token.value) {
        if (viewModel.token.value != null) {
            val prefs = context.getSharedPreferences("empower_prefs", Context.MODE_PRIVATE)
            prefs.edit {
                putBoolean("has_logged_in", true)
                putBoolean("has_registered", true)
                putString("last_username", workerIdOrUsername)
            }
            val workerId = PrefsHelper.getWorkerId(context)
            if (workerId != null) {
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        coroutineScope.launch {
                            viewModel.updateFcmToken(task.result, context)
                        }
                    }
                }
            }
            onLoginSuccess()
            navController.navigate("home") { popUpTo("login") { inclusive = true } }
        }
    }

    // =================================================================
    // MAIN UI
    // =================================================================
    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
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
            // Animated Welcome Banner
            AnimatedVisibility(
                visible = showAnimatedHint,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = if (hintStep < 2) Icons.Default.Star else Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = when (hintStep) {
                                0 -> "Welcome to EmpowerSWR!"
                                1 -> "You need to register if you have not used Luksave before"
                                2 -> "To register, click the 'Register Now' Button"
                                else -> ""
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when (hintStep) {
                                0 -> "Get started.  Register in 2 minutes"
                                1 -> "Call us if unsure"
                                2 -> "Click 'Register Now' button to register"
                                else -> ""
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Title
            Text(
                text = "Log in to EmpowerSWR",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Contextual Subtitle
            Text(
                text = when {
                    isNewUser && hintStep == 0 -> "First time? We'll guide you!"
                    isNewUser && hintStep == 2 -> "Step 2: Register (2 minutes)"
                    else -> "Enter your Username and 4-digit PIN"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Input Fields
            OutlinedTextField(
                value = workerIdOrUsername,
                onValueChange = { workerIdOrUsername = it.trim() },
                label = {
                    Text(if (isNewUser && hintStep == 1) "Worker ID (from badge)" else "Username")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { /* Focus to PIN */ }),
                isError = inputError != null,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it.take(4) },
                label = { Text("4-Digit PIN") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                        onLoginClick()
                    }
                ),
                visualTransformation = PasswordVisualTransformation(),
                isError = inputError != null,
                modifier = Modifier.fillMaxWidth()
            )

            // Error Messages
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

            // Login Button
            Button(
                onClick = {
                    keyboardController?.hide()
                    onLoginClick()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = workerIdOrUsername.isNotEmpty() && pin.isNotEmpty()
            ) {
                Text("Log In")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // SINGLE Register Button - Dynamic Colors
            val prefs = context.getSharedPreferences("empower_prefs", Context.MODE_PRIVATE)
            val hasRegistered = prefs.getBoolean("has_registered", false)
            val isRegistrationRequired = isNewUser || !hasRegistered
            Button(
                onClick = { navController.navigate("registration") },
                colors = if (isRegistrationRequired) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.inversePrimary)
                } else {
                    ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isNewUser) "Register here →" else "Register here",
                    color = if (isRegistrationRequired) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Permission Button
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
                            snackbarHostState.showSnackbar(
                                message = "Location permission already granted",
                                actionLabel = "Dismiss",
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Text("Check Location Permission")
            }
        }
    }

    // =================================================================
    // SETTINGS DIALOG
    // =================================================================
    if (showSettingsPrompt) {
        AlertDialog(
            onDismissRequest = {
                showSettingsPrompt = false
                permissionsHandled = true
            },
            title = { Text("Permission Required") },
            text = { Text("Location permission is required for check-in.") },
            confirmButton = {
                Button(onClick = {
                    showSettingsPrompt = false
                    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.fromParts("package", context.packageName, null))
                    settingsLauncher.launch(intent)
                }) { Text("Open Settings") }
            },
            dismissButton = { Button(onClick = {
                showSettingsPrompt = false
                permissionsHandled = true
            }) { Text("Cancel") } }
        )
    }
}