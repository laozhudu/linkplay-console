package io.github.laozhudu.linkplay.console.remote

/**
 * 一个"遥控器按键"的语义。
 *
 * ## 为什么用语义而不是码值
 *
 * 红外是 NEC 码、蓝牙是 HID usage code、WiFi 遥控是各家自己的 JSON ——
 * 三种传输的"同一个键"长得完全不一样。
 *
 * 如果界面直接存码值，那么**红外遥控器就永远只能是红外遥控器**，
 * 换成蓝牙要重画一遍界面。
 *
 * 用语义之后：界面只认 [RemoteKey.Power]，由各实现自己翻译成自家的码。
 */
sealed interface RemoteKey {

    data object Power : RemoteKey
    data object VolumeUp : RemoteKey
    data object VolumeDown : RemoteKey
    data object Mute : RemoteKey

    data object Up : RemoteKey
    data object Down : RemoteKey
    data object Left : RemoteKey
    data object Right : RemoteKey
    data object Ok : RemoteKey

    data object Back : RemoteKey
    data object Menu : RemoteKey
    data object Home : RemoteKey

    /** 信号源 / 输入切换。 */
    data object InputSource : RemoteKey

    /**
     * 码库里有、但没有归类的键。
     *
     * 码库（Flipper-IRDB，30 万条）里大量按键没有标准语义，
     * 全部硬塞进上面那十几个只会让枚举失去意义。
     * 这类键用 [name] 原样显示。
     */
    data class Custom(val name: String) : RemoteKey
}
