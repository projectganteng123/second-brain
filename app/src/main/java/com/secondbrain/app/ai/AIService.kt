package com.secondbrain.app.ai

import com.secondbrain.app.data.model.Metadata
import com.secondbrain.app.util.DebugLog
import com.secondbrain.app.util.PrefsManager

/** Satu kombinasi (provider × API key × model) yang akan dicoba berurutan. */
data class AICombo(val provider: AIProviderType, val key: String, val model: String)

/**
 * Orchestrator yang mencoba kombinasi (provider × API key × model) sesuai urutan prioritas
 * provider (Groq → Cerebras → Gemini). Jika sebuah kombinasi gagal (limit, auth, jaringan,
 * dll), lanjut ke kombinasi berikutnya. Jika semua kena limit harian, mengembalikan pesan
 * jelas ke pengguna.
 */
class AIService(
    private val combos: List<AICombo>,
    private val promptTemplate: String?
) {

    suspend fun extractMetadata(rawText: String, now: String): Result<Metadata> =
        runFallback { it.extractMetadata(rawText, now) }

    suspend fun answerQuestion(question: String, context: List<String>): Result<String> =
        runFallback { it.answerQuestion(question, context) }

    private fun mask(key: String): String =
        if (key.length <= 8) "($key)" else "${key.take(6)}…${key.takeLast(2)} (${key.length} char)"

    private fun providerFor(combo: AICombo): AIProvider = when (combo.provider) {
        AIProviderType.GEMINI -> GeminiProvider(AIConfig(combo.key, combo.model, promptTemplate))
        else -> OpenAICompatProvider(combo.provider, combo.key, combo.model, promptTemplate)
    }

    private suspend fun <T> runFallback(block: suspend (AIProvider) -> Result<T>): Result<T> {
        if (combos.isEmpty()) {
            return Result.failure(RuntimeException(
                "API key belum diatur atau tidak ada provider yang dicentang. Buka Pengaturan terlebih dahulu."
            ))
        }
        val summary = combos.groupBy { it.provider }.entries.joinToString("; ") { (p, list) ->
            "${p.displayName}: ${list.distinctBy { it.key }.size} key, " +
                "model ${list.map { it.model }.distinct().joinToString(", ")}"
        }
        DebugLog.log("AI keys", summary)
        var sawDaily = false
        var last: Throwable? = null

        for ((index, combo) in combos.withIndex()) {
            DebugLog.log("AI →", "pakai ${combo.provider.displayName} key=${mask(combo.key)} model=${combo.model}")
            val res = block(providerFor(combo))
            if (res.isSuccess) {
                if (index > 0) DebugLog.log(
                    "AI ↩ fallback",
                    "berhasil dengan ${combo.provider.displayName}/${combo.model} (kombinasi ke-${index + 1})"
                )
                return res
            }
            val e = res.exceptionOrNull()
            last = e
            if (e is AIHttpException && e.isDailyLimit) sawDaily = true
            // Coba kombinasi berikutnya untuk SEMUA jenis kegagalan:
            // limit, auth, model tidak ada, model sibuk (503), timeout/jaringan, atau parsing.
            val reason = if (e is AIHttpException) "HTTP ${e.status}" else (e?.javaClass?.simpleName ?: "error")
            DebugLog.log(
                "AI ↪ coba lain",
                "${combo.provider.displayName} key=${mask(combo.key)} model=${combo.model} gagal ($reason), lanjut"
            )
        }

        val msg = if (sawDaily)
            "Semua provider, model & API key sudah kena limit harian. Coba lagi besok, atau tambah API key lain di Pengaturan."
        else
            (last?.message ?: "Gagal menghubungi AI dengan semua provider, model & API key.")
        return Result.failure(RuntimeException(msg))
    }

    companion object {
        /** Service untuk ekstraksi metadata (model ringan dulu). */
        fun forExtraction(prefs: PrefsManager): AIService =
            AIService(buildCombos(prefs, forAnswer = false), prefs.getCustomPrompt().ifBlank { null })

        /** Service untuk tanya-jawab (model kuat dulu). */
        fun forAnswer(prefs: PrefsManager): AIService =
            AIService(buildCombos(prefs, forAnswer = true), null)

        private fun buildCombos(prefs: PrefsManager, forAnswer: Boolean): List<AICombo> =
            prefs.activeProviders().flatMap { p ->
                val models = if (forAnswer) PrefsManager.answerModels(p) else PrefsManager.extractionModels(p)
                prefs.getApiKeys(p).flatMap { k -> models.map { m -> AICombo(p, k, m) } }
            }
    }
}
