package com.secondbrain.app.ai

import com.google.gson.JsonParser
import com.secondbrain.app.data.GsonProvider
import com.secondbrain.app.data.model.*
import com.secondbrain.app.util.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class GeminiProvider(private val config: AIConfig) : AIProvider {

    private val gson = GsonProvider.gson

    override suspend fun generateJson(prompt: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                DebugLog.log("AI → request", "model=${config.model}\n$prompt")
                // Output JSON ekstraksi kecil — 2048 cukup, hemat jatah token-per-menit.
                val responseText = callGemini(prompt, jsonOutput = true, maxTokens = 2048)
                DebugLog.log("AI ← response", responseText)
                responseText
            }.onFailure { DebugLog.log("AI ✕ error", it.message ?: it.toString()) }
        }

    override suspend fun answerQuestion(question: String, contextNotes: List<String>): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val prompt = buildQAPrompt(question, contextNotes)
                DebugLog.log("AI → tanya", prompt)
                val ans = callGemini(prompt, jsonOutput = false, maxTokens = 8192)
                DebugLog.log("AI ← jawab", ans)
                ans
            }.onFailure { DebugLog.log("AI ✕ error", it.message ?: it.toString()) }
        }

    private fun callGemini(prompt: String, jsonOutput: Boolean, maxTokens: Int): String {
        val url = URL(
            "https://generativelanguage.googleapis.com/v1beta/models/${config.model}:generateContent?key=${config.apiKey}"
        )
        // thinkingBudget=0 mematikan "thinking" pada model 2.5 agar seluruh token output
        // dipakai untuk jawaban (mencegah JSON terpotong). responseMimeType memaksa JSON murni.
        val genConfig = buildString {
            append("\"temperature\": 0.1, \"maxOutputTokens\": $maxTokens")
            append(", \"thinkingConfig\": {\"thinkingBudget\": 0}")
            if (jsonOutput) append(", \"responseMimeType\": \"application/json\"")
        }
        val body = """
            {
              "contents": [{"parts": [{"text": ${gson.toJson(prompt)}}]}],
              "generationConfig": {$genConfig}
            }
        """.trimIndent()

        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 15000
        conn.readTimeout = 45000

        OutputStreamWriter(conn.outputStream).use { it.write(body) }

        val status = conn.responseCode
        if (status !in 200..299) {
            val errorBody = conn.errorStream?.bufferedReader()?.readText().orEmpty()
            DebugLog.log("AI ✕ http $status", errorBody.take(800))
            val isRate = status == 429
            val isDaily = isRate && (errorBody.contains("PerDay", true) ||
                errorBody.contains("limit: 0", true) || errorBody.contains("per day", true))
            throw GeminiException(status, isRate, isDaily, friendlyError(status, errorBody))
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

        val candidate = candidates.get(0).asJsonObject
        val finishReason = runCatching { candidate.get("finishReason").asString }.getOrNull()

        // Gabungkan SEMUA parts (model bisa membagi teks ke beberapa part)
        val parts = candidate.getAsJsonObject("content")?.getAsJsonArray("parts")
        val text = buildString {
            parts?.forEach { p ->
                runCatching { p.asJsonObject.get("text").asString }.getOrNull()?.let { append(it) }
            }
        }

        if (text.isBlank()) {
            throw RuntimeException("Respons AI kosong (finishReason=$finishReason).")
        }
        if (finishReason == "MAX_TOKENS") {
            DebugLog.log("AI ⚠ MAX_TOKENS", "Output terpotong; pertimbangkan teks lebih singkat.")
        }
        return text
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
            401 -> "API key tidak dikenali (401). Kemungkinan key tersimpan tidak persis sama " +
                "(ada spasi/karakter tersembunyi) atau Generative Language API belum aktif di project key tsb. " +
                "Hapus isi field, tempel ulang HANYA key baru, lalu Simpan."
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

    private fun buildQAPrompt(question: String, contextNotes: List<String>): String =
        PromptTemplates.qaPrompt(question, contextNotes)
}
