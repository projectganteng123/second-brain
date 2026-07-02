package com.secondbrain.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.secondbrain.app.ui.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Field pilih JAM (HH:mm). Ketuk field → dialog jam analog/keypad; tombol Hapus mengosongkan.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(
    label: String,
    value: String?,
    onChange: (String?) -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    var show by remember { mutableStateOf(false) }
    ClickableField(label, value ?: "", Icons.Outlined.Schedule, isDark, modifier) { show = true }

    if (show) {
        val initial = value?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        val state = rememberTimePickerState(
            initialHour = initial?.hour ?: 8,
            initialMinute = initial?.minute ?: 0,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { show = false },
            title = { Text(label, style = MaterialTheme.typography.titleSmall) },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = {
                    onChange("%02d:%02d".format(state.hour, state.minute))
                    show = false
                }) { Text("OK") }
            },
            dismissButton = {
                Row {
                    if (value != null) {
                        TextButton(onClick = { onChange(null); show = false }) { Text("Hapus", color = Rose600) }
                    }
                    TextButton(onClick = { show = false }) { Text("Batal") }
                }
            }
        )
    }
}

/**
 * Field pilih TANGGAL tunggal (yyyy-MM-dd) dengan kalender. nullable = boleh dihapus.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    value: String?,
    onChange: (String?) -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    var show by remember { mutableStateOf(false) }
    ClickableField(label, value ?: "", Icons.Outlined.CalendarMonth, isDark, modifier) { show = true }

    if (show) {
        val state = rememberDatePickerState(initialSelectedDateMillis = dateToUtcMillis(value))
        DatePickerDialog(
            onDismissRequest = { show = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(utcMillisToDate(it)) }
                    show = false
                }) { Text("OK") }
            },
            dismissButton = {
                Row {
                    if (value != null) {
                        TextButton(onClick = { onChange(null); show = false }) { Text("Hapus", color = Rose600) }
                    }
                    TextButton(onClick = { show = false }) { Text("Batal") }
                }
            }
        ) { DatePicker(state) }
    }
}

/**
 * Daftar TANGGAL (multi): chip per tanggal dengan tombol hapus + chip "tambah" membuka kalender.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MultiDateField(
    label: String,
    dates: List<String>,
    onChange: (List<String>) -> Unit,
    isDark: Boolean
) {
    var show by remember { mutableStateOf(false) }

    Text(label, style = MaterialTheme.typography.labelSmall, color = if (isDark) Lavender400 else Gray600)
    Spacer(Modifier.height(4.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        dates.forEach { d ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isDark) Lavender600.copy(0.35f) else Lavender100)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(d, style = MaterialTheme.typography.labelSmall,
                    color = if (isDark) Lavender200 else Lavender600)
                Icon(
                    Icons.Outlined.Close, "Hapus tanggal",
                    modifier = Modifier.size(13.dp).clickable { onChange(dates - d) },
                    tint = if (isDark) Lavender400 else Gray400
                )
            }
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(if (isDark) GlassDark else GlassLight)
                .clickable { show = true }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(Icons.Outlined.Add, null, modifier = Modifier.size(13.dp),
                tint = if (isDark) Lavender200 else Lavender600)
            Text("Tambah tanggal", style = MaterialTheme.typography.labelSmall,
                color = if (isDark) Lavender200 else Lavender600)
        }
    }

    if (show) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { show = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val d = utcMillisToDate(ms)
                        if (d !in dates) onChange((dates + d).sorted())
                    }
                    show = false
                }) { Text("Tambah") }
            },
            dismissButton = { TextButton(onClick = { show = false }) { Text("Batal") } }
        ) { DatePicker(state) }
    }
}

/**
 * Field pilih TANGGAL + JAM (yyyy-MM-ddTHH:mm), dua langkah: kalender lalu jam.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimeField(
    label: String,
    value: String?,
    onChange: (String?) -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var pickedDate by remember { mutableStateOf<String?>(null) }

    val parsed = value?.split("T")
    ClickableField(
        label,
        value?.replace("T", " ") ?: "",
        Icons.Outlined.EditCalendar,
        isDark,
        modifier
    ) { showDate = true }

    if (showDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = dateToUtcMillis(parsed?.getOrNull(0)))
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        pickedDate = utcMillisToDate(it)
                        showTime = true
                    }
                    showDate = false
                }) { Text("Lanjut pilih jam") }
            },
            dismissButton = {
                Row {
                    if (value != null) {
                        TextButton(onClick = { onChange(null); showDate = false }) { Text("Hapus", color = Rose600) }
                    }
                    TextButton(onClick = { showDate = false }) { Text("Batal") }
                }
            }
        ) { DatePicker(state) }
    }

    if (showTime) {
        val initial = parsed?.getOrNull(1)?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        val state = rememberTimePickerState(
            initialHour = initial?.hour ?: 8,
            initialMinute = initial?.minute ?: 0,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTime = false },
            title = { Text("Pilih jam", style = MaterialTheme.typography.titleSmall) },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = {
                    pickedDate?.let { d ->
                        onChange("${d}T%02d:%02d".format(state.hour, state.minute))
                    }
                    showTime = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Batal") } }
        )
    }
}

/** Field "readonly" yang seluruh areanya bisa diketuk untuk membuka picker. */
@Composable
private fun ClickableField(
    label: String,
    text: String,
    trailing: androidx.compose.ui.graphics.vector.ImageVector,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(modifier) {
        OutlinedTextField(
            value = text,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = {
                Icon(trailing, null, modifier = Modifier.size(18.dp),
                    tint = if (isDark) Lavender400 else Gray400)
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Lavender400,
                unfocusedBorderColor = if (isDark) GlassBorderDark else Lavender200,
                focusedContainerColor = if (isDark) GlassDark else GlassLight,
                unfocusedContainerColor = if (isDark) GlassDark else GlassLight,
                focusedTextColor = if (isDark) Lavender50 else Lavender800,
                unfocusedTextColor = if (isDark) Lavender50 else Lavender800,
                focusedLabelColor = Lavender600,
                unfocusedLabelColor = if (isDark) Lavender400 else Gray400
            )
        )
        // Overlay transparan agar seluruh field bisa diketuk (readOnly menelan klik)
        Box(Modifier.matchParentSize().clickable(onClick = onClick))
    }
}

private val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

private fun dateToUtcMillis(date: String?): Long? =
    date?.let { runCatching { LocalDate.parse(it, ISO_DATE) }.getOrNull() }
        ?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()

private fun utcMillisToDate(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().format(ISO_DATE)
