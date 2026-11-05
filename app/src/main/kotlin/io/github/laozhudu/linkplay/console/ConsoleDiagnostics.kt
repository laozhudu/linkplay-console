package io.github.laozhudu.linkplay.console

import android.app.Application
import io.github.laozhudu.diagnostics.Severity
import io.github.laozhudu.diagnostics.TimingLedger
import io.github.laozhudu.linkplay.console.diagnostics.ActionTiming
import io.github.laozhudu.linkplay.console.lx.ScriptLibrary
import io.github.laozhudu.linkplay.console.lx.ScriptSourceStatus
import io.github.laozhudu.linkplay.console.crash.CrashGuard
import io.github.laozhudu.linkplay.console.diagnostics.AppDiagnostics
import io.github.laozhudu.linkplay.console.diagnostics.DiagnosticsReport
import io.github.laozhudu.linkplay.console.diagnostics.NavTrace
import io.github.laozhudu.linkplay.console.floating.FloatingSourceStatus
import io.github.laozhudu.linkplay.console.library.LocalMediaRegistry
import io.github.laozhudu.linkplay.console.library.LocalSourceStatus
import io.github.laozhudu.linkplay.console.nas.NasSourceStatus
import io.github.laozhudu.linkplay.console.online.SubsonicSourceStatus
import io.github.laozhudu.linkplay.console.provisioning.ProvisionSourceStatus
import io.github.laozhudu.linkplay.console.radio.RadioSourceStatus
import io.github.laozhudu.linkplay.console.voice.VoiceSourceStatus
import io.github.laozhudu.linkplay.console.playback.QueueBus
import io.github.laozhudu.linkplay.console.session.SessionRestore
import io.github.laozhudu.linkplay.protocol.dlna.DeviceProfile
import io.github.laozhudu.linkplay.protocol.identity.UuidMatch
import io.github.laozhudu.linkplay.protocol.remote.CommandTrace

/**
 * 诊断页的**内容来源**：段注册 + 报告拼装。
 *
 * 从 `ConsoleViewModel`（当时 1409 行）拆出来的第四块。搬出来的理由和前三块不同 ——
 * 不是"行数太多"，而是这块代码**不改变任何状态**：
 * 它只读 `ConsoleUiState` 和各模块的静态状态，然后排版成文本。
 * 混在 ViewModel 里时，一个 1400 行的类里有一半函数在改状态、一半在读状态，
 * "改这里会不会影响那里"永远说不清。
 *
 * ## 它凭什么能拿到"全 App"的信息
 *
 * 三段来自别处自报，不在这里：
 * - `AppDiagnostics.sections` —— 谁有状态谁注册（播放、队列、其它模块）
 * - `AppDiagnostics.log` —— 协程失败、命令失败
 * - `CommandTrace` / `NavTrace` / `QueueBus` —— 口令流水、界面位置、队列归属
 *
 * 所以这里只留"必须由宿主拼起来"的部分：设备卡片、启动恢复、后台服务在不在跑。
 *
 * ## 为什么 register 由外部调用而不是在构造函数里
 *
 * 构造函数在 ViewModel 初始化早期执行，那时 `ConsoleUiState` 还是空的。
 * 注册本身不读状态（级别和正文都是函数，渲染时才读），但**把注册和构造分开**
 * 让"什么时候开始出现在诊断页"这件事在 init 里一眼可见。
 */
internal class ConsoleDiagnostics(
    private val application: Application,
    private val host: ConsoleHost,
    private val restore: SessionRestore,
) {

    /**
     * 一次「刷新设备」理论最少发几条指令：**在线设备数 × 2**
     * （每台 `GetControlDeviceInfo` + `getPlayerStatus`，这是实测的最短路径）。
     *
     * 实际条数多于这个数就说明有地方在旁路取数 —— 那正是"最短路径只写在注释里"的症状。
     * 还没探活过（在线数 0）时**不核对**：拿 0 当期望会把每一条指令都判成绕路。
     */
    private fun timingExpectations(): Map<String, Int> {
        val st = host.state.value
        val online = st.saved.count { st.online[it.uuid] == true }
        return if (online > 0) mapOf(ActionTiming.Action.REFRESH_DEVICES.id to online * 2) else emptyMap()
    }

    private fun clockText(ms: Long): String =
        if (ms <= 0L) "-" else java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date(ms))

    /** 把各段注册进诊断页。由 ViewModel 的 init 调用，只调一次。 */
        /**
     * 把「设备 / 队列 / 后台服务」三段注册进诊断页。
     *
     * 为什么设备段也走注册表：第一版是 `diagnosticsReport()` 里硬编码拼的，
     * 结果新增模块时总要回来改这个函数。现在谁有状态谁注册，报告只负责汇总。
     */
    fun register() {
        // 级别是**函数**：注册只发生一次，而"设备全没应答"是运行中才出现的状态
        AppDiagnostics.sections.register(
            "devices",
            "设备",
            {
                val st = host.state.value
                when {
                    // 全部未探测 = 探活根本没跑起来（PR #58 那个 NPE 就是这个形态）
                    st.saved.isNotEmpty() && st.saved.all { st.online[it.uuid] == null } -> Severity.WARN
                    // 在线但一条取数通道都没答 = 设备在、协议不通
                    st.saved.any { d -> st.online[d.uuid] == true && st.cards[d.uuid]?.answered?.isEmpty() == true } -> Severity.WARN
                    else -> Severity.INFO
                }
            },
        ) {
            val st = host.state.value
            if (st.saved.isEmpty()) "  没有已保存的设备"
            else buildString {
                st.saved.forEach { d ->
                    val c = st.cards[d.uuid]
                    appendLine("  ${d.title}  ${d.ip}")
                    appendLine(
                        "    状态=" + when (st.online[d.uuid]) {
                            true -> "在线"; false -> "离线"; null -> "未探测"
                        } +
                            (st.groupedUnder[UuidMatch.key(d.uuid)]?.let { "  属=$it" } ?: "") +
                            "  能力=${(c?.profile ?: DeviceProfile.UNKNOWN).name}",
                    )
                    appendLine(
                        "    音量=${c?.volume ?: 0}  声道=${DiagnosticsReport.channelText(c?.channel)}" +
                            "  电量=${c?.battery?.let { "$it%" } ?: "无"}" +
                            (if (c?.battery != null && c.charging) "（充电）" else "") +
                            "  播放=${if (c?.playing == true) "是" else "否"}",
                    )
                    appendLine(
                        "    取数通道=" +
                            (c?.answered?.takeIf { it.isNotEmpty() }?.map { it.name }?.sorted()?.joinToString("+")
                                ?: "全部未应答"),
                    )
                }
            }
        }
        // 启动恢复 —— 用户报「重开 App 列表是空的」时，光看界面分不清是哪一种：
        // 没读到上次那台设备 / 回到设备但设备上没有列表 / 设备没有、本地快照也没有。
        // 三种的修法完全不同，而这正是最容易反复问用户的一句话。
        AppDiagnostics.sections.register(
            "restore",
            "启动恢复",
            {
                val st = restore.status
                when {
                    // 一个设备都没保存时本来就没什么可恢复的，不算问题
                    host.state.value.saved.isEmpty() -> Severity.INFO
                    st.device == null -> Severity.WARN
                    st.queueCount == 0 -> Severity.WARN
                    else -> Severity.INFO
                }
            },
        ) {
            val st = restore.status
            buildString {
                appendLine("  设备=" + (st.device ?: "未恢复（${st.deviceSkipped ?: "未执行"}）"))
                appendLine(
                    "  队列=" + if (st.queueCount > 0) {
                        "${st.queueCount} 首 · 第 ${st.queueIndex + 1} 首 · 来源=${st.queueSource}"
                    } else {
                        st.queueNote ?: "未执行"
                    },
                )
                appendLine("  耗时=${st.elapsedMs}ms")
            }
        }
        // 洛雪脚本引擎（内容源）—— 先让它自己说出「能不能跑」，再谈界面。
        AppDiagnostics.sections.register(
            "source-lx",
            "洛雪引擎",
            { if (LxSourceStatus.snapshot().ok == false) Severity.WARN else Severity.INFO },
        ) { LxSourceStatus.render() }

        // 组 —— 「加了组却退不出来」的那一层：界面认知与设备真值对不对得上。
        AppDiagnostics.sections.register(
            "group",
            "组",
            {
                val g = GroupSourceStatus.snapshot()
                // 命令被接受但设备仍在组里 = 明确的问题
                if (g.actionConfirmed == false || (g.lastUnmatched > 0)) Severity.WARN else Severity.INFO
            },
        ) { GroupSourceStatus.render() }

        // 内容源 · 本地曲库 —— 第二个盲区（`docs/65` 批次 1）。
        // 它要回答的是「扫描 0 首」到底是没权限、真没歌、还是查到全被跳过。
        AppDiagnostics.sections.register(
            "source-local",
            "本地曲库",
            {
                val st = LocalSourceStatus.snapshot()
                if (st.granted == false || st.failure != null) Severity.WARN else Severity.INFO
            },
        ) { LocalSourceStatus.render() }

        // 内容源 · 在线曲库（Subsonic / tt-music）—— 第三个盲区（`docs/65` 批次 1）。
        // 要回答的是：搜不到是这个词没命中，还是服务端曲库本来就是空的。
        AppDiagnostics.sections.register(
            "source-subsonic",
            "在线曲库",
            {
                val st = SubsonicSourceStatus.snapshot()
                when {
                    st.verified == false -> Severity.WARN
                    st.failure != null -> Severity.WARN
                    st.zeroStreak >= SubsonicSourceStatus.ZERO_STREAK_HINT -> Severity.WARN
                    else -> Severity.INFO
                }
            },
        ) { SubsonicSourceStatus.render() }

        // 内容源 · 电台 —— 第四个盲区（`docs/65` 批次 1）。
        // 这个源最典型的坑是 https：命令回 OK 但 status 永远不动，界面上只是"没反应"。
        AppDiagnostics.sections.register(
            "source-radio",
            "电台",
            { if (RadioSourceStatus.snapshot().failure != null) Severity.WARN else Severity.INFO },
        ) { RadioSourceStatus.render() }

        // 配网 —— 第五个盲区（`docs/65` 批次 1）。
        // 这里最典型的误判是「命令回 OK」被当成「配好了」，实际要等设备在新网络里应答。
        AppDiagnostics.sections.register(
            "source-provision",
            "配网",
            { if (ProvisionSourceStatus.snapshot().failure != null) Severity.WARN else Severity.INFO },
        ) { ProvisionSourceStatus.render() }

        // 语音 —— 第六个盲区（`docs/65` 批次 1）。
        // 三段（识别 / 意图 / 执行）必须分开，否则"语音不管用"只能靠问用户说哪句话。
        AppDiagnostics.sections.register(
            "source-voice",
            "语音",
            { if (VoiceSourceStatus.snapshot().failure != null) Severity.WARN else Severity.INFO },
        ) { VoiceSourceStatus.render() }

        // 悬浮窗 —— 第七个盲区（`docs/65` 批次 1，最后一个）。
        // 权限、服务、窗口几何三件事分开记：界面上它们都表现为"没有那个条"。
        AppDiagnostics.sections.register(
            "source-floating",
            "悬浮窗",
            { if (FloatingSourceStatus.snapshot().failure != null) Severity.WARN else Severity.INFO },
        ) { FloatingSourceStatus.render() }

        // 内容源 · NAS —— 七个盲区里的第一个（`docs/61` 的施工顺序），
        // 选它打头是因为 `docs/60` 要改 NAS 的推流路径，改之前必须先能看见现在的行为。
        AppDiagnostics.sections.register(
            "source-nas",
            "NAS",
            { if (NasSourceStatus.snapshot().failure != null) Severity.WARN else Severity.INFO },
        ) { NasSourceStatus.render() }

        // 界面位置 —— 用户说「点了没反应」时，先要能知道他站在哪一页、有没有浮层挡着。
        // 同样的现象在不同页面是不同的问题，没有这条我只能反问他。
        // 脚本 —— 洛雪只负责搜，取直链全靠脚本。所以"没有脚本/脚本坏了"是
        // "搜到歌却播不出来"的第一嫌疑，必须能一眼看到。
        AppDiagnostics.sections.register(
            "scripts",
            "脚本",
            { if (ScriptSourceStatus.lastFailed()) Severity.WARN else Severity.INFO },
        ) {
            ScriptSourceStatus.render(
                entries = ScriptLibrary.entries(application),
                unrecognized = ScriptLibrary.unrecognized(application),
                activeName = ScriptLibrary.activeName(application),
            )
        }

        // 播放输出 —— 本地播和推流是两套实现，出问题时第一件事是确认"声音该从哪出来"
        AppDiagnostics.sections.register("output", "播放输出") {
            val router = io.github.laozhudu.linkplay.console.playback.PlaybackRouter.of(application)
            val out = router.output()
            val local = router.localState.value
            buildString {
                appendLine("  当前=${if (out == io.github.laozhudu.linkplay.console.playback.PlaybackOutput.Local) "本机（Media3 ExoPlayer）" else "音箱（推流）"}")
                appendLine("  本机状态=在放 ${local.playing} · 有内容 ${local.hasQueue} · ${local.displayTitle}")
                if (out == io.github.laozhudu.linkplay.console.playback.PlaybackOutput.Local && local.hasQueue && !local.playing) {
                    appendLine("  ← 本机有内容但没在放：可能是被来电/别的 App 抢了焦点")
                }
            }.trimEnd()
        }

        // 红外 —— 第一条要确认的信息是"这台手机有没有发射器"，
        // 没有的话整个功能不成立（手机也不能学码，docs/81）。
        AppDiagnostics.sections.register(
            "ir",
            "红外",
            { if (io.github.laozhudu.linkplay.console.ir.IrStatus.lastFailed()) Severity.WARN else Severity.INFO },
        ) { io.github.laozhudu.linkplay.console.ir.IrStatus.render() }

        AppDiagnostics.sections.register("ui", "界面") { NavTrace.render() }

        // 口令流水 —— 覆盖「点了没反应」这类最常报的问题。
        // 三种情况在界面上表现完全一样（发了没照做 / 根本没发 / 回包看不懂），
        // 只有流水账能分清。
        AppDiagnostics.sections.register(
            "commands",
            "指令",
            {
                val trace = CommandTrace.DEFAULT
                when {
                    // 连续失败 3 次以上 = 整条链路断了，和偶发一条失败是两回事
                    trace.consecutiveFailures() >= 3 -> Severity.ERROR
                    trace.failedCount > 0 -> Severity.WARN
                    else -> Severity.INFO
                }
            },
        ) { CommandTrace.DEFAULT.render() }

        // 响应耗时 —— 回答三个问题：跟不跟手、是不是绕路、是不是两处在刷同一件事。
        // 判据全写在段里，不让读的人自己算：
        //   1. 慢动作排前面（平均耗时降序）；
        //   2. 同一动作 2 秒内多次 = 重复触发（两处代码各刷一次 / 事件绑了两遍）；
        //   3. 指令数多于「在线设备数 × 2」= 有取数旁路，最短路径没落地。
        AppDiagnostics.sections.register(
            "timing",
            "响应耗时",
            {
                val slowest = ActionTiming.ledger.slowest(1).firstOrNull()
                val expect = timingExpectations()[ActionTiming.Action.REFRESH_DEVICES.id]
                val detour = expect?.let { ActionTiming.ledger.detourOf(ActionTiming.Action.REFRESH_DEVICES.id, it) }
                when {
                    slowest != null && slowest.avgMs >= TimingLedger.SLOW_MS -> Severity.WARN
                    detour != null -> Severity.WARN
                    else -> Severity.INFO
                }
            },
        ) { ActionTiming.render(expectations = timingExpectations()) }

        AppDiagnostics.sections.register(
            "queue",
            "队列",
            { if (QueueBus.state.value.controlLost || QueueBus.state.value.error != null) Severity.WARN else Severity.INFO },
        ) {
            val q = QueueBus.state.value
            buildString {
                appendLine("  归属=${q.ownerIp ?: "未知"}  运行中=${q.running}  设备持有=${q.deviceOwned}")
                appendLine("  共 ${q.total} 首 · 当前第 ${q.index + 1} 首 · ${q.nowPlaying ?: "无"}")

                appendLine("  来源=${q.sourceNote ?: "-"}  偏移=${q.deviceOffset}  控制丢失=${q.controlLost}")
                q.error?.let { appendLine("  错误=$it") }
            }
        }
        AppDiagnostics.sections.register(
            "services",
            "后台服务",
            {
                // 推流失败（读文件出错这类）才算问题；"中断"是音箱切歌，不是缺陷
                val failed = LocalMediaRegistry.mediaServer()?.stats()?.failed ?: 0
                if (QueueBus.state.value.running || failed > 0) Severity.WARN else Severity.INFO
            },
        ) {
            val app = application
            val names = listOf(
                "playback.QueuePlaybackService" to "队列播放",
                "library.MediaServerService" to "本地媒体服务",
                "floating.FloatingControlService" to "悬浮控制",
            )
            buildString {
                names.forEach { (cls, label) ->
                    // 直接问 ActivityManager：比"我们以为启动了"可靠
                    val running = runCatching {
                        val info = app.packageManager.getPackageInfo(app.packageName, android.content.pm.PackageManager.GET_SERVICES)
                        @Suppress("UNCHECKED_CAST")
                        val am = app.getSystemService(android.content.Context.ACTIVITY_SERVICE)
                            as android.app.ActivityManager
                        // Android 没有公开 API 查"某服务是否在跑"，用 getRunningServices 在本 App 内可用
                        @Suppress("DEPRECATION")
                        am.getRunningServices(Int.MAX_VALUE).any { it.service.className == "${app.packageName}.$cls" }
                    }.getOrDefault(false)
                    appendLine("  $label=${if (running) "运行中" else "未运行"}")
                    // 只写"运行中"解释不了"本地音乐没声音"：请求到没到、发完没有、是不是断了
                    if (cls == "library.MediaServerService" && running) {
                        LocalMediaRegistry.mediaServer()?.let { m ->
                            val st = m.stats()
                            appendLine(
                                "    推流=:${m.port} 请求=${st.requests} 完成=${st.completed}" +
                                    " 中断=${st.aborted} 失败=${st.failed}",
                            )
                            if (st.aborted > 0) {
                                appendLine("    最近中断=${clockText(st.lastAbortMs)} ${st.lastAbortNote}")
                            }
                            if (st.failed > 0) appendLine("    最近失败=${st.lastFailedNote}")
                        }
                    }
                }
            }
        }
    }

        /**
     * 生成一份可截图/可复制的诊断报告。
     *
     * 为什么不在界面里拼：报告要读设备卡片、在线状态、组归属、
     * 协程问题记录、上次崩溃 —— 这些都是状态层的数据。界面只负责显示和复制。
     */
    fun report(): String {
        val st = host.state.value
        val app = application
        val crash = runCatching { CrashGuard.lastReport(app) }.getOrNull()
        val lines = st.saved.map { d ->
            val card = st.cards[d.uuid]
            DiagnosticsReport.DeviceLine(
                name = d.title,
                ip = d.ip,
                online = st.online[d.uuid],
                groupedUnder = st.groupedUnder[UuidMatch.key(d.uuid)],
                battery = card?.battery,
                charging = card?.charging == true,
                channel = card?.channel,
                volume = card?.volume ?: 0,
                playing = card?.playing == true,
                answered = card?.answered?.map { it.name }?.toSet() ?: emptySet(),
                profile = (card?.profile ?: DeviceProfile.UNKNOWN).name,
            )
        }
        val version = runCatching {
            val info = app.packageManager.getPackageInfo(app.packageName, 0)
            "${info.versionName}（${info.longVersionCode}）"
        }.getOrDefault("未知")
        val now = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date())
        val text = DiagnosticsReport.build(
            version = version,
            now = now,
            crash = crash,
            devices = lines,
            entries = AppDiagnostics.recent(),
            // 各模块自报（设备/播放/队列/后台服务），新增模块不用改这里
            sections = AppDiagnostics.sections.render(),
            uptime = AppDiagnostics.uptimeText(),
        )
        // 全文进 logcat：用户一点"诊断"，插上数据线 `adb logcat -s LinkPlayDiag` 就能读到，
        // 不用再让他截图、也不怕他截漏
        AppDiagnostics.logReport(text)
        return text
    }
}
