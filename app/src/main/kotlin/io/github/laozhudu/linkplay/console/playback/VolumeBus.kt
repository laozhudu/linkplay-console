package io.github.laozhudu.linkplay.console.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 某台设备当前的音量/静音状态。 */
data class DeviceVolume(
    val volume: Int = 0,
    val mute: Boolean = false,
)

/**
 * 全进程唯一的音量真相（按设备 IP 索引）。
 *
 * ## 为什么必须抽出来
 *
 * 之前播放页有 `PlaybackViewModel.volume`（自己轮询），首页多房间卡片有
 * `groupMembers[].volume`（只在进入 App 时读一次 `getPlayerStatus`）。
 * 两者互不相通，于是「在播放页拖了音量，回首页还是旧数字」——
 * 用户说的「主页音量应该和播放器的音量调节及时同步，目前不够及时」就是这个。
 *
 * 现在任何一处改动都写进这个对象，两处 UI 都订阅它，天然一致。
 * 设备侧的真实值仍然定期回读（播放页 1s、首页组 2.5s），回读结果也写进来，
 * 所以用遥控器/原厂 App 改了音量，这边也会跟上。
 *
 * 注意：这是**乐观值**。写进来的时候设备可能还没确认，
 * 但音量这种连续量被用户拖来拖去时，"立刻跟手"比"准确 200ms"更重要；
 * 一旦设备回过真实值就会覆盖它。
 */
object VolumeBus {
    private val _state = MutableStateFlow<Map<String, DeviceVolume>>(emptyMap())
    val state: StateFlow<Map<String, DeviceVolume>> = _state.asStateFlow()

    fun publish(ip: String, volume: Int, mute: Boolean) {
        if (ip.isBlank()) return
        val v = DeviceVolume(volume.coerceIn(0, 100), mute)
        _state.update { current ->
            // 值没变就不发新引用，避免无谓重组
            if (current[ip] == v) current else current + (ip to v)
        }
    }

    fun volumeOf(ip: String?): DeviceVolume? = ip?.let { _state.value[it] }

    /** 只改音量（拖动时用，静音状态保持原样）。 */
    fun publishVolume(ip: String, volume: Int) {
        val mute = _state.value[ip]?.mute ?: false
        publish(ip, volume, mute)
    }
}
