package com.empowerswr.test

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.empowerswr.test.network.NetworkModule
import com.empowerswr.test.ui.screens.*
import com.empowerswr.test.ui.theme.EmpowerSWRTheme
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.delay

data class NavItem(
    val title: String,
    val icon: ImageVector,
    val route: String
)

val navItems = listOf(
    NavItem("Home", Icons.Outlined.Home, "home"),
    NavItem("Profile", Icons.Outlined.Person, "profile"),
    NavItem("Flights", Icons.Outlined.AirplanemodeActive, "flight_pdb_details"),
    NavItem("Files", Icons.Outlined.Menu, "documents"),
    NavItem("Info", Icons.Outlined.Info, "information")
)

class MainActivity : ComponentActivity() {
    private val viewModel: EmpowerViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(EmpowerViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return EmpowerViewModel(application) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        val savedToken = PrefsHelper.getToken(this)
        val savedTokenExpiry = PrefsHelper.getTokenExpiry(this)
        Log.d("EmpowerSWR", "onCreate - Saved token: $savedToken, expiry: $savedTokenExpiry")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = android.Manifest.permission.POST_NOTIFICATIONS
            if (checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(permission), 100)
            }
        }

        if (savedToken != null && savedTokenExpiry != null && savedToken.split(".").size == 3) {
            val currentTime = System.currentTimeMillis() / 1000
            if (savedTokenExpiry > currentTime) {
                viewModel.setToken(savedToken)
                Log.d("EmpowerSWR", "onCreate - Restored valid JWT token: $savedToken")
            } else {
                Log.d("EmpowerSWR", "onCreate - Saved token expired, clearing")
                PrefsHelper.clearToken(this)
                viewModel.setToken(null)
            }
        } else {
            Log.d("EmpowerSWR", "onCreate - Saved token invalid or missing, clearing")
            PrefsHelper.clearToken(this)
            viewModel.setToken(null)
        }

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val fcmToken = task.result
                Log.d("EmpowerSWR", "MainActivity - FCM Token: $fcmToken")
                PrefsHelper.saveFcmToken(this, fcmToken)
                val workerId = PrefsHelper.getWorkerId(this)
                if (workerId != null) {
                    viewModel.updateFcmToken(fcmToken, workerId)
                }
            } else {
                Log.e("EmpowerSWR", "MainActivity - FCM Token Error: ${task.exception?.message}")
            }
        }

        setContent {
            EmpowerSWRTheme {
                NavigationSetup(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d("EmpowerSWR", "onNewIntent called with intent: ${intent.extras?.keySet()?.joinToString() ?: "null"}")
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val notificationTitle = intent?.getStringExtra("notification_title")
        val notificationBody = intent?.getStringExtra("notification_body")
        Log.d("EmpowerSWR", "handleIntent - Extras: ${intent?.extras?.keySet()?.joinToString() ?: "none"}")
        if (notificationTitle != null || notificationBody != null) {
            viewModel.setNotificationFromIntent(notificationTitle, notificationBody)
            Log.d("EmpowerSWR", "handleIntent - Notification intent received: $notificationTitle: $notificationBody")
        } else {
            Log.d("EmpowerSWR", "handleIntent - No notification intent extras found")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationSetup(viewModel: EmpowerViewModel) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val token by viewModel.token
    val workerDetails by viewModel.workerDetails
    val currentRoute by navController.currentBackStackEntryAsState()
    val currentDestination = currentRoute?.destination?.route
    var showLogoutDialog by remember { mutableStateOf(false) }
    var initialNavigationDone by rememberSaveable { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Force navigation to home only on initial token set and when on login or invalid route
    LaunchedEffect(token, currentDestination) {
        if (token != null && !initialNavigationDone && (currentDestination == "login" || currentDestination == null)) {
            Log.d("EmpowerSWR", "Token set, forcing initial navigation to home from $currentDestination")
            delay(500) // Delay for token propagation
            navController.navigate("home") {
                popUpTo(navController.graph.startDestinationId) { inclusive = true }
                launchSingleTop = true
            }
            initialNavigationDone = true
            Log.d("EmpowerSWR", "Initial navigation to home completed")
        } else if (token != null && currentDestination == "registration") {
            Log.d("EmpowerSWR", "Token set during registration, skipping forced navigation to allow dialog")
        } else if (token != null && initialNavigationDone) {
            Log.d("EmpowerSWR", "Token set, but initial navigation already done, staying on $currentDestination")
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Confirm Logout") },
            text = { Text("Are you sure you want to log out? Ensure no other users need to use this device.") },
            confirmButton = {
                TextButton(onClick = {
                    PrefsHelper.clearToken(context)
                    viewModel.logout()
                    showLogoutDialog = false
                    initialNavigationDone = false // Reset for next login
                    navController.navigate("login") {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (token == null) "Empower SWR - Login"
                        else workerDetails?.firstName ?: "Worker ID: ${PrefsHelper.getWorkerId(context) ?: "Unknown"}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                actions = {
                    if (token != null) {
                        // Settings Icon
                        IconButton(onClick = { navController.navigate("settings") }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Open Settings",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        // Logout Icon
                        IconButton(onClick = { showLogoutDialog = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = "Logout",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (token != null && currentDestination != "login" && currentDestination != "registration") {
                NavigationBar {
                    navItems.forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.title) },
                            label = { Text(item.title) },
                            selected = currentDestination == item.route,
                            onClick = {
                                Log.d("EmpowerSWR", "NavigationBarItem clicked: ${item.route}")
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = false
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = if (PrefsHelper.hasRegistered(context)) "login" else "registration",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("registration") {
                RegistrationScreen(
                    viewModel = viewModel,
                    context = context,
                    navController = navController
                )
            }
            composable("login") {
                LoginScreen(
                    viewModel = viewModel,
                    context = context,
                    navController = navController,
                    onLoginSuccess = {
                        Log.d("EmpowerSWR", "onLoginSuccess triggered, navigating to home")
                        initialNavigationDone = false
                        navController.navigate("home") {
                            popUpTo(navController.graph.startDestinationId) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable("home") {
                HomeScreen(
                    viewModel = viewModel,
                    context = context
                )
            }
            composable("profile") {
                WorkerDetailsScreen(
                    viewModel = viewModel,
                    context = context,
                    navController = navController
                )
            }
            composable("work_location") {
                WorkLocationScreen(
                    viewModel = viewModel,
                    context = context
                )
            }
            composable("information") {
                InformationScreen(
                    viewModel = viewModel,
                    navController = navController,
                    context = context
                )
            }
            composable("contracts") {
                ContractsScreen(
                    viewModel = viewModel,
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
                    navController = navController
                )
            }
            composable("document-list") {
                DocumentListScreen(
                    navController = navController,
                    context = context
                )
            }
            composable("document-viewer/{filename}") { backStackEntry ->
                DocumentViewerScreen(
                    navController = navController,
                    filename = backStackEntry.arguments?.getString("filename") ?: ""
                )
            }
            composable("document-signer/{filename}") { backStackEntry ->
                DocumentSignerScreen(
                    navController = navController,
                    filename = backStackEntry.arguments?.getString("filename") ?: "",
                    context = context
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
            composable("settings") {
                SettingsScreen(navController = navController)
            }
            composable(
                route = "documents?type={type}&expiryYY={expiryYY}&from={from}",
                arguments = listOf(
                    navArgument("type") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("expiryYY") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("from") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val type = backStackEntry.arguments?.getString("type")
                val expiryYY = backStackEntry.arguments?.getString("expiryYY")
                val from = backStackEntry.arguments?.getString("from")
                Log.d("EmpowerSWR", "DocumentsScreen NavHost params: type=$type, expiryYY=$expiryYY, from=$from")
                DocumentsScreen(
                    uploadService = NetworkModule.uploadService,
                    navController = navController,
                    type = type,
                    expiryYY = expiryYY,
                    from = from
                )
            }
        }
    }
}