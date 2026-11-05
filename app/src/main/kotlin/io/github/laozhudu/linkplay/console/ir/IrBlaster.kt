package io.github.laozhudu.linkplay.console.ir

import android.content.Context
import android.hardware.ConsumerIrManager

/**
 * 红外发射 —— 直接包 `ConsumerIrManager`。
 *
 * ## 三个必须记住的事实（`docs/81` 已核实）
 *
 * 1. **手机不能学码**。AOSP 没有公开的红外接收 API，`ConsumerIrManager` 只有发送。
 *    所以"录码"不成立，码库是唯一路径。
 * 2. `TRANSMIT_IR` 是**普通权限**，装上就有，不需要运行时授权。
 * 3. 没有发射器时 `hasIrEmitter()` 返回 false —— 这种情况下**界面不该出现红外入口**。
 *
 * ## 为什么这里所有方法都"不抛"
 *
 * 红外发射失败在系统层可能是 `IllegalArgumentException`（载波不支持）、
 * `SecurityException`（权限剥离）、`RuntimeException`（驱动异常）。
 * 这个功能本身没有回执（设备响不响应只能靠人眼看），
 * 再抛异常给界面只会变成"点了闪退"，那比没反应更糟。
 */
class IrBlaster(context: Context) {

    private val manager: ConsumerIrManager? =
        context.applicationContext.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

    /** 这台设备有没有红外发射器。取不到系统服务时视为没有。 */
    val hasEmitter: Boolean
        get() = runCatching { manager?.hasIrEmitter() == true }.getOrDefault(false)

    /** 支持的载波频率（Hz）。很多机型只支持一个窄区间。 */
    val carrierFrequencies: List<Int>
        get() = runCatching {
            manager?.carrierFrequencies?.flatMap { listOf(it.minFrequency, it.maxFrequency) }.orEmpty()
        }.getOrDefault(emptyList())

    /** 发一串时序。返回 null 表示成功，否则是给人看的原因。 */
    fun transmit(pattern: IntArray, carrierHz: Int = NecEncoder.CARRIER_HZ): String? {
        val m = manager ?: return "这台设备没有红外服务"
        if (!hasEmitter) return "这台手机没有红外发射器"
        return runCatching {
            m.transmit(carrierHz, pattern)
            null
        }.getOrElse { error ->
            when (error) {
                is IllegalArgumentException -> "载波 ${carrierHz}Hz 不被支持（${error.message}）"
                is SecurityException -> "没有红外权限（${error.message}）"
                else -> "${error.javaClass.simpleName}: ${error.message}"
            }
        }
    }
}
