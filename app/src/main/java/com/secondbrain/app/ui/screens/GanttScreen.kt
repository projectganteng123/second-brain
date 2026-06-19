package com.secondbrain.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.secondbrain.app.data.model.*
import com.secondbrain.app.data.repository.NoteRepository
import com.secondbrain.app.ui.components.*
import com.secondbrain.app.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private const val DAYS = 14
private val LEFT_W = 124.dp
private val CELL_W = 46.dp

private data class GanttRow(
    val noteId: Long,
    val title: String,
    val type: NoteType,
    val startTime: String?,
    val dayIndices: Set<Int>
)

@Composable
fun GanttScreen(
    repo: NoteRepository,
    onBack: () -> Unit,
    onNoteClick: (Long) -> Unit
) {
    val isDark = isSystemDark()
    val notes by repo.getAllActive().collectAsState(initial = emptyList())
    val today = remember { LocalDate.now() }

    val rows = remember(notes) {
        notes.mapNotNull { note ->
            val meta = repo.metadataFrom(note) ?: return@mapNotNull null
            val idx = meta.recurrenceDates.mapNotNull { ds ->
                runCatching {
                    val d = LocalDate.parse(ds, DateTimeFormatter.ISO_LOCAL_DATE)
                    ChronoUnit.DAYS.between(today, d).toInt()
                }.getOrNull()?.takeIf { it in 0 until DAYS }
            }.toSet()
            if (idx.isEmpty()) null
            else GanttRow(
                noteId = note.id,
                title = meta.title.ifBlank { note.rawText.take(40) },
                type = meta.type,
                startTime = meta.startTime,
                dayIndices = idx
            )
        }.sortedBy { it.dayIndices.minOrNull() }
    }

    val dayScroll = rememberScrollState()
    val bgColor = if (isDark) Lavender900 else Gray50

    Scaffold(containerColor = bgColor) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, "Kembali", tint = if (isDark) Lavender200 else Lavender600)
                }
                Text("Gantt jadwal", style = MaterialTheme.typography.titleMedium,
                    color = if (isDark) Lavender50 else Lavender800)
            }
            Text(
                "$DAYS hari ke depan · geser ke samping untuk lihat tanggal lain",
                style = MaterialTheme.typography.labelSmall,
                color = if (isDark) Lavender400 else Gray600,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
            )

            // Header tanggal
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(LEFT_W))
                Row(Modifier.horizontalScroll(dayScroll)) {
                    for (i in 0 until DAYS) {
                        val d = today.plusDays(i.toLong())
                        val isToday = i == 0
                        Column(
                            modifier = Modifier.width(CELL_W),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                d.format(DateTimeFormatter.ofPattern("EEE", Locale("id"))),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isToday) Lavender600 else (if (isDark) Lavender400 else Gray400)
                            )
                            Text(
                                d.dayOfMonth.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isToday) Lavender600 else (if (isDark) Lavender200 else Lavender800)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = if (isDark) GlassBorderDark else GlassBorderLight, thickness = 0.5.dp,
                modifier = Modifier.padding(vertical = 6.dp))

            if (rows.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Tidak ada kegiatan terjadwal dalam $DAYS hari ke depan",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDark) Lavender400 else Gray400,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(rows, key = { it.noteId }) { row ->
                        GanttRowView(row, dayScroll, isDark, onNoteClick)
                    }
                    item { Spacer(Modifier.height(60.dp)) }
                }
            }
        }
    }
}

@Composable
private fun GanttRowView(
    row: GanttRow,
    dayScroll: androidx.compose.foundation.ScrollState,
    isDark: Boolean,
    onNoteClick: (Long) -> Unit
) {
    val (barBg, barFg) = typeColors(row.type)
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Kolom kiri: judul + chip tipe
        Column(
            modifier = Modifier
                .width(LEFT_W)
                .clip(RoundedCornerShape(8.dp))
                .clickable { onNoteClick(row.noteId) }
                .padding(end = 6.dp, top = 2.dp, bottom = 2.dp)
        ) {
            Text(
                row.title,
                style = MaterialTheme.typography.labelMedium,
                color = if (isDark) Lavender50 else Lavender800,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            TypeChip(row.type)
        }
        // Sel hari
        Row(Modifier.horizontalScroll(dayScroll)) {
            for (i in 0 until DAYS) {
                Box(
                    modifier = Modifier.width(CELL_W).height(34.dp).padding(horizontal = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (i in row.dayIndices) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(barBg)
                                .border(1.dp, barFg.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .clickable { onNoteClick(row.noteId) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                row.startTime ?: "•",
                                style = MaterialTheme.typography.labelSmall,
                                color = barFg,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
