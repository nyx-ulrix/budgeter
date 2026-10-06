package com.nyxulrix.budgeter.ui

import android.app.DatePickerDialog
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.time.LocalDate

/** Hard offset shadow, drawn behind and outside the element like the brief's `box-shadow: 4px 4px 0`. */
fun Modifier.pixelShadow(d: Dp = 4.dp, color: Color = Px.brown) = drawBehind {
    drawRect(color, topLeft = Offset(d.toPx(), d.toPx()), size = size)
}

fun Modifier.frame(bg: Color = Px.creamLight, border: Color = Px.brown, width: Dp = 3.dp) =
    background(bg).border(width, border)

/** Framed application window with an orange title strip. */
@Composable
fun Window(
    title: String,
    modifier: Modifier = Modifier,
    header: Color = Px.orange,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().pixelShadow().frame()) {
        Row(
            Modifier.fillMaxWidth().background(header).heightIn(min = 36.dp).padding(start = 10.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title.uppercase(), style = Type.title, color = Px.creamLight, modifier = Modifier.weight(1f).semantics { heading() })
            actions()
        }
        Box(Modifier.fillMaxWidth().height(3.dp).background(Px.brown))
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

/** Window that folds to its title bar. */
@Composable
fun FoldWindow(title: String, open: Boolean = false, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    var isOpen by rememberSaveable(title) { mutableStateOf(open) }
    Column(modifier.fillMaxWidth().pixelShadow().frame()) {
        Row(
            Modifier.fillMaxWidth().background(if (isOpen) Px.orange else Px.cream).heightIn(min = 44.dp)
                .clickable(role = Role.Button, onClickLabel = if (isOpen) "Collapse" else "Expand") { isOpen = !isOpen }
                .padding(horizontal = 10.dp)
                .semantics { stateDescription = if (isOpen) "Expanded" else "Collapsed" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title.uppercase(), style = Type.title, color = if (isOpen) Px.creamLight else Px.brown, modifier = Modifier.weight(1f).semantics { heading() })
            Text(if (isOpen) "[-]" else "[+]", style = Type.title, color = if (isOpen) Px.creamLight else Px.brown)
        }
        if (isOpen) {
            Box(Modifier.fillMaxWidth().height(3.dp).background(Px.brown))
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        }
    }
}

enum class Kind { PRIMARY, SECONDARY, DANGER }

/** Physical-feeling button: hard shadow that collapses when pressed. */
@Composable
fun PixelButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: Kind = Kind.PRIMARY,
    enabled: Boolean = true,
    glyph: List<String>? = null,
    @DrawableRes art: Int? = null,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val bg = when {
        !enabled -> Px.cream
        kind == Kind.PRIMARY -> if (pressed) Px.orangeBright else Px.orange
        kind == Kind.DANGER -> Px.red
        else -> Px.creamLight
    }
    val fg = if (!enabled) Px.muted else if (kind == Kind.SECONDARY) Px.brown else Px.creamLight
    Box(
        modifier
            .defaultMinSize(minHeight = 48.dp, minWidth = 48.dp)
            .offset { if (pressed) IntOffset(3.dp.roundToPx(), 3.dp.roundToPx()) else IntOffset.Zero }
            .then(if (pressed || !enabled) Modifier else Modifier.pixelShadow(3.dp))
            .frame(bg)
            .clickable(src, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            glyph?.let { Glyph(it, fg, 16.dp) }
            art?.let { Art(it, 20.dp) }
            if (text.isNotEmpty()) Text(text.uppercase(), style = Type.button, color = fg)
        }
    }
}

/** Pixel-art image scaled with nearest-neighbour so edges stay hard. */
@Composable
fun Art(@DrawableRes res: Int, size: Dp, modifier: Modifier = Modifier, description: String? = null) = Image(
    ImageBitmap.imageResource(res), description, modifier.size(size), filterQuality = FilterQuality.None,
)

@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color = Px.brown) =
    Text(text.uppercase(), style = Type.label, color = color, modifier = modifier)

@Composable
fun Body(text: String, modifier: Modifier = Modifier, color: Color = Px.brown) =
    Text(text, style = Type.body, color = color, modifier = modifier)

@Composable
fun Small(text: String, modifier: Modifier = Modifier, color: Color = Px.muted) =
    Text(text, style = Type.small, color = color, modifier = modifier)

/** Label + value on one line, value right-aligned. */
@Composable
fun KeyValue(key: String, value: String, valueColor: Color = Px.brown) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Body(key, Modifier.weight(1f))
        Text(value, style = Type.body, color = valueColor)
    }
}

/** Old-dialog text field. Orange border and hard shadow while focused. */
@Composable
fun PixelField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    placeholder: String = "",
    singleLine: Boolean = true,
    error: String? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (label.isNotEmpty()) Label(label)
        BasicTextField(
            value, onChange,
            singleLine = singleLine,
            textStyle = Type.body,
            cursorBrush = SolidColor(Px.orange),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            modifier = Modifier.fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .then(if (focused) Modifier.pixelShadow(3.dp) else Modifier)
                .frame(border = if (error != null) Px.red else if (focused) Px.orange else Px.brown)
                .heightIn(min = 44.dp)
                .padding(horizontal = 8.dp, vertical = 10.dp)
                .semantics { contentDescription = label.ifEmpty { placeholder } },
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text(placeholder, style = Type.body, color = Px.muted)
                    inner()
                }
            },
        )
        if (error != null) Text("! $error", style = Type.small, color = Px.red)
    }
}

/** Segmented bar of blocks, like a retro loading bar. Text label carries the meaning; colour is a cue. */
@Composable
fun PixelProgress(fraction: Float, modifier: Modifier = Modifier, color: Color = Px.orange, blocks: Int = 20) {
    val filled = (fraction.coerceIn(0f, 1f) * blocks).let { if (it > 0f && it < 1f) 1 else it.toInt() }
    Row(
        modifier.fillMaxWidth().frame(Px.cream).padding(3.dp).semantics { contentDescription = "${(fraction * 100).toInt()} percent" },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(blocks) { i ->
            Box(Modifier.weight(1f).height(14.dp).background(if (i < filled) color else Px.creamLight))
        }
    }
}

/** Square status chip with a symbol + word so state never relies on colour alone. */
@Composable
fun Chip(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.frame(color, width = 2.dp).padding(horizontal = 6.dp, vertical = 3.dp)) {
        Text(text.uppercase(), style = Type.label, color = if (color == Px.cream || color == Px.creamLight) Px.brown else Px.creamLight)
    }
}

/** One-of-many picker drawn as a row of square tabs. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> Choice(options: List<T>, selected: T?, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { o ->
            val on = o == selected
            Box(
                Modifier.heightIn(min = 40.dp).frame(if (on) Px.orange else Px.creamLight, width = 2.dp)
                    .clickable(role = Role.RadioButton) { onSelect(o) }
                    .semantics { stateDescription = if (on) "Selected" else "Not selected" }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label(o).uppercase(), style = Type.label, color = if (on) Px.creamLight else Px.brown) }
        }
    }
}

/** Date button that opens the platform date picker. */
@Composable
fun DateField(label: String, date: LocalDate, onChange: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Label(label)
        Box(
            Modifier.fillMaxWidth().frame().heightIn(min = 44.dp)
                .clickable(role = Role.Button, onClickLabel = "Change $label") {
                    DatePickerDialog(ctx, { _, y, m, d -> onChange(LocalDate.of(y, m + 1, d)) }, date.year, date.monthValue - 1, date.dayOfMonth).show()
                }
                .padding(horizontal = 8.dp, vertical = 10.dp),
        ) { Body(date.toString()) }
    }
}

/** Modal window. */
@Composable
fun PixelDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Window(title, Modifier.padding(4.dp), actions = { CloseButton(onDismiss) }) {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        }
    }
}

@Composable
fun CloseButton(onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clickable(role = Role.Button, onClickLabel = "Close", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(24.dp).frame(Px.creamLight, width = 2.dp), contentAlignment = Alignment.Center) {
            Text("X", style = Type.title, color = Px.brown)
        }
    }
}

/** Decorative pixel rule. */
@Composable
fun Rule(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(40) { Box(Modifier.width(4.dp).height(2.dp).background(if (it % 2 == 0) Px.brown else Color.Transparent)) }
    }
}

@Composable
fun Gap(h: Dp = 8.dp) = Spacer(Modifier.height(h))
