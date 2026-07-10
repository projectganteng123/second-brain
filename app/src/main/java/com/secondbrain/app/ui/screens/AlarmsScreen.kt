package com.secondbrain.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.secondbrain.app.data.model.ReminderEntity
import com.secondbrain.app.data.repository.NoteRepository
import com.secondbrain.app.ui.components.*
import com.secondbrain.app.ui.theme.*
import com.secondbrain.app.util.TimeFormat
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val ISO_DT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

private fun millisToIso(ms: Long): String =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault()).format(ISO_DT)

private fun isoToMillis(s: String): Long? = runCatching {
    LocalDateTime.parse(s, ISO_DT).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}.getOrNull()

/**
 * Halaman kepastian alarm: semua alarm & notifikasi yang AKAN berbunyi, urut waktu.
 * Tiap baris bisa dibuka catatannya, diedit (waktu/jenis/pesan), atau dihapus —
 * perubahan langsung dicabut/didaftarkan ulang ke AlarmManager.
 */
@Composable
fun AlarmsScreen(
    repo: NoteRepository,
    onBack: () -> Unit,
    onNoteClick: (Long) -> Unit
) {
    val isDark = isSystemDark()
    val scope = rememberCoroutineScope()
    val reminders by repo.upcomingReminders().collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<ReminderEntity?>(null) }

    val bgColor = if (isDark) Lavender900 else Gray50

    Scaffold(containerColor = bgColor) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, "Kembali", tint = if (isDark) Lavender200 else Lavender600)
                }
                Text(
                    "Alarm & pengingat",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isDark) Lavender50 else Lavender800
                )
            }

            Spacer(Modifier.height(4.dp))
            Text(
                "Semua yang tampil di sini sudah terdaftar di sistem alarm HP. " +
                "Ketuk baris untuk membuka catatannya.",
                style = MaterialTheme.typography.bodySmall,
                color = if (isDark) Lavender400 else Gray600
            )
            Spacer(Modifier.height(8.dp))
            SectionLabel("${reminders.size} akan berbunyi")
            Spacer(Modifier.height(6.dp))

            if (reminders.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Tidak ada alarm atau pengingat yang menunggu.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDark) Lavender400 else Gray400,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(reminders, key = { it.id }) { r ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isDark) GlassDark else GlassLight)
                                .clickable { onNoteClick(r.noteId) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                if (r.isAlarm) Icons.Outlined.Alarm else Icons.Outlined.NotificationsNone,
                                null, modifier = Modifier.size(20.dp),
                                tint = if (r.isAlarm) Peach600 else Sky600
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    r.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isDark) Lavender50 else Lavender800,
                                    maxLines = 2
                                )
                                Text(
                                    TimeFormat.dateTime(millisToIso(r.remindAt)) +
                                        if (r.isAlarm) " · Alarm keras" else " · Notifikasi",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (r.isAlarm) Peach600 else (if (isDark) Lavender400 else Gray600)
                                )
                            }
                            IconButton(onClick = { editing = r }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Outlined.Edit, "Edit alarm", modifier = Modifier.size(16.dp),
                                    tint = if (isDark) Lavender200 else Lavender600)
                            }
                            IconButton(
                                onClick = { scope.launch { repo.deleteReminder(r.id) } },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Outlined.DeleteOutline, "Hapus alarm",
                                    modifier = Modifier.size(16.dp), tint = Rose600)
                            }
                        }
                    }
                    item { Spacer(Modifier.height(60.dp)) }
                }
            }
        }
    }

    // ----- Dialog edit alarm -----
    editing?.let { r ->
        var timeIso by remember(r.id) { mutableStateOf(millisToIso(r.remindAt)) }
        var loud by remember(r.id) { mutableStateOf(r.isAlarm) }
        var msg by remember(r.id) { mutableStateOf(r.message) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Edit alarm") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = msg,
                        onValueChange = { msg = it },
                        label = { Text("Pesan") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    DateTimeField(
                        label = "Waktu berbunyi",
                        value = timeIso,
                        onChange = { new -> if (new != null) timeIso = new },
                        isDark = isDark,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        TimeFormat.dateTime(timeIso),
                        style = MaterialTheme.typography.labelSmall,
                        color = Mint600
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Alarm keras", style = MaterialTheme.typography.bodyMedium)
                            Text("Mati = notifikasi biasa",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isDark) Lavender400 else Gray600)
                        }
                        Switch(checked = loud, onCheckedChange = { loud = it })
                    }
                    if ((isoToMillis(timeIso) ?: 0L) <= System.currentTimeMillis()) {
                        Text(
                            "Waktu sudah lewat — alarm tidak akan berbunyi.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Lemon600
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val millis = isoToMillis(timeIso)
                    if (millis != null) {
                        scope.launch {
                            repo.updateReminder(r.copy(
                                remindAt = millis,
                                isAlarm = loud,
                                message = msg.ifBlank { r.message }
                            ))
                        }
                        editing = null
                    }
                }) { Text("Simpan") }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) { Text("Batal") }
            }
        )
    }
}
