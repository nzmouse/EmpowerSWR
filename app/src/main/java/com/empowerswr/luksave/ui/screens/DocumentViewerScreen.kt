package com.empowerswr.luksave.ui.screens

import android.Manifest
import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.empowerswr.luksave.PrefsHelper
import com.github.barteksc.pdfviewer.PDFView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.URLDecoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentViewerScreen(
    navController: NavController,
    filename: String,
    url: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var pdfFile by remember { mutableStateOf<File?>(null) }
    var currentPage by remember { mutableStateOf(1) }
    var totalPages by remember { mutableStateOf(1) }
    val decodedFilename = URLDecoder.decode(filename, "UTF-8")

    // Permission handling for storage (if needed)
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            scope.launch {
                snackbarHostState.showSnackbar("Storage permission denied")
            }
            Log.e("DocumentViewer", "Storage permission denied")
        }
    }

    // Check storage permission (for API < 29)
    val hasStoragePermission = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true // MediaStore handles permissions on API 29+
    }

    // Register DownloadManager receiver
    var downloadId by remember { mutableStateOf<Long?>(null) }
    val downloadReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) ?: -1
                if (id == downloadId) {
                    val downloadManager = context?.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                    val query = DownloadManager.Query().setFilterById(id)
                    downloadManager?.query(query)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                val localUri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                                val file = File(Uri.parse(localUri).path ?: return@use)
                                if (file.exists()) {
                                    scope.launch(Dispatchers.Main) {
                                        pdfFile = file
                                        Log.d("DocumentViewer", "File downloaded to: ${file.absolutePath}, size: ${file.length()} bytes")
                                    }
                                } else {
                                    scope.launch(Dispatchers.Main) {
                                        snackbarHostState.showSnackbar("Downloaded file not found")
                                        Log.e("DocumentViewer", "Downloaded file not found: ${file.absolutePath}")
                                    }
                                }
                            } else {
                                val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                                scope.launch(Dispatchers.Main) {
                                    snackbarHostState.showSnackbar("Download failed: Status $status, Reason $reason")
                                    Log.e("DocumentViewer", "Download failed with status: $status, reason: $reason")
                                }
                            }
                        } else {
                            scope.launch(Dispatchers.Main) {
                                snackbarHostState.showSnackbar("Download query failed")
                                Log.e("DocumentViewer", "Download query returned empty cursor")
                            }
                        }
                    } ?: run {
                        scope.launch(Dispatchers.Main) {
                            snackbarHostState.showSnackbar("Download manager unavailable")
                            Log.e("DocumentViewer", "Download manager is null")
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(context) {
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        ContextCompat.registerReceiver(
            context,
            downloadReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose {
            context.unregisterReceiver(downloadReceiver)
        }
    }

    LaunchedEffect(url) {
        if (PrefsHelper.getJwtToken(context).isEmpty()) {
            Log.e("DocumentViewer", "JWT token is empty")
            Toast.makeText(context, "Invalid session, please log in", Toast.LENGTH_SHORT).show()
            (context as? Activity)?.finish()
            return@LaunchedEffect
        }
        if (!hasStoragePermission && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return@LaunchedEffect
        }
        scope.launch(Dispatchers.IO) {
            try {
                Log.d("DocumentViewer", "Starting download for URL: $url")
                val file = File(context.cacheDir, decodedFilename)
                if (file.exists()) {
                    Log.d("DocumentViewer", "File already exists: ${file.absolutePath}")
                    scope.launch(Dispatchers.Main) {
                        pdfFile = file
                    }
                    return@launch
                }
                val request = DownloadManager.Request(Uri.parse(url))
                    .setDestinationInExternalFilesDir(context, null, decodedFilename)
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(true)
                val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                downloadId = downloadManager.enqueue(request)
                Log.d("DocumentViewer", "Download enqueued with ID: $downloadId")
            } catch (e: Exception) {
                Log.e("DocumentViewer", "Error initiating download: ${e.message}", e)
                scope.launch(Dispatchers.Main) {
                    snackbarHostState.showSnackbar("Error initiating download: ${e.message}")
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("View Document") },
                navigationIcon = {
                    IconButton(onClick = {
                        Log.d("DocumentViewer", "Back button clicked")
                        navController.popBackStack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    IconButton(
                        onClick = {
                            Log.d("DocumentViewer", "Download clicked")
                            scope.launch(Dispatchers.IO) {
                                try {
                                    pdfFile?.let { file ->
                                        val outputStream = getPublicDownloadsOutputStream(context, decodedFilename)
                                        if (outputStream == null) {
                                            Log.e("DocumentViewer", "Failed to access Downloads directory")
                                            scope.launch(Dispatchers.Main) {
                                                snackbarHostState.showSnackbar("Error: Downloads directory not accessible")
                                            }
                                            return@launch
                                        }
                                        FileInputStream(file).use { input ->
                                            outputStream.use { output: OutputStream ->
                                                val buffer = ByteArray(1024)
                                                var bytesRead: Int
                                                while (input.read(buffer).also { bytesRead = it } != -1) {
                                                    output.write(buffer, 0, bytesRead)
                                                }
                                            }
                                        }
                                        scope.launch(Dispatchers.Main) {
                                            snackbarHostState.showSnackbar("PDF downloaded to Downloads folder")
                                        }
                                    } ?: throw IllegalStateException("No PDF file available")
                                } catch (e: Exception) {
                                    Log.e("DocumentViewer", "Error downloading PDF: ${e.message}", e)
                                    scope.launch(Dispatchers.Main) {
                                        snackbarHostState.showSnackbar("Error downloading PDF: ${e.message}")
                                    }
                                }
                            }
                        },
                        enabled = pdfFile != null
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download",
                            tint = if (pdfFile != null) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = decodedFilename,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
            ) {
                pdfFile?.let { file ->
                    AndroidView(
                        factory = { ctx ->
                            PDFView(ctx, null)
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { pdfView ->
                            Log.d("DocumentViewer", "Updating PDFView with file: ${file.absolutePath}")
                            pdfView.fromFile(file)
                                .defaultPage(currentPage - 1)
                                .onPageChange { page, pageCount ->
                                    currentPage = page + 1
                                    totalPages = pageCount
                                    Log.d("DocumentViewer", "Page changed to $currentPage of $totalPages")
                                }
                                .load()
                        }
                    )
                } ?: Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { if (currentPage > 1) currentPage-- },
                    enabled = currentPage > 1
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous Page")
                }
                Text("Page $currentPage of $totalPages")
                IconButton(
                    onClick = { if (currentPage < totalPages) currentPage++ },
                    enabled = currentPage < totalPages
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Page")
                }
            }
        }
    }
}

private fun getPublicDownloadsOutputStream(context: Context, fileName: String): OutputStream? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            uri?.let { context.contentResolver.openOutputStream(it) }
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val destFile = File(downloadsDir, fileName)
            FileOutputStream(destFile)
        }
    } catch (e: Exception) {
        Log.e("DocumentViewer", "Error accessing Downloads directory: ${e.message}", e)
        null
    }
}