package io.github.laozhudu.linkplay.console.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.laozhudu.linkplay.console.DeviceCard
import io.github.laozhudu.linkplay.console.ui.components.AppShapesExpressive
import io.github.laozhudu.linkplay.console.ui.components.CircleIcon
import io.github.laozhudu.linkplay.console.ui.components.PillTag
import io.github.laozhudu.linkplay.console.ui.components.VSpace
import io.github.laozhudu.linkplay.console.ui.theme.OnDarkPalette
import io.github.laozhudu.linkplay.console.ui.theme.Spacing
import io.github.laozhudu.linkplay.console.ui.theme.StatusColors
import io.github.laozhudu.linkplay.console.ui.theme.seedOf
import io.github.laozhudu.linkplay.protocol.session.SavedDevice

/**
 * 设备卡片（按原厂样式重做，同时保留我们自己的信息）。
 *
 * 原厂那张卡的构成，从上到下：
 * ```
 * doss1                                                  ⚙
 * ┌────┐  000.凯叔·三国演义·引子
 * │封面 │
 * └────┘
 * 🔊 ──────●──────                              [R]
 * ```
 * 我们额外保留的（原厂没有，但这是我们更好用的地方）：
 * - 设备 IP / 在线状态 / 「同步播放中 · 属 dossX」—— 组内从机连不上时不会误报离线；
 * - 音量条拖动**立刻**改设备音量（原厂也是这个交互，但原厂没有我们的合并通道，
 *   拖动时会打出一串 HTTP 请求）；
 * - 组网/声道/屏蔽都在卡片上直接可点，不用进二级页。
 *
 * 封面：设备不回填元数据（`getPlayerStatus` 的 Title 是 HEX，且没有封面地址），
 * 所以这里不假装有封面 —— 用曲名首字 + 派生色块，和无封面时的通用做法一致。
 */
@Composable
internal fun DeviceCardRow(
    device: SavedDevice,
    online: Boolean?,
    groupedUnder: String?,
    card: DeviceCard?,
    /** 在组里（主机或从机）—— 声道标签才可点。 */
    inGroup: Boolean = false,
    /** 主机（不能屏蔽、不能改声道）。 */
    isMaster: Boolean = false,
    /** 已被屏蔽（`mask=1`，不出声但留在组里）。 */
    masked: Boolean = false,
    /** 设备能力档（只用于显示"通用 DLNA"这类**类型标签**，不用于决定渲染什么按钮）。 */
    profile: io.github.laozhudu.linkplay.protocol.dlna.DeviceProfile =
        io.github.laozhudu.linkplay.protocol.dlna.DeviceProfile.UNKNOWN,
    /**
     * 这台设备具备的能力 —— **决定渲染哪些按钮的唯一判据**。
     *
     * 默认 [DeviceCapabilities.LINKPLAY]：卡片还没探测到能力时按功能全开显示，
     * 与 `DeviceProfileCache` 的默认一致（宁可按全开处理，不要因为一次没探测到就藏掉按钮）。
     */
    capabilities: io.github.laozhudu.linkplay.protocol.dlna.DeviceCapabilities =
        io.github.laozhudu.linkplay.protocol.dlna.DeviceCapabilities.LINKPLAY,
    onOpen: () -> Unit,
    onSettings: () -> Unit,
    onVolume: (Int) -> Unit,
    onSetChannel: (Int) -> Unit = {},
    onUngroup: () -> Unit = {},
    onMask: () -> Unit = {},
    onTogglePlay: () -> Unit = {},
    /** 齿轮：打开这台设备的操作菜单。 */
    onRename: () -> Unit = {},
) {
    val palette = remember(device.uuid) { seedOf(device.name, device.ip) }
    // 拖动时的本地乐观值：不这样会出现"手指在动、旋钮不动"（要等 3 秒轮询回读）
    var dragging by remember(device.uuid) { mutableStateOf<Int?>(null) }
    var confirmUngroup by remember(device.uuid) { mutableStateOf(false) }
    // 声道面板：三选一（M3 single-select / Sonos 立体声对的取值方式），而不是"点一下轮一个"
    var channelDialog by remember(device.uuid) { mutableStateOf(false) }
    val volume = dragging ?: card?.volume ?: 0
    val channel = card?.channel
    val title = card?.title

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapesExpressive.squircle)
            .background(
                Brush.linearGradient(listOf(palette.top, palette.accent.copy(alpha = 0.28f))),
            )
            .clickable(onClick = onOpen)
            .padding(Spacing.md),
    ) {
        // 第一行：设备名 + 电量 + 状态标签 + 齿轮
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        device.title,
                        style = MaterialTheme.typography.titleLarge,
                        // 卡片底是派生深色渐变，文字必须恒定白：跟主题变会在浅色模式下隐形
                        color = OnDarkPalette.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // 电量**放在名字这一行**，不放副标题行：
                    // 副标题要显示 IP/在线状态，长 IP 会把电量挤出去（之前就是这样被挤没的）。
                    // 独立设备/主机来自 `GetControlDeviceInfo` 的 `<Status>.battery_percent`，
                    // 组内从机来自主机转述的 `slave_list[].battery`。
                    card?.battery?.let { pct ->
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            if (card.charging) "⚡$pct%" else "🔋$pct%",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (card.charging) StatusColors.ok else OnDarkPalette.text,
                            maxLines = 1,
                        )
                    }
                }
                Text(
                    listOfNotNull(
                        device.ip,
                        groupedUnder?.let { "属 $it" }
                            ?: when (online) {
                                true -> "在线"
                                false -> "离线"
                                null -> null
                            },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = OnDarkPalette.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // 类型标签：必须让人一眼看出"这台是音箱还是电视盒子"——
            // 两类设备能做的事不同（盒子没有组网/声道/预设）
            // 类型标签用 profile：这是"它是什么"，本来就该按类型显示
            if (profile == io.github.laozhudu.linkplay.protocol.dlna.DeviceProfile.GENERIC_DMR) {
                PillTag(text = "通用 DLNA", color = OnDarkPalette.muted)
                Spacer(Modifier.width(Spacing.xs))
            }
            if (groupedUnder != null) {
                PillTag(text = "组内", color = StatusColors.ok)
                Spacer(Modifier.width(Spacing.xs))
            } else if (online == false) {
                PillTag(text = "离线", color = StatusColors.error)
                Spacer(Modifier.width(Spacing.xs))
            }
            // 齿轮：原厂在卡片右上角。点开是这台设备的操作菜单（改名 / 删除 / 进设置），
            // 对应原厂长按卡片弹出的那个菜单 —— 长按现在是拖拽组网，菜单挪到齿轮上
            CircleIcon(onClick = onRename, size = 36.dp) {
                Text("⚙", style = MaterialTheme.typography.titleMedium, color = OnDarkPalette.text)
            }
        }

        VSpace(Spacing.sm)

        // 第二行：缩略图 + 曲名（原厂的"正在播放"条）
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(AppShapesExpressive.squircle)
                    .background(OnDarkPalette.chip),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    title?.trim()?.firstOrNull()?.toString()?.uppercase() ?: "♪",
                    style = MaterialTheme.typography.titleMedium,
                    color = OnDarkPalette.text,
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    title ?: if (online == false) "设备无响应" else "空闲中 · 点开挑一首",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (title != null) OnDarkPalette.text else OnDarkPalette.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val sub = when {
                    card == null -> null
                    card.plicount > 0 -> "${card.plicount} 首 · 第 ${card.plicurr.coerceAtLeast(1)} 首"
                    else -> "没有播放列表"
                }
                if (sub != null) {
                    Text(
                        sub,
                        style = MaterialTheme.typography.labelSmall,
                        color = OnDarkPalette.muted,
                        maxLines = 1,
                    )
                }
            }
        }

        VSpace(Spacing.xs)

        // 第三行：音量 + 声道（原厂就是「喇叭图标 + 滑条 + R/LR」）
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🔊", style = MaterialTheme.typography.bodySmall, color = OnDarkPalette.muted)
            Spacer(Modifier.width(Spacing.xs))
            Slider(
                value = volume.toFloat(),
                onValueChange = {
                    val v = it.toInt().coerceIn(0, 100)
                    dragging = v
                    onVolume(v)
                },
                onValueChangeFinished = { dragging = null },
                valueRange = 0f..100f,
                modifier = Modifier.weight(1f).height(28.dp),
                colors = SliderDefaults.colors(
                    thumbColor = OnDarkPalette.text,
                    activeTrackColor = OnDarkPalette.text.copy(alpha = 0.85f),
                    inactiveTrackColor = OnDarkPalette.muted.copy(alpha = 0.30f),
                ),
            )
            Spacer(Modifier.width(Spacing.xs))
            Text(
                if (card?.mute == true) "静音" else volume.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = OnDarkPalette.muted,
                modifier = Modifier.width(30.dp),
                textAlign = TextAlign.End,
            )
            // 卡片内的播放/暂停：媒体惯例显示"将发生的动作"。
            // 组内从机不显示 —— 它跟随主机，单独下发是无效命令（点了没反应最伤人）。
            if (!(inGroup && !isMaster)) {
                TextButton(onClick = onTogglePlay, contentPadding = PaddingValues(horizontal = 6.dp)) {
                    Text(
                        if (card?.playing == true) "⏸" else "▶",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (card?.playing == true) StatusColors.ok else OnDarkPalette.text,
                    )
                }
            }
            // 屏蔽 / 解除：只有具备 multiroom 能力的设备才有意义。
            // 判据是**能力位**不是类型 —— 加第三类设备时这里一行不用改
            // （项目铁律：不给按了没反应的按钮）
            if (capabilities.supportsMultiroom) {
                if (inGroup && !isMaster) {
                    TextButton(onClick = onMask, contentPadding = PaddingValues(horizontal = 6.dp)) {
                        Text(
                            if (masked) "恢复" else "屏蔽",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (masked) StatusColors.warn else OnDarkPalette.muted,
                        )
                    }
                }
                if (inGroup) {
                    // 解除要二次确认：它会让从机离开当前 Wi-Fi（真机 3 秒后才回来），
                    // 误触一次就是"音箱不见了"的体验
                    TextButton(
                        onClick = { confirmUngroup = true },
                        contentPadding = PaddingValues(horizontal = 6.dp),
                    ) {
                        Text("退出同步", style = MaterialTheme.typography.labelSmall, color = OnDarkPalette.muted)
                    }
                }
            }
            if (!capabilities.supportsChannel) {
                // 没有声道能力：这一格留空，而不是画一个假的 LR
                return@Row
            }
            // 声道：0 立体声 / 1 仅左 / 2 仅右。
            //
            // 显示的是**设备现在报的声道**（`GetControlDeviceInfo.CurrentChannel`），
            // 不是我们点了几次 —— 所以每次下发后都会回读真值再刷新（VM 的 applyChannel）。
            // 点一下按 立体声 → 仅左 → 仅右 循环：
            //   · 独立设备 / 主机 → UPnP `RenderingControl.SetChannel`（真机 .153 实测 200 + 回读一致）
            //   · 组内从机       → 主机转达 `multiroom:SlaveChannel`
            // 两种情况都不需要先组网 —— 上一版只做从机那条，所以"没组网就点不动"。
            Box(
                modifier = Modifier
                    .clip(AppShapesExpressive.pill)
                    .background(
                        if (channel == 1 || channel == 2) StatusColors.ok.copy(alpha = 0.22f)
                        else OnDarkPalette.text.copy(alpha = 0.14f),
                    )
                    .clickable { channelDialog = true }
                    .padding(horizontal = Spacing.sm, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when (channel) {
                        1 -> "仅左"
                        2 -> "仅右"
                        else -> "立体声"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = OnDarkPalette.text,
                )
            }
        }
    }

    // 声道三选一。显示的是设备当前回报的声道（不是我们点过几次）
    if (channelDialog) {
        AlertDialog(
            onDismissRequest = { channelDialog = false },
            title = { Text("「${device.title}」的声道", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Column {
                    Text(
                        "只影响这台设备的输出。选「仅左/仅右」就只从那一侧的单元出声。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    VSpace(Spacing.sm)
                    listOf(0 to "立体声（左右都出声）", 1 to "仅左声道", 2 to "仅右声道").forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    channelDialog = false
                                    onSetChannel(value)
                                }
                                .padding(vertical = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (channel == value) "●" else "○",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (channel == value) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(Spacing.sm))
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { channelDialog = false }) { Text("关闭") } },
        )
    }

    // 解除确认：从机会离开当前 Wi-Fi（真机约 3 秒回来），误触的代价是"音箱不见了"
    if (confirmUngroup) {
        AlertDialog(
            onDismissRequest = { confirmUngroup = false },
            title = { Text("解除「${device.title}」的同步？") },
            text = {
                Text(
                    if (isMaster) "会把组内所有从机都解除，它们会各自回到原来的 Wi-Fi。"
                    else "这台会退出同步并回到原来的 Wi-Fi，大约 3 秒后重新出现。",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmUngroup = false
                    onUngroup()
                }) { Text("解除") }
            },
            dismissButton = { TextButton(onClick = { confirmUngroup = false }) { Text("取消") } },
        )
    }
}
