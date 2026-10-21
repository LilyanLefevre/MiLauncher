package com.lilyan_lefevre.milauncher.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/** Black-on-white QR code of [content], drawn one pixel per module and scaled up without blur. */
@Composable
fun QrCode(content: String, contentDescription: String?, modifier: Modifier = Modifier) {
    val bitmap = remember(content) { qrBitmap(content) }
    Image(bitmap, contentDescription, modifier, filterQuality = FilterQuality.None)
}

private fun qrBitmap(content: String): ImageBitmap {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 2))
    val width = matrix.width
    val pixels = IntArray(width * matrix.height) { if (matrix[it % width, it / width]) Color.BLACK else Color.WHITE }
    return Bitmap.createBitmap(pixels, width, matrix.height, Bitmap.Config.ARGB_8888).asImageBitmap()
}
