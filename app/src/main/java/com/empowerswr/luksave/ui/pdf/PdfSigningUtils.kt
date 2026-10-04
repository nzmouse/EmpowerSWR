package com.empowerswr.luksave.ui.pdf

import android.graphics.Bitmap
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import com.itextpdf.forms.PdfAcroForm
import com.itextpdf.io.image.ImageData
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.io.font.constants.StandardFonts
import com.itextpdf.kernel.pdf.PdfName.BaseFont

data class PlacedAnnotation(
    val pageIndex: Int,          // 0-based
    val type: AnnotationType,
    val pdfX: Float,             // absolute PDF points (bottom-left origin)
    val pdfY: Float,
    val width: Float,
    val height: Float,
    val bitmap: Bitmap? = null   // only for SIGNATURE
)

enum class AnnotationType { SIGNATURE, CHECKMARK }

object PdfSigningUtils {

    fun fillAndSignContract(

        inputFile: File,
        outputFile: File,
        formValues: Map<String, String> = emptyMap(),
        annotations: List<PlacedAnnotation> = emptyList()
    ): Boolean {
        return try {
            Timber.i("fillAndSignContract called with ${annotations.size} annotations")
            PdfReader(inputFile).use { reader ->
                PdfWriter(outputFile).use { writer ->
                    val pdfDoc = PdfDocument(reader, writer)
                    val form = PdfAcroForm.getAcroForm(pdfDoc, true)

                    // Fill text fields
                    formValues.forEach { (fieldName, value) ->
                        form.getField(fieldName)?.setValue(value)
                    }

                    // Stamp annotations
                    annotations.forEach { ann ->
                        try {
                            Timber.d("Stamping annotation type=${ann.type} on page ${ann.pageIndex}")

                            val page = pdfDoc.getPage(ann.pageIndex + 1)
                            val canvas = PdfCanvas(page)

                            when (ann.type) {
                                AnnotationType.SIGNATURE -> {
                                    ann.bitmap?.let { bmp ->
                                        val stream = ByteArrayOutputStream()
                                        bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, stream)
                                        val imageData = ImageDataFactory.create(stream.toByteArray())

                                        canvas.addImageAt(imageData, ann.pdfX, ann.pdfY, false)
                                        Timber.i("✅ Stamped SIGNATURE on page ${ann.pageIndex}")
                                    }
                                }
                                AnnotationType.CHECKMARK -> {
                                    try {
                                        val page = pdfDoc.getPage(ann.pageIndex + 1)
                                        val pageHeight = page.getPageSize().getHeight()

                                        val canvas = PdfCanvas(page)

                                        val font = com.itextpdf.kernel.font.PdfFontFactory.createFont(com.itextpdf.io.font.constants.StandardFonts.HELVETICA_BOLD)
                                        canvas.setFontAndSize(font, 20f)
                                        canvas.setFillColor(com.itextpdf.kernel.colors.DeviceRgb(0, 120, 0))

                                        val x: Float = ann.pdfX
                                        val y: Float = pageHeight - ann.pdfY - (ann.height * 0.6f)

                                        canvas.setFillColor(com.itextpdf.kernel.colors.DeviceRgb(0, 180, 0))
                                        canvas.setStrokeColor(com.itextpdf.kernel.colors.DeviceRgb(0, 100, 0))
                                        canvas.setLineWidth(4f)

                                        canvas.rectangle(x.toDouble(), y.toDouble(), 55.0, 55.0)
                                        canvas.fillStroke()

                                        Timber.i("✅ Nice tick at ($x, $y)")
                                    } catch (e: Exception) {
                                        Timber.e(e, "Failed to stamp checkmark")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to stamp annotation on page ${ann.pageIndex}")

                        }
                    }
                    Timber.i("Finished stamping ${annotations.size} annotations")
                    pdfDoc.close()
                }
            }
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to fill and sign PDF")
            false
        }
    }
}

private fun createImageDataFromBitmap(bitmap: Bitmap): com.itextpdf.io.image.ImageData {
    val stream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream)
    return ImageDataFactory.create(stream.toByteArray())
}
