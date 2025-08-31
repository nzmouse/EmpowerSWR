package com.empowerswr.luksave

import android.app.Activity
import android.app.DownloadManager
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.empowerswr.luksave.network.NetworkModule
import com.empowerswr.luksave.ui.screens.*
import com.empowerswr.luksave.ui.theme.EmpowerSWRTheme
import kotlinx.coroutines.delay
import timber.log.Timber
import java.io.File

class MainActivity : ComponentActivity() {
    private val downloadIds = mutableMapOf<Long, String>()
    val downloadState = mutableStateOf<DownloadState>(DownloadState.Idle)

    fun storeDownload(downloadId: Long, filename: String) {
        downloadIds[downloadId] = filename
    }

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) ?: -1
            val filename = downloadIds[id] ?: return
            val downloadManager = context?.getSystemService(DOWNLOAD_SERVICE) as? DownloadManager
            val query = DownloadManager.Query().setFilterById(id)
            downloadManager?.query(query)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        val localUri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                        val file = if (localUri != null) File(localUri.toUri().path ?: return@use) else null
                        val normalizedFilename = filename.replace("+", " ").replace("%20", " ").trim()
                        val foundFile = file?.takeIf { it.exists() } ?: File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), normalizedFilename)
                        if (foundFile.exists()) {
                            downloadState.value = DownloadState.Completed(normalizedFilename)
                        } else {
                            downloadState.value = DownloadState.Failed(normalizedFilename, "File not found")
                            Timber.e("Downloaded file not found: ${foundFile.absolutePath}")
                        }
                    } else {
                        val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        downloadState.value = DownloadState.Failed(filename, "Status $status, Reason $reason")
                        Timber.e("Download failed with status: $status, reason: $reason")
                    }
                } else {
                    downloadState.value = DownloadState.Failed(filename, "Download query failed")
                    Timber.e("Download query returned empty cursor")
                }
            } ?: run {
                downloadState.value = DownloadState.Failed(filename, "Download manager unavailable")
                Timber.e("Download manager is null")
            }
            downloadIds.remove(id)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val filter = IntentFilter().apply {
            addAction(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            addAction(DownloadManager.ACTION_NOTIFICATION_CLICKED)
            addAction(DownloadManager.ACTION_VIEW_DOWNLOADS)
        }
        ContextCompat.registerReceiver(
            this,
            downloadReceiver,
            filter,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ContextCompat.RECEIVER_NOT_EXPORTED else ContextCompat.RECEIVER_EXPORTED
        )
        setContent {
            EmpowerSWRTheme {
                NavigationSetup()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(downloadReceiver)
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "Receiver not registered")
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

// Helper to find Activity context
fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("UNCHECKED_CAST")
fun NavigationSetup() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val viewModel: EmpowerViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return EmpowerViewModel(context.applicationContext as Application) as T
        }
    })
    val token by viewModel.token
    val workerDetails by viewModel.workerDetails
    val currentRoute by navController.currentBackStackEntryAsState()
    val currentDestination = currentRoute?.destination?.route
    var showLogoutDialog by remember { mutableStateOf(false) }
    var initialNavigationDone by rememberSaveable { mutableStateOf(false) }
    val downloadState = (context.findActivity() as? MainActivity)?.downloadState ?: remember { mutableStateOf<DownloadState>(DownloadState.Idle) }

    LaunchedEffect(token, currentDestination) {
        if (token != null && !initialNavigationDone && (currentDestination == "login" || currentDestination == null)) {
            delay(500)
            navController.navigate("home") {
                popUpTo(navController.graph.startDestinationId) { inclusive = true }
                launchSingleTop = true
            }
            initialNavigationDone = true
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
                    initialNavigationDone = false
                    navController.navigate("login") {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                        launchSingleTop = true
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
                        IconButton(onClick = {
                            navController.navigate("settings") {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = false
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Open Settings",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
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
                            selected = currentDestination == item.route,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
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
                    navController = navController
                )
            }
            composable("login") {
                LoginScreen(
                    viewModel = viewModel,
                    context = context,
                    navController = navController,
                    onLoginSuccess = {
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
                    downloadState = downloadState
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
        }
    }
}