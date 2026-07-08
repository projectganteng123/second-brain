package com.secondbrain.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.secondbrain.app.data.model.*
import com.secondbrain.app.notification.ReminderScheduler
import com.secondbrain.app.util.PrefsManager
import com.secondbrain.app.util.TimeFormat
import com.secondbrain.app.ui.components.*
import com.secondbrain.app.ui.theme.*
import com.secondbrain.app.viewmodel.InputUiState
import com.secondbrain.app.viewmodel.InputViewModel

@Composable
fun PreviewScreen(
    vm: InputViewModel,
    metadata: Metadata,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val isDark = isSystemDark()
    val uiState by vm.uiState.collectAsState()
    val selectedPrioritas by vm.selectedPrioritas.collectAsState()
    val selectedStatus by vm.selectedStatus.collectAsState()
    val useAlarm by vm.useAlarm.collectAsState()

    var editedMetadata by remember {
        mutableStateOf(metadata.copy(
            alarmTimes = metadata.alarmTimesEffective(),
            preparationTime = null,
            preparationTimes = null
        ))
    }
    var editing by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val prefs = remember { PrefsManager(context) }
    // Toggle waktu alarm: menyala bila AI/user mengisi waktunya, bisa dimatikan.
    var alarmTimesEnabled by remember { mutableStateOf(metadata.alarmTimesEffective().isNotEmpty()) }

    LaunchedEffect(uiState) {
        if (uiState is InputUiState.Saved) {
            // Jadwalkan alarm langsung (tanpa delay WorkManager) agar reminder dekat-waktu tetap bunyi
            runCatching { ReminderScheduler.scheduleUpcoming(context) }
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

            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Outlined.ArrowBack, "Kembali",
                        tint = if (isDark) Lavender200 else Lavender600
                    )
                }
                Text(
                    "Cek & simpan",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isDark) Lavender50 else Lavender800
                )
            }

            Spacer(Modifier.height(12.dp))

            // Metadata card
            GlassCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionLabel("hasil ekstraksi AI")
                    TextButton(onClick = { editing = !editing }) {
                        Icon(
                            if (editing) Icons.Outlined.Close else Icons.Outlined.Edit,
                            null, modifier = Modifier.size(16.dp), tint = Lavender600
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (editing) "Tutup" else "Edit", color = Lavender600,
                            style = MaterialTheme.typography.labelSmall)
                    }
                }

                MetadataRow("Judul", editedMetadata.title.ifBlank { "-" })
                MetadataRow("Jenis", editedMetadata.type.label)

                if (editedMetadata.startTime != null || editedMetadata.endTime != null) {
                    val time = buildString {
                        editedMetadata.startTime?.let { append(it) }
                        editedMetadata.endTime?.let { append(" – $it") }
                    }
                    MetadataRow("Waktu", time)
                }

                if (editedMetadata.recurrenceDates.isNotEmpty()) {
                    MetadataRow(
                        "Tanggal",
                        if (editedMetadata.recurrenceDates.size == 1)
                            TimeFormat.date(editedMetadata.recurrenceDates.first())
                        else
                            "${TimeFormat.date(editedMetadata.recurrenceDates.first())} (+${editedMetadata.recurrenceDates.size - 1})"
                    )
                }

                if (editedMetadata.locations.isNotEmpty()) {
                    MetadataRow("Lokasi", editedMetadata.locations.joinToString(", ") { it.value })
                }

                if (editedMetadata.entities.people.isNotEmpty()) {
                    MetadataRow("Orang", editedMetadata.entities.people.joinToString(", "))
                }

                if (editedMetadata.entities.organizations.isNotEmpty()) {
                    MetadataRow("Organisasi", editedMetadata.entities.organizations.joinToString(", "))
                }

                if (editedMetadata.keywords.isNotEmpty()) {
                    MetadataRow("Keywords", editedMetadata.keywords.take(5).joinToString(", "))
                }

                if (editedMetadata.actions.isNotEmpty()) {
                    MetadataRow(
                        "Aksi",
                        editedMetadata.actions.joinToString("\n") {
                            buildString {
                                append("• ${it.action}")
                                it.owner?.let { o -> append(" ($o)") }
                                it.deadline?.let { d -> append(" → ${TimeFormat.dateTime(d)}") }
                            }
                        }
                    )
                }

                val extraSchedules = editedMetadata.extraSchedules.orEmpty()
                if (extraSchedules.isNotEmpty()) {
                    MetadataRow(
                        "Kegiatan lain",
                        extraSchedules.joinToString("\n") { it.displayLine() } +
                            "\n(masing-masing tetap dibuatkan pengingat)"
                    )
                }

                val transactions = editedMetadata.transactions.orEmpty()
                if (transactions.isNotEmpty()) {
                    MetadataRow("Transaksi", transactions.joinToString("\n") { it.displayLine() })
                }

                if (editedMetadata.summary.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        editedMetadata.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDark) Lavender200 else Gray600,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }

            // Editable card
            if (editing) {
                Spacer(Modifier.height(8.dp))
                MetadataEditor(
                    metadata = editedMetadata,
                    isDark = isDark,
                    onChange = { editedMetadata = it }
                )
            }

            // Warning: no end time
            if (editedMetadata.endTime == null && editedMetadata.startTime != null) {
                Spacer(Modifier.height(8.dp))
                WarningBox("Waktu selesai tidak ditemukan. Reminder akan dikirim saat waktu mulai.")
            }

            // Warning: no dates
            if (editedMetadata.recurrenceDates.isEmpty() &&
                editedMetadata.type in listOf(NoteType.MEETING, NoteType.TASK, NoteType.EVENT, NoteType.REMINDER)
            ) {
                Spacer(Modifier.height(8.dp))
                WarningBox("Tanggal tidak terdeteksi. Catatan tidak akan muncul di kalender.")
            }

            Spacer(Modifier.height(12.dp))

            // Grup catatan: saran AI (cocok existing = tercentang; baru = tidak) + pilihan manual
            val activeGroups by vm.activeGroups.collectAsState(initial = emptyList())
            val selectedGroups by vm.selectedGroups.collectAsState()
            GlassCard {
                SectionLabel("grup", modifier = Modifier.padding(bottom = 8.dp))
                GroupPickerSection(
                    selectedNames = selectedGroups,
                    suggestions = metadata.suggestedGroups.orEmpty(),
                    existingNames = activeGroups.map { it.name },
                    onToggle = vm::toggleGroup
                )
            }

            Spacer(Modifier.height(12.dp))

            // Manual fields
            GlassCard {
                SectionLabel("pengaturan manual", modifier = Modifier.padding(bottom = 8.dp))

                // Prioritas
                Text(
                    "Prioritas",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDark) Lavender400 else Gray600
                )
                Spacer(Modifier.height(4.dp))
                PrioritySelector(selectedPrioritas, vm::setPrioritas)

                Spacer(Modifier.height(4.dp))
                Text(
                    "Dibiarkan = pakai rekomendasi AI.",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDark) Lavender400 else Gray400
                )

                Spacer(Modifier.height(10.dp))

                // Status
                Text(
                    "Status",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDark) Lavender400 else Gray600
                )
                Spacer(Modifier.height(4.dp))
                StatusSelector(selectedStatus, vm::setStatus)

                Spacer(Modifier.height(12.dp))

                // Alarm toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Alarm acara", style = MaterialTheme.typography.bodyMedium,
                            color = if (isDark) Lavender50 else Lavender800)
                        val offsetMin = prefs.getAlarmOffsetMinutes()
                        Text(
                            "Alarm keras " +
                                (if (offsetMin == 0) "tepat saat acara mulai" else "$offsetMin menit sebelum acara") +
                                ". Saat mulai selalu ada notifikasi info.",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDark) Lavender400 else Gray600
                        )
                    }
                    Switch(checked = useAlarm, onCheckedChange = vm::setUseAlarm)
                }

                // ----- Waktu alarm (boleh lebih dari satu) -----
                Spacer(Modifier.height(12.dp))
                AlarmTimesSection(
                    times = editedMetadata.alarmTimes.orEmpty(),
                    enabled = alarmTimesEnabled,
                    onEnabledChange = { alarmTimesEnabled = it },
                    onTimesChange = { editedMetadata = editedMetadata.copy(alarmTimes = it) },
                    isDark = isDark
                )
            }

            Spacer(Modifier.height(16.dp))

            // Save button
            val isSaving = uiState is InputUiState.Saving
            GlassButton(
                text = if (isSaving) "Menyimpan..." else "Simpan catatan",
                icon = Icons.Outlined.Save,
                onClick = {
                    vm.saveNote(editedMetadata.copy(
                        alarmTimes = if (alarmTimesEnabled)
                            editedMetadata.alarmTimes.orEmpty().filter { it.isNotBlank() }
                        else emptyList()
                    ))
                },
                accent = true,
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun WarningBox(message: String) {
    val isDark = isSystemDark()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(Icons.Outlined.Warning, null, tint = Lemon600, modifier = Modifier.size(16.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = if (isDark) Lemon200 else Lemon800
        )
    }
}

@Composable
private fun PrioritySelector(selected: Priority?, onSelect: (Priority?) -> Unit) {
    val isDark = isSystemDark()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Priority.entries.forEach { p ->
            val isSelected = p == selected
            GlassButton(
                text = p.label,
                onClick = { onSelect(if (isSelected) null else p) },
                accent = isSelected,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun StatusSelector(selected: NoteStatus?, onSelect: (NoteStatus?) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        NoteStatus.entries.forEach { s ->
            GlassButton(
                text = s.label,
                onClick = { onSelect(s) },
                accent = s == selected,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

