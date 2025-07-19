package com.empowerswr.test.ui.screens

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.empowerswr.test.network.Document
import com.empowerswr.test.network.NetworkModule
import com.empowerswr.test.PrefsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DocumentListScreen(
    navController: NavController,
    context: Context
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var documents by remember { mutableStateOf<List<Document>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val jwtToken = PrefsHelper.getJwtToken(context)
    val (givenName, surname) = PrefsHelper.getWorkerDetails(context)
    val workerName = "$givenName $surname"
    val service = NetworkModule.provideDownloadService(context)

    // Track active downloads
    val activeDownloads = remember { mutableStateMapOf<Long, String>() }

    // BroadcastReceiver for download completion
    val downloadReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                Log.d("DocumentList", "Broadcast received: ${intent.action}")
                val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                Log.d("DocumentList", "Download ID received: $downloadId")
                val documentName = activeDownloads[downloadId] ?: run {
                    Log.w("DocumentList", "No document name found for download ID: $downloadId")
                    return
                }
                val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor: Cursor? = downloadManager.query(query)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        val reason = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        Log.d("DocumentList", "Download status for $documentName: $status, reason: $reason")
                        when (status) {
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Downloaded $documentName to Downloads folder")
                                }
                                Log.d("DocumentList", "Download completed for $documentName")
                            }
                            DownloadManager.STATUS_FAILED -> {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Download failed for $documentName: Error code $reason")
                                }
                                Log.e("DocumentList", "Download failed for $documentName: Error code $reason")
                            }
                            else -> {
                                Log.d("DocumentList", "Download in progress or paused for $documentName: Status $status")
                            }
                        }
                    } else {
                        Log.w("DocumentList", "No cursor data for download ID: $downloadId")
                    }
                }
                activeDownloads.remove(downloadId)
            }
        }
    }

    // Register BroadcastReceiver
    DisposableEffect(Unit) {
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        ContextCompat.registerReceiver(
            context,
            downloadReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose {
            context.unregisterReceiver(downloadReceiver)
            Log.d("DocumentList", "Unregistered download receiver")
        }
    }

    // Fallback to check download status
    LaunchedEffect(activeDownloads.size) {
        while (activeDownloads.isNotEmpty()) {
            delay(5000) // Check every 5 seconds
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            activeDownloads.toMap().forEach { (downloadId, documentName) ->
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor: Cursor? = downloadManager.query(query)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        val reason = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        when (status) {
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Downloaded $documentName to Downloads folder")
                                }
                                Log.d("DocumentList", "Fallback: Download completed for $documentName")
                                activeDownloads.remove(downloadId)
                            }
                            DownloadManager.STATUS_FAILED -> {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Download failed for $documentName: Error code $reason")
                                }
                                Log.e("DocumentList", "Fallback: Download failed for $documentName: Error code $reason")
                                activeDownloads.remove(downloadId)
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (jwtToken.isEmpty() || workerName == "Unknown Unknown") {
            Log.e("DocumentList", "Invalid JWT or worker name")
            Toast.makeText(context, "Invalid session, please log in", Toast.LENGTH_SHORT).show()
            return@LaunchedEffect
        }
        scope.launch {
            try {
                documents = service.getDocuments("Bearer $jwtToken", workerName)
                Log.d("DocumentList", "Fetched documents: $documents")
            } catch (e: Exception) {
                Log.e("DocumentList", "Error fetching documents: ${e.message}", e)
                Toast.makeText(context, "Failed to load documents: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility), // Ignore status bar
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Ol Dokumen Blong Yu",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        Log.d("DocumentList", "Back button clicked")
                        navController.popBackStack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .height(48.dp)
                    .padding(0.dp)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
                    .padding(padding),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(documents) { document ->
                    DocumentItem(
                        document = document,
                        onViewClick = {
                            Log.d("DocumentList", "View clicked: ${document.name}")
                            try {
                                navController.navigate("document-viewer/${document.name}")
                            } catch (e: IllegalArgumentException) {
                                Log.e("DocumentList", "Navigation failed: Invalid destination document-viewer/${document.name}", e)
                                Toast.makeText(context, "Error: Invalid navigation destination", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Log.e("DocumentList", "Navigation failed: ${e.message}", e)
                                Toast.makeText(context, "Navigation error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onSignClick = {
                            Log.d("DocumentList", "Sign clicked: ${document.name}")
                            try {
                                navController.navigate("document-signer/${document.name}")
                            } catch (e: IllegalArgumentException) {
                                Log.e("DocumentList", "Navigation failed: Invalid destination document-signer/${document.name}", e)
                                Toast.makeText(context, "Error: Invalid navigation destination", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Log.e("DocumentList", "Navigation failed: ${e.message}", e)
                                Toast.makeText(context, "Navigation error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDownloadClick = {
                            Log.d("DocumentList", "Download clicked: ${document.name}")
                            val downloadId = downloadFile(context, document, jwtToken)
                            if (downloadId != -1L) {
                                activeDownloads[downloadId] = document.name
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DocumentItem(
    document: Document,
    onViewClick: () -> Unit,
    onSignClick: () -> Unit,
    onDownloadClick: () -> Unit
) {
    // Map document suffix to type
    val documentTypes = listOf(
        "Passport" to "PPT",
        "DFAT Privacy/Consent" to "PRI",
        "National ID Card" to "NID",
        "Birth Certificate" to "BC",
        "Driving Licence" to "DL",
        "Police Clearance" to "PC",
        "Medical" to "MED",
        "Contract" to "LOO",
        "Spouse Letter" to "SPO",
        "Chief/Pastor Letter" to "REF"
    )
    val suffix = document.name.substringBeforeLast(".pdf", "")
        .substringAfterLast("-", "").trim().uppercase()
    Log.d("DocumentList", "Extracted suffix for ${document.name}: $suffix")
    val documentType = documentTypes.firstOrNull {
        it.second.equals(suffix, ignoreCase = true)
    }?.first ?: document.name // Fallback to full name if no match

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.PictureAsPdf,
                contentDescription = "PDF Icon",
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = documentType,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onViewClick) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = "View ${document.name}"
                )
            }
            // Commented out to hide Sign button until functionality is fixed
            /*
            IconButton(onClick = onSignClick) {
                Icon(
                    imageVector = Icons.Default.Create,
                    contentDescription = "Sign ${document.name}",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            */
            IconButton(onClick = onDownloadClick) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Download ${document.name}",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun downloadFile(context: Context, document: Document, jwtToken: String): Long {
    try {
        val encodedFilename = document.name.replace(" ", "%20")
        val fileUrl = "https://db.nougro.com/api/download.php?filename=$encodedFilename"
        Log.d("DocumentList", "Attempting to download from: $fileUrl")
        val request = DownloadManager.Request(Uri.parse(fileUrl))
            .setTitle(document.name)
            .setDescription("Downloading ${document.name}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, document.name)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
        request.addRequestHeader("Authorization", "Bearer $jwtToken")
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)
        Toast.makeText(context, "Downloading ${document.name}", Toast.LENGTH_SHORT).show()
        Log.d("DocumentList", "Download started for ${document.name} with ID: $downloadId")
        return downloadId
    } catch (e: Exception) {
        Log.e("DocumentList", "Download failed: ${e.message}", e)
        Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
        return -1L
    }
}