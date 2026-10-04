package com.empowerswr.luksave.ui.pdf

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun SignaturePadDialog(
    onSignatureCaptured: (Bitmap) -> Unit,
    onDismiss: () -> Unit
) {
    var path by remember { mutableStateOf(AndroidPath()) }
    var currentPoints by remember { mutableStateOf(listOf<Offset>()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth(0.95f)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Draw your signature below", style = MaterialTheme.typography.titleMedium)

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .background(Color.Transparent)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    path.moveTo(offset.x, offset.y)
                                    currentPoints = listOf(offset)
                                },
                                onDrag = { change, dragAmount ->
                                    val last = currentPoints.lastOrNull() ?: Offset.Zero
                                    val newPoint = Offset(last.x + dragAmount.x, last.y + dragAmount.y)
                                    currentPoints = currentPoints + newPoint
                                    path.lineTo(newPoint.x, newPoint.y)
                                    change.consume()
                                }
                            )
                        }
                ) {
                    drawContext.canvas.nativeCanvas.drawPath(
                        path,
                        Paint().apply {
                            color = android.graphics.Color.BLACK
                            style = Paint.Style.STROKE
                            strokeWidth = 7f
                            isAntiAlias = true
                            strokeJoin = Paint.Join.ROUND
                            strokeCap = Paint.Cap.ROUND
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        path.reset()
                        currentPoints = emptyList()
                    }, modifier = Modifier.weight(1f)) {
                        Text("Clear")
                    }

                    Button(onClick = {
                        val bitmap = Bitmap.createBitmap(900, 400, Bitmap.Config.ARGB_8888)
                        val canvas = AndroidCanvas(bitmap)
                        canvas.drawColor(android.graphics.Color.TRANSPARENT)

                        canvas.drawPath(path, Paint().apply {
                            color = android.graphics.Color.BLACK
                            style = Paint.Style.STROKE
                            strokeWidth = 8f
                            isAntiAlias = true
                            strokeJoin = Paint.Join.ROUND
                            strokeCap = Paint.Cap.ROUND
                        })

                        onSignatureCaptured(bitmap)
                    }, modifier = Modifier.weight(1f)) {
                        Text("Save Signature")
                    }
                }
            }
        }
    }
}

