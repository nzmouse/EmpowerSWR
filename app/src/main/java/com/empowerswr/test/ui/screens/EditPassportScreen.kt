package com.empowerswr.test.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.empowerswr.test.EmpowerViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import android.util.Log
import androidx.navigation.NavOptions
import android.net.Uri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPassportScreen(viewModel: EmpowerViewModel, navController: NavHostController) {
    val workerDetails by viewModel.workerDetails
    val context = LocalContext.current
    var firstName by remember { mutableStateOf(workerDetails?.firstName ?: "") }
    var surname by remember { mutableStateOf(workerDetails?.surname ?: "") }
    var passportNumber by remember { mutableStateOf(workerDetails?.ppno ?: "") }
    var birthPlace by remember { mutableStateOf(workerDetails?.birthplace ?: "") }
    var dateExpiry by remember { mutableStateOf("") }
    var province by remember { mutableStateOf(workerDetails?.birthProvince ?: "") }
    var isSaving by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    // Province options
    val provinceOptions = listOf("TORBA", "SANMA", "PENAMA", "MALAMPA", "SHEFA", "TAFEA")
    var expanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Passport Details") },
                navigationIcon = {
                    IconButton(onClick = {
                        focusManager.clearFocus()
                        navController.popBackStack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            TextField(
                value = firstName,
                onValueChange = { firstName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("First Name") }
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = surname,
                onValueChange = { surname = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Surname") }
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = passportNumber,
                onValueChange = { passportNumber = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Passport Number") }
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = birthPlace,
                onValueChange = { birthPlace = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Birth Place") }
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = dateExpiry,
                onValueChange = { dateExpiry = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Date Expiry (dd MMM YYYY)") }
            )
            Spacer(modifier = Modifier.height(8.dp))
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                TextField(
                    value = province,
                    onValueChange = { },
                    readOnly = true,
                    label = { Text("Province") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    provinceOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                province = option
                                expanded = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    focusManager.clearFocus()
                    if (passportNumber.isBlank() || dateExpiry.isBlank()) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Passport Number and Date Expiry are required")
                        }
                        return@Button
                    }
                    if (!passportNumber.matches(Regex("RV\\d{7}"))) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Passport Number must be 9 characters starting with RV followed by 7 digits")
                        }
                        return@Button
                    }
                    val inputFormat = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
                    inputFormat.isLenient = false
                    val outputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
                    val formattedExpiry = try {
                        val date = inputFormat.parse(dateExpiry)
                        outputFormat.format(date!!)
                    } catch (e: Exception) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Invalid Date Expiry format. Use dd MMM YYYY with valid date")
                        }
                        return@Button
                    }
                    isSaving = true
                    viewModel.updatePassportDetails(firstName, surname, passportNumber, birthPlace, formattedExpiry, province) { success, error ->
                        isSaving = false
                        coroutineScope.launch {
                            if (success) {
                                snackbarHostState.showSnackbar("Updated successfully")
                                navController.previousBackStackEntry?.savedStateHandle?.set("refresh_profile", true)
                                val expiryYear = formattedExpiry.take(4)
                                val yy = expiryYear.takeLast(2)
                                val route = "documents?type=passport&expiryYY=$yy&from=passport"
                                Log.d("EmpowerSWR", "Navigating to $route")
                                Log.d("EmpowerSWR", "Current back stack: ${navController.currentBackStack.value.map { it.destination.route }}")
                                try {
                                    navController.navigate(route) {
                                        launchSingleTop = true
                                    }
                                } catch (e: Exception) {
                                    Log.e("EmpowerSWR", "Navigation failed: ${e.message}", e)
                                    snackbarHostState.showSnackbar("Navigation error: ${e.message}")
                                }
                            } else {
                                snackbarHostState.showSnackbar(error ?: "Update failed")
                            }
                        }
                    }
                },
                enabled = !isSaving
            ) {
                if (isSaving) CircularProgressIndicator(modifier = Modifier.size(24.dp)) else Text("Save")
            }
        }
    }
}