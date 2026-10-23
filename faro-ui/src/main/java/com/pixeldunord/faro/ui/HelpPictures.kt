package com.pixeldunord.faro.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pixeldunord.faro.FaroAttachment
import com.pixeldunord.faro.FaroRequest
import java.util.Locale

/** Size of a picture tile: a phone screen in miniature. */
private val TileWidth = 84.dp
private val TileHeight = 140.dp

/**
 * The pictures: the screenshot of the previous screen offered first (attach it, cover details first,
 * or not this one), then the attached ones as a filmstrip, each with "cover details" and "remove",
 * and a dashed slot to add more from the system photo picker (no permission needed).
 */
@Composable
internal fun Pictures(state: HelpState, model: HelpModel, strings: FaroStrings, accentInk: Color, enabled: Boolean) {
    val room = FaroRequest.MAX_ATTACHMENTS - state.attachments.size
    val pickOne = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { model.attach(listOf(it)) }
    }
    val pickMany = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(FaroRequest.MAX_ATTACHMENTS),
    ) { uris -> model.attach(uris) }
    val colors = MaterialTheme.colorScheme

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                strings.picturesTitle,
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            Text(
                String.format(Locale.ROOT, strings.picturesCount, state.attachments.size, FaroRequest.MAX_ATTACHMENTS),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
        }

        state.suggestion?.takeIf { room > 0 }?.let { shot ->
            ScreenshotOffer(shot, strings, accentInk, enabled, model)
        }

        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.attachments.forEachIndexed { index, attachment ->
                AttachedTile(attachment, accentInk, index + 1, strings, enabled, onCover = { model.editAttachment(attachment.id) }) {
                    model.remove(attachment.id)
                }
            }
            if (room > 0) {
                AddTile(strings.addPicture, accentInk, enabled) {
                    val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    if (room >= 2) pickMany.launch(request) else pickOne.launch(request)
                }
            }
        }
        Text(strings.picturesHint, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScreenshotOffer(shot: Bitmap, strings: FaroStrings, accentInk: Color, enabled: Boolean, model: HelpModel) {
    val colors = MaterialTheme.colorScheme
    val image = remember(shot) { shot.asImageBitmap() }
    val shape = MaterialTheme.shapes.medium
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.primaryContainer.copy(alpha = 0.55f))
            .border(1.dp, colors.primary.copy(alpha = 0.45f), shape)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Image(
            image,
            contentDescription = strings.screenshotTitle,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 72.dp, height = 120.dp)
                .clip(MaterialTheme.shapes.small)
                .border(1.dp, colors.outlineVariant, MaterialTheme.shapes.small),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(strings.screenshotTitle, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
            Text(strings.screenshotText, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = model::editSuggestion, enabled = enabled) {
                    Icon(FaroIcons.Pencil, null, modifier = Modifier.size(16.dp))
                    Text(strings.screenshotCover, modifier = Modifier.padding(start = 6.dp))
                }
                TextButton(onClick = model::attachSuggestion, enabled = enabled) {
                    Text(strings.screenshotAttach, color = accentInk)
                }
                TextButton(onClick = model::dismissSuggestion, enabled = enabled) {
                    Text(strings.screenshotDismiss, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun AttachedTile(
    attachment: FaroAttachment,
    accentInk: Color,
    position: Int,
    strings: FaroStrings,
    enabled: Boolean,
    onCover: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val bitmap = remember(attachment.id) {
        val options = BitmapFactory.Options().apply { inSampleSize = 4 }
        BitmapFactory.decodeByteArray(attachment.bytes, 0, attachment.bytes.size, options)?.asImageBitmap()
    }
    val shape = MaterialTheme.shapes.small
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(TileWidth, TileHeight)) {
            if (bitmap != null) {
                Image(
                    bitmap,
                    contentDescription = String.format(Locale.ROOT, strings.pictureDescription, position),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize().clip(shape).border(1.dp, colors.outlineVariant, shape),
                )
            }
            // Remove: a 48 dp target with a small round face.
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(48.dp)
                    .clickable(enabled = enabled, role = Role.Button, onClickLabel = strings.removePicture, onClick = onRemove)
                    .semantics { contentDescription = strings.removePicture },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.62f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(FaroIcons.Close, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
        TextButton(onClick = onCover, enabled = enabled, modifier = Modifier.padding(top = 2.dp)) {
            Icon(FaroIcons.Pencil, null, tint = accentInk, modifier = Modifier.size(14.dp))
            Text(strings.coverPicture, color = accentInk, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 4.dp))
        }
    }
}

@Composable
private fun AddTile(label: String, accentInk: Color, enabled: Boolean, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.small
    val dash = accentInk.copy(alpha = 0.7f)
    Column(
        Modifier
            .size(TileWidth, TileHeight)
            .clip(shape)
            .drawBehind {
                val r = 10.dp.toPx()
                drawRoundRect(
                    dash,
                    cornerRadius = CornerRadius(r, r),
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))),
                )
            }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(FaroIcons.PhotoPlus, null, tint = accentInk, modifier = Modifier.size(28.dp))
        Text(label, color = accentInk, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
    }
}
