package com.empowerswr.test.ui.screens

import android.content.Context
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.empowerswr.test.EmpowerViewModel

// Home Screen
// Displays welcome message (placeholder)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: EmpowerViewModel,
    context: Context
) {
    Text("Home Screen - Welcome!")
}