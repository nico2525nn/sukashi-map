package jp.nico2525.sukashimap.ui.settings

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import jp.nico2525.sukashimap.model.ExportFormat
import jp.nico2525.sukashimap.ui.SukashiViewModel
import jp.nico2525.sukashimap.ui.components.ExpressiveConnectedSegmentedRow
import jp.nico2525.sukashimap.ui.components.ExpressiveSectionCard
import jp.nico2525.sukashimap.ui.theme.ThemeMode
import kotlinx.coroutines.launch

@Composable
fun SettingsSheet(
    viewModel: SukashiViewModel,
    onDismiss: () -> Unit
) {
    val prefs by viewModel.prefsState.collectAsState()
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showAttribution by remember { mutableStateOf(false) }

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
            Text(
                text = "設定",
                style = MaterialTheme.typography.headlineSmallEmphasized,
                color = MaterialTheme.colorScheme.onSurface
            )

            ExpressiveSectionCard(title = "外観") {
                ExpressiveConnectedSegmentedRow(
                    options = ThemeMode.entries,
                    selected = prefs.themeMode,
                    onSelect = { viewModel.setThemeMode(it) },
                    labelProvider = {
                        when (it) {
                            ThemeMode.SYSTEM -> "システム"
                            ThemeMode.LIGHT -> "ライト"
                            ThemeMode.DARK -> "ダーク"
                        }
                    }
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("ダイナミックカラー", style = MaterialTheme.typography.bodyMediumEmphasized)
                            Text(
                                "壁紙の色に合わせて配色を自動調整",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = prefs.dynamicColor,
                            onCheckedChange = { viewModel.setDynamicColor(it) }
                        )
                    }
                }
            }

            ExpressiveSectionCard(title = "保存・位置情報") {
                Text("画像の保存形式", style = MaterialTheme.typography.bodyMediumEmphasized)
                ExpressiveConnectedSegmentedRow(
                    options = ExportFormat.entries,
                    selected = prefs.defaultExportFormat,
                    onSelect = { viewModel.setDefaultExportFormat(it) },
                    labelProvider = { it.label }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Ultra HDR / HDR 色域を保持", style = MaterialTheme.typography.bodyMediumEmphasized)
                        Text(
                            "対応端末のHDR輝度マップを維持して書き出し",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = prefs.preserveUltraHdr,
                        onCheckedChange = { viewModel.setPreserveHdr(it) }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("カメラ撮影時の現在地補完", style = MaterialTheme.typography.bodyMediumEmphasized)
                        Text(
                            "アプリ内カメラで撮影した場合に端末位置を付与",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = prefs.autoLocationEnabled,
                        onCheckedChange = { viewModel.setAutoLocation(it) }
                    )
                }

                TextButton(
                    onClick = {
                        scope.launch {
                            viewModel.preferencesRepository.clearHistoryForMode(null)
                            viewModel.emitMessage("入力履歴を消去しました")
                        }
                    },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text("最近の入力履歴をクリア")
                }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = { showAttribution = !showAttribution },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(if (showAttribution) "地図データ・フォント出典を閉じる" else "地図データ・フォント出典を表示")
                }
                AnimatedVisibility(visible = showAttribution) {
                    ExpressiveSectionCard(title = "") {
                        Text("• 海岸線・行政区域・鉄道データ: 国土交通省 国土数値情報 (N03 / N02)", style = MaterialTheme.typography.bodySmall)
                        Text("• 湖沼データ: 国土地理院 地球地図日本", style = MaterialTheme.typography.bodySmall)
                        Text("• 世界地図・空港: Natural Earth / U.S. Census Bureau / OpenFlights", style = MaterialTheme.typography.bodySmall)
                        Text("• 道路経路・地名検索: © OpenStreetMap contributors / OSRM / Photon", style = MaterialTheme.typography.bodySmall)
                        Text("• フォント: Barlow, JetBrains Mono, DM Sans, Outfit, Kaisei Tokumin (OFL 1.1)", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
