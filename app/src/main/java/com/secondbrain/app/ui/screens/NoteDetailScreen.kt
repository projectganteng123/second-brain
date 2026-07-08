package com.secondbrain.app.ui.screens

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
import androidx.compose.ui.unit.dp
import com.secondbrain.app.data.model.*
import com.secondbrain.app.ui.components.*
import com.secondbrain.app.ui.theme.*
import com.secondbrain.app.viewmodel.NoteDetailViewModel

@Composable
fun NoteDetailScreen(
    vm: NoteDetailViewModel,
    noteId: Long,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    onOpenGroup: (Long) -> Unit
) {
    val isDark = isSystemDark()
    val state by vm.state.collectAsState()

    val rescheduleContext = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(noteId) { vm.load(noteId) }
    LaunchedEffect(state.deleted) { if (state.deleted) onDeleted() }
    // Jadwalkan ulang alarm setiap kali catatan berubah (toggle alarm / proses ulang)
    LaunchedEffect(state.note?.updatedAt, state.note?.useAlarm) {
        if (state.note != null) {
            runCatching { com.secondbrain.app.notification.ReminderScheduler.scheduleUpcoming(rescheduleContext) }
        }
    }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    var editing by remember { mutableStateOf(false) }
    var editText by remember(state.note?.id) { mutableStateOf(state.note?.rawText ?: "") }
    var confirmReExtract by remember { mutableStateOf(false) }

    val bgColor = if (isDark) Lavender900 else Gray50

    Scaffold(
        containerColor = bgColor,
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        val note = state.note
        val meta = state.metadata

        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Lavender600)
            }
            return@Scaffold
        }
        if (note == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Catatan tidak ditemukan", color = if (isDark) Lavender400 else Gray600)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(16.dp))

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, "Kembali", tint = if (isDark) Lavender200 else Lavender600)
                    }
                    Text(
                        "Detail catatan",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isDark) Lavender50 else Lavender800
                    )
                }
                Row {
                    IconButton(onClick = { vm.toggleArchive() }) {
                        Icon(
                            if (note.isArchived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                            "Arsip", tint = if (isDark) Lavender400 else Lavender600
                        )
                    }
                    IconButton(onClick = { vm.delete() }) {
                        Icon(Icons.Outlined.DeleteOutline, "Hapus", tint = Rose600)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Raw text card (view / edit)
            GlassCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionLabel("catatan asli")
                    if (!editing) {
                        TextButton(onClick = { editText = note.rawText; editing = true }) {
                            Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(16.dp), tint = Lavender600)
                            Spacer(Modifier.width(4.dp))
                            Text("Edit", color = Lavender600, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                if (editing) {
                    OutlinedTextField(
                        value = editText,
                        onValueChange = { editText = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Lavender400,
                            unfocusedBorderColor = if (isDark) GlassBorderDark else Lavender200,
                            focusedTextColor = if (isDark) Lavender50 else Lavender800,
                            unfocusedTextColor = if (isDark) Lavender50 else Lavender800
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassButton(
                            text = if (state.reExtracting) "Memproses..." else "Simpan & proses ulang",
                            icon = Icons.Outlined.AutoAwesome,
                            onClick = { confirmReExtract = true },
                            accent = true,
                            enabled = !state.reExtracting,
                            modifier = Modifier.weight(1f)
                        )
                        GlassButton(text = "Batal", onClick = { editing = false })
                    }
                } else {
                    Text(
                        note.rawText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDark) Lavender50 else Lavender800
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Lampiran (foto tampil inline; video/file/link diketuk untuk dibuka)
            if (note.attachmentsJson.isNotBlank()) {
                AttachmentSection(note.attachmentsJson, isDark)
                Spacer(Modifier.height(10.dp))
            }

            // Metadata (read-only + mode edit lengkap dengan picker jam/kalender)
            var editingMeta by remember(note.id) { mutableStateOf(false) }
            var editedMeta by remember(note.id) { mutableStateOf<Metadata?>(null) }
            var alarmTimesEnabled by remember(note.id) { mutableStateOf(false) }
            val displayMeta = editedMeta ?: meta
            GlassCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionLabel("metadata")
                    TextButton(onClick = {
                        if (editingMeta) {
                            editingMeta = false; editedMeta = null
                        } else {
                            // Waktu alarm dinormalkan ke field baru (fallback legacy)
                            val base = (meta ?: Metadata()).let {
                                it.copy(
                                    alarmTimes = it.alarmTimesEffective(),
                                    preparationTime = null,
                                    preparationTimes = null
                                )
                            }
                            editedMeta = base
                            alarmTimesEnabled = base.alarmTimes.orEmpty().isNotEmpty()
                            editingMeta = true
                        }
                    }) {
                        Icon(
                            if (editingMeta) Icons.Outlined.Close else Icons.Outlined.Edit,
                            null, modifier = Modifier.size(16.dp), tint = Lavender600
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (editingMeta) "Tutup" else "Edit", color = Lavender600,
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (displayMeta == null) {
                    Text(
                        "Belum ada metadata — ketuk Edit untuk mengisi manual.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDark) Lavender400 else Gray600
                    )
                } else {
                    MetadataRow("Judul", displayMeta.title.ifBlank { "-" })
                    MetadataRow("Jenis", displayMeta.type.label)
                    if (displayMeta.startTime != null || displayMeta.endTime != null) {
                        MetadataRow("Waktu", buildString {
                            displayMeta.startTime?.let { append(it) }
                            displayMeta.endTime?.let { append(" – $it") }
                        })
                    }
                    if (displayMeta.recurrenceDates.isNotEmpty()) {
                        MetadataRow("Tanggal", displayMeta.recurrenceDates.joinToString(", ") {
                            com.secondbrain.app.util.TimeFormat.dateMedium(it)
                        })
                    }
                    if (displayMeta.locations.isNotEmpty()) {
                        MetadataRow("Lokasi", displayMeta.locations.joinToString(", ") { it.value })
                    }
                    if (displayMeta.entities.people.isNotEmpty()) {
                        MetadataRow("Orang", displayMeta.entities.people.joinToString(", "))
                    }
                    if (displayMeta.entities.organizations.isNotEmpty()) {
                        MetadataRow("Organisasi", displayMeta.entities.organizations.joinToString(", "))
                    }
                    if (displayMeta.keywords.isNotEmpty()) {
                        MetadataRow("Keywords", displayMeta.keywords.joinToString(", "))
                    }
                    if (displayMeta.actions.isNotEmpty()) {
                        MetadataRow("Aksi", displayMeta.actions.joinToString("\n") {
                            buildString {
                                append("• ${it.action}")
                                it.owner?.let { o -> append(" ($o)") }
                                it.deadline?.let { d -> append(" → ${com.secondbrain.app.util.TimeFormat.dateTime(d)}") }
                            }
                        })
                    }
                    val extraSchedules = displayMeta.extraSchedules.orEmpty()
                    if (extraSchedules.isNotEmpty()) {
                        MetadataRow("Kegiatan lain", extraSchedules.joinToString("\n") { it.displayLine() })
                    }
                    val transactions = displayMeta.transactions.orEmpty()
                    if (transactions.isNotEmpty()) {
                        MetadataRow("Transaksi", transactions.joinToString("\n") { it.displayLine() })
                    }
                    if (displayMeta.summary.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            displayMeta.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isDark) Lavender200 else Gray600,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }
            }

            // Mode edit: editor lengkap + waktu alarm + Simpan/Batal
            editedMeta?.takeIf { editingMeta }?.let { current ->
                Spacer(Modifier.height(8.dp))
                MetadataEditor(
                    metadata = current,
                    isDark = isDark,
                    onChange = { editedMeta = it }
                )
                Spacer(Modifier.height(8.dp))
                GlassCard {
                    AlarmTimesSection(
                        times = current.alarmTimes.orEmpty(),
                        enabled = alarmTimesEnabled,
                        onEnabledChange = { alarmTimesEnabled = it },
                        onTimesChange = { editedMeta = current.copy(alarmTimes = it) },
                        isDark = isDark
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton(
                        text = "Simpan metadata",
                        icon = Icons.Outlined.Save,
                        onClick = {
                            vm.saveMetadata(current.copy(
                                alarmTimes = if (alarmTimesEnabled)
                                    current.alarmTimes.orEmpty().filter { it.isNotBlank() }
                                else emptyList()
                            ))
                            editingMeta = false; editedMeta = null
                        },
                        accent = true,
                        modifier = Modifier.weight(1f)
                    )
                    GlassButton(text = "Batal", onClick = { editingMeta = false; editedMeta = null })
                }
            }
            Spacer(Modifier.height(10.dp))

            // Grup catatan: keanggotaan + saran AI yang belum dikonsumsi (jalur pending)
            val noteGroups by vm.groupsOf(note.id).collectAsState(initial = emptyList())
            val allGroups by vm.activeGroups().collectAsState(initial = emptyList())
            var showGroupPicker by remember { mutableStateOf(false) }
            GlassCard {
                SectionLabel("grup", modifier = Modifier.padding(bottom = 6.dp))
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    noteGroups.forEach { g ->
                        GroupChip(
                            label = g.name,
                            selected = true,
                            isDark = isDark,
                            onClick = { onOpenGroup(g.id) },
                            onRemove = { vm.removeFromGroup(g.id) }
                        )
                    }
                    GroupChip("+ Grup", selected = false, isDark = isDark,
                        onClick = { showGroupPicker = true })
                }

                // Saran AI (tap = terima, ✕ = tolak) — hanya yang belum jadi anggota
                val pendingSuggestions = meta?.suggestedGroups.orEmpty().filterNot { s ->
                    noteGroups.any { it.name.trim().equals(s.trim(), ignoreCase = true) }
                }
                if (pendingSuggestions.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Saran AI — ketuk untuk menerima:",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isDark) Lavender400 else Gray600)
                    Spacer(Modifier.height(4.dp))
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        pendingSuggestions.forEach { s ->
                            GroupChip(
                                label = s.trim(),
                                selected = false,
                                isDark = isDark,
                                onClick = { vm.acceptGroupSuggestion(s) },
                                onRemove = { vm.rejectGroupSuggestion(s) }
                            )
                        }
                    }
                }
            }
            if (showGroupPicker) {
                GroupPickerDialog(
                    existingNames = allGroups.map { it.name },
                    alreadySelected = noteGroups.map { it.name },
                    onPick = { vm.addToGroup(it); showGroupPicker = false },
                    onDismiss = { showGroupPicker = false }
                )
            }
            Spacer(Modifier.height(10.dp))

            // Management: priority & status
            GlassCard {
                SectionLabel("manajemen", modifier = Modifier.padding(bottom = 8.dp))

                Text("Prioritas", style = MaterialTheme.typography.labelSmall,
                    color = if (isDark) Lavender400 else Gray600)
                Spacer(Modifier.height(4.dp))
                val curP = note.prioritas?.let { runCatching { Priority.valueOf(it) }.getOrNull() }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Priority.entries.forEach { p ->
                        GlassButton(
                            text = p.label,
                            onClick = { vm.setPrioritas(if (curP == p) null else p) },
                            accent = curP == p,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text("Status", style = MaterialTheme.typography.labelSmall,
                    color = if (isDark) Lavender400 else Gray600)
                Spacer(Modifier.height(4.dp))
                val curS = note.status?.let { runCatching { NoteStatus.valueOf(it) }.getOrNull() }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NoteStatus.entries.forEach { s ->
                        GlassButton(
                            text = s.label,
                            onClick = { vm.setStatus(if (curS == s) null else s) },
                            accent = curS == s,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Jadikan alarm", style = MaterialTheme.typography.bodyMedium,
                            color = if (isDark) Lavender50 else Lavender800)
                        Text("Pengingat berbunyi alarm + layar penuh",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDark) Lavender400 else Gray600)
                    }
                    Switch(checked = note.useAlarm, onCheckedChange = { vm.setUseAlarm(it) })
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    // Konfirmasi sebelum proses ulang: hasil AI menimpa SEMUA metadata, termasuk editan manual
    if (confirmReExtract) {
        AlertDialog(
            onDismissRequest = { confirmReExtract = false },
            title = { Text("Proses ulang dengan AI?") },
            text = {
                Text("Semua metadata — termasuk yang pernah kamu edit manual — akan ditimpa hasil ekstraksi AI yang baru.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmReExtract = false
                    vm.reExtract(editText)
                    editing = false
                }) { Text("Lanjut") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReExtract = false }) { Text("Batal") }
            }
        )
    }
}
