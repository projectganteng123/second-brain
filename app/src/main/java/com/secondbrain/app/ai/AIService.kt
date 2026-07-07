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

        // Hemat kuota: prompt Keuangan/Acara hanya jalan bila teksnya mengindikasikan
        // transaksi/jadwal (Universal selalu jalan).
        val needFinance = ExtractionHeuristics.mightContainFinance(rawText)
        val needSchedule = ExtractionHeuristics.mightContainSchedule(rawText)
        if (!needFinance || !needSchedule) {
            DebugLog.log(
                "AI ⏭ hemat",
                "prompt dilewati: " + listOfNotNull(
                    if (!needFinance) "Keuangan" else null,
                    if (!needSchedule) "Acara" else null
                ).joinToString(", ")
            )
        }

        coroutineScope {
            // Tiap prompt MULAI dari API key berbeda (round-robin per key) supaya panggilan
            // paralel tidak menghantam limit per-menit key yang sama.
            var slot = 0
            fun launchPrompt(template: String) = slot++.let { offset ->
                async {
                    runFallback(rotatedCombos(offset)) {
                        it.generateJson(PromptTemplates.fill(template, now, rawText))
                    }
                }
            }
            val uTask = launchPrompt(p.universal)
            val fTask = if (needFinance) launchPrompt(p.finance) else null
            val sTask = if (needSchedule) launchPrompt(p.schedule) else null

            val uRes = uTask.await()
            val fRes = fTask?.await()
            val sRes = sTask?.await()
            listOfNotNull(uRes, fRes, sRes).firstOrNull { it.isFailure }
                ?.let { throw it.exceptionOrNull()!! }

            ExtractionParser.merge(
                universalRaw = uRes.getOrThrow(),
                financeRaw = fRes?.getOrThrow() ?: EMPTY_FINANCE,
                scheduleRaw = sRes?.getOrThrow() ?: EMPTY_SCHEDULE
            )
        }
    }.onFailure {
        // Pembatalan user bukan kegagalan — teruskan agar coroutine benar-benar berhenti.
        if (it is kotlinx.coroutines.CancellationException) throw it
    }

    /**
     * Putar urutan per API KEY (bukan sekadar provider): key = satuan limit per-menit.
     * Prompt ke-0 mulai dari key #1, ke-1 dari key #2, dst. — mis. Groq-1, Groq-2,
     * Cerebras-1. Fallback tetap menjalar ke seluruh kombinasi key×model lain.
     */
    private fun rotatedCombos(offset: Int): List<AICombo> {
        val units = combos.groupBy { it.provider to it.key }.values.toList()
        if (units.size <= 1) return combos
        val k = offset % units.size
        return (units.drop(k) + units.take(k)).flatten()
    }

    suspend fun answerQuestion(question: String, context: List<String>): Result<String> =
        runFallback { it.answerQuestion(question, context) }

    private fun mask(key: String): String =
        if (key.length <= 8) "($key)" else "${key.take(6)}…${key.takeLast(2)} (${key.length} char)"

    private fun providerFor(combo: AICombo): AIProvider = when (combo.provider) {
        AIProviderType.GEMINI -> GeminiProvider(AIConfig(combo.key, combo.model))
        else -> OpenAICompatProvider(combo.provider, combo.key, combo.model)
    }

    private suspend fun <T> runFallback(
        comboList: List<AICombo> = combos,
        block: suspend (AIProvider) -> Result<T>
    ): Result<T> {
        if (comboList.isEmpty()) {
            return Result.failure(RuntimeException(
                "API key belum diatur atau tidak ada provider yang dicentang. Buka Pengaturan terlebih dahulu."
            ))
        }
        var sawDaily = false
        var last: Throwable? = null

        for ((index, combo) in comboList.withIndex()) {
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
        private const val EMPTY_FINANCE = """{"transactions":[]}"""
        private const val EMPTY_SCHEDULE = """{"schedules":[]}"""

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
