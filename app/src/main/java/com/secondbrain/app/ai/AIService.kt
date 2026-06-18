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
    private val preferredModel: String,
    private val modelPool: List<String>,
    private val promptTemplate: String?
) {

    private fun combos(): List<Pair<String, String>> {
        val models = (listOf(preferredModel) + modelPool).distinct()
        return keys.flatMap { k -> models.map { m -> k to m } }
    }

    suspend fun extractMetadata(rawText: String, now: String): Result<Metadata> =
        runFallback { it.extractMetadata(rawText, now) }

    suspend fun answerQuestion(question: String, context: List<String>): Result<String> =
        runFallback { it.answerQuestion(question, context) }

    private suspend fun <T> runFallback(block: suspend (GeminiProvider) -> Result<T>): Result<T> {
        if (keys.isEmpty()) {
            return Result.failure(RuntimeException("API key Gemini belum diatur. Buka Pengaturan terlebih dahulu."))
        }
        var sawDaily = false
        var last: Throwable? = null

        for ((index, combo) in combos().withIndex()) {
            val (key, model) = combo
            val provider = GeminiProvider(AIConfig(key, model, promptTemplate))
            val res = block(provider)
            if (res.isSuccess) {
                if (index > 0) DebugLog.log("AI ↩ fallback", "berhasil dengan model=$model (kombinasi ke-${index + 1})")
                return res
            }
            val e = res.exceptionOrNull()
            last = e
            if (e is GeminiException) {
                if (e.isDailyLimit) sawDaily = true
                if (e.isRateLimit || e.status == 400 || e.status == 403 || e.status == 404) {
                    // limit / kunci tidak valid / model tidak tersedia → coba kombinasi berikutnya
                    DebugLog.log("AI ↪ coba lain", "model=$model gagal (${e.status}), lanjut kombinasi berikutnya")
                    continue
                }
                return res
            } else {
                // error non-HTTP (parsing/jaringan) → hentikan, tidak perlu spam semua kombinasi
                return res
            }
        }

        val msg = if (sawDaily)
            "Semua model & API key sudah kena limit harian Gemini. Coba lagi besok, atau tambah API key lain di Pengaturan."
        else
            (last?.message ?: "Gagal menghubungi AI dengan semua model & API key.")
        return Result.failure(RuntimeException(msg))
    }
}
