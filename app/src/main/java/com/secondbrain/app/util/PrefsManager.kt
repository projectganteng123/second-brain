package com.secondbrain.app.util

import android.content.Context
import androidx.core.content.edit

class PrefsManager(context: Context) {
    private val prefs = context.getSharedPreferences("secondbrain_prefs", Context.MODE_PRIVATE)

    fun saveApiKey(key: String) = prefs.edit { putString(KEY_API, key) }
    fun getApiKey(): String = prefs.getString(KEY_API, "") ?: ""

    /** Daftar API key. Pisah berdasarkan whitespace apa pun (baris/spasi/\r/tab) agar
     *  karakter tak terlihat tidak ikut terkirim ke URL. */
    fun getApiKeys(): List<String> =
        getApiKey().split(Regex("\\s+")).map { it.trim() }.filter { it.isNotBlank() }

    fun saveReminderOffsetHours(hours: Int) = prefs.edit { putInt(KEY_REMINDER_OFFSET, hours) }
    fun getReminderOffsetHours(): Int = prefs.getInt(KEY_REMINDER_OFFSET, 24)

    /** Template prompt ekstraksi kustom. Kosong = pakai default. */
    fun saveCustomPrompt(template: String) = prefs.edit { putString(KEY_CUSTOM_PROMPT, template) }
    fun getCustomPrompt(): String = prefs.getString(KEY_CUSTOM_PROMPT, "") ?: ""
    fun clearCustomPrompt() = prefs.edit { remove(KEY_CUSTOM_PROMPT) }

    companion object {
        private const val KEY_API = "gemini_api_key"
        private const val KEY_REMINDER_OFFSET = "reminder_offset_hours"
        private const val KEY_CUSTOM_PROMPT = "custom_extraction_prompt"

        /** Tangga model GRATIS, urut dari paling ringan ke paling kuat. Pro tidak dimasukkan (berbayar). */
        val MODEL_LADDER = listOf(
            "gemini-2.5-flash-lite",
            "gemini-2.5-flash",
            "gemini-flash-latest"
        )

        /** Ekstraksi: mulai dari model ringan, naik bila limit. */
        val EXTRACTION_MODELS = MODEL_LADDER

        /** Menjawab: mulai dari model kuat, turun bila limit. */
        val ANSWER_MODELS = MODEL_LADDER.reversed()

        val REMINDER_OFFSET_OPTIONS = listOf(1, 3, 12, 24, 48)
    }
}
