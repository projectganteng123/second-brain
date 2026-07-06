package com.secondbrain.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secondbrain.app.data.model.TransactionRef
import com.secondbrain.app.data.repository.NoteRepository
import com.secondbrain.app.ui.components.*
import com.secondbrain.app.ui.theme.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.Locale

private enum class TxTypeFilter(val label: String) { ALL("Semua"), INCOME("Masuk"), EXPENSE("Keluar") }
private enum class TxSort(val label: String) {
    DATE_DESC("Tanggal terbaru"), DATE_ASC("Tanggal terlama"),
    AMOUNT_DESC("Nominal terbesar"), AMOUNT_ASC("Nominal terkecil")
}

private val PIE_COLORS = listOf(
    Lavender400, Mint600, Sky600, Peach600, Rose600, Lemon600,
    Lavender600, Sky200, Peach200, Rose200, Mint200, Lemon200
)

private fun TransactionRef.isIncome() = tx.type.equals("income", ignoreCase = true)
private fun TransactionRef.signedAmount() = if (isIncome()) tx.amount else -tx.amount
private fun TransactionRef.category() = tx.category?.takeIf { it.isNotBlank() } ?: "Lainnya"

private fun formatIdr(amount: Double): String =
    "Rp" + java.text.NumberFormat.getNumberInstance(Locale("in", "ID")).format(amount.toLong())

/** Bentuk singkat untuk sel kalender: 950, 12rb, 1,5jt. */
private fun formatShortIdr(amount: Double): String {
    val a = kotlin.math.abs(amount)
    return when {
        a >= 1_000_000 -> {
            val juta = a / 1_000_000
            if (juta % 1.0 < 0.05) "${juta.toInt()}jt"
            else String.format(Locale("in", "ID"), "%.1fjt", juta)
        }
        a >= 1_000 -> "${(a / 1_000).toInt()}rb"
        else -> a.toInt().toString()
    }
}

@Composable
fun FinanceScreen(
    repo: NoteRepository,
    onBack: () -> Unit,
    onNoteClick: (Long) -> Unit
) {
    val isDark = isSystemDark()
    val notes by repo.getAllActive().collectAsState(initial = emptyList())
    var range by remember { mutableStateOf(TimeRange.of(RangePreset.MONTH)) }

    val allTx = remember(notes) { repo.allTransactions(notes) }
    val inRange = remember(allTx, range) { allTx.filter { range.contains(it.date) } }

    var typeFilter by remember { mutableStateOf(TxTypeFilter.ALL) }
    var categoryFilter by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf(TxSort.DATE_DESC) }

    val tableTx = remember(inRange, typeFilter, categoryFilter, sort) {
        inRange
            .filter {
                when (typeFilter) {
                    TxTypeFilter.ALL -> true
                    TxTypeFilter.INCOME -> it.isIncome()
                    TxTypeFilter.EXPENSE -> !it.isIncome()
                }
            }
            .filter { categoryFilter == null || it.category() == categoryFilter }
            .sortedWith(
                when (sort) {
                    TxSort.DATE_DESC -> compareByDescending { it.date }
                    TxSort.DATE_ASC -> compareBy { it.date }
                    TxSort.AMOUNT_DESC -> compareByDescending { it.tx.amount }
                    TxSort.AMOUNT_ASC -> compareBy { it.tx.amount }
                }
            )
    }

    val bgColor = if (isDark) Lavender900 else Gray50

    Scaffold(containerColor = bgColor) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
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
                        Text("Keuangan", style = MaterialTheme.typography.titleMedium,
                            color = if (isDark) Lavender50 else Lavender800)
                    }
                    TimeRangeSelector(range, { range = it }, isDark)
                }
            }

            // ----- Line chart saldo -----
            if (allTx.isNotEmpty()) {
                item { BalanceLineChart(allTx, range, isDark) }
            }

            if (inRange.isEmpty()) {
                item {
                    GlassCard {
                        Text(
                            "Tidak ada transaksi pada rentang waktu ini. Catat pengeluaran/pemasukan " +
                            "lewat catatan biasa — AI akan mengekstraknya otomatis.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isDark) Lavender400 else Gray600
                        )
                    }
                }
            } else {
                // ----- Pie chart per kategori -----
                item { PieCard("semua transaksi per kategori", inRange, isDark) }
                val income = inRange.filter { it.isIncome() }
                if (income.isNotEmpty()) item { PieCard("pemasukan per kategori", income, isDark) }
                val expense = inRange.filter { !it.isIncome() }
                if (expense.isNotEmpty()) item { PieCard("pengeluaran per kategori", expense, isDark) }
            }

            // ----- Kalender keuangan -----
            item { FinanceCalendar(allTx, inRange, range, isDark) }

            // ----- Tabel transaksi -----
            item {
                Column {
                    SectionLabel("transaksi (${tableTx.size})")
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TxTypeFilter.entries.forEach { f ->
                            FilterChip(
                                selected = typeFilter == f,
                                onClick = { typeFilter = f },
                                label = { Text(f.label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        CategoryPicker(
                            categories = inRange.map { it.category() }.distinct().sorted(),
                            selected = categoryFilter,
                            onSelect = { categoryFilter = it },
                            isDark = isDark
                        )
                        SortPicker(sort, { sort = it }, isDark)
                    }
                }
            }

            items(tableTx) { ref ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDark) GlassDark else GlassLight)
                        .clickable { onNoteClick(ref.noteId) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            ref.tx.item?.takeIf { it.isNotBlank() } ?: ref.category(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isDark) Lavender50 else Lavender800
                        )
                        Text(
                            "${ref.date} · ${ref.category()} · ${ref.noteTitle.take(28)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDark) Lavender400 else Gray400,
                            maxLines = 1
                        )
                    }
                    val signed = ref.signedAmount()
                    Text(
                        (if (signed >= 0) "+" else "-") + formatIdr(kotlin.math.abs(signed)),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (signed >= 0) Mint600 else Rose600
                    )
                }
            }

            item { Spacer(Modifier.height(60.dp)) }
        }
    }
}

// ---------- Line chart saldo ----------

/**
 * Saldo berjalan sepanjang rentang terpilih: titik per hari, naik oleh pemasukan dan
 * turun oleh pengeluaran. Saldo awal = kumulatif semua transaksi SEBELUM rentang.
 * (Transaksi hanya punya tanggal, tanpa jam — jadi resolusi grafik per hari.)
 */
@Composable
private fun BalanceLineChart(allTx: List<TransactionRef>, range: TimeRange, isDark: Boolean) {
    val days = (ChronoUnit.DAYS.between(range.from, range.to).toInt() + 1).coerceAtLeast(1)
    val series = remember(allTx, range) {
        val initial = allTx.filter { it.date.isBefore(range.from) }.sumOf { it.signedAmount() }
        val perDay = allTx.filter { range.contains(it.date) }
            .groupBy { it.date }
            .mapValues { (_, list) -> list.sumOf { it.signedAmount() } }
        var run = initial
        listOf(initial) + (0 until days).map { i ->
            run += perDay[range.from.plusDays(i.toLong())] ?: 0.0
            run
        }
    }
    val first = series.first()
    val last = series.last()
    val trendColor = if (last >= first) Mint600 else Rose600
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 9.sp, color = if (isDark) Lavender400 else Gray400)

    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionLabel("saldo")
            Text(
                (if (last >= 0) "" else "-") + formatIdr(kotlin.math.abs(last)),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = trendColor
            )
        }
        Spacer(Modifier.height(6.dp))
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val minV = series.min()
            val maxV = series.max()
            val span = (maxV - minV).takeIf { it > 0 } ?: 1.0
            val padTop = 14f
            val padBottom = 6f
            val h = size.height - padTop - padBottom
            fun x(i: Int) = i.toFloat() * size.width / (series.size - 1).coerceAtLeast(1)
            fun y(v: Double) = padTop + ((1 - (v - minV) / span) * h).toFloat()

            // Garis putus-putus saldo nol (bila 0 berada dalam rentang nilai)
            if (minV < 0 && maxV > 0) {
                drawLine(
                    color = if (isDark) Lavender400.copy(0.4f) else Gray400.copy(0.5f),
                    start = Offset(0f, y(0.0)),
                    end = Offset(size.width, y(0.0)),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                )
            }

            val line = Path().apply {
                series.forEachIndexed { i, v ->
                    if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v))
                }
            }
            // Isian gradasi di bawah garis
            val fill = Path().apply {
                addPath(line)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(fill, Brush.verticalGradient(
                listOf(trendColor.copy(alpha = 0.25f), Color.Transparent),
                startY = 0f, endY = size.height
            ))
            drawPath(line, trendColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))

            // Label nilai maks & min di sisi kiri
            fun shortSigned(v: Double) = (if (v < 0) "-" else "") + formatShortIdr(v)
            drawText(textMeasurer, shortSigned(maxV), topLeft = Offset(2f, 0f), style = labelStyle)
            drawText(
                textMeasurer, shortSigned(minV),
                topLeft = Offset(2f, size.height - 12.sp.toPx()), style = labelStyle
            )
        }
        Spacer(Modifier.height(2.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${range.from}", style = MaterialTheme.typography.labelSmall,
                color = if (isDark) Lavender400 else Gray400)
            Text("${range.to}", style = MaterialTheme.typography.labelSmall,
                color = if (isDark) Lavender400 else Gray400)
        }
    }
}

// ---------- Pie chart ----------

@Composable
private fun PieCard(title: String, txs: List<TransactionRef>, isDark: Boolean) {
    val byCategory = txs.groupBy { it.category() }
        .mapValues { (_, list) -> list.sumOf { it.tx.amount } }
        .toList()
        .sortedByDescending { it.second }
        .filter { it.second > 0 }
    if (byCategory.isEmpty()) return
    val total = byCategory.sumOf { it.second }

    GlassCard {
        SectionLabel(title, modifier = Modifier.padding(bottom = 8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(110.dp)) {
                var start = -90f
                byCategory.forEachIndexed { i, (_, amount) ->
                    val sweep = (amount / total * 360f).toFloat()
                    drawArc(
                        color = PIE_COLORS[i % PIE_COLORS.size],
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = true
                    )
                    start += sweep
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                byCategory.forEachIndexed { i, (cat, amount) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(9.dp).clip(CircleShape).background(PIE_COLORS[i % PIE_COLORS.size]))
                        Text(cat, style = MaterialTheme.typography.labelSmall,
                            color = if (isDark) Lavender50 else Lavender800,
                            maxLines = 1, modifier = Modifier.weight(1f))
                        Text(
                            "${formatIdr(amount)} (${(amount / total * 100).toInt()}%)",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDark) Lavender400 else Gray600
                        )
                    }
                }
            }
        }
    }
}

// ---------- Kalender keuangan ----------

@Composable
private fun FinanceCalendar(
    allTx: List<TransactionRef>,
    inRange: List<TransactionRef>,
    range: TimeRange,
    isDark: Boolean
) {
    var month by remember(range) { mutableStateOf(YearMonth.from(range.from)) }
    val netByDate = remember(allTx) {
        allTx.groupBy { it.date }.mapValues { (_, list) -> list.sumOf { it.signedAmount() } }
    }
    val totalIn = inRange.filter { it.isIncome() }.sumOf { it.tx.amount }
    val totalOut = inRange.filter { !it.isIncome() }.sumOf { it.tx.amount }
    val net = totalIn - totalOut

    GlassCard {
        SectionLabel("kalender keuangan", modifier = Modifier.padding(bottom = 8.dp))

        // Total rentang terpilih — di atas kalender
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TotalCell("Masuk", "+" + formatIdr(totalIn), Mint600, isDark)
            TotalCell("Keluar", "-" + formatIdr(totalOut), Rose600, isDark)
            TotalCell("Total", (if (net >= 0) "+" else "-") + formatIdr(kotlin.math.abs(net)),
                if (net >= 0) Mint600 else Rose600, isDark)
        }

        Spacer(Modifier.height(10.dp))

        // Navigasi bulan
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { month = month.minusMonths(1) }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Outlined.ChevronLeft, "Bulan sebelumnya",
                    modifier = Modifier.size(18.dp), tint = if (isDark) Lavender200 else Lavender600)
            }
            Text(
                month.month.getDisplayName(java.time.format.TextStyle.FULL, Locale("id", "ID")) + " " + month.year,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isDark) Lavender50 else Lavender800,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { month = month.plusMonths(1) }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Outlined.ChevronRight, "Bulan berikutnya",
                    modifier = Modifier.size(18.dp), tint = if (isDark) Lavender200 else Lavender600)
            }
        }

        // Header hari
        Row(Modifier.fillMaxWidth()) {
            listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min").forEach { d ->
                Text(d, style = MaterialTheme.typography.labelSmall,
                    color = if (isDark) Lavender400 else Gray400,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(4.dp))

        // Grid tanggal
        val lead = month.atDay(1).dayOfWeek.value - 1   // Senin = 0
        val days = month.lengthOfMonth()
        val rows = (lead + days + 6) / 7
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val dayNum = r * 7 + c - lead + 1
                    if (dayNum in 1..days) {
                        val date = month.atDay(dayNum)
                        val selected = range.contains(date)
                        val dayNet = netByDate[date]
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(1.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selected) (if (isDark) Lavender600.copy(0.35f) else Lavender100)
                                    else Color.Transparent
                                )
                                .padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "$dayNum",
                                style = MaterialTheme.typography.labelSmall,
                                color = when {
                                    selected -> if (isDark) Lavender50 else Lavender800
                                    else -> if (isDark) Lavender400 else Gray600
                                }
                            )
                            Text(
                                dayNet?.let { (if (it >= 0) "+" else "-") + formatShortIdr(it) } ?: " ",
                                style = MaterialTheme.typography.labelSmall,
                                color = when {
                                    dayNet == null -> Color.Transparent
                                    dayNet >= 0 -> Mint600
                                    else -> Rose600
                                },
                                maxLines = 1
                            )
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun TotalCell(label: String, value: String, color: Color, isDark: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (isDark) Lavender400 else Gray400)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = color)
    }
}

// ---------- Filter & sort picker ----------

@Composable
private fun CategoryPicker(
    categories: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    isDark: Boolean
) {
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
            Icon(Icons.Outlined.Category, null, modifier = Modifier.size(14.dp),
                tint = if (isDark) Lavender200 else Lavender600)
            if (selected != null) {
                Spacer(Modifier.width(4.dp))
                Text(selected.take(10), style = MaterialTheme.typography.labelSmall,
                    color = if (isDark) Lavender200 else Lavender600)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Semua kategori", style = MaterialTheme.typography.bodySmall) },
                onClick = { onSelect(null); open = false }
            )
            categories.forEach { cat ->
                DropdownMenuItem(
                    text = { Text(cat, style = MaterialTheme.typography.bodySmall) },
                    onClick = { onSelect(cat); open = false }
                )
            }
        }
    }
}

@Composable
private fun SortPicker(sort: TxSort, onSelect: (TxSort) -> Unit, isDark: Boolean) {
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
            TxSort.entries.forEach { s ->
                DropdownMenuItem(
                    text = {
                        Text(
                            if (s == sort) "✓ ${s.label}" else s.label,
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    onClick = { onSelect(s); open = false }
                )
            }
        }
    }
}
