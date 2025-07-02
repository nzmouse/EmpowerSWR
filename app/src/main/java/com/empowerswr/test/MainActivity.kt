package com.empowerswr.test

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.empowerswr.test.ui.screens.RegistrationScreen
import com.empowerswr.test.ui.screens.LoginScreen
import com.empowerswr.test.ui.screens.HomeScreen
import com.empowerswr.test.ui.screens.WorkerDetailsScreen
import com.empowerswr.test.ui.screens.WorkLocationScreen
import com.empowerswr.test.ui.screens.ContractsScreen
import com.empowerswr.test.ui.screens.DocumentsScreen
import com.empowerswr.test.ui.screens.UpdateDetailsScreen
import com.empowerswr.test.ui.screens.InformationScreen
import com.empowerswr.test.ui.screens.SettingsScreen
import com.empowerswr.test.ui.theme.EmpowerSWRTheme
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.empowerswr.test.network.NetworkModule
import com.empowerswr.test.network.NetworkModule.uploadService

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
            Log.d("EmpowerSWR", "setContent called")
            EmpowerSWRTheme {
                AppNavigation(
                    viewModel = viewModel,
                    context = this@MainActivity
                )
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

data class NavItem(
    val title: String,
    val icon: ImageVector,
    val route: String
)

val navItems = listOf(
    NavItem("Home", Icons.Filled.Home, "home"),
    NavItem("Profile", Icons.Filled.Person, "profile"),
    NavItem("Work Location", Icons.Filled.LocationOn, "work_location"),
    NavItem("Contracts", Icons.Filled.Description, "contracts"),
    NavItem("Update Details", Icons.Filled.Edit, "update_details"),
    NavItem("Documents", Icons.Filled.FileCopy, "documents"),
    NavItem("Information", Icons.Filled.Info, "information"),
    NavItem("Settings", Icons.Filled.Settings, "settings")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    viewModel: EmpowerViewModel,
    context: Context
) {
    val navController = rememberNavController()
    val token by viewModel.token
    val workerDetails by viewModel.workerDetails
    val currentRoute by navController.currentBackStackEntryAsState()
    val currentDestination = currentRoute?.destination?.route
    var showLogoutDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Force navigation to home after login and clear back stack
    LaunchedEffect(token) {
        if (token != null) {
            Log.d("EmpowerSWR", "Token set, forcing navigation to home from $currentDestination")
            delay(500) // Increased delay for token propagation
            navController.navigate("home") {
                popUpTo(navController.graph.startDestinationId) { inclusive = true }
                launchSingleTop = true
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
                    PrefsHelper.clearToken(context)
                    viewModel.logout()
                    showLogoutDialog = false
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
                        if (token == null) "Empower SWR - Login"
                        else "Logged in as ${workerDetails?.firstName ?: "Worker ID: ${PrefsHelper.getWorkerId(context) ?: "Unknown"}"}"
                    )
                },
                actions = {
                    if (token != null) {
                        IconButton(onClick = { showLogoutDialog = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = "Logout"
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
                                    restoreState = false // Prevent restoring profile
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
                    onLoginSuccess = {
                        Log.d("EmpowerSWR", "onLoginSuccess triggered, navigating to home")
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
            composable("contracts") {
                ContractsScreen(
                    viewModel = viewModel,
                    context = context
                )
            }
            composable("update_details") {
                UpdateDetailsScreen(
                    viewModel = viewModel,
                    context = context
                )
            }
            composable("information") {
                InformationScreen(
                    viewModel = viewModel,
                    context = context
                )
            }
            composable("settings") {
                SettingsScreen()
            }
            composable("documents") { DocumentsScreen(uploadService = NetworkModule.uploadService) }
            composable("documents") {
                DocumentsScreen(uploadService = uploadService)
            }
        }
    }
}