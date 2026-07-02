package com.secondbrain.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.secondbrain.app.data.model.Attachment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Menyimpan file lampiran ke folder internal app (filesDir/attachments) dan membuka
 * lampiran lewat aplikasi lain (galeri/pemutar video/pembuka file) via FileProvider.
 * Lampiran TIDAK pernah dikirim ke AI.
 */
object AttachmentStore {

    private const val DIR = "attachments"

    fun dir(context: Context): File =
        File(context.filesDir, DIR).apply { mkdirs() }

    fun resolve(context: Context, att: Attachment): File =
        File(context.filesDir, att.path)

    /** File tujuan untuk hasil jepretan kamera. */
    fun newImageFile(context: Context): File {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(dir(context), "IMG_$stamp.jpg")
    }

    fun contentUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /** Attachment untuk file foto hasil kamera yang sudah berada di folder lampiran. */
    fun imageAttachment(file: File): Attachment =
        Attachment(type = Attachment.TYPE_IMAGE, path = "$DIR/${file.name}", name = file.name)

    /**
     * Salin isi [uri] (galeri/file picker) ke folder lampiran.
     * Mengembalikan Attachment dengan path relatif, atau null bila gagal dibaca.
     */
    fun copyIntoStore(context: Context, uri: Uri): Attachment? {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri)
        val displayName = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: "lampiran"

        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(60)
        val outFile = File(dir(context), "${stamp}_$safeName")

        return runCatching {
            resolver.openInputStream(uri)?.use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            Attachment(type = typeFromMime(mime), path = "$DIR/${outFile.name}", name = displayName)
        }.onFailure {
            outFile.delete()
            DebugLog.log("Lampiran ✕", "gagal salin: ${it.message}")
        }.getOrNull()
    }

    fun typeFromMime(mime: String?): String = when {
        mime?.startsWith("image/") == true -> Attachment.TYPE_IMAGE
        mime?.startsWith("video/") == true -> Attachment.TYPE_VIDEO
        else -> Attachment.TYPE_FILE
    }

    /** Teks penanda yang ditambahkan ke catatan agar AI tahu ada lampiran (file tidak dikirim). */
    fun markerFor(att: Attachment): String = when (att.type) {
        Attachment.TYPE_IMAGE -> "[Lampiran foto: ${att.name}]"
        Attachment.TYPE_VIDEO -> "[Lampiran video: ${att.name}]"
        Attachment.TYPE_LINK -> "[Lampiran link: ${att.path}]"
        else -> "[Lampiran file: ${att.name}]"
    }

    private fun mimeFor(att: Attachment): String {
        val ext = att.path.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: when (att.type) {
            Attachment.TYPE_IMAGE -> "image/*"
            Attachment.TYPE_VIDEO -> "video/*"
            else -> "*/*"
        }
    }

    /** Buka lampiran: link ke browser, file lokal ke app yang sesuai (galeri/pemutar/pembuka). */
    fun open(context: Context, att: Attachment): Boolean = runCatching {
        val intent = if (att.type == Attachment.TYPE_LINK) {
            val url = if (att.path.startsWith("http", true)) att.path else "https://${att.path}"
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
        } else {
            val file = resolve(context, att)
            if (!file.exists()) throw RuntimeException("File lampiran tidak ditemukan: ${att.path}")
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri(context, file), mimeFor(att))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    }.onFailure { DebugLog.log("Lampiran ✕ buka", it.message ?: it.toString()) }
        .getOrDefault(false)

    /** Hapus file fisik lampiran lokal (dipanggil saat user membatalkan lampiran). */
    fun deleteFile(context: Context, att: Attachment) {
        if (att.type != Attachment.TYPE_LINK) {
            runCatching { resolve(context, att).delete() }
        }
    }
}
