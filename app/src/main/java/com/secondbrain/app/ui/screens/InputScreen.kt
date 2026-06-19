package com.secondbrain.app.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
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

    var isListening by remember { mutableStateOf(false) }
    var hasMicPermission by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        hasMicPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) {
            isListening = true
            startStt(context) { result ->
                vm.appendText(result)   // tambahkan, jangan timpa
                isListening = false
            }
        }
    }

    fun toggleListening() {
        if (isListening) {
            isListening = false
            return
        }
        if (hasMicPermission) {
            isListening = true
            startStt(context) { result ->
                vm.appendText(result)
                isListening = false
            }
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(uiState) {
        if (uiState is InputUiState.Saved) {
            onSaved()
            vm.reset()
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

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, "Kembali", tint = if (isDark) Lavender200 else Lavender600)
                }
                Text(
                    "Catatan baru",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isDark) Lavender50 else Lavender800
                )
            }

            Spacer(Modifier.height(20.dp))

            // ----- Tombol mic besar (mode utama) -----
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val micBg = when {
                    isListening -> Peach600
                    isDark -> Lavender600.copy(0.35f)
                    else -> Lavender100
                }
                val micTint = when {
                    isListening -> androidx.compose.ui.graphics.Color.White
                    isDark -> Lavender200
                    else -> Lavender600
                }
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(micBg)
                        .border(1.dp, if (isDark) GlassBorderDark else GlassBorderLight, CircleShape)
                        .clickable { toggleListening() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isListening) Icons.Outlined.Stop else Icons.Outlined.Mic,
                        contentDescription = if (isListening) "Berhenti" else "Rekam suara",
                        tint = micTint,
                        modifier = Modifier.size(40.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (isListening) "Sedang mendengarkan..." else "Ketuk untuk merekam suara",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) Lavender200 else Lavender600
                )
                Text(
                    "Hasil suara ditambahkan ke teks di bawah",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDark) Lavender400 else Gray400,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(18.dp))

            // ----- Teks catatan (selalu ada, bisa diketik manual) -----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionLabel("teks catatan")
                // mic kecil selalu tersedia walau sedang mengetik
                Icon(
                    if (isListening) Icons.Outlined.Stop else Icons.Outlined.Mic,
                    contentDescription = "Tambah suara",
                    tint = if (isListening) Peach600 else (if (isDark) Lavender400 else Lavender600),
                    modifier = Modifier.size(20.dp).clickable { toggleListening() }
                )
            }
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = rawText,
                onValueChange = vm::updateText,
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                placeholder = {
                    Text(
                        "Rekam suara di atas, atau ketik langsung di sini...",
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

            if (rawText.isBlank()) {
                TipBox(isDark)
            } else {
                if (uiState is InputUiState.Error) {
                    ErrorBox((uiState as InputUiState.Error).message, isDark)
                    Spacer(Modifier.height(8.dp))
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
private fun TipBox(isDark: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isDark) Sky600.copy(0.1f) else Sky50, RoundedCornerShape(12.dp))
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
            .background(if (isDark) Rose600.copy(0.1f) else Rose50, RoundedCornerShape(12.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Outlined.ErrorOutline, null, tint = Rose600, modifier = Modifier.size(16.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = if (isDark) Rose200 else Rose800)
    }
}

private fun startStt(
    context: android.content.Context,
    onResult: (String) -> Unit
) {
    if (!SpeechRecognizer.isRecognitionAvailable(context)) {
        onResult("")
        return
    }
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
        override fun onError(error: Int) { onResult(""); recognizer.destroy() }
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
