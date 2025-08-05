package com.empowerswr.test.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.empowerswr.test.EmpowerViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditContactScreen(viewModel: EmpowerViewModel, navController: NavHostController) {
    val workerDetails by viewModel.workerDetails
    var primaryPhone by remember { mutableStateOf(workerDetails?.phone ?: "") }
    var secondaryPhone by remember { mutableStateOf(workerDetails?.phone2 ?: "") }
    var aunzPhone by remember { mutableStateOf(workerDetails?.aunzPhone ?: "") }
    var email by remember { mutableStateOf(workerDetails?.email ?: "") }
    var isSaving by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Contact Information") },
                navigationIcon = {
                    IconButton(onClick = {
                        focusManager.clearFocus()  // Dismiss keyboard on back
                        navController.popBackStack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(scrollState)
                    .imePadding(),  // Push content above keyboard
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top  // Stack naturally
            ) {
                Text("Primary Phone", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = primaryPhone,
                    onValueChange = { primaryPhone = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Enter primary phone") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Secondary Phone", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = secondaryPhone,
                    onValueChange = { secondaryPhone = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Enter secondary phone") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("AU/NZ Phone", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = aunzPhone,
                    onValueChange = { aunzPhone = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Enter AU/NZ phone") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Email", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Enter email address") }
                )
                Spacer(modifier = Modifier.height(16.dp))  // Space before button
                Button(
                    onClick = {
                        focusManager.clearFocus()  // Dismiss keyboard on submit
                        if (primaryPhone.isNotBlank()) {
                            isSaving = true
                            viewModel.updateContactInfo(primaryPhone, secondaryPhone, aunzPhone, email) { success, error ->
                                isSaving = false
                                coroutineScope.launch {
                                    if (success) {
                                        snackbarHostState.showSnackbar("Submitted for review")
                                        navController.previousBackStackEntry?.savedStateHandle?.set("refresh_profile", true)
                                        navController.popBackStack()
                                    } else {
                                        snackbarHostState.showSnackbar(error ?: "Submit failed")
                                    }
                                }
                            }
                        } else {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Primary phone cannot be empty")
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
}