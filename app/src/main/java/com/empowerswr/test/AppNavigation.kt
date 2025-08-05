                    package com.empowerswr.test

                    import android.util.Log
                    import androidx.compose.runtime.Composable
                    import androidx.navigation.compose.NavHost
                    import androidx.navigation.compose.composable
                    import androidx.navigation.compose.rememberNavController
                    import androidx.compose.ui.platform.LocalContext
                    import androidx.navigation.NavType
                    import androidx.navigation.navArgument
                    import com.empowerswr.test.network.NetworkModule
                    import com.empowerswr.test.ui.screens.DocumentsScreen
                    import com.empowerswr.test.ui.screens.EditContactScreen
                    import com.empowerswr.test.ui.screens.EditPassportScreen
                    import com.empowerswr.test.ui.screens.EditPersonalScreen
                    import com.empowerswr.test.ui.screens.FlightsScreen
                    import com.empowerswr.test.ui.screens.HomeScreen
                    import com.empowerswr.test.ui.screens.InformationScreen
                    import com.empowerswr.test.ui.screens.LoginScreen
                    import com.empowerswr.test.ui.screens.RegistrationScreen
                    import com.empowerswr.test.ui.screens.SettingsScreen
                    import com.empowerswr.test.ui.screens.WorkerDetailsScreen

                    @Composable
                    fun AppNavigation(viewModel: EmpowerViewModel) {
                        val navController = rememberNavController()
                        val context = LocalContext.current
                        Log.d("EmpowerSWR", "AppNavigation NavController hash: ${navController.hashCode()}")
                        NavHost(navController, startDestination = "login") {
                            composable("edit_passport") {
                                EditPassportScreen(viewModel = viewModel, navController = navController)
                            }
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
                                Log.d("EmpowerSWR", "Back stack entry destination route: ${backStackEntry.destination.route}")
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