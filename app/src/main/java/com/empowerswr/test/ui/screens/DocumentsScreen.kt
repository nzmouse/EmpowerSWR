package com.empowerswr.test.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.empowerswr.test.network.UploadService
import com.empowerswr.test.PrefsHelper
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.HttpException
import java.io.File
import android.util.Log
import com.empowerswr.test.network.NetworkModule

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(
    uploadService: UploadService = NetworkModule.uploadService,
    navController: NavController? = null,
    type: String? = null,
    expiryYY: String? = null,
    from: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var isUploading by remember { mutableStateOf(false) }
    var selectedDocumentType by remember(type, from) { mutableStateOf(if (from == "passport") "Passport" else (type ?: "")) }
    val documentTypes = listOf(
        "Passport" to "PPT",
        "DFAT Privacy/Consent" to "PRI",
        "National ID Card" to "NID",
        "Birth Certificate" to "BC",
        "Driving Licence" to "DL",
        "Police Clearance" to "PC",
        "Medical" to "MED",
        "Contract" to "CON",
        "Spouse Letter" to "SPO",
        "Chief/Pastor Letter" to "REF"
    )

    var expanded by remember { mutableStateOf(from == "passport") }

    Log.d("EmpowerSWR", "DocumentsScreen composable rendered with type=$type, expiryYY=$expiryYY, from=$from, selectedDocumentType=$selectedDocumentType")
    Log.d("EmpowerSWR", "NavController hash: ${navController.hashCode()}")
    navController?.currentBackStack?.value?.let { backStack ->
        Log.d("EmpowerSWR", "DocumentsScreen back stack: ${backStack.map { it.destination.route }}")
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        Log.d("EmpowerSWR", "File picker result: uri=$uri")
        if (uri != null && selectedDocumentType.isNotEmpty()) {
            isUploading = true
            scope.launch {
                try {
                    Log.d("EmpowerSWR", "Starting file picker upload: $uri")
                    uploadFile(context, uri, selectedDocumentType, documentTypes, uploadService, isScanned = false, expiryYY = if (from == "passport") expiryYY else null)
                    snackbarHostState.showSnackbar("Upload successful")
                    if (from == "passport") {
                        navController?.popBackStack()
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
            scope.launch {
                Log.w("EmpowerSWR", "File picker: Invalid uri=$uri or documentType=$selectedDocumentType")
                snackbarHostState.showSnackbar("Please select a document type and file")
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
                            uploadFile(context, pdfUri, selectedDocumentType, documentTypes, uploadService, isScanned = true, expiryYY = if (from == "passport") expiryYY else null)
                            snackbarHostState.showSnackbar("Scan and upload successful")
                            if (from == "passport") {
                                navController?.popBackStack()
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
                    snackbarHostState.showSnackbar("Please select a document type")
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
        topBar = {
            TopAppBar(
                title = { Text("Upload Document") },
                navigationIcon = {
                    IconButton(onClick = {
                        navController?.popBackStack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Upload Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Upload",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    key(selectedDocumentType) {
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
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                Log.d("EmpowerSWR", "Scan Document button clicked")
                                if (selectedDocumentType.isEmpty()) {
                                    scope.launch {
                                        Toast.makeText(context, "Select a document type", Toast.LENGTH_SHORT).show()
                                        Log.w("EmpowerSWR", "Scan failed: No document type selected")
                                        snackbarHostState.showSnackbar("Please select a document type")
                                    }
                                } else {
                                    val token = PrefsHelper.getJwtToken(context)
                                    if (token.isEmpty()) {
                                        scope.launch {
                                            Toast.makeText(context, "Please log in to scan documents", Toast.LENGTH_SHORT).show()
                                            Log.w("EmpowerSWR", "Scan failed: No valid JWT")
                                            snackbarHostState.showSnackbar("Please log in to scan documents")
                                        }
                                    } else {
                                        val activity = context as? Activity
                                        if (activity != null) {
                                            scanner.getStartScanIntent(activity)
                                                .addOnSuccessListener { intentSender ->
                                                    Log.d("EmpowerSWR", "Scanner intent created")
                                                    scannerLauncher.launch(
                                                        IntentSenderRequest.Builder(intentSender).build()
                                                    )
                                                }
                                                .addOnFailureListener { e ->
                                                    scope.launch {
                                                        Toast.makeText(context, "Failed to start scanner: ${e.message}", Toast.LENGTH_SHORT).show()
                                                        Log.e("EmpowerSWR", "Scanner failed to start: ${e.message}", e)
                                                        snackbarHostState.showSnackbar("Failed to start scanner: ${e.message}")
                                                    }
                                                }
                                        } else {
                                            scope.launch {
                                                Toast.makeText(context, "Activity context required", Toast.LENGTH_SHORT).show()
                                                Log.e("EmpowerSWR", "Scan failed: No activity context")
                                                snackbarHostState.showSnackbar("Activity context required")
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Scan Document")
                        }
                        Button(
                            onClick = {
                                Log.d("EmpowerSWR", "Upload Document button clicked")
                                if (selectedDocumentType.isEmpty()) {
                                    scope.launch {
                                        Toast.makeText(context, "Select a document type", Toast.LENGTH_SHORT).show()
                                        Log.w("EmpowerSWR", "Upload failed: No document type selected")
                                        snackbarHostState.showSnackbar("Please select a document type")
                                    }
                                } else {
                                    val token = PrefsHelper.getJwtToken(context)
                                    if (token.isEmpty()) {
                                        scope.launch {
                                            Toast.makeText(context, "Please log in to upload documents", Toast.LENGTH_SHORT).show()
                                            Log.w("EmpowerSWR", "Upload failed: No valid JWT")
                                            snackbarHostState.showSnackbar("Please log in to upload documents")
                                        }
                                    } else {
                                        Log.d("EmpowerSWR", "Launching file picker")
                                        filePicker.launch("application/pdf,image/jpeg,image/png")
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Upload Document")
                        }
                    }
                }
            }

            // Ol Dokumen Blong Mi Button (hide if from == "passport")
            if (from != "passport") {
                Button(
                    onClick = {
                        Log.d("EmpowerSWR", "Ol Dokumen Blong Mi button clicked")
                        scope.launch {
                            try {
                                if (navController == null) {
                                    Log.e("EmpowerSWR", "Navigation failed: navController is null")
                                    snackbarHostState.showSnackbar("Navigation not available")
                                } else {
                                    Log.d("EmpowerSWR", "Navigating to document-list")
                                    navController.navigate("document-list")
                                }
                            } catch (e: IllegalArgumentException) {
                                Log.e("EmpowerSWR", "Navigation failed: Invalid destination document-list", e)
                                snackbarHostState.showSnackbar("Error: Invalid navigation destination")
                            } catch (e: Exception) {
                                Log.e("EmpowerSWR", "Navigation failed: ${e.message}", e)
                                snackbarHostState.showSnackbar("Navigation error: ${e.message}")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text(
                        text = "Ol Dokumen Blong Mi",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            if (isUploading) {
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
    isScanned: Boolean,
    expiryYY: String? = null
) {
    Log.d("EmpowerSWR", "Starting upload for docType: $docType, uri: $uri, isScanned: $isScanned, expiryYY: $expiryYY")
    val contentResolver = context.contentResolver
    val (givenName, surname) = PrefsHelper.getWorkerDetails(context)
    val capitalizedGivenName = givenName.split(" ").joinToString(" ") { it.lowercase().replaceFirstChar { it.uppercaseChar() } }
    val capitalizedSurname = surname.split(" ").joinToString(" ") { it.lowercase().replaceFirstChar { it.uppercaseChar() } }
    var code = docTypes.find { it.first == docType }?.second ?: run {
        Log.e("EmpowerSWR", "Invalid docType: $docType")
        return
    }
    if (docType == "Passport" && expiryYY != null) {
        code += expiryYY
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
            tempFile.outputStream().use { output -> input.copyTo(output) }
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
        val response = uploadService.uploadFile("Bearer $token", body, "false")
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