package com.empowerswr.luksave.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
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
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch

private val RegisterGreen = Color(0xFF1B7A3D)
private val RegisterGreenDark = Color(0xFF145C2E)

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
fun LoginScreen(
    viewModel: EmpowerViewModel,
    context: Context,
    navController: NavHostController,
    onLoginSuccess: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val loginErrorState by viewModel.loginError

    var workerIdOrUsername by rememberSaveable { mutableStateOf("") }
    var pin by rememberSaveable { mutableStateOf("") }
    var loginError by remember { mutableStateOf<String?>(null) }
    var inputError by remember { mutableStateOf<String?>(null) }
    var showSettingsPrompt by remember { mutableStateOf(false) }
    var permissionsHandled by remember { mutableStateOf(false) }

    val prefs = remember {
        context.getSharedPreferences("empower_prefs", Context.MODE_PRIVATE)
    }

    val isFirstRun = remember {
        val neverLoggedIn = !prefs.getBoolean("has_logged_in", false)
        val noSavedUser = prefs.getString("last_username", null).isNullOrEmpty()
        val neverRegistered = !prefs.getBoolean("has_registered", false)
        if (neverLoggedIn && noSavedUser && neverRegistered && prefs.getLong("install_timestamp", 0L) == 0L) {
            prefs.edit { putLong("install_timestamp", System.currentTimeMillis()) }
        }
        neverLoggedIn && noSavedUser && neverRegistered
    }

    var showLoginForm by rememberSaveable { mutableStateOf(false) }
    val showChooser = isFirstRun && !showLoginForm

    context.findEmpowerActivity() ?: run {
        throw IllegalStateException("LoginScreen must be called within a ComponentActivity")
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (!fineGranted && !coarseGranted) {
            showSettingsPrompt = true
            loginError = "Opem location long settings."
        }
        permissionsHandled = true
    }

    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            showSettingsPrompt = true
            loginError = "Opem location long settings."
        }
        permissionsHandled = true
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            permissionsHandled = true
        }
    }

    fun validateInput(): String? = when {
        workerIdOrUsername.isEmpty() -> "Raetem username"
        pin.length != 4 -> "PIN i mas 4 namba"
        else -> null
    }

    fun onLoginClick() {
        inputError = validateInput()
        loginError = null
        if (inputError == null) {
            coroutineScope.launch {
                viewModel.login(workerIdOrUsername, pin, context)
            }
        }
    }

    LaunchedEffect(loginErrorState) {
        loginErrorState?.let { message ->
            val shown = if (
                isFirstRun &&
                (message.contains("invalid", ignoreCase = true) ||
                        message.contains("not found", ignoreCase = true) ||
                        message.contains("incorrect", ignoreCase = true))
            ) {
                "I no wok. Presim REJISTA."
            } else {
                "Username o PIN i rong"
            }
            loginError = shown
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    message = shown,
                    actionLabel = "OK",
                    duration = SnackbarDuration.Long
                )
                viewModel.clearCheckInState()
            }
        }
    }

    LaunchedEffect(viewModel.token.value) {
        if (viewModel.token.value != null) {
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

    val versionLogoId = remember { findVersionLogoId(context) }
    val versionNicknameId = remember {
        context.resources.getIdentifier("version_nickname", "string", context.packageName)
    }
    val versionNickname = if (versionNicknameId != 0) {
        context.getString(versionNicknameId)
    } else {
        "Egg"
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
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
                if (showChooser) {
                    FirstRunChooser(
                        onRegister = { navController.navigate("registration") },
                        onLogin = { showLoginForm = true }
                    )
                } else {
                    LoginForm(
                        workerIdOrUsername = workerIdOrUsername,
                        onUsernameChange = { workerIdOrUsername = it.trim() },
                        pin = pin,
                        onPinChange = { pin = it.take(4) },
                        inputError = inputError,
                        loginError = loginError,
                        isFirstRun = isFirstRun,
                        onLoginClick = {
                            keyboardController?.hide()
                            onLoginClick()
                        },
                        onForgot = { navController.navigate("forgot_credentials") },
                        onRegister = { navController.navigate("registration") },
                        onCheckLocation = {
                            coroutineScope.launch {
                                if (ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.ACCESS_FINE_LOCATION
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    permissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                } else {
                                    snackbarHostState.showSnackbar(
                                        message = "Location i stret",
                                        actionLabel = "OK",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            }
                        },
                        locationGranted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED,
                        onBackToChooser = if (isFirstRun) {
                            { showLoginForm = false }
                        } else {
                            null
                        }
                    )
                }
                Spacer(modifier = Modifier.height(28.dp))
                Text(
                    text = versionNickname,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (showSettingsPrompt) {
        AlertDialog(
            onDismissRequest = {
                showSettingsPrompt = false
                permissionsHandled = true
            },
            title = { Text("Location") },
            text = { Text("App i nidim location blong check-in.") },
            confirmButton = {
                Button(onClick = {
                    showSettingsPrompt = false
                    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.fromParts("package", context.packageName, null))
                    settingsLauncher.launch(intent)
                }) { Text("Settings") }
            },
            dismissButton = {
                Button(onClick = {
                    showSettingsPrompt = false
                    permissionsHandled = true
                }) { Text("No") }
            }
        )
    }
}

@Composable
private fun FirstRunChooser(
    onRegister: () -> Unit,
    onLogin: () -> Unit
) {
    Text(
        text = "Luksave",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    Text(
        text = "EmpowerSWR",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(28.dp))

    Text(
        text = "Fes taem?",
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    Text(
        text = "Presim grin baton",
        fontSize = 20.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 4.dp)
    )

    Spacer(modifier = Modifier.height(24.dp))

    Button(
        onClick = onRegister,
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = RegisterGreen,
            contentColor = Color.White
        )
    ) {
        Icon(
            imageVector = Icons.Default.PersonAdd,
            contentDescription = null,
            modifier = Modifier.size(36.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "REJISTA",
            fontSize = 28.sp,
            fontWeight = FontWeight.Black
        )
    }

    Spacer(modifier = Modifier.height(28.dp))

    Text(
        text = "Bambae yu yusum:",
        fontSize = 16.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Top
    ) {
        StepIcon(Icons.Default.AccountBox, "Passport")
        StepIcon(Icons.Default.Person, "Surname")
        StepIcon(Icons.Default.Lock, "4 namba")
    }

    Spacer(modifier = Modifier.height(36.dp))

    TextButton(onClick = onLogin) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("Mi gat username finis")
    }
}

@Composable
private fun StepIcon(icon: ImageVector, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color(0xFFE8F5E9)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = RegisterGreenDark,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun LoginForm(
    workerIdOrUsername: String,
    onUsernameChange: (String) -> Unit,
    pin: String,
    onPinChange: (String) -> Unit,
    inputError: String?,
    loginError: String?,
    isFirstRun: Boolean,
    onLoginClick: () -> Unit,
    onForgot: () -> Unit,
    onRegister: () -> Unit,
    onCheckLocation: () -> Unit,
    locationGranted: Boolean,
    onBackToChooser: (() -> Unit)?
) {
    Text(
        text = "LOGIN",
        fontSize = 28.sp,
        fontWeight = FontWeight.Black
    )
    Spacer(modifier = Modifier.height(20.dp))

    OutlinedTextField(
        value = workerIdOrUsername,
        onValueChange = onUsernameChange,
        label = { Text("Username") },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Next
        ),
        isError = inputError != null,
        modifier = Modifier.fillMaxWidth(),
        textStyle = LocalTextStyle.current.copy(fontSize = 20.sp)
    )
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedTextField(
        value = pin,
        onValueChange = onPinChange,
        label = { Text("PIN  •  4 namba") },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { onLoginClick() }),
        visualTransformation = PasswordVisualTransformation(),
        isError = inputError != null,
        modifier = Modifier.fillMaxWidth(),
        textStyle = LocalTextStyle.current.copy(fontSize = 20.sp)
    )

    inputError?.let {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = it,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
    loginError?.let {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = it,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }

    Spacer(modifier = Modifier.height(20.dp))
    Button(
        onClick = onLoginClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        shape = RoundedCornerShape(12.dp),
        enabled = workerIdOrUsername.isNotEmpty() && pin.isNotEmpty()
    ) {
        Icon(Icons.Default.Lock, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("LOGIN", fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }

    Spacer(modifier = Modifier.height(8.dp))
    TextButton(onClick = onForgot) {
        Text("Mi fogetem PIN o Username?")
    }

    Spacer(modifier = Modifier.height(16.dp))
    Button(
        onClick = onRegister,
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isFirstRun) 72.dp else 56.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = RegisterGreen,
            contentColor = Color.White
        )
    ) {
        Icon(Icons.Default.PersonAdd, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "REJISTA",
            fontSize = 22.sp,
            fontWeight = FontWeight.Black
        )
    }

    onBackToChooser?.let {
        TextButton(onClick = it) {
            Text("Go bak")
        }
    }

    if (!locationGranted) {
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onCheckLocation) {
            Text("Location permission")
        }
    }
}
