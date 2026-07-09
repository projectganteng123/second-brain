package com.secondbrain.app.ai

interface AIProvider {
    /** Kirim satu prompt, minta output JSON mentah (parsing dilakukan pemanggil). */
    suspend fun generateJson(prompt: String, maxTokens: Int = 2048): Result<String>
    suspend fun answerQuestion(question: String, contextNotes: List<String>): Result<String>

    /** Kirim prompt + media (gambar/PDF, base64). Provider tanpa vision mengembalikan failure
     *  sehingga tangga fallback lanjut ke provider berikutnya. */
    suspend fun generateJsonWithMedia(
        prompt: String,
        mimeType: String,
        dataBase64: String,
        maxTokens: Int = 4096
    ): Result<String> =
        Result.failure(UnsupportedOperationException("Provider ini tidak mendukung input gambar/dokumen"))
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
