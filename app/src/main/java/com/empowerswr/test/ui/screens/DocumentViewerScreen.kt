package com.empowerswr.test.ui.screens

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
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
import androidx.navigation.NavController
import com.github.barteksc.pdfviewer.PDFView
import com.empowerswr.test.network.NetworkModule
import com.empowerswr.test.PrefsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentViewerScreen(
    navController: NavController,
    filename: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var pdfFile by remember { mutableStateOf<File?>(null) }
    var currentPage by remember { mutableStateOf(1) }
    var totalPages by remember { mutableStateOf(1) }
    val jwtToken = PrefsHelper.getJwtToken(context)
    val service = NetworkModule.provideDownloadService(context)

    LaunchedEffect(filename) {
        if (jwtToken.isEmpty()) {
            Log.e("DocumentViewer", "JWT token is empty")
            Toast.makeText(context, "Invalid session, please log in", Toast.LENGTH_SHORT).show()
            (context as? Activity)?.finish()
            return@LaunchedEffect
        }
        scope.launch(Dispatchers.IO) {
            try {
                val encodedFilename = filename.replace(" ", "%20")
                Log.d("DocumentViewer", "Downloading PDF: $encodedFilename")
                val response = service.downloadDocument("Bearer $jwtToken", encodedFilename)
                if (!response.isSuccessful) {
                    Log.e("DocumentViewer", "Download failed: HTTP ${response.code()} ${response.message()}")
                    scope.launch(Dispatchers.Main) {
                        snackbarHostState.showSnackbar("Download failed: HTTP ${response.code()}")
                    }
                    return@launch
                }
                if (!response.headers()["Content-Type"]?.contains("application/pdf")!!) {
                    Log.e("DocumentViewer", "Invalid content type: ${response.headers()["Content-Type"]}")
                    scope.launch(Dispatchers.Main) {
                        snackbarHostState.showSnackbar("Invalid file format received")
                    }
                    return@launch
                }
                val file = File(context.cacheDir, filename)
                FileOutputStream(file).use { output ->
                    response.body()?.bytes()?.let { output.write(it) } ?: throw Exception("Empty response body")
                }
                Log.d("DocumentViewer", "File saved to: ${file.absolutePath}, size: ${file.length()} bytes")
                pdfFile = file
            } catch (e: Exception) {
                Log.e("DocumentViewer", "Error loading PDF: ${e.message}", e)
                scope.launch(Dispatchers.Main) {
                    snackbarHostState.showSnackbar("Error loading PDF: ${e.message}")
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
                                        val outputStream = getPublicDownloadsOutputStream(context, filename)
                                        if (outputStream == null) {
                                            Log.e("DocumentViewer", "Failed to access Downloads directory")
                                            scope.launch(Dispatchers.Main) {
                                                snackbarHostState.showSnackbar("Error: Downloads directory not accessible")
                                            }
                                            return@launch
                                        }
                                        FileInputStream(file).use { input ->
                                            outputStream.use { output ->
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
                text = filename,
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