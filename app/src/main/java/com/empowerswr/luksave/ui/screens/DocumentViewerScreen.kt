package com.empowerswr.luksave.ui.screens

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.empowerswr.luksave.MainActivity
import com.empowerswr.luksave.PrefsHelper
import com.empowerswr.luksave.findActivity
import com.empowerswr.luksave.network.ListFilesService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
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
    val nicknameBase = docTypeCode?.let { documentTypes.find { it.second == docTypeCode }?.first } ?: decodedFilename.substringBeforeLast(".pdf")
    val possibleNicknames = (0..10).flatMap { i ->
        val base = if (i == 0) "$nicknameBase" else "$nicknameBase-$i"
        listOf("$base.pdf", "$base.jpg", "$base.png")
    }

    // Log screen usage
    LaunchedEffect(Unit) {
        Timber.i("ScreenUsage: DocumentViewerScreen displayed, workerId=${PrefsHelper.getWorkerId(context) ?: "unknown"}, timestamp=${System.currentTimeMillis()}")
    }

    // Launch PDF viewer after download
    LaunchedEffect(pdfFile) {
        pdfFile?.let { file ->
            scope.launch(Dispatchers.Main) {
                try {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/pdf")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(Intent.createChooser(intent, "Open PDF"))
                    Timber.d("DocumentViewerScreen: Launched PDF viewer with URI: $uri")
                } catch (e: Exception) {
                    Timber.e(e, "DocumentViewerScreen: No PDF viewer found")
                    scope.launch {
                        snackbarHostState.showSnackbar("No PDF viewer app found. Install Google PDF Viewer.")
                    }
                }
            }
        }
    }

    // Collect download completion events and resolve content URI
    LaunchedEffect(decodedFilename) {
        downloadCompleteFlow.collect { (downloadId, filename) ->
            Timber.d("DocumentViewerScreen: Received download complete for ID: $downloadId, filename: $filename")
            if (filename.contains(decodedFilename) && filename.endsWith(".tmp")) {
                val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                if (downloadManager == null) {
                    Timber.e("DocumentViewerScreen: DownloadManager unavailable")
                    scope.launch(Dispatchers.Main) {
                        snackbarHostState.showSnackbar("Download service unavailable")
                    }
                    return@collect
                }
                val query = DownloadManager.Query().setFilterById(downloadId)
                downloadManager.query(query).use { cursor ->
                    if (cursor.moveToFirst()) {
                        val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            val uri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                            val tempFile = File(context.cacheDir, decodedFilename)
                            context.contentResolver.openInputStream(uri.toUri())?.use { input ->
                                FileOutputStream(tempFile).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            if (tempFile.exists() && tempFile.length() > 0 && isValidPdf(tempFile)) {
                                pdfFile = tempFile
                                // Clean up .tmp files in Downloads
                                scope.launch(Dispatchers.IO) {
                                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                    downloadsDir.listFiles()?.forEach { file ->
                                        Timber.d("DocumentViewerScreen: File in Downloads: ${file.absolutePath}")
                                        if (file.name.contains(decodedFilename) && file.name.endsWith(".tmp")) {
                                            Timber.d("DocumentViewerScreen: Attempting to delete temp file: ${file.absolutePath}")
                                            var deleteAttempts = 0
                                            val maxDeleteAttempts = 3
                                            while (file.exists() && deleteAttempts < maxDeleteAttempts) {
                                                if (file.delete()) {
                                                    Timber.d("DocumentViewerScreen: Deleted temp file: ${file.absolutePath}")
                                                    break
                                                }
                                                delay(500)
                                                deleteAttempts++
                                            }
                                            if (file.exists()) {
                                                Timber.e("DocumentViewerScreen: Failed to delete temp file: ${file.absolutePath} after $maxDeleteAttempts attempts")
                                            }
                                        }
                                    }
                                }
                                Timber.i("DocumentViewerScreen: Set pdfFile to ${tempFile.absolutePath}")
                            } else {
                                scope.launch(Dispatchers.Main) {
                                    snackbarHostState.showSnackbar("Downloaded file invalid")
                                }
                                Timber.e("DocumentViewerScreen: Temp file invalid: exists=${tempFile.exists()}, size=${tempFile.length()}")
                                if (tempFile.exists()) tempFile.delete()
                            }
                        } else {
                            scope.launch(Dispatchers.Main) {
                                snackbarHostState.showSnackbar("Download failed")
                            }
                            Timber.e("DocumentViewerScreen: Download failed for ID: $downloadId, status: $status")
                        }
                    }
                }
            } else {
                Timber.d("DocumentViewerScreen: Filename $filename does not match expected temp file for $decodedFilename")
            }
        }
    }

    // FIXED FALLBACK: Check DOWNLOADS FOLDER FIRST!
    LaunchedEffect(decodedFilename, retryTrigger) {
        if (pdfFile == null) {
            scope.launch(Dispatchers.IO) {
                var attempts = 0
                val maxAttempts = 90
                while (attempts < maxAttempts && pdfFile == null) {
                    // CHECK DOWNLOADS FOLDER FIRST (NEW!)
                    val downloadsFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), decodedFilename)
                    if (downloadsFile.exists() && downloadsFile.length() > 0 && isValidPdf(downloadsFile)) {
                        pdfFile = downloadsFile
                        Timber.i("DocumentViewerScreen: Found PDF in DOWNLOADS: ${downloadsFile.absolutePath}")
                        break
                    }

                    // THEN CHECK CACHE (existing)
                    val cacheFile = File(context.cacheDir, decodedFilename)
                    if (cacheFile.exists() && cacheFile.length() > 0 && isValidPdf(cacheFile)) {
                        pdfFile = cacheFile
                        Timber.i("DocumentViewerScreen: Found PDF in CACHE: ${cacheFile.absolutePath}")
                        break
                    }

                    Timber.d("DocumentViewerScreen: Checking for ${decodedFilename}, attempt: $attempts")
                    delay(1000)
                    attempts++
                }
                if (attempts >= maxAttempts) {
                    Timber.e("DocumentViewerScreen: File check timed out for: $decodedFilename")
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
                // Compute target filename
                val computedTargetFilename = decodedFilename
                val tempFilename = "$computedTargetFilename.tmp"
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

                // Start download
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
                            retryTrigger++
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
                .padding(horizontal = 2.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = targetFilename ?: decodedFilename,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (pdfFile == null) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text("Loading PDF...")
            } else {
                Text("PDF opened in external viewer")
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