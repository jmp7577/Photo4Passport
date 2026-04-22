package com.example.photo4passport.ui.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class CropGuideView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2563EB")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2563EB")
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
    }

    private val dimPaint = Paint().apply {
        color = Color.parseColor("#66000000")
        style = Paint.Style.FILL
    }

    private val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#662563EB")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(8f, 6f), 0f)
    }

    // Aspect ratio of the selected country (widthMm / heightMm)
    var aspectRatio: Float = 1f
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Calculate crop rect that matches export output (fit-center with country aspect ratio)
        val viewW = width.toFloat()
        val viewH = height.toFloat()

        val cropW: Float
        val cropH: Float
        if (viewW / viewH <= aspectRatio) {
            cropW = viewW * 0.88f
            cropH = cropW / aspectRatio
        } else {
            cropH = viewH * 0.88f
            cropW = cropH * aspectRatio
        }

        val left = (viewW - cropW) / 2f
        val top = (viewH - cropH) / 2f
        val right = left + cropW
        val bottom = top + cropH

        val cropRect = RectF(left, top, right, bottom)

        // Dim areas outside crop boundary
        canvas.drawRect(0f, 0f, viewW, top, dimPaint)
        canvas.drawRect(0f, bottom, viewW, viewH, dimPaint)
        canvas.drawRect(0f, top, left, bottom, dimPaint)
        canvas.drawRect(right, top, viewW, bottom, dimPaint)

        // Draw crop border
        canvas.drawRect(cropRect, borderPaint)

        // Draw corner accents
        val cornerLen = cropW * 0.08f
        // Top-left
        canvas.drawLine(left, top, left + cornerLen, top, cornerPaint)
        canvas.drawLine(left, top, left, top + cornerLen, cornerPaint)
        // Top-right
        canvas.drawLine(right - cornerLen, top, right, top, cornerPaint)
        canvas.drawLine(right, top, right, top + cornerLen, cornerPaint)
        // Bottom-left
        canvas.drawLine(left, bottom - cornerLen, left, bottom, cornerPaint)
        canvas.drawLine(left, bottom, left + cornerLen, bottom, cornerPaint)
        // Bottom-right
        canvas.drawLine(right, bottom - cornerLen, right, bottom, cornerPaint)
        canvas.drawLine(right - cornerLen, bottom, right, bottom, cornerPaint)

        // Draw center crosshair
        val cx = (left + right) / 2f
        val cy = (top + bottom) / 2f
        canvas.drawLine(left + 8f, cy, right - 8f, cy, crosshairPaint)
        canvas.drawLine(cx, top + 8f, cx, bottom - 8f, crosshairPaint)
    }
}