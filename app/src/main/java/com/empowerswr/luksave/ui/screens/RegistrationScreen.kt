package com.empowerswr.luksave.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
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

private val RegisterGreen = Color(0xFF1B7A3D)

private fun findVersionLogoId(context: Context): Int {
    val names = arrayOf(
        "version_logo",
        "versionlogo",
        "ic_version_logo",
        "version_logo_foreground"
    )
    val types = arrayOf("drawable", "mipmap")
    for (name in names) {
        for (type in types) {
            val id = context.resources.getIdentifier(name, type, context.packageName)
            if (id != 0) return id
        }
    }
    return 0
}

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

    val versionLogoId = remember { findVersionLogoId(localContext) }

    LaunchedEffect(Unit) {
        Timber.i("RegistrationScreen opened")
    }

    var passportError by remember { mutableStateOf<String?>(null) }
    var surnameError by remember { mutableStateOf<String?>(null) }
    var usernameError by remember { mutableStateOf<String?>(null) }
    var pinError by remember { mutableStateOf<String?>(null) }

    fun validateFields(): Boolean {
        passportError = null
        surnameError = null
        usernameError = null
        pinError = null

        var isValid = true

        val trimmedPassport = passport.trim().uppercase()
        if (trimmedPassport.isEmpty()) {
            passportError = "Raetem passport"
            isValid = false
        } else if (!trimmedPassport.matches(Regex("^[A-Z]{2}\\d{6,7}$"))) {
            passportError = "Passport olsem RV0127280"
            isValid = false
        }

        val trimmedSurname = surname.trim().uppercase()
        if (trimmedSurname.isEmpty()) {
            surnameError = "Raetem surname"
            isValid = false
        }

        if (username.isEmpty()) {
            usernameError = "Raetem username"
            isValid = false
        } else if (username.length < 3) {
            usernameError = "Username i mas 3 leta"
            isValid = false
        } else if (username.length > 20) {
            usernameError = "Username i long tumas"
            isValid = false
        } else if (!username.matches(Regex("^[a-zA-Z0-9]+$"))) {
            usernameError = "Leta mo namba nomo"
            isValid = false
        }

        if (pin.length != 4) {
            pinError = "PIN i mas 4 namba"
            isValid = false
        } else if (pin != confirmPin) {
            pinError = "Tufala PIN i no semak"
            isValid = false
        } else if (pin.all { it == pin[0] }) {
            pinError = "PIN i no 1111 o 0000"
            isValid = false
        }

        return isValid
    }

    fun onRegisterClick() {
        if (validateFields()) {
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

    if (showWorkerIdDialog) {
        Dialog(
            onDismissRequest = { },
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
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "YU STRET",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Username blong yu",
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = username.ifBlank { PrefsHelper.getWorkerId(localContext) ?: "" },
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = RegisterGreen,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Yusum username mo PIN blong login.",
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )
                    val workerId = PrefsHelper.getWorkerId(localContext)
                    if (!workerId.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Worker ID: $workerId",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = {
                            Timber.i("Registration success: dialog dismissed")
                            showWorkerIdDialog = false
                            dialogDismissed = true
                            navController.navigate("login") {
                                popUpTo("registration") { inclusive = true }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RegisterGreen,
                            contentColor = Color.White
                        )
                    ) {
                        Text("OK", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

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

    LaunchedEffect(loginError) {
        loginError?.let { error ->
            Timber.e("Registration failed: $error")
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (versionLogoId != 0) {
                Image(
                    painter = painterResource(id = versionLogoId),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .fillMaxHeight(),
                    contentScale = ContentScale.FillWidth,
                    colorFilter = ColorFilter.tint(
                        Color.Black.copy(alpha = 0.18f),
                        BlendMode.SrcAtop
                    ),
                    alpha = 0.28f
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .verticalScroll(scrollState)
                    .imePadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "REJISTA",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Passport • Surname • Username • PIN",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = passport,
                    onValueChange = { passport = it.trim().uppercase() },
                    label = { Text("Passport  •  RV0127280") },
                    isError = passportError != null,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 20.sp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    )
                )
                FieldError(passportError)
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = surname,
                    onValueChange = { surname = it.trim().uppercase() },
                    label = { Text("Surname") },
                    isError = surnameError != null,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 20.sp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    )
                )
                FieldError(surnameError)
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it.trim() },
                    label = { Text("Username") },
                    isError = usernameError != null,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 20.sp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    )
                )
                FieldError(usernameError)
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.take(4) },
                    label = { Text("PIN  •  4 namba") },
                    isError = pinError != null,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 20.sp),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Next
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { confirmPin = it.take(4) },
                    label = { Text("PIN bakegen") },
                    isError = pinError != null,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 20.sp),
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
                FieldError(pinError)

                loginError?.let {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                fcmError?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = { onRegisterClick() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RegisterGreen,
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("REJISTA", fontSize = 24.sp, fontWeight = FontWeight.Black)
                }

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { navController.navigate("login") }) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Mi gat username finis")
                }

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_DIAL).apply {
                            data = "tel:34357 o 5534357".toUri()
                        }
                        localContext.startActivity(intent)
                    }
                ) {
                    Text("Help? Ring 555-1234")
                }
            }
        }
    }
}

@Composable
private fun FieldError(message: String?) {
    message?.let {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = it,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
