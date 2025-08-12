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
import android.util.Log
import androidx.compose.material3.HorizontalDivider
import java.net.URLEncoder

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
                snackbarHostState.showSnackbar("Please log in to view documents")
                Log.w("EmpowerSWR", "File list failed: No valid JWT")
            } else {
                val fileList = listFilesService.listFiles("Bearer $token")
                files = fileList ?: emptyList()
                Log.d("EmpowerSWR", "Fetched files: $fileList")
            }
        } catch (e: HttpException) {
            Log.e("EmpowerSWR", "File list failed: HTTP ${e.code()} ${e.message()}", e)
            snackbarHostState.showSnackbar("Failed to load files: HTTP ${e.code()}")
        } catch (e: Exception) {
            Log.e("EmpowerSWR", "File list failed: ${e.message}", e)
            snackbarHostState.showSnackbar("Failed to load files: ${e.message}")
        } finally {
            isLoadingFiles = false
        }
    }

    Log.d("EmpowerSWR", "DocumentsScreen composable rendered")

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        Log.d("EmpowerSWR", "File picker result: uri=$uri")
        if (uri != null && selectedDocumentType.isNotEmpty()) {
            isUploading = true
            scope.launch {
                try {
                    Log.d("EmpowerSWR", "Starting file picker upload: $uri")
                    uploadFile(context, uri, selectedDocumentType, documentTypes, uploadService, isScanned = false)
                    snackbarHostState.showSnackbar("Upload successful")
                    // Reload file list
                    val token = PrefsHelper.getJwtToken(context)
                    if (!token.isEmpty()) {
                        val fileList = listFilesService.listFiles("Bearer $token")
                        files = fileList ?: emptyList()
                        Log.d("EmpowerSWR", "Refreshed files: $fileList")
                    }
                } catch (e: HttpException) {
                    Log.e("EmpowerSWR", "Upload failed: HTTP ${e.code()} ${e.message()}", e)
                    val errorBody = e.response()?.errorBody()?.string()
                    snackbarHostState.showSnackbar("Upload failed: HTTP ${e.code()} ${errorBody ?: e.message()}")
                } catch (e: Exception) {
                    Log.e("EmpowerSWR", "Upload failed: ${e.message}", e)
                    snackbarHostState.showSnackbar("Upload failed: ${e.message ?: "Unknown error"}")
                } finally {
                    isUploading = false
                }
            }
        } else {
            Log.w("EmpowerSWR", "File picker: Invalid uri=$uri or documentType=$selectedDocumentType")
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
        Log.d("EmpowerSWR", "Scanner result: resultCode=${activityResult.resultCode}")
        scope.launch {
            if (activityResult.resultCode == Activity.RESULT_OK) {
                val result = GmsDocumentScanningResult.fromActivityResultIntent(activityResult.data)
                if (result != null && selectedDocumentType.isNotEmpty()) {
                    isUploading = true
                    try {
                        val pdfUri = result.getPdf()?.uri
                        Log.d("EmpowerSWR", "Scanner PDF URI: $pdfUri")
                        if (pdfUri != null) {
                            Log.d("EmpowerSWR", "Starting scanner upload: $pdfUri")
                            uploadFile(context, pdfUri, selectedDocumentType, documentTypes, uploadService, isScanned = true)
                            snackbarHostState.showSnackbar("Scan and upload successful")
                            // Reload file list
                            val token = PrefsHelper.getJwtToken(context)
                            if (!token.isEmpty()) {
                                val fileList = listFilesService.listFiles("Bearer $token")
                                files = fileList ?: emptyList()
                                Log.d("EmpowerSWR", "Refreshed files: $fileList")
                            }
                        } else {
                            snackbarHostState.showSnackbar("Failed to scan document")
                            Log.e("EmpowerSWR", "Scanner failed: No PDF URI")
                        }
                    } catch (e: HttpException) {
                        Log.e("EmpowerSWR", "Upload failed: HTTP ${e.code()} ${e.message()}", e)
                        val errorBody = e.response()?.errorBody()?.string()
                        snackbarHostState.showSnackbar("Upload failed: HTTP ${e.code()} ${errorBody ?: e.message()}")
                    } catch (e: Exception) {
                        Log.e("EmpowerSWR", "Upload failed: ${e.message}", e)
                        snackbarHostState.showSnackbar("Upload failed: ${e.message ?: "Unknown error"}")
                    } finally {
                        isUploading = false
                    }
                } else {
                    Log.w("EmpowerSWR", "Scanner result is null or documentType=$selectedDocumentType")
                }
            } else if (activityResult.resultCode == Activity.RESULT_CANCELED) {
                snackbarHostState.showSnackbar("Scan cancelled")
                Log.d("EmpowerSWR", "Scan cancelled")
            } else {
                snackbarHostState.showSnackbar("Scan failed")
                Log.e("EmpowerSWR", "Scan failed: resultCode=${activityResult.resultCode}")
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
                        IconButton(onClick = {
                            Log.d("EmpowerSWR", "Dropdown clicked")
                            expanded = true
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
                        Log.d("EmpowerSWR", "Dropdown dismissed")
                        expanded = false
                    }
                ) {
                    documentTypes.forEach { (type, _) ->
                        DropdownMenuItem(
                            text = { Text(type) },
                            onClick = {
                                Log.d("EmpowerSWR", "Selected document type: $type")
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
                        Log.d("EmpowerSWR", "Scan Document button clicked")
                        if (selectedDocumentType.isEmpty()) {
                            Toast.makeText(context, "Select a document type", Toast.LENGTH_SHORT).show()
                            Log.w("EmpowerSWR", "Scan failed: No document type selected")
                        } else {
                            val token = PrefsHelper.getJwtToken(context)
                            if (token.isEmpty()) {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Please log in to scan documents")
                                }
                                Log.w("EmpowerSWR", "Scan failed: No valid JWT")
                            } else {
                                val activity = context as? Activity
                                if (activity != null) {
                                    scope.launch {
                                        try {
                                            scanner.getStartScanIntent(activity)
                                                .addOnSuccessListener { intentSender ->
                                                    Log.d("EmpowerSWR", "Scanner intent created")
                                                    scannerLauncher.launch(
                                                        IntentSenderRequest.Builder(intentSender).build()
                                                    )
                                                }
                                                .addOnFailureListener { e ->
                                                    Log.e("EmpowerSWR", "Scanner failed to start: ${e.message}", e)
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar("Failed to start scanner: ${e.message}")
                                                    }
                                                }
                                        } catch (e: Exception) {
                                            Log.e("EmpowerSWR", "Scanner intent creation failed: ${e.message}", e)
                                            snackbarHostState.showSnackbar("Scanner error: ${e.message}")
                                        }
                                    }
                                } else {
                                    Toast.makeText(context, "Activity context required", Toast.LENGTH_SHORT).show()
                                    Log.e("EmpowerSWR", "Scan failed: No activity context")
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
                        Log.d("EmpowerSWR", "Upload Document button clicked")
                        if (selectedDocumentType.isEmpty()) {
                            Toast.makeText(context, "Select a document type", Toast.LENGTH_SHORT).show()
                            Log.w("EmpowerSWR", "Upload failed: No document type selected")
                        } else {
                            val token = PrefsHelper.getJwtToken(context)
                            if (token.isEmpty()) {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Please log in to upload documents")
                                }
                                Log.w("EmpowerSWR", "Upload failed: No valid JWT")
                            } else {
                                Log.d("EmpowerSWR", "Launching file picker")
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
                                            scope.launch {
                                                try {
                                                    // Extract full filename from URL
                                                    val fullFilename = file.url.substringAfterLast("/").substringBefore("?")
                                                    val encodedFilename = URLEncoder.encode(fullFilename, "UTF-8")
                                                    val encodedUrl = URLEncoder.encode(file.url, "UTF-8")
                                                    navController.navigate("documentViewer/$encodedFilename/$encodedUrl")
                                                    Log.d("EmpowerSWR", "Navigating to DocumentViewer with filename: $fullFilename, URL: ${file.url}")
                                                } catch (e: Exception) {
                                                    Log.e("EmpowerSWR", "Navigation failed: ${e.message}", e)
                                                    snackbarHostState.showSnackbar("Navigation failed: ${e.message}")
                                                }
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
                                                    downloadFile(context, file.url, file.name, file.extension)
                                                    snackbarHostState.showSnackbar("Downloading ${file.name}")
                                                } catch (e: Exception) {
                                                    Log.e("EmpowerSWR", "Download failed: ${e.message}", e)
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
    Log.d("EmpowerSWR", "Starting upload for docType: $docType, uri: $uri, isScanned: $isScanned")
    val contentResolver = context.contentResolver
    val (givenName, surname) = PrefsHelper.getWorkerDetails(context)
    val capitalizedGivenName = givenName.split(" ").joinToString(" ") { it.lowercase().replaceFirstChar { it.uppercaseChar() } }
    val capitalizedSurname = surname.split(" ").joinToString(" ") { it.lowercase().replaceFirstChar { it.uppercaseChar() } }
    val code = docTypes.find { it.first == docType }?.second ?: run {
        Log.e("EmpowerSWR", "Invalid docType: $docType")
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
                Log.e("EmpowerSWR", "Unsupported MIME type: ${contentResolver.getType(uri)}")
                return
            }
        }
    }
    val fileName = "$capitalizedGivenName $capitalizedSurname - $code.$extension"
    Log.d("EmpowerSWR", "File name: $fileName")
    val tempFile = File(context.cacheDir, fileName)

    try {
        contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        Log.d("EmpowerSWR", "File copied to: ${tempFile.absolutePath}, size: ${tempFile.length()}")
    } catch (e: Exception) {
        Log.e("EmpowerSWR", "File copy failed: ${e.message}", e)
        throw e
    }

    val contentType = if (isScanned) {
        "application/pdf".toMediaType()
    } else {
        contentResolver.getType(uri)?.toMediaType() ?: "application/octet-stream".toMediaType()
    }
    Log.d("EmpowerSWR", "Content-Type: $contentType")
    val requestFile = tempFile.asRequestBody(contentType)
    val body = MultipartBody.Part.createFormData("file", fileName, requestFile)
    val token = PrefsHelper.getJwtToken(context)
    Log.d("EmpowerSWR", "Sending request with token: $token")
    try {
        val response = uploadService.uploadFile("Bearer $token", body)
        Log.d("EmpowerSWR", "Upload response: $response")
    } catch (e: HttpException) {
        Log.e("EmpowerSWR", "Upload failed: HTTP ${e.code()} ${e.message()}", e)
        val errorBody = e.response()?.errorBody()?.string()
        throw HttpException(e.response()!!).apply { initCause(Exception("HTTP ${e.code()}: ${errorBody ?: e.message()}")) }
    } catch (e: Exception) {
        Log.e("EmpowerSWR", "Upload failed: ${e.message}", e)
        throw e
    }
}

private fun downloadFile(context: Context, url: String, name: String, extension: String) {
    val request = DownloadManager.Request(Uri.parse(url))
        .setTitle("Downloading $name")
        .setDescription("Downloading $name.$extension")
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "$name.$extension")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setAllowedOverMetered(true)
        .setAllowedOverRoaming(true)
    val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    downloadManager.enqueue(request)
    Log.d("EmpowerSWR", "Started download: $url")
}