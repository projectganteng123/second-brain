package com.secondbrain.app.capture

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Mode kata pemicu ("Jarvis") di layar Input.
 *
 * Pencocokan dilakukan pada TEKS hasil STT, bukan audio: [SttSession] di-loop selama
 * WAITING. Transkrip dibuang kecuali diawali kata pemicu; begitu terpicu, sisa kalimat
 * disisipkan (kata pemicu dibuang / diganti placeholder) lalu loop BERHENTI (Captured) —
 * menghindari bunyi "ding" beruntun dari restart sesi. Kalimat tambahan direkam lewat
 * tombol mic biasa; kata pemicu diaktifkan lagi via [startListening] ("Dengar lagi").
 *
 * Kasus "Jarvis" diucap sendirian (isi menyusul setelah jeda): satu sesi lanjutan
 * (Continuing) dijalankan untuk menangkap isinya — sekali saja, bukan loop.
 *
 * Kontrak mic serial dipertahankan: pemanggil wajib [stopListening] sebelum memakai
 * SpeechRecognizer lain (mic manual/tombol template). Semua metode dari main thread.
 */
class VoiceTriggerController(
    context: Context,
    private val triggerWord: () -> String,
    private val placeholderWord: () -> String,
    private val onInsert: (String) -> Unit
) {
    sealed interface State {
        object Off : State
        /** Menunggu kata pemicu; [triggerHeard] true saat transkrip parsial sudah cocok. */
        data class Waiting(val triggerHeard: Boolean) : State
        /** Kata pemicu terucap sendirian — satu sesi lanjutan menangkap isinya. */
        object Continuing : State
        /** Kalimat pemicu tertangkap; berhenti mendengarkan sampai diaktifkan lagi. */
        object Captured : State
        /** Recognizer gagal beruntun — loop dihentikan agar tidak berputar panas. */
        data class Unavailable(val message: String) : State
    }

    var state by mutableStateOf<State>(State.Off)
        private set

    private val session = SttSession(context)
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var fastFailStreak = 0  // sesi kosong yang berakhir <1,5 dtk = recognizer bermasalah
    private var sessionStartedAt = 0L

    fun startListening() {
        if (running) return
        running = true
        fastFailStreak = 0
        state = State.Waiting(triggerHeard = false)
        startSession()
    }

    fun stopListening() {
        running = false
        handler.removeCallbacksAndMessages(null)
        session.destroy()
        if (state != State.Off) state = State.Off
    }

    private fun startSession() {
        if (!running) return
        sessionStartedAt = SystemClock.elapsedRealtime()
        session.start(
            preferOffline = true,
            onPhase = { phase ->
                if (running && phase is SttSession.Phase.Listening) {
                    val s = state
                    if (s is State.Waiting) {
                        val heard = phase.partial.isNotBlank() &&
                            matchTrigger(phase.partial, triggerWord()) != null
                        if (heard != s.triggerHeard) state = State.Waiting(heard)
                    }
                }
            },
            onFinal = { text -> if (running) handleFinal(text) }
        )
    }

    private fun handleFinal(text: String) {
        val elapsed = SystemClock.elapsedRealtime() - sessionStartedAt
        val trimmed = text.trim()

        // Sesi kosong yang mati terlalu cepat bukan hening normal (timeout hening ±5 dtk),
        // melainkan recognizer bermasalah (tidak tersedia/izin/busy) → backoff, jangan loop panas.
        if (trimmed.isEmpty() && elapsed < 1500) {
            fastFailStreak++
            if (fastFailStreak >= 6) {
                stopListening()
                state = State.Unavailable("Pengenalan suara bermasalah — kata pemicu berhenti. Buka ulang layar ini untuk mencoba lagi.")
                return
            }
            scheduleRestart(1000L * fastFailStreak)
            return
        }
        fastFailStreak = 0

        when (val s = state) {
            is State.Waiting -> {
                val rest = if (trimmed.isEmpty()) null else matchTrigger(trimmed, triggerWord())
                when {
                    rest == null -> {
                        if (s.triggerHeard) state = State.Waiting(triggerHeard = false)
                        scheduleRestart(250)
                    }
                    rest.isBlank() -> {
                        state = State.Continuing
                        scheduleRestart(250)
                    }
                    else -> {
                        val ph = placeholderWord().trim()
                        onInsert(if (ph.isEmpty()) rest else "$ph $rest")
                        finishCaptured()
                    }
                }
            }
            State.Continuing -> {
                if (trimmed.isNotEmpty()) {
                    onInsert(trimmed)
                    finishCaptured()
                } else {
                    // Tidak ada isi setelah kata pemicu → kembali menunggu.
                    state = State.Waiting(triggerHeard = false)
                    scheduleRestart(250)
                }
            }
            else -> {}
        }
    }

    /** Kalimat tertangkap: hentikan loop dengar (tanpa ding lanjutan), tunggu "Dengar lagi". */
    private fun finishCaptured() {
        running = false
        handler.removeCallbacksAndMessages(null)
        session.destroy()
        state = State.Captured
    }

    private fun scheduleRestart(delayMs: Long) {
        handler.postDelayed({ if (running) startSession() }, delayMs)
    }

    companion object {
        /** Samakan bentuk token: huruf kecil, buang semua selain huruf/angka. */
        fun normalizeToken(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

        /** Jarak edit Levenshtein (toleransi salah transkrip kata pemicu). */
        fun levenshtein(a: String, b: String): Int {
            if (a == b) return 0
            if (a.isEmpty()) return b.length
            if (b.isEmpty()) return a.length
            var prev = IntArray(b.length + 1) { it }
            var cur = IntArray(b.length + 1)
            for (i in 1..a.length) {
                cur[0] = i
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
                }
                val t = prev; prev = cur; cur = t
            }
            return prev[b.length]
        }

        /**
         * Jika [transcript] DIAWALI [trigger] (toleran salah transkrip: "jarpis", "jar vis"),
         * kembalikan sisa kalimat setelah kata pemicu; selain itu null.
         * Pemicu di tengah kalimat sengaja tidak memicu.
         */
        fun matchTrigger(transcript: String, trigger: String): String? {
            val trigNorm = normalizeToken(trigger)
            if (trigNorm.isEmpty()) return null
            val trigTokenCount = trigger.trim().split(Regex("\\s+")).count { it.isNotBlank() }
            val words = transcript.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.isEmpty()) return null
            val tolerance = maxOf(1, (trigNorm.length * 3 + 9) / 10)   // ≈30% panjang, bulat ke atas
            // STT bisa memecah satu kata jadi dua ("jar vis") → coba n dan n+1 kata pertama.
            for (take in intArrayOf(trigTokenCount, trigTokenCount + 1)) {
                if (words.size < take) continue
                val candidate = words.take(take).joinToString("") { normalizeToken(it) }
                if (candidate.isEmpty()) continue
                if (levenshtein(candidate, trigNorm) <= tolerance) {
                    return words.drop(take).joinToString(" ")
                }
            }
            return null
        }
    }
}
