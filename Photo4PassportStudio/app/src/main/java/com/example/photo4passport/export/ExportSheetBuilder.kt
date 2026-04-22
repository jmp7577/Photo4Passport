package com.example.photo4passport.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/**
 * Builds a printable sheet of passport photos.
 *
 * Target output size: 4×6 inch at 300 dpi = 1200×1800 px
 * Each photo cell is sized to match the country's aspect ratio exactly.
 */
class ExportSheetBuilder {

    companion object {
        // 4×6 inch at 300 dpi
        private const val SHEET_WIDTH_PX = 1200
        private const val SHEET_HEIGHT_PX = 1800
        private const val MARGIN_PX = 36
        private const val SPACING_PX = 18
    }

    /**
     * @param passportBitmap  The processed passport photo (already cropped & background-applied).
     * @param count           Number of copies: 4, 6, or 8.
     * @param aspectRatio     width/height of the country spec (e.g. 51/51 = 1.0 for US, 35/45 for UK).
     */
    fun buildSheet(passportBitmap: Bitmap, count: Int, aspectRatio: Float = 1f): Bitmap {
        // Determine grid layout
        val (columns, rows) = when (count) {
            4 -> Pair(2, 2)
            6 -> Pair(2, 3)
            8 -> Pair(2, 4)
            else -> Pair(2, 2)
        }

        // Calculate cell dimensions that maintain aspect ratio within available space
        val availableWidth = SHEET_WIDTH_PX - 2 * MARGIN_PX - (columns - 1) * SPACING_PX
        val availableHeight = SHEET_HEIGHT_PX - 2 * MARGIN_PX - (rows - 1) * SPACING_PX

        val cellWidthByCol = availableWidth / columns
        val cellHeightByRow = availableHeight / rows

        // Fit within cell preserving aspect ratio
        val cellWidth: Int
        val cellHeight: Int
        if (cellWidthByCol.toFloat() / cellHeightByRow <= aspectRatio) {
            cellWidth = cellWidthByCol
            cellHeight = (cellWidth / aspectRatio).toInt().coerceAtLeast(1)
        } else {
            cellHeight = cellHeightByRow
            cellWidth = (cellHeight * aspectRatio).toInt().coerceAtLeast(1)
        }

        val sheet = Bitmap.createBitmap(SHEET_WIDTH_PX, SHEET_HEIGHT_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(Color.WHITE)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.LTGRAY
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        // Scale source bitmap to cell size
        val scaled = Bitmap.createScaledBitmap(passportBitmap, cellWidth, cellHeight, true)

        // Center the grid on the sheet
        val gridWidth = columns * cellWidth + (columns - 1) * SPACING_PX
        val gridHeight = rows * cellHeight + (rows - 1) * SPACING_PX
        val startX = (SHEET_WIDTH_PX - gridWidth) / 2
        val startY = (SHEET_HEIGHT_PX - gridHeight) / 2

        for (row in 0 until rows) {
            for (col in 0 until columns) {
                val left = startX + col * (cellWidth + SPACING_PX)
                val top = startY + row * (cellHeight + SPACING_PX)
                canvas.drawBitmap(scaled, left.toFloat(), top.toFloat(), null)
                canvas.drawRect(
                    left.toFloat(), top.toFloat(),
                    (left + cellWidth).toFloat(), (top + cellHeight).toFloat(),
                    borderPaint
                )
            }
        }

        scaled.recycle()
        return sheet
    }
}
