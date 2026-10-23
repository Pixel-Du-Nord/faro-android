package com.pixeldunord.faro.ui

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The cover editor's tools. */
private enum class Tool { COVER, BOX, MARK }

/** One stroke or box, in BITMAP pixels so it can be burnt in at full resolution. */
private class Mark(val tool: Tool, val points: List<Offset>) {
    fun plus(point: Offset) = Mark(tool, if (tool == Tool.BOX) listOf(points.first(), point) else points + point)
}

/** Fixed dark backdrop: a photo editor reads best on near-black, in both themes. */
private val Backdrop = Color(0xFF0B0F18)
private val OnBackdrop = Color(0xFFE8ECF4)
private val Ink = Color(0xFF111111)

/**
 * Full-screen editor to draw over a picture before sending it: a thick black marker and boxes to
 * cover names, numbers or faces, and a thin accent pen to point at what went wrong.
 */
@Composable
internal fun CoverEditor(
    bitmap: Bitmap,
    strings: FaroStrings,
    accent: Color,
    onDone: (Bitmap) -> Unit,
    onCancel: () -> Unit,
) {
    val marks = remember(bitmap) { mutableStateListOf<Mark>() }
    var current by remember(bitmap) { mutableStateOf<Mark?>(null) }
    var tool by remember { mutableStateOf(Tool.COVER) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val image = remember(bitmap) { bitmap.asImageBitmap() }

    // Stroke widths in bitmap pixels, relative to the picture so they look the same on any size.
    val coverWidth = max(min(bitmap.width, bitmap.height) * 0.05f, 14f)
    val markWidth = max(coverWidth / 4f, 4f)

    fun fit(size: IntSize): Pair<Float, Offset> {
        if (size.width == 0 || size.height == 0) return 1f to Offset.Zero
        val scale = min(size.width / bitmap.width.toFloat(), size.height / bitmap.height.toFloat())
        val offset = Offset((size.width - bitmap.width * scale) / 2f, (size.height - bitmap.height * scale) / 2f)
        return scale to offset
    }

    BackHandler(onBack = onCancel)
    LightSystemBars()
    // Drawn over the help screen (not a dialog window) so the system bars are handled like the rest.
    Box(
        Modifier
            .fillMaxSize()
            .background(Backdrop)
            // Nothing under the editor may be touched while it is open.
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false) } },
    ) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            // Top bar: cancel, title, done.
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onCancel) {
                    Icon(FaroIcons.Close, contentDescription = strings.cancel, tint = OnBackdrop)
                }
                Text(
                    strings.editorTitle,
                    color = OnBackdrop,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                Button(
                    onClick = {
                        val all = marks.toList() + listOfNotNull(current)
                        onDone(burn(bitmap, all, coverWidth, markWidth, accent))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = onAccent(accent)),
                    modifier = Modifier.padding(end = 8.dp),
                ) { Text(strings.done) }
            }

            // The picture and what is drawn over it.
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(12.dp)
                    .onSizeChanged { canvasSize = it }
                    .semantics { contentDescription = strings.editorCanvas }
                    .pointerInput(tool, bitmap) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val (scale, offset) = fit(canvasSize)
                            fun toBitmap(p: Offset) = Offset(
                                ((p.x - offset.x) / scale).coerceIn(0f, bitmap.width.toFloat()),
                                ((p.y - offset.y) / scale).coerceIn(0f, bitmap.height.toFloat()),
                            )
                            val start = toBitmap(down.position)
                            current = Mark(tool, if (tool == Tool.BOX) listOf(start, start) else listOf(start))
                            down.consume()
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                current = current?.plus(toBitmap(change.position))
                                change.consume()
                            }
                            current?.let { marks.add(it) }
                            current = null
                        }
                    },
            ) {
                val (scale, offset) = fit(IntSize(size.width.roundToInt(), size.height.roundToInt()))
                val dst = IntSize((bitmap.width * scale).roundToInt(), (bitmap.height * scale).roundToInt())
                drawImage(image, dstOffset = IntOffset(offset.x.roundToInt(), offset.y.roundToInt()), dstSize = dst)
                clipRect(offset.x, offset.y, offset.x + dst.width, offset.y + dst.height) {
                    withTransform({
                        translate(offset.x, offset.y)
                        scale(scale, scale, pivot = Offset.Zero)
                    }) {
                        (marks + listOfNotNull(current)).forEach { mark ->
                            when (mark.tool) {
                                Tool.BOX -> {
                                    val r = Rect(mark.points.first(), mark.points.last())
                                    val box = Rect(min(r.left, r.right), min(r.top, r.bottom), max(r.left, r.right), max(r.top, r.bottom))
                                    drawRect(Ink, box.topLeft, Size(max(box.width, 2f), max(box.height, 2f)))
                                }
                                Tool.COVER, Tool.MARK -> {
                                    val color = if (mark.tool == Tool.COVER) Ink else accent
                                    val width = if (mark.tool == Tool.COVER) coverWidth else markWidth
                                    if (mark.points.size == 1) {
                                        drawCircle(color, width / 2f, mark.points.first())
                                    } else {
                                        drawPath(
                                            pathOf(mark.points),
                                            color,
                                            style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Text(
                strings.editorHint,
                color = OnBackdrop.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
            )

            // Tools.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(Modifier.weight(1f).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ToolButton(FaroIcons.Pencil, strings.toolCover, tool == Tool.COVER, accent) { tool = Tool.COVER }
                    ToolButton(FaroIcons.Square, strings.toolBox, tool == Tool.BOX, accent) { tool = Tool.BOX }
                    ToolButton(FaroIcons.Highlight, strings.toolMark, tool == Tool.MARK, accent) { tool = Tool.MARK }
                }
                IconButton(onClick = { if (marks.isNotEmpty()) marks.removeAt(marks.lastIndex) }, enabled = marks.isNotEmpty()) {
                    Icon(FaroIcons.Undo, contentDescription = strings.undo, tint = OnBackdrop.copy(alpha = if (marks.isNotEmpty()) 1f else 0.35f))
                }
                IconButton(onClick = { marks.clear() }, enabled = marks.isNotEmpty()) {
                    Icon(FaroIcons.Trash, contentDescription = strings.startOver, tint = OnBackdrop.copy(alpha = if (marks.isNotEmpty()) 1f else 0.35f))
                }
            }
            Spacer(Modifier.size(8.dp))
        }
    }
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.22f) else Color.Transparent)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 56.dp)
            .width(72.dp)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) accent else OnBackdrop, modifier = Modifier.size(22.dp))
        Text(label, color = if (selected) accent else OnBackdrop, maxLines = 1, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
    }
}

private fun pathOf(points: List<Offset>): Path = Path().apply {
    moveTo(points.first().x, points.first().y)
    // Midpoint quadratic smoothing: a hand-drawn line without corners.
    for (i in 1 until points.size) {
        val a = points[i - 1]
        val b = points[i]
        quadraticTo(a.x, a.y, (a.x + b.x) / 2f, (a.y + b.y) / 2f)
    }
    lineTo(points.last().x, points.last().y)
}

/** Draws the marks INTO a copy of the bitmap: what is covered never leaves the device. */
private fun burn(source: Bitmap, marks: List<Mark>, coverWidth: Float, markWidth: Float, accent: Color): Bitmap {
    val out = source.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = android.graphics.Canvas(out)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    for (mark in marks) {
        paint.color = (if (mark.tool == Tool.MARK) accent else Ink).toArgb()
        when (mark.tool) {
            Tool.BOX -> {
                paint.style = Paint.Style.FILL
                val a = mark.points.first()
                val b = mark.points.last()
                canvas.drawRect(min(a.x, b.x), min(a.y, b.y), max(a.x, b.x) + 1f, max(a.y, b.y) + 1f, paint)
            }
            Tool.COVER, Tool.MARK -> {
                val width = if (mark.tool == Tool.COVER) coverWidth else markWidth
                if (mark.points.size == 1) {
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(mark.points[0].x, mark.points[0].y, width / 2f, paint)
                } else {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = width
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.strokeJoin = Paint.Join.ROUND
                    val path = android.graphics.Path()
                    val p = mark.points
                    path.moveTo(p[0].x, p[0].y)
                    for (i in 1 until p.size) {
                        path.quadTo(p[i - 1].x, p[i - 1].y, (p[i - 1].x + p[i].x) / 2f, (p[i - 1].y + p[i].y) / 2f)
                    }
                    path.lineTo(p.last().x, p.last().y)
                    canvas.drawPath(path, paint)
                }
            }
        }
    }
    return out
}

/** Light status and navigation bar icons over the dark editor, restored when it closes. */
@Composable
private fun LightSystemBars() {
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(view) {
        val window = (view.context as? android.app.Activity)?.window
        val controller = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        val status = controller?.isAppearanceLightStatusBars
        val navigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            status?.let { controller.isAppearanceLightStatusBars = it }
            navigation?.let { controller.isAppearanceLightNavigationBars = it }
        }
    }
}
