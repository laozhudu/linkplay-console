package io.github.laozhudu.linkplay.console.playback

import io.github.laozhudu.linkplay.protocol.queue.QueueItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 队列播放在整个进程内的唯一状态源。
 *
 * 为什么要有它：编排原来在 `NasViewModel` 里跑，用户退出页面 ViewModel 被清掉，
 * 队列就断了（`ClientSideQueue` 的循环随协程一起没）。挪进前台服务后，服务持有循环，
 * UI 只订阅这份状态 —— 页面来去都不影响播放。
 *
 * **队列内容也放在这里**（[QueueRunState.items]）：播放页要展示「播放列表」并支持点某首跳过去，
 * 那是播放页的一部分，不该再藏到别的页面里。
 */
data class QueueRunState(
    val running: Boolean = false,
    val items: List<QueueItem> = emptyList(),
    /** 当前正在播第几首（0 基）。空队列为 -1。 */
    val index: Int = -1,
    /** 正在播的曲名。 */
    val nowPlaying: String? = null,
    val playedCount: Int = 0,
    val total: Int = 0,
    /** 编排提前结束（不是用户停止）。通常是控制面掉线。 */
    val controlLost: Boolean = false,
    /**
     * 队列由**设备**持有（把整个 m3u 推给设备之后的状态）。
     *
     * 与 [running] 的区别：`running` 表示"我们自己的前台服务正在逐首投流"。
     * 设备持有列表时我们不需要任何后台循环，App 可以退出，界面依然能显示列表与当前第几首。
     */
    val deviceOwned: Boolean = false,
    /**
     * 设备侧第 1 首对应本地列表的第几首。
     *
     * 因为设备只认 `playlist` 命令的 index=0，我们把列表旋转过再下发，
     * 所以设备报的 `plicurr` 要加上这个偏移才对得上本地列表。
     */
    val deviceOffset: Int = 0,
    /** 这份列表从哪来的（设备 / 本地快照 / 占位），直接显示给用户，避免"看起来像同步了其实没有"。 */
    val sourceNote: String? = null,
    /**
     * 这份列表属于哪台设备（按 IP）。
     *
     * 为什么必须记：多台音箱时队列是**各归各的**。之前 QueueBus 是全局单例、
     * 换设备不清空，于是切到 `.164` 后播放页显示的还是 `.153` 的
     * 「设备列表 122 首 · 第 3 首」，用户一按播放就对着不相干的内容操作
     * （截图里就是这种情况）。null 表示"不知道归属"，一律按不匹配处理。
     */
    val ownerIp: String? = null,
    val error: String? = null,
    val log: List<String> = emptyList(),
) {
    val current: QueueItem? get() = items.getOrNull(index)
}

object QueueBus {

    /**
     * 本机播放时的队列归属。
     *
     * 为什么用字符串而不是 null：`ownerIp = null` 的语义是"不知道归属"，
     * 界面一律按不匹配处理（那时是为了防止切设备后串台）。
     * 本机是**明确的归属**，只是它不是一台设备 —— 所以给它一个不会和 IP 撞的标识。
     */
    const val LOCAL_OWNER = "local"
    private val _state = MutableStateFlow(QueueRunState())

    /**
     * 当前这份队列属于哪台设备。
     *
     * 由 [setOwner] 与各写入方法共同维护。**外部不能再直接写** ——
     * 之前 `begin/restore` 内部读的是"当前 owner"，调用方忘了先 setOwner 就会
     * 把新列表挂到上一台设备名下（PR #40 修过一次，但只修了 QueueStarter 一条路，
     * NAS / Console / 服务内部那几处仍然会串台）。
     *
     * 现在所有写入方法**强制要求传 deviceIp**，编译期就堵死了这个漏点。
     */
    @Volatile
    private var currentOwner: String? = null
    val state: StateFlow<QueueRunState> = _state.asStateFlow()

    /**
     * 服务注册进来的「操作队列」入口。
     *
     * 为什么用回调而不是让 UI 持有 Service：UI 只该看到 state 和几个动作；
     * 直接引用 Service 会让 ViewModel / Compose 的生命周期跟着服务乱掉。
     */
    @Volatile
    private var controller: QueueController? = null

    fun bind(c: QueueController?) {
        controller = c
    }

    /** 服务是否在跑（有 controller 才能真的跳转/删除）。 */
    val hasController: Boolean get() = controller != null

    /**
     * 开始一份**属于 [deviceIp]** 的新队列。
     *
     * @param deviceIp 队列归属，必填。之前是内部读 currentOwner，
     *   调用方忘了 setOwner 就把列表挂到上一台设备名下 —— 编译期强制之后不可能再漏。
     */
    fun begin(deviceIp: String, items: List<QueueItem>, startAt: Int = 0) {
        currentOwner = deviceIp
        _state.update {
            QueueRunState(
                ownerIp = deviceIp,
                running = true,
                items = items,
                // startAt 之前写死 0：从中间某一首起播时，列表显示"第 1 首"而声音是第 8 首
                index = startAt.coerceIn(0, maxOf(0, items.lastIndex)),
                total = items.size,
                playedCount = 0,
            )
        }
    }

    /**
     * 声明"接下来的队列属于这台设备"。
     *
     * 换了设备就**整份丢掉**上一台的列表与日志 —— 多台音箱时队列各归各的，
     * 继续显示别人的列表会让用户对着不相干的内容按播放。
     */
    fun setOwner(deviceIp: String) {
        if (currentOwner == deviceIp) return
        currentOwner = deviceIp
        _state.update { QueueRunState(ownerIp = deviceIp) }
    }

    /**
     * 当前队列归属哪台设备 —— 读取侧守卫用。
     *
     * 写入侧现在编译期强制带归属（[begin]/[restore]/[playing]/[clear]），
     * 这个方法主要给**读**用：界面拿它决定"要不要显示这份列表"。
     */
    fun owner(): String? = currentOwner

    /**
     * 上报"现在在放第几首"。带归属：别的设备的播放进度不该改这份队列的高亮。
     */
    fun playing(deviceIp: String, title: String, index: Int, items: List<QueueItem>? = null) {
        if (currentOwner != deviceIp) return
        _state.update {
            it.copy(
                nowPlaying = title,
                index = index,
                playedCount = index + 1,
                items = items ?: it.items,
            )
        }
    }

    fun finish(controlLost: Boolean, log: List<String>) = _state.update {
        it.copy(running = false, controlLost = controlLost, log = log.takeLast(30))
    }

    fun fail(message: String) = _state.update {
        it.copy(running = false, error = message)
    }

    fun appendLog(line: String) = _state.update { it.copy(log = (it.log + line).takeLast(30)) }

    /**
     * 从本地快照恢复队列（App 重开时用）。
     *
     * 关键区别：**不把 running 置 true**。设备还在放上一首，但控制这个队列的前台服务并没有在跑
     * （没有 controller）。如实标记成「已恢复，未接管」，界面上点某一首就会直接投流接管，
     * 而不是假装有个正在运行的队列。
     */
    fun restore(
        /** 队列归属，必填（理由见 [begin]）。 */
        deviceIp: String,
        items: List<QueueItem>,
        index: Int,
        title: String?,
        deviceOwned: Boolean = false,
        offset: Int = 0,
        note: String? = null,
    ) {
        currentOwner = deviceIp
        _state.update {
        QueueRunState(
            ownerIp = deviceIp,
            running = false,
            items = items,
            index = index.coerceIn(0, (items.size - 1).coerceAtLeast(0)),
            nowPlaying = title ?: items.getOrNull(index)?.title,
            total = items.size,
            playedCount = index + 1,
            deviceOwned = deviceOwned,
            deviceOffset = offset,
            sourceNote = note,
        )
        }
    }

    /** 标记当前队列由设备持有（不启动本地循环）。 */
    fun markDeviceOwned(offset: Int = 0) =
        _state.update { it.copy(deviceOwned = true, running = false, deviceOffset = offset) }

    /** 设备侧位置回读后同步高亮（标题从本地列表取，设备不回填元数据）。 */
    fun syncDeviceIndex(deviceIndex: Int) = _state.update { st ->
        if (st.items.isEmpty() || deviceIndex < 0) return@update st
        // 设备侧位置 + 旋转偏移 = 本地列表里的位置
        val mapped = (st.deviceOffset + deviceIndex) % st.items.size
        st.copy(index = mapped, nowPlaying = st.items[mapped].title, deviceOwned = true)
    }

    /** 停止后保留 items/nowPlaying，方便 UI 显示「上次播到哪」。 */
    fun stopped() = _state.update { it.copy(running = false) }

    /**
     * 清空队列。带归属：只清**这台设备**的，别人的队列不该被顺手抹掉。
     */
    fun clear(deviceIp: String) {
        if (currentOwner != deviceIp) return
        _state.update { QueueRunState(ownerIp = currentOwner) }
    }

    /**
     * 这台设备的队列是不是"我们手上这份"。
     *
     * 调用方（播放页 / 切设备）用它判断：不匹配就必须重读设备，不能继续显示别人的列表。
     */
    fun owns(deviceIp: String?): Boolean = currentOwner != null && deviceIp != null && currentOwner == deviceIp

    /** 跳到第 [index] 首。服务不在时什么都不做（UI 自己回读状态即可）。 */
    fun skipTo(index: Int) {
        controller?.skipTo(index)
    }

    /**
     * 从队列里移除第 [index] 首。
     *
     * 服务在跑（有 controller）时交给服务，保证它自己的游标跟着调；
     * 服务没在跑（比如只投了一首、或者用户刚进来）时也必须能删 ——
     * 否则界面上点了叉毫无反应，就是用户说的「播放列表没法用」。
     * 所以这里保留一个本地兜底。
     */
    fun removeAt(index: Int) {
        val c = controller
        if (c != null) {
            c.removeAt(index)
        } else {
            dropLocal(index)
        }
    }

    /** 只改本地列表（服务缺席时的兜底）。当前曲之后的索引要跟着前移。 */
    private fun dropLocal(index: Int) = _state.update { st ->
        if (index !in st.items.indices) return@update st
        val next = st.items.toMutableList().apply { removeAt(index) }
        val newIndex = when {
            next.isEmpty() -> -1
            index < st.index -> st.index - 1
            index == st.index -> st.index.coerceAtMost(next.lastIndex)
            else -> st.index
        }
        st.copy(items = next, index = newIndex, total = next.size)
    }
}

/** 队列操作面：只暴露播放页需要的两个动作，避免 UI 依赖整个 Service。 */
interface QueueController {
    fun skipTo(index: Int)
    fun removeAt(index: Int)
}
