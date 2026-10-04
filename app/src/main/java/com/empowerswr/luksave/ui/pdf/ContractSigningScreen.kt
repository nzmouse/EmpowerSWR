package com.empowerswr.luksave.ui.pdf

import android.content.Context
import android.graphics.Bitmap
import android.util.SizeF
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.alamin5g.pdf.PDFView
import com.empowerswr.luksave.PrefsHelper
import com.empowerswr.luksave.network.UploadService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import timber.log.Timber
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContractSigningScreen(
    filename: String,
    contractUrl: String,
    uploadService: UploadService,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pdfFile by remember { mutableStateOf<File?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var currentPage by remember { mutableStateOf(0) }
    var totalPages by remember { mutableStateOf(0) }

    var currentTool by remember { mutableStateOf(Tool.NONE) }
    var showSignaturePad by remember { mutableStateOf(false) }
    var pendingSignatureBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val annotations = remember { mutableStateListOf<PlacedAnnotation>() }

    LaunchedEffect(contractUrl) {
        downloadContract(contractUrl, context) { file ->
            pdfFile = file
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Sign Contract - Page ${currentPage + 1}/$totalPages",
                        style = MaterialTheme.typography.titleSmall   // smaller text
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                windowInsets = WindowInsets(0, 0, 0, 0),  // reduce height
                modifier = Modifier.height(48.dp)  // force small height
            )
        }

    ) { padding ->
        Box(Modifier.fillMaxSize().padding(0.dp)) {
            if (isLoading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                pdfFile?.let { file ->
                    AndroidView(
                        factory = { ctx ->
                            PDFView(ctx).apply {
                                fromFile(file)
                                    .enableSwipe(true)
                                    .swipeHorizontal(false)
                                    .enableDoubletap(true)
                                    .enableAnnotationRendering(true)
                                    .fitPolicy(PDFView.FitPolicy.WIDTH)
                                    .defaultPage(0)
                                    .onLoad { pages -> totalPages = pages }
                                    .onPageChange { page, _ -> currentPage = page }
                                    .onTap { motionEvent ->
                                        if (currentTool == Tool.CHECKMARK) {
                                            val zoom = getZoom()
                                            val pageSize = getPageSize(currentPage)
                                            val pdfX = motionEvent.x / zoom * (pageSize.width / width.toFloat())
                                            val pdfY = motionEvent.y / zoom * (pageSize.height / height.toFloat())

                                            annotations.add(
                                                PlacedAnnotation(
                                                    pageIndex = currentPage,
                                                    type = AnnotationType.CHECKMARK,
                                                    pdfX = pdfX,
                                                    pdfY = pdfY,
                                                    width = 35f,
                                                    height = 35f
                                                )
                                            )
                                            Timber.i("✅ Checkmark tap at screen (${motionEvent.x}, ${motionEvent.y}) → PDF ($pdfX, $pdfY) on page $currentPage")
                                            true
                                        } else false
                                    }
                                    .load()
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
// Floating buttons
                Box(Modifier.fillMaxSize()) {
                    // Signature button
                    FloatingActionButton(
                        onClick = { currentTool = Tool.SIGNATURE; showSignaturePad = true },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                    ) {
                        Icon(Icons.Default.Edit, "Signature")
                    }

                    // Checkmark button
                    FloatingActionButton(
                        onClick = { currentTool = if (currentTool == Tool.CHECKMARK) Tool.NONE else Tool.CHECKMARK },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
                    ) {
                        Icon(Icons.Default.Check, "Checkmark")
                    }

                    // Save button
                    FloatingActionButton(
                        onClick = {
                            scope.launch {
                                val success = saveAndUpload(pdfFile!!, annotations.toList(), filename, uploadService, context)
                                if (success) onClose()
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp),
                        containerColor = MaterialTheme.colorScheme.tertiary
                    ) {
                        Icon(Icons.Default.Save, "Save")
                    }
                }
                // Overlay ONLY when placing signature
                if (pendingSignatureBitmap != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(0.4f))
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    annotations.add(
                                        PlacedAnnotation(
                                            pageIndex = currentPage,
                                            type = AnnotationType.SIGNATURE,
                                            pdfX = offset.x * 0.75f,
                                            pdfY = offset.y * 0.75f,
                                            width = 180f,
                                            height = 70f,
                                            bitmap = pendingSignatureBitmap
                                        )
                                    )
                                    pendingSignatureBitmap = null
                                    currentTool = Tool.NONE
                                    Timber.i("✅ Signature placed at page $currentPage")
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Tap where to place signature", color = Color.White)
                    }
                }
            }
        }
    }

    if (showSignaturePad) {
        SignaturePadDialog(
            onSignatureCaptured = { bmp ->
                pendingSignatureBitmap = bmp
                showSignaturePad = false
                Timber.i("Signature captured and ready for placement")
            },
            onDismiss = { showSignaturePad = false }
        )
    }
}

// ---------- Download Contract ----------
private fun downloadContract(url: String, context: Context, onDone: (File?) -> Unit) {
    val file = File(context.cacheDir, "contract_${System.currentTimeMillis()}.pdf")

    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
        try {
            val client = okhttp3.OkHttpClient()
            val request = okhttp3.Request.Builder().url(url).build()
            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                response.body?.byteStream()?.use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onDone(file)
                }
            } else {
                withContext(kotlinx.coroutines.Dispatchers.Main) { onDone(null) }
            }
        } catch (e: Exception) {
            Timber.e(e, "Contract download failed")
            withContext(kotlinx.coroutines.Dispatchers.Main) { onDone(null) }
        }
    }
}
enum class Tool { NONE, SIGNATURE, CHECKMARK }
// ---------- Save & Upload ----------
private suspend fun saveAndUpload(
    inputFile: File,
    annotations: List<PlacedAnnotation>,
    originalFilename: String,
    uploadService: UploadService,
    context: Context
): Boolean {
    val signedFile = File(context.cacheDir, "SIGNED_$originalFilename")

    val success = PdfSigningUtils.fillAndSignContract(
        inputFile = inputFile,
        outputFile = signedFile,
        formValues = emptyMap(), // add form fields later if needed
        annotations = annotations
    )

    if (!success || !signedFile.exists()) {
        Timber.e("Failed to create signed PDF")
        return false
    }
    Timber.i("PdfSigningUtils returned success=$success, annotations count=${annotations.size}")
    return try {
        val token = PrefsHelper.getToken(context) ?: return false
        val (givenName, surname) = PrefsHelper.getWorkerDetails(context)
        val fileName = "$givenName $surname - CON.pdf"

        val requestFile = signedFile.asRequestBody("application/pdf".toMediaType())
        val body = MultipartBody.Part.createFormData("file", fileName, requestFile)

        uploadService.uploadFile(
            token = "Bearer $token",
            file = body,
            isSigned = "true"
        )
        Timber.i("✅ Signed contract uploaded")
        true
    } catch (e: Exception) {
        Timber.e(e, "Upload failed")
        false
    }
}


