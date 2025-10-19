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
    var username by rememberSaveable { mutableStateOf("") }
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

    val token by viewModel.token
    val loginError by viewModel.loginError

    // Log screen open
    LaunchedEffect(Unit) {
        Timber.i("RegistrationScreen opened")
    }

    // Individual field errors (set on button click)
    var passportError by remember { mutableStateOf<String?>(null) }
    var surnameError by remember { mutableStateOf<String?>(null) }
    var usernameError by remember { mutableStateOf<String?>(null) }
    var pinError by remember { mutableStateOf<String?>(null) }

    fun validateFields(): Boolean {
        // Clear previous errors
        passportError = null
        surnameError = null
        usernameError = null
        pinError = null

        var isValid = true

        // Passport validation
        val trimmedPassport = passport.trim().uppercase()
        if (trimmedPassport.isEmpty()) {
            passportError = "Passport number is required"
            isValid = false
        } else if (!trimmedPassport.matches(Regex("^[A-Z]{2}\\d{6,7}$"))) {
            passportError = "Passport format: e.g., RV0127280"
            isValid = false
        }

        // Surname validation
        val trimmedSurname = surname.trim().uppercase()
        if (trimmedSurname.isEmpty()) {
            surnameError = "Surname is required"
            isValid = false
        }

        // Username validation
        if (username.isEmpty()) {
            usernameError = "Username cannot be empty"
            isValid = false
        } else if (username.length < 3) {
            usernameError = "Username must be at least 3 characters"
            isValid = false
        } else if (username.length > 20) {
            usernameError = "Username cannot exceed 20 characters"
            isValid = false
        } else if (!username.matches(Regex("^[a-zA-Z0-9]+$"))) {
            usernameError = "Username can only contain letters and numbers"
            isValid = false
        }

        // PIN validation
        if (pin.length != 4) {
            pinError = "PIN must be 4 digits"
            isValid = false
        } else if (pin != confirmPin) {
            pinError = "PINs do not match"
            isValid = false
        } else if (pin.all { it == pin[0] }) {
            pinError = "PIN cannot be all the same digit"
            isValid = false
        }

        return isValid
    }

    fun onRegisterClick() {
        val valid = validateFields()
        if (valid) {
            keyboardController?.hide()
            coroutineScope.launch {
                Timber.i("Registration attempt: passport=$passport, username=$username")
                viewModel.register(
                    passport.trim().uppercase(),
                    surname.trim().uppercase(),
                    username.trim(),
                    pin,
                    localContext
                )
            }
        }
    }

    // Show Worker ID dialog after registration
    if (showWorkerIdDialog) {
        Dialog(
            onDismissRequest = { /* Prevent dismissal without OK */ },
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
                            Timber.i("Registration success: Worker ID dialog dismissed")
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
            val existingToken = PrefsHelper.getToken(localContext)
            if (existingToken != null && existingToken != token) {
                PrefsHelper.clearToken(localContext)
            }
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    fcmToken = task.result
                    coroutineScope.launch {
                        viewModel.updateFcmToken(fcmToken!!, localContext)
                    }
                } else {
                    fcmError = "Failed to get FCM token: ${task.exception?.message}"
                }
            }
            viewModel.fetchWorkerDetails(localContext)
            viewModel.fetchAlerts(localContext)
            PrefsHelper.setRegistered(localContext, true)
            registrationComplete = true
            Timber.i("Registration success: Token received")
            showWorkerIdDialog = true
        }
    }

    // Log registration failure
    LaunchedEffect(loginError) {
        loginError?.let { error ->
            Timber.e("Registration failed: $error")
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
                text = "Enter your passport number (e.g., RV0127280), surname, and choose a username and PIN",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Passport Field
            OutlinedTextField(
                value = passport,
                onValueChange = { passport = it.trim().uppercase() },
                label = { Text("Passport Number (e.g., RV0127280)") },
                isError = passportError != null,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                )
            )
            passportError?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), // ✅ BOLD
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Surname Field
            OutlinedTextField(
                value = surname,
                onValueChange = { surname = it.trim().uppercase() },
                label = { Text("Surname") },
                isError = surnameError != null,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                )
            )
            surnameError?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), // ✅ BOLD
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Username Field
            OutlinedTextField(
                value = username,
                onValueChange = { username = it.trim() },
                label = { Text("Username / Nickname") },
                isError = usernameError != null,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                )
            )
            usernameError?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), // ✅ BOLD
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            // PIN Field
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it.take(4) },
                label = { Text("Choose a 4-Digit PIN") },
                isError = pinError != null,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Next
                )
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Confirm PIN Field
            OutlinedTextField(
                value = confirmPin,
                onValueChange = { confirmPin = it.take(4) },
                label = { Text("Re-enter PIN") },
                isError = pinError != null,
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
                        onRegisterClick()
                    }
                )
            )
            pinError?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), // ✅ BOLD
                    modifier = Modifier.padding(start = 16.dp)
                )
            }

            // Server Errors
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
            fcmError?.let {
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
                onClick = { onRegisterClick() },
                modifier = Modifier.fillMaxWidth()
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