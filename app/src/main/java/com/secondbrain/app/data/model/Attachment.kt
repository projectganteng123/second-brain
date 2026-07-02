package com.secondbrain.app.data.model

import com.google.gson.reflect.TypeToken
import com.secondbrain.app.data.GsonProvider

/**
 * Lampiran sebuah catatan. File disimpan di folder internal app (filesDir/attachments),
 * [path] berisi path RELATIF terhadap filesDir (mis. "attachments/IMG_x.jpg") agar tetap
 * valid setelah backup/restore antar perangkat. Untuk [TYPE_LINK], [path] berisi URL penuh.
 * Lampiran TIDAK dikirim ke AI — hanya teks penanda "[Lampiran ...]" yang masuk ke catatan.
 */
data class Attachment(
    val type: String,
    val path: String,
    val name: String = ""
) {
    companion object {
        const val TYPE_IMAGE = "image"
        const val TYPE_VIDEO = "video"
        const val TYPE_FILE = "file"
        const val TYPE_LINK = "link"

        fun listFromJson(json: String): List<Attachment> {
            if (json.isBlank()) return emptyList()
            return runCatching {
                val type = object : TypeToken<List<Attachment>>() {}.type
                GsonProvider.gson.fromJson<List<Attachment>>(json, type) ?: emptyList()
            }.getOrDefault(emptyList())
        }

        fun listToJson(list: List<Attachment>): String =
            if (list.isEmpty()) "" else GsonProvider.gson.toJson(list)
    }
}
