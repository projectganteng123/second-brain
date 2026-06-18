package com.secondbrain.app.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.secondbrain.app.ui.components.*
import com.secondbrain.app.ui.theme.*
import com.secondbrain.app.viewmodel.InputUiState
import com.secondbrain.app.viewmodel.InputViewModel

@Composable
fun InputScreen(
    vm: InputViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val isDark = isSystemDark()
    val uiState by vm.uiState.collectAsState()
    val rawText by vm.rawText.collectAsState()

    var inputMode by remember { mutableStateOf<InputMode?>(null) }
    var isListening by remember { mutableStateOf(false) }

    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) inputMode = InputMode.VOICE
    }

    LaunchedEffect(uiState) {
        if (uiState is InputUiState.Saved) {
            onSaved()
            vm.reset()
        }
        if (uiState is InputUiState.Preview) {
            // navigation handled in nav graph
        }
    }

    val bgColor = if (isDark) Lavender900 else Gray50

    Scaffold(containerColor = bgColor) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(16.dp))

            // Back + Title
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Outlined.ArrowBack, "Kembali",
                        tint = if (isDark) Lavender200 else Lavender600
                    )
                }
                Text(
                    "Catatan baru",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isDark) Lavender50 else Lavender800
                )
            }

            Spacer(Modifier.height(16.dp))

            // Mode selector (shown until mode picked or text entered)
            if (inputMode == null && rawText.isBlank()) {
                SectionLabel("pilih mode input")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassModeButton(
                        text = "Rekam suara",
                        icon = Icons.Outlined.Mic,
                        selected = inputMode == InputMode.VOICE,
                        onClick = {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        modifier = Modifier.weight(1f)
                    )
                    GlassModeButton(
                        text = "Ketik manual",
                        icon = Icons.Outlined.Keyboard,
                        selected = inputMode == InputMode.TEXT,
                        onClick = { inputMode = InputMode.TEXT },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(20.dp))
            }

            // STT active indicator
            if (inputMode == InputMode.VOICE) {
                VoiceInputSection(
                    isListening = isListening,
                    onToggle = {
                        if (isListening) {
                            isListening = false
                        } else {
                            isListening = true
                            startStt(context) { result ->
                                vm.updateText(result)
                                isListening = false
                                inputMode = InputMode.TEXT
                            }
                        }
                    },
                    onSwitchToText = { inputMode = InputMode.TEXT }
                )
                Spacer(Modifier.height(16.dp))
            }

            // Text field (shown when mode = TEXT or text already present)
            if (inputMode == InputMode.TEXT || rawText.isNotBlank()) {
                SectionLabel("teks catatan")
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = rawText,
                    onValueChange = vm::updateText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    placeholder = {
                        Text(
                            "Tulis catatan, jadwal, atau ide...",
                            color = if (isDark) Lavender400.copy(0.5f) else Gray400
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Lavender400,
                        unfocusedBorderColor = if (isDark) GlassBorderDark else Lavender200,
                        focusedContainerColor = if (isDark) GlassDark else GlassLight,
                        unfocusedContainerColor = if (isDark) GlassDark else GlassLight,
                        focusedTextColor = if (isDark) Lavender50 else Lavender800,
                        unfocusedTextColor = if (isDark) Lavender50 else Lavender800
                    )
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${rawText.length} karakter",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDark) Lavender400 else Gray400
                )
                Spacer(Modifier.height(12.dp))
            }

            // Tip
            if (rawText.isBlank()) {
                TipBox(isDark)
                Spacer(Modifier.height(12.dp))
            }

            // Process / error state
            if (rawText.isNotBlank()) {
                when (val state = uiState) {
                    is InputUiState.Error -> {
                        ErrorBox(state.message, isDark)
                        Spacer(Modifier.height(12.dp))
                    }
                    else -> {}
                }

                val isLoading = uiState is InputUiState.Extracting || uiState is InputUiState.Saving
                GlassButton(
                    text = if (isLoading) "Memproses..." else "Proses dengan AI",
                    icon = Icons.Outlined.AutoAwesome,
                    onClick = vm::processWithAI,
                    accent = true,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                )

                if (uiState is InputUiState.Error) {
                    Spacer(Modifier.height(8.dp))
                    GlassButton(
                        text = "Simpan tanpa AI (offline)",
                        onClick = vm::savePendingOffline,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun VoiceInputSection(
    isListening: Boolean,
    onToggle: () -> Unit,
    onSwitchToText: () -> Unit
) {
    val isDark = isSystemDark()
    GlassCard {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Icon(
                if (isListening) Icons.Outlined.MicOff else Icons.Outlined.Mic,
                null,
                tint = if (isListening) Peach600 else Lavender600,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (isListening) "Sedang mendengarkan..." else "Tekan untuk mulai merekam",
                style = MaterialTheme.typography.bodySmall,
                color = if (isDark) Lavender200 else Lavender600
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassButton(
                    text = if (isListening) "Berhenti" else "Rekam",
                    icon = if (isListening) Icons.Outlined.Stop else Icons.Outlined.Mic,
                    onClick = onToggle,
                    accent = isListening
                )
                GlassButton(
                    text = "Ketik manual",
                    icon = Icons.Outlined.Keyboard,
                    onClick = onSwitchToText
                )
            }
        }
    }
}

@Composable
private fun TipBox(isDark: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isDark) Sky600.copy(0.1f) else Sky50,
                RoundedCornerShape(12.dp)
            )
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Outlined.Lightbulb, null, tint = Sky600, modifier = Modifier.size(16.dp))
        Text(
            "Sebutkan waktu, orang, dan tempat secara spesifik agar AI dapat mengekstrak metadata dengan akurat.",
            style = MaterialTheme.typography.bodySmall,
            color = if (isDark) Sky200 else Sky800
        )
    }
}

@Composable
private fun ErrorBox(message: String, isDark: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isDark) Rose600.copy(0.1f) else Rose50,
                RoundedCornerShape(12.dp)
            )
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Outlined.ErrorOutline, null, tint = Rose600, modifier = Modifier.size(16.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = if (isDark) Rose200 else Rose800
        )
    }
}

enum class InputMode { VOICE, TEXT }

private fun startStt(
    context: android.content.Context,
    onResult: (String) -> Unit
) {
    val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
    }
    recognizer.setRecognitionListener(object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull() ?: ""
            onResult(text)
            recognizer.destroy()
        }
        override fun onError(error: Int) { recognizer.destroy() }
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    })
    recognizer.startListening(intent)
}
