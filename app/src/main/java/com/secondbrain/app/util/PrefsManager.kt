package com.secondbrain.app.util

import android.content.Context
import androidx.core.content.edit
import com.secondbrain.app.ai.AIProviderType
import com.secondbrain.app.ai.ExtractionKind

class PrefsManager(context: Context) {
    private val prefs = context.getSharedPreferences("secondbrain_prefs", Context.MODE_PRIVATE)

    // ---- API key per provider (boleh banyak key, satu per baris) ----

    fun saveApiKeyText(provider: AIProviderType, text: String) =
        prefs.edit { putString(apiPrefKey(provider), text) }

    fun getApiKeyText(provider: AIProviderType): String =
        prefs.getString(apiPrefKey(provider), "") ?: ""

    /** Daftar API key sebuah provider. Pisah berdasarkan whitespace apa pun (baris/spasi/\r/tab)
     *  agar karakter tak terlihat tidak ikut terkirim ke server. */
    fun getApiKeys(provider: AIProviderType): List<String> =
        getApiKeyText(provider).split(Regex("\\s+")).map { it.trim() }.filter { it.isNotBlank() }

    fun setProviderEnabled(provider: AIProviderType, enabled: Boolean) =
        prefs.edit { putBoolean(enabledPrefKey(provider), enabled) }

    fun isProviderEnabled(provider: AIProviderType): Boolean =
        prefs.getBoolean(enabledPrefKey(provider), true)

    /** Provider yang dicentang DAN punya key, urut prioritas: Groq → Cerebras → Gemini. */
    fun activeProviders(): List<AIProviderType> =
        PROVIDER_PRIORITY.filter { isProviderEnabled(it) && getApiKeys(it).isNotEmpty() }

    fun hasAnyActiveApiKey(): Boolean = activeProviders().isNotEmpty()

    private fun apiPrefKey(provider: AIProviderType): String = when (provider) {
        // Nama lama dipertahankan agar key Gemini yang sudah tersimpan tidak hilang saat update.
        AIProviderType.GEMINI -> "gemini_api_key"
        AIProviderType.GROQ -> "groq_api_key"
        AIProviderType.CEREBRAS -> "cerebras_api_key"
    }

    private fun enabledPrefKey(provider: AIProviderType): String =
        "provider_enabled_" + provider.name.lowercase()

    // ---- Lainnya ----

    // ---- Alarm acara ----

    /** Toggle alarm di layar konfirmasi (Preview) menyala secara default atau tidak. */
    fun setEventAlarmDefaultOn(on: Boolean) = prefs.edit { putBoolean(KEY_EVENT_ALARM_DEFAULT, on) }
    fun isEventAlarmDefaultOn(): Boolean = prefs.getBoolean(KEY_EVENT_ALARM_DEFAULT, false)

    /** Alarm berbunyi X menit sebelum acara (0 = tepat saat mulai). */
    fun saveAlarmOffsetMinutes(minutes: Int) = prefs.edit { putInt(KEY_ALARM_OFFSET_MIN, minutes) }
    fun getAlarmOffsetMinutes(): Int = prefs.getInt(KEY_ALARM_OFFSET_MIN, 15)

    // ---- Rentang waktu halaman (Keuangan/Acara) — bertahan walau keluar halaman/app ----

    fun saveTimeRange(page: String, preset: String, from: String, to: String) = prefs.edit {
        putString("range_${page}_preset", preset)
        putString("range_${page}_from", from)
        putString("range_${page}_to", to)
    }
    fun getTimeRangePreset(page: String): String? = prefs.getString("range_${page}_preset", null)
    fun getTimeRangeFrom(page: String): String? = prefs.getString("range_${page}_from", null)
    fun getTimeRangeTo(page: String): String? = prefs.getString("range_${page}_to", null)

    /** Template prompt ekstraksi kustom per jenis (Universal/Keuangan/Acara). Kosong = default. */
    fun saveExtractionPrompt(kind: ExtractionKind, template: String) =
        prefs.edit { putString(promptKey(kind), template) }
    fun getExtractionPrompt(kind: ExtractionKind): String =
        prefs.getString(promptKey(kind), "") ?: ""
    fun clearExtractionPrompt(kind: ExtractionKind) = prefs.edit { remove(promptKey(kind)) }
    private fun promptKey(kind: ExtractionKind): String = "custom_prompt_" + kind.name.lowercase()

    /** Template capture yang aktif saat layar input dibuka (null = tidak ada). */
    fun saveDefaultTemplateId(id: String?) = prefs.edit {
        if (id == null) remove(KEY_DEFAULT_TEMPLATE) else putString(KEY_DEFAULT_TEMPLATE, id)
    }
    fun getDefaultTemplateId(): String? = prefs.getString(KEY_DEFAULT_TEMPLATE, null)

    // ---- Kata pemicu suara (voice trigger) di layar input ----

    fun setVoiceTriggerEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_VOICE_TRIGGER_ENABLED, enabled) }
    fun isVoiceTriggerEnabled(): Boolean = prefs.getBoolean(KEY_VOICE_TRIGGER_ENABLED, false)

    fun saveVoiceTriggerWord(word: String) = prefs.edit { putString(KEY_VOICE_TRIGGER_WORD, word) }
    fun getVoiceTriggerWord(): String =
        prefs.getString(KEY_VOICE_TRIGGER_WORD, DEFAULT_VOICE_TRIGGER_WORD)
            ?.ifBlank { DEFAULT_VOICE_TRIGGER_WORD } ?: DEFAULT_VOICE_TRIGGER_WORD

    /** Teks pengganti kata pemicu di catatan. Kosong = kata pemicu dibuang. */
    fun saveVoiceTriggerPlaceholder(text: String) = prefs.edit { putString(KEY_VOICE_TRIGGER_PLACEHOLDER, text) }
    fun getVoiceTriggerPlaceholder(): String = prefs.getString(KEY_VOICE_TRIGGER_PLACEHOLDER, "") ?: ""

    companion object {
        private const val KEY_EVENT_ALARM_DEFAULT = "event_alarm_default"
        private const val KEY_ALARM_OFFSET_MIN = "alarm_offset_minutes"
        private const val KEY_DEFAULT_TEMPLATE = "default_template_id"
        private const val KEY_VOICE_TRIGGER_ENABLED = "voice_trigger_enabled"
        private const val KEY_VOICE_TRIGGER_WORD = "voice_trigger_word"
        private const val KEY_VOICE_TRIGGER_PLACEHOLDER = "voice_trigger_placeholder"
        const val DEFAULT_VOICE_TRIGGER_WORD = "Jarvis"

        /** Urutan pemakaian bila lebih dari satu provider dicentang. */
        val PROVIDER_PRIORITY = listOf(
            AIProviderType.GROQ,
            AIProviderType.CEREBRAS,
            AIProviderType.GEMINI
        )

        /** Tangga model GRATIS per provider, urut dari paling ringan ke paling kuat. */
        val GROQ_MODEL_LADDER = listOf(
            "llama-3.1-8b-instant",
            "llama-3.3-70b-versatile"
        )
        val CEREBRAS_MODEL_LADDER = listOf(
            "llama3.1-8b",
            "llama-3.3-70b"
        )
        val GEMINI_MODEL_LADDER = listOf(
            "gemini-2.5-flash-lite",
            "gemini-2.5-flash",
            "gemini-flash-latest"
        )

        /** Model bervision (baca gambar) — yang kuat dulu demi akurasi. PDF hanya Gemini. */
        val GROQ_VISION_MODELS = listOf("meta-llama/llama-4-scout-17b-16e-instruct")
        val GEMINI_VISION_MODELS = listOf("gemini-2.5-flash", "gemini-2.5-flash-lite")

        fun modelLadder(provider: AIProviderType): List<String> = when (provider) {
            AIProviderType.GROQ -> GROQ_MODEL_LADDER
            AIProviderType.CEREBRAS -> CEREBRAS_MODEL_LADDER
            AIProviderType.GEMINI -> GEMINI_MODEL_LADDER
        }

        /** Ekstraksi: mulai dari model TERKUAT demi akurasi (hasil fiktif dari model kecil
         *  lebih mahal daripada kuota); turun ke model ringan bila kena limit. */
        fun extractionModels(provider: AIProviderType): List<String> = modelLadder(provider).reversed()

        /** Menjawab: mulai dari model kuat, turun bila limit. */
        fun answerModels(provider: AIProviderType): List<String> = modelLadder(provider).reversed()

        /** Pilihan offset alarm sebelum acara (menit); 0 = tepat saat mulai. */
        val ALARM_OFFSET_OPTIONS = listOf(60, 45, 30, 15, 10, 5, 0)
    }
}
