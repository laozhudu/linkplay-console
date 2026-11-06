package io.github.laozhudu.linkplay.console.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.laozhudu.linkplay.console.ui.apple.InsetGroup
import io.github.laozhudu.linkplay.console.ui.apple.LargeTitle
import io.github.laozhudu.linkplay.console.ui.apple.rememberAppHaptics
import io.github.laozhudu.linkplay.console.ui.components.EmptyState
import io.github.laozhudu.linkplay.console.ui.components.VSpace
import io.github.laozhudu.linkplay.console.ui.theme.Spacing
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * 遥控页（`docs/93` 定稿第 4 屏）。
 *
 * ## 键是"能发多少画多少"，不是"设计图有几个画几个"
 *
 * 画哪些键**完全由 [RemoteControl.keys] 决定**。当前红外实现只有一条探测码，
 * 所以这里只会出现一个「电源」圆键 —— 这不是界面没做完，
 * 是 `IrRemoteControl.DEFAULT_CODEBOOK` 里真的只有一条。
 *
 * 接上 `Flipper-IRDB`（30 万条，CC0）之后 `keys()` 一变，这个网格自动长出
 * 方向圆盘、音量 ±、信号源…… **这个文件一行都不用改。**
 *
 * ## 文案底线：说"已发送"，不说"已打开"
 *
 * 红外没有回执。`ConsumerIrManager.transmit()` 是 void，
 * 系统只保证"波形交给驱动了"。所以成功文案必须是「已发送」，
 * 说"已打开电视"就是在报告里说谎（`docs/52` 铁律二）。
 */
@Composable
internal fun RemoteScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // keys() 内部会查一次 hasIrEmitter()（走 Binder）。放进 remember，
    // 别让每次重组都去问一遍系统服务。
    val control = remember { IrRemoteControl.from(context) }
    val keys = remember(control) { control.keys().sortedBy { orderOf(it) } }
    val scope = rememberCoroutineScope()
    val haptics = rememberAppHaptics()

    var lastResult by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg),
    ) {
        LargeTitle(title = "遥控", subtitle = if (keys.isEmpty()) "无可用按键" else "红外 · ${keys.size} 个键")
        VSpace(Spacing.md)

        if (keys.isEmpty()) {
            // keys() 返回空集 = 这台手机没有红外发射器。
            // 不画按不动的键，直接说清为什么（与悬浮窗那次踩的坑相反）。
            EmptyState(
                title = "这台手机没有红外发射器",
                hint = "遥控页只在有红外发射器的手机上显示按键。\n蓝牙遥控还是零代码（接口层已建好）。",
            )
            VSpace(Spacing.lg)
        } else {
            // 按键网格：3 列。当前只有 1 个键，就是一个大圆。
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                keys.chunked(3).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        row.forEach { key ->
                            RemoteKeyButton(
                                key = key,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    haptics.tap()
                                    scope.launch {
                                        lastResult = when (val r = control.send(key)) {
                                            RemoteResult.Sent -> "已发送「${labelOf(key)}」 —— 发出去就不归我们管了，收没收到只有电视知道"
                                            RemoteResult.Unsupported -> "这份码表里没有「${labelOf(key)}」"
                                            is RemoteResult.Failed -> r.reason
                                        }
                                    }
                                },
                            )
                        }
                        // 补空位，让最后一行的键保持等宽
                        repeat(3 - row.size) {
                            Box(Modifier.weight(1f))
                        }
                    }
                }
            }

            lastResult?.let {
                VSpace(Spacing.sm)
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            VSpace(Spacing.lg)
        }

        // 诊断行（`docs/52` 铁律一：诊断台跟着功能一起长）。
        // 由 RemoteControl 自己给出，诊断台不用按类型 switch。
        InsetGroup(
            title = "状态",
            footer = "码表是示例，不是码库。接上 Flipper-IRDB 之后这里会自动变成「选品牌 → 选型号」。",
        ) {
            Text(
                text = control.diagnoseLine(),
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        VSpace(Spacing.xl)
    }
}

/** 一个圆键。直径 92dp —— 拇指按得准，也不至于一屏只放得下三个。 */
@Composable
private fun RemoteKeyButton(
    key: RemoteKey,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(92.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = labelOf(key),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** 语义键 → 界面文案。**只在这里有中文**，接口层不带文案。 */
internal fun labelOf(key: RemoteKey): String = when (key) {
    RemoteKey.Power -> "电源"
    RemoteKey.VolumeUp -> "音量 +"
    RemoteKey.VolumeDown -> "音量 −"
    RemoteKey.Mute -> "静音"
    RemoteKey.Up -> "上"
    RemoteKey.Down -> "下"
    RemoteKey.Left -> "左"
    RemoteKey.Right -> "右"
    RemoteKey.Ok -> "确定"
    RemoteKey.Back -> "返回"
    RemoteKey.Menu -> "菜单"
    RemoteKey.Home -> "主页"
    RemoteKey.InputSource -> "信号源"
    is RemoteKey.Custom -> key.name
}

/** 按键排列顺序（先电源，再音量，再方向）。 */
private fun orderOf(key: RemoteKey): Int = when (key) {
    RemoteKey.Power -> 0
    RemoteKey.VolumeUp -> 1
    RemoteKey.VolumeDown -> 2
    RemoteKey.Mute -> 3
    RemoteKey.Up -> 10
    RemoteKey.Down -> 11
    RemoteKey.Left -> 12
    RemoteKey.Right -> 13
    RemoteKey.Ok -> 14
    RemoteKey.Back -> 20
    RemoteKey.Home -> 21
    RemoteKey.Menu -> 22
    RemoteKey.InputSource -> 23
    is RemoteKey.Custom -> 99
}
