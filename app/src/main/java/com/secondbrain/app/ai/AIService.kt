package com.secondbrain.app.ai

import com.secondbrain.app.data.model.Metadata
import com.secondbrain.app.util.DebugLog
import com.secondbrain.app.util.PrefsManager
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Satu kombinasi (provider × API key × model) yang akan dicoba berurutan. */
data class AICombo(val provider: AIProviderType, val key: String, val model: String)

/** Tiga template prompt ekstraksi (custom dari Settings, atau default). */
data class ExtractionPrompts(
    val universal: String,
    val finance: String,
    val schedule: String
) {
    companion object {
        fun from(prefs: PrefsManager): ExtractionPrompts = ExtractionPrompts(
            universal = prefs.getExtractionPrompt(ExtractionKind.UNIVERSAL)
                .ifBlank { PromptTemplates.DEFAULT_UNIVERSAL },
            finance = prefs.getExtractionPrompt(ExtractionKind.FINANCE)
                .ifBlank { PromptTemplates.DEFAULT_FINANCE },
            schedule = prefs.getExtractionPrompt(ExtractionKind.SCHEDULE)
                .ifBlank { PromptTemplates.DEFAULT_SCHEDULE }
        )
    }
}

/**
 * Orchestrator yang mencoba kombinasi (provider × API key × model) sesuai urutan prioritas
 * provider (Groq → Cerebras → Gemini). Jika sebuah kombinasi gagal (limit, auth, jaringan,
 * dll), lanjut ke kombinasi berikutnya. Jika semua kena limit harian, mengembalikan pesan
 * jelas ke pengguna.
 *
 * Ekstraksi = TIGA prompt (Universal, Keuangan, Acara) dijalankan PARALEL — masing-masing
 * dengan tangga fallback sendiri — lalu hasilnya digabung ExtractionParser.merge menjadi
 * satu Metadata. Jika salah satu gagal total, seluruh ekstraksi dianggap gagal (catatan
 * jatuh ke isPendingExtraction, diproses ulang nanti).
 */
class AIService(
    private val combos: List<AICombo>,
    private val prompts: ExtractionPrompts?
) {

    suspend fun extractMetadata(rawText: String, now: String): Result<Metadata> = runCatching {
        val p = prompts ?: throw IllegalStateException("AIService ini dibuat untuk tanya-jawab, bukan ekstraksi")
        coroutineScope {
            val universal = async { runFallback { it.generateJson(PromptTemplates.fill(p.universal, now, rawText)) } }
            val finance = async { runFallback { it.generateJson(PromptTemplates.fill(p.finance, now, rawText)) } }
            val schedule = async { runFallback { it.generateJson(PromptTemplates.fill(p.schedule, now, rawText)) } }
            val results = listOf(universal.await(), finance.await(), schedule.await())
            results.firstOrNull { it.isFailure }?.let { throw it.exceptionOrNull()!! }
            ExtractionParser.merge(
                universalRaw = results[0].getOrThrow(),
                financeRaw = results[1].getOrThrow(),
                scheduleRaw = results[2].getOrThrow()
            )
        }
    }

    suspend fun answerQuestion(question: String, context: List<String>): Result<String> =
        runFallback { it.answerQuestion(question, context) }

    private fun mask(key: String): String =
        if (key.length <= 8) "($key)" else "${key.take(6)}…${key.takeLast(2)} (${key.length} char)"

    private fun providerFor(combo: AICombo): AIProvider = when (combo.provider) {
        AIProviderType.GEMINI -> GeminiProvider(AIConfig(combo.key, combo.model))
        else -> OpenAICompatProvider(combo.provider, combo.key, combo.model)
    }

    private suspend fun <T> runFallback(block: suspend (AIProvider) -> Result<T>): Result<T> {
        if (combos.isEmpty()) {
            return Result.failure(RuntimeException(
                "API key belum diatur atau tidak ada provider yang dicentang. Buka Pengaturan terlebih dahulu."
            ))
        }
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
        /** Service untuk ekstraksi metadata (model ringan dulu; 3 prompt paralel). */
        fun forExtraction(prefs: PrefsManager): AIService =
            AIService(buildCombos(prefs, forAnswer = false), ExtractionPrompts.from(prefs))

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
