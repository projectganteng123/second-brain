package com.secondbrain.app.capture

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Mode kata pemicu ("Jarvis") di layar Input — TANPA loop.
 *
 * Satu kali diaktifkan = SATU sesi dengar (menghindari bunyi "bip" berulang dari
 * restart sesi). Pencocokan pada TEKS hasil STT:
 * - Diawali kata pemicu → sisa kalimat disisipkan (pemicu dibuang/diganti placeholder) → Captured.
 * - Kata pemicu diucap sendirian → SATU sesi lanjutan (Continuing) menangkap isinya.
 * - Tidak ada kata pemicu / hening → BERHENTI (Stopped) — aktifkan lagi via [startListening].
 *
 * Kontrak mic serial dipertahankan: pemanggil wajib [stopListening] sebelum memakai
 * SpeechRecognizer lain. Semua metode dari main thread.
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
        /** Sesi berakhir tanpa kata pemicu — tidak diulang (hindari bip beruntun). */
        object Stopped : State
    }

    var state by mutableStateOf<State>(State.Off)
        private set

    private val session = SttSession(context)
    private var running = false

    fun startListening() {
        if (running) return
        running = true
        state = State.Waiting(triggerHeard = false)
        startSession()
    }

    fun stopListening() {
        running = false
        session.destroy()
        if (state != State.Off) state = State.Off
    }

    private fun startSession() {
        if (!running) return
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
        val trimmed = text.trim()
        when (state) {
            is State.Waiting -> {
                val rest = if (trimmed.isEmpty()) null else matchTrigger(trimmed, triggerWord())
                when {
                    rest == null -> finish(State.Stopped)      // tidak ada pemicu → berhenti, TANPA ulang
                    rest.isBlank() -> {
                        state = State.Continuing               // "Jarvis" saja → satu sesi lanjutan
                        startSession()
                    }
                    else -> {
                        val ph = placeholderWord().trim()
                        onInsert(if (ph.isEmpty()) rest else "$ph $rest")
                        finish(State.Captured)
                    }
                }
            }
            State.Continuing -> {
                if (trimmed.isNotEmpty()) {
                    onInsert(trimmed)
                    finish(State.Captured)
                } else finish(State.Stopped)
            }
            else -> {}
        }
    }

    /** Hentikan mendengarkan dan tampilkan state akhir (Captured/Stopped). */
    private fun finish(endState: State) {
        running = false
        session.destroy()
        state = endState
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
