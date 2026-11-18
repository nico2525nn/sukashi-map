package jp.nico2525.sukashimap.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import jp.nico2525.sukashimap.model.OverlayColorPresets

/**
 * Canonical Material 3 Expressive Connected ButtonGroup using `ButtonGroupScope.animateWidth`
 * and `ButtonGroupDefaults.connected*ButtonShapes()`.
 */
@Composable
fun <T> ExpressiveConnectedSegmentedRow(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    labelProvider: (T) -> String = { "" },
    iconProvider: ((T) -> ImageVector?)? = null
) {
    val horizPad = if (options.size >= 4) 6.dp else 10.dp
    ButtonGroup(
        overflowIndicator = {},
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        options.forEachIndexed { index, item ->
            customItem(
                buttonGroupContent = {
                    val isSelected = item == selected
                    val interactionSource = remember(index) { MutableInteractionSource() }
                    val shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    }
                    FilledTonalToggleButton(
                        checked = isSelected,
                        onCheckedChange = { onSelect(item) },
                        shapes = shapes,
                        interactionSource = interactionSource,
                        contentPadding = PaddingValues(horizontal = horizPad, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .animateWidth(interactionSource)
                    ) {
                        val icon = iconProvider?.invoke(item)
                        val text = labelProvider(item)
                        if (icon != null) {
                            Icon(
                                imageVector = icon,
                                contentDescription = text.ifBlank { null },
                                modifier = Modifier.size(if (text.isBlank()) 20.dp else 16.dp)
                            )
                            if (text.isNotBlank()) {
                                Spacer(Modifier.width(if (options.size >= 4) 4.dp else 6.dp))
                            }
                        }
                        if (text.isNotBlank()) {
                            Text(
                                text = text,
                                style = if (options.size >= 4) {
                                    if (isSelected) MaterialTheme.typography.labelMediumEmphasized else MaterialTheme.typography.labelMedium
                                } else {
                                    if (isSelected) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelLarge
                                },
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                menuContent = {}
            )
        }
    }
}

/**
 * Canonical Material 3 Expressive Slider using `SliderState`.
 */
@Composable
fun ExpressiveValueSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null
) {
    val sliderState = rememberSliderState(
        value.coerceIn(valueRange.start, valueRange.endInclusive),
        steps,
        valueRange
    )
    SideEffect {
        val clamped = value.coerceIn(valueRange.start, valueRange.endInclusive)
        if (sliderState.value != clamped) {
            sliderState.value = clamped
        }
    }
    Slider(
        state = sliderState,
        onValueChange = {
            sliderState.value = it
            onValueChange(it)
        },
        onValueChangeFinished = onValueChangeFinished,
        colors = SliderDefaults.colors(),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun ExpressiveSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    trailingHeader: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title.isNotBlank() || trailingHeader != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                trailingHeader?.invoke()
            }
        }
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
        }
    }
}

@Composable
fun ExpressiveColorSwatchRow(
    label: String,
    selectedColorArgb: Int,
    onColorSelected: (Int) -> Unit,
    eyepickerBitmap: Bitmap? = null
) {
    var showCustomDialog by remember { mutableStateOf(false) }
    var showEyedropperDialog by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (eyepickerBitmap != null) {
                    AssistChip(
                        onClick = { showEyedropperDialog = true },
                        label = { Text("スポイト") },
                        leadingIcon = {
                            Icon(Icons.Rounded.Colorize, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                }
                AssistChip(
                    onClick = { showCustomDialog = true },
                    label = { Text("カスタム") },
                    leadingIcon = {
                        Icon(Icons.Rounded.Palette, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OverlayColorPresets.swatchesArgb.forEach { argb ->
                val isSelected = selectedColorArgb == argb
                val ringWidth by animateDpAsState(if (isSelected) 2.5.dp else 1.dp, label = "swatchRing")
                val ringColor by animateColorAsState(
                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    label = "swatchColor"
                )
                val fill = Color(argb)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = { onColorSelected(argb) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(fill)
                            .border(BorderStroke(ringWidth, ringColor), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = if (fill.luminance() > 0.55f) Color.Black else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            if (selectedColorArgb !in OverlayColorPresets.swatchesArgb) {
                val customFill = Color(selectedColorArgb)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button) { showCustomDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(customFill)
                            .border(BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = if (customFill.luminance() > 0.55f) Color.Black else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }

    if (showCustomDialog) {
        CustomColorPickerDialog(
            initialArgb = selectedColorArgb,
            onDismiss = { showCustomDialog = false },
            onConfirm = {
                onColorSelected(it)
                showCustomDialog = false
            }
        )
    }

    if (showEyedropperDialog && eyepickerBitmap != null) {
        PhotoEyedropperDialog(
            bitmap = eyepickerBitmap,
            onDismiss = { showEyedropperDialog = false },
            onPicked = {
                onColorSelected(it)
                showEyedropperDialog = false
            }
        )
    }
}

@Composable
private fun CustomColorPickerDialog(
    initialArgb: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val hsvInit = remember(initialArgb) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initialArgb, it) }
    }
    var hue by remember { mutableFloatStateOf(hsvInit[0]) }
    var sat by remember { mutableFloatStateOf(hsvInit[1]) }
    var value by remember { mutableFloatStateOf(hsvInit[2]) }
    val currentArgb = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))
    var hexInput by remember(currentArgb) {
        mutableStateOf(String.format("#%06X", 0xFFFFFF and currentArgb))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("カスタムカラー") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(Color(currentArgb))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                )
                Text("色相", style = MaterialTheme.typography.labelMedium)
                ExpressiveValueSlider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f)
                Text("彩度", style = MaterialTheme.typography.labelMedium)
                ExpressiveValueSlider(value = sat, onValueChange = { sat = it }, valueRange = 0f..1f)
                Text("明度", style = MaterialTheme.typography.labelMedium)
                ExpressiveValueSlider(value = value, onValueChange = { value = it }, valueRange = 0f..1f)
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { txt ->
                        hexInput = txt
                        val clean = txt.trim().removePrefix("#")
                        if (clean.length == 6) {
                            clean.toIntOrNull(16)?.let { rgb ->
                                val f = FloatArray(3)
                                android.graphics.Color.colorToHSV(0xFF000000.toInt() or rgb, f)
                                hue = f[0]; sat = f[1]; value = f[2]
                            }
                        }
                    },
                    label = { Text("HEX (#RRGGBB)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(currentArgb) }, shapes = ButtonDefaults.shapes()) { Text("適用") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text("キャンセル") }
        }
    )
}

@Composable
private fun PhotoEyedropperDialog(
    bitmap: Bitmap,
    onDismiss: () -> Unit,
    onPicked: (Int) -> Unit
) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var tapNorm by remember { mutableStateOf(Offset(0.5f, 0.5f)) }
    val sampledColor = remember(bitmap, tapNorm) {
        val x = (tapNorm.x * (bitmap.width - 1)).toInt().coerceIn(0, bitmap.width - 1)
        val y = (tapNorm.y * (bitmap.height - 1)).toInt().coerceIn(0, bitmap.height - 1)
        bitmap.getPixel(x, y) or 0xFF000000.toInt()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("写真から色を抽出") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .onSizeChanged { boxSize = it }
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                if (boxSize.width > 0 && boxSize.height > 0) {
                                    tapNorm = Offset(
                                        (offset.x / boxSize.width).coerceIn(0f, 1f),
                                        (offset.y / boxSize.height).coerceIn(0f, 1f)
                                    )
                                }
                            }
                        }
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cx = tapNorm.x * size.width
                        val cy = tapNorm.y * size.height
                        drawCircle(Color.White, radius = 14.dp.toPx(), center = Offset(cx, cy), style = Stroke(3.dp.toPx()))
                        drawCircle(Color(sampledColor), radius = 10.dp.toPx(), center = Offset(cx, cy))
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(sampledColor))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    )
                    Text(
                        text = String.format("#%06X", 0xFFFFFF and sampledColor),
                        style = MaterialTheme.typography.bodyMediumEmphasized
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPicked(sampledColor) }, shapes = ButtonDefaults.shapes()) { Text("適用") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text("キャンセル") }
        }
    )
}
