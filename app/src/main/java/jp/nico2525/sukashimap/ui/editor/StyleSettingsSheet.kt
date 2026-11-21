package jp.nico2525.sukashimap.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import jp.nico2525.sukashimap.model.OverlayCorner
import jp.nico2525.sukashimap.model.OverlayFont
import jp.nico2525.sukashimap.model.OverlayStyle
import jp.nico2525.sukashimap.model.StylePreset
import jp.nico2525.sukashimap.model.TransportMode
import jp.nico2525.sukashimap.ui.components.ExpressiveColorSwatchRow
import jp.nico2525.sukashimap.ui.components.ExpressiveSectionCard
import jp.nico2525.sukashimap.ui.components.ExpressiveConnectedSegmentedRow
import jp.nico2525.sukashimap.ui.components.ExpressiveValueSlider
import java.util.Locale

@Composable
fun StyleSettingsSheet(
    style: OverlayStyle,
    mode: TransportMode,
    presets: List<StylePreset>,
    eyepickerBitmap: Bitmap?,
    onStyleChange: (OverlayStyle) -> Unit,
    onResetLayout: () -> Unit = {},
    onSavePreset: (String) -> Unit,
    onDeletePreset: (String) -> Unit,
    onDismiss: () -> Unit,
    panelMaxHeight: Dp = 460.dp,
    onHeightChanged: (Dp) -> Unit = {}
) {
    val density = LocalDensity.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var selectedTab by remember { mutableIntStateOf(0) }
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var presetNameInput by remember { mutableStateOf("") }

    val tabs = listOf("カラー", "フォント", "サイズ", "プリセット")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        scrimColor = Color.Transparent,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = panelMaxHeight)
                .onSizeChanged { onHeightChanged(with(density) { it.height.toDp() }) }
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "スタイル",
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(
                    onClick = onDismiss,
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text("閉じる")
                }
            }

            SecondaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent
            ) {
                tabs.forEachIndexed { idx, label ->
                    Tab(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        text = {
                            Text(
                                text = label,
                                style = if (selectedTab == idx) {
                                    MaterialTheme.typography.labelLargeEmphasized
                                } else {
                                    MaterialTheme.typography.labelLarge
                                }
                            )
                        }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (selectedTab) {
                    0 -> {
                        ExpressiveSectionCard(title = "テキスト") {
                            ExpressiveColorSwatchRow(
                                label = "文字色",
                                selectedColorArgb = style.textColorArgb,
                                onColorSelected = { onStyleChange(style.copy(textColorArgb = it)) },
                                eyepickerBitmap = eyepickerBitmap
                            )
                        }

                        ExpressiveSectionCard(title = "地図") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("地図を表示", style = MaterialTheme.typography.bodyMediumEmphasized)
                                Switch(
                                    checked = style.showMap,
                                    onCheckedChange = { onStyleChange(style.copy(showMap = it)) }
                                )
                            }

                            if (style.showMap) {
                                if (mode != TransportMode.SPOT) {
                                    ExpressiveColorSwatchRow(
                                        label = "ルート線",
                                        selectedColorArgb = style.routeColorArgb,
                                        onColorSelected = { onStyleChange(style.copy(routeColorArgb = it)) },
                                        eyepickerBitmap = eyepickerBitmap
                                    )
                                }
                                ExpressiveColorSwatchRow(
                                    label = "海岸線・境界線",
                                    selectedColorArgb = style.borderColorArgb,
                                    onColorSelected = { onStyleChange(style.copy(borderColorArgb = it)) },
                                    eyepickerBitmap = eyepickerBitmap
                                )
                            }
                        }
                    }

                    1 -> {
                        ExpressiveSectionCard(title = "フォント") {
                            OverlayFont.entries.forEach { fontEntry ->
                                val isSelected = style.font == fontEntry
                                val previewFamily = fontEntry.toComposeFontFamily()
                                ListItem(
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    leadingContent = {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { onStyleChange(style.copy(font = fontEntry)) }
                                        )
                                    },
                                    supportingContent = {
                                        Text(
                                            text = if (fontEntry == OverlayFont.JP_SANS || fontEntry == OverlayFont.JP_SERIF) {
                                                "日本語の透かし · 京都駅 · 2026/05/25"
                                            } else {
                                                "NH 1731 · KIX → OKA · 2026/05/25"
                                            },
                                            fontFamily = previewFamily,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    },
                                    modifier = Modifier
                                        .clip(MaterialTheme.shapes.medium)
                                        .clickable { onStyleChange(style.copy(font = fontEntry)) }
                                ) {
                                    Text(
                                        text = fontEntry.displayName,
                                        fontFamily = previewFamily,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }
                        }
                    }

                    2 -> {
                        ExpressiveSectionCard(title = "サイズ（%）・配置") {
                            Text("テキストの位置", style = MaterialTheme.typography.bodyMedium)
                            ExpressiveConnectedSegmentedRow(
                                options = OverlayCorner.entries.toList(),
                                selected = style.textCorner,
                                groupLabel = "テキストの基準位置",
                                onSelect = { corner ->
                                    onStyleChange(style.copy(textCorner = corner, textFreePos = null))
                                },
                                labelProvider = ::cornerLabel
                            )
                            Text("地図の位置", style = MaterialTheme.typography.bodyMedium)
                            ExpressiveConnectedSegmentedRow(
                                options = OverlayCorner.entries.toList(),
                                selected = style.mapCorner,
                                groupLabel = "地図の基準位置",
                                onSelect = { corner ->
                                    onStyleChange(style.copy(mapCorner = corner, mapFreePos = null))
                                },
                                labelProvider = ::cornerLabel
                            )
                            PercentSlider(
                                label = "文字全体",
                                value = style.textScale,
                                onValueChange = { onStyleChange(style.copy(textScale = it)) }
                            )
                            PercentSlider(
                                label = "タイトル",
                                value = style.titleScale,
                                onValueChange = { onStyleChange(style.copy(titleScale = it)) }
                            )
                            PercentSlider(
                                label = "サブタイトル",
                                value = style.subtitleScale,
                                onValueChange = { onStyleChange(style.copy(subtitleScale = it)) }
                            )
                            PercentSlider(
                                label = "座標・地名",
                                value = style.locationScale,
                                onValueChange = { onStyleChange(style.copy(locationScale = it)) }
                            )
                            PercentSlider(
                                label = "日付",
                                value = style.dateScale,
                                onValueChange = { onStyleChange(style.copy(dateScale = it)) }
                            )
                            PercentSlider(
                                label = "地図",
                                value = style.mapBoxScale,
                                onValueChange = { onStyleChange(style.copy(mapBoxScale = it)) }
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("地図内部ズーム", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    String.format(Locale.US, "%.2f×", style.mapInternalZoom),
                                    style = MaterialTheme.typography.labelLargeEmphasized
                                )
                            }
                            ExpressiveValueSlider(
                                value = style.mapInternalZoom,
                                onValueChange = { onStyleChange(style.copy(mapInternalZoom = it)) },
                                valueRange = 0.5f..5.0f
                            )

                            OutlinedButton(
                                onClick = onResetLayout,
                                shapes = ButtonDefaults.shapes(),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("位置・拡大率をリセット")
                            }
                        }
                    }

                    3 -> {
                        ExpressiveSectionCard(
                            title = "保存済みプリセット",
                            trailingHeader = {
                                FilledTonalButton(
                                    onClick = { showSavePresetDialog = true },
                                    shapes = ButtonDefaults.shapes()
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("現在のスタイルを保存")
                                }
                            }
                        ) {
                            presets.forEach { p ->
                                ListItem(
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    supportingContent = { Text(p.style.font.displayName) },
                                    trailingContent = if (p.id.startsWith("custom_")) {
                                        {
                                            IconButton(
                                                onClick = { onDeletePreset(p.id) },
                                                shapes = IconButtonDefaults.shapes()
                                            ) {
                                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "削除")
                                            }
                                        }
                                    } else null,
                                    modifier = Modifier
                                        .clip(MaterialTheme.shapes.medium)
                                        .clickable { onStyleChange(p.style) }
                                ) {
                                    Text(p.name, style = MaterialTheme.typography.bodyLargeEmphasized)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (showSavePresetDialog) {
        AlertDialog(
            onDismissRequest = { showSavePresetDialog = false },
            title = { Text("プリセットを保存") },
            text = {
                OutlinedTextField(
                    value = presetNameInput,
                    onValueChange = { presetNameInput = it },
                    label = { Text("名前") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSavePreset(presetNameInput)
                        presetNameInput = ""
                        showSavePresetDialog = false
                    },
                    shapes = ButtonDefaults.shapes()
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSavePresetDialog = false },
                    shapes = ButtonDefaults.shapes()
                ) { Text("キャンセル") }
            }
        )
    }
}

private fun cornerLabel(corner: OverlayCorner): String = when (corner) {
    OverlayCorner.TOP_LEFT -> "左上"
    OverlayCorner.TOP_RIGHT -> "右上"
    OverlayCorner.BOTTOM_LEFT -> "左下"
    OverlayCorner.BOTTOM_RIGHT -> "右下"
}

@Composable
private fun PercentSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            String.format(Locale.US, "%.0f%%", value * 100f),
            style = MaterialTheme.typography.labelLargeEmphasized
        )
    }
    ExpressiveValueSlider(
        value = value,
        onValueChange = onValueChange,
        valueRange = 0.5f..2.5f,
        steps = 199
    )
}
