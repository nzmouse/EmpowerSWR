package com.empowerswr.luksave.ui.screens

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LocationToggle(
    context: Context,
    sharedPreferences: SharedPreferences,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    var isDeviceEnabled by remember { mutableStateOf(false) }
    var isPermissionGranted by remember { mutableStateOf(false) }
    var isPrefEnabled by rememberSaveable { mutableStateOf(false) }

    fun updateState() {
        isDeviceEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
        isPermissionGranted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        isPrefEnabled = sharedPreferences.getBoolean("location_enabled", false)
    }

    val effectiveEnabled = isDeviceEnabled && isPermissionGranted && isPrefEnabled

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        updateState()
        coroutineScope.launch {
            snackbarHostState.showSnackbar(
                if (granted) "Location permission granted" else "Location permission denied"
            )
        }
    }

    // Initialize state
    LaunchedEffect(Unit) {
        updateState()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Enable Location Services",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = effectiveEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        sharedPreferences.edit { putBoolean("location_enabled", true) }
                        updateState()
                        when {
                            !isDeviceEnabled -> {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Please enable Location Services")
                                }
                                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                            }
                            !isPermissionGranted -> {
                                permissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
                            }
                            else -> {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Location enabled")
                                }
                            }
                        }
                    } else {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Disable location in system settings.  Wait while I open App Info")
                            delay(1000)
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = "package:${context.packageName}".toUri()
                                }
                            )
                        }
                    }
                },
                modifier = Modifier.padding(end = 8.dp)
            )
        }


    }

    // Keep state synced when returning from system settings
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            updateState()
        }
    }
}

@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("LuksavePrefs", Context.MODE_PRIVATE)
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Location toggle
        LocationToggle(
            context = context,
            sharedPreferences = sharedPreferences,
            snackbarHostState = snackbarHostState
        )
        Text(
            text = "Location is used for features like finding nearby services (e.g., churches, supermarkets).",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )

        // Snackbar host
        SnackbarHost(hostState = snackbarHostState)



        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "About",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Privacy Policy link
        val annotatedText = buildAnnotatedString {
            append("Review our ")
            val policyText = "Privacy Policy"
            val start = length
            withStyle(
                style = SpanStyle(
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline
                )
            ) {
                append(policyText)
            }
            addStringAnnotation(
                tag = "URL",
                annotation = "https://db.nougro.com/privacy-policy.html",
                start = start,
                end = start + policyText.length
            )
            append(" for details on how we handle your data.")
        }
        Text(
            text = annotatedText,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clickable {
                    val annotation = annotatedText.getStringAnnotations(tag = "URL", start = 0, end = annotatedText.length)
                        .firstOrNull()
                    annotation?.let {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, it.item.toUri())
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Unable to open link: ${e.message}")
                            }
                        }
                    }
                }
        )



        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable {
                    val intent = Intent(Intent.ACTION_VIEW, "https://github.com/nzmouse/EmpowerSWR".toUri())
                    context.startActivity(intent)
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Code,
                    contentDescription = "Source Code",
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Source Code",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable {
                    val intent = Intent(Intent.ACTION_VIEW, "https://github.com/nzmouse/EmpowerSWR/blob/main/LICENSE.txt".toUri())
                    context.startActivity(intent)
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Description,
                    contentDescription = "License",
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "License",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable {
                    val intent = Intent(Intent.ACTION_VIEW, "https://db.nougro.com/api.terms.php".toUri())
                    context.startActivity(intent)
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = "Terms of Use",
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Terms of Use",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "© 2025 EmpowerSWR. All rights reserved.\n" +
                    "Luksave App, Version 1.0.0 (Awo)\n" +
                    "This application is open source under the GNU AGPLv3 license due to the use of iText. " +
                    "Luksave is the first recruiting app of its kind in the South West Pacific. " +
                    "Unauthorized reproduction or distribution is prohibited.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { navController.popBackStack() }) {
            Text("Back")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    MaterialTheme {
        SettingsScreen(navController = rememberNavController())
    }
}
