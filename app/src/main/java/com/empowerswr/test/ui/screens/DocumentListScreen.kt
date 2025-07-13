package com.empowerswr.test.ui.screens

import android.app.Activity
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.empowerswr.test.network.Document
import com.empowerswr.test.network.NetworkModule
import com.empowerswr.test.PrefsHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentListScreen(navController: NavController) {
    val context = navController.context
    val scope = rememberCoroutineScope()
    var documents by remember { mutableStateOf<List<Document>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val jwtToken = PrefsHelper.getJwtToken(context)
    val (givenName, surname) = PrefsHelper.getWorkerDetails(context)
    val workerName = "$givenName $surname"
    val service = NetworkModule.provideDownloadService(context)

    LaunchedEffect(Unit) {
        if (jwtToken.isEmpty() || workerName == "Unknown Unknown") {
            Log.e("DocumentList", "Invalid JWT or worker name")
            return@LaunchedEffect
        }
        scope.launch {
            try {
                documents = service.getDocuments("Bearer $jwtToken", workerName)
                Log.d("DocumentList", "Fetched documents: $documents")
            } catch (e: Exception) {
                Log.e("DocumentList", "Error fetching documents", e)
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("EmpowerSWR Documents") },
                navigationIcon = {
                    IconButton(onClick = { (context as? Activity)?.finish() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (documents.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No documents found for $workerName")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
            ) {
                items(documents) { document ->
                    DocumentItem(document) {
                        navController.navigate("document_viewer/${document.name}")
                    }
                }
            }
        }
    }
}

@Composable
fun DocumentItem(document: Document, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.PictureAsPdf, // Use a PDF icon
                contentDescription = "PDF Icon",
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = document.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { onClick() }) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = "View/Sign"
                )
            }
        }
    }
}