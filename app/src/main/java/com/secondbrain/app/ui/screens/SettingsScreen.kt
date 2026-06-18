package com.secondbrain.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.secondbrain.app.ai.PromptTemplates
import com.secondbrain.app.data.repository.NoteRepository
import com.secondbrain.app.ui.components.*
import com.secondbrain.app.ui.theme.*
import com.secondbrain.app.util.PrefsManager
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    repo: NoteRepository,
    onBack: () -> Unit,
    onOpenDebug: () -> Unit = {},
    onOpenArchive: () -> Unit = {},
    onOpenActionItems: () -> Unit = {}
) {
    val isDark = isSystemDark()
    val context = LocalContext.current
    val prefs = remember { PrefsManager(context) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var apiKey by remember { mutableStateOf(prefs.getApiKey()) }
    var showKey by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    var selectedModel by remember { mutableStateOf(prefs.getModel()) }
    var offsetHours by remember { mutableIntStateOf(prefs.getReminderOffsetHours()) }

    // Export launchers
    val exportJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val data = repo.exportJson()
                context.contentResolver.openOutputStream(uri)?.use { it.write(data.toByteArray()) }
            }.onSuccess { snackbar.showSnackbar("Berhasil ekspor JSON") }
             .onFailure { snackbar.showSnackbar("Gagal ekspor: ${it.message}") }
        }
    }
    val exportCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val data = repo.exportCsv()
                context.contentResolver.openOutputStream(uri)?.use { it.write(data.toByteArray()) }
            }.onSuccess { snackbar.showSnackbar("Berhasil ekspor CSV") }
             .onFailure { snackbar.showSnackbar("Gagal ekspor: ${it.message}") }
        }
    }

    // Custom prompt: tampilkan default sebagai titik awal bila belum pernah diubah
    var promptText by remember {
        mutableStateOf(prefs.getCustomPrompt().ifBlank { PromptTemplates.DEFAULT_EXTRACTION })
    }
    var promptSaved by remember { mutableStateOf(false) }
    var promptError by remember { mutableStateOf<String?>(null) }

    val bgColor = if (isDark) Lavender900 else Gray50

    Scaffold(
        containerColor = bgColor,
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, "Kembali", tint = if (isDark) Lavender200 else Lavender600)
                }
                Text(
                    "Pengaturan",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isDark) Lavender50 else Lavender800
                )
            }

            Spacer(Modifier.height(16.dp))

            GlassCard {
                SectionLabel("Gemini API", modifier = Modifier.padding(bottom = 8.dp))
                Text(
                    "Dapatkan API key gratis di Google AI Studio (aistudio.google.com).",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) Lavender400 else Gray600
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it; saved = false },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Gemini API Key") },
                    placeholder = { Text("AIzaSy...") },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None
                                          else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                if (showKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                null,
                                tint = if (isDark) Lavender400 else Gray400
                            )
                        }
                    },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
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
                Spacer(Modifier.height(10.dp))
                GlassButton(
                    text = if (saved) "Tersimpan" else "Simpan API Key",
                    icon = if (saved) Icons.Outlined.CheckCircle else Icons.Outlined.Save,
                    onClick = {
                        prefs.saveApiKey(apiKey.trim())
                        saved = true
                    },
                    accent = !saved,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(12.dp))

            // ----- Model AI -----
            GlassCard {
                SectionLabel("model AI", modifier = Modifier.padding(bottom = 8.dp))
                Text(
                    "Pilih model Gemini. Jika satu model kena limit/dihapus dari free tier, ganti ke yang lain.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) Lavender400 else Gray600
                )
                Spacer(Modifier.height(8.dp))
                FlowChips(
                    options = PrefsManager.MODEL_OPTIONS,
                    selected = selectedModel,
                    isDark = isDark,
                    onSelect = {
                        selectedModel = it
                        prefs.saveModel(it)
                        scope.launch { snackbar.showSnackbar("Model: $it") }
                    }
                )
            }

            Spacer(Modifier.height(12.dp))

            // ----- Offset reminder -----
            GlassCard {
                SectionLabel("pengingat", modifier = Modifier.padding(bottom = 8.dp))
                Text(
                    "Ingatkan saya berapa jam sebelum kegiatan dimulai (juga ada pengingat saat kegiatan mulai).",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) Lavender400 else Gray600
                )
                Spacer(Modifier.height(8.dp))
                FlowChips(
                    options = PrefsManager.REMINDER_OFFSET_OPTIONS.map { "$it jam" },
                    selected = "$offsetHours jam",
                    isDark = isDark,
                    onSelect = { label ->
                        val h = label.substringBefore(" ").toIntOrNull() ?: 24
                        offsetHours = h
                        prefs.saveReminderOffsetHours(h)
                        scope.launch { snackbar.showSnackbar("Pengingat $h jam sebelum") }
                    }
                )
            }

            Spacer(Modifier.height(12.dp))

            // ----- Export -----
            GlassCard {
                SectionLabel("ekspor data", modifier = Modifier.padding(bottom = 8.dp))
                Text(
                    "Cadangkan semua catatan ke file. JSON paling lengkap; CSV mudah dibuka di spreadsheet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) Lavender400 else Gray600
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton(
                        text = "Ekspor JSON",
                        icon = Icons.Outlined.Code,
                        onClick = { exportJsonLauncher.launch("secondbrain-backup.json") },
                        modifier = Modifier.weight(1f)
                    )
                    GlassButton(
                        text = "Ekspor CSV",
                        icon = Icons.Outlined.TableChart,
                        onClick = { exportCsvLauncher.launch("secondbrain-export.csv") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ----- Navigasi -----
            GlassCard {
                SectionLabel("lainnya", modifier = Modifier.padding(bottom = 8.dp))
                NavRow(Icons.Outlined.Checklist, "Semua action items", isDark, onOpenActionItems)
                NavRow(Icons.Outlined.Archive, "Arsip", isDark, onOpenArchive)
            }

            Spacer(Modifier.height(12.dp))

            // ----- Custom prompt -----
            GlassCard {
                SectionLabel("prompt ekstraksi (lanjutan)", modifier = Modifier.padding(bottom = 8.dp))

                // Peringatan
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isDark) Lemon600.copy(0.12f) else Lemon50,
                            RoundedCornerShape(10.dp)
                        )
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Outlined.Warning, null, modifier = Modifier.size(16.dp), tint = Lemon600)
                    Text(
                        "PERINGATAN: Mengubah prompt bisa membuat ekstraksi GAGAL jika AI tidak lagi " +
                        "mengembalikan JSON dengan field yang dibutuhkan app. Wajib pertahankan placeholder " +
                        "{now} dan {note}, serta struktur JSON-nya. Ubah hanya jika kamu paham.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDark) Lemon200 else Lemon800
                    )
                }

                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = promptText,
                    onValueChange = { promptText = it; promptSaved = false; promptError = null },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 320.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Lavender400,
                        unfocusedBorderColor = if (isDark) GlassBorderDark else Lavender200,
                        focusedContainerColor = if (isDark) GlassDark else GlassLight,
                        unfocusedContainerColor = if (isDark) GlassDark else GlassLight,
                        focusedTextColor = if (isDark) Lavender50 else Lavender800,
                        unfocusedTextColor = if (isDark) Lavender50 else Lavender800
                    )
                )

                promptError?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Rose600)
                }

                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton(
                        text = if (promptSaved) "Tersimpan" else "Simpan prompt",
                        icon = if (promptSaved) Icons.Outlined.CheckCircle else Icons.Outlined.Save,
                        onClick = {
                            when {
                                !promptText.contains(PromptTemplates.PLACEHOLDER_NOTE) ->
                                    promptError = "Prompt harus memuat placeholder {note} (teks catatan)."
                                !promptText.contains(PromptTemplates.PLACEHOLDER_NOW) ->
                                    promptError = "Prompt harus memuat placeholder {now} (waktu sekarang)."
                                else -> {
                                    prefs.saveCustomPrompt(promptText.trim())
                                    promptSaved = true
                                    promptError = null
                                }
                            }
                        },
                        accent = !promptSaved,
                        modifier = Modifier.weight(1f)
                    )
                    GlassButton(
                        text = "Reset",
                        icon = Icons.Outlined.RestartAlt,
                        onClick = {
                            prefs.clearCustomPrompt()
                            promptText = PromptTemplates.DEFAULT_EXTRACTION
                            promptSaved = false
                        }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            GlassCard {
                SectionLabel("pengembang", modifier = Modifier.padding(bottom = 8.dp))
                Text(
                    "Lihat apa yang dikirim ke AI, respons baliknya, dan operasi simpan/cari di database.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) Lavender400 else Gray600
                )
                Spacer(Modifier.height(10.dp))
                GlassButton(
                    text = "Buka Debug Log",
                    icon = Icons.Outlined.BugReport,
                    onClick = onOpenDebug,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FlowChips(
    options: List<String>,
    selected: String,
    isDark: Boolean,
    onSelect: (String) -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { opt ->
            val sel = opt == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (sel) (if (isDark) Lavender600.copy(0.4f) else Lavender100)
                        else (if (isDark) GlassDark else GlassLight)
                    )
                    .clickable { onSelect(opt) }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text(
                    opt,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (sel) (if (isDark) Lavender200 else Lavender600)
                            else (if (isDark) Lavender400 else Gray600)
                )
            }
        }
    }
}

@Composable
private fun NavRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isDark: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(18.dp), tint = if (isDark) Lavender200 else Lavender600)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = if (isDark) Lavender50 else Lavender800,
            modifier = Modifier.weight(1f))
        Icon(Icons.Outlined.ChevronRight, null, modifier = Modifier.size(18.dp), tint = if (isDark) Lavender400 else Gray400)
    }
}
