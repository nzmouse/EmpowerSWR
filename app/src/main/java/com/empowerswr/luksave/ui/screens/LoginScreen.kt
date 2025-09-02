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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.empowerswr.luksave.EmpowerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: EmpowerViewModel,
    context: Context,
    navController: NavHostController, // Added NavHostController parameter
    onLoginSuccess: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarScope = rememberCoroutineScope { Dispatchers.Main }
    var workerId by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var loginError by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val loginErrorState by viewModel.loginError
    var showSettingsPrompt by remember { mutableStateOf(false) }
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
            showSettingsPrompt = true;
            loginError = "Location permission denied. Please enable it in app settings.";
        }
    }

    // Settings launcher
    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            showSettingsPrompt = true;
            loginError = "Location permission still denied. Please enable it in settings.";
            Timber.tag("InformationScreen").e("Location permission still denied after settings");
        }
    }

    // Check permissions on start with delay to ensure lifecycle stability
    LaunchedEffect(Unit) {
        delay(500) // Ensure composable is stable
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    // Check permission and request
    fun checkAndRequestLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            snackbarScope.launch {
                snackbarHostState.showSnackbar(
                    message = "Location permission already granted",
                    actionLabel = "Dismiss",
                    duration = SnackbarDuration.Short
                )
            }
        } else {
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
                    }
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        showSettingsPrompt = false
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
                    Timber.tag("LoginScreen").e("Login error Snackbar shown: Message= %s Result = %s", message, result)
                    viewModel.clearCheckInState()
                } catch (e: Exception) {
                    Timber.tag("LoginScreen").e(e, "Failed to show login error Snackbar")
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
            verticalArrangement = Arrangement.Top
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
            Text(
                text = "Don't have an account? Register here",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable {
                        navController.navigate("registration")
                    }
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
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