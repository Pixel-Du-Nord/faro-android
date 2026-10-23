package com.pixeldunord.faro.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pixeldunord.faro.Faro
import com.pixeldunord.faro.FaroField
import com.pixeldunord.faro.FaroFieldType
import com.pixeldunord.faro.FaroForm
import com.pixeldunord.faro.FaroIssue
import com.pixeldunord.faro.FaroKind
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HelpForm(
    form: FaroForm,
    state: HelpState,
    model: HelpModel,
    options: FaroHelpOptions,
    strings: FaroStrings,
    accentInk: Color,
) {
    val sending = state.phase == Phase.SENDING
    val requesters = remember(form) { (form.fields.map { it.key } + FaroForm.KIND).associateWith { BringIntoViewRequester() } }

    // After a failed validation, bring the first problem into view.
    LaunchedEffect(state.issueSignal) {
        if (state.issueSignal == 0) return@LaunchedEffect
        val first = (listOf(FaroForm.KIND) + form.fields.map { it.key }).firstOrNull { it in state.issues }
        first?.let { requesters[it]?.bringIntoView() }
    }

    Column(
        Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // Heading.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                form.title.ifEmpty { strings.defaultTitle },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                form.intro.ifEmpty { strings.defaultIntro },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.link == Link.CACHED) {
                Row(
                    Modifier
                        .padding(top = 6.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(role = Role.Button, onClickLabel = strings.retry, onClick = model::load)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(FaroIcons.WifiOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Text(
                        strings.cachedNotice,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(FaroIcons.Refresh, null, tint = accentInk, modifier = Modifier.size(18.dp))
                }
            }
        }

        // Kind.
        Column(
            Modifier.bringIntoViewRequester(requesters.getValue(FaroForm.KIND)),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val issue = state.issues[FaroForm.KIND]
            Text(
                strings.kindLabel,
                style = MaterialTheme.typography.titleSmall,
                color = if (issue != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            KindPicker(form.kinds, state.kind, strings, accentInk, enabled = !sending, onSelect = model::chooseKind)
            when {
                issue != null -> IssueLine(strings.issueKind)
                state.kind != null -> Text(
                    strings.kindHint(state.kind),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Fields, in the team's order; two half-width fields share a row on wide screens.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth >= 520.dp
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                val rows = if (wide) pairHalves(form.fields) else form.fields.map { listOf(it) }
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { field ->
                            Box(Modifier.weight(1f).bringIntoViewRequester(requesters.getValue(field.key))) {
                                FieldInput(
                                    field = field,
                                    value = state.values[field.key].orEmpty(),
                                    issue = state.issues[field.key],
                                    strings = strings,
                                    enabled = !sending,
                                    onChange = { model.set(field.key, it) },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (options.allowAttachments) {
            Pictures(state, model, strings, accentInk, enabled = !sending)
        }

        if (options.showDeviceInfo && Faro.isConfigured) {
            DeviceInfo(options.extraDevice, strings)
        }

        // Notice and send.
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.notice?.let { NoticeLine(it, strings) }
            Button(
                onClick = model::send,
                enabled = !sending,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .semantics { if (sending) liveRegion = LiveRegionMode.Polite },
                shape = MaterialTheme.shapes.medium,
            ) {
                if (sending) {
                    androidx.compose.material3.CircularProgressIndicator(
                        Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(strings.sending)
                } else {
                    Icon(FaroIcons.Send, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(form.submit.ifEmpty { strings.send }, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        options.contactEmail?.takeIf { it.isNotBlank() }?.let { address ->
            SelectionContainer {
                Text(
                    String.format(Locale.ROOT, strings.contactFallback, address),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(Modifier.heightIn(min = 8.dp))
    }
}

private fun pairHalves(fields: List<FaroField>): List<List<FaroField>> {
    val rows = mutableListOf<List<FaroField>>()
    var pending: FaroField? = null
    for (field in fields) {
        if (field.halfWidth) {
            if (pending != null) {
                rows += listOf(pending, field)
                pending = null
            } else {
                pending = field
            }
        } else {
            pending?.let { rows += listOf(it) }
            pending = null
            rows += listOf(field)
        }
    }
    pending?.let { rows += listOf(it) }
    return rows
}

// ---- kinds ------------------------------------------------------------------------------------

private fun kindIcon(kind: FaroKind): ImageVector = when (kind) {
    FaroKind.PROBLEM -> FaroIcons.Bug
    FaroKind.SUGGESTION -> FaroIcons.Bulb
    FaroKind.QUESTION -> FaroIcons.Question
}

/**
 * The kinds as lamps in a row: the chosen one lights up in the accent. With large text (or a narrow
 * screen) they stack so no name is cut.
 */
@Composable
private fun KindPicker(
    kinds: List<FaroKind>,
    selected: FaroKind?,
    strings: FaroStrings,
    accentInk: Color,
    enabled: Boolean,
    onSelect: (FaroKind) -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stacked = maxWidth / kinds.size.coerceAtLeast(1) < 108.dp * fontScale
        val content: @Composable (FaroKind, Modifier) -> Unit = { kind, modifier ->
            KindLamp(kind, selected == kind, strings.kindName(kind), accentInk, stacked, enabled, modifier) { onSelect(kind) }
        }
        if (stacked) {
            Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                kinds.forEach { content(it, Modifier.fillMaxWidth()) }
            }
        } else {
            // Same height for all three, even when one name wraps.
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                kinds.forEach { content(it, Modifier.weight(1f).fillMaxHeight()) }
            }
        }
    }
}

@Composable
private fun KindLamp(
    kind: FaroKind,
    selected: Boolean,
    name: String,
    accentInk: Color,
    stacked: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    val background by animateColorAsState(if (selected) colors.primaryContainer else colors.surfaceContainerLow, label = "lamp")
    val border by animateColorAsState(if (selected) colors.primary else colors.outlineVariant, label = "edge")
    val lift by animateFloatAsState(if (selected) 1f else 0f, label = "lift")
    val tint = if (selected) accentInk else colors.onSurfaceVariant
    val inner: @Composable (Modifier) -> Unit = { textModifier ->
        Box(
            Modifier
                .size(36.dp)
                .graphicsLayer { scaleX = 1f + 0.08f * lift; scaleY = 1f + 0.08f * lift }
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(if (selected) colors.primary.copy(alpha = 0.18f) else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(kindIcon(kind), contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Text(
            name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) colors.onSurface else colors.onSurfaceVariant,
            textAlign = if (stacked) TextAlign.Start else TextAlign.Center,
            modifier = textModifier,
        )
        if (stacked && selected) Icon(FaroIcons.Check, null, tint = accentInk, modifier = Modifier.size(20.dp))
    }
    val base = modifier
        .clip(shape)
        .background(background)
        .border(if (selected) 1.5.dp else 1.dp, border, shape)
        .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
        .heightIn(min = if (stacked) 56.dp else 84.dp)
    if (stacked) {
        Row(
            base.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) { inner(Modifier.weight(1f)) }
    } else {
        Column(
            base.padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        ) { inner(Modifier) }
    }
}

// ---- fields -----------------------------------------------------------------------------------

private fun issueText(issue: FaroIssue, strings: FaroStrings): String = when (issue) {
    FaroIssue.REQUIRED -> strings.issueRequired
    FaroIssue.INVALID_EMAIL -> strings.issueEmail
    FaroIssue.TOO_SHORT -> strings.issueTooShort
    FaroIssue.TOO_LONG -> strings.issueTooLong
    FaroIssue.INVALID -> strings.issueInvalid
}

private fun fieldLabel(field: FaroField, strings: FaroStrings): String = field.label.ifEmpty {
    when (field.key) {
        FaroForm.NAME -> strings.nameLabel
        FaroForm.EMAIL -> strings.emailLabel
        FaroForm.MESSAGE -> strings.messageLabel
        else -> field.key
    }
}

@Composable
private fun FieldInput(
    field: FaroField,
    value: String,
    issue: FaroIssue?,
    strings: FaroStrings,
    enabled: Boolean,
    onChange: (String) -> Unit,
) {
    val label = fieldLabel(field, strings)
    when (field.type) {
        FaroFieldType.SELECT -> SelectField(field, label, value, issue, strings, enabled, onChange)
        FaroFieldType.CHECKBOX -> CheckboxField(label, field.help, value == "true", issue, strings, enabled) {
            onChange(if (it) "true" else "")
        }
        FaroFieldType.DATE -> DateField(field, label, value, issue, strings, enabled, onChange)
        else -> TextInput(field, label, value, issue, strings, enabled, onChange)
    }
}

@Composable
private fun TextInput(
    field: FaroField,
    label: String,
    value: String,
    issue: FaroIssue?,
    strings: FaroStrings,
    enabled: Boolean,
    onChange: (String) -> Unit,
) {
    val isMessage = field.key == FaroForm.MESSAGE
    val multiline = field.type == FaroFieldType.TEXTAREA
    val placeholder = field.placeholder.ifEmpty {
        when (field.key) {
            FaroForm.EMAIL -> strings.emailPlaceholder
            FaroForm.MESSAGE -> strings.messagePlaceholder
            else -> ""
        }
    }
    val help = field.help.ifEmpty { if (field.key == FaroForm.EMAIL) strings.emailHelp else "" }
    val errorText = issue?.let { issueText(it, strings) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        enabled = enabled,
        label = { Text(if (field.required || field.type == FaroFieldType.CHECKBOX) label else "$label · ${strings.optional}") },
        placeholder = placeholder.takeIf { it.isNotEmpty() }?.let { { Text(it) } },
        isError = issue != null,
        singleLine = !multiline,
        minLines = if (isMessage) 5 else if (multiline) 3 else 1,
        shape = MaterialTheme.shapes.medium,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        keyboardOptions = KeyboardOptions(
            capitalization = when {
                field.key == FaroForm.NAME -> KeyboardCapitalization.Words
                field.type == FaroFieldType.TEXT || multiline -> KeyboardCapitalization.Sentences
                else -> KeyboardCapitalization.None
            },
            keyboardType = when (field.type) {
                FaroFieldType.EMAIL -> KeyboardType.Email
                FaroFieldType.NUMBER -> KeyboardType.Decimal
                FaroFieldType.TEL -> KeyboardType.Phone
                FaroFieldType.URL -> KeyboardType.Uri
                else -> KeyboardType.Text
            },
            imeAction = if (multiline) ImeAction.Default else ImeAction.Next,
        ),
        supportingText = if (errorText != null || help.isNotEmpty() || isMessage) {
            {
                Row {
                    Text(errorText ?: help, modifier = Modifier.weight(1f))
                    if (isMessage) {
                        val count = value.codePointCount(0, value.length)
                        Text(
                            String.format(Locale.ROOT, strings.characterCount, count, FaroForm.MESSAGE_MAX),
                            fontFamily = FontFamily.Monospace,
                            color = if (count > FaroForm.MESSAGE_MAX) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        } else {
            null
        },
        modifier = Modifier
            .fillMaxWidth()
            .semantics { if (errorText != null) error(errorText) },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectField(
    field: FaroField,
    label: String,
    value: String,
    issue: FaroIssue?,
    strings: FaroStrings,
    enabled: Boolean,
    onChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldHeading(label, field.required, issue != null, strings)
        if (field.placeholder.isNotEmpty() && value.isEmpty()) {
            Text(field.placeholder, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // Options in their groups (if any), ungrouped first.
        val sections = buildList {
            val loose = field.options.filter { o -> o.group == null || field.groups.none { it.key == o.group } }
            if (loose.isNotEmpty()) add(null to loose)
            field.groups.forEach { g -> field.options.filter { it.group == g.key }.takeIf { it.isNotEmpty() }?.let { add(g.label to it) } }
        }
        sections.forEach { (heading, options) ->
            if (heading != null) {
                Text(
                    heading,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                options.forEach { option ->
                    val selected = value == option.value
                    FilterChip(
                        selected = selected,
                        enabled = enabled,
                        // Tapping the chosen option again clears an optional field.
                        onClick = { onChange(if (selected && !field.required) "" else option.value) },
                        label = { Text(option.label) },
                        leadingIcon = if (selected) {
                            { Icon(FaroIcons.Check, null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                        } else {
                            null
                        },
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.heightIn(min = 40.dp),
                    )
                }
            }
        }
        FieldFooter(field.help, issue, strings)
    }
}

@Composable
private fun CheckboxField(
    label: String,
    help: String,
    checked: Boolean,
    issue: FaroIssue?,
    strings: FaroStrings,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange)
                .heightIn(min = 48.dp)
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = checked, onCheckedChange = null, enabled = enabled, modifier = Modifier.padding(12.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (issue != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
        FieldFooter(help, issue, strings)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(
    field: FaroField,
    label: String,
    value: String,
    issue: FaroIssue?,
    strings: FaroStrings,
    enabled: Boolean,
    onChange: (String) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val format = remember { SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") } }
    val errorText = issue?.let { issueText(it, strings) }
    Box {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(if (field.required) label else "$label · ${strings.optional}") },
            placeholder = { Text("yyyy-mm-dd") },
            trailingIcon = { Icon(FaroIcons.Calendar, null) },
            isError = issue != null,
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            supportingText = (errorText ?: field.help.takeIf { it.isNotEmpty() })?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth().semantics { if (errorText != null) error(errorText) },
        )
        // The whole field opens the calendar.
        Box(
            Modifier
                .matchParentSize()
                .padding(bottom = if (errorText != null || field.help.isNotEmpty()) 24.dp else 0.dp)
                .clickable(enabled = enabled, role = Role.Button, onClickLabel = strings.pickDate) { open = true },
        )
    }
    if (open) {
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = runCatching { format.parse(value)?.time }.getOrNull(),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { onChange(format.format(Date(it))) }
                    open = false
                }) { Text(strings.done) }
            },
            dismissButton = {
                TextButton(onClick = {
                    if (!field.required) onChange("")
                    open = false
                }) { Text(if (field.required) strings.cancel else strings.clear) }
            },
        ) { DatePicker(state = picker) }
    }
}

@Composable
private fun FieldHeading(label: String, required: Boolean, hasIssue: Boolean, strings: FaroStrings) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = if (hasIssue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f, fill = false).semantics { heading() },
        )
        if (!required) {
            Text(
                strings.optional,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun FieldFooter(help: String, issue: FaroIssue?, strings: FaroStrings) {
    if (issue != null) {
        IssueLine(issueText(issue, strings))
    } else if (help.isNotEmpty()) {
        Text(help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun IssueLine(text: String) {
    Row(
        Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(FaroIcons.AlertCircle, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun NoticeLine(notice: Notice, strings: FaroStrings) {
    val (icon, text) = when (notice) {
        Notice.CHECK_FIELDS -> FaroIcons.AlertCircle to strings.checkFields
        Notice.OFFLINE -> FaroIcons.WifiOff to strings.noticeOffline
        Notice.RATE_LIMITED -> FaroIcons.Clock to strings.noticeRateLimited
        Notice.FAILED -> FaroIcons.AlertCircle to strings.noticeFailed
        Notice.IMAGE -> FaroIcons.PhotoPlus to strings.noticeImage
        Notice.UNAVAILABLE -> FaroIcons.AlertCircle to strings.noticeUnavailable
    }
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(colors.errorContainer)
            .padding(12.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, tint = colors.onErrorContainer, modifier = Modifier.size(18.dp))
        Text(text, color = colors.onErrorContainer, style = MaterialTheme.typography.bodySmall)
    }
}

// ---- what travels -----------------------------------------------------------------------------

@Composable
private fun DeviceInfo(extra: Map<String, String>, strings: FaroStrings) {
    var open by rememberSaveable { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (open) 180f else 0f, label = "chevron")
    val facts = remember(extra, open) { if (open) Faro.client.deviceFacts(extra) else emptyMap() }
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(colors.surfaceContainerLow)
            .border(1.dp, colors.outlineVariant, MaterialTheme.shapes.medium),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = if (open) strings.hide else strings.show) { open = !open }
                .heightIn(min = 52.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(FaroIcons.Device, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Text(strings.deviceTitle, style = MaterialTheme.typography.titleSmall, color = colors.onSurface, modifier = Modifier.weight(1f))
            Icon(FaroIcons.ChevronDown, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp).rotate(rotation))
        }
        if (open) {
            Column(
                Modifier
                    .padding(horizontal = 14.dp)
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .background(colors.surfaceContainerHighest.copy(alpha = 0.6f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                facts.forEach { (k, v) ->
                    Row(Modifier.clearAndSetSemantics { contentDescription = "$k: $v" }) {
                        Text(
                            k,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.width(110.dp),
                        )
                        Text(v, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = colors.onSurface)
                    }
                }
            }
        }
        Row(
            Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(FaroIcons.Shield, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Text(strings.deviceNever, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}
