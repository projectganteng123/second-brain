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

    suspend fun extractMetadata(
        rawText: String,
        now: String,
        groupNames: List<String> = emptyList()
    ): Result<Metadata> = runCatching {
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
                        it.generateJson(PromptTemplates.fill(template, now, rawText, groupNames))
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

    /** Hasil pembacaan gambar/dokumen: jenis sumber + teks isi. */
    data class MediaText(val source: String, val text: String)

    /** Baca gambar/PDF jadi teks catatan (dipakai fitur "Baca dengan AI" di layar Input).
     *  [promptTemplate] dari Settings (bisa diedit user). Key yang berhasil dicatat agar
     *  TIDAK diprioritaskan pada ekstraksi berikutnya. */
    suspend fun readMedia(
        mimeType: String,
        dataBase64: String,
        promptTemplate: String = PromptTemplates.MEDIA_READ
    ): Result<MediaText> = runCatching {
        if (combos.isEmpty()) {
            throw RuntimeException(
                "Membaca gambar/PDF butuh API key Gemini (gambar & PDF) atau Groq (gambar saja). " +
                "Tambahkan di Pengaturan."
            )
        }
        // Beberapa file dibaca bersamaan → tiap pembacaan mulai dari key berbeda (round-robin)
        val raw = runFallback(rotatedCombos(READ_SLOT.getAndIncrement()), onComboSuccess = { noteVisionKeyUsed(it.key) }) {
            // Ekstraksi lengkap butuh ruang output lebih besar dari JSON metadata biasa
            it.generateJsonWithMedia(promptTemplate, mimeType, dataBase64, maxTokens = 4096)
        }.getOrThrow()
        val (source, text) = ExtractionParser.parseMedia(raw)
        if (text.isBlank()) throw RuntimeException("AI tidak menemukan teks yang bisa dibaca pada file ini.")
        MediaText(source, text)
    }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }

    /** Baca isi dokumen (Word/Excel/CSV/TXT — teks sudah diekstrak lokal) jadi teks catatan.
     *  Tidak butuh vision, jadi semua provider bisa. [promptTemplate] dari Settings. */
    suspend fun readDocument(
        kind: String,
        content: String,
        promptTemplate: String = PromptTemplates.DOC_READ
    ): Result<MediaText> = runCatching {
        if (combos.isEmpty()) {
            throw RuntimeException("API key belum diatur atau tidak ada provider yang dicentang. Buka Pengaturan terlebih dahulu.")
        }
        val raw = runFallback(rotatedCombos(READ_SLOT.getAndIncrement()), onComboSuccess = { noteVisionKeyUsed(it.key) }) {
            it.generateJson(PromptTemplates.fillDocRead(promptTemplate, kind, content), maxTokens = 4096)
        }.getOrThrow()
        val (source, text) = ExtractionParser.parseMedia(raw, fallbackSource = kind)
        if (text.isBlank()) throw RuntimeException("AI tidak menemukan isi yang bisa dibaca pada file ini.")
        MediaText(source, text)
    }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }

    private fun mask(key: String): String =
        if (key.length <= 8) "($key)" else "${key.take(6)}…${key.takeLast(2)} (${key.length} char)"

    private fun providerFor(combo: AICombo): AIProvider = when (combo.provider) {
        AIProviderType.GEMINI -> GeminiProvider(AIConfig(combo.key, combo.model))
        else -> OpenAICompatProvider(combo.provider, combo.key, combo.model)
    }

    private suspend fun <T> runFallback(
        comboList: List<AICombo> = combos,
        onComboSuccess: ((AICombo) -> Unit)? = null,
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
                onComboSuccess?.invoke(combo)
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

        // Key yang barusan dipakai membaca gambar — jangan diprioritaskan untuk
        // ekstraksi berikutnya (kuota per-menitnya dianggap sudah terpakai).
        @Volatile private var lastVisionKey: String? = null
        @Volatile private var lastVisionAt: Long = 0L

        /** Penghitung global agar pembacaan file paralel tersebar ke key berbeda. */
        private val READ_SLOT = java.util.concurrent.atomic.AtomicInteger(0)

        private fun noteVisionKeyUsed(key: String) {
            lastVisionKey = key
            lastVisionAt = System.currentTimeMillis()
        }

        /** Pindahkan key bekas baca-gambar ke urutan TERAKHIR (jendela 90 detik ≈ 1 menit TPM). */
        private fun deprioritizeVisionKey(combos: List<AICombo>): List<AICombo> {
            val k = lastVisionKey ?: return combos
            if (System.currentTimeMillis() - lastVisionAt > 90_000L) return combos
            val (used, rest) = combos.partition { it.key == k }
            return rest + used
        }

        /** Service untuk ekstraksi metadata (model ringan dulu; 3 prompt paralel). */
        fun forExtraction(prefs: PrefsManager): AIService =
            AIService(deprioritizeVisionKey(buildCombos(prefs, forAnswer = false)), ExtractionPrompts.from(prefs))

        /** Service untuk tanya-jawab (model kuat dulu). */
        fun forAnswer(prefs: PrefsManager): AIService =
            AIService(buildCombos(prefs, forAnswer = true), null)

        /** Service untuk membaca gambar/PDF. PDF: Gemini saja; gambar: Groq (Scout) + Gemini. */
        fun forVision(prefs: PrefsManager, forPdf: Boolean): AIService =
            AIService(buildVisionCombos(prefs, forPdf), null)

        /** Service untuk membaca dokumen teks (semua provider, model ringan dulu). */
        fun forReading(prefs: PrefsManager): AIService =
            AIService(buildCombos(prefs, forAnswer = false), null)

        private fun buildCombos(prefs: PrefsManager, forAnswer: Boolean): List<AICombo> =
            prefs.activeProviders().flatMap { p ->
                val models = if (forAnswer) PrefsManager.answerModels(p) else PrefsManager.extractionModels(p)
                prefs.getApiKeys(p).flatMap { k -> models.map { m -> AICombo(p, k, m) } }
            }

        private fun buildVisionCombos(prefs: PrefsManager, forPdf: Boolean): List<AICombo> {
            // Untuk vision, GEMINI DIDAHULUKAN (akurasi baca gambar jauh lebih baik daripada
            // Llama-4 Scout); Groq hanya cadangan untuk gambar.
            val active = prefs.activeProviders()
            val order = listOf(AIProviderType.GEMINI, AIProviderType.GROQ).filter { it in active }
            return order.flatMap { p ->
                val models = when {
                    p == AIProviderType.GEMINI -> PrefsManager.GEMINI_VISION_MODELS
                    p == AIProviderType.GROQ && !forPdf -> PrefsManager.GROQ_VISION_MODELS
                    else -> emptyList()
                }
                prefs.getApiKeys(p).flatMap { k -> models.map { m -> AICombo(p, k, m) } }
            }
        }
    }
}
