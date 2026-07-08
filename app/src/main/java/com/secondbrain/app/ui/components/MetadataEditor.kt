package com.secondbrain.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.secondbrain.app.data.model.*
import com.secondbrain.app.ui.theme.*
import com.secondbrain.app.util.AmountParse
import com.secondbrain.app.util.TimeFormat

/**
 * Editor SEMUA field metadata — dipakai PreviewScreen dan NoteDetailScreen.
 * (Waktu alarm diedit terpisah lewat [AlarmTimesSection] supaya tiap layar
 * bisa menaruhnya di kartunya sendiri tanpa dua sumber yang saling menimpa.)
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun MetadataEditor(
    metadata: Metadata,
    isDark: Boolean,
    onChange: (Metadata) -> Unit
) {
    val fieldColors = editorFieldColors(isDark)
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
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            NoteType.entries.forEach { t ->
                EditorChip(
                    label = t.label,
                    selected = t == metadata.type,
                    isDark = isDark,
                    onClick = { onChange(metadata.copy(type = t)) }
                )
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
                    DateTimeField(
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

        // ----- Kegiatan lain (extraSchedules) -----
        Spacer(Modifier.height(10.dp))
        Text("Kegiatan lain", style = MaterialTheme.typography.labelSmall,
            color = if (isDark) Lavender400 else Gray600)
        Spacer(Modifier.height(4.dp))
        val schedules = metadata.extraSchedules.orEmpty()
        schedules.forEachIndexed { i, sch ->
            ExtraScheduleCard(
                schedule = sch,
                index = i,
                isDark = isDark,
                fieldShape = fieldShape,
                fieldColors = fieldColors,
                onChange = { new ->
                    onChange(metadata.copy(
                        extraSchedules = schedules.toMutableList().also { it[i] = new }
                    ))
                },
                onDelete = {
                    onChange(metadata.copy(
                        extraSchedules = schedules.toMutableList().also { it.removeAt(i) }
                    ))
                }
            )
            Spacer(Modifier.height(6.dp))
        }
        GlassButton(
            text = "Tambah kegiatan",
            icon = Icons.Outlined.Add,
            onClick = {
                onChange(metadata.copy(extraSchedules = schedules + ExtraSchedule(type = "event")))
            },
            modifier = Modifier.fillMaxWidth()
        )

        // ----- Transaksi -----
        Spacer(Modifier.height(10.dp))
        Text("Transaksi", style = MaterialTheme.typography.labelSmall,
            color = if (isDark) Lavender400 else Gray600)
        Spacer(Modifier.height(4.dp))
        val transactions = metadata.transactions.orEmpty()
        transactions.forEachIndexed { i, tx ->
            TransactionCard(
                tx = tx,
                index = i,
                listSize = transactions.size,
                isDark = isDark,
                fieldShape = fieldShape,
                fieldColors = fieldColors,
                onChange = { new ->
                    onChange(metadata.copy(
                        transactions = transactions.toMutableList().also { it[i] = new }
                    ))
                },
                onDelete = {
                    onChange(metadata.copy(
                        transactions = transactions.toMutableList().also { it.removeAt(i) }
                    ))
                }
            )
            Spacer(Modifier.height(6.dp))
        }
        GlassButton(
            text = "Tambah transaksi",
            icon = Icons.Outlined.Add,
            onClick = {
                onChange(metadata.copy(transactions = transactions + Transaction(type = "expense")))
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Satu kartu kegiatan tambahan: judul, jenis, tanggal, jam mulai, alarm keras. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ExtraScheduleCard(
    schedule: ExtraSchedule,
    index: Int,
    isDark: Boolean,
    fieldShape: RoundedCornerShape,
    fieldColors: TextFieldColors,
    onChange: (ExtraSchedule) -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isDark) GlassDark else GlassLight)
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = schedule.title ?: "",
                onValueChange = { onChange(schedule.copy(title = it.ifBlank { null })) },
                label = { Text("Kegiatan ${index + 1}") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = fieldShape,
                colors = fieldColors
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.DeleteOutline, "Hapus kegiatan", tint = Rose600,
                    modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SCHEDULE_TYPES.forEach { (value, label) ->
                EditorChip(
                    label = label,
                    selected = schedule.type.equals(value, ignoreCase = true),
                    isDark = isDark,
                    onClick = { onChange(schedule.copy(type = value)) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        MultiDateField(
            label = "Tanggal",
            dates = schedule.dates.orEmpty(),
            onChange = { onChange(schedule.copy(dates = it.ifEmpty { null })) },
            isDark = isDark
        )

        Spacer(Modifier.height(8.dp))
        TimeField(
            label = "Jam mulai",
            value = schedule.startTime,
            onChange = { onChange(schedule.copy(startTime = it)) },
            isDark = isDark,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Alarm keras", style = MaterialTheme.typography.bodyMedium,
                color = if (isDark) Lavender50 else Lavender800)
            Switch(checked = schedule.useAlarm, onCheckedChange = { onChange(schedule.copy(useAlarm = it)) })
        }
    }
}

/** Satu kartu transaksi: field inti selalu tampil, sisanya dilipat "Detail lainnya". */
@Composable
private fun TransactionCard(
    tx: Transaction,
    index: Int,
    listSize: Int,
    isDark: Boolean,
    fieldShape: RoundedCornerShape,
    fieldColors: TextFieldColors,
    onChange: (Transaction) -> Unit,
    onDelete: () -> Unit
) {
    // Teks lokal nominal/qty: di-reset saat jumlah kartu berubah (tambah/hapus) supaya
    // sinkron lagi dengan data; selama mengetik listSize tetap → kursor tidak lompat.
    var amountText by remember(listSize) { mutableStateOf(if (tx.amount == 0.0) "" else AmountParse.format(tx.amount)) }
    var qtyText by remember(listSize) { mutableStateOf(tx.quantity?.let { AmountParse.format(it) } ?: "") }
    var expanded by remember { mutableStateOf(false) }
    val amountError = amountText.isNotBlank() && AmountParse.parse(amountText) == null
    val qtyError = qtyText.isNotBlank() && AmountParse.parse(qtyText) == null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isDark) GlassDark else GlassLight)
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = tx.item ?: "",
                onValueChange = { onChange(tx.copy(item = it.ifBlank { null })) },
                label = { Text("Item ${index + 1}") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = fieldShape,
                colors = fieldColors
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.DeleteOutline, "Hapus transaksi", tint = Rose600,
                    modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            EditorChip(
                label = "Keluar",
                selected = !tx.type.equals("income", ignoreCase = true),
                isDark = isDark,
                onClick = { onChange(tx.copy(type = "expense")) }
            )
            EditorChip(
                label = "Masuk",
                selected = tx.type.equals("income", ignoreCase = true),
                isDark = isDark,
                onClick = { onChange(tx.copy(type = "income")) }
            )
        }

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = amountText,
            onValueChange = { v ->
                amountText = v
                // Nilai tak valid TIDAK menimpa data — hanya field merah
                AmountParse.parse(v)?.let { onChange(tx.copy(amount = it)) }
            },
            label = { Text("Nominal") },
            isError = amountError,
            supportingText = if (amountError) ({ Text("Angka tidak valid", color = Rose600) }) else null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            shape = fieldShape,
            colors = fieldColors
        )

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = tx.category ?: "",
                onValueChange = { onChange(tx.copy(category = it.ifBlank { null })) },
                label = { Text("Kategori") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = fieldShape,
                colors = fieldColors
            )
            DateField(
                label = "Tanggal",
                value = tx.date,
                onChange = { onChange(tx.copy(date = it)) },
                isDark = isDark,
                modifier = Modifier.weight(1f)
            )
        }

        // ----- Detail lainnya (dilipat) -----
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                null, modifier = Modifier.size(16.dp),
                tint = if (isDark) Lavender200 else Lavender600
            )
            Text("Detail lainnya", style = MaterialTheme.typography.labelSmall,
                color = if (isDark) Lavender200 else Lavender600)
        }

        if (expanded) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { v ->
                        qtyText = v
                        if (v.isBlank()) onChange(tx.copy(quantity = null))
                        else AmountParse.parse(v)?.let { onChange(tx.copy(quantity = it)) }
                    },
                    label = { Text("Qty") },
                    isError = qtyError,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    shape = fieldShape,
                    colors = fieldColors
                )
                OutlinedTextField(
                    value = tx.unit ?: "",
                    onValueChange = { onChange(tx.copy(unit = it.ifBlank { null })) },
                    label = { Text("Satuan") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = fieldShape,
                    colors = fieldColors
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = tx.currency ?: "",
                    onValueChange = { onChange(tx.copy(currency = it.ifBlank { null })) },
                    label = { Text("Mata uang") },
                    placeholder = { Text("IDR") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = fieldShape,
                    colors = fieldColors
                )
                OutlinedTextField(
                    value = tx.paymentMethod ?: "",
                    onValueChange = { onChange(tx.copy(paymentMethod = it.ifBlank { null })) },
                    label = { Text("Metode bayar") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = fieldShape,
                    colors = fieldColors
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = tx.merchant ?: "",
                    onValueChange = { onChange(tx.copy(merchant = it.ifBlank { null })) },
                    label = { Text("Merchant") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = fieldShape,
                    colors = fieldColors
                )
                OutlinedTextField(
                    value = tx.person ?: "",
                    onValueChange = { onChange(tx.copy(person = it.ifBlank { null })) },
                    label = { Text("Orang") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = fieldShape,
                    colors = fieldColors
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = tx.notes ?: "",
                onValueChange = { onChange(tx.copy(notes = it.ifBlank { null })) },
                label = { Text("Catatan") },
                modifier = Modifier.fillMaxWidth(),
                shape = fieldShape,
                colors = fieldColors
            )
        }
    }
}

/**
 * Seksi "Waktu alarm" (alarmTimes) — switch + daftar DateTimeField multi.
 * Dipakai PreviewScreen (kartu pengaturan manual) dan NoteDetailScreen (mode edit).
 */
@Composable
fun AlarmTimesSection(
    times: List<String>,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onTimesChange: (List<String>) -> Unit,
    isDark: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Waktu alarm", style = MaterialTheme.typography.bodyMedium,
                color = if (isDark) Lavender50 else Lavender800)
            Text("Tiap waktu menjadi alarm keras (mis. pengingat persiapan)",
                style = MaterialTheme.typography.labelSmall,
                color = if (isDark) Lavender400 else Gray600)
        }
        Switch(
            checked = enabled,
            onCheckedChange = {
                onEnabledChange(it)
                if (it && times.isEmpty()) onTimesChange(listOf(defaultAlarmTime()))
            }
        )
    }
    if (enabled) {
        times.forEachIndexed { i, t ->
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                DateTimeField(
                    label = "Alarm ${i + 1}",
                    value = t,
                    onChange = { new ->
                        val list = times.toMutableList()
                        if (new == null) list.removeAt(i) else list[i] = new
                        onTimesChange(list)
                    },
                    isDark = isDark,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    onTimesChange(times.toMutableList().also { it.removeAt(i) })
                }) {
                    Icon(Icons.Outlined.DeleteOutline, "Hapus waktu alarm",
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
            text = "Tambah waktu alarm",
            icon = Icons.Outlined.Add,
            onClick = { onTimesChange(times + defaultAlarmTime()) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Waktu alarm default saat user menambah manual: 1 jam dari sekarang. */
fun defaultAlarmTime(): String =
    java.time.LocalDateTime.now().plusHours(1)
        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"))

/** Chip pilih-satu bergaya glass (jenis catatan, jenis kegiatan, arah transaksi). */
@Composable
private fun EditorChip(
    label: String,
    selected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) (if (isDark) Lavender600.copy(0.4f) else Lavender100)
                else (if (isDark) GlassDark else GlassLight)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) (if (isDark) Lavender200 else Lavender600)
                    else (if (isDark) Lavender400 else Gray600)
        )
    }
}

@Composable
private fun editorFieldColors(isDark: Boolean) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Lavender400,
    unfocusedBorderColor = if (isDark) GlassBorderDark else Lavender200,
    focusedContainerColor = if (isDark) GlassDark else GlassLight,
    unfocusedContainerColor = if (isDark) GlassDark else GlassLight,
    focusedTextColor = if (isDark) Lavender50 else Lavender800,
    unfocusedTextColor = if (isDark) Lavender50 else Lavender800,
    focusedLabelColor = Lavender600,
    unfocusedLabelColor = if (isDark) Lavender400 else Gray400
)

private val SCHEDULE_TYPES = listOf(
    "meeting" to "Meeting",
    "task" to "Tugas",
    "event" to "Acara",
    "reminder" to "Pengingat"
)
