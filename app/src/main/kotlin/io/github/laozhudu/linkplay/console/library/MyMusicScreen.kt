package io.github.laozhudu.linkplay.console.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import io.github.laozhudu.linkplay.console.ConsoleUiState
import io.github.laozhudu.linkplay.console.ui.components.AppCard
import io.github.laozhudu.linkplay.console.ui.components.PillTag
import io.github.laozhudu.linkplay.console.ui.components.SectionTitle
import io.github.laozhudu.linkplay.console.ui.components.VSpace
import io.github.laozhudu.linkplay.console.ui.theme.Spacing
import io.github.laozhudu.linkplay.protocol.library.MyMusicEntry
import io.github.laozhudu.linkplay.protocol.library.MyMusicSummary

/**
 * 「我的音乐」汇总页 —— 「媒体库」Tab 的主体。
 *
 * ## 为什么做成"六行 + 数量"而不是继续用来源卡片
 *
 * 原厂这一页就是几行带数量的入口（本地 1 首 / 共享 2 个 / 歌单 0 个 / 最近 0 首），
 * 用户扫一眼就知道"我这儿有什么、有多少"，不用逐个点进去数。
 * 我们之前是五张说明性卡片，每张都要点开才知道里面有没有东西 —— 功能一样，信息密度差一截。
 *
 * ## 数量从哪来
 *
 * 数量由 [ConsoleUiState] 携带，本页**不自己查库**：查库是 IO，放在 Composable 里
 * 会随重组反复触发（还会因为滚动抖动重扫 MediaStore）。
 * 进页面时通过 [onRefresh] 让 ViewModel 刷一次。
 * 文案规则在 core-protocol 的 [MyMusicSummary]，有单测；本文件只负责画。
 *
 * ## 关于"没有选中音箱"
 *
 * 这里**不早退**：数量是本机信息，跟投给哪台音箱无关，没选设备也应该能看见自己有几首歌。
 * 只在顶部留一行可点的提示，点了去设备页。
 */
@Composable
fun MyMusicScreen(
    state: ConsoleUiState,
    onRefresh: () -> Unit,
    onOpenLocal: () -> Unit,
    onOpenNas: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenRecent: () -> Unit,
    onOpenOnline: () -> Unit,
    onOpenRadio: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenDeviceSettings: () -> Unit,
    onOpenScripts: () -> Unit,
    onOpenLxSearch: () -> Unit,
    onOpenSearchHub: () -> Unit,
    onGoDevices: () -> Unit = {},
) {
    // 进页面刷一次：数量会随"刚导入的歌 / 刚连的 NAS / 刚建的歌单"变化，
    // 只在 ViewModel init 里刷会导致回到这页还显示旧数字。
    LaunchedEffect(Unit) { onRefresh() }

    val onlineCount = state.saved.count { state.online[it.uuid] != false }
    val rows = MyMusicSummary.build(
        localTracks = state.localMusicCount,
        nasServers = state.nasServerCount,
        playlists = state.playlistCount,
        favorites = state.favoriteCount,
        recents = state.recentCount,
        radios = state.radioCount,
        onlineConfigured = state.onlineConfigured,
    )
    // key → 动作。用 key 而不是位置来派发：MyMusicSummary 保证顺序稳定，
    // 以后在中间插一行时这里只加一个分支，不会错位。
    val onOpen: (String) -> Unit = { key ->
        when (key) {
            MyMusicSummary.KEY_LOCAL -> onOpenLocal()
            MyMusicSummary.KEY_NAS -> onOpenNas()
            MyMusicSummary.KEY_PLAYLISTS -> onOpenPlaylists()
            MyMusicSummary.KEY_RECENT -> onOpenRecent()
            MyMusicSummary.KEY_ONLINE -> onOpenOnline()
            MyMusicSummary.KEY_RADIO -> onOpenRadio()
            else -> Unit
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.lg),
        contentPadding = PaddingValues(top = Spacing.md, bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item {
            SectionTitle(
                title = "我的音乐",
                subtitle = if (state.saved.isEmpty()) "还没有添加音箱" else "$onlineCount 台在线",
            )
        }

        if (state.selectedIp == null) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onGoDevices),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "还没选音箱：可以先浏览，投歌前点这里去设备页",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "去选 ›",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        items(rows, key = { it.key }) { entry ->
            MyMusicRow(entry = entry, onClick = { onOpen(entry.key) })
        }

        // 本地 0 首时补一句"为什么是 0"。
        // 不用弹窗：没授权是一个**持续状态**而不是一次性事件，弹窗挡住了别的入口还每次都要关。
        if (state.localMusicCount <= 0) {
            item {
                Text(
                    "本地是 0 首：Android 13+ 要先授予「音乐和音频」权限才会扫到歌，点「手机本地」即可授权。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.md),
                )
            }
        }

        item { VSpace(Spacing.sm) }
        item { SectionTitle("其它入口", "原有页面都还在，按需进入") }

        item {
            AppCard(onClick = onOpenLibrary) {
                SectionTitle("音乐库", "搜索 · 收藏 · 最近播放 · 歌单")
            }
        }
        item {
            AppCard(onClick = onOpenNas) {
                SectionTitle("NAS 媒体库", "局域网 DLNA 服务器上的曲目，点一首即播")
            }
        }
        item {
            AppCard(onClick = onOpenLocal) {
                SectionTitle("手机本地音乐", "手机里的歌，通过本机 HTTP 服务推给音箱")
            }
        }
        item {
            AppCard(onClick = onOpenOnline) {
                SectionTitle("在线曲库", "接 Subsonic / tt-music，搜什么投什么")
            }
        }
        item {
            AppCard(onClick = onOpenRadio) {
                SectionTitle("网络电台", "内置可用电台 + 自己粘流地址")
            }
        }
        item {
            // 统一入口：一个词同时问四个源。放在其它入口第一位，因为它是"找东西听"的起点
            AppCard(onClick = onOpenSearchHub) {
                SectionTitle("找点东西听", "一个输入框，洛雪 / 电台 / 本地 / NAS 一起问")
            }

            // 洛雪音乐：搜一首 → 取直链 → 投到音箱（取直链要靠下面那页装的脚本）
            AppCard(onClick = onOpenLxSearch) {
                SectionTitle("洛雪音乐", "搜一首歌，点一下就从音箱放出来")
            }

            // 洛雪音源脚本：搜索结果能不能放，取决于这里装了什么（洛雪只负责搜）
            AppCard(onClick = onOpenScripts) {
                SectionTitle("洛雪音源脚本", "装了几支、哪支在用 —— 没有脚本搜到也放不了")
            }

            AppCard(onClick = onOpenDeviceSettings) {
                SectionTitle("音箱设置", "改名 · 固件 · 预设键 · 定时关闭")
            }
        }
    }
}

/** 单行：左名称（+可选说明），右数量标签。 */
@Composable
private fun MyMusicRow(entry: MyMusicEntry, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                entry.subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(Spacing.sm))
            // "0 首 / 未配置"用弱色：一排亮色标签会让人误以为"里面都有东西"
            val empty = entry.countText == "未配置" || entry.countText.startsWith("0 ")
            PillTag(
                text = entry.countText,
                color = if (empty) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.primary,
            )
        }
    }
}
