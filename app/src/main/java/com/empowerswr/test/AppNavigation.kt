package com.empowerswr.test

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.platform.LocalContext
import com.empowerswr.test.ui.screens.HomeScreen
import com.empowerswr.test.ui.screens.LoginScreen
import com.empowerswr.test.ui.screens.RegistrationScreen
import com.empowerswr.test.ui.screens.WorkerDetailsScreen

@Composable
fun AppNavigation(viewModel: EmpowerViewModel) {
    val navController = rememberNavController()
    val context = LocalContext.current
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
    }
}