package com.empowerswr.test.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.empowerswr.test.EmpowerViewModel
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPersonalScreen(viewModel: EmpowerViewModel, navController: NavHostController) {
    val workerDetails by viewModel.workerDetails
    var preferredName by remember { mutableStateOf(workerDetails?.prefName ?: "") }
    var isSaving by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Personal Information") },
                navigationIcon = {
                    IconButton(onClick = {
                        focusManager.clearFocus()  // Dismiss keyboard on back
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top  // Stack naturally
        ) {
            Text("Preferred Name", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = preferredName,
                onValueChange = { preferredName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Enter preferred name") }
            )
            Spacer(modifier = Modifier.height(16.dp))  // Space between field and button
            Button(
                onClick = {
                    focusManager.clearFocus()  // Dismiss keyboard on submit
                    if (preferredName.isNotBlank()) {
                        isSaving = true
                        viewModel.updatePreferredName(preferredName) { success, error ->
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
                            snackbarHostState.showSnackbar("Preferred name cannot be empty")
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