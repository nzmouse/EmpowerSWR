package com.empowerswr.luksave.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.empowerswr.luksave.EmpowerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotCredentialsScreen(
    viewModel: EmpowerViewModel,
    navController: NavController
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var documentValue by remember { mutableStateOf("") }
    var motherName by remember { mutableStateOf("") }

    var isVerifying by remember { mutableStateOf(false) }
    var showResultDialog by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }

    var verifiedWorkerId by remember { mutableStateOf<Int?>(null) }
    var verifiedUsername by remember { mutableStateOf<String?>(null) }

    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    // Auto-detect type
    val detectedType = when {
        documentValue.startsWith("RV", ignoreCase = true) -> "passport"
        documentValue.all { it.isDigit() } && documentValue.length >= 4 -> "nid"
        else -> ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fogetem PIN o Username") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Text("←")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Enter Passport o NID mo nem blong mama blong yu",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = documentValue,
                onValueChange = {
                    documentValue = it.filter { c -> c.isLetterOrDigit() }.uppercase()
                },
                label = { Text("Passport o National ID Number") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "What is your mother’s first name?",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            OutlinedTextField(
                value = motherName,
                onValueChange = { motherName = it.trim() },
                label = { Text("Answer") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (documentValue.length < 4 || motherName.length < 2) {
                        Toast.makeText(context, "Plis filim olgeta field", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    isVerifying = true

                    viewModel.verifyForReset(detectedType, documentValue, motherName) { success, workerId, username ->
                        isVerifying = false
                        if (success && workerId != null) {
                            verifiedWorkerId = workerId
                            verifiedUsername = username
                            showResultDialog = true
                        } else {
                            Toast.makeText(context, "Invalid details. Please try again.", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isVerifying && detectedType.isNotEmpty()
            ) {
                if (isVerifying) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Text("Verify")
                }
            }
        }
    }

    // Result Dialog - Shows masked username (your preferred text)
    if (showResultDialog) {
        AlertDialog(
            onDismissRequest = { showResultDialog = false },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Username",
                        style = MaterialTheme.typography.bodyLarge
                    )

                    val maskedUsername = verifiedUsername?.let { username ->
                        if (username.length > 2) {
                            username.take(username.length - 2) + "**"
                        } else {
                            username
                        }
                    } ?: "Unknown"

                    Text(
                        text = maskedUsername,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Yu wantem resetem PIN blong yu?")
                }
            },
            confirmButton = {
                Button(onClick = {
                    showResultDialog = false
                    showPinDialog = true
                    newPin = ""
                    confirmPin = ""
                    pinError = null
                }) {
                    Text("Reset PIN")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showResultDialog = false
                    navController.popBackStack()
                }) {
                    Text("Back to Login")
                }
            }
        )
    }

    // PIN Reset Dialog
    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("Set New PIN") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) newPin = it },
                        label = { Text("New PIN (4-6 digits)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) confirmPin = it },
                        label = { Text("Confirm New PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                    )

                    pinError?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPin.length < 4 || newPin.length > 6) {
                            pinError = "PIN must be 4-6 digits"
                            return@Button
                        }
                        if (newPin != confirmPin) {
                            pinError = "PINs do not match"
                            return@Button
                        }

                        verifiedWorkerId?.let { wid ->
                            viewModel.resetPin(wid, newPin) { success, message ->
                                if (success) {
                                    Toast.makeText(context, "PIN i reset finis!\nYu save login wetem niu PIN nao.", Toast.LENGTH_LONG).show()
                                    showPinDialog = false
                                    navController.popBackStack()
                                } else {
                                    pinError = message ?: "Failed to reset PIN"
                                }
                            }
                        }
                    }
                ) {
                    Text("Save New PIN")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}