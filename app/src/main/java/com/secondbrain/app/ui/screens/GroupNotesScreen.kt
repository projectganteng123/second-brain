package com.secondbrain.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.secondbrain.app.data.model.*
import com.secondbrain.app.data.repository.NoteRepository
import com.secondbrain.app.ui.components.NoteCard
import com.secondbrain.app.ui.components.SectionLabel
import com.secondbrain.app.ui.components.isSystemDark
import com.secondbrain.app.ui.theme.*

/** Daftar catatan di dalam satu grup. */
@Composable
fun GroupNotesScreen(
    repo: NoteRepository,
    groupId: Long,
    onBack: () -> Unit,
    onNoteClick: (Long) -> Unit
) {
    val isDark = isSystemDark()
    var group by remember { mutableStateOf<GroupEntity?>(null) }
    LaunchedEffect(groupId) { group = repo.getGroup(groupId) }
    val notes by repo.notesInGroup(groupId).collectAsState(initial = emptyList())

    Scaffold(containerColor = if (isDark) Lavender900 else Gray50) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, "Kembali",
                        tint = if (isDark) Lavender200 else Lavender600)
                }
                Text(group?.name ?: "Grup", style = MaterialTheme.typography.titleMedium,
                    color = if (isDark) Lavender50 else Lavender800)
            }
            Spacer(Modifier.height(8.dp))
            SectionLabel("${notes.size} catatan")
            Spacer(Modifier.height(4.dp))

            if (notes.isEmpty()) {
                Text("Belum ada catatan di grup ini.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) Lavender400 else Gray600)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(notes, key = { it.id }) { note ->
                    val meta = repo.metadataFrom(note)
                    NoteCard(
                        title = meta?.title?.ifBlank { note.rawText.take(60) } ?: note.rawText.take(60),
                        type = meta?.type ?: NoteType.NOTE,
                        timeRange = buildString {
                            meta?.startTime?.let { append(it) }
                            meta?.endTime?.let { append(" – $it") }
                        }.ifBlank { null },
                        prioritas = note.prioritas?.let { runCatching { Priority.valueOf(it) }.getOrNull() },
                        status = note.status?.let { runCatching { NoteStatus.valueOf(it) }.getOrNull() },
                        onClick = { onNoteClick(note.id) }
                    )
                }
            }
        }
    }
}
