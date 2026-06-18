package com.secondbrain.app.ai

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.secondbrain.app.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class GeminiProvider(private val config: AIConfig) : AIProvider {

    private val gson = Gson()

    override suspend fun extractMetadata(rawText: String, currentDateTime: String): Result<Metadata> =
        withContext(Dispatchers.IO) {
            runCatching {
                val prompt = buildExtractionPrompt(rawText, currentDateTime)
                val responseText = callGemini(prompt)
                parseMetadata(responseText)
            }
        }

    override suspend fun answerQuestion(question: String, contextNotes: List<String>): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val prompt = buildQAPrompt(question, contextNotes)
                callGemini(prompt)
            }
        }

    private fun callGemini(prompt: String): String {
        val url = URL(
            "https://generativelanguage.googleapis.com/v1beta/models/${config.model}:generateContent?key=${config.apiKey}"
        )
        val body = """
            {
              "contents": [{"parts": [{"text": ${gson.toJson(prompt)}}]}],
              "generationConfig": {"temperature": 0.1, "maxOutputTokens": 1024}
            }
        """.trimIndent()

        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 15000
        conn.readTimeout = 30000

        OutputStreamWriter(conn.outputStream).use { it.write(body) }

        val status = conn.responseCode
        if (status !in 200..299) {
            val errorBody = conn.errorStream?.bufferedReader()?.readText().orEmpty()
            throw RuntimeException(friendlyError(status, errorBody))
        }

        val response = conn.inputStream.bufferedReader().readText()
        val json = JsonParser.parseString(response).asJsonObject

        // Bisa diblokir safety filter -> tidak ada candidates
        val candidates = json.getAsJsonArray("candidates")
        if (candidates == null || candidates.size() == 0) {
            val block = runCatching {
                json.getAsJsonObject("promptFeedback").get("blockReason").asString
            }.getOrNull()
            throw RuntimeException(
                if (block != null) "Permintaan diblokir Gemini (alasan: $block). Coba ubah teks catatan."
                else "Gemini tidak mengembalikan jawaban. Coba lagi."
            )
        }

        return candidates
            .get(0).asJsonObject
            .getAsJsonObject("content")
            .getAsJsonArray("parts")
            .get(0).asJsonObject
            .get("text").asString
    }

    private fun friendlyError(status: Int, body: String): String {
        // Ambil pesan asli dari struktur { "error": { "message": "...", "status": "..." } }
        val apiMessage = runCatching {
            JsonParser.parseString(body).asJsonObject
                .getAsJsonObject("error").get("message").asString
        }.getOrNull()

        return when (status) {
            400 -> if (apiMessage?.contains("API key not valid", true) == true || apiMessage?.contains("API_KEY_INVALID", true) == true)
                "API key tidak valid. Pastikan kamu menyalin key dengan benar di Pengaturan."
            else "Permintaan ditolak (400): ${apiMessage ?: "format tidak sesuai"}"
            403 -> if (apiMessage?.contains("SERVICE_DISABLED", true) == true ||
                       apiMessage?.contains("has not been used", true) == true ||
                       apiMessage?.contains("disabled", true) == true)
                "Generative Language API belum aktif di project Google kamu. Buka console.developers.google.com → aktifkan \"Generative Language API\", lalu coba lagi. Atau buat API key baru di aistudio.google.com."
            else "Akses ditolak (403): ${apiMessage ?: "periksa izin API key"}"
            404 -> "Model AI tidak ditemukan (404). Mungkin nama model tidak tersedia untuk key kamu: ${apiMessage ?: ""}"
            429 -> {
                val quotaId = runCatching {
                    JsonParser.parseString(body).asJsonObject
                        .getAsJsonObject("error").getAsJsonArray("details")
                        .firstNotNullOf { d ->
                            d.asJsonObject.getAsJsonArray("violations")
                                ?.get(0)?.asJsonObject?.get("quotaId")?.asString
                        }
                }.getOrNull()
                val retry = runCatching {
                    JsonParser.parseString(body).asJsonObject
                        .getAsJsonObject("error").getAsJsonArray("details")
                        .firstNotNullOf { d ->
                            d.asJsonObject.get("retryDelay")?.asString
                        }
                }.getOrNull()
                val perDay = quotaId?.contains("PerDay", true) == true
                buildString {
                    append("Limit Gemini tercapai (429). ")
                    when {
                        perDay -> append("Kuota harian gratis habis (reset tengah malam waktu Pasifik). ")
                        quotaId != null -> append("Limit per-menit tercapai. ")
                        else -> append("")
                    }
                    if (retry != null) append("Coba lagi dalam $retry. ")
                    if (quotaId != null) append("[$quotaId]")
                    if (apiMessage != null && quotaId == null) append(apiMessage)
                }
            }
            in 500..599 -> "Server Gemini sedang bermasalah ($status). Coba lagi nanti."
            else -> "Gagal ($status): ${apiMessage ?: body.take(200)}"
        }
    }

    private fun parseMetadata(raw: String): Metadata {
        val cleaned = raw
            .trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```")
            .trim()
        return gson.fromJson(cleaned, Metadata::class.java)
    }

    private fun buildExtractionPrompt(rawText: String, currentDateTime: String): String = """
Kamu adalah asisten ekstraksi metadata dari catatan kerja dalam Bahasa Indonesia.

Waktu sekarang: $currentDateTime

Ekstrak metadata dari catatan berikut dan kembalikan HANYA JSON valid tanpa penjelasan apapun.

Catatan:
"$rawText"

Kembalikan JSON dengan struktur berikut (isi yang relevan saja, kosongkan yang tidak ada):
{
  "title": "judul singkat kegiatan",
  "type": "meeting|task|reminder|event|note|idea|personal",
  "startTime": "HH:mm atau null",
  "endTime": "HH:mm atau null",
  "locations": [{"type": "location|platform", "value": "nama tempat"}],
  "entities": {
    "people": ["nama orang"],
    "organizations": ["nama organisasi/perusahaan"]
  },
  "keywords": ["kata kunci penting"],
  "recurrenceDates": ["YYYY-MM-DD"],
  "actions": [{"action": "deskripsi aksi", "owner": "nama atau null", "deadline": "YYYY-MM-DD atau null"}],
  "summary": "ringkasan 1-3 kalimat"
}

Aturan:
- Semua tanggal relatif (besok, minggu depan, dll) harus dikonversi ke tanggal absolut berdasarkan waktu sekarang
- recurrenceDates maksimal 90 hari ke depan
- Untuk kegiatan berulang, generate semua tanggal dalam rentang tersebut
- type harus salah satu dari: meeting, task, reminder, event, note, idea, personal
- Kembalikan HANYA JSON, tidak ada teks lain
""".trimIndent()

    private fun buildQAPrompt(question: String, contextNotes: List<String>): String {
        val context = contextNotes.mapIndexed { i, n -> "[${i + 1}] $n" }.joinToString("\n\n")
        return """
Kamu adalah asisten pribadi yang menjawab pertanyaan berdasarkan catatan pengguna.

Pertanyaan: "$question"

Catatan yang relevan:
$context

Jawab pertanyaan secara ringkas dan langsung berdasarkan catatan di atas.
Jika informasi tidak tersedia dalam catatan, katakan dengan jelas.
Gunakan Bahasa Indonesia.
""".trimIndent()
    }
}
