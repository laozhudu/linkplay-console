package io.github.laozhudu.linkplay.protocol.multiroom

import io.github.laozhudu.linkplay.protocol.DeviceHttpClient
import io.github.laozhudu.linkplay.protocol.Json
import io.github.laozhudu.linkplay.protocol.JsonValue

/**
 * 主机自身热点的关键参数。
 *
 * 为什么必须从 `getStatusEx` 读、不能用猜的：
 * 单 Wi-Fi 模块的机器上，**AP 和 STA 只能是同一个信道**，
 * 所以主机热点的信道一定等于它当前连上游 Wi-Fi 的信道（`WifiChannel`）。
 * 真机两台都在 11，而原厂 APK 里 `h.m` 也是从 `getStatusEx` 读出来的。
 * 之前传 `ch=0` 让固件自己选，实测两个信道段都没连上。
 *
 * @param ssid 自身热点名（`ssid` 字段，如 `DOSS DS-1831_400C`）；**不是** `essid`（那是它连的上游）
 * @param channel 热点信道（`WifiChannel`），读不到为 0
 * @param securemode 0=开放，非 0=加密
 */
data class ApInfo(
    val ssid: String,
    val channel: Int = 0,
    val securemode: Int = 0,
) {
    val isOpen: Boolean get() = securemode == 0
}

/** 一台已经或准备入组的从机。 */
data class SlaveInfo(
    val ip: String,
    val uuid: String? = null,
    val name: String? = null,
    val volume: Int? = null,
    val inGroup: Boolean = false,
)

/**
 * 多房间（LinkPlay 的 multiroom）。
 *
 * ## 命令与参数格式的来源
 *
 * 全部逐字取自原厂 APK 的 `com.wifiaudio.a.v`（jadx 反编译），不是猜的：
 *
 * ```
 * multiroom:getSlaveList                              读从机列表
 * multiroom:SlaveVolume:<从机IP>:<0-100>              设从机音量
 * multiroom:SlaveChannel:<从机IP>:<声道>               设从机声道
 * multiroom:SlaveDeviceName:<从机IP>:<URL 编码名>      给从机改名
 * multiroom:SlaveKickout:<参数>                        把从机踢出组
 * multiroom:SlaveMask:<参数>                           主机拉从机入组
 * multiroom:SlaveUnMask:<参数>                         取消入组
 * ```
 *
 * 注意 `SlaveVolume`/`SlaveChannel`/`SlaveDeviceName` 的第一个参数是**从机 IP**，
 * 而 `SlaveMask`/`SlaveUnMask`/`SlaveKickout` 的参数在 APK 里是由上层逻辑拼出来的字符串
 * （`v.b(String,String)` 直接透传），无法从反编译确定。
 *
 * ## 成组是怎么做的（原厂 APK 的拖拽逻辑）
 *
 * 真正的组队命令在 `com.wifiaudio.a.b.a(master, slave, apInfo)`，逐字是：
 *
 * ```
 * GET http://<从机IP>/httpapi.asp?command=multiroom:ConnnectAP:<主机IP>:ConnectMasterAp:
 *     ssid=<主机自身热点名的小写HEX>:ch=<信道>:auth=<WPA2PSK|OPEN>:encry=<AES|NONE>:pwd=<HEX>:chext=1
 * ```
 *
 * 三个要点，都是从代码里读出来的、容易搞错的：
 * 1. **请求发给从机**，主机信息放在参数里（不是发给主机让它去拉从机）；
 * 2. `ssid` 用**小写 hex**（普通配网的 `ConnectMasterAp` 用大写 hex，两者不同）；
 * 3. `chext=1` 表示「这是多房间从机接入」，与普通配网的 `chext=0` 区分。
 *
 * 发完之后原厂 App 的做法是**轮询确认**：定时器每 2 秒读一次主机的
 * `multiroom:getSlaveList`，40 秒内看到从机出现就算成功（`drag_msg_play_sync_success`），
 * 超时就是失败（`drag_msg_play_sync_fail_timeout`）。这个类的 [joinWithPolling] 就是这条流程。
 *
 * ## 真机实测的困境（必须知道）
 *
 * 两台 DS-1831（`.153` fw 3.6.5310 / `.164` fw 3.6.6623）同时在线时：
 *
 * ```
 * A: multiroom:getSlaveList → {"slaves": 0}
 * B: multiroom:getSlaveList → {"slaves": 0}
 * 在 B 上发 multiroom:SlaveMask:<各种参数> → 回 OK，但两台读回来仍是 0
 * ```
 *
 * 也就是说**设备之间还没互相「看见」**：`OK` 只代表命令被接受，不代表组队成功。
 * 原厂 App 里对应的入口是 `SlaveSpeakerAddWPSActivity`（提示用户按 WPS 键配对），
 * 说明这一步是**硬件配对**，软件无法单方面完成。
 *
 * 所以本类只做「能确定的事」：读从机列表、给已入组的从机调音量/声道/改名/踢出。
 * 入组本身需要设备先互相可见，调用方必须把这一点如实告诉用户，不能假装成功。
 */
class MultiroomClient(
    private val http: DeviceHttpClient = DeviceHttpClient(),
) {

    /**
     * 让 [slaveIp] 接入 [masterIp] 的组。
     *
     * @param masterSsid 主机的**自身热点名**（`getStatusEx.ssid`，如 `DOSS DS-1831_400C`），
     *   不是它连着的上游 Wi-Fi（那是 `essid`，容易搞混）
     * @param channel 主机热点信道；拿不到就传 0（固件会自己选）
     * @param password 主机热点密码；真机 `securemode=0` 表示无密码，传空串即可
     */
    fun joinGroup(
        masterIp: String,
        masterSsid: String,
        slaveIp: String,
        channel: Int = 0,
        password: String = "",
    ): String {
        // 忠实照抄原厂：`auth` 只看「密码串是否非空」，不看它是真密码还是脏数据。
        // 真机教训 —— 我曾按 securemode=0 自作聪明改用 OPEN，命令照样回 OK，但从机根本不来；
        // 原厂走的是 WPA2PSK 分支，用它就能组上。宁可照抄，不要"优化"协议。
        val secured = password.isNotEmpty()
        val cmd = buildString {
            append("multiroom:ConnnectAP:").append(masterIp)
            append(":ConnectMasterAp:ssid=").append(hexLower(masterSsid))
            append(":ch=").append(channel)
            append(":auth=").append(if (secured) "WPA2PSK" else "OPEN")
            append(":encry=").append(if (secured) "AES" else "NONE")
            append(":pwd=").append(if (secured) hexLower(password) else "")
            append(":chext=1")
        }
        return http.send(slaveIp, cmd)
    }

    /**
     * 下发成组命令并轮询确认（照搬原厂 App 的 2 秒 / 40 秒节奏）。
     *
     * 为什么要轮询而不是看命令返回值：`multiroom:*` 系列**只要命令名认识就回 `OK`**，
     * 真机上 `SlaveMask` 各种参数都回 OK 但一台从机都没出现（见下文实测）。
     * 唯一可信的成功判据是「主机读回来的从机列表里真的多了一台」。
     *
     * @param onTick 每次轮询回调（已用秒数），给 UI 显示进度用
     * @return 成功为 true；40 秒内没看到从机则为 false
     */
    fun joinWithPolling(
        masterIp: String,
        masterSsid: String,
        slaveIp: String,
        channel: Int = 0,
        password: String = "",
        timeoutMs: Long = 40_000,
        intervalMs: Long = 2_000,
        sleep: (Long) -> Unit = { Thread.sleep(it) },
        now: () -> Long = { System.currentTimeMillis() },
        onTick: (Long) -> Unit = {},
        shouldStop: () -> Boolean = { false },
    ): Boolean {
        // 信道：AP 与 STA 同信道，用主机自报的 WifiChannel（传 0 让固件自选实测不行）
        val realChannel = if (channel > 0) channel else (readApInfo(masterIp)?.channel ?: 0)
        // 密码：留空则照原厂从 getNetwork 取整段返回值（见 readJoinPassword 的说明）
        val realPassword = password.ifBlank { readJoinPassword(masterIp) }
        runCatching { joinGroup(masterIp, masterSsid, slaveIp, realChannel, realPassword) }
        // 从机会换 IP：接入主机热点后它落在主机的 AP 网段（真机实测 192.168.99.153 → 10.10.10.92），
        // 所以「按 IP 找自己刚发命令的那台」永远找不到。正确判据是**列表里多了一台新的**。
        val before = slaves(masterIp).map { identity(it) }.toSet()
        val start = now()
        while (now() - start < timeoutMs) {
            sleep(intervalMs)
            if (shouldStop()) return false
            onTick((now() - start) / 1000)
            val added = slaves(masterIp).filter { identity(it) !in before }
            if (added.isNotEmpty()) return true
        }
        return false
    }

    /** 从机身份：uuid 归一化后优先，没有 uuid 才退回 IP（IP 会变，不能当主键）。 */
    private fun identity(slave: SlaveInfo): String =
        (slave.uuid ?: "").removePrefix("uuid:").lowercase().ifBlank { slave.ip }

    /** 读某台设备（通常是主机）已知的从机。设备不认识从机时返回空列表。 */
    fun slaves(masterIp: String): List<SlaveInfo> {
        val raw = runCatching { http.send(masterIp, "multiroom:getSlaveList") }.getOrNull() ?: return emptyList()
        return parseSlaveList(raw)
    }

    /**
     * 读主机自身热点的 [ApInfo]（ssid / 信道 / 加密方式）。
     *
     * 只读 `getStatusEx`，**不含密码** —— 密码的真实来源见 [readJoinPassword]。
     */
    fun readApInfo(masterIp: String): ApInfo? {
        val raw = runCatching { http.send(masterIp, "getStatusEx") }.getOrNull() ?: return null
        val root = runCatching { Json.parse(raw) }.getOrNull() as? JsonValue.Obj ?: return null
        val ssid = root.string("ssid")?.takeIf { it.isNotBlank() } ?: return null
        return ApInfo(
            ssid = ssid,
            channel = root.string("WifiChannel")?.toIntOrNull() ?: root.int("WifiChannel") ?: 0,
            securemode = root.string("securemode")?.toIntOrNull() ?: 0,
        )
    }

    /**
     * 读主机热点密码 —— 就照原厂的做法，把 `getNetwork` 的**整段返回值**当密码。
     *
     * 听起来很脏，但这正是原厂 `ax.a(String)` 干的事：
     * ```java
     * this.f1415a.f.n = str;                       // str = getNetwork 的返回
     * com.wifiaudio.a.b.a(this.b, this.c, this.f1415a);
     * ```
     * 然后 `b.a()` 用 `str.length() != 0 ? "WPA2PSK" : "OPEN"` 决定 auth。
     *
     * 这批固件 `getNetwork` 只返回 `{"securemode":"0",}`（不是密码），于是原厂实际发出的是
     * `auth=WPA2PSK:encry=AES:pwd=<那段 JSON 的 hex>`。**这个"错"的命令能组上**，
     * 而按 `securemode=0` 推断出的 `auth=OPEN` 组不上 —— 真机两台机器对照验证过。
     *
     * 结论：这里不做"美化"，原样返回，让 [joinGroup] 走与原厂完全相同的分支。
     * 用户在 UI 里手填密码时，会覆盖这里的结果。
     */
    fun readJoinPassword(masterIp: String): String {
        val raw = runCatching { http.send(masterIp, "getNetwork") }.getOrNull() ?: return ""
        val t = raw.trim()
        return if (t.startsWith("<ERR") || t == "unknown command") "" else t
    }

    fun setSlaveVolume(masterIp: String, slaveIp: String, volume: Int): String =
        http.send(masterIp, "multiroom:SlaveVolume:$slaveIp:${volume.coerceIn(0, 100)}")

    fun setSlaveChannel(masterIp: String, slaveIp: String, channel: Int): String =
        http.send(masterIp, "multiroom:SlaveChannel:$slaveIp:${channel.coerceIn(0, 2)}")

    fun renameSlave(masterIp: String, slaveIp: String, name: String): String =
        http.send(masterIp, "multiroom:SlaveDeviceName:$slaveIp:${java.net.URLEncoder.encode(name, "utf-8")}")

    /**
     * 让从机入组。
     *
     * 参数在 APK 里是不透明的字符串，这里按最常见的两种写法各试一次（从机 IP、从机 uuid），
     * 并且**只用返回值判断命令是否被接受**，真正的成功与否要重新读 [slaves] 才知道。
     */
    fun mask(masterIp: String, slave: SlaveInfo): List<String> {
        val candidates = buildList {
            add(slave.ip)
            slave.uuid?.let { add(it) }
        }.distinct()
        return candidates.map { p ->
            val r = runCatching { http.send(masterIp, "multiroom:SlaveMask:$p") }.getOrDefault("<ERR>")
            "$p → $r"
        }
    }

    fun unmask(masterIp: String, slave: SlaveInfo): String =
        runCatching { http.send(masterIp, "multiroom:SlaveUnMask:${slave.ip}") }.getOrDefault("<ERR>")

    fun kickout(masterIp: String, slave: SlaveInfo): String =
        runCatching { http.send(masterIp, "multiroom:SlaveKickout:${slave.ip}") }.getOrDefault("<ERR>")

    companion object {
        /** 小写 hex（多房间命令用这种；普通配网命令用大写，两者不要混）。 */
        internal fun hexLower(text: String): String =
            text.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) }

        /**
         * 解析 `{"slaves": N}` 或更完整的从机数组。
         *
         * 真机实测只见过 `{"slaves": 0}` 这一种形状；其他固件可能返回
         * `{"slaves":[{...}]}` 或 `{"slave_list":[...]}`（`getStatusEx` 里用的是后者），
         * 所以三种都兼容 —— 解析失败时返回空列表而不是抛异常，
         * 不能让「读不到从机」把整个设置页打挂。
         */
        fun parseSlaveList(raw: String): List<SlaveInfo> {
            if (raw.isBlank()) return emptyList()
            val root = runCatching { Json.parse(raw) }.getOrNull() as? JsonValue.Obj ?: return emptyList()

            // 形状 1：{"slaves": 0}
            (root["slaves"] as? JsonValue.Num)?.let { if (it.value.toInt() == 0) return emptyList() }

            // 形状 2/3：数组形式
            val arr = root.array("slaves") ?: root.array("slave_list") ?: return emptyList()
            return arr.items.mapNotNull { v ->
                val o = v as? JsonValue.Obj ?: return@mapNotNull null
                val ip = o.string("ip") ?: return@mapNotNull null
                SlaveInfo(
                    ip = ip,
                    uuid = o.string("uuid"),
                    name = o.string("name") ?: o.string("DeviceName"),
                    volume = o.string("vol")?.toIntOrNull() ?: o.int("vol"),
                    inGroup = (o.string("group") ?: o.string("mask"))?.let { it != "0" } ?: true,
                )
            }
        }
    }
}
