package dev.lumbina.tv.platform.accessibility

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/**
 * [text] as a QR code [side] pixels across: black modules on a white card with a quiet zone, as
 * the launcher draws it, for an overlay that has no Compose.
 */
internal fun qrBitmap(text: String, side: Int): Bitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0))
    val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    val corner = side * CORNER
    canvas.drawRoundRect(RectF(0f, 0f, side.toFloat(), side.toFloat()), corner, corner, paint)
    val quiet = side * QUIET
    val cell = (side - 2 * quiet) / matrix.width
    paint.color = Color.BLACK
    paint.isAntiAlias = false
    for (y in 0 until matrix.height) {
        for (x in 0 until matrix.width) {
            if (!matrix[x, y]) continue
            val left = quiet + x * cell
            val top = quiet + y * cell
            // Cells overlap a little so no hairline gaps show between neighbours.
            canvas.drawRect(left, top, left + cell + SEAM, top + cell + SEAM, paint)
        }
    }
    return bitmap
}

private const val CORNER = 0.05f
private const val QUIET = 0.07f
private const val SEAM = 0.5f
