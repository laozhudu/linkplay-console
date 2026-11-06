package io.github.laozhudu.linkplay.console.remote

/**
 * 一切「遥控设备」的统一接口。
 *
 * ## 为什么现在就要有它
 *
 * 现在红外只有一个 `NecEncoder` + 一个"试发电源键"按钮，**没有任何抽象**。
 * 蓝牙、WiFi 遥控是零代码。
 *
 * 如果直接往下做，会变成三份互不相干的东西：编码一份、发送一份、界面一份、诊断一份。
 * 每加一种遥控就再散一次（`docs/95` 扩展性矩阵第 4 行）。
 *
 * 有了这个接口：
 * - **界面只认 [RemoteControl]**，遥控页画哪些键由 [keys] 决定；
 * - 加蓝牙/WiFi 时，界面**一行不改**；
 * - 诊断行由 [diagnoseLine] 自己给，不用在诊断台里 switch 类型。
 */
interface RemoteControl {

    /** 给人看的名字（"红外" / "蓝牙" / "电视盒子 WiFi"）。 */
    val label: String

    /** 传输方式。界面据此决定要不要提示"手机需有红外发射器"这类话。 */
    val kind: Kind

    /**
     * 这台设备现在能发哪些键。
     *
     * **界面据此画遥控器，不画 [RemoteResult.Unsupported] 的键。**
     * 这比"画出来再点了没反应"诚实得多（悬浮窗踩过那个坑）。
     */
    fun keys(): Set<RemoteKey>

    suspend fun send(key: RemoteKey): RemoteResult

    /** 一行诊断（接进 `AppDiagnostics` 时不用按类型 switch）。 */
    fun diagnoseLine(): String

    enum class Kind {
        /** 红外（`ConsumerIrManager`，**只有发送没有接收**）。 */
        Ir,

        /** 蓝牙（HID / GATT）。 */
        Ble,

        /** 局域网协议（各家自己一套）。 */
        Http,
    }
}
