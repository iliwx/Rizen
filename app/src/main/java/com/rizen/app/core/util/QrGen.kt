package com.rizen.app.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File
import java.io.FileOutputStream
import kotlin.random.Random

object QrGen {

    private const val PREFIX = "WAKEPROTOCOL:"

    /** A payload that can't collide with a random QR on a cereal box. */
    fun newPayload(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val body = (1..16).map { chars[Random.nextInt(chars.length)] }.joinToString("")
        return "$PREFIX$body"
    }

    fun matches(scanned: String?, expected: String): Boolean =
        !scanned.isNullOrBlank() && expected.isNotBlank() && scanned.trim() == expected.trim()

    /** High error correction so it still scans after being printed and taped to a mirror. */
    fun bitmap(payload: String, sizePx: Int = 720): Bitmap {
        val matrix = QRCodeWriter().encode(
            payload,
            BarcodeFormat.QR_CODE,
            sizePx,
            sizePx,
            mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
                EncodeHintType.MARGIN to 2,
            ),
        )
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
    }

    /** Writes to app files/qr and returns a shareable content:// uri. */
    fun share(context: Context, payload: String): android.net.Uri {
        val dir = File(context.filesDir, "qr").apply { mkdirs() }
        val file = File(dir, "wake_protocol_code.png")
        FileOutputStream(file).use { out ->
            bitmap(payload, 1080).compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
