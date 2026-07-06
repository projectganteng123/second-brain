package com.secondbrain.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.secondbrain.app.data.model.*
import com.secondbrain.app.data.repository.NoteRepository
import com.secondbrain.app.ui.components.*
import com.secondbrain.app.ui.theme.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// ---------- Model tampilan ----------

private data class EventCard(
    val noteId: Long,
    val title: String,
    val type: NoteType,
    val dates: List<LocalDate>,
    val startTime: LocalTime?,
    val endTime: LocalTime?,
    val priority: Priority?,
    val status: NoteStatus,
    val actions: List<ActionItem>
)

private data class GanttBar(
    val noteId: Long,
    val title: String,
    val dates: List<LocalDate>,
    val startTime: LocalTime?,
    val endTime: LocalTime?,
    val color: Color
)

private fun priorityColor(p: Priority?): Color = when (p) {
    Priority.PENTING_URGEN -> DotUrgentImportant
    Priority.PENTING_TIDAK_URGEN -> DotImportantOnly
    Priority.URGEN_TIDAK_PENTING -> DotUrgentOnly
    else -> DotNeutral
}

private fun parseTime(s: String?): LocalTime? =
    s?.let { runCatching { LocalTime.parse(it, DateTimeFormatter.ofPattern("HH:mm")) }.getOrNull() }

private fun parseDates(dates: List<String>): List<LocalDate> =
    dates.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.sorted()

private enum class KanbanSort(val label: String) { DATE("Tanggal terdekat"), PRIORITY("Prioritas") }

@Composable
fun EventsScreen(
    repo: NoteRepository,
    onBack: () -> Unit,
    onNoteClick: (Long) -> Unit
) {
    val isDark = isSystemDark()
    val scope = rememberCoroutineScope()
    val notes by repo.getAllActive().collectAsState(initial = emptyList())
    var range by remember { mutableStateOf(TimeRange.of(RangePreset.MONTH)) }

    var priorityFilter by remember { mutableStateOf<Priority?>(null) }
    var sort by remember { mutableStateOf(KanbanSort.DATE) }

    // Bangun kartu kegiatan (jadwal utama catatan) & batang gantt (utama + kegiatan tambahan)
    val cards = remember(notes, range) {
        notes.mapNotNull { note ->
            val meta = repo.metadataFrom(note) ?: return@mapNotNull null
            val dates = parseDates(meta.recurrenceDates).filter { range.contains(it) }
            val extraInRange = meta.extraSchedules.orEmpty()
                .any { parseDates(it.dates.orEmpty()).any { d -> range.contains(d) } }
            if (dates.isEmpty() && !extraInRange) return@mapNotNull null
            EventCard(
                noteId = note.id,
                title = meta.title.ifBlank { note.rawText.take(40) },
                type = meta.type,
                dates = dates,
                startTime = parseTime(meta.startTime),
                endTime = parseTime(meta.endTime),
                priority = Priority.fromString(note.prioritas ?: meta.priority),
                status = NoteStatus.fromString(note.status ?: meta.status) ?: NoteStatus.BELUM_MULAI,
                actions = meta.actions
            )
        }
    }
    val bars = remember(notes, range) {
        notes.flatMap { note ->
            val meta = repo.metadataFrom(note) ?: return@flatMap emptyList<GanttBar>()
            val color = priorityColor(Priority.fromString(note.prioritas ?: meta.priority))
            val title = meta.title.ifBlank { note.rawText.take(40) }
            val main = parseDates(meta.recurrenceDates).filter { range.contains(it) }
            val mainBar = if (main.isNotEmpty()) listOf(
                GanttBar(note.id, title, main, parseTime(meta.startTime), parseTime(meta.endTime), color)
            ) else emptyList()
            val extraBars = meta.extraSchedules.orEmpty().mapNotNull { ex ->
                val d = parseDates(ex.dates.orEmpty()).filter { range.contains(it) }
                if (d.isEmpty()) null
                else GanttBar(note.id, ex.title.orEmpty().ifBlank { title }, d, parseTime(ex.startTime), null, color)
            }
            mainBar + extraBars
        }
    }

    // 3 kegiatan terdekat (termasuk yang sedang berjalan)
    val nowDt = LocalDateTime.now()
    val upcoming = remember(cards) {
        cards.mapNotNull { c ->
            val next = c.dates.mapNotNull { d ->
                val start = LocalDateTime.of(d, c.startTime ?: LocalTime.of(8, 0))
                val end = LocalDateTime.of(d, c.endTime ?: (c.startTime ?: LocalTime.of(8, 0)).plusHours(1))
                when {
                    !start.isAfter(nowDt) && !end.isBefore(nowDt) -> nowDt   // sedang berjalan
                    start.isAfter(nowDt) -> start
                    else -> null
                }
            }.minOrNull()
            next?.let { c to it }
        }.sortedBy { it.second }.take(3)
    }

    val filteredCards = remember(cards, priorityFilter, sort) {
        cards.filter { priorityFilter == null || it.priority == priorityFilter }
            .sortedWith(
                when (sort) {
                    KanbanSort.DATE -> compareBy { it.dates.firstOrNull() ?: LocalDate.MAX }
                    KanbanSort.PRIORITY -> compareBy { it.priority?.ordinal ?: 99 }
                }
            )
    }

    val bgColor = if (isDark) Lavender900 else Gray50

    Scaffold(containerColor = bgColor) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, "Kembali", tint = if (isDark) Lavender200 else Lavender600)
                    }
                    Text("Acara", style = MaterialTheme.typography.titleMedium,
                        color = if (isDark) Lavender50 else Lavender800)
                }
                TimeRangeSelector(range, { range = it }, isDark)
            }

            Spacer(Modifier.height(8.dp))

            // ----- Highlight 3 kegiatan terdekat -----
            if (upcoming.isNotEmpty()) {
                SectionLabel("terdekat")
                Spacer(Modifier.height(6.dp))
                upcoming.forEach { (card, at) ->
                    val ongoing = at == nowDt
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isDark) GlassDark else GlassLight)
                            .clickable { onNoteClick(card.noteId) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.width(4.dp).height(34.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(priorityColor(card.priority))
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(card.title, style = MaterialTheme.typography.bodyMedium,
                                color = if (isDark) Lavender50 else Lavender800, maxLines = 1)
                            Text(
                                if (ongoing) "Sedang berjalan · ${card.type.label}"
                                else at.format(DateTimeFormatter.ofPattern("EEE, dd MMM HH:mm", java.util.Locale("id", "ID"))) +
                                    " · ${card.type.label}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (ongoing) Mint600 else (if (isDark) Lavender400 else Gray600)
                            )
                        }
                        Icon(Icons.Outlined.ChevronRight, null, modifier = Modifier.size(16.dp),
                            tint = if (isDark) Lavender400 else Gray400)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // ----- Gantt chart -----
            GanttSection(bars, range, isDark, onNoteClick)

            Spacer(Modifier.height(12.dp))

            // ----- Kanban -----
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SectionLabel("papan status (${filteredCards.size})")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    PriorityFilterPicker(priorityFilter, { priorityFilter = it }, isDark)
                    KanbanSortPicker(sort, { sort = it }, isDark)
                }
            }
            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                NoteStatus.entries.forEach { status ->
                    KanbanColumn(
                        status = status,
                        cards = filteredCards.filter { it.status == status },
                        isDark = isDark,
                        onNoteClick = onNoteClick,
                        onMove = { noteId, newStatus ->
                            scope.launch { repo.setStatus(noteId, newStatus) }
                        },
                        onToggleAction = { noteId, index, done ->
                            scope.launch { repo.setActionDone(noteId, index, done) }
                        }
                    )
                }
            }

            Spacer(Modifier.height(60.dp))
        }
    }
}

// ---------- Gantt ----------

@Composable
private fun GanttSection(
    bars: List<GanttBar>,
    range: TimeRange,
    isDark: Boolean,
    onNoteClick: (Long) -> Unit
) {
    val hourMode = range.preset == RangePreset.TODAY
    val unit = if (hourMode) 34.dp else 30.dp
    val cols = if (hourMode) 24
        else (ChronoUnit.DAYS.between(range.from, range.to).toInt() + 1).coerceAtMost(92)
    val scroll = rememberScrollState()
    val labelWidth = 92.dp

    GlassCard {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SectionLabel("gantt")
            Text(
                if (hourMode) "satuan: jam" else "satuan: hari",
                style = MaterialTheme.typography.labelSmall,
                color = if (isDark) Lavender400 else Gray400
            )
        }
        Spacer(Modifier.height(6.dp))

        if (bars.isEmpty()) {
            Text("Tidak ada kegiatan pada rentang waktu ini.",
                style = MaterialTheme.typography.bodySmall,
                color = if (isDark) Lavender400 else Gray400)
        } else {
            // Header sumbu waktu
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(labelWidth))
                Row(Modifier.horizontalScroll(scroll)) {
                    repeat(cols) { i ->
                        Text(
                            if (hourMode) String.format("%02d", i)
                            else range.from.plusDays(i.toLong()).dayOfMonth.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDark) Lavender400 else Gray400,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(unit)
                        )
                    }
                }
            }
            Spacer(Modifier.height(2.dp))

            bars.forEach { bar ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(28.dp)) {
                    Text(
                        bar.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isDark) Lavender200 else Lavender600,
                        textDecoration = TextDecoration.Underline,
                        maxLines = 1,
                        modifier = Modifier.width(labelWidth)
                            .clickable { onNoteClick(bar.noteId) }
                            .padding(end = 6.dp)
                    )
                    Row(Modifier.horizontalScroll(scroll)) {
                        Box(Modifier.width(unit * cols).height(18.dp)) {
                            if (hourMode) {
                                val today = LocalDate.now()
                                if (bar.dates.any { it == today }) {
                                    val startH = bar.startTime?.hour ?: 8
                                    val endH = (bar.endTime?.hour ?: (startH + 1)).coerceAtLeast(startH + 1)
                                    Box(
                                        Modifier
                                            .offset(x = unit * startH)
                                            .width(unit * (endH - startH))
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(bar.color.copy(alpha = 0.85f))
                                    )
                                }
                            } else {
                                bar.dates.forEach { d ->
                                    val idx = ChronoUnit.DAYS.between(range.from, d).toInt()
                                    if (idx in 0 until cols) {
                                        Box(
                                            Modifier
                                                .offset(x = unit * idx + 2.dp)
                                                .width(unit - 4.dp)
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(5.dp))
                                                .background(bar.color.copy(alpha = 0.85f))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------- Kanban ----------

@Composable
private fun KanbanColumn(
    status: NoteStatus,
    cards: List<EventCard>,
    isDark: Boolean,
    onNoteClick: (Long) -> Unit,
    onMove: (Long, NoteStatus) -> Unit,
    onToggleAction: (Long, Int, Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .width(240.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isDark) GlassDark else GlassMid)
            .padding(8.dp)
    ) {
        Text(
            "${status.label} (${cards.size})",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = if (isDark) Lavender200 else Lavender600,
            modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
        )
        Column(
            modifier = Modifier
                .heightIn(max = 440.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (cards.isEmpty()) {
                Text("—", style = MaterialTheme.typography.labelSmall,
                    color = if (isDark) Lavender400 else Gray400,
                    modifier = Modifier.padding(6.dp))
            }
            cards.forEach { card ->
                KanbanCardView(card, status, isDark, onNoteClick, onMove, onToggleAction)
            }
        }
    }
}

@Composable
private fun KanbanCardView(
    card: EventCard,
    status: NoteStatus,
    isDark: Boolean,
    onNoteClick: (Long) -> Unit,
    onMove: (Long, NoteStatus) -> Unit,
    onToggleAction: (Long, Int, Boolean) -> Unit
) {
    var actionsExpanded by remember(card.noteId) { mutableStateOf(false) }
    val pColor = priorityColor(card.priority)
    val doneCount = card.actions.count { it.done }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(pColor.copy(alpha = if (isDark) 0.18f else 0.12f))
            .padding(8.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.width(3.dp).height(30.dp).clip(RoundedCornerShape(2.dp)).background(pColor))
            Spacer(Modifier.width(7.dp))
            Column(Modifier.weight(1f).clickable { onNoteClick(card.noteId) }) {
                Text(card.title, style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = if (isDark) Lavender50 else Lavender800, maxLines = 2)
                val dateLine = buildString {
                    card.dates.firstOrNull()?.let { append(it) }
                    card.startTime?.let { append(" ").append(it) }
                    if (card.dates.size > 1) append(" (+${card.dates.size - 1})")
                }
                Text(
                    (if (dateLine.isBlank()) "" else "$dateLine · ") + card.type.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDark) Lavender400 else Gray600, maxLines = 1
                )
            }
        }

        // Progress dari action items
        if (card.actions.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { doneCount.toFloat() / card.actions.size },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = Mint600,
                trackColor = if (isDark) GlassDark else Gray100
            )
            // Accordion aksi
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clickable { actionsExpanded = !actionsExpanded }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Aksi $doneCount/${card.actions.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDark) Lavender200 else Lavender600,
                    modifier = Modifier.weight(1f))
                Icon(
                    if (actionsExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    null, modifier = Modifier.size(14.dp),
                    tint = if (isDark) Lavender400 else Gray400
                )
            }
            if (actionsExpanded) {
                card.actions.forEachIndexed { i, act ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = act.done,
                            onCheckedChange = { onToggleAction(card.noteId, i, it) },
                            modifier = Modifier.size(28.dp),
                            colors = CheckboxDefaults.colors(checkedColor = Mint600)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            act.action,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDark) Lavender50 else Lavender800,
                            textDecoration = if (act.done) TextDecoration.LineThrough else null,
                            maxLines = 2
                        )
                    }
                }
            }
        }

        // Pindah kolom (mengubah status otomatis)
        val statuses = NoteStatus.entries
        val idx = statuses.indexOf(status)
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { onMove(card.noteId, statuses[idx - 1]) },
                enabled = idx > 0,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(Icons.Outlined.ChevronLeft, "Pindah kiri", modifier = Modifier.size(16.dp),
                    tint = if (idx > 0) (if (isDark) Lavender200 else Lavender600)
                           else (if (isDark) Lavender400.copy(0.3f) else Gray400.copy(0.4f)))
            }
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = { onMove(card.noteId, statuses[idx + 1]) },
                enabled = idx < statuses.size - 1,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(Icons.Outlined.ChevronRight, "Pindah kanan", modifier = Modifier.size(16.dp),
                    tint = if (idx < statuses.size - 1) (if (isDark) Lavender200 else Lavender600)
                           else (if (isDark) Lavender400.copy(0.3f) else Gray400.copy(0.4f)))
            }
        }
    }
}

// ---------- Filter & sort ----------

@Composable
private fun PriorityFilterPicker(selected: Priority?, onSelect: (Priority?) -> Unit, isDark: Boolean) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(if (isDark) GlassDark else GlassLight)
                .clickable { open = true }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(9.dp).clip(RoundedCornerShape(5.dp)).background(priorityColor(selected)))
            Spacer(Modifier.width(4.dp))
            Text(
                selected?.label?.take(14) ?: "Semua prioritas",
                style = MaterialTheme.typography.labelSmall,
                color = if (isDark) Lavender200 else Lavender600
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Semua prioritas", style = MaterialTheme.typography.bodySmall) },
                onClick = { onSelect(null); open = false }
            )
            Priority.entries.forEach { p ->
                DropdownMenuItem(
                    text = { Text(p.label, style = MaterialTheme.typography.bodySmall) },
                    onClick = { onSelect(p); open = false }
                )
            }
        }
    }
}

@Composable
private fun KanbanSortPicker(sort: KanbanSort, onSelect: (KanbanSort) -> Unit, isDark: Boolean) {
    var open by remember { mutableStateOf(false) }
    Box {
        Icon(
            Icons.Outlined.SwapVert, "Urutkan",
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(if (isDark) GlassDark else GlassLight)
                .clickable { open = true }
                .padding(6.dp)
                .size(16.dp),
            tint = if (isDark) Lavender200 else Lavender600
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            KanbanSort.entries.forEach { s ->
                DropdownMenuItem(
                    text = {
                        Text(if (s == sort) "✓ ${s.label}" else s.label,
                            style = MaterialTheme.typography.bodySmall)
                    },
                    onClick = { onSelect(s); open = false }
                )
            }
        }
    }
}
