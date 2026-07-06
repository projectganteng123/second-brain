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
            preparationTimes = metadata.preparationTimesEffective(),
            preparationTime = null
        ))
    }
    var editing by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val prefs = remember { PrefsManager(context) }
    // Toggle alarm persiapan: menyala bila AI/user mengisi waktunya, bisa dimatikan.
    var prepEnabled by remember { mutableStateOf(metadata.preparationTimesEffective().isNotEmpty()) }
    val canExactAlarm = remember {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            (context.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager)
                .canScheduleExactAlarms()
        } else true
    }

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
                                it.deadline?.let { d -> append(" → $d") }
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

                // ----- Alarm persiapan (boleh lebih dari satu waktu) -----
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Alarm persiapan", style = MaterialTheme.typography.bodyMedium,
                            color = if (isDark) Lavender50 else Lavender800)
                        Text("Tiap waktu persiapan menjadi alarm keras",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDark) Lavender400 else Gray600)
                    }
                    Switch(
                        checked = prepEnabled,
                        onCheckedChange = {
                            prepEnabled = it
                            if (it && editedMetadata.preparationTimes.orEmpty().isEmpty()) {
                                editedMetadata = editedMetadata.copy(
                                    preparationTimes = listOf(defaultPrepTime())
                                )
                            }
                        }
                    )
                }
                if (prepEnabled) {
                    val prepList = editedMetadata.preparationTimes.orEmpty()
                    prepList.forEachIndexed { i, t ->
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DateTimeField(
                                label = "Persiapan ${i + 1}",
                                value = t,
                                onChange = { new ->
                                    val list = prepList.toMutableList()
                                    if (new == null) list.removeAt(i) else list[i] = new
                                    editedMetadata = editedMetadata.copy(preparationTimes = list)
                                },
                                isDark = isDark,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                editedMetadata = editedMetadata.copy(
                                    preparationTimes = prepList.toMutableList().also { it.removeAt(i) }
                                )
                            }) {
                                Icon(Icons.Outlined.DeleteOutline, "Hapus waktu persiapan",
                                    tint = Rose600, modifier = Modifier.size(18.dp))
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Outlined.Alarm, null, modifier = Modifier.size(12.dp), tint = Mint600)
                            Text(TimeFormat.dateTime(t),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isDark) Mint200 else Mint600)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    GlassButton(
                        text = "Tambah waktu persiapan",
                        icon = Icons.Outlined.Add,
                        onClick = {
                            editedMetadata = editedMetadata.copy(
                                preparationTimes = prepList + defaultPrepTime()
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // ----- Izin exact alarm (Android 12+) -----
                if (!canExactAlarm && editedMetadata.recurrenceDates.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(if (isDark) Lemon600.copy(0.12f) else Lemon50, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Outlined.Warning, null, modifier = Modifier.size(16.dp), tint = Lemon600)
                        Column {
                            Text(
                                "Notifikasi acara butuh izin \"Alarm & pengingat\" agar tampil tepat waktu. " +
                                "Tanpa izin, notifikasi acara TIDAK dijadwalkan (alarm keras tetap jalan).",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isDark) Lemon200 else Lemon800
                            )
                            TextButton(onClick = {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                    context.startActivity(
                                        android.content.Intent(
                                            android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                            android.net.Uri.parse("package:${context.packageName}")
                                        )
                                    )
                                }
                            }) { Text("Beri izin", color = Lemon600) }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Save button
            val isSaving = uiState is InputUiState.Saving
            GlassButton(
                text = if (isSaving) "Menyimpan..." else "Simpan catatan",
                icon = Icons.Outlined.Save,
                onClick = {
                    vm.saveNote(editedMetadata.copy(
                        preparationTimes = if (prepEnabled)
                            editedMetadata.preparationTimes.orEmpty().filter { it.isNotBlank() }
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

/** Waktu persiapan default saat user menambah manual: 1 jam dari sekarang. */
private fun defaultPrepTime(): String =
    java.time.LocalDateTime.now().plusHours(1)
        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"))

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

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun MetadataEditor(
    metadata: Metadata,
    isDark: Boolean,
    onChange: (Metadata) -> Unit
) {
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Lavender400,
        unfocusedBorderColor = if (isDark) GlassBorderDark else Lavender200,
        focusedContainerColor = if (isDark) GlassDark else GlassLight,
        unfocusedContainerColor = if (isDark) GlassDark else GlassLight,
        focusedTextColor = if (isDark) Lavender50 else Lavender800,
        unfocusedTextColor = if (isDark) Lavender50 else Lavender800,
        focusedLabelColor = Lavender600,
        unfocusedLabelColor = if (isDark) Lavender400 else Gray400
    )
    val fieldShape = RoundedCornerShape(12.dp)
    fun splitComma(s: String) = s.split(",").map { it.trim() }.filter { it.isNotBlank() }

    // Teks lokal untuk field yang diketik bebas (dipisah koma), agar kursor tidak lompat
    var locationsText by remember { mutableStateOf(metadata.locations.joinToString(", ") { it.value }) }
    var peopleText by remember { mutableStateOf(metadata.entities.people.joinToString(", ")) }
    var orgsText by remember { mutableStateOf(metadata.entities.organizations.joinToString(", ")) }
    var keywordsText by remember { mutableStateOf(metadata.keywords.joinToString(", ")) }

    GlassCard {
        SectionLabel("edit manual", modifier = Modifier.padding(bottom = 8.dp))

        OutlinedTextField(
            value = metadata.title,
            onValueChange = { onChange(metadata.copy(title = it)) },
            label = { Text("Judul") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = fieldShape,
            colors = fieldColors
        )

        Spacer(Modifier.height(8.dp))
        Text("Jenis", style = MaterialTheme.typography.labelSmall, color = if (isDark) Lavender400 else Gray600)
        Spacer(Modifier.height(4.dp))
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            NoteType.entries.forEach { t ->
                val sel = t == metadata.type
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (sel) (if (isDark) Lavender600.copy(0.4f) else Lavender100)
                            else (if (isDark) GlassDark else GlassLight)
                        )
                        .clickable { onChange(metadata.copy(type = t)) }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        t.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (sel) (if (isDark) Lavender200 else Lavender600)
                                else (if (isDark) Lavender400 else Gray600)
                    )
                }
            }
        }

        // ----- Jam mulai/selesai: picker jam -----
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimeField(
                label = "Jam mulai",
                value = metadata.startTime,
                onChange = { onChange(metadata.copy(startTime = it)) },
                isDark = isDark,
                modifier = Modifier.weight(1f)
            )
            TimeField(
                label = "Jam selesai",
                value = metadata.endTime,
                onChange = { onChange(metadata.copy(endTime = it)) },
                isDark = isDark,
                modifier = Modifier.weight(1f)
            )
        }

        // ----- Tanggal: kalender multi-tanggal -----
        Spacer(Modifier.height(10.dp))
        MultiDateField(
            label = "Tanggal",
            dates = metadata.recurrenceDates,
            onChange = { onChange(metadata.copy(recurrenceDates = it)) },
            isDark = isDark
        )

        // (Alarm persiapan diedit di kartu "pengaturan manual" — bukan di sini,
        //  supaya tidak ada dua sumber yang saling menimpa.)

        // ----- Lokasi, orang, organisasi, keywords -----
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = locationsText,
            onValueChange = {
                locationsText = it
                onChange(metadata.copy(locations = splitComma(it).map { v -> LocationEntry(value = v) }))
            },
            label = { Text("Lokasi (pisah koma)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = fieldShape,
            colors = fieldColors
        )

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = peopleText,
            onValueChange = {
                peopleText = it
                onChange(metadata.copy(entities = metadata.entities.copy(people = splitComma(it))))
            },
            label = { Text("Orang (pisah koma)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = fieldShape,
            colors = fieldColors
        )

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = orgsText,
            onValueChange = {
                orgsText = it
                onChange(metadata.copy(entities = metadata.entities.copy(organizations = splitComma(it))))
            },
            label = { Text("Organisasi (pisah koma)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = fieldShape,
            colors = fieldColors
        )

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = keywordsText,
            onValueChange = {
                keywordsText = it
                onChange(metadata.copy(keywords = splitComma(it)))
            },
            label = { Text("Keywords (pisah koma)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = fieldShape,
            colors = fieldColors
        )

        // ----- Ringkasan -----
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = metadata.summary,
            onValueChange = { onChange(metadata.copy(summary = it)) },
            label = { Text("Ringkasan") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
            shape = fieldShape,
            colors = fieldColors
        )

        // ----- Action items -----
        Spacer(Modifier.height(10.dp))
        Text("Action items", style = MaterialTheme.typography.labelSmall,
            color = if (isDark) Lavender400 else Gray600)
        Spacer(Modifier.height(4.dp))
        metadata.actions.forEachIndexed { i, act ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isDark) GlassDark else GlassLight)
                    .padding(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = act.action,
                        onValueChange = { v ->
                            onChange(metadata.copy(
                                actions = metadata.actions.toMutableList().also { it[i] = act.copy(action = v) }
                            ))
                        },
                        label = { Text("Aksi ${i + 1}") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = fieldShape,
                        colors = fieldColors
                    )
                    IconButton(onClick = {
                        onChange(metadata.copy(
                            actions = metadata.actions.toMutableList().also { it.removeAt(i) }
                        ))
                    }) {
                        Icon(Icons.Outlined.DeleteOutline, "Hapus aksi", tint = Rose600,
                            modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = act.owner ?: "",
                        onValueChange = { v ->
                            onChange(metadata.copy(
                                actions = metadata.actions.toMutableList()
                                    .also { it[i] = act.copy(owner = v.ifBlank { null }) }
                            ))
                        },
                        label = { Text("Penanggung jawab") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = fieldShape,
                        colors = fieldColors
                    )
                    DateField(
                        label = "Deadline",
                        value = act.deadline,
                        onChange = { v ->
                            onChange(metadata.copy(
                                actions = metadata.actions.toMutableList()
                                    .also { it[i] = act.copy(deadline = v) }
                            ))
                        },
                        isDark = isDark,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        GlassButton(
            text = "Tambah aksi",
            icon = Icons.Outlined.Add,
            onClick = { onChange(metadata.copy(actions = metadata.actions + ActionItem())) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
