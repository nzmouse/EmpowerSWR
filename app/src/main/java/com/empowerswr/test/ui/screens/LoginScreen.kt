package com.empowerswr.test.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.empowerswr.test.EmpowerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: EmpowerViewModel,
    context: Context,
    onLoginSuccess: () -> Unit
) {
    Log.d("EmpowerSWR", "LoginScreen composable called, Android version: ${Build.VERSION.SDK_INT}, Release: ${Build.VERSION.RELEASE}")
    val coroutineScope = rememberCoroutineScope()
    val snackbarScope = rememberCoroutineScope { Dispatchers.Main }
    var workerId by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var loginError by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val loginErrorState by viewModel.loginError
    var showSettingsPrompt by remember { mutableStateOf(false) }
    val activity = context.findEmpowerActivity() ?: run {
        Log.e("EmpowerSWR", "Context is not a ComponentActivity")
        throw IllegalStateException("LoginScreen must be called within a ComponentActivity")
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        Log.d("EmpowerSWR", "Permission result: fineGranted=$fineGranted, coarseGranted=$coarseGranted")
        if (fineGranted || coarseGranted) {
            Log.d("EmpowerSWR", "Location permission granted")
        } else {
            showSettingsPrompt = true
            loginError = "Location permission denied. Please enable it in app settings."
            Log.d("EmpowerSWR", "Permission denied, showing settings prompt")
        }
    }

    // Settings launcher
    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        Log.d("EmpowerSWR", "Returned from settings, rechecking permission")
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            Log.d("EmpowerSWR", "Location permission granted after settings")
        } else {
            showSettingsPrompt = true
            loginError = "Location permission still denied. Please enable it in settings."
            Log.d("EmpowerSWR", "Location permission still denied after settings")
        }
    }

    // Check permissions on start with delay to ensure lifecycle stability
    LaunchedEffect(Unit) {
        Log.d("EmpowerSWR", "Checking initial permission state in LoginScreen")
        delay(500) // Ensure composable is stable
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d("EmpowerSWR", "Initial permission request in LoginScreen")
            permissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    // Check permission and request
    fun checkAndRequestLocationPermission() {
        Log.d("EmpowerSWR", "Checking location permission")
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            Log.d("EmpowerSWR", "Location permission already granted")
            snackbarScope.launch {
                snackbarHostState.showSnackbar(
                    message = "Location permission already granted",
                    actionLabel = "Dismiss",
                    duration = SnackbarDuration.Short
                )
            }
        } else {
            Log.d("EmpowerSWR", "Launching permission request")
            permissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    // Settings prompt dialog
    if (showSettingsPrompt) {
        AlertDialog(
            onDismissRequest = {
                showSettingsPrompt = false
                Log.d("EmpowerSWR", "Settings dialog dismissed")
            },
            title = { Text("Permission Required") },
            text = { Text("Location permission is required for check-in. Please enable it in app settings.") },
            confirmButton = {
                Button(
                    onClick = {
                        showSettingsPrompt = false
                        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        intent.data = Uri.fromParts("package", context.packageName, null)
                        settingsLauncher.launch(intent)
                        Log.d("EmpowerSWR", "Opening app settings for permission")
                    }
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        showSettingsPrompt = false
                        Log.d("EmpowerSWR", "Settings dialog cancelled")
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    LaunchedEffect(loginErrorState) {
        loginErrorState?.let { message ->
            loginError = message
            snackbarScope.launch {
                try {
                    val result = snackbarHostState.showSnackbar(
                        message = message,
                        actionLabel = "Dismiss",
                        duration = SnackbarDuration.Long
                    )
                    Log.d("EmpowerSWR", "Login error Snackbar shown: $message, result: $result")
                    viewModel.clearCheckInState()
                } catch (e: Exception) {
                    Log.e("EmpowerSWR", "Failed to show login error Snackbar: ${e.message}", e)
                }
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
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Login",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = workerId,
                onValueChange = { workerId = it },
                label = { Text("Worker ID") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it },
                label = { Text("PIN") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                        coroutineScope.launch {
                            Log.d("EmpowerSWR", "Login button clicked")
                            viewModel.login(workerId, pin)
                            if (viewModel.token.value != null) {
                                onLoginSuccess()
                            }
                        }
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    Log.d("EmpowerSWR", "Login button clicked")
                    coroutineScope.launch {
                        viewModel.login(workerId, pin)
                        if (viewModel.token.value != null) {
                            onLoginSuccess()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = workerId.isNotEmpty() && pin.isNotEmpty()
            ) {
                Text("Login")
            }
            loginError?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    Log.d("EmpowerSWR", "Manual permission check button pressed")
                    coroutineScope.launch {
                        checkAndRequestLocationPermission()
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
}