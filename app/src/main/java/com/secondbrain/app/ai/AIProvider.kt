package com.secondbrain.app.ai

import com.secondbrain.app.data.GsonProvider
import com.secondbrain.app.data.model.Metadata
import com.secondbrain.app.util.DebugLog

interface AIProvider {
    suspend fun extractMetadata(rawText: String, currentDateTime: String): Result<Metadata>
    suspend fun answerQuestion(question: String, contextNotes: List<String>): Result<String>
}

/** Jenis provider AI yang didukung. Urutan prioritas diatur di PrefsManager.PROVIDER_PRIORITY. */
enum class AIProviderType(val displayName: String) {
    GROQ("Groq"),
    CEREBRAS("Cerebras"),
    GEMINI("Gemini")
}

data class AIConfig(
    val apiKey: String,
    val model: String = "gemini-2.5-flash",
    /** Template ekstraksi kustom dari user. null = pakai PromptTemplates.DEFAULT_EXTRACTION */
    val extractionPromptTemplate: String? = null
)

/** Error HTTP bertipe agar orchestrator bisa memutuskan fallback, apa pun provider-nya. */
open class AIHttpException(
    val status: Int,
    val isRateLimit: Boolean,
    val isDailyLimit: Boolean,
    message: String
) : RuntimeException(message)

/** Error Gemini bertipe agar orchestrator bisa memutuskan fallback. */
class GeminiException(
    status: Int,
    isRateLimit: Boolean,
    isDailyLimit: Boolean,
    message: String
) : AIHttpException(status, isRateLimit, isDailyLimit, message)

/** Parser JSON metadata yang toleran terhadap pagar ```json dan teks di sekitar blok JSON. */
object MetadataParser {
    fun parse(raw: String): Metadata {
        var cleaned = raw.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```")
            .trim()
        // Ambil hanya blok { ... } pertama bila ada teks tambahan di sekitarnya
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start >= 0 && end > start) cleaned = cleaned.substring(start, end + 1)

        return try {
            GsonProvider.gson.fromJson(cleaned, Metadata::class.java)
                ?: throw RuntimeException("JSON kosong")
        } catch (e: Exception) {
            DebugLog.log("AI ✕ parse gagal", "${e.message}\n--- JSON mentah ---\n$cleaned")
            throw RuntimeException(
                "AI mengembalikan format yang tidak lengkap/terpotong. Coba proses ulang, " +
                "atau persingkat catatan. (lihat panel Debug untuk detail)"
            )
        }
    }
}
