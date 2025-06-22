package com.empowerswr.test

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import com.empowerswr.test.ui.screens.LoginScreen
import com.empowerswr.test.ui.screens.WorkerDetailsScreen
import com.empowerswr.test.ui.screens.HomeScreen
import com.empowerswr.test.ui.screens.RegistrationScreen
import com.empowerswr.test.ui.theme.EmpowerSWRTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Log.d("EmpowerSWR", "setContent called")
            EmpowerSWRTheme {
                Log.d("EmpowerSWR", "EmpowerSWRTheme applied")
                val viewModel: EmpowerViewModel = viewModel()
                val savedToken = PrefsHelper.getToken(this)
                val savedTokenExpiry = PrefsHelper.getTokenExpiry(this)
                Log.d("EmpowerSWR", "onCreate - Saved token: $savedToken, expiry: $savedTokenExpiry")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val permission = android.Manifest.permission.POST_NOTIFICATIONS
                    if (checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(arrayOf(permission), 100)
                    }
                }

                if (savedToken != null && savedTokenExpiry != null) {
                    val currentTime = System.currentTimeMillis() / 1000
                    if (savedTokenExpiry > currentTime) {
                        viewModel.setToken(savedToken)
                        Log.d("EmpowerSWR", "onCreate - Restored valid token: $savedToken")
                    } else {
                        Log.d("EmpowerSWR", "onCreate - Saved token expired, clearing")
                        PrefsHelper.clearToken(this)
                        viewModel.setToken(null)
                    }
                } else if (savedToken != null) {
                    Log.d("EmpowerSWR", "onCreate - Saved token has no expiry, clearing")
                    PrefsHelper.clearToken(this)
                    viewModel.setToken(null)
                }

                AppNavigation(
                    viewModel = viewModel,
                    context = this@MainActivity
                )
            }
        }
    }
}

data class NavItem(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val route: String)

val navItems = listOf(
    NavItem("Home", Icons.Filled.Home, "home"),
    NavItem("Profile", Icons.Filled.Person, "profile/{workerId}"),
    NavItem("Work Location", Icons.Filled.LocationOn, "work_location"),
    NavItem("Contracts", Icons.Filled.Description, "contracts"),
    NavItem("Update Details", Icons.Filled.Edit, "update_details"),
    NavItem("Information", Icons.Filled.Info, "information"),
    NavItem("Settings", Icons.Filled.Settings, "settings")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(viewModel: EmpowerViewModel, context: Context) {
    Log.d("EmpowerSWR", "AppNavigation started")
    val navController = rememberNavController()
    val token by viewModel.token
    val currentRoute by navController.currentBackStackEntryAsState()
    val currentDestination = currentRoute?.destination?.route
    var errorMessage by remember { mutableStateOf<String?>(null) }

    if (errorMessage != null) {
        Text(
            text = "Error: $errorMessage",
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(16.dp)
        )
        return
    }

    Scaffold(
        topBar = {
            Log.d("EmpowerSWR", "Rendering TopAppBar")
            TopAppBar(
                title = {
                    Text(
                        if (token == null) "Empower SWR - Login" else "Logged in"
                    )
                }
            )
        }
    ) { innerPadding ->
        Log.d("EmpowerSWR", "NavHost initializing")
        NavHost(
            navController = navController,
            startDestination = if (PrefsHelper.hasRegistered(context)) "login" else "registration",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("registration") {
                Log.d("EmpowerSWR", "Navigating to RegistrationScreen")
                RegistrationScreen(viewModel = viewModel, context = context, navController = navController)
            }
            composable("login") {
                Log.d("EmpowerSWR", "Navigating to LoginScreen")
                LoginScreen(viewModel = viewModel, context = context, navController = navController)
            }
            composable("home") {
                Log.d("EmpowerSWR", "Navigating to HomeScreen")
                HomeScreen(navController = navController, viewModel = viewModel, context = context)
            }
            composable(
                route = "profile/{workerId}",
                arguments = listOf(navArgument("workerId") { type = NavType.StringType })
            ) { backStackEntry ->
                val workerId = backStackEntry.arguments?.getString("workerId") ?: ""
                Log.d("EmpowerSWR", "Navigated to profile with workerId: $workerId")
                if (workerId.isNotEmpty()) {
                    WorkerDetailsScreen(
                        navController = navController,
                        viewModel = viewModel,
                        workerId = workerId,
                        context = context
                    )
                } else {
                    Text("Invalid Worker ID", modifier = Modifier.padding(16.dp))
                    Log.e("EmpowerSWR", "Invalid workerId: empty")
                }
            }
            composable("work_location") {
                Log.d("EmpowerSWR", "Navigating to WorkLocationScreen")
                Text("Work Location Screen", modifier = Modifier.padding(16.dp))
            }
            composable("contracts") {
                Log.d("EmpowerSWR", "Navigating to ContractsScreen")
                Text("Contracts Screen", modifier = Modifier.padding(16.dp))
            }
            composable("update_details") {
                Log.d("EmpowerSWR", "Navigating to UpdateDetailsScreen")
                Text("Update Details Screen", modifier = Modifier.padding(16.dp))
            }
            composable("information") {
                Log.d("EmpowerSWR", "Navigating to InformationScreen")
                Text("Information Screen", modifier = Modifier.padding(16.dp))
            }
            composable("settings") {
                Log.d("EmpowerSWR", "Navigating to SettingsScreen")
                Text("Settings Screen", modifier = Modifier.padding(16.dp))
            }
        }
    }
}