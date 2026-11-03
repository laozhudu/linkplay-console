package io.github.laozhudu.linkplay.console.provisioning

import io.github.laozhudu.linkplay.console.ui.theme.StatusColors
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.laozhudu.linkplay.protocol.ProvisioningResult

@Composable
fun ProvisioningScreen(
    onDone: () -> Unit,
    onBack: () -> Unit,
    vm: ProvisioningViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        StepHeader(state.step)

        state.error?.let {
            Text("· $it", color = StatusColors.error, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
        }
        state.validationError?.let {
            Text("· $it", color = StatusColors.error, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
        }

        when (state.step) {
            ProvisionStep.CONNECT_TO_DEVICE_AP -> StepConnectAp(onNext = vm::acknowledgeApConnected, onBack = onBack)
            ProvisionStep.FIND_DEVICE -> StepFindDevice(state, vm)
            ProvisionStep.PICK_NETWORK -> StepPickNetwork(state, vm)
            ProvisionStep.ENTER_PASSWORD -> StepEnterPassword(state, vm)
            ProvisionStep.SENDING -> StepSending(state, vm, onDone)
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun StepHeader(step: ProvisionStep) {
    val (index, total, title) = when (step) {
        ProvisionStep.CONNECT_TO_DEVICE_AP -> Triple(1, 4, "连接音箱热点")
        ProvisionStep.FIND_DEVICE -> Triple(2, 4, "找到音箱")
        ProvisionStep.PICK_NETWORK -> Triple(3, 4, "选择 Wi-Fi")
        ProvisionStep.ENTER_PASSWORD -> Triple(4, 4, "输入密码")
        ProvisionStep.SENDING -> Triple(4, 4, "正在连接")
    }
    Text("步骤 $index / $total", style = MaterialTheme.typography.labelSmall)
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    LinearProgressIndicator(
        progress = { index / total.toFloat() },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun StepConnectAp(onNext: () -> Unit, onBack: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp)) {
            Text("音箱首次配网时会自己开一个热点，名字形如 LinkPlay_xxxx。", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Text("① 打开系统「设置 → WLAN」", style = MaterialTheme.typography.bodySmall)
            Text("② 连上名叫 LinkPlay_xxxx 的热点（无密码或密码见音箱说明）", style = MaterialTheme.typography.bodySmall)
            Text("③ 允许「此网络无法上网」提示，保持连接", style = MaterialTheme.typography.bodySmall)
            Text("④ 回到这里点下一步", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "注意：连着音箱热点时手机没有外网，这是正常的。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
    Spacer(Modifier.height(16.dp))
    Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("我已连上音箱热点") }
    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("返回") }
}

@Composable
private fun StepFindDevice(state: ProvisionUiState, vm: ProvisioningViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = state.manualIp,
            onValueChange = vm::setManualIp,
            label = { Text("设备地址（可留空自动查找）") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Button(onClick = vm::useManualIp) { Text("用这个") }
    }
    Spacer(Modifier.height(10.dp))
    Button(
        onClick = vm::findDevice,
        enabled = !state.finding,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (state.finding) CircularProgressIndicator(Modifier.width(16.dp).height(16.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp))
        Text(if (state.finding) "正在查找…" else "自动查找音箱")
    }
    Spacer(Modifier.height(8.dp))
    Text(
        "· 先试 SSDP 组播发现；若热点网络屏蔽组播，会再试常见默认地址 " +
            ProvisioningViewModel.DEFAULT_DEVICE_ADDRESSES.joinToString(" / "),
        style = MaterialTheme.typography.bodySmall,
    )
    if (state.findLog.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text("查找日志", style = MaterialTheme.typography.labelSmall)
        state.findLog.forEach {
            Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
    }
    TextButton(onClick = vm::back, modifier = Modifier.fillMaxWidth()) { Text("返回") }
}

@Composable
private fun StepPickNetwork(state: ProvisionUiState, vm: ProvisioningViewModel) {
    Text(
        "设备 ${state.deviceIp} 扫到的 Wi-Fi",
        style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = vm::scanNetworks, enabled = !state.scanning) {
            Text(if (state.scanning) "扫描中…" else "重新扫描")
        }
        Spacer(Modifier.width(8.dp))
        if (state.aps.isNotEmpty()) {
            Text("${state.aps.size} 个", style = MaterialTheme.typography.bodySmall)
        }
    }
    Spacer(Modifier.height(8.dp))
    if (state.aps.isNotEmpty()) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                items(state.aps) { ap ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(ap.ssid, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "ch ${ap.channel} · ${"▮".repeat(ap.signalLevel)}${"▯".repeat(4 - ap.signalLevel)}" +
                                    if (ap.isOpen) " · 开放" else " · ${ap.auth}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        TextButton(onClick = { vm.selectAp(ap) }) { Text("选择") }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Text("或手动输入 SSID（隐藏网络用）", style = MaterialTheme.typography.labelSmall)
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = state.manualSsid,
            onValueChange = vm::setManualSsid,
            label = { Text("SSID") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Button(onClick = vm::useManualSsid) { Text("下一步") }
    }
    TextButton(onClick = vm::back, modifier = Modifier.fillMaxWidth()) { Text("返回") }
}

@Composable
private fun StepEnterPassword(state: ProvisionUiState, vm: ProvisioningViewModel) {
    var passwordVisible by remember { mutableStateOf(false) }
    Text("目标网络：${state.selectedAp?.ssid.orEmpty()}", fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("开放网络（无密码）", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Switch(checked = state.isOpenNetwork, onCheckedChange = vm::setOpenNetwork)
    }
    if (!state.isOpenNetwork) {
        OutlinedTextField(
            value = state.password,
            onValueChange = vm::setPassword,
            label = { Text("Wi-Fi 密码（8-63 位）") },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                TextButton(onClick = { passwordVisible = !passwordVisible }) {
                    Text(if (passwordVisible) "隐藏" else "显示")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Spacer(Modifier.height(12.dp))
    Button(onClick = vm::submit, modifier = Modifier.fillMaxWidth()) {
        Text(if (state.isOpenNetwork) "下发并连接" else "开始配网")
    }
    Text(
        "2.4GHz 才稳：多数这批老固件不支持 5GHz。密码只发给局域网内的音箱，不经任何服务器。",
        style = MaterialTheme.typography.bodySmall,
    )
    TextButton(onClick = vm::back, modifier = Modifier.fillMaxWidth()) { Text("上一步") }
}

@Composable
private fun StepSending(state: ProvisionUiState, vm: ProvisioningViewModel, onDone: () -> Unit) {
    val result = state.result
    if (state.sending) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text("已下发，等待音箱连上…", fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "第 ${state.probeCount} 次探测 · ${state.elapsedMs / 1000}s / 90s",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "音箱正在切网络，这期间原地址会短暂连不上，属正常。不要关掉 App。",
            style = MaterialTheme.typography.bodySmall,
        )
        return
    }

    when (result) {
        is ProvisioningResult.Connected -> {
            Text("配网成功", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "音箱已连上「${result.status.essid}」并拿到联网状态。",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "下一步：把手机切回自家 Wi-Fi，然后用「设备列表」的 SSDP 搜索把音箱加进来。",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("完成") }
        }
        is ProvisioningResult.JoinedButOffline -> {
            Text("已连上，但未联网", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "音箱已切到「${result.status.essid}」，但还没拿到 IP / 上不了外网。通常是密码错误或路由器 DHCP 问题。",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = vm::restart, modifier = Modifier.fillMaxWidth()) { Text("重试") }
        }
        is ProvisioningResult.Timeout -> {
            Text("超时未连上", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "90 秒内没看到音箱切到目标网络。最后观察到的网络：" +
                    (result.lastSeen?.essid ?: "未知"),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = vm::restart, modifier = Modifier.fillMaxWidth()) { Text("重试") }
        }
        is ProvisioningResult.Unreachable -> {
            Text("设备失联", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "连续探测失败：${result.lastError ?: "无响应"}。可能已成功切换网络（原地址失效），" +
                    "也可能掉线。建议把手机切回自家 Wi-Fi，再用 SSDP 搜索确认。",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("去设备列表确认") }
            TextButton(onClick = vm::restart, modifier = Modifier.fillMaxWidth()) { Text("再试一次") }
        }
        null -> {
            Text("未知状态", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = vm::restart, modifier = Modifier.fillMaxWidth()) { Text("重试") }
        }
    }
}
