package com.secondbrain.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.secondbrain.app.capture.*
import com.secondbrain.app.data.model.Attachment
import com.secondbrain.app.ui.components.*
import com.secondbrain.app.ui.theme.*
import com.secondbrain.app.util.AttachmentStore
import com.secondbrain.app.util.MediaReader
import com.secondbrain.app.util.PrefsManager
import com.secondbrain.app.viewmodel.InputUiState
import com.secondbrain.app.viewmodel.InputViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun InputScreen(
    vm: InputViewModel,
    onOpenList: () -> Unit,
    onAsk: () -> Unit,
    onSaved: () -> Unit
) {
    val isDark = isSystemDark()
    val context = LocalContext.current
    val uiState by vm.uiState.collectAsState()

    val prefs = remember { PrefsManager(context) }
    val controller = remember { GuidedCaptureController() }
    val session = remember { SttSession(context) }
    DisposableEffect(Unit) { onDispose { session.destroy() } }

    // Inisialisasi dari teks vm bila kembali ke layar ini
    LaunchedEffect(Unit) {
        val t = vm.rawText.value
        if (t.isNotEmpty() && controller.value.text.isEmpty()) {
            controller.onValueChange(TextFieldValue(t, androidx.compose.ui.text.TextRange(t.length)))
        }
    }

    var recording by remember { mutableStateOf(false) }
    var cuePhase by remember { mutableStateOf<SttSession.Phase>(SttSession.Phase.Preparing) }
    var activeLabel by remember { mutableStateOf<String?>(null) }
    var captureCanceled by remember { mutableStateOf(false) }
    var pendingStart by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingPermStart by remember { mutableStateOf<(() -> Unit)?>(null) }

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) { pendingPermStart?.invoke() }
        pendingPermStart = null
    }

    // ----- Kata pemicu suara (voice trigger) -----
    val voiceTriggerEnabled = remember { prefs.isVoiceTriggerEnabled() }
    var triggerMuted by remember { mutableStateOf(false) }
    // Mic manual hanya membangunkan lagi pemicu yang tadinya masih MENUNGGU —
    // pemicu yang sudah Captured tetap diam (kata pemicu hanya di awal).
    var resumeTriggerAfterManual by remember { mutableStateOf(false) }
    val voiceTrigger = remember {
        VoiceTriggerController(
            context,
            triggerWord = { prefs.getVoiceTriggerWord() },
            placeholderWord = { prefs.getVoiceTriggerPlaceholder() },
            onInsert = { text ->
                controller.appendPlain(text)
                vm.updateText(controller.value.text)
            }
        )
    }

    // Hidup hanya selama layar ini di depan (ON_RESUME..ON_PAUSE); mati saat app ke background.
    val lifecycleOwner = LocalLifecycleOwner.current

    fun maybeResumeVoiceTrigger() {
        if (voiceTriggerEnabled && hasMicPermission && !triggerMuted &&
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        ) voiceTrigger.startListening()
    }

    DisposableEffect(voiceTriggerEnabled, hasMicPermission, triggerMuted) {
        val shouldListen = voiceTriggerEnabled && hasMicPermission && !triggerMuted
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (shouldListen && !recording) voiceTrigger.startListening()
                Lifecycle.Event.ON_PAUSE -> voiceTrigger.stopListening()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (shouldListen && !recording &&
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        ) voiceTrigger.startListening()
        if (!shouldListen) voiceTrigger.stopListening()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            voiceTrigger.stopListening()
        }
    }

    fun finalizeCapture(label: String?, target: ButtonTarget, result: String) {
        recording = false
        controller.setHighlight(null)
        if (!captureCanceled) {
            if (result.isNotBlank()) {
                when (target) {
                    is ButtonTarget.Insert ->
                        if (label != null) controller.insertLabeledBlock(label, result)
                        else controller.appendPlain(result)
                    is ButtonTarget.Replace -> controller.replaceBlock(target.block, label ?: "", result)
                }
            } else if (target is ButtonTarget.Replace) {
                // Selesai dengan hasil kosong saat re-record → blok lama dihapus
                controller.replaceBlock(target.block, label ?: "", "")
            }
        }
        vm.updateText(controller.value.text)
        val queued = pendingStart
        pendingStart = null
        if (queued != null) queued()
        else if (resumeTriggerAfterManual) {
            resumeTriggerAfterManual = false
            maybeResumeVoiceTrigger()
        }
    }

    fun beginCapture(label: String?) {
        resumeTriggerAfterManual = voiceTrigger.state is VoiceTriggerController.State.Waiting ||
            voiceTrigger.state == VoiceTriggerController.State.Continuing
        voiceTrigger.stopListening()   // lepas mic dari mode kata pemicu dulu (serial)
        val target = if (label == null) ButtonTarget.Insert else controller.targetForButton()
        if (target is ButtonTarget.Replace) controller.setHighlight(target.block.start) // Pengaman 2
        activeLabel = label
        captureCanceled = false
        recording = true
        cuePhase = SttSession.Phase.Preparing
        session.start(
            onPhase = { cuePhase = it },
            onFinal = { result -> finalizeCapture(label, target, result) }
        )
    }

    fun requestCapture(label: String?) {
        if (!hasMicPermission) {
            pendingPermStart = { beginCapture(label) }
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (recording) {
            pendingStart = { beginCapture(label) }   // serial: selesaikan dulu, baru mulai berikutnya
            session.stop()
        } else beginCapture(label)
    }

    LaunchedEffect(uiState) {
        if (uiState is InputUiState.Saved) {
            controller.clear()
            onSaved()
            vm.reset()
        }
    }

    // ----- Lampiran & alat edit teks -----
    val clipboard = LocalClipboardManager.current
    val attachments by vm.attachments.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var linkText by remember { mutableStateOf("") }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }

    fun addAttachment(att: Attachment) {
        vm.addAttachment(att)
        // Hanya teks penanda yang masuk ke catatan (dan dikirim ke AI) — file tidak.
        controller.appendPlain(AttachmentStore.markerFor(att))
        vm.updateText(controller.value.text)
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = pendingCameraFile
        pendingCameraFile = null
        if (ok && f != null && f.exists()) addAttachment(AttachmentStore.imageAttachment(f))
        else f?.delete()
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { AttachmentStore.copyIntoStore(context, it)?.let(::addAttachment) }
    }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { AttachmentStore.copyIntoStore(context, it)?.let(::addAttachment) }
    }

    // ----- Baca gambar/file dengan AI (struk, tulisan tangan, screenshot, PDF, Word/Excel/CSV) -----
    // Pembacaan berjalan DI LATAR (di ViewModel): user bisa lanjut menulis / langsung memproses.
    val readScope = rememberCoroutineScope()
    val readingCount by vm.readingCount.collectAsState()
    val readMessage by vm.readMessage.collectAsState()
    val pendingInserts by vm.pendingInserts.collectAsState()
    var showReadChooser by remember { mutableStateOf(false) }
    var pendingReadCameraFile by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(readMessage) {
        readMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            vm.consumeReadMessage()
        }
    }
    // Hasil baca yang datang saat layar terbuka → sisipkan ke teks catatan
    LaunchedEffect(pendingInserts) {
        pendingInserts.firstOrNull()?.let { text ->
            controller.appendPlain(text)
            vm.updateText(controller.value.text)
            vm.consumeInsert(text)
        }
    }

    fun startRead(prepare: () -> Result<MediaReader.Prepared>) {
        readScope.launch {
            withContext(Dispatchers.IO) { prepare() }
                .onSuccess { vm.readMedia(it) }
                .onFailure {
                    com.secondbrain.app.util.DebugLog.log("Baca ✕ siapkan file", it.message ?: it.toString())
                    Toast.makeText(context, it.message ?: "File tidak bisa dibaca", Toast.LENGTH_LONG).show()
                }
        }
    }

    // Boleh pilih BANYAK file sekaligus — tiap file dibaca paralel (key API berbeda, round-robin)
    val readFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            // Lampirkan file aslinya + baca isinya dengan AI
            AttachmentStore.copyIntoStore(context, uri)?.let(::addAttachment)
            startRead { MediaReader.prepareFromUri(context, uri) }
        }
    }
    val readCameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = pendingReadCameraFile
        pendingReadCameraFile = null
        if (ok && f != null && f.exists()) {
            addAttachment(AttachmentStore.imageAttachment(f))
            startRead { MediaReader.prepareImageFile(f) }
        } else f?.delete()
    }

    val labelColor = if (isDark) Lavender200 else Lavender600
    val hlBg = if (isDark) Peach600.copy(0.30f) else Peach200
    val transform = VisualTransformation { annotated ->
        val s = annotated.text
        val styled = buildAnnotatedString {
            append(s)
            controller.blocks.forEach { b ->
                val st = b.start.coerceIn(0, s.length)
                val le = b.labelEnd.coerceIn(st, s.length)
                if (le > st) addStyle(SpanStyle(color = labelColor, fontWeight = FontWeight.Medium), st, le)
                if (controller.highlightStart == b.start) {
                    val en = b.end.coerceIn(st, s.length)
                    if (en > st) addStyle(SpanStyle(background = hlBg), st, en)
                }
            }
        }
        TransformedText(styled, OffsetMapping.Identity)
    }

    val bgColor = if (isDark) Lavender900 else Gray50
    Scaffold(containerColor = bgColor) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
                .imePadding()   // area teks tidak tertutup keyboard
        ) {
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenList) {
                    Icon(Icons.Outlined.Menu, "Daftar catatan",
                        tint = if (isDark) Lavender200 else Lavender600)
                }
                Text("Catatan baru", style = MaterialTheme.typography.titleMedium,
                    color = if (isDark) Lavender50 else Lavender800)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onAsk) {
                    Icon(Icons.Outlined.AutoAwesome, "Tanya AI",
                        tint = if (isDark) Lavender200 else Lavender600)
                }
            }

            // (Pemandu template disembunyikan sementara — akan diperbaiki nanti.)

            Spacer(Modifier.height(12.dp))

            // ----- Cue rekam -----
            if (recording) {
                RecordingCue(
                    label = activeLabel,
                    phase = cuePhase,
                    onDone = { session.stop() },
                    onCancel = { captureCanceled = true; session.cancel() }
                )
                Spacer(Modifier.height(12.dp))
            }

            // ----- Mic bebas + teks -----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionLabel("teks catatan")
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape)
                        .background(if (recording && activeLabel == null) Peach600 else (if (isDark) Lavender600.copy(0.3f) else Lavender100))
                        .clickable { requestCapture(null) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Mic, "Rekam bebas",
                        tint = if (recording && activeLabel == null) androidx.compose.ui.graphics.Color.White
                               else (if (isDark) Lavender200 else Lavender600),
                        modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(2.dp))

            // ----- Status kata pemicu suara -----
            if (voiceTriggerEnabled) {
                VoiceTriggerStatus(
                    state = voiceTrigger.state,
                    triggerWord = prefs.getVoiceTriggerWord(),
                    muted = triggerMuted,
                    hasPermission = hasMicPermission,
                    isDark = isDark,
                    onToggleMute = { triggerMuted = !triggerMuted },
                    onRearm = { voiceTrigger.startListening() },
                    onRequestPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                )
                Spacer(Modifier.height(4.dp))
            }

            // ----- Toolbar: alat edit + lampiran (ikon saja) -----
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToolIcon(Icons.Outlined.ContentPaste, "Tempel dari clipboard", isDark) {
                    clipboard.getText()?.text?.let {
                        if (it.isNotBlank()) {
                            controller.appendPlain(it)
                            vm.updateText(controller.value.text)
                        }
                    }
                }
                ToolIcon(Icons.Outlined.Undo, "Urungkan", isDark, enabled = controller.canUndo) {
                    controller.undo(); vm.updateText(controller.value.text)
                }
                ToolIcon(Icons.Outlined.Redo, "Ulangi", isDark, enabled = controller.canRedo) {
                    controller.redo(); vm.updateText(controller.value.text)
                }
                ToolIcon(Icons.Outlined.DeleteSweep, "Hapus semua", isDark,
                    enabled = controller.value.text.isNotEmpty() || attachments.isNotEmpty()) {
                    showClearDialog = true
                }
                Spacer(Modifier.weight(1f))
                ToolIcon(Icons.Outlined.DocumentScanner, "Baca gambar/file dengan AI", isDark) {
                    showReadChooser = true
                }
                ToolIcon(Icons.Outlined.PhotoCamera, "Lampirkan dari kamera", isDark) {
                    val f = AttachmentStore.newImageFile(context)
                    pendingCameraFile = f
                    cameraLauncher.launch(AttachmentStore.contentUri(context, f))
                }
                ToolIcon(Icons.Outlined.Image, "Lampirkan dari galeri", isDark) {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                    )
                }
                ToolIcon(Icons.Outlined.AttachFile, "Lampirkan file", isDark) {
                    fileLauncher.launch(arrayOf("*/*"))
                }
                ToolIcon(Icons.Outlined.Link, "Lampirkan link", isDark) {
                    linkText = ""; showLinkDialog = true
                }
            }
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = controller.value,
                onValueChange = { controller.onValueChange(it); vm.updateText(it.text) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                placeholder = {
                    Text("Ketik, rekam suara, atau kirim gambar/file…",
                        color = if (isDark) Lavender400.copy(0.5f) else Gray400)
                },
                visualTransformation = transform,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Lavender400,
                    unfocusedBorderColor = if (isDark) GlassBorderDark else Lavender200,
                    focusedContainerColor = if (isDark) GlassDark else GlassLight,
                    unfocusedContainerColor = if (isDark) GlassDark else GlassLight,
                    focusedTextColor = if (isDark) Lavender50 else Lavender800,
                    unfocusedTextColor = if (isDark) Lavender50 else Lavender800
                )
            )
            Spacer(Modifier.height(4.dp))
            Text("${controller.value.text.length} karakter",
                style = MaterialTheme.typography.labelSmall, color = if (isDark) Lavender400 else Gray400)

            // ----- Daftar lampiran -----
            if (attachments.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    attachments.forEach { att ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isDark) GlassDark else GlassLight)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                attachmentIcon(att.type), null,
                                modifier = Modifier.size(14.dp),
                                tint = if (isDark) Lavender200 else Lavender600
                            )
                            Text(
                                att.name.ifBlank { att.path }.take(24),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isDark) Lavender50 else Lavender800
                            )
                            Icon(
                                Icons.Outlined.Close, "Hapus lampiran",
                                modifier = Modifier.size(14.dp).clickable {
                                    AttachmentStore.deleteFile(context, att)
                                    vm.removeAttachment(att)
                                },
                                tint = if (isDark) Lavender400 else Gray400
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            val text = controller.value.text
            if (text.isBlank()) {
                TipBox(isDark)
            } else {
                if (uiState is InputUiState.Error) {
                    ErrorBox((uiState as InputUiState.Error).message, isDark)
                    Spacer(Modifier.height(8.dp))
                }
                val isLoading = uiState is InputUiState.Extracting || uiState is InputUiState.Saving
                GlassButton(
                    text = if (isLoading) "Memproses..." else "Proses dengan AI",
                    icon = Icons.Outlined.AutoAwesome,
                    onClick = vm::processWithAI,
                    accent = true,
                    enabled = !isLoading && !recording,
                    modifier = Modifier.fillMaxWidth()
                )
                if (uiState is InputUiState.Error) {
                    Spacer(Modifier.height(8.dp))
                    GlassButton(text = "Simpan tanpa AI (offline)",
                        onClick = vm::savePendingOffline, modifier = Modifier.fillMaxWidth())
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    // ----- Dialog hapus semua -----
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Hapus semua?") },
            text = { Text("Teks catatan dan daftar lampiran akan dikosongkan. Teks masih bisa dikembalikan dengan tombol Urungkan; file lampiran ikut terhapus.") },
            confirmButton = {
                TextButton(onClick = {
                    attachments.forEach { AttachmentStore.deleteFile(context, it) }
                    vm.clearAttachments()
                    controller.clearText()
                    vm.updateText("")
                    showClearDialog = false
                }) { Text("Hapus", color = Rose600) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Batal") }
            }
        )
    }

    // ----- Dialog pilih sumber "Baca dengan AI" -----
    if (showReadChooser) {
        AlertDialog(
            onDismissRequest = { showReadChooser = false },
            title = { Text("Baca dengan AI") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Struk, catatan tulis tangan, screenshot chat, poster, PDF, Word (.docx), " +
                        "Excel (.xlsx), CSV, atau TXT — teksnya diekstrak ke catatan (bisa diedit dulu) " +
                        "dan file aslinya ikut dilampirkan. Boleh pilih BEBERAPA file sekaligus; " +
                        "semuanya dibaca paralel dengan API berbeda. " +
                        "Maks 4 MB (gambar/PDF) / 10 MB (dokumen).",
                        style = MaterialTheme.typography.bodySmall
                    )
                    GlassButton(
                        text = "Kamera (foto struk/catatan)",
                        icon = Icons.Outlined.PhotoCamera,
                        onClick = {
                            showReadChooser = false
                            val f = AttachmentStore.newImageFile(context)
                            pendingReadCameraFile = f
                            readCameraLauncher.launch(AttachmentStore.contentUri(context, f))
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    GlassButton(
                        text = "Galeri / File (gambar, PDF, Word, Excel, CSV)",
                        icon = Icons.Outlined.Image,
                        onClick = {
                            showReadChooser = false
                            readFileLauncher.launch(arrayOf("image/*", "application/pdf", "text/*", "application/*"))
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showReadChooser = false }) { Text("Batal") }
            }
        )
    }

    // ----- Loading screen terpadu: baca file & proses AI (bagi user sama saja) -----
    val extracting = uiState is InputUiState.Extracting
    var readOverlayHidden by remember { mutableStateOf(false) }
    LaunchedEffect(readingCount) { if (readingCount == 0) readOverlayHidden = false }
    if (extracting || (readingCount > 0 && !readOverlayHidden)) {
        ExtractionLoadingOverlay(
            isDark = isDark,
            extracting = extracting,
            readingCount = readingCount,
            onCancel = { if (extracting) vm.cancelExtraction() else vm.cancelReads() },
            onHide = if (!extracting) ({ readOverlayHidden = true }) else null
        )
    }

    // ----- Dialog tambah link -----
    if (showLinkDialog) {
        AlertDialog(
            onDismissRequest = { showLinkDialog = false },
            title = { Text("Lampirkan link") },
            text = {
                OutlinedTextField(
                    value = linkText,
                    onValueChange = { linkText = it },
                    label = { Text("URL") },
                    placeholder = { Text("https://…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = linkText.isNotBlank(),
                    onClick = {
                        val url = linkText.trim()
                        addAttachment(Attachment(Attachment.TYPE_LINK, url, url))
                        showLinkDialog = false
                    }
                ) { Text("Tambah") }
            },
            dismissButton = {
                TextButton(onClick = { showLinkDialog = false }) { Text("Batal") }
            }
        )
    }
}

private val APP_TIPS = listOf(
    "Aktifkan kata pemicu di Pengaturan — ucapkan \"Jarvis, ingatkan saya…\" di layar ini tanpa menyentuh apa pun.",
    "Sebut waktu spesifik — \"besok jam 9 pagi\" — agar jadwal & alarm akurat sampai menit.",
    "Tulis harga sekalian (\"kopi 25rb\") — transaksi otomatis masuk halaman Keuangan.",
    "Bilang \"pakai alarm\" atau \"bangunkan saya\" supaya toggle alarm menyala otomatis.",
    "Minta persiapan: \"ingatkan sehari sebelumnya\" — jadi alarm persiapan tersendiri.",
    "Beberapa kegiatan dalam satu catatan? Semuanya tetap dibuatkan pengingat.",
    "Geser layar ke kanan untuk melihat, mencari, dan memfilter semua catatanmu.",
    "Ketuk ikon ✨ di dashboard untuk bertanya ke catatanmu, mis. \"minggu ini ada apa?\".",
    "Ekspor JSON rutin di Pengaturan — pilih Google Drive agar cadangan aman.",
    "Punya ≥2 API key (boleh provider sama) membuat ekstraksi paralel bebas limit per menit."
)

/** Layar tunggu terpadu — dipakai saat MEMBACA file maupun MEMPROSES catatan (bagi user
 *  keduanya sama: "AI sedang bekerja"). Animasi + tips bergantian + tombol batal;
 *  saat membaca ada tombol sembunyikan (baca lanjut di latar); tombol kembali tetap keluar. */
@Composable
private fun ExtractionLoadingOverlay(
    isDark: Boolean,
    extracting: Boolean,
    readingCount: Int,
    onCancel: () -> Unit,
    onHide: (() -> Unit)? = null
) {
    var tipIndex by remember { mutableStateOf(APP_TIPS.indices.random()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(4500)
            tipIndex = (tipIndex + 1) % APP_TIPS.size
        }
    }
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.85f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulseScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background((if (isDark) Lavender900 else Gray50).copy(alpha = 0.97f))
            // Blokir sentuhan ke layar di belakang
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(84.dp),
                    color = Lavender400,
                    strokeWidth = 3.dp
                )
                Icon(
                    Icons.Outlined.AutoAwesome, null,
                    modifier = Modifier.size(34.dp).graphicsLayer { scaleX = pulse; scaleY = pulse },
                    tint = if (isDark) Lavender200 else Lavender600
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                if (extracting) "Memproses catatan…"
                else "Membaca ${if (readingCount > 1) "$readingCount file" else "file"}…",
                style = MaterialTheme.typography.titleMedium,
                color = if (isDark) Lavender50 else Lavender800
            )
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    extracting && readingCount > 0 -> "Menunggu pembacaan gambar/file selesai dulu…"
                    extracting -> "AI membaca info umum, transaksi, dan jadwal secara paralel"
                    else -> "Teks hasil pembacaan akan masuk ke catatan dan tetap bisa diedit"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (isDark) Lavender400 else Gray600,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Crossfade(targetState = tipIndex, label = "tips") { i ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDark) GlassDark else GlassLight)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Outlined.Lightbulb, null, modifier = Modifier.size(16.dp), tint = Sky600)
                    Text(
                        "Tips: ${APP_TIPS[i]}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDark) Sky200 else Sky800
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            GlassButton(
                text = if (extracting) "Batalkan" else "Batalkan pembacaan",
                icon = Icons.Outlined.Close,
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth()
            )
            if (onHide != null) {
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onHide, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Sembunyikan — lanjut menulis, pembacaan jalan di latar",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isDark) Lavender200 else Lavender600
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Tombol kembali = keluar; proses tetap lanjut di latar.",
                style = MaterialTheme.typography.labelSmall,
                color = if (isDark) Lavender400 else Gray400
            )
        }
    }
}

@Composable
private fun VoiceTriggerStatus(
    state: VoiceTriggerController.State,
    triggerWord: String,
    muted: Boolean,
    hasPermission: Boolean,
    isDark: Boolean,
    onToggleMute: () -> Unit,
    onRearm: () -> Unit,
    onRequestPermission: () -> Unit
) {
    val continuing = state == VoiceTriggerController.State.Continuing
    val captured = state == VoiceTriggerController.State.Captured
    val stopped = state == VoiceTriggerController.State.Stopped
    val heard = (state as? VoiceTriggerController.State.Waiting)?.triggerHeard == true
    val bg = when {
        continuing -> if (isDark) Peach600.copy(0.25f) else Peach200.copy(0.5f)
        captured || heard -> if (isDark) Sky600.copy(0.2f) else Sky50
        else -> if (isDark) GlassDark else GlassLight
    }
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(bg, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val (icon, text, tint) = when {
            !hasPermission -> Triple(Icons.Outlined.MicOff, "Izinkan mikrofon untuk kata pemicu", Lemon600)
            muted -> Triple(Icons.Outlined.HearingDisabled, "Kata pemicu dijeda", if (isDark) Lavender400 else Gray400)
            continuing -> Triple(Icons.Outlined.GraphicEq, "Kata pemicu terdengar — ucapkan isinya…", Peach600)
            captured -> Triple(Icons.Outlined.CheckCircle, "Tertangkap — tambah lewat tombol mic", Sky600)
            stopped -> Triple(Icons.Outlined.HearingDisabled, "Tidak ada kata pemicu — berhenti mendengar",
                if (isDark) Lavender400 else Gray600)
            heard -> Triple(Icons.Outlined.Hearing, "Kata pemicu terdengar…", Sky600)
            state is VoiceTriggerController.State.Waiting ->
                Triple(Icons.Outlined.Hearing, "Menunggu \"$triggerWord\"…", if (isDark) Lavender400 else Gray600)
            else -> Triple(Icons.Outlined.Hearing, "Kata pemicu nonaktif sementara", if (isDark) Lavender400 else Gray400)
        }
        Icon(icon, null, modifier = Modifier.size(15.dp), tint = tint)
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = if (isDark) Lavender200 else Lavender800,
            modifier = if (!hasPermission) Modifier.weight(1f).clickable(onClick = onRequestPermission)
                       else Modifier.weight(1f)
        )
        if ((captured || stopped) && !muted && hasPermission) {
            TextButton(onClick = onRearm, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text("Dengar lagi", style = MaterialTheme.typography.labelSmall, color = Sky600)
            }
        }
        if (hasPermission) {
            IconButton(onClick = onToggleMute, modifier = Modifier.size(28.dp)) {
                Icon(
                    if (muted) Icons.Outlined.Hearing else Icons.Outlined.HearingDisabled,
                    if (muted) "Aktifkan kata pemicu" else "Jeda kata pemicu",
                    modifier = Modifier.size(15.dp),
                    tint = if (isDark) Lavender400 else Gray400
                )
            }
        }
    }
}

@Composable
private fun ToolIcon(
    icon: ImageVector,
    desc: String,
    isDark: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(36.dp)) {
        Icon(
            icon, desc,
            modifier = Modifier.size(18.dp),
            tint = if (enabled) (if (isDark) Lavender200 else Lavender600)
                   else (if (isDark) Lavender400.copy(0.35f) else Gray400.copy(0.5f))
        )
    }
}

internal fun attachmentIcon(type: String): ImageVector = when (type) {
    Attachment.TYPE_IMAGE -> Icons.Outlined.Image
    Attachment.TYPE_VIDEO -> Icons.Outlined.Videocam
    Attachment.TYPE_LINK -> Icons.Outlined.Link
    else -> Icons.Outlined.InsertDriveFile
}

@Composable
private fun Chip(label: String, sel: Boolean, isDark: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (sel) (if (isDark) Lavender600.copy(0.4f) else Lavender100)
                else (if (isDark) GlassDark else GlassLight)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = if (sel) (if (isDark) Lavender200 else Lavender600) else (if (isDark) Lavender400 else Gray600))
    }
}

@Composable
private fun TipBox(isDark: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(if (isDark) Sky600.copy(0.1f) else Sky50, RoundedCornerShape(12.dp)).padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Outlined.Lightbulb, null, tint = Sky600, modifier = Modifier.size(16.dp))
        Text("Catat bebas — sebutkan waktu, orang, tempat, dan harga agar AI akurat. Geser ke kanan untuk melihat semua catatan.",
            style = MaterialTheme.typography.bodySmall, color = if (isDark) Sky200 else Sky800)
    }
}

@Composable
private fun ErrorBox(message: String, isDark: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(if (isDark) Rose600.copy(0.1f) else Rose50, RoundedCornerShape(12.dp)).padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Outlined.ErrorOutline, null, tint = Rose600, modifier = Modifier.size(16.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = if (isDark) Rose200 else Rose800)
    }
}
