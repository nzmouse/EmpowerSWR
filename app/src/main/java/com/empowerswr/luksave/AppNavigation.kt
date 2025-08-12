package com.empowerswr.luksave

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.platform.LocalContext
import com.empowerswr.luksave.network.NetworkModule
import com.empowerswr.luksave.ui.screens.DocumentViewerScreen
import com.empowerswr.luksave.ui.screens.DocumentsScreen
import com.empowerswr.luksave.ui.screens.EditPassportScreen
import com.empowerswr.luksave.ui.screens.FlightsScreen
import com.empowerswr.luksave.ui.screens.HomeScreen
import com.empowerswr.luksave.ui.screens.LoginScreen
import com.empowerswr.luksave.ui.screens.RegistrationScreen
import com.empowerswr.luksave.ui.screens.SettingsScreen
import com.empowerswr.luksave.ui.screens.WorkerDetailsScreen

@Composable
fun AppNavigation(viewModel: EmpowerViewModel) {
    val navController = rememberNavController()
    val context = LocalContext.current
    Log.d("EmpowerSWR", "AppNavigation NavController hash: ${navController.hashCode()}")
    NavHost(navController, startDestination = "login") {
        composable("login") {
            LoginScreen(
                viewModel = viewModel,
                context = LocalContext.current,
                navController = navController,
                onLoginSuccess = { navController.navigate("home") { popUpTo("login") { inclusive = true } } }
            )
        }
        composable("registration") {
            RegistrationScreen(
                viewModel = viewModel,
                context = LocalContext.current,
                navController = navController
            )
        }
        composable("flights") {
            FlightsScreen(
                viewModel = viewModel,
                context = LocalContext.current,
                navController = navController
            )
        }
        composable("workerDetails") {
            WorkerDetailsScreen(
                viewModel = viewModel,
                context = context,
                navController = navController
            )
        }
        composable("home") {
            HomeScreen(viewModel = viewModel, context = context)
        }
        composable("settings") {
            SettingsScreen(navController = navController)
        }
        composable("edit_passport") {
            Log.d("EmpowerSWR", "Navigated to edit_passport")
            EditPassportScreen(viewModel = viewModel, navController = navController)
        }
        composable("documents") {
            DocumentsScreen(
                uploadService = NetworkModule.uploadService,
                listFilesService = NetworkModule.listFilesService,
                navController = navController
            )
        }
        composable("documentViewer/{filename}/{url}") { backStackEntry ->
            val filename = backStackEntry.arguments?.getString("filename") ?: ""
            val url = backStackEntry.arguments?.getString("url") ?: ""
            DocumentViewerScreen(
                navController = navController,
                filename = filename,
                url = url
            )
        }
    }
}