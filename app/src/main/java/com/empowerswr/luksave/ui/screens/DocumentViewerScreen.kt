package com.empowerswr.luksave.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.alamin5g.pdf.PDFView
import com.empowerswr.luksave.network.ListFilesService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import timber.log.Timber
import java.io.File
import java.io.FileInputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentViewerScreen(
    navController: NavController,
    filename: String,
    url: String,
    listFilesService: ListFilesService,
    downloadCompleteFlow: SharedFlow<Pair<Long, String>>
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var pdfFile by remember { mutableStateOf<File?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    val decodedFilename = filename.replace("+", " ").replace("%20", " ").trim()

    // Detailed download with logging
    LaunchedEffect(url) {
        isLoading = true
        scope.launch(Dispatchers.IO) {
            try {
                Timber.d("DocumentViewerScreen: Starting download for $decodedFilename")
                Timber.d("DocumentViewerScreen: URL length: ${url.length}")

                val file = File(context.cacheDir, decodedFilename)
                Timber.d("DocumentViewerScreen: Target cache: ${file.absolutePath}")

                if (file.exists() && file.length() > 1000) {
                    if (isValidPdf(file)) {
                        pdfFile = file
                        Timber.i("DocumentViewerScreen: ✅ Using cached file")
                        return@launch
                    } else {
                        file.delete()
                    }
                }

                // Fresh download
                val client = OkHttpClient.Builder().followRedirects(true).build()
                val request = okhttp3.Request.Builder().url(url).build()

                Timber.d("DocumentViewerScreen: Executing request...")
                client.newCall(request).execute().use { response ->
                    Timber.d("DocumentViewerScreen: Response code = ${response.code}")

                    if (response.isSuccessful) {
                        response.body?.byteStream()?.use { input ->
                            file.outputStream().use { output ->
                                val bytes = input.copyTo(output)
                                Timber.i("DocumentViewerScreen: Downloaded $bytes bytes")
                            }
                        }

                        if (file.exists() && isValidPdf(file)) {
                            pdfFile = file
                            Timber.i("DocumentViewerScreen: ✅ Valid PDF ready")
                        } else {
                            Timber.e("DocumentViewerScreen: Downloaded file invalid")
                        }
                    } else {
                        Timber.e("DocumentViewerScreen: HTTP ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "DocumentViewerScreen: Download failed")
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(decodedFilename) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
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
                                .load()
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )
            } ?: Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Failed to load document")
            }
        }
    }
}

private fun isValidPdf(file: File): Boolean {
    return try {
        FileInputStream(file).use { input ->
            val header = ByteArray(4)
            input.read(header) == 4 &&
                    header[0] == 0x25.toByte() && header[1] == 0x50.toByte() &&
                    header[2] == 0x44.toByte() && header[3] == 0x46.toByte()
        }
    } catch (e: Exception) {
        false
    }
}