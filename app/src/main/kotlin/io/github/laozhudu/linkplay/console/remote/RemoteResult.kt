package io.github.laozhudu.linkplay.console.remote

/**
 * 发一个遥控键的结局。
 *
 * ## 为什么必须区分「已发出」和「成功」
 *
 * **红外没有回执。** `ConsumerIrManager.transmit()` 是 void —— 系统只保证
 * "波形交给驱动了"，不保证电视收到了、也不保证码是对的。
 *
 * 所以这里只有 [Sent]（已发出），**没有 Success**。
 * 界面文案必须说"已发送"，不能说"已打开电视"—— 那是我们不知道的事。
 *
 * 蓝牙 HID / WiFi 遥控**有**回执，那些实现可以在 Sent 之外给更确定的结论，
 * 但接口层面保持保守：**宁可说得少，不能说错。**
 */
sealed interface RemoteResult {

    /** 已发出。不等于设备收到了。 */
    data object Sent : RemoteResult

    /** 这份码表（或这台设备）没有这个键 —— 界面不该画它。 */
    data object Unsupported : RemoteResult

    /** 发不出去：没有发射器 / 载波不支持 / 驱动异常 / 没连上。 */
    data class Failed(val reason: String) : RemoteResult
}
