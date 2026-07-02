package com.secondbrain.app.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.secondbrain.app.data.model.Attachment
import com.secondbrain.app.ui.theme.*
import com.secondbrain.app.util.AttachmentStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Menampilkan lampiran sebuah catatan: foto sebagai thumbnail (ketuk untuk buka penuh),
 * video/file/link sebagai baris yang bisa diketuk untuk diputar/dibuka di app lain.
 */
@Composable
fun AttachmentSection(attachmentsJson: String, isDark: Boolean) {
    val attachments = remember(attachmentsJson) { Attachment.listFromJson(attachmentsJson) }
    if (attachments.isEmpty()) return
    val context = LocalContext.current

    GlassCard {
        SectionLabel("lampiran", modifier = Modifier.padding(bottom = 8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            attachments.forEach { att ->
                when (att.type) {
                    Attachment.TYPE_IMAGE -> AttachmentImage(att, isDark)
                    else -> AttachmentRow(att, isDark)
                }
            }
        }
    }
}

@Composable
private fun AttachmentImage(att: Attachment, isDark: Boolean) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, att.path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val file = AttachmentStore.resolve(context, att)
                if (!file.exists()) return@runCatching null
                // Decode dengan sampling agar hemat memori (target lebar ~1080px)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= 1080) sample *= 2
                BitmapFactory.decodeFile(
                    file.absolutePath,
                    BitmapFactory.Options().apply { inSampleSize = sample }
                )
            }.getOrNull()
        }
    }

    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = att.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 240.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable { AttachmentStore.open(context, att) }
        )
    } else {
        AttachmentRow(att, isDark)
    }
}

@Composable
private fun AttachmentRow(att: Attachment, isDark: Boolean) {
    val context = LocalContext.current
    val (icon: ImageVector, hint: String) = when (att.type) {
        Attachment.TYPE_VIDEO -> Icons.Outlined.PlayCircle to "Ketuk untuk putar"
        Attachment.TYPE_LINK -> Icons.Outlined.Link to "Ketuk untuk buka"
        Attachment.TYPE_IMAGE -> Icons.Outlined.Image to "Ketuk untuk buka"
        else -> Icons.Outlined.InsertDriveFile to "Ketuk untuk buka"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isDark) GlassDark else GlassLight)
            .clickable { AttachmentStore.open(context, att) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(22.dp), tint = if (isDark) Lavender200 else Lavender600)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                att.name.ifBlank { att.path },
                style = MaterialTheme.typography.bodySmall,
                color = if (isDark) Lavender50 else Lavender800,
                maxLines = 2
            )
            Text(
                hint,
                style = MaterialTheme.typography.labelSmall,
                color = if (isDark) Lavender400 else Gray400
            )
        }
        Icon(Icons.Outlined.OpenInNew, null, modifier = Modifier.size(16.dp),
            tint = if (isDark) Lavender400 else Gray400)
    }
}
