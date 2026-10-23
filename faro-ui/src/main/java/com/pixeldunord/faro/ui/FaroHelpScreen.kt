package com.pixeldunord.faro.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pixeldunord.faro.Faro
import com.pixeldunord.faro.FaroForm
import com.pixeldunord.faro.FaroTicket
import java.util.Locale

/**
 * The ready-made "Help & feedback" screen: drawn from your Faro request form, in your app's language,
 * with the kinds, every custom field, pictures (with the screenshot of the previous screen and an
 * editor to cover private details), what travels with the request, and every state (loading,
 * offline with retry, per-field errors, sending and the request number at the end).
 *
 * Put it in your navigation (call [FaroScreenshot.capture] before navigating to offer the screenshot),
 * or open it as its own activity with [FaroHelpActivity.start].
 *
 * ```kotlin
 * composable("help") {
 *     FaroHelpScreen(onClose = { navController.popBackStack() })
 * }
 * ```
 *
 * @param onClose Called by the close button and by "Done" after sending.
 * @param modifier Applied to the screen's root.
 * @param options What to ask and show.
 * @param theme Colors, shapes and type.
 * @param onSent Called once with the ticket when a request gets in.
 */
@Composable
public fun FaroHelpScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    options: FaroHelpOptions = FaroHelpOptions(),
    theme: FaroTheme = FaroTheme(),
    onSent: (FaroTicket) -> Unit = {},
) {
    val model: HelpModel = viewModel(key = "com.pixeldunord.faro.ui.help")
    remember(model) {
        model.start(options)
        true
    }
    val state by model.state.collectAsState()
    val requested = options.locale
        ?: if (Faro.isConfigured) Faro.client.defaultLocale else Locale.getDefault().toLanguageTag()
    val strings = options.strings
        ?: FaroStrings.forLocale(state.form?.locale?.takeIf { FaroStrings.supports(it) } ?: requested)

    LaunchedEffect(state.ticket) { state.ticket?.let(onSent) }

    ProvideFaroTheme(theme) { dark ->
        val accentInk = readableAccent(MaterialTheme.colorScheme, dark)
        Box(modifier) {
            Scaffold(
                // While the cover editor is open, only it is visible to TalkBack.
                modifier = if (state.editing != null) Modifier.clearAndSetSemantics { } else Modifier,
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    TopBar(
                        title = strings.screenTitle,
                        closeLabel = strings.close,
                        progress = progressOf(state),
                        busy = state.phase == Phase.SENDING || (state.form == null && state.link == Link.LOADING),
                        onClose = onClose,
                    )
                },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                    val form = state.form
                    val ticket = state.ticket
                    when {
                        state.phase == Phase.SENT && ticket != null && form != null ->
                            SuccessStub(form, ticket, strings, accentInk, onAnother = model::startOver, onDone = onClose)
                        form != null -> HelpForm(form, state, model, options, strings, accentInk)
                        state.link == Link.OFFLINE ->
                            EmptyState(FaroIcons.WifiOff, strings.offlineTitle, strings.offlineText, strings.retry, model::load)
                        state.link == Link.UNAVAILABLE ->
                            EmptyState(
                                FaroIcons.AlertCircle, strings.unavailableTitle, strings.unavailableText,
                                if (Faro.isConfigured) strings.retry else null, model::load,
                            )
                        else -> Loading(strings)
                    }
                }
            }
            state.editing?.let { target ->
                CoverEditor(
                    bitmap = target.bitmap,
                    strings = strings,
                    accent = MaterialTheme.colorScheme.primary,
                    onDone = model::finishEdit,
                    onCancel = model::cancelEdit,
                )
            }
        }
    }
}

/** How much of what is required is done: the kind plus every required field that passes. */
private fun progressOf(state: HelpState): Float {
    val form = state.form ?: return 0f
    if (state.phase == Phase.SENT) return 1f
    val required = form.fields.filter { it.required }
    val issues = form.validate(state.kind, state.values)
    val total = required.size + 1
    val done = (if (FaroForm.KIND in issues) 0 else 1) + required.count { it.key !in issues }
    return done.toFloat() / total
}

@Composable
internal fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}

@Composable
private fun TopBar(title: String, closeLabel: String, progress: Float, busy: Boolean, onClose: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(FaroIcons.Close, contentDescription = closeLabel, tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 4.dp).semantics { heading() },
            )
        }
        Beam(progress, busy)
    }
}

/**
 * The lighthouse beam under the top bar: it fills as the form gets ready to send, and sweeps while
 * something is on its way.
 */
@Composable
private fun Beam(progress: Float, busy: Boolean) {
    val reduced = rememberReducedMotion()
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val filled by animateFloatAsState(progress, tween(if (reduced) 0 else 450), label = "beam")
    val sweep = if (busy && !reduced) {
        val transition = rememberInfiniteTransition(label = "sweep")
        transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "x").value
    } else {
        -1f
    }
    Canvas(Modifier.fillMaxWidth().height(3.dp)) {
        drawRect(track)
        if (filled > 0f) {
            val end = size.width * filled
            drawRect(
                Brush.horizontalGradient(listOf(accent.copy(alpha = 0.15f), accent), startX = 0f, endX = end),
                size = Size(end, size.height),
            )
        }
        if (sweep >= 0f) {
            val w = size.width * 0.35f
            val x = -w + (size.width + w) * sweep
            drawRect(
                Brush.horizontalGradient(listOf(Color.Transparent, accent, Color.Transparent), startX = x, endX = x + w),
                topLeft = Offset(x, 0f),
                size = Size(w, size.height),
            )
        }
    }
}

@Composable
private fun Loading(strings: FaroStrings) {
    val reduced = rememberReducedMotion()
    val alpha = if (reduced) {
        0.6f
    } else {
        rememberInfiniteTransition(label = "pulse")
            .animateFloat(0.35f, 0.8f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a").value
    }
    val block = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(
        Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .padding(20.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = strings.loading
                liveRegion = LiveRegionMode.Polite
            },
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val shape = MaterialTheme.shapes.medium
        Box(Modifier.fillMaxWidth(0.55f).height(28.dp).graphicsLayer { this.alpha = alpha }.clip(shape).background(block))
        Box(Modifier.fillMaxWidth(0.9f).height(16.dp).graphicsLayer { this.alpha = alpha }.clip(shape).background(block))
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) {
                Box(Modifier.weight(1f).height(72.dp).graphicsLayer { this.alpha = alpha }.clip(shape).background(block))
            }
        }
        repeat(3) {
            Box(Modifier.fillMaxWidth().height(56.dp).graphicsLayer { this.alpha = alpha }.clip(shape).background(block))
        }
        Box(Modifier.fillMaxWidth().height(120.dp).graphicsLayer { this.alpha = alpha }.clip(shape).background(block))
    }
}

@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    text: String,
    action: String?,
    onAction: () -> Unit,
) {
    Column(
        Modifier
            .widthIn(max = 480.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // A dark lamp: the icon inside a ring with dashed "rays" that are off.
        val ring = MaterialTheme.colorScheme.outlineVariant
        Box(
            Modifier
                .size(112.dp)
                .drawWithContent {
                    drawContent()
                    drawCircle(
                        ring,
                        radius = size.minDimension / 2f - 2.dp.toPx(),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx())),
                        ),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(34.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (action != null) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(FaroIcons.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(action)
            }
        }
    }
}

/**
 * Success: a ticket stub, torn along its perforation, with the request number as the team will see
 * it; the beam passes over it once.
 */
@Composable
private fun SuccessStub(
    form: FaroForm,
    ticket: FaroTicket,
    strings: FaroStrings,
    accentInk: Color,
    onAnother: () -> Unit,
    onDone: () -> Unit,
) {
    val reduced = rememberReducedMotion()
    val sweep = remember(ticket.number) { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(ticket.number) { if (!reduced) sweep.animateTo(1f, tween(1400)) }
    val accent = MaterialTheme.colorScheme.primary
    val paper = MaterialTheme.colorScheme.surfaceContainerLow
    val background = MaterialTheme.colorScheme.background
    val edge = MaterialTheme.colorScheme.outlineVariant
    val number = String.format(Locale.ROOT, strings.successNumber, ticket.number)

    Column(
        Modifier
            .widthIn(max = 560.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    liveRegion = LiveRegionMode.Polite
                }
                .drawWithContent {
                    val r = 14.dp.toPx()
                    val stubX = size.width * 0.32f
                    drawRoundRect(paper, cornerRadius = CornerRadius(r, r))
                    drawRoundRect(edge, cornerRadius = CornerRadius(r, r), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                    // Perforation: two bites and a dotted tear line.
                    drawCircle(background, 12.dp.toPx(), Offset(stubX, 0f))
                    drawCircle(background, 12.dp.toPx(), Offset(stubX, size.height))
                    drawLine(
                        edge, Offset(stubX, 16.dp.toPx()), Offset(stubX, size.height - 16.dp.toPx()),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 6.dp.toPx())),
                    )
                    drawContent()
                    // The beam crossing the stub once.
                    if (sweep.value in 0.001f..0.999f) clipRect {
                        val w = size.width * 0.45f
                        val x = -w + (size.width + w) * sweep.value
                        drawRect(
                            Brush.horizontalGradient(listOf(Color.Transparent, accent.copy(alpha = 0.28f), Color.Transparent), startX = x, endX = x + w),
                            topLeft = Offset(x, 0f),
                            size = Size(w, size.height),
                        )
                    }
                },
        ) {
            Row(Modifier.padding(vertical = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.fillMaxWidth(0.32f), contentAlignment = Alignment.Center) {
                    Icon(FaroIcons.CircleCheck, contentDescription = null, tint = accentInk, modifier = Modifier.size(44.dp))
                }
                Column(Modifier.padding(start = 20.dp, end = 16.dp)) {
                    Text(
                        strings.successLabel.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        letterSpacing = 2.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        number,
                        style = MaterialTheme.typography.displaySmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = accentInk,
                    )
                }
            }
        }

        Text(
            form.success.ifEmpty { strings.successDefault },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                String.format(Locale.ROOT, strings.successReplyTo, ticket.email, ticket.number),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!ticket.confirmed) {
                Text(
                    strings.successUnconfirmed,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onAnother, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Text(strings.sendAnother, textAlign = TextAlign.Center, color = accentInk)
            }
            Button(onClick = onDone, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Text(strings.done, textAlign = TextAlign.Center)
            }
        }
    }
}
