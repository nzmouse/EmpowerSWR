package com.empowerswr.luksave.ui.screens

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Environment
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.empowerswr.luksave.DownloadState
import com.empowerswr.luksave.MainActivity
import com.empowerswr.luksave.PrefsHelper
import com.empowerswr.luksave.findActivity
import com.empowerswr.luksave.network.ListFilesService
import com.github.barteksc.pdfviewer.PDFView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.net.URLDecoder
import timber.log.Timber
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentViewerScreen(
    navController: NavController,
    filename: String,
    url: String,
    listFilesService: ListFilesService,
    downloadState: State<DownloadState>
) {
    // Recomposition counter for debugging
    var recompositionCount by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        recompositionCount++
        Timber.d("DocumentViewerScreen: Recomposition count: $recompositionCount")
    }

    // Navigation attempt counter
    var navigationAttemptCount by remember { mutableStateOf(0) }
    LaunchedEffect(filename, url) {
        navigationAttemptCount++
        Timber.d("DocumentViewerScreen: Navigation attempt count: $navigationAttemptCount for filename=$filename, url=$url")
    }

    Timber.d("DocumentViewerScreen: Composing with filename=$filename, url=$url")
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var pdfFile by remember { mutableStateOf<File?>(null) }
    var currentPage by remember { mutableStateOf(1) }
    var totalPages by remember { mutableStateOf(1) }
    var hasNavigated by rememberSaveable { mutableStateOf(false) } // Prevent re-navigation
    var targetFilename by remember { mutableStateOf<String?>(null) } // Store target filename
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
    val nicknameBase = docTypeCode?.let { documentTypes.find { it.second == docTypeCode }?.first } ?: "Unknown"
    val possibleNicknames = (0..10).flatMap { i ->
        val base = if (i == 0) "$nicknameBase" else "$nicknameBase-$i"
        listOf("$base.pdf", "$base.jpg", "$base.png")
    }

    // Permission handling
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            scope.launch {
                snackbarHostState.showSnackbar("Storage permission denied")
            }
            Timber.e("DocumentViewerScreen: Storage permission denied")
        }
    }

    val hasStoragePermission = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

    // Dynamic BroadcastReceiver for downloads
    var downloadId by remember { mutableStateOf<Long?>(null) }
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Timber.d("DocumentViewerScreen: Broadcast received, action: ${intent?.action}, extras: ${intent?.extras?.keySet()?.joinToString()}")
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) ?: -1
                Timber.d("DocumentViewerScreen: Broadcast download ID: $id, expected: $downloadId")
                if (id == downloadId) {
                    val downloadManager = context?.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                    val query = DownloadManager.Query().setFilterById(id)
                    downloadManager?.query(query)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                            Timber.d("DocumentViewerScreen: Download status: $status")
                            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                val localUri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                                Timber.d("DocumentViewerScreen: Local URI: $localUri")
                                val file = if (localUri != null) File(localUri.toUri().path ?: return@use) else null
                                val normalizedFilename = targetFilename?.replace("+", " ")?.replace("%20", " ")?.trim() ?: decodedFilename
                                val foundFile = file?.takeIf { it.exists() } ?: File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), normalizedFilename)
                                Timber.d("DocumentViewerScreen: Download completed, file: ${foundFile.absolutePath}, exists: ${foundFile.exists()}")
                                if (foundFile.exists()) {
                                    scope.launch(Dispatchers.Main) {
                                        pdfFile = foundFile
                                    }
                                } else {
                                    scope.launch(Dispatchers.Main) {
                                        snackbarHostState.showSnackbar("Downloaded file not found")
                                        Timber.e("DocumentViewerScreen: Downloaded file not found: ${foundFile.absolutePath}")
                                    }
                                }
                            } else {
                                val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                                scope.launch(Dispatchers.Main) {
                                    snackbarHostState.showSnackbar("Download failed: Status $status, Reason $reason")
                                    Timber.e("DocumentViewerScreen: Download failed with status: $status, reason: $reason")
                                }
                            }
                        }
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            addAction(DownloadManager.ACTION_NOTIFICATION_CLICKED)
            addAction(DownloadManager.ACTION_VIEW_DOWNLOADS)
        }
        Timber.d("DocumentViewerScreen: Registering dynamic receiver with filter: $filter")
        context.registerReceiver(
            receiver,
            filter,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Context.RECEIVER_NOT_EXPORTED else Context.RECEIVER_EXPORTED
        )
        onDispose {
            try {
                context.unregisterReceiver(receiver)
                Timber.d("DocumentViewerScreen: Unregistered dynamic receiver")
            } catch (e: IllegalArgumentException) {
                Timber.e(e, "DocumentViewerScreen: Receiver not registered")
            }
        }
    }

    // Handle download state
    LaunchedEffect(downloadState.value) {
        when (val state = downloadState.value) {
            is DownloadState.Completed -> {
                val normalizedFilename = state.filename.replace("+", " ").replace("%20", " ").trim()
                if (possibleNicknames.contains(normalizedFilename) || normalizedFilename == decodedFilename || normalizedFilename == encodedFilename) {
                    val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), normalizedFilename)
                    Timber.d("DocumentViewerScreen: Download completed, file: ${file.absolutePath}, exists: ${file.exists()}")
                    if (file.exists() && file.extension.lowercase() == "pdf") {
                        pdfFile = file
                    } else {
                        snackbarHostState.showSnackbar("Downloaded file not found or not a PDF")
                        Timber.e("DocumentViewerScreen: Downloaded file not found or not a PDF: ${file.absolutePath}")
                    }
                }
            }
            is DownloadState.Failed -> {
                val normalizedFailedFilename = state.filename.replace("+", " ").replace("%20", " ").trim()
                if (possibleNicknames.contains(normalizedFailedFilename) || normalizedFailedFilename == decodedFilename || normalizedFailedFilename == encodedFilename) {
                    snackbarHostState.showSnackbar("Download failed: ${state.message}")
                    Timber.e("DocumentViewerScreen: Download failed: ${state.message}")
                }
            }
            else -> { /* Ignore other states */ }
        }
    }

    // Download PDF with version check
    LaunchedEffect(key1 = "$decodedFilename-$url-$hasStoragePermission") {
        if (hasNavigated) {
            Timber.d("DocumentViewerScreen: Skipping download due to navigation guard")
            return@LaunchedEffect
        }
        hasNavigated = true
        if (PrefsHelper.getJwtToken(context).isEmpty()) {
            Timber.e("DocumentViewerScreen: JWT token is empty")
            Toast.makeText(context, "Invalid session, please log in", Toast.LENGTH_SHORT).show()
            (context as? Activity)?.finish()
            return@LaunchedEffect
        }
        if (!hasStoragePermission) {
            Timber.d("DocumentViewerScreen: Requesting storage permission")
            permissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return@LaunchedEffect
        }
        scope.launch(Dispatchers.IO) {
            try {
                // Check Downloads directory for possible nicknames
                var file: File? = null
                var tempNickname: String? = null
                for (nickname in possibleNicknames) {
                    val testFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), nickname)
                    if (testFile.exists()) {
                        file = testFile
                        tempNickname = nickname
                        break
                    }
                }
                // Also check original filename (decoded and encoded)
                val originalFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), decodedFilename)
                if (originalFile.exists() && file == null) {
                    file = originalFile
                    tempNickname = decodedFilename
                }
                val encodedFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), encodedFilename)
                if (encodedFile.exists() && file == null) {
                    file = encodedFile
                    tempNickname = encodedFilename
                }

                var downloadUrl = url
                // Try to fetch fresh URL
                val token = PrefsHelper.getJwtToken(context)
                val fileList = listFilesService.listFiles("Bearer $token")
                val fileItem = fileList?.find {
                    it.name == decodedFilename ||
                            it.name == filename ||
                            it.name == encodedFilename ||
                            it.name.replace("+", " ").replace("%20", " ").trim() == decodedFilename ||
                            it.name.lowercase() == decodedFilename.lowercase() ||
                            possibleNicknames.contains(it.name)
                }
                if (fileItem != null) {
                    downloadUrl = fileItem.url
                    Timber.d("DocumentViewerScreen: Fresh URL: $downloadUrl, fileItem name: ${fileItem.name}")
                } else {
                    Timber.w("DocumentViewerScreen: File not found in list, using passed URL: $url")
                }

                // Use nickname for saving
                val computedTargetFilename = if (docTypeCode != null) {
                    // Find an available nickname (e.g., Driving Licence.pdf, Driving Licence-1.pdf)
                    var candidateNickname = "$nicknameBase.pdf"
                    var index = 0
                    var candidateFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), candidateNickname)
                    while (candidateFile.exists()) {
                        index++
                        candidateNickname = "$nicknameBase-$index.pdf"
                        candidateFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), candidateNickname)
                    }
                    candidateNickname
                } else {
                    decodedFilename
                }
                targetFilename = computedTargetFilename

                // Check if local file is valid
                if (file != null && tempNickname != null) {
                    val localLastModified = file.lastModified()
                    val oneDayAgo = System.currentTimeMillis() - 24 * 60 * 60 * 1000
                    if (localLastModified > oneDayAgo && file.extension.lowercase() == "pdf") {
                        Timber.i("DocumentViewerScreen: Using local file: ${file.absolutePath}, last modified: $localLastModified")
                        scope.launch(Dispatchers.Main) {
                            pdfFile = file
                            targetFilename = tempNickname // Update targetFilename for display
                        }
                        return@launch
                    } else {
                        Timber.d("DocumentViewerScreen: Local file too old or not a PDF, deleting: ${file.absolutePath}")
                        file.delete()
                    }
                }

                // Delete any existing files with suffixes (e.g., (1), (2))
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val baseName = computedTargetFilename.substringBeforeLast(".")
                downloadsDir.listFiles()?.forEach { existingFile ->
                    if (existingFile.name.startsWith(baseName) && existingFile.name.matches(Regex("$baseName(\\s*\\(\\d+\\))?\\.(pdf|jpg|png)"))) {
                        Timber.d("DocumentViewerScreen: Deleting existing file: ${existingFile.absolutePath}")
                        existingFile.delete()
                    }
                }

                Timber.d("DocumentViewerScreen: Preparing download request for $downloadUrl, target filename: $computedTargetFilename")
                val request = DownloadManager.Request(downloadUrl.toUri())
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, computedTargetFilename)
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(true)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                downloadId = downloadManager.enqueue(request)
                Timber.i("DocumentViewerScreen: Download enqueued with ID: $downloadId")
                (context.findActivity() as? MainActivity)?.storeDownload(downloadId!!, computedTargetFilename)

                // Fallback polling if BroadcastReceiver fails
                var attempts = 0
                val maxAttempts = 30 // 30 seconds at 1-second intervals
                while (attempts < maxAttempts && pdfFile == null) {
                    val checkFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), computedTargetFilename)
                    if (checkFile.exists() && checkFile.extension.lowercase() == "pdf") {
                        Timber.i("DocumentViewerScreen: File found via polling: ${checkFile.absolutePath}")
                        scope.launch(Dispatchers.Main) {
                            pdfFile = checkFile
                        }
                        break
                    }
                    delay(1000)
                    attempts++
                }
                if (attempts >= maxAttempts) {
                    Timber.e("DocumentViewerScreen: File not found after polling: $computedTargetFilename")
                    scope.launch(Dispatchers.Main) {
                        snackbarHostState.showSnackbar("Download timed out")
                    }
                }
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
                                fromFile(file)
                                    .defaultPage(currentPage - 1)
                                    .onPageChange { page, pageCount ->
                                        scope.launch {
                                            Timber.d("DocumentViewerScreen: PDFView page changed to ${page + 1} of $pageCount")
                                            delay(100) // Debounce
                                            currentPage = page + 1
                                            totalPages = pageCount
                                        }
                                    }
                                    .load()
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