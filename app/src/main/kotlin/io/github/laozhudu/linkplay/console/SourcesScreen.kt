package io.github.laozhudu.linkplay.console

import androidx.compose.runtime.Composable
import io.github.laozhudu.linkplay.console.ConsoleUiState
import io.github.laozhudu.linkplay.console.library.MyMusicScreen

/**
 * 「媒体库」Tab。
 *
 * 主体是原厂样式的「我的音乐」汇总（几行入口 + 真实数量）；原来那几张来源卡片
 * 收在下方「其它入口」里，功能一个没少，只是不再占着首屏。
 */
@Composable
internal fun SourcesScreen(
    state: ConsoleUiState,
    vm: ConsoleViewModel,
    onOpenNas: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenLocal: () -> Unit,
    onOpenRadio: () -> Unit,
    onOpenOnline: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenScripts: () -> Unit,
    onOpenLxSearch: () -> Unit,
    onGoDevices: () -> Unit,
) {
    MyMusicScreen(
        state = state,
        // 进页面刷一次：本地曲库/歌单/电台都可能是刚在别的页面改过的
        onRefresh = vm::refreshMusicCounts,
        onOpenLocal = onOpenLocal,
        onOpenNas = onOpenNas,
        // 「全部歌单」「最近播放」都落在音乐库页（那里有对应分区），不新开二级页
        onOpenPlaylists = onOpenLibrary,
        onOpenRecent = onOpenLibrary,
        onOpenOnline = onOpenOnline,
        onOpenRadio = onOpenRadio,
        onOpenScripts = onOpenScripts,
        onOpenLxSearch = onOpenLxSearch,
        onOpenLibrary = onOpenLibrary,
        onOpenDeviceSettings = onOpenSettings,
        onGoDevices = onGoDevices,
    )
}
