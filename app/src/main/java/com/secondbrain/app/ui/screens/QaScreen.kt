package com.secondbrain.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.secondbrain.app.ui.components.*
import com.secondbrain.app.ui.theme.*
import com.secondbrain.app.viewmodel.QaSource
import com.secondbrain.app.viewmodel.QaState
import com.secondbrain.app.viewmodel.QaViewModel

@Composable
fun QaScreen(
    vm: QaViewModel,
    onBack: () -> Unit,
    onSourceClick: (Long) -> Unit,
    onOpenSettings: () -> Unit
) {
    val isDark = isSystemDark()
    val state by vm.state.collectAsState()
    val question by vm.question.collectAsState()

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
                    "Tanya AI",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isDark) Lavender50 else Lavender800
                )
            }

            Spacer(Modifier.height(12.dp))

            // Question input
            OutlinedTextField(
                value = question,
                onValueChange = vm::updateQuestion,
                modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp),
                placeholder = {
                    Text(
                        "Tanyakan apa saja — mis. \"Apa jadwal saya minggu ini?\"",
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

            Spacer(Modifier.height(10.dp))

            val thinking = state is QaState.Thinking
            GlassButton(
                text = if (thinking) "Mencari jawaban..." else "Tanya",
                icon = Icons.Outlined.Send,
                onClick = vm::ask,
                accent = true,
                enabled = !thinking && question.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            when (val s = state) {
                is QaState.Idle -> SuggestedQuestions(vm)
                is QaState.Thinking -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = Lavender600, strokeWidth = 2.dp)
                        Text("Menelusuri catatanmu...", style = MaterialTheme.typography.bodySmall,
                            color = if (isDark) Lavender400 else Gray600)
                    }
                }
                is QaState.Answer -> AnswerCard(s.text, s.sources, onSourceClick)
                is QaState.NoResults -> {
                    InfoBox(
                        icon = Icons.Outlined.SearchOff,
                        title = "Tidak ada catatan relevan",
                        body = "Coba ubah kata kunci, atau cek manual catatanmu — mungkin metadata-nya perlu diperbaiki.",
                        color = Lemon600,
                        bg = if (isDark) Lemon600.copy(0.1f) else Lemon50,
                        textColor = if (isDark) Lemon200 else Lemon800
                    )
                }
                is QaState.Error -> {
                    InfoBox(
                        icon = Icons.Outlined.ErrorOutline,
                        title = "Terjadi kesalahan",
                        body = s.message,
                        color = Rose600,
                        bg = if (isDark) Rose600.copy(0.1f) else Rose50,
                        textColor = if (isDark) Rose200 else Rose800,
                        action = if (s.message.contains("API key")) "Buka Pengaturan" else null,
                        onAction = onOpenSettings
                    )
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SuggestedQuestions(vm: QaViewModel) {
    val isDark = isSystemDark()
    val suggestions = listOf(
        "Apa jadwal saya hari ini?",
        "Apa yang harus saya siapkan minggu ini?",
        "Siapa saja yang akan saya temui?",
        "Tugas apa yang belum selesai?"
    )
    Column {
        SectionLabel("contoh pertanyaan")
        Spacer(Modifier.height(6.dp))
        suggestions.forEach { q ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isDark) GlassDark else GlassLight)
                    .border(1.dp, if (isDark) GlassBorderDark else GlassBorderLight, RoundedCornerShape(10.dp))
                    .clickable { vm.updateQuestion(q); vm.ask() }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Outlined.ChatBubbleOutline, null, modifier = Modifier.size(15.dp),
                    tint = if (isDark) Lavender400 else Lavender600)
                Text(q, style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) Lavender200 else Lavender800)
            }
        }
    }
}

@Composable
private fun AnswerCard(text: String, sources: List<QaSource>, onSourceClick: (Long) -> Unit) {
    val isDark = isSystemDark()
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Outlined.AutoAwesome, null, modifier = Modifier.size(16.dp), tint = Lavender600)
            Text("Jawaban", style = MaterialTheme.typography.labelMedium,
                color = if (isDark) Lavender200 else Lavender600)
        }
        Spacer(Modifier.height(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium,
            color = if (isDark) Lavender50 else Lavender800)

        if (sources.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = if (isDark) GlassBorderDark else GlassBorderLight, thickness = 0.5.dp)
            Spacer(Modifier.height(8.dp))
            Text("Sumber catatan", style = MaterialTheme.typography.labelSmall,
                color = if (isDark) Lavender400 else Gray400)
            Spacer(Modifier.height(6.dp))
            sources.forEach { src ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSourceClick(src.id) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Outlined.Description, null, modifier = Modifier.size(14.dp),
                        tint = if (isDark) Lavender400 else Lavender600)
                    Text(src.title, style = MaterialTheme.typography.bodySmall,
                        color = if (isDark) Sky200 else Sky600)
                }
            }
        }
    }
}

@Composable
private fun InfoBox(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    color: androidx.compose.ui.graphics.Color,
    bg: androidx.compose.ui.graphics.Color,
    textColor: androidx.compose.ui.graphics.Color,
    action: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, modifier = Modifier.size(18.dp), tint = color)
            Text(title, style = MaterialTheme.typography.labelMedium, color = textColor)
        }
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodySmall, color = textColor)
        if (action != null && onAction != null) {
            Spacer(Modifier.height(10.dp))
            GlassButton(text = action, onClick = onAction)
        }
    }
}
