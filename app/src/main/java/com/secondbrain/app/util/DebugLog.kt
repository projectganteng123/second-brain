package com.secondbrain.app.util

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DebugEntry(
    val time: String,
    val tag: String,
    val message: String
)

/**
 * Buffer log dalam-memori untuk inspeksi via layar Debug di app (berguna saat tes via APK
 * tanpa akses Logcat). Juga menulis ke Logcat (`SecondBrain`).
 */
object DebugLog {
    private const val MAX = 200
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val _entries = MutableStateFlow<List<DebugEntry>>(emptyList())
    val entries: StateFlow<List<DebugEntry>> = _entries

    fun log(tag: String, message: String) {
        Log.d("SecondBrain", "[$tag] $message")
        val last = _entries.value.lastOrNull()
        // Abaikan duplikat berurutan (mis. query dashboard yang re-emit berkali-kali)
        if (last != null && last.tag == tag && last.message == message) return
        val entry = DebugEntry(timeFmt.format(Date()), tag, message)
        _entries.value = (_entries.value + entry).takeLast(MAX)
    }

    fun clear() { _entries.value = emptyList() }
}
