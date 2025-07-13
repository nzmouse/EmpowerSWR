package com.empowerswr.test.ui.screens

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.github.barteksc.pdfviewer.PDFView
import com.github.gcacace.signaturepad.views.SignaturePad
import com.itextpdf.forms.PdfAcroForm
import com.itextpdf.forms.fields.PdfFormField
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import com.itextpdf.kernel.pdf.canvas.PdfCanvasConstants
import com.itextpdf.kernel.colors.ColorConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import com.empowerswr.test.network.NetworkModule
import com.empowerswr.test.PrefsHelper
import com.itextpdf.kernel.pdf.xobject.PdfImageXObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import retrofit2.Response
import okhttp3.ResponseBody

enum class AnnotationMode { NONE, CHECK, SIGNATURE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentViewerScreen(navController: NavController, filename: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var pdfFile by remember { mutableStateOf<File?>(null) }
    var signatureBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var currentPage by remember { mutableStateOf(1) }
    var totalPages by remember { mutableStateOf(1) }
    var annotationMode by remember { mutableStateOf(AnnotationMode.NONE) }
    var formFields by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedField by remember { mutableStateOf<String?>(null) }
    var checkAnnotations by remember { mutableStateOf<List<Pair<Int, Pair<Float, Float>>>>(emptyList()) }
    var signatureAnnotations by remember { mutableStateOf<List<Pair<Int, Pair<Float, Float>>>>(emptyList()) }
    var refreshTrigger by remember { mutableStateOf(0) }
    val jwtToken = PrefsHelper.getJwtToken(context)
    val service = NetworkModule.provideDownloadService(context)

    LaunchedEffect(filename, refreshTrigger) {
        if (jwtToken.isEmpty()) {
            Log.e("DocumentViewer", "JWT token is empty")
            (context as? Activity)?.finish()
            return@LaunchedEffect
        }
        scope.launch(Dispatchers.IO) {
            try {
                val encodedFilename = filename.replace(" ", "%20")
                Log.d("DocumentViewer", "Downloading PDF: $filename (encoded: $encodedFilename)")
                Log.d("DocumentViewer", "Request URL: https://db.nougro.com/api/download.php?filename=$encodedFilename")
                Log.d("DocumentViewer", "Request Headers: Authorization=Bearer $jwtToken")
                val response = service.downloadDocument("Bearer $jwtToken", encodedFilename)
                Log.d("DocumentViewer", "Response Code: ${response.code()}")
                Log.d("DocumentViewer", "Response Headers: ${response.headers()}")
                if (!response.isSuccessful) {
                    val errorBody = response.errorBody()?.string() ?: "No error body"
                    Log.e("DocumentViewer", "Download failed: HTTP ${response.code()} ${response.message()}")
                    Log.e("DocumentViewer", "Error response: $errorBody")
                    val debugFile = File(context.cacheDir, "debug_response_${filename.replace("[^a-zA-Z0-9.-]", "_")}.txt")
                    FileOutputStream(debugFile).use { output ->
                        output.write(errorBody.toByteArray())
                    }
                    Log.d("DocumentViewer", "Response saved to: ${debugFile.absolutePath}")
                    scope.launch(Dispatchers.Main) {
                        snackbarHostState.showSnackbar("Download failed: HTTP ${response.code()}")
                    }
                    return@launch
                }
                val contentType = response.headers()["Content-Type"] ?: "unknown"
                Log.d("DocumentViewer", "Content-Type: $contentType")
                if (!contentType.contains("application/pdf")) {
                    val body = response.body()?.string() ?: "No response body"
                    Log.e("DocumentViewer", "Invalid content type: $contentType")
                    Log.e("DocumentViewer", "Response body: $body")
                    val debugFile = File(context.cacheDir, "debug_response_${filename.replace("[^a-zA-Z0-9.-]", "_")}.txt")
                    FileOutputStream(debugFile).use { output ->
                        output.write(body.toByteArray())
                    }
                    Log.d("DocumentViewer", "Response saved to: ${debugFile.absolutePath}")
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
                // Validate PDF header
                FileInputStream(file).bufferedReader().useLines { lines ->
                    lines.firstOrNull()?.let { line ->
                        Log.d("DocumentViewer", "PDF Header: $line")
                        if (!line.startsWith("%PDF-")) {
                            Log.e("DocumentViewer", "Invalid PDF header: $line")
                            scope.launch(Dispatchers.Main) {
                                snackbarHostState.showSnackbar("Downloaded file is not a valid PDF")
                            }
                            return@launch
                        }
                    }
                }
                PdfReader(file).use { reader ->
                    reader.setUnethicalReading(true)
                    PdfDocument(reader).use { pdfDoc ->
                        totalPages = pdfDoc.numberOfPages
                        val form = PdfAcroForm.getAcroForm(pdfDoc, false)
                        formFields = form?.getAllFormFields()?.keys?.toList() ?: emptyList()
                    }
                }
                pdfFile = file
                Log.d("DocumentViewer", "PDF loaded successfully: ${file.absolutePath}")
            } catch (e: Exception) {
                Log.e("DocumentViewer", "Error loading PDF or form fields", e)
                scope.launch(Dispatchers.Main) {
                    snackbarHostState.showSnackbar("Error loading PDF: ${e.message}")
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { /* Empty title to avoid duplication */ },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = {
                                annotationMode = AnnotationMode.CHECK
                                Log.d("DocumentViewer", "Check mode activated")
                            },
                            modifier = Modifier.background(Color.Transparent)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Check Mode",
                                tint = if (annotationMode == AnnotationMode.CHECK) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        IconButton(
                            onClick = {
                                annotationMode = AnnotationMode.SIGNATURE
                                Log.d("DocumentViewer", "Sign mode activated")
                            },
                            modifier = Modifier.background(Color.Transparent)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Create,
                                contentDescription = "Sign Mode",
                                tint = if (annotationMode == AnnotationMode.SIGNATURE) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        IconButton(
                            onClick = {
                                annotationMode = AnnotationMode.NONE
                                Log.d("DocumentViewer", "Clear mode activated")
                            },
                            modifier = Modifier.background(Color.Transparent)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear Mode",
                                tint = if (annotationMode == AnnotationMode.NONE) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onPrimary
                            )
                        }
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
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            Log.d("DocumentViewer", "Tap detected on Box at offset: $offset")
                            val pdfWidth = 595f
                            val pdfHeight = 842f
                            val viewWidth = this.size.width.toFloat()
                            val viewHeight = this.size.height.toFloat()
                            val x = (offset.x / viewWidth) * pdfWidth
                            val y = pdfHeight - (offset.y / viewHeight) * pdfHeight
                            Log.d("DocumentViewer", "Calculated PDF coordinates: ($x, $y) on page $currentPage in mode $annotationMode")
                            when (annotationMode) {
                                AnnotationMode.CHECK -> {
                                    checkAnnotations = checkAnnotations + Pair(currentPage, Pair(x, y))
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            val tempFile: File = signPdf(
                                                inputFile = pdfFile!!,
                                                signatureBitmap = signatureBitmap,
                                                selectedField = selectedField,
                                                checkAnnotations = checkAnnotations,
                                                pageNumber = currentPage,
                                                signatureAnnotations = signatureAnnotations,
                                                context = context
                                            )
                                            pdfFile = tempFile
                                            scope.launch(Dispatchers.Main) {
                                                snackbarHostState.showSnackbar("Check mark added on page $currentPage")
                                                refreshTrigger++
                                            }
                                        } catch (e: Exception) {
                                            Log.e("DocumentViewer", "Error adding check mark", e)
                                            scope.launch(Dispatchers.Main) {
                                                snackbarHostState.showSnackbar("Error adding check mark: ${e.message}")
                                            }
                                        }
                                    }
                                }
                                AnnotationMode.SIGNATURE -> {
                                    if (signatureBitmap != null) {
                                        signatureAnnotations = signatureAnnotations + Pair(currentPage, Pair(x, y))
                                        scope.launch(Dispatchers.IO) {
                                            try {
                                                val tempFile: File = signPdf(
                                                    inputFile = pdfFile!!,
                                                    signatureBitmap = signatureBitmap,
                                                    selectedField = selectedField,
                                                    checkAnnotations = checkAnnotations,
                                                    pageNumber = currentPage,
                                                    signatureAnnotations = signatureAnnotations,
                                                    context = context
                                                )
                                                pdfFile = tempFile
                                                scope.launch(Dispatchers.Main) {
                                                    snackbarHostState.showSnackbar("Signature added on page $currentPage")
                                                    refreshTrigger++
                                                }
                                            } catch (e: Exception) {
                                                Log.e("DocumentViewer", "Error adding signature", e)
                                                scope.launch(Dispatchers.Main) {
                                                    snackbarHostState.showSnackbar("Error adding signature: ${e.message}")
                                                }
                                            }
                                        }
                                    } else {
                                        scope.launch(Dispatchers.Main) {
                                            snackbarHostState.showSnackbar("Please draw a signature first")
                                        }
                                    }
                                }
                                else -> {}
                            }
                            Log.d("DocumentViewer", "Tap processed: ($x, $y) on page $currentPage in mode $annotationMode")
                        }
                    }
            ) {
                pdfFile?.let { file ->
                    AndroidView(
                        factory = { ctx ->
                            PDFView(ctx, null).apply {
                                setOnTouchListener { _, event ->
                                    Log.d("DocumentViewer", "PDFView touch event: ${event.action}")
                                    false // Let Compose handle the touch
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White),
                        update = { pdfView ->
                            Log.d("DocumentViewer", "Updating PDFView with file: ${file.absolutePath}")
                            pdfView.fromFile(file)
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
                    Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Previous Page")
                }
                Text("Page $currentPage of $totalPages")
                IconButton(
                    onClick = { if (currentPage < totalPages) currentPage++ },
                    enabled = currentPage < totalPages
                ) {
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Next Page")
                }
            }

            CustomSignatureView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(Color.White)
                    .then(
                        if (annotationMode == AnnotationMode.SIGNATURE)
                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary)
                        else Modifier
                    ),
                onSignatureChanged = { bitmap ->
                    signatureBitmap = bitmap
                    Log.d("DocumentViewer", "Signature changed: ${bitmap != null}")
                }
            )

            if (formFields.isNotEmpty()) {
                var expanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedTextField(
                        value = selectedField ?: "Select Form Field",
                        onValueChange = { },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = {
                                expanded = true
                                Log.d("DocumentViewer", "Dropdown opened")
                            }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown")
                            }
                        }
                    )
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = {
                            expanded = false
                            Log.d("DocumentViewer", "Dropdown dismissed")
                        }
                    ) {
                        formFields.forEach { field ->
                            DropdownMenuItem(
                                content = { Text(field) },
                                onClick = {
                                    selectedField = field
                                    expanded = false
                                    Log.d("DocumentViewer", "Form field selected: $field")
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            val tempFile: File = signPdf(
                                                inputFile = pdfFile!!,
                                                signatureBitmap = signatureBitmap,
                                                selectedField = selectedField,
                                                checkAnnotations = checkAnnotations,
                                                pageNumber = currentPage,
                                                signatureAnnotations = signatureAnnotations,
                                                context = context
                                            )
                                            pdfFile = tempFile
                                            scope.launch(Dispatchers.Main) {
                                                snackbarHostState.showSnackbar("Form field $field checked")
                                                refreshTrigger++
                                            }
                                        } catch (e: Exception) {
                                            Log.e("DocumentViewer", "Error checking form field", e)
                                            scope.launch(Dispatchers.Main) {
                                                snackbarHostState.showSnackbar("Error checking form field: ${e.message}")
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(
                    onClick = {
                        signatureBitmap = null
                        Log.d("DocumentViewer", "Clear signature clicked")
                    },
                    enabled = signatureBitmap != null,
                    modifier = Modifier.background(Color.Transparent)
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear Signature",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(
                    onClick = {
                        Log.d("DocumentViewer", "Save to Server clicked")
                        scope.launch(Dispatchers.IO) {
                            try {
                                val signedFile: File = signPdf(
                                    inputFile = pdfFile!!,
                                    signatureBitmap = signatureBitmap,
                                    selectedField = selectedField,
                                    checkAnnotations = checkAnnotations,
                                    pageNumber = currentPage,
                                    signatureAnnotations = signatureAnnotations,
                                    context = context
                                )
                                Log.d("DocumentViewer", "Saving PDF to server: ${signedFile.absolutePath}, size: ${signedFile.length()} bytes")
                                // Validate PDF header
                                FileInputStream(signedFile).bufferedReader().useLines { lines ->
                                    lines.firstOrNull()?.let { line ->
                                        Log.d("DocumentViewer", "Signed PDF Header: $line")
                                        if (!line.startsWith("%PDF-")) {
                                            Log.e("DocumentViewer", "Invalid signed PDF header: $line")
                                            scope.launch(Dispatchers.Main) {
                                                snackbarHostState.showSnackbar("Signed PDF is invalid")
                                            }
                                            return@launch
                                        }
                                    }
                                }
                                // Save debug copy
                                val debugFile = File(context.cacheDir, "DEBUG_uploaded_${signedFile.name}")
                                signedFile.copyTo(debugFile, overwrite = true)
                                Log.d("DocumentViewer", "Debug PDF saved to: ${debugFile.absolutePath}")
                                // Check file existence and size
                                if (!signedFile.exists() || signedFile.length() == 0L) {
                                    Log.e("DocumentViewer", "Signed file is missing or empty!")
                                    scope.launch(Dispatchers.Main) {
                                        snackbarHostState.showSnackbar("Signed file is invalid or empty")
                                    }
                                    return@launch
                                }
                                val requestFile = signedFile.asRequestBody("application/pdf".toMediaTypeOrNull())
                                val part = MultipartBody.Part.createFormData("file", signedFile.name, requestFile)
                                val response = service.uploadSignedDocument("Bearer $jwtToken", part, "true")
                                Log.d("DocumentViewer", "Upload response: ${response.status}, ${response.message}")
                                scope.launch(Dispatchers.Main) {
                                    snackbarHostState.showSnackbar(
                                        response.message ?: if (response.status == "success") {
                                            "Signed PDF uploaded successfully"
                                        } else {
                                            "Failed to upload signed PDF"
                                        }
                                    )
                                }
                            } catch (e: Exception) {
                                Log.e("DocumentViewer", "Error saving PDF to server", e)
                                scope.launch(Dispatchers.Main) {
                                    snackbarHostState.showSnackbar("Error saving PDF: ${e.message}")
                                }
                            }
                        }
                    },
                    enabled = pdfFile != null && (signatureBitmap != null || selectedField != null || checkAnnotations.isNotEmpty() || signatureAnnotations.isNotEmpty()),
                    modifier = Modifier.background(Color.Transparent)
                ) {
                    Icon(
                        imageVector = Icons.Default.Upload,
                        contentDescription = "Save to Server",
                        tint = if (pdfFile != null && (signatureBitmap != null || selectedField != null || checkAnnotations.isNotEmpty() || signatureAnnotations.isNotEmpty()))
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }
                IconButton(
                    onClick = {
                        Log.d("DocumentViewer", "Download clicked")
                        scope.launch(Dispatchers.IO) {
                            try {
                                val signedFile: File = signPdf(
                                    inputFile = pdfFile!!,
                                    signatureBitmap = signatureBitmap,
                                    selectedField = selectedField,
                                    checkAnnotations = checkAnnotations,
                                    pageNumber = currentPage,
                                    signatureAnnotations = signatureAnnotations,
                                    context = context
                                )
                                Log.d("DocumentViewer", "Downloading PDF: ${signedFile.absolutePath}, size: ${signedFile.length()} bytes")
                                // Validate PDF header
                                FileInputStream(signedFile).bufferedReader().useLines { lines ->
                                    lines.firstOrNull()?.let { line ->
                                        Log.d("DocumentViewer", "Signed PDF Header: $line")
                                        if (!line.startsWith("%PDF-")) {
                                            Log.e("DocumentViewer", "Invalid signed PDF header: $line")
                                            scope.launch(Dispatchers.Main) {
                                                snackbarHostState.showSnackbar("Signed PDF is invalid")
                                            }
                                            return@launch
                                        }
                                    }
                                }
                                // Save debug copy
                                val debugFile = File(context.cacheDir, "DEBUG_downloaded_${signedFile.name}")
                                signedFile.copyTo(debugFile, overwrite = true)
                                Log.d("DocumentViewer", "Debug PDF saved to: ${debugFile.absolutePath}")
                                // Check file existence and size
                                if (!signedFile.exists() || signedFile.length() == 0L) {
                                    Log.e("DocumentViewer", "Signed file is missing or empty!")
                                    scope.launch(Dispatchers.Main) {
                                        snackbarHostState.showSnackbar("Signed file is invalid or empty")
                                    }
                                    return@launch
                                }
                                val outputStream = getPublicDownloadsOutputStream(context, signedFile.name)
                                if (outputStream == null) {
                                    Log.e("DocumentViewer", "Failed to access Downloads directory")
                                    scope.launch(Dispatchers.Main) {
                                        snackbarHostState.showSnackbar("Error: Downloads directory not accessible")
                                    }
                                    return@launch
                                }
                                FileInputStream(signedFile).use { input ->
                                    outputStream.use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                scope.launch(Dispatchers.Main) {
                                    snackbarHostState.showSnackbar("Signed PDF downloaded to Downloads folder")
                                }
                            } catch (e: Exception) {
                                Log.e("DocumentViewer", "Error downloading PDF", e)
                                scope.launch(Dispatchers.Main) {
                                    snackbarHostState.showSnackbar("Error downloading PDF: ${e.message}")
                                }
                            }
                        }
                    },
                    enabled = pdfFile != null && (signatureBitmap != null || selectedField != null || checkAnnotations.isNotEmpty() || signatureAnnotations.isNotEmpty()),
                    modifier = Modifier.background(Color.Transparent)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download",
                        tint = if (pdfFile != null && (signatureBitmap != null || selectedField != null || checkAnnotations.isNotEmpty() || signatureAnnotations.isNotEmpty()))
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
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
        Log.e("DocumentViewer", "Error accessing Downloads directory", e)
        null
    }
}

@Composable
fun CustomSignatureView(
    modifier: Modifier = Modifier,
    onSignatureChanged: (Bitmap?) -> Unit
) {
    AndroidView(
        factory = { context ->
            SignaturePad(context, null).apply {
                setOnSignedListener(object : SignaturePad.OnSignedListener {
                    override fun onStartSigning() {
                        Log.d("CustomSignatureView", "Started signing")
                    }
                    override fun onSigned() {
                        onSignatureChanged(signatureBitmap)
                        Log.d("CustomSignatureView", "Signature captured")
                    }
                    override fun onClear() {
                        onSignatureChanged(null)
                        Log.d("CustomSignatureView", "Signature cleared")
                    }
                })
            }
        },
        modifier = modifier,
        update = { view ->
            // Update logic if needed
        }
    )
}

private fun signPdf(
    inputFile: File,
    signatureBitmap: Bitmap?,
    selectedField: String?,
    checkAnnotations: List<Pair<Int, Pair<Float, Float>>>,
    pageNumber: Int,
    signatureAnnotations: List<Pair<Int, Pair<Float, Float>>>,
    context: Context
): File {
    val outputFile = File(context.cacheDir, "signed_${inputFile.name}")
    try {
        if (!inputFile.exists() || inputFile.length() == 0L) {
            Log.e("DocumentViewer", "Input file is missing or empty: ${inputFile.absolutePath}")
            throw IllegalStateException("Input PDF is invalid or empty")
        }
        val reader = PdfReader(inputFile)
        reader.setUnethicalReading(true)
        val writer = PdfWriter(outputFile)
        val pdfDoc = PdfDocument(reader, writer)
        val form = PdfAcroForm.getAcroForm(pdfDoc, true)
        form.setNeedAppearances(true)

        // Validate page numbers
        val totalPages = pdfDoc.numberOfPages
        checkAnnotations.forEach { (page, _) ->
            if (page < 1 || page > totalPages) {
                Log.e("DocumentViewer", "Invalid check mark page number: $page, total pages: $totalPages")
                throw IllegalArgumentException("Invalid page number for check mark: $page")
            }
        }
        signatureAnnotations.forEach { (page, _) ->
            if (page < 1 || page > totalPages) {
                Log.e("DocumentViewer", "Invalid signature page number: $page, total pages: $totalPages")
                throw IllegalArgumentException("Invalid page number for signature: $page")
            }
        }

        // Apply form field (checkbox) if selected
        selectedField?.let { fieldName ->
            val field = form.getField(fieldName)
            if (field != null && field is PdfFormField) {
                val validState = when {
                    field.getValueAsString() in listOf("Yes", "On") -> "Yes"
                    else -> "On"
                }
                field.setValue(validState)
                Log.d("DocumentViewer", "Checked form field: $fieldName with value $validState")
            }
        }

        // Apply check mark annotations as lines (larger size for visibility)
        checkAnnotations.forEach { (page, position) ->
            try {
                val pdfPage = pdfDoc.getPage(page)
                val canvas = PdfCanvas(pdfPage)
                canvas.setLineCapStyle(PdfCanvasConstants.LineCapStyle.ROUND)
                canvas.setStrokeColor(ColorConstants.RED)
                canvas.setLineWidth(4f)
                val adjustedX = position.first.coerceIn(0f, 595f - 40f)
                val adjustedY = position.second.coerceIn(0f, 842f - 40f)
                canvas.moveTo(adjustedX.toDouble(), adjustedY.toDouble())
                canvas.lineTo((adjustedX + 20f).toDouble(), (adjustedY - 20f).toDouble())
                canvas.lineTo((adjustedX + 40f).toDouble(), (adjustedY + 20f).toDouble())
                canvas.stroke()
                Log.d("DocumentViewer", "Added check mark lines on page $page at ($adjustedX, $adjustedY)")
            } catch (e: Exception) {
                Log.e("DocumentViewer", "Error adding check mark on page $page", e)
            }
        }

        // Apply signature annotations
        signatureBitmap?.let { bitmap ->
            signatureAnnotations.forEach { (pageNum, pos) ->
                try {
                    val page = pdfDoc.getPage(pageNum)
                    val canvas = PdfCanvas(page)
                    val stream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    val imageData = ImageDataFactory.create(stream.toByteArray())
                    val image = PdfImageXObject(imageData)
                    val adjustedX = pos.first.coerceIn(0f, 595f - image.width)
                    val adjustedY = pos.second.coerceIn(0f, 842f - image.height)
                    canvas.addXObjectAt(image, adjustedX, adjustedY)
                    Log.d("DocumentViewer", "Applied signature on page $pageNum at ($adjustedX, $adjustedY)")
                } catch (e: Exception) {
                    Log.e("DocumentViewer", "Error applying signature on page $pageNum", e)
                }
            }
        } ?: Log.w("DocumentViewer", "No signature bitmap provided")

        pdfDoc.close()
        Log.d("DocumentViewer", "PDF modification completed: ${outputFile.absolutePath}, size: ${outputFile.length()} bytes")
    } catch (e: Exception) {
        Log.e("DocumentViewer", "Error modifying PDF", e)
        throw e
    }
    return outputFile
}