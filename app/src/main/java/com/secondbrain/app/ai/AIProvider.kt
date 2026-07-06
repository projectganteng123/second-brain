package com.secondbrain.app.ai

interface AIProvider {
    /** Kirim satu prompt, minta output JSON mentah (parsing dilakukan pemanggil). */
    suspend fun generateJson(prompt: String): Result<String>
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
    val model: String = "gemini-2.5-flash"
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
