package com.secondbrain.app.capture

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * State machine STT SERIAL terisolasi dari UI.
 * - Hanya satu sesi aktif. start() melepaskan mic sesi lama dulu (destroy) sebelum mulai baru,
 *   mencegah ERROR_RECOGNIZER_BUSY (error 8).
 * - Error/timeout diperlakukan sebagai "selesai, kosong" agar antrean tidak menggantung.
 * - Hasil kosong tidak menyisipkan apa pun (ditangani pemanggil).
 *
 * Semua metode dipanggil dari main thread (sesuai kontrak SpeechRecognizer).
 */
class SttSession(private val context: Context) {

    sealed interface Phase {
        /** Jeda serah-terima mic, belum siap bicara. */
        object Preparing : Phase
        /** Mic hidup; partial = transkrip parsial live. */
        data class Listening(val partial: String) : Phase
    }

    private var recognizer: SpeechRecognizer? = null
    private var onPhase: ((Phase) -> Unit)? = null
    private var onFinal: ((String) -> Unit)? = null
    private var finished = false

    fun start(preferOffline: Boolean = false, onPhase: (Phase) -> Unit, onFinal: (String) -> Unit) {
        releaseInternal()                 // lepas mic sesi sebelumnya dulu (serial)
        this.onPhase = onPhase
        this.onFinal = onFinal
        this.finished = false

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            finish("")
            return
        }

        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { onPhase(Phase.Listening("")) }
            override fun onPartialResults(partialResults: Bundle?) {
                val t = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                onPhase(Phase.Listening(t))
            }
            override fun onResults(results: Bundle?) {
                val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                finish(t)
            }
            override fun onError(error: Int) { finish("") }       // error/timeout = kosong
            override fun onBeginningOfSpeech() {}
            override fun onEndOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        onPhase(Phase.Preparing)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Mode kata pemicu mendengar terus — jangan alirkan audio ke cloud bila bisa on-device.
            if (preferOffline) putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        runCatching { r.startListening(intent) }.onFailure { finish("") }
    }

    /** Selesai normal: minta hasil final (akan memicu onResults). */
    fun stop() { runCatching { recognizer?.stopListening() } }

    /** Batal: buang sesi, hasil dianggap kosong. */
    fun cancel() { finish("") }

    fun destroy() { releaseInternal() }

    private fun finish(text: String) {
        if (finished) return
        finished = true
        val cb = onFinal
        releaseInternal()
        cb?.invoke(text)
    }

    private fun releaseInternal() {
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
    }
}
