package com.empowerswr.test.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class CustomSignatureView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val paint = Paint().apply {
        color = Color.BLACK
        strokeWidth = 5f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    private var bitmap: Bitmap? = null
    private var canvas: Canvas? = null
    private var lastX = 0f
    private var lastY = 0f
    private var onSignatureChanged: ((Bitmap?) -> Unit)? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        canvas = Canvas(bitmap!!)
        canvas?.drawColor(Color.TRANSPARENT)
        onSignatureChanged?.invoke(bitmap)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        bitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                canvas?.drawLine(lastX, lastY, event.x, event.y, paint)
                lastX = event.x
                lastY = event.y
                invalidate()
                onSignatureChanged?.invoke(bitmap)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    fun clear() {
        bitmap?.eraseColor(Color.TRANSPARENT)
        invalidate()
        onSignatureChanged?.invoke(bitmap)
    }

    fun getSignatureBitmap(): Bitmap? = bitmap

    fun setOnSignatureChanged(callback: (Bitmap?) -> Unit) {
        onSignatureChanged = callback
    }
}