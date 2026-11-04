package io.github.laozhudu.linkplay.console.session

import io.github.laozhudu.linkplay.console.ConsoleHost
import io.github.laozhudu.linkplay.console.playback.DevicePlaylistGateway
import io.github.laozhudu.linkplay.console.playback.QueueBus
import io.github.laozhudu.linkplay.protocol.playback.QueueRestore
import io.github.laozhudu.linkplay.protocol.player.DeviceQueueState
import io.github.laozhudu.linkplay.protocol.player.PlayerStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

/**
 * 重开 App 时的**会话恢复**：回到上次那台设备，并把播放列表也找回来。
 *
 * 从 `ConsoleViewModel` 拆出来的第三块（前两块是 `GroupController` / `DeviceControl`）。
 *
 * ## 为什么这两件事是一块
 *
 * 「重开 App 应该跟没关过一样」是一个用户诉求，不是两个：
 * 只恢复设备 → 播放页空着；只恢复队列 → 不知道队列属于哪台。
 * 拆开以后调用方必须记得**按顺序**调两次，顺序错了不会报错，只是列表空了 ——
 * 那正是最难查的一类问题。放在同一个类里，顺序写死在 [restore] 里。
 *
 * ## 为什么恢复结果要留状态
 *
 * 用户报得最多的是「重开 App 列表是空的」。这一句话对应三种完全不同的原因：
 * 1. 根本没读到上次那台设备（记录丢了 / 设备换了地址）；
 * 2. 回到设备了，但设备上没有列表；
 * 3. 设备没有列表、本地快照也没有。
 *
 * 三者的修法不同，而界面看起来一样。所以这里把每一步的结果攒成 [RestoreStatus]，
 * 由诊断页的「启动恢复」段显示 —— 这是铁律一（诊断跟着功能长）要求的：
 * 成功也要能看见，否则「没恢复」和「没执行」分不开。
 */
internal class SessionRestore(private val host: ConsoleHost) {

    /**
     * 最近一次恢复的结果（不可变快照）。
     *
     * 用 `AtomicReference` 而不是普通 `var`：写入在主线程（init 里同步调用），
     * 读取来自诊断渲染（可能在任何线程），普通 `var` 在这里属于数据竞争。
     */
    private val ref = AtomicReference(RestoreStatus())

    val status: RestoreStatus get() = ref.get()

    private fun mutate(transform: (RestoreStatus) -> RestoreStatus) {
        ref.updateAndGet(transform)
    }

    /** 恢复设备和队列。**顺序固定**，见类注释。 */
    fun restore() {
        restoreLastDevice()
        restoreLastQueue()
    }

    /**
     * 重开 App 时自动回到上次那台设备。
     *
     * 用户要求「原厂退出后重开能读到原来的状态」。光恢复队列不够 ——
     * 没有"当前设备"，播放页是空的、列表也无处可挂。所以这里按 uuid（其次 IP）找回，
     * 找到了就静默选中，不跳 Tab（用户可能只想看设备列表）。
     */
    fun restoreLastDevice() {
        val key = host.lastDeviceKey()
        if (key == null) {
            mutate { it.copy(deviceSkipped = "没有上次设备记录") }
            return
        }
        val target = host.state.value.saved.firstOrNull {
            it.uuid == key || "ip:${it.ip}" == key
        }
        if (target == null) {
            // 记录还在、设备没了：多半是用户在别处删了，或者地址变了
            mutate { it.copy(deviceSkipped = "上次设备不在已保存列表（$key）") }
            return
        }
        host.update {
            it.copy(
                selectedIp = target.ip,
                selectedLocation = target.location,
                selectedUuid = target.uuid,
            )
        }
        mutate { it.copy(device = "${target.title}（${target.ip}）", deviceSkipped = null) }
        host.log("恢复上次设备：${target.title}（${target.ip}）")
    }

    /**
     * 重开 App 时恢复播放列表。
     *
     * 判定逻辑全在 [QueueRestore]（纯函数、有单测）。这里只负责三件事：
     * 1. 读**设备真值**（`plicount`/`plicurr`）—— 列表本来就在设备上，这是权威；
     * 2. 读本地快照（提供曲目标题，设备不回填元数据）；
     * 3. 两者交给 [QueueRestore.plan] 合成，结果写进 [QueueBus]。
     *
     * 为什么先等一小会儿：[restoreLastDevice] 里才拿到 selectedIp/uuid，
     * 两者是同一批调用的，这里按"当前已选设备"读，拿不到就等它落定。
     */
    fun restoreLastQueue() {
        host.launch("restoreLastQueue") {
            delay(150)
            val ip = host.state.value.selectedIp
            if (ip == null) {
                mutate { it.copy(queueNote = "没有选中设备，跳过") }
                return@launch
            }
            val uuid = host.state.value.selectedUuid
            val started = System.currentTimeMillis()

            // ① 原厂做法：BrowseQueue 把设备上的整份列表（含曲名）读回来 —— 这才是"同步设备列表"
            val deviceList = DevicePlaylistGateway.read(ip, host.state.value.selectedLocation)
            if (deviceList != null && !deviceList.isEmpty) {
                val items = DevicePlaylistGateway.toQueueItems(deviceList)
                // 设备说"有列表"但一条曲目都没解析出来时直接跳过。
                // 不跳的话 `coerceIn(0, -1)` 会抛 IllegalArgumentException ——
                // 这是一类真会闪退的写法（范围反了），不是理论风险。
                if (items.isEmpty()) {
                    host.log("设备报了列表但读不到曲目，跳过恢复")
                    mutate {
                        it.copy(
                            queueNote = "设备报了列表但读不到曲目",
                            elapsedMs = System.currentTimeMillis() - started,
                        )
                    }
                    return@launch
                }
                val idx = deviceList.currentIndex.coerceIn(0, items.lastIndex)
                QueueBus.restore(
                    deviceIp = ip,
                    items = items,
                    index = idx,
                    title = items.getOrNull(idx)?.title,
                    deviceOwned = true,
                    note = "设备列表 ${items.size} 首 · 第 ${idx + 1} 首",
                )
                host.log("从设备读回播放列表：${items.size} 首（队列名 ${deviceList.name}）")
                mutate {
                    it.copy(
                        queueSource = "设备列表",
                        queueCount = items.size,
                        queueIndex = idx,
                        queueNote = null,
                        elapsedMs = System.currentTimeMillis() - started,
                    )
                }
                return@launch
            }

            // ② 设备没有队列 → 用本地快照兜底（并说明设备当前是空的）
            val device = withContext(Dispatchers.IO) {
                runCatching {
                    DeviceQueueState.from(PlayerStatus.parse(host.send(ip, "getPlayerStatus")))
                }.getOrNull()
            }
            val snapshot = withContext(Dispatchers.IO) {
                host.loadPlayback(uuid, ip)
            }
            val plan = QueueRestore.plan(snapshot, device)
            if (plan == null) {
                host.log("没有可恢复的播放列表")
                mutate {
                    it.copy(
                        queueNote = "设备无列表，本地也没有快照",
                        elapsedMs = System.currentTimeMillis() - started,
                    )
                }
                return@launch
            }
            QueueBus.restore(
                deviceIp = ip,
                items = plan.items,
                index = plan.index,
                title = plan.items.getOrNull(plan.index)?.title,
                deviceOwned = plan.fromDevice,
                offset = snapshot?.deviceOffset ?: 0,
                note = plan.note,
            )
            host.log("恢复播放列表：${plan.items.size} 首 · ${plan.note}")
            mutate {
                it.copy(
                    queueSource = "本地快照",
                    queueCount = plan.items.size,
                    queueIndex = plan.index,
                    queueNote = null,
                    elapsedMs = System.currentTimeMillis() - started,
                )
            }
        }
    }
}

/**
 * 一次启动恢复的结果。字段都是"给人看的一句话"，会在诊断页原样出现。
 *
 * [device] / [deviceSkipped] 二选一；[queueNote] 与 [queueCount] > 0 二选一。
 */
internal data class RestoreStatus(
    /** 恢复成功时：`doss1（192.168.99.164）`。 */
    val device: String? = null,
    /** 没恢复时的原因（没有记录 / 记录对不上设备）。 */
    val deviceSkipped: String? = null,
    /** 队列从哪来：`设备列表` / `本地快照`。 */
    val queueSource: String? = null,
    val queueCount: Int = 0,
    val queueIndex: Int = 0,
    /** 没恢复出队列时的原因。 */
    val queueNote: String? = null,
    /** 取队列那一步花了多久（含设备往返）。 */
    val elapsedMs: Long = 0L,
)
