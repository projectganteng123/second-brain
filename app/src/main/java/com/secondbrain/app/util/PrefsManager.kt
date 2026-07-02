package com.secondbrain.app.util

import android.content.Context
import androidx.core.content.edit
import com.secondbrain.app.ai.AIProviderType

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

    fun saveReminderOffsetHours(hours: Int) = prefs.edit { putInt(KEY_REMINDER_OFFSET, hours) }
    fun getReminderOffsetHours(): Int = prefs.getInt(KEY_REMINDER_OFFSET, 24)

    /** Template prompt ekstraksi kustom. Kosong = pakai default. */
    fun saveCustomPrompt(template: String) = prefs.edit { putString(KEY_CUSTOM_PROMPT, template) }
    fun getCustomPrompt(): String = prefs.getString(KEY_CUSTOM_PROMPT, "") ?: ""
    fun clearCustomPrompt() = prefs.edit { remove(KEY_CUSTOM_PROMPT) }

    /** Template capture yang aktif saat layar input dibuka (null = tidak ada). */
    fun saveDefaultTemplateId(id: String?) = prefs.edit {
        if (id == null) remove(KEY_DEFAULT_TEMPLATE) else putString(KEY_DEFAULT_TEMPLATE, id)
    }
    fun getDefaultTemplateId(): String? = prefs.getString(KEY_DEFAULT_TEMPLATE, null)

    companion object {
        private const val KEY_REMINDER_OFFSET = "reminder_offset_hours"
        private const val KEY_CUSTOM_PROMPT = "custom_extraction_prompt"
        private const val KEY_DEFAULT_TEMPLATE = "default_template_id"

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

        fun modelLadder(provider: AIProviderType): List<String> = when (provider) {
            AIProviderType.GROQ -> GROQ_MODEL_LADDER
            AIProviderType.CEREBRAS -> CEREBRAS_MODEL_LADDER
            AIProviderType.GEMINI -> GEMINI_MODEL_LADDER
        }

        /** Ekstraksi: mulai dari model ringan, naik bila limit. */
        fun extractionModels(provider: AIProviderType): List<String> = modelLadder(provider)

        /** Menjawab: mulai dari model kuat, turun bila limit. */
        fun answerModels(provider: AIProviderType): List<String> = modelLadder(provider).reversed()

        val REMINDER_OFFSET_OPTIONS = listOf(1, 3, 12, 24, 48)
    }
}
