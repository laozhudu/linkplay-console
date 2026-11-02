package io.github.laozhudu.linkplay.console.library

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.laozhudu.linkplay.protocol.library.TrackRecord

/**
 * 音乐库页：搜索 / 收藏 / 最近 / 电台 + 预设键。
 *
 * 布局取舍：四个分区用顶部一排按钮切换而不是 TabRow——屏幕宽度有限，
 * 搜索框要占满一行，TabRow 会把内容挤到第二屏。
 */
@Composable
fun LibraryScreen(
    deviceIp: String,
    location: String?,
    deviceLabel: String?,
    onBack: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as android.app.Application
    val vm: LibraryViewModel = viewModel(
        key = "library-$deviceIp",
        factory = viewModelFactory { initializer { LibraryViewModel(app, deviceIp, location, deviceLabel) } },
    )
    val state by vm.state.collectAsState()

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) vm.refreshAll()
    }
    LaunchedEffect(Unit) { launcher.launch(permission) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                LibraryTab.SEARCH to "搜索",
                LibraryTab.FAVORITES to "收藏 ${state.favorites.size}",
                LibraryTab.RECENT to "最近 ${state.recents.size}",
                LibraryTab.RADIO to "电台 ${state.radios.size}",
            ).forEach { (tab, label) ->
                if (state.tab == tab) Button(onClick = { vm.setTab(tab) }) { Text(label) }
                else OutlinedButton(onClick = { vm.setTab(tab) }) { Text(label) }
            }
        }

        state.error?.let {
            Spacer(Modifier.height(6.dp))
            Text("· $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = vm::clearError) { Text("知道了") }
        }

        state.capabilities?.let { caps ->
            Spacer(Modifier.height(4.dp))
            Text(caps.summary(), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
            if (!caps.canPushQueue) {
                Text(
                    "提示：这台设备不支持整列表推送，只能单曲播放。",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        state.nowPlaying?.let {
            Spacer(Modifier.height(4.dp))
            Text("正在播放：$it", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        }

        Spacer(Modifier.height(8.dp))
        when (state.tab) {
            LibraryTab.SEARCH -> SearchSection(state, vm)
            LibraryTab.FAVORITES -> TrackList(
                title = "收藏",
                tracks = state.favorites,
                emptyHint = "还没有收藏。搜索结果里点 ☆ 添加。",
                onPlay = vm::play,
                onToggleFav = vm::toggleFavorite,
                favoriteKeys = state.favorites.map { it.dedupeKey }.toSet(),
            )
            LibraryTab.RECENT -> TrackList(
                title = "最近播放",
                tracks = state.recents,
                emptyHint = "还没有播放记录。",
                onPlay = vm::play,
                onToggleFav = vm::toggleFavorite,
                favoriteKeys = state.favorites.map { it.dedupeKey }.toSet(),
            )
            LibraryTab.RADIO -> RadioSection(state, vm)
        }

        Spacer(Modifier.height(10.dp))
        Text("设备预设键（1-6）", style = MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..6).forEach { index ->
                OutlinedButton(onClick = { vm.pressPreset(index) }) { Text("$index") }
            }
        }

        state.log.asReversed().take(5).forEach {
            Text(it, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
        }

        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("返回") }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SearchSection(state: LibraryUiState, vm: LibraryViewModel) {
    OutlinedTextField(
        value = state.query,
        onValueChange = vm::setQuery,
        label = { Text("搜索：标题 / 艺术家 / 拼音首字母（试试 zjl）") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    if (state.query.isBlank()) {
        Text(
            "支持拼音首字母：输入 `zj` 找「周杰伦」，输入 `yq` 找「夜曲」。",
            style = MaterialTheme.typography.bodySmall,
        )
        return
    }
    if (state.hits.isEmpty()) {
        Text("没有匹配", style = MaterialTheme.typography.bodySmall)
        return
    }
    val favKeys = state.favorites.map { it.dedupeKey }.toSet()
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
            items(state.hits, key = { it.track.dedupeKey }) { hit ->
                TrackRow(
                    track = hit.track,
                    isFavorite = hit.track.dedupeKey in favKeys,
                    onPlay = { vm.play(hit.track) },
                    onToggleFav = { vm.toggleFavorite(hit.track) },
                )
            }
        }
    }
}

@Composable
private fun TrackList(
    title: String,
    tracks: List<TrackRecord>,
    emptyHint: String,
    onPlay: (TrackRecord) -> Unit,
    onToggleFav: (TrackRecord) -> Unit,
    favoriteKeys: Set<String>,
) {
    if (tracks.isEmpty()) {
        Text(emptyHint, style = MaterialTheme.typography.bodySmall)
        return
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
            items(tracks, key = { it.dedupeKey }) { track ->
                TrackRow(
                    track = track,
                    isFavorite = track.dedupeKey in favoriteKeys,
                    onPlay = { onPlay(track) },
                    onToggleFav = { onToggleFav(track) },
                )
            }
        }
    }
}

@Composable
private fun TrackRow(
    track: TrackRecord,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onToggleFav: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(track.title, style = MaterialTheme.typography.bodySmall)
            Text(
                track.displayArtist ?: track.sourceType,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        TextButton(onClick = onToggleFav) { Text(if (isFavorite) "★" else "☆") }
        Button(onClick = onPlay) { Text("播") }
    }
}

@Composable
private fun RadioSection(state: LibraryUiState, vm: LibraryViewModel) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    OutlinedTextField(
        value = url,
        onValueChange = {
            url = it
            if (name.isBlank()) name = io.github.laozhudu.linkplay.protocol.radio.RadioStation.suggestName(it)
        },
        label = { Text("流地址（http/https，如 http://host/live.m3u8）") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("电台名称") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Button(onClick = {
            vm.addRadio(name, url)
            name = ""
            url = ""
        }) { Text("添加") }
    }

    Spacer(Modifier.height(8.dp))
    if (state.radios.isEmpty()) {
        Text("还没有电台。粘一条流地址即可（本 App 不内置任何平台接口）。", style = MaterialTheme.typography.bodySmall)
        return
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
            items(state.radios, key = { it.id }) { station ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(station.name, style = MaterialTheme.typography.bodySmall)
                        Text(station.url, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                    }
                    Button(onClick = { vm.playRadio(station) }) { Text("播") }
                    TextButton(onClick = { vm.removeRadio(station.id) }) { Text("删") }
                }
            }
        }
    }
}
