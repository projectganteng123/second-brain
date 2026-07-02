package com.secondbrain.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
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
import com.secondbrain.app.capture.*
import com.secondbrain.app.data.model.Attachment
import com.secondbrain.app.ui.components.*
import com.secondbrain.app.ui.theme.*
import com.secondbrain.app.util.AttachmentStore
import com.secondbrain.app.util.PrefsManager
import com.secondbrain.app.viewmodel.InputUiState
import com.secondbrain.app.viewmodel.InputViewModel
import java.io.File

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun InputScreen(
    vm: InputViewModel,
    onBack: () -> Unit,
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

    var selectedTemplate by remember { mutableStateOf(CaptureTemplates.byId(prefs.getDefaultTemplateId())) }

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
        pendingStart?.let { val s = it; pendingStart = null; s() }
    }

    fun beginCapture(label: String?) {
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
        ) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, "Kembali", tint = if (isDark) Lavender200 else Lavender600)
                }
                Text("Catatan baru", style = MaterialTheme.typography.titleMedium,
                    color = if (isDark) Lavender50 else Lavender800)
            }

            Spacer(Modifier.height(14.dp))

            // ----- Pemilih template -----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionLabel("pemandu (opsional)")
                if (selectedTemplate != null) {
                    val isDefault = prefs.getDefaultTemplateId() == selectedTemplate!!.id
                    TextButton(onClick = {
                        prefs.saveDefaultTemplateId(if (isDefault) null else selectedTemplate!!.id)
                    }) {
                        Icon(
                            if (isDefault) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                            null, modifier = Modifier.size(15.dp), tint = Lavender600
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(if (isDefault) "Default" else "Jadikan default",
                            style = MaterialTheme.typography.labelSmall, color = Lavender600)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("Bebas", selectedTemplate == null, isDark) { selectedTemplate = null }
                CaptureTemplates.BUILT_IN.forEach { t ->
                    Chip(t.name, selectedTemplate?.id == t.id, isDark) { selectedTemplate = t }
                }
            }

            // ----- Tombol pertanyaan -----
            selectedTemplate?.let { tpl ->
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    tpl.questions.forEach { q ->
                        GlassButton(
                            text = q,
                            icon = Icons.Outlined.Mic,
                            onClick = { requestCapture(q) }
                        )
                    }
                }
            }

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
                    Text("Ketik, rekam bebas, atau ketuk tombol pemandu di atas…",
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
        Text("Ketuk tombol pemandu untuk menjawab per bagian, atau catat bebas. Sebutkan waktu, orang, tempat agar AI akurat.",
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
