package jp.nico2525.sukashimap.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import jp.nico2525.sukashimap.export.MediaExporter
import jp.nico2525.sukashimap.model.CropAspectPreset
import jp.nico2525.sukashimap.model.CropFrameSettings
import jp.nico2525.sukashimap.ui.components.ExpressiveConnectedSegmentedRow
import jp.nico2525.sukashimap.ui.components.ExpressiveSectionCard
import jp.nico2525.sukashimap.ui.components.ExpressiveValueSlider
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun CropFrameSheet(
    rawBitmap: Bitmap?,
    isVideo: Boolean = false,
    initialSettings: CropFrameSettings,
    onDismiss: () -> Unit,
    onApply: (CropFrameSettings) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draft by remember(initialSettings) { mutableStateOf(initialSettings) }
    val latestDraft by rememberUpdatedState(draft)

    val thumbSource = remember(rawBitmap) {
        rawBitmap?.let { src ->
            val maxDim = max(src.width, src.height)
            if (maxDim <= 640) src
            else {
                val scale = 640f / maxDim
                Bitmap.createScaledBitmap(
                    src,
                    (src.width * scale).roundToInt().coerceAtLeast(1),
                    (src.height * scale).roundToInt().coerceAtLeast(1),
                    true
                )
            }
        }
    }

    val previewBmp = remember(thumbSource, draft) {
        thumbSource?.let { MediaExporter.applyCropAndFrameToBitmap(it, draft) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "切り抜き・余白枠",
                    style = MaterialTheme.typography.headlineSmallEmphasized
                )
                Button(
                    onClick = { onApply(draft) },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text("適用")
                }
            }

            if (previewBmp != null && !isVideo) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val cur = latestDraft
                                val newZoom = (cur.cropZoom * zoom).coerceIn(1f, 3.5f)
                                val dx = pan.x / size.width.coerceAtLeast(1)
                                val dy = pan.y / size.height.coerceAtLeast(1)
                                draft = cur.copy(
                                    cropZoom = newZoom,
                                    cropCenterX = (cur.cropCenterX - dx * 0.45f).coerceIn(0f, 1f),
                                    cropCenterY = (cur.cropCenterY - dy * 0.45f).coerceIn(0f, 1f)
                                )
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = previewBmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    )
                }
            }

            if (!isVideo) {
                ExpressiveSectionCard(
                    title = "比率",
                    trailingHeader = {
                        FilledTonalIconButton(
                            onClick = { draft = draft.copy(isLandscapeOrientation = !draft.isLandscapeOrientation) },
                            shapes = IconButtonDefaults.shapes()
                        ) {
                            Icon(Icons.Rounded.ScreenRotation, contentDescription = "縦横切替")
                        }
                    }
                ) {
                    val row1 = listOf(CropAspectPreset.ORIGINAL, CropAspectPreset.FREE, CropAspectPreset.RATIO_1_1)
                    val row2 = listOf(CropAspectPreset.RATIO_4_5, CropAspectPreset.RATIO_9_16, CropAspectPreset.RATIO_2_3)

                    ExpressiveConnectedSegmentedRow(
                        options = row1,
                        selected = draft.aspectPreset,
                        onSelect = { draft = draft.copy(aspectPreset = it) },
                        labelProvider = { it.labelJa }
                    )

                    ExpressiveConnectedSegmentedRow(
                        options = row2,
                        selected = draft.aspectPreset,
                        onSelect = { draft = draft.copy(aspectPreset = it) },
                        labelProvider = { preset ->
                            if (draft.isLandscapeOrientation) {
                                when (preset) {
                                    CropAspectPreset.RATIO_4_5 -> "5:4"
                                    CropAspectPreset.RATIO_9_16 -> "16:9"
                                    CropAspectPreset.RATIO_2_3 -> "3:2"
                                    else -> preset.labelJa
                                }
                            } else {
                                preset.labelJa
                            }
                        }
                    )

                    if (draft.aspectPreset == CropAspectPreset.FREE) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("自由比率 (横/縦)", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                String.format(java.util.Locale.US, "%.2f", draft.freeAspectRatio),
                                style = MaterialTheme.typography.labelLargeEmphasized
                            )
                        }
                        ExpressiveValueSlider(
                            value = draft.freeAspectRatio,
                            onValueChange = { draft = draft.copy(freeAspectRatio = it) },
                            valueRange = 0.4f..2.5f
                        )
                    }
                }
            }

            ExpressiveSectionCard(title = "余白フレーム") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("余白幅", style = MaterialTheme.typography.bodyMedium)
                    Text("${(draft.frameMarginRatio * 100).roundToInt()}%", style = MaterialTheme.typography.labelLargeEmphasized)
                }
                ExpressiveValueSlider(
                    value = draft.frameMarginRatio,
                    onValueChange = { draft = draft.copy(frameMarginRatio = it) },
                    valueRange = 0f..0.49f
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("背景色", style = MaterialTheme.typography.bodyMedium)
                    val frameSwatches = listOf(
                        0xFFFFFFFF.toInt() to "白",
                        0xFFF6F2EA.toInt() to "生成り",
                        0xFF121418.toInt() to "黒"
                    )
                    frameSwatches.forEach { (argb, name) ->
                        val selected = draft.frameColorArgb == argb
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable { draft = draft.copy(frameColorArgb = argb) },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(argb))
                                    .border(
                                        BorderStroke(
                                            if (selected) 2.5.dp else 1.dp,
                                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                        ),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) {
                                    Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = name,
                                        tint = if (Color(argb).luminance() > 0.5f) Color.Black else Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}
