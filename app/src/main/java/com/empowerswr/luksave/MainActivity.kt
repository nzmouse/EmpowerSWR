package com.empowerswr.luksave

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.empowerswr.luksave.network.NetworkModule
import com.empowerswr.luksave.network.NetworkModule.uploadService
import com.empowerswr.luksave.ui.pdf.ContractSigningScreen
import com.empowerswr.luksave.ui.screens.*
import com.empowerswr.luksave.ui.theme.EmpowerSWRTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber
import java.io.File

private val api: EmpowerApi by lazy {
    Retrofit.Builder()
        .baseUrl("https://db.empowerswr.com/api/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(EmpowerApi::class.java)
}

class MainActivity : ComponentActivity() {
    private val downloadMap = mutableMapOf<Long, String>()
    private val _downloadCompleteFlow = MutableSharedFlow<Pair<Long, String>>(replay = 1)
    val downloadCompleteFlow = _downloadCompleteFlow.asSharedFlow()

    companion object {
        val downloadCompleteFlowInternal: MutableSharedFlow<Pair<Long, String>> by lazy { MutableSharedFlow(replay = 1) }
    }

    fun storeDownload(downloadId: Long, filename: String) {
        downloadMap[downloadId] = filename
        Timber.d("MainActivity: Stored download ID: $downloadId for filename: $filename")
    }

    fun getDownloadFilename(downloadId: Long): String? {
        return downloadMap[downloadId]
    }

    fun removeDownload(downloadId: Long) {
        downloadMap.remove(downloadId)
        Timber.d("MainActivity: Removed download ID: $downloadId")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        Timber.i("=== onNewIntent CALLED ===")
        Timber.i("Extras: ${intent.extras}")

        val notificationId = intent.getStringExtra("notification_id") ?: "unknown"

        if (notificationId != "unknown") {
            Timber.i("=== REPORTING READ === notificationId=$notificationId")
            reportNotificationRead(notificationId)
        } else {
            Timber.i("No notification_id in intent")
        }
    }

    private fun reportNotificationRead(notificationId: String) {
        val workerId = PrefsHelper.getWorkerId(this) ?: run {
            Timber.e("reportNotificationRead: No workerId found")
            return
        }

        Timber.i("=== REPORTING READ === workerId=$workerId, notificationId=$notificationId")

        lifecycleScope.launch {
            try {
                val response = api.reportNotificationRead(workerId, notificationId)
                Timber.i("Read report SUCCESS - isSuccessful: ${response.isSuccessful}")
            } catch (e: Exception) {
                Timber.e(e, "Read report FAILED")
            }
        }
    }

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Timber.d("MainActivity: Broadcast received, action: ${intent?.action}")
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) ?: -1
            Timber.d("MainActivity: Broadcast download ID: $id")
            val filename = getDownloadFilename(id)
            if (filename != null) {
                val file = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    filename.replace("+", " ").replace("%20", " ").trim()
                )
                Timber.d("MainActivity: Checking file: ${file.absolutePath}, exists: ${file.exists()}, size: ${file.length()}")
                if (file.exists() && file.length() > 0 && file.extension.lowercase() == "pdf") {
                    _downloadCompleteFlow.tryEmit(id to filename)
                    downloadCompleteFlowInternal.tryEmit(id to filename)
                    Timber.i("MainActivity: Emitted download complete for ID: $id, filename: $filename")
                } else {
                    Timber.e("MainActivity: File invalid: exists=${file.exists()}, size=${file.length()}, extension=${file.extension}")
                }
                removeDownload(id)
            } else {
                Timber.w("MainActivity: No filename found for download ID: $id")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                addDataScheme("content")
                addDataScheme("file")
            }
        }
        try {
            val receiverFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Context.RECEIVER_NOT_EXPORTED
            } else {
                ContextCompat.RECEIVER_EXPORTED
            }
            registerReceiver(downloadReceiver, filter, receiverFlags)
            Timber.i("MainActivity: Download receiver registered successfully")
        } catch (e: Exception) {
            Timber.e(e, "MainActivity: Failed to register download receiver")
        }

        setContent {
            EmpowerSWRTheme {
                val viewModel: EmpowerViewModel = ViewModelProvider(this)[EmpowerViewModel::class.java]
                NavigationSetup(viewModel = viewModel, downloadCompleteFlow = downloadCompleteFlow)
            }
        }
        try {
            val view = findViewById<android.view.View>(android.R.id.content)
            view.javaClass.getMethod("setFrameRate", Float::class.javaPrimitiveType)
                .invoke(view, 60f)
        } catch (_: Exception) {
            // Ignore on older devices
        }
    }

    private fun handleIntent(intent: Intent?, viewModel: EmpowerViewModel) {
        intent?.extras?.let { extras ->
            val title = extras.getString("notification_title") ?: extras.getString("gcm.notification.title") ?: "No Title"
            val body = extras.getString("notification_body") ?: extras.getString("gcm.notification.body") ?: "No Body"
            viewModel.setNotificationFromIntent(title, body)
        } ?: run {
            Timber.i("No notification data in intent")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(downloadReceiver)
            Timber.d("MainActivity: Unregistered download receiver")
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "MainActivity: Receiver not registered")
        }
    }
}

sealed class DownloadState {
    object Idle : DownloadState()
    data class Completed(val filename: String) : DownloadState()
    data class Failed(val filename: String, val message: String) : DownloadState()
}

data class NavItem(val route: String, val title: String, val icon: ImageVector)

val navItems = listOf(
    NavItem("home", "", Icons.Default.Home),
    NavItem("profile", "", Icons.Default.Person),
    NavItem("team", "", Icons.Default.Group),
    NavItem("flight_pdb_details", "", Icons.Default.Flight),
    NavItem("documents", "", Icons.Default.Folder),
    NavItem("information", "", Icons.Default.Info)
)

fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

private const val ROUTE_LOGIN = "login"
private const val ROUTE_HOME = "home"
private const val ROUTE_REGISTRATION = "registration"
private const val ROUTE_FORGOT = "forgot_credentials"
private const val ROUTE_SETTINGS = "settings"

private val AUTH_ROUTES = setOf(ROUTE_LOGIN, ROUTE_REGISTRATION, ROUTE_FORGOT)

/**
 * True only after NavHost has called setGraph(). Reading .graph before that
 * throws IllegalStateException: You must call setGraph() before calling getGraph().
 */
private fun NavHostController.isGraphReady(): Boolean {
    if (currentBackStackEntry == null) return false
    return try {
        graph
        true
    } catch (_: IllegalStateException) {
        false
    }
}

/**
 * Navigate using route-string popUpTo so we never touch navController.graph
 * during the first composition (graph is still null).
 */
private fun NavHostController.navigateClearingTo(route: String) {
    if (!isGraphReady()) return
    navigate(route) {
        popUpTo(ROUTE_LOGIN) { inclusive = true }
        launchSingleTop = true
    }
}

private fun NavHostController.navigateTab(route: String) {
    if (!isGraphReady()) return
    navigate(route) {
        popUpTo(ROUTE_HOME) { saveState = true }
        launchSingleTop = true
        restoreState = false
    }
}

@SuppressLint("UnrememberedMutableState")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("UNCHECKED_CAST")
fun NavigationSetup(viewModel: EmpowerViewModel, downloadCompleteFlow: SharedFlow<Pair<Long, String>>) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val token by viewModel.token
    val workerDetails by viewModel.workerDetails
    val currentRoute by navController.currentBackStackEntryAsState()
    val currentDestination = currentRoute?.destination?.route
    var showLogoutDialog by remember { mutableStateOf(false) }
    var initialNavigationDone by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var feedbackText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    // Wait until NavHost has attached the graph. On first composition
    // currentDestination is null and graph.startDestinationId would crash.
    LaunchedEffect(token, currentDestination, showLogoutDialog) {
        val route = currentDestination ?: return@LaunchedEffect
        if (!navController.isGraphReady()) return@LaunchedEffect

        if (route == ROUTE_FORGOT || route == ROUTE_REGISTRATION) {
            return@LaunchedEffect
        }

        if (token == null && !showLogoutDialog && route != ROUTE_LOGIN) {
            navController.navigateClearingTo(ROUTE_LOGIN)
            initialNavigationDone = false
        } else if (token != null && !initialNavigationDone && route == ROUTE_LOGIN) {
            delay(300)
            if (navController.isGraphReady()) {
                navController.navigateClearingTo(ROUTE_HOME)
                initialNavigationDone = true
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Confirm Logout") },
            text = { Text("Are you sure you want to log out? Ensure no other users need to use this device.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.logout(context)
                    showLogoutDialog = false
                    initialNavigationDone = false
                    navController.navigateClearingTo(ROUTE_LOGIN)
                }) {
                    Text("Logout")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showFeedbackDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isLoading) {
                    showFeedbackDialog = false
                    feedbackText = ""
                }
            },
            title = { Text("Submit Feedback") },
            text = {
                Column {
                    TextField(
                        value = feedbackText,
                        onValueChange = { feedbackText = it },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Text),
                        enabled = !isLoading
                    )
                }
            },
            confirmButton = {
                val context = LocalContext.current
                var errorMessage by remember { mutableStateOf<String?>(null) }
                var navigateToLogin by remember { mutableStateOf(false) }
                TextButton(
                    onClick = {
                        if (feedbackText.isNotBlank()) {
                            isLoading = true
                            coroutineScope.launch {
                                try {
                                    viewModel.submitFeedback(context, 0, feedbackText, currentDestination)
                                    errorMessage = "Feedback submitted successfully"
                                    showFeedbackDialog = false
                                    feedbackText = ""
                                } catch (e: HttpException) {
                                    errorMessage = when (e.code()) {
                                        400 -> "Invalid input. Please check feedback text."
                                        401 -> {
                                            navigateToLogin = true
                                            "Unauthorized. Please log in again."
                                        }
                                        403 -> "Unauthorized for this worker."
                                        500 -> "Server error. Please try again later."
                                        else -> "Failed to submit feedback: ${e.message()}"
                                    }
                                    Timber.tag("NavigationSetup").e(e, "Feedback submission failed: %s", errorMessage)
                                } catch (e: Exception) {
                                    errorMessage = "Failed to submit feedback: ${e.message ?: "Unknown error"}"
                                    Timber.tag("NavigationSetup").e(e, "Unexpected error during feedback submission")
                                } finally {
                                    isLoading = false
                                }
                            }
                        } else {
                            errorMessage = "Please enter feedback text"
                        }
                    },
                    enabled = feedbackText.isNotBlank() && !isLoading
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        }
                        Text("Submit")
                    }
                }
                LaunchedEffect(errorMessage) {
                    errorMessage?.let {
                        snackbarHostState.showSnackbar(it)
                        errorMessage = null
                    }
                }
                LaunchedEffect(navigateToLogin) {
                    if (navigateToLogin) {
                        navController.navigateClearingTo(ROUTE_LOGIN)
                        navigateToLogin = false
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (!isLoading) {
                            showFeedbackDialog = false
                            feedbackText = ""
                        }
                    },
                    enabled = !isLoading
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (token == null) "Empower SWR - Login"
                        else workerDetails?.firstName ?: "Worker ID: ${PrefsHelper.getWorkerId(context) ?: "Unknown"}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                actions = {
                    if (token != null) {
                        IconButton(onClick = { showFeedbackDialog = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Comment,
                                contentDescription = "Feedback",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = {
                            navController.navigate(ROUTE_SETTINGS) {
                                popUpTo(ROUTE_HOME) { saveState = true }
                                launchSingleTop = true
                                restoreState = false
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Open Settings",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = { showLogoutDialog = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = "Logout",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (token != null && currentDestination != ROUTE_LOGIN && currentDestination != ROUTE_REGISTRATION) {
                NavigationBar {
                    navItems.forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.title) },
                            selected = currentDestination == item.route,
                            onClick = { navController.navigateTab(item.route) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_LOGIN,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(ROUTE_REGISTRATION) {
                RegistrationScreen(
                    viewModel = viewModel,
                    navController = navController
                )
            }
            composable(ROUTE_LOGIN) {
                LoginScreen(
                    viewModel = viewModel,
                    context = context,
                    navController = navController,
                    onLoginSuccess = {
                        initialNavigationDone = false
                        navController.navigateClearingTo(ROUTE_HOME)
                    }
                )
            }
            composable(ROUTE_HOME) {
                HomeScreen(
                    viewModel = viewModel,
                    context = context,
                    navController = navController
                )
            }
            composable("profile") {
                WorkerDetailsScreen(
                    viewModel = viewModel,
                    context = context,
                    navController = navController
                )
            }
            composable("information") {
                InformationScreen(
                    viewModel = viewModel,
                    navController = navController,
                    context = context
                )
            }
            composable("team") {
                TeamScreen(
                    viewModel = viewModel,
                    navController = navController,
                    context = context
                )
            }
            composable("flight_pdb_details") {
                FlightsScreen(
                    viewModel = viewModel,
                    context = context,
                    navController = navController
                )
            }
            composable("documents") {
                DocumentsScreen(
                    uploadService = NetworkModule.uploadService,
                    listFilesService = NetworkModule.listFilesService,
                    navController = navController
                )
            }
            composable(ROUTE_SETTINGS) {
                SettingsScreen(
                    navController = navController
                )
            }
            composable(
                "documentViewer/{filename}/{url}",
                arguments = listOf(
                    navArgument("filename") { type = NavType.StringType },
                    navArgument("url") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val filename = backStackEntry.arguments?.getString("filename") ?: ""
                val url = backStackEntry.arguments?.getString("url") ?: ""
                DocumentViewerScreen(
                    navController = navController,
                    filename = filename,
                    url = url,
                    listFilesService = NetworkModule.listFilesService,
                    downloadCompleteFlow = downloadCompleteFlow
                )
            }
            composable("contractSigning/{filename}/{url}") { backStackEntry ->
                val filename = backStackEntry.arguments?.getString("filename") ?: ""
                val url = backStackEntry.arguments?.getString("url") ?: ""
                ContractSigningScreen(
                    filename = filename,
                    contractUrl = url,
                    uploadService = uploadService,
                    onClose = { navController.popBackStack() }
                )
            }
            composable("edit_personal") {
                EditPersonalScreen(
                    viewModel = viewModel,
                    navController = navController
                )
            }
            composable("edit_contact") {
                EditContactScreen(
                    viewModel = viewModel,
                    navController = navController
                )
            }
            composable("edit_passport") {
                EditPassportScreen(
                    viewModel = viewModel,
                    navController = navController
                )
            }
            composable(ROUTE_FORGOT) {
                ForgotCredentialsScreen(viewModel = viewModel, navController = navController)
            }
        }
    }
}
