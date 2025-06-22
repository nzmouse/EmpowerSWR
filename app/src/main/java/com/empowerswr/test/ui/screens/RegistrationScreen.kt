package com.empowerswr.test.ui.screens

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.empowerswr.test.EmpowerViewModel
import com.empowerswr.test.PrefsHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistrationScreen(
    viewModel: EmpowerViewModel,
    context: Context,
    navController: NavHostController
) {
    val coroutineScope = rememberCoroutineScope()
    var passport by remember { mutableStateOf("") }
    var surname by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var registerError by remember { mutableStateOf<String?>(null) }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(pin, confirmPin) {
        pinError = when {
            pin != confirmPin -> "PINs do not match"
            pin.length < 4 -> "PIN must be at least 4 digits"
            else -> null
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "Register", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = passport,
            onValueChange = { passport = it },
            label = { Text("Passport Number") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = surname,
            onValueChange = { surname = it },
            label = { Text("Surname") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = { newValue -> pin = newValue.take(4) },
            label = { Text("PIN") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Next
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = confirmPin,
            onValueChange = { newValue -> confirmPin = newValue.take(4) },
            label = { Text("Re-enter PIN") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    keyboardController?.hide()
                    if (pinError == null && pin.isNotEmpty()) {
                        coroutineScope.launch {
                            Log.d("EmpowerSWR", "Keyboard Done: Attempting registration with passport: $passport, surname: $surname, pin: $pin")
                            try {
                                viewModel.register(passport, surname, pin)
                                PrefsHelper.setRegistered(context, true)
                                navController.navigate("login") {
                                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                                }
                            } catch (e: Exception) {
                                registerError = e.message
                                Log.e("EmpowerSWR", "Registration failed: ${e.message}", e)
                            }
                        }
                    }
                }
            )
        )
        pinError?.let { error ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(error, color = MaterialTheme.colorScheme.error)
        }
        registerError?.let { error ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(error, color = MaterialTheme.colorScheme.error)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                Log.d("EmpowerSWR", "Register button clicked")
                keyboardController?.hide()
                if (pinError == null && pin.isNotEmpty()) {
                    coroutineScope.launch {
                        Log.d("EmpowerSWR", "Coroutine launched for registration")
                        try {
                            viewModel.register(passport, surname, pin)
                            PrefsHelper.setRegistered(context, true)
                            navController.navigate("login") {
                                popUpTo(navController.graph.startDestinationId) { inclusive = true }
                            }
                        } catch (e: Exception) {
                            registerError = e.message
                            Log.e("EmpowerSWR", "Registration failed: ${e.message}", e)
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = pinError == null && pin.isNotEmpty()
        ) {
            Text("Register")
        }
    }
}