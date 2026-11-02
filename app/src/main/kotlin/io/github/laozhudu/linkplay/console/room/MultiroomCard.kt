package io.github.laozhudu.linkplay.console.room

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.laozhudu.linkplay.console.ConsoleUiState
import io.github.laozhudu.linkplay.console.ConsoleViewModel
import io.github.laozhudu.linkplay.console.GroupMember
import io.github.laozhudu.linkplay.console.ui.components.AppCard
import io.github.laozhudu.linkplay.console.ui.components.PillTag
import io.github.laozhudu.linkplay.console.ui.components.SectionTitle
import io.github.laozhudu.linkplay.console.ui.components.VSpace
import io.github.laozhudu.linkplay.console.ui.theme.Spacing
import io.github.laozhudu.linkplay.console.ui.theme.StatusColors
import io.github.laozhudu.linkplay.protocol.session.SavedDevice

/**
 * 首页的「多房间」卡片 —— 组网的可视化配置面板。
 *
 * ## 为什么放在首页
 *
 * 多房间是「我家有几台音箱、它们现在怎么连」的事，属于**设备层面**，
 * 不是某一台设备的设置项。放进「设备设置」里，用户得先点进某台设备才能看见全局，
 * 心智模型是反的。Sonos / 小爱 / HomePod 都把它放在首页第一屏。
 *
 * ## 为什么不是拖拽
 *
 * 拖拽要求把两张卡片精确重叠，规则（拖到谁身上谁当主机）在界面上完全看不见。
 * 这里直接画成「树」：主机在上、从机在下，加减音箱用按钮，一眼就懂谁听谁的。
 *
 * ## 每台音箱都能单独调
 *
 * 真机验证过：从机虽然连不上（在主机热点网段 `10.10.10.x`），
 * 但 `multiroom:SlaveVolume:<从机IP>:<0-100>` 与 `multiroom:SlaveChannel:<从机IP>:<ch>`
 * 都能由主机代转，回读有效。所以每台从机都有自己的音量条与声道选择。
 *
 * 假设放在这里而不是「设备设置」里：一个房间里两台音箱，
 * 常见的诉求就是「靠近床头那台音量小一点、只听左声道」，这不该逼用户来回跳页。
 */
@Composable
fun MultiroomCard(
    state: ConsoleUiState,
    vm: ConsoleViewModel,
    onOpenDevice: (SavedDevice) -> Unit,
) {
    // 音量以全站总线为准：播放页刚拖过的值要立刻反映到这里
    val bus by io.github.laozhudu.linkplay.console.playback.VolumeBus.state.collectAsState()
    val members = state.groupMembers.map { m ->
        // 主机音量跟着总线走；从机音量只能由主机回报（直连不可达）
        if (m.isMaster) bus[m.ip]?.let { b -> m.copy(volume = b.volume) } ?: m else m
    }
    val grouped = members.size > 1
    var picker by remember { mutableStateOf(false) }
    val outsiders = state.saved.filter { saved -> members.none { it.ip == saved.ip } }

    AppCard {
        SectionTitle(
            title = "多房间",
            subtitle = when {
                state.multiroomBusy -> "正在组网…"
                grouped -> "${members.size} 台同步播放"
                state.saved.size < 2 -> "只有一台音箱"
                else -> "两台以上可以一起响"
            },
        )
        VSpace(Spacing.sm)

        if (grouped) {
            // ---------- 主机：整块高亮，说明它是"发号施令"的那台 ----------
            members.filter { it.isMaster }.forEach { master ->
                MemberBlock(
                    member = master,
                    master = master,
                    vm = vm,
                    onRemove = null,
                    onOpen = {
                        state.saved.firstOrNull { it.ip == master.ip }?.let(onOpenDevice)
                    },
                )
                VSpace(Spacing.xs)
            }

            // ---------- 从机：整体右缩进，视觉上"挂在主机下面" ----------
            Column(Modifier.fillMaxWidth().padding(start = Spacing.md)) {
                members.filter { !it.isMaster }.forEach { slave ->
                    MemberBlock(
                        member = slave,
                        master = members.first(),
                        vm = vm,
                        onRemove = { vm.removeFromGroup(slave) },
                        onOpen = null,
                    )
                    VSpace(Spacing.xs)
                }
            }
        } else {
            Text(
                "还没有组。把另一台音箱加进来，两台就会播同一首歌。",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            VSpace(Spacing.sm)
        }

        state.multiroomNote?.let { note ->
            Text(
                note,
                style = MaterialTheme.typography.labelMedium,
                color = if (grouped) StatusColors.ok else MaterialTheme.colorScheme.error,
            )
            VSpace(Spacing.xs)
        }

        if (state.multiroomBusy) {
            Text(
                "正在把「${state.multiroomTarget ?: "音箱"}」加进来… ${state.multiroomTick}s / 40s",
                style = MaterialTheme.typography.labelMedium,
            )
            VSpace(Spacing.xs)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (outsiders.isNotEmpty()) {
                PillButton(
                    text = if (grouped) "＋ 加音箱" else "＋ 组起来",
                    enabled = !state.multiroomBusy,
                    onClick = { picker = true },
                    filled = true,
                )
            }
            if (grouped) {
                PillButton(text = "刷新状态", enabled = true, onClick = vm::refreshGroup, filled = false)
            }
        }

        if (grouped) {
            VSpace(Spacing.xs)
            Text(
                "从机由主机代控：音量和声道都能单独调，退出组后它会自己连回原 Wi-Fi。",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (picker) {
        AlertDialog(
            onDismissRequest = { picker = false },
            title = { Text("加入哪一台？") },
            text = {
                Column {
                    Text("它会连到主机，然后两台同步播放同一首歌。", style = MaterialTheme.typography.bodySmall)
                    VSpace(Spacing.sm)
                    outsiders.forEach { other ->
                        Text(
                            text = other.title,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { picker = false; vm.addToGroup(other) }
                                .padding(vertical = 12.dp, horizontal = Spacing.sm),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picker = false }) { Text("取消") } },
        )
    }
}

/** 一台音箱的控制块：名称 + 音量条 +（从机）声道分段。 */
@Composable
private fun MemberBlock(
    member: GroupMember,
    master: GroupMember,
    vm: ConsoleViewModel,
    onRemove: (() -> Unit)?,
    onOpen: (() -> Unit)?,
) {
    val accent = if (member.isMaster) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.secondary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (member.isMaster) accent.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            )
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier
                    .weight(1f)
                    .then(
                        if (onOpen != null) {
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onOpen)
                        } else Modifier,
                    ),
            ) {
                Text(
                    member.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (member.isMaster) "主机 · 播放源" else "从机 · 跟随主机",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PillTag(
                text = "${member.volume ?: 0}",
                color = accent,
            )
            if (onRemove != null) {
                Spacer(Modifier.width(Spacing.xs))
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onRemove),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✕", style = MaterialTheme.typography.titleSmall)
                }
            }
        }

        // 音量：拖动即下发（VM 里有合并限速通道，不会刷爆设备）
        Slider(
            value = (member.volume ?: 0).toFloat().coerceIn(0f, 100f),
            onValueChange = { vm.setGroupVolume(member, it.toInt()) },
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        // 声道：只有从机有这条命令，主机不做假开关
        if (!member.isMaster) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "声道",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(Spacing.xs))
                ChannelChip("立体声", member.channel == 0) { vm.setGroupChannel(member, 0) }
                ChannelChip("仅左", member.channel == 1) { vm.setGroupChannel(member, 1) }
                ChannelChip("仅右", member.channel == 2) { vm.setGroupChannel(member, 2) }
            }
        }
    }
}

@Composable
private fun ChannelChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(end = Spacing.xs)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PillButton(text: String, enabled: Boolean, onClick: () -> Unit, filled: Boolean) {
    val bg = if (filled) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (filled) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (enabled) bg else bg.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = 9.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}
