package com.empowerswr.luksave.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.empowerswr.luksave.EmpowerViewModel
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPersonalScreen(viewModel: EmpowerViewModel, navController: NavHostController) {
    val workerDetails by viewModel.workerDetails
    var preferredName by remember { mutableStateOf(workerDetails?.prefName ?: "") }
    var isSaving by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Personal Information") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Text("Preferred Name", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = preferredName,
                onValueChange = { preferredName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Enter preferred name") }
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    focusManager.clearFocus()
                    if (preferredName.isNotBlank()) {
                        isSaving = true
                        viewModel.updatePreferredName(preferredName, context) { isSuccess, error ->
                            isSaving = false
                            coroutineScope.launch {
                                if (isSuccess) {
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
                enabled = !isSaving,
                content = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                        Text("Save")
                    }
                }
            )
        }
    }
}