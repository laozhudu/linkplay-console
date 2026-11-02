package io.github.laozhudu.linkplay.protocol

import java.net.URLEncoder

/**
 * LinkPlay / WiiMu 设备 HTTP 控制面。
 *
 * 全部命令走 `GET http://<device-ip>/httpapi.asp?command=<cmd>`，明文、无鉴权、
 * 无 token。命令字符串来自对原厂 App 的静态分析（见 docs/01-linkplay-protocol.md），
 * 这里只做「拼串」这一件事，方便单测覆盖参数格式。
 *
 * 注意：**播放/暂停/音量这类通道不走这里**。原厂 App 是用 UPnP AVTransport /
 * RenderingControl 控制的，本文件只覆盖 httpapi 面。`setPlayerCmd:switchmode:` 是
 * 输入源切换，不是播放控制。
 */
object LinkPlayCommands {

    const val HTTP_API_PATH = "/httpapi.asp?command="

    /** 设备属性：返回 JSON，字段见 [LinkPlayStatus]。 */
    fun statusEx(): String = "getStatusEx"

    /** 当前 Wi-Fi / 网络状态：返回文本键值。 */
    fun network(): String = "getNetwork"

    /** 扫描 AP 列表（配网用）。返回文本，逐行 AP 信息。 */
    fun apList(): String = "wlanGetApListEx"

    /** 设备名，URL 编码（不是 hex）。 */
    fun deviceName(name: String): String = "setDeviceName:" + urlEncode(name)

    /** 语言，如 `zh_CN` / `en_US`。 */
    fun language(lang: String): String = "setLanguage:$lang"

    /** 恢复出厂。危险操作，客户端必须二次确认。 */
    fun restoreToDefault(): String = "restoreToDefault"

    /** 输入源切换，如 `wifi` / `line-in` / `bluetooth` / `optical`。 */
    fun switchMode(mode: String): String = "setPlayerCmd:switchmode:$mode"

    /** 模拟面板按键，n 为厂商定义的键值。 */
    fun keyClick(n: Int): String = "MCUKeyShortClick:$n"

    /** 配网后让设备停止投递 EasyLink 应答。 */
    fun easyLinkResponseStop(): String = "EasyLinkResponseStop"

    /** 检查网络/服务连通性。 */
    fun startCheck(): String = "StartCheck"

    /**
     * `setNetwork:<dhcp 0|1>:<payload>`（原厂把第二个参数直接拼在冒号后，
     * 具体 payload 语义未在静态分析中确证，因此按原始字符串透传）。
     */
    fun setNetwork(dhcp: Boolean, payload: String): String =
        "setNetwork:${if (dhcp) 1 else 0}:$payload"

    // ---- 配网（AP 模式直连设备后下发） ----

    /**
     * 让设备连到指定 Wi-Fi。ssid/pwd 用**大写 hex**，与原厂一致。
     * `auth=WPA2PSK/encry=AES` 当密码非空，否则 `OPEN/NONE`。
     */
    fun connectMasterAp(ssid: String, channel: Int, password: String, chext: Int = 0): String {
        val secured = password.isNotEmpty()
        return buildString {
            append("ConnectMasterAp:ssid=").append(HexCodec.encodeUtf8Upper(ssid))
            append(":ch=").append(channel)
            append(":auth=").append(if (secured) "WPA2PSK" else "OPEN")
            append(":encry=").append(if (secured) "AES" else "NONE")
            append(":pwd=").append(HexCodec.encodeUtf8Upper(password))
            append(":chext=").append(chext)
        }
    }

    /** `wlanConnectApEx`：已连设备的 Wi-Fi 后，让它去连另一个 AP（ssid/pwd 小写 hex）。 */
    fun wlanConnectApEx(ssid: String, channel: Int, auth: String, encry: String, password: String): String =
        buildString {
            append("wlanConnectApEx:ssid=").append(HexCodec.encodeUtf8(ssid))
            append(":ch=").append(channel)
            append(":auth=").append(auth)
            append(":encry=").append(encry)
            append(":pwd=").append(HexCodec.encodeUtf8(password))
            append(":chext=1")
        }

    /** 多房间：让某台从机跟随主机所在 AP（原厂拼法，注意 `ConnnectAP` 三个 n 是原样保留的）。 */
    fun multiroomConnectAp(masterIp: String, ssid: String, channel: Int, password: String): String {
        val secured = password.isNotEmpty()
        return buildString {
            append("multiroom:ConnnectAP:").append(masterIp)
            append(":ConnectMasterAp:ssid=").append(HexCodec.encodeUtf8(ssid))
            append(":ch=").append(channel)
            append(":auth=").append(if (secured) "WPA2PSK" else "OPEN")
            append(":encry=").append(if (secured) "AES" else "NONE")
            append(":pwd=").append(HexCodec.encodeUtf8(password))
            append(":chext=1")
        }
    }

    // ---- 多房间编组 ----

    fun slaveList(): String = "multiroom:getSlaveList"

    fun slaveVolume(slaveIp: String, volume: Int): String = "multiroom:SlaveVolume:$slaveIp:$volume"

    fun slaveChannel(slaveIp: String, channel: Int): String = "multiroom:SlaveChannel:$slaveIp:$channel"

    /** 从机改名，URL 编码。 */
    fun slaveDeviceName(slaveIp: String, name: String): String =
        "multiroom:SlaveDeviceName:$slaveIp:" + urlEncode(name)

    /** 把从机加入本机群组（ip 为掩码串，按设备返回的 mask 列表下发）。 */
    fun slaveMask(ips: String): String = "multiroom:SlaveMask:$ips"

    fun slaveUnMask(ips: String): String = "multiroom:SlaveUnMask:$ips"

    fun slaveKickout(slaveIp: String): String = "multiroom:SlaveKickout:$slaveIp"

    // ---- 音箱固件 OTA ----

    fun firmwareUpdateStart(): String = "getMvRemoteUpdateStart"

    fun firmwareUpdateStatus(): String = "getMvRemoteUpdateStatus"

    fun firmwareDownloadStatus(): String = "getMvRomDownloadStatus"

    fun firmwareBurnPercent(): String = "getMvRomBurnPrecent"


    // ---- 播放控制（WiiM 官方 HTTP API v1.2 §2.3；老固件可能不支持，需探测） ----

    fun getPlayerStatus(): String = "getPlayerStatus"

    fun getMetaInfo(): String = "getMetaInfo"

    /** 直接播一个音频流地址；官方说明「响应固定为 OK」。 */
    fun playUrl(url: String): String = "setPlayerCmd:play:$url"

    /** 播 m3u/asx 播放列表，index 为起始序号。 */
    fun playPlaylist(url: String, index: Int = 0): String = "setPlayerCmd:playlist:$url:$index"

    fun pause(): String = "setPlayerCmd:pause"

    fun resume(): String = "setPlayerCmd:resume"

    /** 当前在播就暂停、暂停就继续。 */
    fun togglePause(): String = "setPlayerCmd:onepause"

    fun stopPlayer(): String = "setPlayerCmd:stop"

    fun nextTrack(): String = "setPlayerCmd:next"

    fun previousTrack(): String = "setPlayerCmd:prev"

    /** 位置单位是**秒**（官方文档明确），不是毫秒。 */
    fun seekPlayer(seconds: Long): String = "setPlayerCmd:seek:${seconds.coerceAtLeast(0)}"

    fun playerVolume(volume: Int): String = "setPlayerCmd:vol:${volume.coerceIn(0, 100)}"

    fun playerMute(mute: Boolean): String = "setPlayerCmd:mute:${if (mute) 1 else 0}"

    /** 0 顺序不循环 / 1 单曲循环 / 2 随机循环 / -1 顺序循环。 */
    fun loopMode(mode: Int): String = "setPlayerCmd:loopmode:$mode"

    // ---- 预设键（官方 §2.8） ----

    fun presetInfo(): String = "getPresetInfo"

    // ---- 设备控制（官方 §2.5 / §2.6） ----

    fun reboot(): String = "reboot"

    /** sec=0 立即关机，-1 取消关机定时。 */
    fun shutdown(seconds: Int): String = "setShutdown:$seconds"

    fun getShutdown(): String = "getShutdown"

    fun timeSync(yyyymmddhhmmss: String): String = "timeSync:$yyyymmddhhmmss"

    fun alarmStop(): String = "alarmStop"

    // ---- EQ（官方 §2.4） ----

    fun eqOn(): String = "EQOn"

    fun eqOff(): String = "EQOff"

    fun eqStatus(): String = "EQGetStat"

    fun eqList(): String = "EQGetList"

    fun eqLoad(name: String): String = "EQLoad:$name"

    fun urlFor(deviceIp: String, command: String): String =
        "http://$deviceIp$HTTP_API_PATH$command"

    private fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
