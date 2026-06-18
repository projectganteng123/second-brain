package com.secondbrain.app.util

import android.content.Context
import androidx.core.content.edit

class PrefsManager(context: Context) {
    private val prefs = context.getSharedPreferences("secondbrain_prefs", Context.MODE_PRIVATE)

    fun saveApiKey(key: String) = prefs.edit { putString(KEY_API, key) }
    fun getApiKey(): String = prefs.getString(KEY_API, "") ?: ""

    fun saveReminderOffsetHours(hours: Int) = prefs.edit { putInt(KEY_REMINDER_OFFSET, hours) }
    fun getReminderOffsetHours(): Int = prefs.getInt(KEY_REMINDER_OFFSET, 24)

    companion object {
        private const val KEY_API = "gemini_api_key"
        private const val KEY_REMINDER_OFFSET = "reminder_offset_hours"
    }
}
