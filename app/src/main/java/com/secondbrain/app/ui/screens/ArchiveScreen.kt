package com.secondbrain.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.secondbrain.app.data.model.*
import com.secondbrain.app.data.repository.NoteRepository
import com.secondbrain.app.ui.components.*
import com.secondbrain.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ArchiveScreen(
    repo: NoteRepository,
    onBack: () -> Unit,
    onNoteClick: (Long) -> Unit
) {
    val isDark = isSystemDark()
    val notes by repo.getArchived().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    val bgColor = if (isDark) Lavender900 else Gray50

    Scaffold(containerColor = bgColor) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, "Kembali", tint = if (isDark) Lavender200 else Lavender600)
                }
                Text(
                    "Arsip",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isDark) Lavender50 else Lavender800
                )
            }

            Spacer(Modifier.height(8.dp))
            SectionLabel("${notes.size} catatan diarsipkan")

            if (notes.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Belum ada catatan diarsipkan",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDark) Lavender400 else Gray400,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(notes, key = { it.id }) { note ->
                        val meta = repo.metadataFrom(note)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) {
                                NoteCard(
                                    title = meta?.title?.ifBlank { note.rawText.take(60) } ?: note.rawText.take(60),
                                    type = meta?.type ?: NoteType.NOTE,
                                    timeRange = meta?.recurrenceDates?.firstOrNull(),
                                    prioritas = note.prioritas?.let { runCatching { Priority.valueOf(it) }.getOrNull() },
                                    status = note.status?.let { runCatching { NoteStatus.valueOf(it) }.getOrNull() },
                                    onClick = { onNoteClick(note.id) }
                                )
                            }
                            IconButton(onClick = { scope.launch { repo.setArchived(note.id, false) } }) {
                                Icon(Icons.Outlined.Unarchive, "Kembalikan", tint = Mint600)
                            }
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}
