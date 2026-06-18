package com.secondbrain.app.util

import android.content.Context
import androidx.core.content.edit

class PrefsManager(context: Context) {
    private val prefs = context.getSharedPreferences("secondbrain_prefs", Context.MODE_PRIVATE)

    fun saveApiKey(key: String) = prefs.edit { putString(KEY_API, key) }
    fun getApiKey(): String = prefs.getString(KEY_API, "") ?: ""

    /** Daftar API key (disimpan satu per baris). */
    fun getApiKeys(): List<String> =
        getApiKey().split("\n").map { it.trim() }.filter { it.isNotBlank() }

    fun saveReminderOffsetHours(hours: Int) = prefs.edit { putInt(KEY_REMINDER_OFFSET, hours) }
    fun getReminderOffsetHours(): Int = prefs.getInt(KEY_REMINDER_OFFSET, 24)

    /** Template prompt ekstraksi kustom. Kosong = pakai default. */
    fun saveCustomPrompt(template: String) = prefs.edit { putString(KEY_CUSTOM_PROMPT, template) }
    fun getCustomPrompt(): String = prefs.getString(KEY_CUSTOM_PROMPT, "") ?: ""
    fun clearCustomPrompt() = prefs.edit { remove(KEY_CUSTOM_PROMPT) }

    /** Model Gemini yang dipakai. */
    fun saveModel(model: String) = prefs.edit { putString(KEY_MODEL, model) }
    fun getModel(): String = prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL

    companion object {
        private const val KEY_API = "gemini_api_key"
        private const val KEY_REMINDER_OFFSET = "reminder_offset_hours"
        private const val KEY_CUSTOM_PROMPT = "custom_extraction_prompt"
        private const val KEY_MODEL = "gemini_model"

        const val DEFAULT_MODEL = "gemini-2.5-flash"
        val MODEL_OPTIONS = listOf(
            "gemini-2.5-flash",
            "gemini-2.5-flash-lite",
            "gemini-flash-latest",
            "gemini-2.5-pro"
        )
        val REMINDER_OFFSET_OPTIONS = listOf(1, 3, 12, 24, 48)
    }
}
