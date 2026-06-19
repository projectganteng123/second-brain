package com.secondbrain.app.ai

import com.secondbrain.app.data.model.Metadata
import com.secondbrain.app.util.DebugLog

/**
 * Orchestrator yang mencoba kombinasi (API key × model). Jika sebuah model kena limit
 * harian (429 PerDay), lanjut ke model/kunci berikutnya. Jika semua kombinasi kena limit,
 * mengembalikan pesan jelas ke pengguna.
 */
class AIService(
    private val keys: List<String>,
    private val models: List<String>,
    private val promptTemplate: String?
) {

    private fun combos(): List<Pair<String, String>> =
        keys.flatMap { k -> models.distinct().map { m -> k to m } }

    suspend fun extractMetadata(rawText: String, now: String): Result<Metadata> =
        runFallback { it.extractMetadata(rawText, now) }

    suspend fun answerQuestion(question: String, context: List<String>): Result<String> =
        runFallback { it.answerQuestion(question, context) }

    private fun mask(key: String): String =
        if (key.length <= 8) "($key)" else "${key.take(6)}…${key.takeLast(2)} (${key.length} char)"

    private suspend fun <T> runFallback(block: suspend (GeminiProvider) -> Result<T>): Result<T> {
        if (keys.isEmpty()) {
            return Result.failure(RuntimeException("API key Gemini belum diatur. Buka Pengaturan terlebih dahulu."))
        }
        DebugLog.log("AI keys", "${keys.size} key, model: ${models.joinToString(", ")}")
        var sawDaily = false
        var last: Throwable? = null

        for ((index, combo) in combos().withIndex()) {
            val (key, model) = combo
            DebugLog.log("AI →", "pakai key=${mask(key)} model=$model")
            val provider = GeminiProvider(AIConfig(key, model, promptTemplate))
            val res = block(provider)
            if (res.isSuccess) {
                if (index > 0) DebugLog.log("AI ↩ fallback", "berhasil dengan model=$model (kombinasi ke-${index + 1})")
                return res
            }
            val e = res.exceptionOrNull()
            last = e
            if (e is GeminiException && e.isDailyLimit) sawDaily = true
            // Coba kombinasi (key×model) berikutnya untuk SEMUA jenis kegagalan:
            // limit, auth, model tidak ada, model sibuk (503), timeout/jaringan, atau parsing.
            val reason = if (e is GeminiException) "HTTP ${e.status}" else (e?.javaClass?.simpleName ?: "error")
            DebugLog.log("AI ↪ coba lain", "key=${mask(key)} model=$model gagal ($reason), lanjut")
        }

        val msg = if (sawDaily)
            "Semua model & API key sudah kena limit harian Gemini. Coba lagi besok, atau tambah API key lain di Pengaturan."
        else
            (last?.message ?: "Gagal menghubungi AI dengan semua model & API key.")
        return Result.failure(RuntimeException(msg))
    }
}
