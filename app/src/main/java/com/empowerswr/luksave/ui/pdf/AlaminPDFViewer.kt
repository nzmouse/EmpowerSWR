package com.empowerswr.luksave.ui.pdf

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.alamin5g.pdf.PDFView
import java.io.File

@Composable
fun AlaminPDFViewer(
    file: File,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context: Context ->
            PDFView(context).apply {
                fromFile(file)
                    .enableAnnotationRendering(true)
                    .enableSwipe(true)
                    .swipeHorizontal(false)
                    .load()
            }
        },
        modifier = modifier
    )
}

