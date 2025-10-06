package com.empowerswr.luksave.ui.screens

import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.empowerswr.luksave.MainActivity
import com.empowerswr.luksave.PrefsHelper
import com.empowerswr.luksave.findActivity
import com.empowerswr.luksave.network.ListFilesService
import com.github.barteksc.pdfviewer.PDFView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.net.URLDecoder
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentViewerScreen(
    navController: NavController,
    filename: String,
    url: String,
    listFilesService: ListFilesService,
    downloadCompleteFlow: SharedFlow<Pair<Long, String>>
) {
    Timber.d("DocumentViewerScreen: Composing with filename=$filename, url=$url")
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var pdfFile by remember { mutableStateOf<File?>(null) }
    var currentPage by remember { mutableStateOf(1) }
    var totalPages by remember { mutableStateOf(1) }
    var hasNavigated by rememberSaveable { mutableStateOf(false) }
    var targetFilename by remember { mutableStateOf<String?>(null) }
    var retryTrigger by remember { mutableStateOf(0) }
    val decodedFilename = try {
        URLDecoder.decode(filename.replace("+", "%20"), "UTF-8").trim()
    } catch (e: Exception) {
        Timber.e(e, "DocumentViewerScreen: Failed to decode filename: $filename")
        filename.replace("+", " ").trim()
    }
    val encodedFilename = try {
        URLEncoder.encode(decodedFilename, "UTF-8")
    } catch (e: Exception) {
        Timber.e(e, "DocumentViewerScreen: Failed to encode filename: $decodedFilename")
        decodedFilename
    }

    // Map document types to nicknames
    val documentTypes = listOf(
        "Passport" to "PPT",
        "National ID Card" to "NID",
        "Birth Certificate" to "BC",
        "Driving Licence" to "DL",
        "Police Clearance" to "PC",
        "Medical" to "MED",
        "Contract" to "CON",
        "Spouse Letter" to "SPO",
        "Chief/Pastor Letter" to "REF"
    )
    val docTypeCode = documentTypes.find { decodedFilename.endsWith("- ${it.second}.pdf") }?.second
        ?: documentTypes.find { decodedFilename == "${it.first}.pdf" }?.second
    val nicknameBase = docTypeCode?.let { documentTypes.find { it.second == docTypeCode }?.first } ?: decodedFilename.substringBeforeLast(".")
    val possibleNicknames = (0..10).flatMap { i ->
        val base = if (i == 0) "$nicknameBase" else "$nicknameBase-$i"
        listOf("$base.pdf", "$base.jpg", "$base.png")
    }
    // Log screen usage
    LaunchedEffect(Unit) {
        Timber.i("ScreenUsage: DocumentViewerScreen displayed, workerId=${PrefsHelper.getWorkerId(context) ?: "unknown"}, timestamp=${System.currentTimeMillis()}")
    }
    // Collect download completion events from MainActivity
    LaunchedEffect(decodedFilename) {
        downloadCompleteFlow.collect { (downloadId, filename) ->
            Timber.d("DocumentViewerScreen: Received download complete for ID: $downloadId, filename: $filename")
            if (filename == decodedFilename + ".tmp" || filename == encodedFilename + ".tmp" || possibleNicknames.contains(filename + ".tmp")) {
                val tempFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), filename.replace("+", " ").replace("%20", " ").trim())
                Timber.d("DocumentViewerScreen: Checking temp file: ${tempFile.absolutePath}, exists: ${tempFile.exists()}, size: ${tempFile.length()}")
                if (tempFile.exists() && tempFile.length() > 0 && tempFile.extension.lowercase() == "tmp" && isValidPdf(tempFile)) {
                    val finalFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), decodedFilename)
                    if (finalFile.exists()) {
                        Timber.d("DocumentViewerScreen: Deleting old file: ${finalFile.absolutePath}")
                        finalFile.delete()
                    }
                    Timber.d("DocumentViewerScreen: Renaming temp file to final: ${tempFile.absolutePath} to ${finalFile.absolutePath}")
                    tempFile.renameTo(finalFile)
                    pdfFile = finalFile
                    Timber.i("DocumentViewerScreen: Set pdfFile to ${finalFile.absolutePath}")
                } else {
                    snackbarHostState.showSnackbar("Downloaded file not found or invalid")
                    Timber.e("DocumentViewerScreen: Temp file invalid: exists=${tempFile.exists()}, size=${tempFile.length()}, extension=${tempFile.extension}, validPdf=${isValidPdf(tempFile)}")
                    if (tempFile.exists()) {
                        tempFile.delete()
                    }
                }
            }
        }
    }

    // Fallback file check (for temp file)
    LaunchedEffect(decodedFilename, retryTrigger) {
        if (pdfFile == null) {
            scope.launch(Dispatchers.IO) {
                var attempts = 0
                val maxAttempts = 90 // 90 seconds
                while (attempts < maxAttempts && pdfFile == null) {
                    val tempFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), decodedFilename + ".tmp")
                    Timber.d("DocumentViewerScreen: Fallback checking temp file: ${tempFile.absolutePath}, attempt: $attempts, exists: ${tempFile.exists()}, size: ${tempFile.length()}, validPdf: ${isValidPdf(tempFile)}")
                    if (tempFile.exists() && tempFile.length() > 0 && tempFile.extension.lowercase() == "tmp" && isValidPdf(tempFile)) {
                        val finalFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), decodedFilename)
                        if (finalFile.exists()) {
                            Timber.d("DocumentViewerScreen: Deleting old file: ${finalFile.absolutePath}")
                            finalFile.delete()
                        }
                        Timber.i("DocumentViewerScreen: Renaming temp file to final: ${tempFile.absolutePath} to ${finalFile.absolutePath}")
                        tempFile.renameTo(finalFile)
                        scope.launch(Dispatchers.Main) {
                            pdfFile = finalFile
                        }
                        break
                    }
                    delay(1000)
                    attempts++
                }
                if (attempts >= maxAttempts) {
                    Timber.e("DocumentViewerScreen: Fallback file check timed out for: $decodedFilename")
                    scope.launch(Dispatchers.Main) {
                        snackbarHostState.showSnackbar("File not found after timeout")
                    }
                }
            }
        }
    }

    // Download PDF
    LaunchedEffect(key1 = decodedFilename) {
        if (hasNavigated) {
            Timber.d("DocumentViewerScreen: Skipping download due to navigation guard")
            return@LaunchedEffect
        }
        hasNavigated = true

        if (PrefsHelper.getToken(context)?.isEmpty() != false) {
            Timber.e("DocumentViewerScreen: JWT token is empty")
            scope.launch {
                snackbarHostState.showSnackbar("Please log in to access documents")
                navController.navigate("login") {
                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    launchSingleTop = true
                }
            }
            return@LaunchedEffect
        }

        scope.launch(Dispatchers.IO) {
            try {
                // Compute target filename (use R2 filename directly)
                val computedTargetFilename = decodedFilename
                val tempFilename = "$computedTargetFilename.tmp" // Temp for download
                targetFilename = computedTargetFilename

                // Fetch fresh URL
                val token = PrefsHelper.getToken(context)
                val fileList = listFilesService.listFiles("Bearer $token")
                val fileItem = fileList?.find {
                    it.name == decodedFilename ||
                            it.name == filename ||
                            it.name == encodedFilename ||
                            it.name.replace("+", " ").replace("%20", " ").trim() == decodedFilename ||
                            it.name.lowercase() == decodedFilename.lowercase() ||
                            possibleNicknames.contains(it.name)
                }
                val downloadUrl = fileItem?.url ?: url
                Timber.d("DocumentViewerScreen: Using download URL: $downloadUrl")

                // Start download to temp file
                val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                if (downloadManager == null) {
                    Timber.e("DocumentViewerScreen: DownloadManager unavailable")
                    scope.launch(Dispatchers.Main) {
                        snackbarHostState.showSnackbar("Download service unavailable")
                    }
                    return@launch
                }
                val request = DownloadManager.Request(downloadUrl.toUri())
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, tempFilename)
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(true)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                val downloadId = downloadManager.enqueue(request)
                Timber.i("DocumentViewerScreen: Download enqueued with ID: $downloadId for temp file: $tempFilename")
                (context.findActivity() as? MainActivity)?.storeDownload(downloadId, tempFilename)
            } catch (e: Exception) {
                Timber.e(e, "DocumentViewerScreen: Error initiating download")
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
                        Timber.d("DocumentViewerScreen: Navigating back")
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
                            scope.launch(Dispatchers.IO) {
                                try {
                                    pdfFile?.let { file ->
                                        val destFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), targetFilename ?: decodedFilename)
                                        if (!destFile.exists()) {
                                            FileInputStream(file).use { input ->
                                                destFile.outputStream().use { output ->
                                                    input.copyTo(output)
                                                }
                                            }
                                        }
                                        scope.launch(Dispatchers.Main) {
                                            snackbarHostState.showSnackbar("PDF available in Downloads folder")
                                        }
                                    } ?: throw IllegalStateException("No PDF file available")
                                } catch (e: Exception) {
                                    Timber.e(e, "DocumentViewerScreen: Error copying PDF to Downloads")
                                    scope.launch(Dispatchers.Main) {
                                        snackbarHostState.showSnackbar("Error copying PDF: ${e.message}")
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
                    IconButton(
                        onClick = {
                            retryTrigger++ // Trigger fallback check
                            Timber.d("DocumentViewerScreen: Manual retry triggered")
                        },
                        enabled = pdfFile == null
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Retry",
                            tint = if (pdfFile == null) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Timber.d("DocumentViewerScreen: Composing Scaffold")
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = targetFilename ?: decodedFilename,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
            ) {
                pdfFile?.let { file ->
                    Timber.d("DocumentViewerScreen: Rendering PDFView for ${file.absolutePath}")
                    if (!file.exists()) {
                        Timber.e("DocumentViewerScreen: PDF file does not exist: ${file.absolutePath}")
                        Text("Error: PDF file not found")
                        return@let
                    }
                    if (file.length() == 0L) {
                        Timber.e("DocumentViewerScreen: PDF file is empty: ${file.absolutePath}")
                        Text("Error: PDF file is empty")
                        return@let
                    }
                    AndroidView(
                        factory = { ctx ->
                            PDFView(ctx, null).apply {
                                try {
                                    fromFile(file)
                                        .defaultPage(currentPage - 1)
                                        .onPageChange { page, pageCount ->
                                            scope.launch {
                                                Timber.d("DocumentViewerScreen: PDFView page changed to ${page + 1} of $pageCount")
                                                currentPage = page + 1
                                                totalPages = pageCount
                                            }
                                        }
                                        .onError { error ->
                                            Timber.e(error, "DocumentViewerScreen: PDFView failed to load ${file.absolutePath}")
                                            scope.launch(Dispatchers.Main) {
                                                snackbarHostState.showSnackbar("Failed to load PDF: ${error.message}")
                                            }
                                        }
                                        .onLoad {
                                            Timber.i("DocumentViewerScreen: PDFView loaded successfully for ${file.absolutePath}")
                                        }
                                        .load()
                                } catch (e: Exception) {
                                    Timber.e(e, "DocumentViewerScreen: PDFView initialization failed for ${file.absolutePath}")
                                    scope.launch(Dispatchers.Main) {
                                        snackbarHostState.showSnackbar("Failed to initialize PDF: ${e.message}")
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { pdfView ->
                            if (pdfView.currentPage != currentPage - 1) {
                                Timber.d("DocumentViewerScreen: Updating PDFView to page ${currentPage - 1}")
                                pdfView.jumpTo(currentPage - 1, true)
                            }
                        }
                    )
                } ?: Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Timber.d("DocumentViewerScreen: Showing loading text")
                    Text("Loading PDF...")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (currentPage > 1) {
                            Timber.d("DocumentViewerScreen: Navigating to previous page: ${currentPage - 1}")
                            currentPage--
                        }
                    },
                    enabled = currentPage > 1
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous Page")
                }
                Text("Page $currentPage of $totalPages")
                IconButton(
                    onClick = {
                        if (currentPage < totalPages) {
                            Timber.d("DocumentViewerScreen: Navigating to next page: ${currentPage + 1}")
                            currentPage++
                        }
                    },
                    enabled = currentPage < totalPages
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Page")
                }
            }
        }
    }
}

// Simple PDF validation
private fun isValidPdf(file: File): Boolean {
    return try {
        FileInputStream(file).use { input ->
            val header = ByteArray(4)
            val bytesRead = input.read(header)
            bytesRead == 4 && header[0] == 0x25.toByte() && header[1] == 0x50.toByte() && header[2] == 0x44.toByte() && header[3] == 0x46.toByte()
        }
    } catch (e: Exception) {
        Timber.e(e, "Failed to validate PDF: ${file.absolutePath}")
        false
    }
}