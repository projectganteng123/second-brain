package com.secondbrain.app.ai

import com.google.gson.JsonParser
import com.secondbrain.app.data.GsonProvider
import com.secondbrain.app.util.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Provider untuk API yang kompatibel dengan format OpenAI chat completions:
 * Groq (api.groq.com) dan Cerebras (api.cerebras.ai).
 */
class OpenAICompatProvider(
    private val type: AIProviderType,
    private val apiKey: String,
    private val model: String
) : AIProvider {

    private val gson = GsonProvider.gson
    private val name = type.displayName

    private val endpoint = when (type) {
        AIProviderType.GROQ -> "https://api.groq.com/openai/v1/chat/completions"
        AIProviderType.CEREBRAS -> "https://api.cerebras.ai/v1/chat/completions"
        AIProviderType.GEMINI -> throw IllegalArgumentException("Gemini memakai GeminiProvider")
    }

    override suspend fun generateJson(prompt: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                DebugLog.log("AI → request", "provider=$name model=$model\n$prompt")
                val responseText = call(prompt, jsonOutput = true)
                DebugLog.log("AI ← response", responseText)
                responseText
            }.onFailure { DebugLog.log("AI ✕ error", it.message ?: it.toString()) }
        }

    override suspend fun answerQuestion(question: String, contextNotes: List<String>): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val prompt = PromptTemplates.qaPrompt(question, contextNotes)
                DebugLog.log("AI → tanya", "provider=$name model=$model\n$prompt")
                val ans = call(prompt, jsonOutput = false)
                DebugLog.log("AI ← jawab", ans)
                ans
            }.onFailure { DebugLog.log("AI ✕ error", it.message ?: it.toString()) }
        }

    private fun call(prompt: String, jsonOutput: Boolean): String {
        val body = buildString {
            append("{")
            append("\"model\": ${gson.toJson(model)}, ")
            append("\"messages\": [{\"role\": \"user\", \"content\": ${gson.toJson(prompt)}}], ")
            append("\"temperature\": 0.1, ")
            append("\"max_completion_tokens\": 8192")
            // JSON mode hanya untuk Groq; dukungan json_object di Cerebras belum merata
            // di semua model. ExtractionParser sudah toleran terhadap teks di sekitar JSON.
            if (jsonOutput && type == AIProviderType.GROQ) {
                append(", \"response_format\": {\"type\": \"json_object\"}")
            }
            append("}")
        }

        val conn = URL(endpoint).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        conn.doOutput = true
        conn.connectTimeout = 15000
        conn.readTimeout = 45000

        OutputStreamWriter(conn.outputStream).use { it.write(body) }

        val status = conn.responseCode
        if (status !in 200..299) {
            val errorBody = conn.errorStream?.bufferedReader()?.readText().orEmpty()
            DebugLog.log("AI ✕ http $status", "[$name] " + errorBody.take(800))
            val isRate = status == 429
            val isDaily = isRate && (
                errorBody.contains("per day", true) ||
                errorBody.contains("PerDay", true) ||
                errorBody.contains("(RPD)") ||
                errorBody.contains("(TPD)") ||
                errorBody.contains("daily", true)
            )
            throw AIHttpException(status, isRate, isDaily, friendlyError(status, errorBody))
        }

        val response = conn.inputStream.bufferedReader().readText()
        val json = JsonParser.parseString(response).asJsonObject

        val choices = json.getAsJsonArray("choices")
        if (choices == null || choices.size() == 0) {
            throw RuntimeException("$name tidak mengembalikan jawaban. Coba lagi.")
        }
        val choice = choices.get(0).asJsonObject
        val finishReason = runCatching { choice.get("finish_reason").asString }.getOrNull()
        val text = runCatching {
            choice.getAsJsonObject("message").get("content").asString
        }.getOrNull().orEmpty()

        if (text.isBlank()) {
            throw RuntimeException("Respons $name kosong (finish_reason=$finishReason).")
        }
        if (finishReason == "length") {
            DebugLog.log("AI ⚠ length", "Output $name terpotong; pertimbangkan teks lebih singkat.")
        }
        return text
    }

    private fun friendlyError(status: Int, body: String): String {
        // Struktur error OpenAI-compat: { "error": { "message": "...", "type": "..." } }
        val apiMessage = runCatching {
            JsonParser.parseString(body).asJsonObject
                .getAsJsonObject("error").get("message").asString
        }.getOrNull()

        return when (status) {
            400 -> "Permintaan ditolak $name (400): ${apiMessage ?: "format tidak sesuai"}"
            401 -> "API key $name tidak valid/tidak dikenali (401). Kemungkinan key tersimpan tidak persis sama " +
                "(ada spasi/karakter tersembunyi). Hapus isi field, tempel ulang HANYA key baru, lalu Simpan."
            403 -> "Akses ditolak $name (403): ${apiMessage ?: "periksa izin API key"}"
            404 -> "Model \"$model\" tidak ditemukan di $name (404). ${apiMessage.orEmpty()}"
            429 -> "Limit $name tercapai (429). ${apiMessage ?: "Coba lagi nanti, atau tambah API key lain di Pengaturan."}"
            in 500..599 -> "Server $name sedang bermasalah ($status). Coba lagi nanti."
            else -> "Gagal di $name ($status): ${apiMessage ?: body.take(200)}"
        }
    }
}
