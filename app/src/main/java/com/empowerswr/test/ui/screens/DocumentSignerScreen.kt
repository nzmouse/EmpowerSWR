package com.empowerswr.test.ui.screens

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import com.itextpdf.kernel.pdf.canvas.PdfCanvasConstants
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.pdf.xobject.PdfImageXObject
import com.empowerswr.test.network.NetworkModule
import com.empowerswr.test.PrefsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream

enum class AnnotationMode { NONE, CHECK, SIGNATURE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentSignerScreen(
    navController: NavController,
    filename: String,
    context: Context
) {
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
    val jwtToken = PrefsHelper.getJwtToken(context)
    val service = NetworkModule.provideDownloadService(context)

    LaunchedEffect(filename) {
        if (jwtToken.isEmpty()) {
            Log.e("DocumentSigner", "JWT token is empty")
            Toast.makeText(context, "Invalid session, please log in", Toast.LENGTH_SHORT).show()
            (context as? Activity)?.finish()
            return@LaunchedEffect
        }
        scope.launch(Dispatchers.IO) {
            try {
                val encodedFilename = filename.replace(" ", "%20")
                Log.d("DocumentSigner", "Downloading PDF: $encodedFilename")
                val response = service.downloadDocument("Bearer $jwtToken", encodedFilename)
                if (!response.isSuccessful) {
                    Log.e("DocumentSigner", "Download failed: HTTP ${response.code()} ${response.message()}")
                    scope.launch(Dispatchers.Main) {
                        snackbarHostState.showSnackbar("Download failed: HTTP ${response.code()}")
                    }
                    return@launch
                }
                if (!response.headers()["Content-Type"]?.contains("application/pdf")!!) {
                    Log.e("DocumentSigner", "Invalid content type: ${response.headers()["Content-Type"]}")
                    scope.launch(Dispatchers.Main) {
                        snackbarHostState.showSnackbar("Invalid file format received")
                    }
                    return@launch
                }
                val file = File(context.cacheDir, filename)
                FileOutputStream(file).use { output ->
                    response.body()?.bytes()?.let { output.write(it) } ?: throw Exception("Empty response body")
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
                Log.d("DocumentSigner", "PDF loaded successfully: ${file.absolutePath}")
            } catch (e: Exception) {
                Log.e("DocumentSigner", "Error loading PDF: ${e.message}", e)
                scope.launch(Dispatchers.Main) {
                    snackbarHostState.showSnackbar("Error loading PDF: ${e.message}")
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sign Document") },
                navigationIcon = {
                    IconButton(onClick = {
                        Log.d("DocumentSigner", "Back button clicked")
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = {
                                annotationMode = AnnotationMode.CHECK
                                Log.d("DocumentSigner", "Check mode activated")
                            }
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
                                Log.d("DocumentSigner", "Sign mode activated")
                            }
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
                                Log.d("DocumentSigner", "Clear mode activated")
                            }
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
                color = MaterialTheme.colorScheme.onBackground
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(Color.White)
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            Log.d("DocumentSigner", "Tap detected at offset: $offset")
                            val pdfWidth = 595f // Standard PDF width (A4)
                            val pdfHeight = 842f // Standard PDF height (A4)
                            val viewWidth = size.width.toFloat()
                            val viewHeight = size.height.toFloat()
                            val x = (offset.x / viewWidth) * pdfWidth
                            val y = pdfHeight - (offset.y / viewHeight) * pdfHeight
                            when (annotationMode) {
                                AnnotationMode.CHECK -> {
                                    checkAnnotations = checkAnnotations + Pair(currentPage, Pair(x, y))
                                    Log.d("DocumentSigner", "Added check annotation on page $currentPage at ($x, $y)")
                                }
                                AnnotationMode.SIGNATURE -> {
                                    if (signatureBitmap != null) {
                                        signatureAnnotations = signatureAnnotations + Pair(currentPage, Pair(x, y))
                                        Log.d("DocumentSigner", "Added signature annotation on page $currentPage at ($x, $y)")
                                    } else {
                                        scope.launch(Dispatchers.Main) {
                                            snackbarHostState.showSnackbar("Please draw a signature first")
                                        }
                                    }
                                }
                                else -> {}
                            }
                        }
                    }
            ) {
                pdfFile?.let { file ->
                    AndroidView(
                        factory = { ctx ->
                            PDFView(ctx, null).apply {
                                setOnTouchListener { _, event ->
                                    Log.d("DocumentSigner", "PDFView touch event: ${event.action}")
                                    false // Let Compose handle touches
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { pdfView ->
                            Log.d("DocumentSigner", "Updating PDFView with file: ${file.absolutePath}")
                            pdfView.fromFile(file)
                                .defaultPage(currentPage - 1)
                                .onPageChange { page, pageCount ->
                                    currentPage = page + 1
                                    totalPages = pageCount
                                    Log.d("DocumentSigner", "Page changed to $currentPage of $totalPages")
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
                    Log.d("DocumentSigner", "Signature changed: ${bitmap != null}")
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
                                Log.d("DocumentSigner", "Dropdown opened")
                            }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown")
                            }
                        }
                    )
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = {
                            expanded = false
                            Log.d("DocumentSigner", "Dropdown dismissed")
                        }
                    ) {
                        formFields.forEach { field ->
                            DropdownMenuItem(
                                text = { Text(field) },
                                onClick = {
                                    selectedField = field
                                    expanded = false
                                    Log.d("DocumentSigner", "Form field selected: $field")
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
                        checkAnnotations = emptyList()
                        signatureAnnotations = emptyList()
                        selectedField = null
                        Log.d("DocumentSigner", "Clear annotations and signature")
                        scope.launch(Dispatchers.Main) {
                            snackbarHostState.showSnackbar("Annotations and signature cleared")
                        }
                    },
                    enabled = signatureBitmap != null || checkAnnotations.isNotEmpty() || signatureAnnotations.isNotEmpty() || selectedField != null
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear Annotations",
                        tint = if (signatureBitmap != null || checkAnnotations.isNotEmpty() || signatureAnnotations.isNotEmpty() || selectedField != null)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }
                IconButton(
                    onClick = {
                        Log.d("DocumentSigner", "Save to Server clicked")
                        scope.launch(Dispatchers.IO) {
                            try {
                                val signedFile = signPdf(
                                    inputFile = pdfFile!!,
                                    signatureBitmap = signatureBitmap,
                                    selectedField = selectedField,
                                    checkAnnotations = checkAnnotations,
                                    pageNumber = currentPage,
                                    signatureAnnotations = signatureAnnotations,
                                    context = context
                                )
                                if (!signedFile.exists() || signedFile.length() == 0L) {
                                    throw IllegalStateException("Signed file is invalid or empty")
                                }
                                val requestFile = signedFile.asRequestBody("application/pdf".toMediaTypeOrNull())
                                val part = MultipartBody.Part.createFormData("file", signedFile.name, requestFile)
                                val response = service.uploadSignedDocument("Bearer $jwtToken", part, "true")
                                Log.d("DocumentSigner", "Upload response: ${response.status}, ${response.message}")
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
                                Log.e("DocumentSigner", "Error saving PDF: ${e.message}", e)
                                scope.launch(Dispatchers.Main) {
                                    snackbarHostState.showSnackbar("Error saving PDF: ${e.message}")
                                }
                            }
                        }
                    },
                    enabled = pdfFile != null && (signatureBitmap != null || selectedField != null || checkAnnotations.isNotEmpty() || signatureAnnotations.isNotEmpty())
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
                        Log.d("DocumentSigner", "Download clicked")
                        scope.launch(Dispatchers.IO) {
                            try {
                                val signedFile = signPdf(
                                    inputFile = pdfFile!!,
                                    signatureBitmap = signatureBitmap,
                                    selectedField = selectedField,
                                    checkAnnotations = checkAnnotations,
                                    pageNumber = currentPage,
                                    signatureAnnotations = signatureAnnotations,
                                    context = context
                                )
                                if (!signedFile.exists() || signedFile.length() == 0L) {
                                    throw IllegalStateException("Signed file is invalid or empty")
                                }
                                val outputStream = getPublicDownloadsOutputStream(context, signedFile.name)
                                if (outputStream == null) {
                                    Log.e("DocumentSigner", "Failed to access Downloads directory")
                                    scope.launch(Dispatchers.Main) {
                                        snackbarHostState.showSnackbar("Error: Downloads directory not accessible")
                                    }
                                    return@launch
                                }
                                FileInputStream(signedFile).use { input ->
                                    outputStream.use { output ->
                                        val buffer = ByteArray(1024)
                                        var bytesRead: Int
                                        while (input.read(buffer).also { bytesRead = it } != -1) {
                                            output.write(buffer, 0, bytesRead)
                                        }
                                    }
                                }
                                scope.launch(Dispatchers.Main) {
                                    snackbarHostState.showSnackbar("Signed PDF downloaded to Downloads folder")
                                }
                            } catch (e: Exception) {
                                Log.e("DocumentSigner", "Error downloading PDF: ${e.message}", e)
                                scope.launch(Dispatchers.Main) {
                                    snackbarHostState.showSnackbar("Error downloading PDF: ${e.message}")
                                }
                            }
                        }
                    },
                    enabled = pdfFile != null && (signatureBitmap != null || selectedField != null || checkAnnotations.isNotEmpty() || signatureAnnotations.isNotEmpty())
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
        Log.e("DocumentSigner", "Error accessing Downloads directory: ${e.message}", e)
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
                        Log.d("DocumentSigner", "Started signing")
                    }
                    override fun onSigned() {
                        onSignatureChanged(signatureBitmap)
                        Log.d("DocumentSigner", "Signature captured")
                    }
                    override fun onClear() {
                        onSignatureChanged(null)
                        Log.d("DocumentSigner", "Signature cleared")
                    }
                })
            }
        },
        modifier = modifier
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
            Log.e("DocumentSigner", "Input file is missing or empty: ${inputFile.absolutePath}")
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
                throw IllegalArgumentException("Invalid page number for check mark: $page")
            }
        }
        signatureAnnotations.forEach { (page, _) ->
            if (page < 1 || page > totalPages) {
                throw IllegalArgumentException("Invalid page number for signature: $page")
            }
        }

        // Apply form field (checkbox) if selected
        selectedField?.let { fieldName ->
            val field = form.getField(fieldName)
            if (field != null) {
                try {
                    val validState = when {
                        field.getValueAsString() in listOf("Yes", "On") -> "Yes"
                        else -> "On"
                    }
                    field.setValue(validState)
                    Log.d("DocumentSigner", "Checked form field: $fieldName with value $validState")
                } catch (e: Exception) {
                    Log.e("DocumentSigner", "Error setting form field $fieldName: ${e.message}", e)
                }
            }
        }

        // Apply check mark annotations (lines) if no AcroForm field is selected
        if (selectedField == null) {
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
                    Log.d("DocumentSigner", "Added check mark on page $page at ($adjustedX, $adjustedY)")
                } catch (e: Exception) {
                    Log.e("DocumentSigner", "Error adding check mark on page $page: ${e.message}", e)
                }
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
                    Log.d("DocumentSigner", "Applied signature on page $pageNum at ($adjustedX, $adjustedY)")
                } catch (e: Exception) {
                    Log.e("DocumentSigner", "Error applying signature on page $pageNum: ${e.message}", e)
                }
            }
        } ?: Log.w("DocumentSigner", "No signature bitmap provided")

        pdfDoc.close()
        if (!outputFile.exists() || outputFile.length() == 0L) {
            throw IllegalStateException("Output PDF is invalid or empty")
        }
        Log.d("DocumentSigner", "PDF modification completed: ${outputFile.absolutePath}, size: ${outputFile.length()} bytes")
        return outputFile
    } catch (e: Exception) {
        Log.e("DocumentSigner", "Error modifying PDF: ${e.message}", e)
        throw e
    }
}