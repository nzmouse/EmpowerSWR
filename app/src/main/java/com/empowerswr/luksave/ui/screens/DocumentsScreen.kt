package com.empowerswr.luksave.ui.screens

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import androidx.navigation.compose.currentBackStackEntryAsState
import com.empowerswr.luksave.MainActivity
import com.empowerswr.luksave.findActivity
import java.net.URLEncoder
import timber.log.Timber
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
    var lastClickTime by rememberSaveable { mutableStateOf(0L) } // Debounce navigation
    var navigationAttemptCount by remember { mutableStateOf(0) } // Navigation attempt counter
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

    // Load files on composition
    LaunchedEffect(Unit) {
        isLoadingFiles = true
        try {
            val token = PrefsHelper.getJwtToken(context)
            if (token.isEmpty()) {
                snackbarHostState.showSnackbar("Please Log-in to view documents")
                Timber.w("File list failed: No valid JWT")
            } else {
                val fileList = listFilesService.listFiles("Bearer $token")
                files = fileList?.map { item ->
                    item.copy(name = item.name.replace("+", " ").replace("%20", " ").trim())
                } ?: emptyList()
                Timber.i("Fetched %d files", files.size)
            }
        } catch (e: HttpException) {
            Timber.tag("DocumentsScreen").e(e, "File list failed: HTTP ${e.code()} ${e.message()}")
            snackbarHostState.showSnackbar("Failed to load files: HTTP ${e.code()}")
        } catch (e: Exception) {
            snackbarHostState.showSnackbar("Failed to load files: ${e.message}")
        } finally {
            isLoadingFiles = false
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && selectedDocumentType.isNotEmpty()) {
            isUploading = true
            scope.launch {
                try {
                    uploadFile(context, uri, selectedDocumentType, documentTypes, uploadService, isScanned = false)
                    snackbarHostState.showSnackbar("Upload successful")
                    // Reload file list
                    val token = PrefsHelper.getJwtToken(context)
                    if (!token.isEmpty()) {
                        val fileList = listFilesService.listFiles("Bearer $token")
                        files = fileList?.map { item ->
                            item.copy(name = item.name.replace("+", " ").replace("%20", " ").trim())
                        } ?: emptyList()
                        Timber.i("Refreshed %d files", files.size)
                    }
                } catch (e: HttpException) {
                    Timber.tag("DocumentsScreen").e(e, "Upload failed: HTTP ${e.code()} ${e.message()}")
                    val errorBody = e.response()?.errorBody()?.string()
                    snackbarHostState.showSnackbar("Upload failed: HTTP ${e.code()} ${errorBody ?: e.message()}")
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Upload failed: ${e.message ?: "Unknown error"}")
                } finally {
                    isUploading = false
                }
            }
        } else {
            Timber.w("File picker: Invalid uri=$uri or documentType=$selectedDocumentType")
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
                            uploadFile(context, pdfUri, selectedDocumentType, documentTypes, uploadService, isScanned = true)
                            snackbarHostState.showSnackbar("Scan and upload successful")
                            // Reload file list
                            val token = PrefsHelper.getJwtToken(context)
                            if (!token.isEmpty()) {
                                val fileList = listFilesService.listFiles("Bearer $token")
                                files = fileList.map { item ->
                                    item.copy(name = item.name.replace("+", " ").replace("%20", " ").trim())
                                } ?: emptyList()
                                Timber.i("Refreshed %d files", files.size)
                            }
                        } else {
                            snackbarHostState.showSnackbar("Failed to scan document")
                            Timber.tag("DocumentsScreen").e("Scanner failed: No PDF URI")
                        }
                    } catch (e: HttpException) {
                        Timber.tag("DocumentsScreen").e("Upload failed: HTTP ${e.code()} ${e.message()}", e)
                        val errorBody = e.response()?.errorBody()?.string()
                        snackbarHostState.showSnackbar("Upload failed: HTTP ${e.code()} ${errorBody ?: e.message()}")
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("Upload failed: ${e.message ?: "Unknown error"}")
                    } finally {
                        isUploading = false
                    }
                } else {
                    Timber.w("Scanner result is null or documentType=$selectedDocumentType")
                }
            } else if (activityResult.resultCode == Activity.RESULT_CANCELED) {
                snackbarHostState.showSnackbar("Scan cancelled")
                Timber.i("Scan cancelled")
            } else {
                snackbarHostState.showSnackbar("Scan failed")
                Timber.tag("DocumentsScreen").e("Scan failed: resultCode=${activityResult.resultCode}")
            }
        }
    }

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
            Box {
                OutlinedTextField(
                    value = selectedDocumentType,
                    onValueChange = { },
                    label = { Text("Select Document Type") },
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = { expanded = true }) {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Dropdown"
                            )
                        }
                    }
                )
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    documentTypes.forEach { (type, _) ->
                        DropdownMenuItem(
                            text = { Text(type) },
                            onClick = {
                                selectedDocumentType = type
                                expanded = false
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
                            Toast.makeText(context, "Select a document type", Toast.LENGTH_SHORT).show()
                            Timber.w("Scan failed: No document type selected")
                        } else {
                            val token = PrefsHelper.getJwtToken(context)
                            if (token.isEmpty()) {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Please Log-in to scan documents")
                                }
                                Timber.w("Scan failed: No valid JWT")
                            } else {
                                val activity = context as? Activity
                                if (activity != null) {
                                    scope.launch {
                                        try {
                                            scanner.getStartScanIntent(activity)
                                                .addOnSuccessListener { intentSender ->
                                                    scannerLauncher.launch(
                                                        IntentSenderRequest.Builder(intentSender).build()
                                                    )
                                                }
                                                .addOnFailureListener { e ->
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar("Failed to start scanner: ${e.message}")
                                                    }
                                                }
                                        } catch (e: Exception) {
                                            snackbarHostState.showSnackbar("Scanner error: ${e.message}")
                                        }
                                    }
                                } else {
                                    Toast.makeText(context, "Activity context required", Toast.LENGTH_SHORT).show()
                                    Timber.tag("DocumentsScreen").e("Scan failed: No activity context")
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 4.dp)
                ) {
                    Text("Scan Document")
                }

                Button(
                    onClick = {
                        if (selectedDocumentType.isEmpty()) {
                            Toast.makeText(context, "Select a document type", Toast.LENGTH_SHORT).show()
                            Timber.w("Upload failed: No document type selected")
                        } else {
                            val token = PrefsHelper.getJwtToken(context)
                            if (token.isEmpty()) {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Please Log-in to upload documents")
                                }
                                Timber.w("Upload failed: No valid JWT")
                            } else {
                                filePicker.launch("application/pdf,image/jpeg,image/png")
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp)
                ) {
                    Text("Upload Document")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // File List Section
            if (isLoadingFiles) {
                CircularProgressIndicator()
            } else if (files.isNotEmpty()) {
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
                                            if (currentTime - lastClickTime > 2000) { // Keep time-based debounce
                                                lastClickTime = currentTime
                                                var lastNavigatedFile = fullFilename
                                                navigationAttemptCount++
                                                Timber.d("DocumentsScreen: Navigation attempt $navigationAttemptCount for filename=$fullFilename")
                                                scope.launch {
                                                    try {
                                                        // Map to nickname for navigation
                                                        val docTypeCode = documentTypes.find { fullFilename.endsWith("- ${it.second}.pdf") }?.second
                                                            ?: documentTypes.find { fullFilename == "${it.first}.pdf" }?.second
                                                        val nickname = docTypeCode?.let { documentTypes.find { it.second == docTypeCode }?.first } ?: fullFilename.substringBeforeLast(".")
                                                        // Use the base filename without checking for existence
                                                        val targetNickname = "$nickname.pdf" // Always use base filename, allowing overwrite
                                                        val encodedFilename = URLEncoder.encode(targetNickname, "UTF-8")
                                                        val encodedUrl = URLEncoder.encode(file.url, "UTF-8")
                                                        Timber.d("DocumentsScreen: Navigating to DocumentViewer with filename: $targetNickname, URL: ${file.url}")
                                                        // Clear previous documentViewer entries
                                                        navController.popBackStack("documentViewer/{filename}/{url}", inclusive = true, saveState = false)
                                                        navController.navigate("documentViewer/$encodedFilename/$encodedUrl") {
                                                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                                                            launchSingleTop = true
                                                            restoreState = false
                                                        }
                                                        // Log back stack after navigation
                                                        val backStack = navController.currentBackStack.value.map { it.destination.route }
                                                        Timber.d("DocumentsScreen: Back stack after navigation: $backStack")
                                                    } catch (e: Exception) {
                                                        Timber.e(e, "DocumentsScreen: Navigation failed")
                                                        snackbarHostState.showSnackbar("Navigation failed: ${e.message}")
                                                    }
                                                }
                                            } else {
                                                Timber.v("DocumentsScreen: Navigation debounced for filename=${file.name}, lastClickTime=$lastClickTime, currentTime=$currentTime")
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
                                                    val docTypeCode = documentTypes.find { fullFilename.endsWith("- ${it.second}.pdf") }?.second
                                                        ?: documentTypes.find { fullFilename == "${it.first}.pdf" }?.second
                                                    val nickname = docTypeCode?.let { documentTypes.find { it.second == docTypeCode }?.first } ?: fullFilename.substringBeforeLast(".")
                                                    val targetNickname = (0..10).map { i ->
                                                        if (i == 0) "$nickname.pdf" else "$nickname-$i.pdf"
                                                    }.find { nick ->
                                                        !File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), nick).exists()
                                                    } ?: fullFilename
                                                    val downloadId = downloadFile(context, file.url, targetNickname.substringBeforeLast("."), targetNickname.substringAfterLast("."))
                                                    snackbarHostState.showSnackbar("Downloading $targetNickname")
                                                    (context.findActivity() as? MainActivity)?.storeDownload(downloadId, targetNickname)
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
                Text("No documents found", style = MaterialTheme.typography.bodyMedium)
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
    Timber.i("Starting upload")
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
        when (contentResolver.getType(uri)?.lowercase()) {
            "application/pdf" -> "pdf"
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            else -> {
                Timber.tag("DocumentsScreen").e("Unsupported MIME type: %s", contentResolver.getType(uri))
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
    val token = PrefsHelper.getJwtToken(context)
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
    val request = DownloadManager.Request(url.toUri())
        .setTitle("Downloading $normalizedName")
        .setDescription("Downloading $normalizedName.$extension")
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "$normalizedName.$extension")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setAllowedOverMetered(true)
        .setAllowedOverRoaming(true)
    val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val downloadId = downloadManager.enqueue(request)
    Timber.i("Started download: $normalizedName with ID: $downloadId")
    return downloadId
}