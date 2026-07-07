package com.secondbrain.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Menyiapkan file untuk fitur "Baca dengan AI":
 * - Gambar → diperkecil (maks sisi 1536 px, JPEG 80) → dikirim ke model vision.
 * - PDF → dikirim apa adanya ke Gemini (maks 4 MB).
 * - Word (.docx), Excel (.xlsx), CSV, TXT → teksnya DIEKSTRAK LOKAL (tanpa vision),
 *   lalu dirangkum model teks biasa — semua provider bisa.
 * Ukuran melebihi batas → error dengan pesan jelas.
 */
object MediaReader {

    const val MAX_BYTES = 4 * 1024 * 1024        // 4 MB untuk media (gambar/PDF)
    const val MAX_DOC_BYTES = 10 * 1024 * 1024   // 10 MB untuk dokumen (teksnya dipotong)
    const val MAX_TEXT_CHARS = 12_000

    private const val DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    sealed class Prepared {
        /** Gambar/PDF — dikirim sebagai media ke model vision. */
        data class Media(val mimeType: String, val base64: String) : Prepared()
        /** Dokumen — teks sudah diekstrak lokal, tinggal dirangkum model teks. */
        data class Text(val kind: String, val text: String) : Prepared()
    }

    fun prepareFromUri(context: Context, uri: Uri): Result<Prepared> = runCatching {
        val mime = context.contentResolver.getType(uri).orEmpty()
        val name = displayName(context, uri).lowercase()
        fun open(): InputStream? = context.contentResolver.openInputStream(uri)

        when {
            mime.startsWith("image/") -> prepareBitmap(decodeScaled(::open))

            mime == "application/pdf" || name.endsWith(".pdf") -> {
                val bytes = open()?.use { it.readBytes() } ?: throw RuntimeException("File tidak bisa dibaca.")
                if (bytes.size > MAX_BYTES) throw RuntimeException(
                    "PDF terlalu besar (${bytes.size / 1_048_576} MB — maksimal 4 MB). " +
                    "Kompres dulu, atau foto halaman yang dibutuhkan."
                )
                Prepared.Media("application/pdf", Base64.encodeToString(bytes, Base64.NO_WRAP))
            }

            mime == DOCX_MIME || name.endsWith(".docx") ->
                Prepared.Text("dokumen Word", readDocx(sizeChecked(context, uri, ::open)))

            mime == XLSX_MIME || name.endsWith(".xlsx") ->
                Prepared.Text("tabel Excel", readXlsx(sizeChecked(context, uri, ::open)))

            mime == "text/csv" || name.endsWith(".csv") ->
                Prepared.Text("data CSV", readPlainText(sizeChecked(context, uri, ::open)))

            mime.startsWith("text/") || name.endsWith(".txt") || name.endsWith(".md") ->
                Prepared.Text("dokumen teks", readPlainText(sizeChecked(context, uri, ::open)))

            else -> throw RuntimeException(
                "Jenis file \"${mime.ifBlank { name.substringAfterLast('.', "tidak dikenal") }}\" belum bisa dibaca AI. " +
                "Didukung: gambar, PDF, Word (.docx), Excel (.xlsx), CSV, dan TXT. " +
                "File lain tetap bisa dilampirkan biasa."
            )
        }
    }

    fun prepareImageFile(file: File): Result<Prepared> = runCatching {
        prepareBitmap(decodeScaled { FileInputStream(file) })
    }

    // ---------- Gambar ----------

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

    private fun prepareBitmap(src: Bitmap): Prepared.Media {
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
        return Prepared.Media("image/jpeg", Base64.encodeToString(bytes, Base64.NO_WRAP))
    }

    // ---------- Dokumen ----------

    /** Nama tampilan file (untuk fallback deteksi ekstensi bila MIME generik). */
    private fun displayName(context: Context, uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null }
    }.getOrNull() ?: uri.lastPathSegment.orEmpty()

    private fun sizeChecked(context: Context, uri: Uri, open: () -> InputStream?): () -> InputStream? {
        val size = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else -1L
            } ?: -1L
        }.getOrDefault(-1L)
        if (size > MAX_DOC_BYTES) throw RuntimeException(
            "File terlalu besar (${size / 1_048_576} MB — maksimal 10 MB untuk dokumen)."
        )
        return open
    }

    private fun readPlainText(open: () -> InputStream?): String {
        val raw = open()?.bufferedReader()?.use { it.readText() }
            ?: throw RuntimeException("File tidak bisa dibaca.")
        return capText(raw.trim())
    }

    /** .docx = zip berisi word/document.xml — ambil teksnya tanpa library tambahan. */
    private fun readDocx(open: () -> InputStream?): String {
        ZipInputStream(open() ?: throw RuntimeException("File tidak bisa dibuka.")).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val xml = zip.readBytes().toString(Charsets.UTF_8)
                    val text = xml
                        .replace(Regex("</w:p>"), "\n")
                        .replace(Regex("<[^>]+>"), "")
                        .let(::unescapeXml)
                        .lines().map { it.trim() }.filter { it.isNotEmpty() }
                        .joinToString("\n")
                    if (text.isBlank()) throw RuntimeException("Dokumen Word ini kosong / tidak berisi teks.")
                    return capText(text)
                }
                entry = zip.nextEntry
            }
        }
        throw RuntimeException("Isi dokumen Word tidak ditemukan (file rusak?).")
    }

    /** .xlsx = zip berisi sharedStrings + sheet XML — susun ulang jadi baris "a | b | c". */
    private fun readXlsx(open: () -> InputStream?): String {
        val shared = mutableListOf<String>()
        val sheets = LinkedHashMap<String, String>()
        ZipInputStream(open() ?: throw RuntimeException("File tidak bisa dibuka.")).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                when {
                    entry.name == "xl/sharedStrings.xml" -> {
                        val xml = zip.readBytes().toString(Charsets.UTF_8)
                        Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL).findAll(xml)
                            .forEach { shared.add(unescapeXml(it.groupValues[1])) }
                    }
                    entry.name.startsWith("xl/worksheets/sheet") && entry.name.endsWith(".xml") ->
                        sheets[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                }
                entry = zip.nextEntry
            }
        }
        if (sheets.isEmpty()) throw RuntimeException("Isi tabel Excel tidak ditemukan (file rusak?).")

        val cellRe = Regex("<c[^>]*?(?:t=\"(\\w+)\")?[^>]*>(.*?)</c>", RegexOption.DOT_MATCHES_ALL)
        val valRe = Regex("<v>(.*?)</v>", RegexOption.DOT_MATCHES_ALL)
        val inlineRe = Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL)

        val result = sheets.entries.mapIndexed { i, (_, xml) ->
            val rows = Regex("<row[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL).findAll(xml)
                .mapNotNull { row ->
                    val cells = cellRe.findAll(row.groupValues[1]).map { c ->
                        val type = c.groupValues[1]
                        val body = c.groupValues[2]
                        val v = valRe.find(body)?.groupValues?.get(1)
                            ?: inlineRe.find(body)?.groupValues?.get(1)
                            ?: ""
                        if (type == "s") shared.getOrNull(v.trim().toIntOrNull() ?: -1) ?: ""
                        else unescapeXml(v)
                    }.toList()
                    cells.joinToString(" | ").trim(' ', '|').ifBlank { null }
                }.joinToString("\n")
            if (sheets.size > 1) "— Sheet ${i + 1} —\n$rows" else rows
        }.joinToString("\n").trim()

        if (result.isBlank()) throw RuntimeException("Tabel Excel ini kosong.")
        return capText(result)
    }

    private fun unescapeXml(s: String): String = s
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'")

    private fun capText(s: String): String =
        if (s.length <= MAX_TEXT_CHARS) s
        else s.take(MAX_TEXT_CHARS) + "\n…(dipotong — file terlalu panjang, hanya bagian awal yang dibaca)"
}
