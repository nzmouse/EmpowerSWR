package com.empowerswr.luksave.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import android.Manifest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    // Load location preference from SharedPreferences
    val sharedPreferences = context.getSharedPreferences("LuksavePrefs", Context.MODE_PRIVATE)
    var isLocationEnabled by remember {
        mutableStateOf(sharedPreferences.getBoolean("location_enabled", true))
    }

    // Permission launcher for location
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            sharedPreferences.edit().putBoolean("location_enabled", true).apply()
            isLocationEnabled = true
        } else {
            sharedPreferences.edit().putBoolean("location_enabled", false).apply()
            isLocationEnabled = false
            Toast.makeText(context, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Enable Location Services",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = isLocationEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        // Request location permission
                        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    } else {
                        // Disable location
                        sharedPreferences.edit().putBoolean("location_enabled", false).apply()
                        isLocationEnabled = false
                    }
                }
            )
        }
        Text(
            text = "Location is used for features like finding nearby services (e.g., churches, supermarkets).",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
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
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(it.item))
                            ContextCompat.startActivity(context, intent, null)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Unable to open link: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
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