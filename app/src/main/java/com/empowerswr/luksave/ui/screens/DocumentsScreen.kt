package com.empowerswr.luksave.ui.screens

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.empowerswr.luksave.network.FileItem
import com.empowerswr.luksave.network.ListFilesService
import com.empowerswr.luksave.network.UploadService
import com.empowerswr.luksave.PrefsHelper
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.HttpException
import java.io.File
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.core.net.toUri
import com.empowerswr.luksave.MainActivity
import com.empowerswr.luksave.findActivity
import java.net.URLEncoder
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(
    uploadService: UploadService,
    listFilesService: ListFilesService,
    navController: NavController
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var isUploading by remember { mutableStateOf(false) }
    var selectedDocumentType by remember { mutableStateOf("") }
    var files by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var isLoadingFiles by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) } // Separate state for refresh
    var lastClickTime by rememberSaveable { mutableStateOf(0L) }
    var lastNavigatedFile by rememberSaveable { mutableStateOf<String?>(null) } // Keep for logging
    var navigationAttemptCount by remember { mutableStateOf(0) }
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

    var expanded by remember { mutableStateOf(false) }
    val localContext = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }
    // Log screen usage
    LaunchedEffect(Unit) {
        Timber.i("ScreenUsage: DocumentsScreen displayed, workerId=${PrefsHelper.getWorkerId(context) ?: "unknown"}, timestamp=${System.currentTimeMillis()}")
    }
    // Handle clicks on the TextField to expand dropdown
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Press) {
                expanded = true
                focusManager.clearFocus()
            }
        }
    }

    // File loading logic
    fun loadFiles() {
        scope.launch {
            isLoadingFiles = true
            isRefreshing = true
            try {
                val token = PrefsHelper.getToken(localContext)
                if (token?.isEmpty() != false) {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Please log in to view documents")
                        navController.navigate("login") {
                            popUpTo(navController.graph.startDestinationId) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                    Timber.w("File list failed: No valid JWT")
                } else {
                    val fileList = listFilesService.listFiles("Bearer $token")
                    files = fileList?.map { item ->
                        item.copy(name = item.name.replace("+", " ").replace("%20", " ").trim())
                    } ?: emptyList()
                }
            } catch (e: HttpException) {
                Timber.tag("DocumentsScreen").e(e, "File list failed: HTTP ${e.code()} ${e.message()}")
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Failed to load files: HTTP ${e.code()}")
                }
            } catch (e: Exception) {
                Timber.tag("DocumentsScreen").e(e, "File list failed: ${e.message}")
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Failed to load files: ${e.message ?: "Unknown error"}")
                }
            } finally {
                isLoadingFiles = false
                isRefreshing = false
            }
        }
    }

    // Load files on composition
    LaunchedEffect(Unit) {
        loadFiles()
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && selectedDocumentType.isNotEmpty()) {
            isUploading = true
            coroutineScope.launch {
                try {
                    uploadFile(localContext, uri, selectedDocumentType, documentTypes, uploadService, isScanned = false)
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Upload successful")
                    }
                    // Reload file list
                    loadFiles()
                } catch (e: HttpException) {
                    Timber.tag("DocumentsScreen").e(e, "Upload failed: HTTP ${e.code()} ${e.message()}")
                    val errorBody = e.response()?.errorBody()?.string()
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Upload failed: HTTP ${e.code()} ${errorBody ?: e.message()}")
                    }
                } catch (e: Exception) {
                    Timber.tag("DocumentsScreen").e(e, "Upload failed: ${e.message}")
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Upload failed: ${e.message ?: "Unknown error"}")
                    }
                } finally {
                    isUploading = false
                }
            }
        } else {
            Timber.w("File picker: Invalid uri=$uri or documentType=$selectedDocumentType")
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Please select a file and document type")
            }
        }
    }

    val scannerOptions = GmsDocumentScannerOptions.Builder()
        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
        .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
        .setPageLimit(if (selectedDocumentType == "Contract") 10 else 1)
        .build()
    val scanner = GmsDocumentScanning.getClient(scannerOptions)
    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        scope.launch {
            if (activityResult.resultCode == Activity.RESULT_OK) {
                val result = GmsDocumentScanningResult.fromActivityResultIntent(activityResult.data)
                if (result != null && selectedDocumentType.isNotEmpty()) {
                    isUploading = true
                    try {
                        val pdfUri = result.pdf?.uri
                        if (pdfUri != null) {
                            uploadFile(localContext, pdfUri, selectedDocumentType, documentTypes, uploadService, isScanned = true)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Scan and upload successful")
                            }
                            // Reload file list
                            loadFiles()
                        } else {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Failed to scan document")
                            }
                            Timber.tag("DocumentsScreen").e("Scanner failed: No PDF URI")
                        }
                    } catch (e: HttpException) {
                        Timber.tag("DocumentsScreen").e(e, "Upload failed: HTTP ${e.code()} ${e.message()}")
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Upload failed: HTTP ${e.code()} ${e.message()}")
                        }
                    } catch (e: Exception) {
                        Timber.tag("DocumentsScreen").e(e, "Upload failed: ${e.message}")
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Upload failed: ${e.message ?: "Unknown error"}")
                        }
                    } finally {
                        isUploading = false
                    }
                } else {
                    Timber.w("Scanner result is null or documentType=$selectedDocumentType")
                }
            } else if (activityResult.resultCode == Activity.RESULT_CANCELED) {
                snackbarHostState.showSnackbar("Scan cancelled")
            } else {
                snackbarHostState.showSnackbar("Scan failed")
                Timber.tag("DocumentsScreen").e("Scan failed: resultCode=${activityResult.resultCode}")
            }
        }
    }

    // Swipe-to-refresh state
    val refreshState = rememberPullToRefreshState()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Upload Section
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedDocumentType,
                    onValueChange = { },
                    label = { Text("Select Document Type") },
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = true,
                    interactionSource = interactionSource,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.None),
                    trailingIcon = {
                        IconButton(onClick = {
                            expanded = true
                            focusManager.clearFocus()
                        }) {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Dropdown"
                            )
                        }
                    }
                )
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = {
                        expanded = false
                        focusManager.clearFocus()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = 8.dp) // Slight offset to avoid clipping
                ) {
                    documentTypes.forEach { (type, _) ->
                        DropdownMenuItem(
                            text = { Text(type) },
                            onClick = {
                                selectedDocumentType = type
                                expanded = false
                                focusManager.clearFocus()
                                Timber.d("DocumentsScreen: Selected document type: $type, expanded=$expanded")
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Buttons in a Row (50:50)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = {
                        if (selectedDocumentType.isEmpty()) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Please select a document type")
                            }
                            Timber.w("Scan failed: No document type selected")
                        } else {
                            val token = PrefsHelper.getToken(localContext)
                            if (token?.isEmpty() != false) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Please log in to scan documents")
                                    navController.navigate("login") {
                                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                                Timber.w("Scan failed: No valid JWT")
                            } else {
                                coroutineScope.launch {
                                    try {
                                        val activity = localContext.findActivity()
                                        if (activity != null) {
                                            scanner.getStartScanIntent(activity)
                                                .addOnSuccessListener { intentSender ->
                                                    scannerLauncher.launch(
                                                        IntentSenderRequest.Builder(intentSender).build()
                                                    )
                                                }
                                                .addOnFailureListener { e ->
                                                    coroutineScope.launch {
                                                        snackbarHostState.showSnackbar("Failed to start scanner: ${e.message ?: "Unknown error"}")
                                                    }
                                                }
                                        } else {
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Activity context required to start scanner")
                                            }
                                            Timber.tag("DocumentsScreen").e("Scan failed: No activity context")
                                        }
                                    } catch (e: Exception) {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Scanner error: ${e.message ?: "Unknown error"}")
                                        }
                                        Timber.tag("DocumentsScreen").e(e, "Scanner error")
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 4.dp),
                    content = {
                        Text("Scan Document")
                    }
                )

                Button(
                    onClick = {
                        if (selectedDocumentType.isEmpty()) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Please select a document type")
                            }
                            Timber.w("Upload failed: No document type selected")
                        } else {
                            val token = PrefsHelper.getToken(localContext)
                            if (token?.isEmpty() != false) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Please log in to upload documents")
                                    navController.navigate("login") {
                                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                                Timber.w("Upload failed: No valid JWT")
                            } else {
                                filePicker.launch("application/pdf,image/jpeg,image/png")
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp),
                    content = {
                        Text("Upload Document")
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // File List Section
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    loadFiles()
                },
                state = refreshState,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (files.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Uploaded Documents",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyColumn {
                                items(files) { file ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (file.extension in listOf("jpg", "jpeg", "png")) {
                                            AsyncImage(
                                                model = file.url,
                                                contentDescription = file.name,
                                                modifier = Modifier.size(50.dp)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.PictureAsPdf,
                                                contentDescription = file.name,
                                                modifier = Modifier.size(50.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(file.name, modifier = Modifier.weight(1f))
                                        IconButton(
                                            onClick = {
                                                val currentTime = System.currentTimeMillis()
                                                val fullFilename = file.url.substringAfterLast("/").substringBefore("?").replace("+", " ").replace("%20", " ").trim()
                                                if (currentTime - lastClickTime > 2000) {
                                                    lastClickTime = currentTime
                                                    lastNavigatedFile = fullFilename // Keep for logging
                                                    navigationAttemptCount++
                                                    scope.launch {
                                                        try {
                                                            // Use the original filename from R2
                                                            val encodedFilename = URLEncoder.encode(fullFilename, "UTF-8")
                                                            val encodedUrl = URLEncoder.encode(file.url, "UTF-8")
                                                           // Replace existing DocumentViewerScreen
                                                            navController.navigate("documentViewer/$encodedFilename/$encodedUrl") {
                                                                popUpTo("documentViewer/{filename}/{url}") { inclusive = true }
                                                                launchSingleTop = true
                                                                restoreState = false
                                                            }
                                                        } catch (e: Exception) {
                                                            Timber.e(e, "DocumentsScreen: Navigation failed")
                                                            snackbarHostState.showSnackbar("Navigation failed: ${e.message}")
                                                        }
                                                    }
                                                } else {
                                                    Timber.v("DocumentsScreen: Navigation debounced for filename=$fullFilename, lastClickTime=$lastClickTime, currentTime=$currentTime")
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Visibility,
                                                contentDescription = "View ${file.name}"
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    try {
                                                        val fullFilename = file.url.substringAfterLast("/").substringBefore("?").replace("+", " ").replace("%20", " ").trim()
                                                        val downloadId = downloadFile(context, file.url, fullFilename.substringBeforeLast("."), fullFilename.substringAfterLast("."))
                                                        snackbarHostState.showSnackbar("Downloading $fullFilename")
                                                        (context.findActivity() as? MainActivity)?.storeDownload(downloadId, fullFilename)
                                                    } catch (e: Exception) {
                                                        Timber.tag("DocumentsScreen").e(e, "Download failed")
                                                        snackbarHostState.showSnackbar("Download failed: ${e.message}")
                                                    }
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Download,
                                                contentDescription = "Download ${file.name}"
                                            )
                                        }
                                    }
                                    if (file != files.last()) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 8.dp),
                                            thickness = 1.dp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = "No documents found",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            if (isUploading) {
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator()
            }
        }
    }
}

private suspend fun uploadFile(
    context: Context,
    uri: Uri,
    docType: String,
    docTypes: List<Pair<String, String>>,
    uploadService: UploadService,
    isScanned: Boolean = false
) {
    val contentResolver = context.contentResolver
    val (givenName, surname) = PrefsHelper.getWorkerDetails(context)
    val capitalizedGivenName = givenName.split(" ").joinToString(" ") { it.lowercase().replaceFirstChar { it -> it.uppercaseChar() } }
    val capitalizedSurname = surname.split(" ").joinToString(" ") { it.lowercase().replaceFirstChar { it -> it.uppercaseChar() } }
    val code = docTypes.find { it.first == docType }?.second ?: run {
        Timber.tag("DocumentsScreen").e("Invalid docType %s", docType)
        return
    }
    val extension = if (isScanned) {
        "pdf"
    } else {
        val mimeType = contentResolver.getType(uri)?.lowercase()
        when (mimeType) {
            "application/pdf" -> "pdf"
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            else -> {
                Timber.tag("DocumentsScreen").e("Unsupported MIME type: %s", mimeType)
                return
            }
        }
    }
    val fileName = "$capitalizedGivenName $capitalizedSurname - $code.$extension"
    val tempFile = File(context.cacheDir, fileName)

    try {
        contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    } catch (e: Exception) {
        Timber.tag("DocumentsScreen").e(e, "File copy failed")
        throw e
    }

    val contentType = if (isScanned) {
        "application/pdf".toMediaType()
    } else {
        contentResolver.getType(uri)?.toMediaType() ?: "application/octet-stream".toMediaType()
    }
    val requestFile = tempFile.asRequestBody(contentType)
    val body = MultipartBody.Part.createFormData("file", fileName, requestFile)
    val token = PrefsHelper.getToken(context)
    try {
        val response = uploadService.uploadFile("Bearer $token", body)
    } catch (e: HttpException) {
        Timber.tag("DocumentsScreen").e(e, "Upload failed: HTTP")
        val errorBody = e.response()?.errorBody()?.string()
        throw HttpException(e.response()!!).apply { initCause(Exception("HTTP ${e.code()}: ${errorBody ?: e.message()}")) }
    } catch (e: Exception) {
        throw e
    }
}

private fun downloadFile(context: Context, url: String, name: String, extension: String): Long {
    val normalizedName = name.replace("+", " ").replace("%20", " ").trim()
    val targetFilename = "$normalizedName.$extension"
    val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), targetFilename)
    if (file.exists()) {
        file.delete()
    }
    val request = DownloadManager.Request(url.toUri())
        .setTitle("Downloading $normalizedName")
        .setDescription("Downloading $targetFilename")
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, targetFilename)
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setAllowedOverMetered(true)
        .setAllowedOverRoaming(true)
    val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val downloadId = downloadManager.enqueue(request)
    Timber.i("Started download: $targetFilename with ID: $downloadId")
    (context.findActivity() as? MainActivity)?.storeDownload(downloadId, targetFilename)
    return downloadId
}