package com.secondbrain.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.secondbrain.app.data.model.*
import com.secondbrain.app.data.repository.NoteRepository
import com.secondbrain.app.ui.components.*
import com.secondbrain.app.ui.theme.*

private enum class NoteFilter(val label: String) {
    SEMUA("Semua"),
    TERJADWAL("Terjadwal"),
    TANPA_TANGGAL("Tanpa tanggal")
}

@Composable
fun AllNotesScreen(
    repo: NoteRepository,
    onBack: () -> Unit,
    onNoteClick: (Long) -> Unit
) {
    val isDark = isSystemDark()
    val notes by repo.getAllActive().collectAsState(initial = emptyList())
    var filter by remember { mutableStateOf(NoteFilter.SEMUA) }
    var typeFilter by remember { mutableStateOf<NoteType?>(null) }

    val shown = remember(notes, filter, typeFilter) {
        notes.filter { note ->
            val meta = repo.metadataFrom(note)
            val hasDate = meta?.recurrenceDates?.isNotEmpty() == true
            val dateOk = when (filter) {
                NoteFilter.SEMUA -> true
                NoteFilter.TERJADWAL -> hasDate
                NoteFilter.TANPA_TANGGAL -> !hasDate
            }
            val typeOk = typeFilter == null || meta?.type == typeFilter
            dateOk && typeOk
        }
    }

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
                    "Semua catatan",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isDark) Lavender50 else Lavender800
                )
            }

            Spacer(Modifier.height(10.dp))

            // Filter chips
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NoteFilter.entries.forEach { f ->
                    val selected = f == filter
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (selected) (if (isDark) Lavender600.copy(0.4f) else Lavender100)
                                else (if (isDark) GlassDark else GlassLight)
                            )
                            .clickable { filter = f }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(
                            f.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) (if (isDark) Lavender200 else Lavender600)
                                    else (if (isDark) Lavender400 else Gray600)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Filter jenis
            TypeFilterRow(typeFilter, isDark) { typeFilter = it }

            Spacer(Modifier.height(8.dp))
            SectionLabel("${shown.size} catatan")

            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Belum ada catatan di kategori ini",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDark) Lavender400 else Gray400,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(shown, key = { it.id }) { note ->
                        val meta = repo.metadataFrom(note)
                        NoteCard(
                            title = meta?.title?.ifBlank { note.rawText.take(60) } ?: note.rawText.take(60),
                            type = meta?.type ?: NoteType.NOTE,
                            timeRange = meta?.recurrenceDates?.firstOrNull(),
                            prioritas = note.prioritas?.let { runCatching { Priority.valueOf(it) }.getOrNull() },
                            status = note.status?.let { runCatching { NoteStatus.valueOf(it) }.getOrNull() },
                            onClick = { onNoteClick(note.id) }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
private fun TypeFilterRow(selected: NoteType?, isDark: Boolean, onSelect: (NoteType?) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Chip("Semua", selected == null, isDark) { onSelect(null) }
        NoteType.entries.forEach { t ->
            Chip(t.label, selected == t, isDark) { onSelect(if (selected == t) null else t) }
        }
    }
}

@Composable
private fun Chip(label: String, sel: Boolean, isDark: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (sel) (if (isDark) Lavender600.copy(0.4f) else Lavender100)
                else (if (isDark) GlassDark else GlassLight)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (sel) (if (isDark) Lavender200 else Lavender600)
                    else (if (isDark) Lavender400 else Gray600)
        )
    }
}
