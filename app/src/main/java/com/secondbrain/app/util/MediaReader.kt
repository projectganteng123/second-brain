package com.secondbrain.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * Menyiapkan gambar/PDF untuk dikirim ke AI (fitur "Baca dengan AI"):
 * gambar diperkecil (maks sisi 1536 px, JPEG 80) supaya hemat token & aman dari batas
 * ukuran permintaan; PDF dikirim apa adanya. Lebih besar dari [MAX_BYTES] → error jelas.
 */
object MediaReader {

    const val MAX_BYTES = 4 * 1024 * 1024   // 4 MB

    data class Prepared(val mimeType: String, val base64: String)

    fun prepareFromUri(context: Context, uri: Uri): Result<Prepared> = runCatching {
        val mime = context.contentResolver.getType(uri).orEmpty()
        when {
            mime.startsWith("image/") ->
                prepareBitmap(decodeScaled { context.contentResolver.openInputStream(uri) })

            mime == "application/pdf" -> {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw RuntimeException("File tidak bisa dibaca.")
                if (bytes.size > MAX_BYTES) throw RuntimeException(
                    "PDF terlalu besar (${bytes.size / 1_048_576} MB — maksimal 4 MB). " +
                    "Kompres dulu, atau foto halaman yang dibutuhkan."
                )
                Prepared("application/pdf", Base64.encodeToString(bytes, Base64.NO_WRAP))
            }

            else -> throw RuntimeException(
                "Jenis file \"${mime.ifBlank { "tidak dikenal" }}\" belum bisa dibaca AI. " +
                "Yang didukung: gambar & PDF. File lain tetap bisa dilampirkan biasa."
            )
        }
    }

    fun prepareImageFile(file: File): Result<Prepared> = runCatching {
        prepareBitmap(decodeScaled { FileInputStream(file) })
    }

    /** Decode dengan inSampleSize agar gambar besar tidak bikin kehabisan memori. */
    private fun decodeScaled(open: () -> InputStream?): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: throw RuntimeException("Gambar tidak bisa dibuka.")
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1536) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return open()?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw RuntimeException("Gambar tidak bisa dibaca.")
    }

    private fun prepareBitmap(src: Bitmap): Prepared {
        val maxDim = 1536
        val scale = minOf(1f, maxDim.toFloat() / maxOf(src.width, src.height))
        val bmp = if (scale < 1f) {
            Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true)
        } else src
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
        val bytes = out.toByteArray()
        if (bytes.size > MAX_BYTES) throw RuntimeException(
            "Gambar masih terlalu besar setelah dikompres (maksimal 4 MB). Coba foto ulang."
        )
        return Prepared("image/jpeg", Base64.encodeToString(bytes, Base64.NO_WRAP))
    }
}
