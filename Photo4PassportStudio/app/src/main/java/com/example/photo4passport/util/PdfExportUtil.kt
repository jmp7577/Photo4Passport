package com.example.photo4passport.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object PdfExportUtil {

    /**
     * Export the passport sheet bitmap as a PDF sized to 4×6 inches at 72 pts/inch.
     *
     * The bitmap is scaled to fill the page so that when printed on a 4×6 inch photo
     * sheet, each passport photo is at the correct physical size (e.g. 2×2 in for US).
     *
     * Page size: 288 × 432 pts  (4 in × 6 in × 72 pts/in)
     */
    fun exportBitmapAsPdf(
        context: Context,
        bitmap: Bitmap,
        fileName: String = "passport_sheet.pdf"
    ): Uri {
        // 4×6 inch in PDF user-space units (1 pt = 1/72 inch)
        val pageWidthPts = (4 * 72)   // 288 pts
        val pageHeightPts = (6 * 72)  // 432 pts

        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidthPts, pageHeightPts, 1).create()
        val page = document.startPage(pageInfo)

        // Scale bitmap to fit the page maintaining aspect ratio
        val bitmapAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
        val pageAspect = pageWidthPts.toFloat() / pageHeightPts.toFloat()

        val destRect: RectF
        if (bitmapAspect > pageAspect) {
            // Bitmap is wider → fit width
            val scaledH = pageWidthPts / bitmapAspect
            val top = (pageHeightPts - scaledH) / 2f
            destRect = RectF(0f, top, pageWidthPts.toFloat(), top + scaledH)
        } else {
            // Bitmap is taller → fit height
            val scaledW = pageHeightPts * bitmapAspect
            val left = (pageWidthPts - scaledW) / 2f
            destRect = RectF(left, 0f, left + scaledW, pageHeightPts.toFloat())
        }

        page.canvas.drawBitmap(bitmap, null, destRect, null)
        document.finishPage(page)

        val file = File(context.cacheDir, fileName)
        FileOutputStream(file).use { output -> document.writeTo(output) }
        document.close()

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
