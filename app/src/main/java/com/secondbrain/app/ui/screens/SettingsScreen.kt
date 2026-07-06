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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.secondbrain.app.ai.AIProviderType
import com.secondbrain.app.ai.ExtractionKind
import com.secondbrain.app.ai.PromptTemplates
import com.secondbrain.app.capture.SttSession
import com.secondbrain.app.capture.VoiceTriggerController
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

    var groqKeys by remember { mutableStateOf(prefs.getApiKeyText(AIProviderType.GROQ)) }
    var cerebrasKeys by remember { mutableStateOf(prefs.getApiKeyText(AIProviderType.CEREBRAS)) }
    var geminiKeys by remember { mutableStateOf(prefs.getApiKeyText(AIProviderType.GEMINI)) }
    var groqEnabled by remember { mutableStateOf(prefs.isProviderEnabled(AIProviderType.GROQ)) }
    var cerebrasEnabled by remember { mutableStateOf(prefs.isProviderEnabled(AIProviderType.CEREBRAS)) }
    var geminiEnabled by remember { mutableStateOf(prefs.isProviderEnabled(AIProviderType.GEMINI)) }
    var saved by remember { mutableStateOf(false) }

    var alarmDefaultOn by remember { mutableStateOf(prefs.isEventAlarmDefaultOn()) }
    var alarmOffsetMin by remember { mutableIntStateOf(prefs.getAlarmOffsetMinutes()) }

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
    val importJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val json = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.readText()
                    ?: throw RuntimeException("File tidak bisa dibaca")
                val count = repo.importJson(json, prefs.getAlarmOffsetMinutes())
                runCatching { com.secondbrain.app.notification.ReminderScheduler.scheduleUpcoming(context) }
                count
            }.onSuccess { snackbar.showSnackbar("Berhasil impor $it catatan") }
             .onFailure { snackbar.showSnackbar("Gagal impor: ${it.message}") }
        }
    }

    // ----- Kata pemicu suara -----
    var vtEnabled by remember { mutableStateOf(prefs.isVoiceTriggerEnabled()) }
    var vtWord by remember { mutableStateOf(prefs.getVoiceTriggerWord()) }
    var vtPlaceholder by remember { mutableStateOf(prefs.getVoiceTriggerPlaceholder()) }
    var vtSaved by remember { mutableStateOf(false) }
    var vtError by remember { mutableStateOf<String?>(null) }
    var vtTesting by remember { mutableStateOf(false) }
    var vtTestTranscript by remember { mutableStateOf<String?>(null) }
    var vtTestDetected by remember { mutableStateOf<Boolean?>(null) }
    val vtSession = remember { SttSession(context) }
    DisposableEffect(Unit) { onDispose { vtSession.destroy() } }

    fun startTriggerTest() {
        vtTesting = true
        vtTestTranscript = ""
        vtTestDetected = null
        vtSession.start(
            preferOffline = true,
            onPhase = { phase ->
                if (phase is SttSession.Phase.Listening) vtTestTranscript = phase.partial
            },
            onFinal = { text ->
                vtTesting = false
                vtTestTranscript = text
                vtTestDetected = VoiceTriggerController.matchTrigger(text, vtWord) != null
            }
        )
    }

    val micPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) startTriggerTest() }

    fun requestTriggerTest() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) startTriggerTest()
        else micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

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
                SectionLabel("API provider AI", modifier = Modifier.padding(bottom = 8.dp))
                Text(
                    "Isi API key minimal satu provider (semuanya punya paket gratis). " +
                    "Boleh LEBIH DARI SATU key per provider — tulis satu key per baris; jika satu key " +
                    "kena limit, app otomatis mencoba key berikutnya. Centang provider yang ingin dipakai. " +
                    "Jika lebih dari satu dicentang, urutan prioritas: Groq → Cerebras → Gemini.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) Lavender400 else Gray600
                )
                Spacer(Modifier.height(6.dp))
                ProviderKeySection(
                    name = "Groq",
                    enabled = groqEnabled,
                    onEnabledChange = {
                        groqEnabled = it
                        prefs.setProviderEnabled(AIProviderType.GROQ, it)
                    },
                    keyText = groqKeys,
                    onKeyChange = { groqKeys = it; saved = false },
                    placeholder = "gsk_...",
                    linkUrl = "https://console.groq.com/keys",
                    isDark = isDark
                )
                Spacer(Modifier.height(10.dp))
                ProviderKeySection(
                    name = "Cerebras",
                    enabled = cerebrasEnabled,
                    onEnabledChange = {
                        cerebrasEnabled = it
                        prefs.setProviderEnabled(AIProviderType.CEREBRAS, it)
                    },
                    keyText = cerebrasKeys,
                    onKeyChange = { cerebrasKeys = it; saved = false },
                    placeholder = "csk-...",
                    linkUrl = "https://cloud.cerebras.ai",
                    isDark = isDark
                )
                Spacer(Modifier.height(10.dp))
                ProviderKeySection(
                    name = "Gemini",
                    enabled = geminiEnabled,
                    onEnabledChange = {
                        geminiEnabled = it
                        prefs.setProviderEnabled(AIProviderType.GEMINI, it)
                    },
                    keyText = geminiKeys,
                    onKeyChange = { geminiKeys = it; saved = false },
                    placeholder = "AIzaSy...",
                    linkUrl = "https://aistudio.google.com/apikey",
                    isDark = isDark
                )
                Spacer(Modifier.height(10.dp))
                GlassButton(
                    text = if (saved) "Tersimpan" else "Simpan API Key",
                    icon = if (saved) Icons.Outlined.CheckCircle else Icons.Outlined.Save,
                    onClick = {
                        prefs.saveApiKeyText(AIProviderType.GROQ, groqKeys.trim())
                        prefs.saveApiKeyText(AIProviderType.CEREBRAS, cerebrasKeys.trim())
                        prefs.saveApiKeyText(AIProviderType.GEMINI, geminiKeys.trim())
                        saved = true
                    },
                    accent = !saved,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(12.dp))

            // ----- Model AI (otomatis) -----
            GlassCard {
                SectionLabel("model AI", modifier = Modifier.padding(bottom = 8.dp))
                Text(
                    "Model dipilih otomatis & gratis. Ekstraksi memakai model ringan; menjawab pertanyaan " +
                    "memakai model lebih kuat. Jika satu model/provider kena limit, app otomatis pindah ke " +
                    "yang berikutnya.\n" +
                    "Groq: " + PrefsManager.GROQ_MODEL_LADDER.joinToString(" → ") + "\n" +
                    "Cerebras: " + PrefsManager.CEREBRAS_MODEL_LADDER.joinToString(" → ") + "\n" +
                    "Gemini: " + PrefsManager.GEMINI_MODEL_LADDER.joinToString(" → "),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) Lavender400 else Gray600
                )
            }

            Spacer(Modifier.height(12.dp))

            // ----- Alarm acara -----
            GlassCard {
                SectionLabel("alarm acara", modifier = Modifier.padding(bottom = 8.dp))
                Text(
                    "Saat acara dimulai selalu ada NOTIFIKASI informasi (dengan kata semangat). " +
                    "ALARM keras berbunyi beberapa menit sebelum acara — hanya bila toggle Alarm " +
                    "aktif di layar konfirmasi. Atur default toggle & jaraknya di sini.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) Lavender400 else Gray600
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Alarm aktif secara default",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDark) Lavender50 else Lavender800,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = alarmDefaultOn,
                        onCheckedChange = {
                            alarmDefaultOn = it
                            prefs.setEventAlarmDefaultOn(it)
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = Lavender600)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Alarm berbunyi … sebelum acara",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDark) Lavender400 else Gray600
                )
                Spacer(Modifier.height(6.dp))
                FlowChips(
                    options = PrefsManager.ALARM_OFFSET_OPTIONS.map { if (it == 0) "Saat mulai" else "$it mnt" },
                    selected = if (alarmOffsetMin == 0) "Saat mulai" else "$alarmOffsetMin mnt",
                    isDark = isDark,
                    onSelect = { label ->
                        val m = if (label == "Saat mulai") 0 else label.substringBefore(" ").toIntOrNull() ?: 15
                        alarmOffsetMin = m
                        prefs.saveAlarmOffsetMinutes(m)
                        scope.launch {
                            snackbar.showSnackbar(
                                if (m == 0) "Alarm tepat saat acara mulai" else "Alarm $m menit sebelum acara"
                            )
                        }
                    }
                )
            }

            Spacer(Modifier.height(12.dp))

            // ----- Kata pemicu suara -----
            GlassCard {
                SectionLabel("kata pemicu suara", modifier = Modifier.padding(bottom = 8.dp))
                Text(
                    "Saat layar Catatan baru terbuka, app mendengarkan (mic hanya aktif selama layar " +
                    "itu terbuka). Ucapan biasa diabaikan; kalimat yang DIAWALI kata pemicu langsung " +
                    "ditulis ke catatan — mis. \"${vtWord.ifBlank { "Jarvis" }}, ingatkan saya 10 menit lagi\". " +
                    "Setelah satu kalimat tertangkap, mendengarkan BERHENTI (menghindari bunyi \"ding\" " +
                    "berulang) — tambah kalimat lewat tombol mic, atau ketuk \"Dengar lagi\" untuk memakai " +
                    "kata pemicu lagi. Catatan: selama MENUNGGU kata pemicu, sebagian HP tetap berbunyi " +
                    "\"ding\" tiap beberapa detik dan baterai lebih boros.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) Lavender400 else Gray600
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Aktifkan kata pemicu",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDark) Lavender50 else Lavender800,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = vtEnabled,
                        onCheckedChange = {
                            vtEnabled = it
                            prefs.setVoiceTriggerEnabled(it)
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = Lavender600)
                    )
                }
                if (vtEnabled) {
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = vtWord,
                        onValueChange = { vtWord = it; vtSaved = false; vtError = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Kata pemicu") },
                        placeholder = { Text(PrefsManager.DEFAULT_VOICE_TRIGGER_WORD) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = settingsFieldColors(isDark)
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = vtPlaceholder,
                        onValueChange = { vtPlaceholder = it; vtSaved = false },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Teks pengganti kata pemicu (opsional)") },
                        placeholder = { Text("mis. AI,") },
                        supportingText = {
                            Text(
                                "Kosongkan agar kata pemicu dibuang. Diisi \"AI,\" → tersimpan \"AI, ingatkan saya…\".",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isDark) Lavender400 else Gray400
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = settingsFieldColors(isDark)
                    )
                    vtError?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = Rose600)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassButton(
                            text = if (vtSaved) "Tersimpan" else "Simpan",
                            icon = if (vtSaved) Icons.Outlined.CheckCircle else Icons.Outlined.Save,
                            onClick = {
                                if (vtWord.isBlank()) {
                                    vtError = "Kata pemicu tidak boleh kosong."
                                } else {
                                    prefs.saveVoiceTriggerWord(vtWord.trim())
                                    prefs.saveVoiceTriggerPlaceholder(vtPlaceholder.trim())
                                    vtSaved = true
                                    vtError = null
                                }
                            },
                            accent = !vtSaved,
                            modifier = Modifier.weight(1f)
                        )
                        GlassButton(
                            text = if (vtTesting) "Bicara…" else "Uji kata pemicu",
                            icon = Icons.Outlined.Mic,
                            onClick = { if (!vtTesting) requestTriggerTest() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // Hasil uji: transkrip mentah + status terdeteksi, supaya user tahu
                    // kata pilihannya konsisten dikenali STT sebelum diandalkan.
                    if (vtTesting || vtTestTranscript != null) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isDark) GlassDark else GlassLight, RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            when {
                                vtTesting -> Icon(Icons.Outlined.Mic, null, modifier = Modifier.size(16.dp), tint = Peach600)
                                vtTestDetected == true -> Icon(Icons.Outlined.CheckCircle, null, modifier = Modifier.size(16.dp), tint = Sky600)
                                else -> Icon(Icons.Outlined.Cancel, null, modifier = Modifier.size(16.dp), tint = Rose600)
                            }
                            Column {
                                Text(
                                    when {
                                        vtTesting -> "Ucapkan: \"${vtWord.ifBlank { "kata pemicu" }}, …\""
                                        vtTestDetected == true -> "Terdeteksi!"
                                        else -> "Tidak terdeteksi — coba kata lain yang lebih mudah dikenali."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isDark) Lavender50 else Lavender800
                                )
                                val heard = vtTestTranscript.orEmpty()
                                if (heard.isNotBlank()) {
                                    Text(
                                        "Terdengar: \"$heard\"",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (isDark) Lavender400 else Gray600
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ----- Cadangkan & pulihkan -----
            GlassCard {
                SectionLabel("cadangkan & pulihkan", modifier = Modifier.padding(bottom = 8.dp))
                Text(
                    "Ekspor menyimpan semua catatan ke file — di jendela penyimpanan, pilih Google Drive " +
                    "agar cadangan langsung tersimpan di Drive. Impor mengembalikan catatan dari file " +
                    "cadangan JSON (catatan dengan ID sama akan ditimpa, sisanya digabung). " +
                    "Selain itu, Android mencadangkan database & pengaturan secara otomatis ke Google Drive " +
                    "(Auto Backup) dan memulihkannya saat app di-install ulang di akun yang sama.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) Lavender400 else Gray600
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton(
                        text = "Ekspor JSON",
                        icon = Icons.Outlined.CloudUpload,
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
                Spacer(Modifier.height(8.dp))
                GlassButton(
                    text = "Impor JSON (pulihkan cadangan)",
                    icon = Icons.Outlined.CloudDownload,
                    onClick = { importJsonLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/*")) },
                    modifier = Modifier.fillMaxWidth()
                )
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
                        "Tiap catatan diproses TIGA prompt paralel — Universal (info umum + action items), " +
                        "Keuangan (transaksi), Acara (jadwal + pengingat/alarm) — lalu hasilnya digabung. " +
                        "PERINGATAN: mengubah prompt bisa membuat ekstraksi GAGAL jika AI tidak lagi " +
                        "mengembalikan JSON dengan field yang dibutuhkan app. Wajib pertahankan placeholder " +
                        "{note} (semua prompt) dan {now} (prompt Acara), serta struktur JSON-nya.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDark) Lemon200 else Lemon800
                    )
                }

                PromptEditor(ExtractionKind.UNIVERSAL, prefs, isDark)
                PromptEditor(ExtractionKind.FINANCE, prefs, isDark)
                PromptEditor(ExtractionKind.SCHEDULE, prefs, isDark)
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

@Composable
private fun ProviderKeySection(
    name: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    keyText: String,
    onKeyChange: (String) -> Unit,
    placeholder: String,
    linkUrl: String,
    isDark: Boolean
) {
    val uriHandler = LocalUriHandler.current
    var showKey by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = enabled,
            onCheckedChange = onEnabledChange,
            colors = CheckboxDefaults.colors(
                checkedColor = Lavender600,
                uncheckedColor = if (isDark) Lavender400 else Gray400
            )
        )
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isDark) Lavender50 else Lavender800,
            modifier = Modifier.weight(1f)
        )
        Text(
            "Ambil API key",
            style = MaterialTheme.typography.labelSmall,
            color = if (isDark) Lavender200 else Lavender600,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier
                .clickable { uriHandler.openUri(linkUrl) }
                .padding(vertical = 6.dp)
        )
    }
    OutlinedTextField(
        value = keyText,
        onValueChange = onKeyChange,
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
        label = { Text("$name API Key (satu per baris)") },
        placeholder = { Text(placeholder) },
        singleLine = false,
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

/** Editor satu template prompt ekstraksi, dilipat per jenis agar layar tidak penuh. */
@Composable
private fun PromptEditor(kind: ExtractionKind, prefs: PrefsManager, isDark: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    var text by remember {
        mutableStateOf(prefs.getExtractionPrompt(kind).ifBlank { PromptTemplates.defaultFor(kind) })
    }
    var saved by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Prompt ${kind.label}",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isDark) Lavender50 else Lavender800,
            modifier = Modifier.weight(1f)
        )
        Icon(
            if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            null, modifier = Modifier.size(18.dp),
            tint = if (isDark) Lavender400 else Gray400
        )
    }
    if (expanded) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; saved = false; error = null },
            modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 320.dp),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            shape = RoundedCornerShape(12.dp),
            colors = settingsFieldColors(isDark)
        )
        error?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = Rose600)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassButton(
                text = if (saved) "Tersimpan" else "Simpan",
                icon = if (saved) Icons.Outlined.CheckCircle else Icons.Outlined.Save,
                onClick = {
                    when {
                        !text.contains(PromptTemplates.PLACEHOLDER_NOTE) ->
                            error = "Prompt harus memuat placeholder {note} (teks catatan)."
                        kind == ExtractionKind.SCHEDULE && !text.contains(PromptTemplates.PLACEHOLDER_NOW) ->
                            error = "Prompt Acara harus memuat placeholder {now} (waktu sekarang)."
                        else -> {
                            prefs.saveExtractionPrompt(kind, text.trim())
                            saved = true
                            error = null
                        }
                    }
                },
                accent = !saved,
                modifier = Modifier.weight(1f)
            )
            GlassButton(
                text = "Reset",
                icon = Icons.Outlined.RestartAlt,
                onClick = {
                    prefs.clearExtractionPrompt(kind)
                    text = PromptTemplates.defaultFor(kind)
                    saved = false
                    error = null
                }
            )
        }
    }
}

@Composable
private fun settingsFieldColors(isDark: Boolean) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Lavender400,
    unfocusedBorderColor = if (isDark) GlassBorderDark else Lavender200,
    focusedContainerColor = if (isDark) GlassDark else GlassLight,
    unfocusedContainerColor = if (isDark) GlassDark else GlassLight,
    focusedTextColor = if (isDark) Lavender50 else Lavender800,
    unfocusedTextColor = if (isDark) Lavender50 else Lavender800,
    focusedLabelColor = Lavender600,
    unfocusedLabelColor = if (isDark) Lavender400 else Gray400
)

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
