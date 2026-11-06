package io.github.laozhudu.linkplay.console.remote

import io.github.laozhudu.linkplay.console.ir.IrBlaster
import io.github.laozhudu.linkplay.console.ir.NecEncoder

/**
 * 红外遥控实现（NEC 编码）。
 *
 * ## 现在的码表是「示例」，不是码库
 *
 * `docs/81` 核过：`Lucaslhm/Flipper-IRDB` 是 CC0、30 万条，但**没接**。
 * 现在内置的只有一条探测码（NEC 地址 `0x04` / 命令 `0x08`），
 * 那是用来"试发一下看有没有反应"的，**不保证能控你家电视**。
 *
 * 所以 [keys] 现在只返回 [RemoteKey.Power]，界面就只画一个电源键 ——
 * **这是当前的真实能力，不是设计偷懒。**
 *
 * 接码库时只要换掉 `codebook`，界面和 `RemoteControl` 接口都不用动。
 *
 * ## 为什么依赖是三个 lambda 而不是直接 new IrBlaster
 *
 * `IrBlaster` 要 Android 的 `ConsumerIrManager`。直接依赖它，这个类就**没法跑纯 JVM 单测** ——
 * 而"没有发射器时 keys() 必须为空"这种规则恰恰最该被测试锁住（它决定界面画什么）。
 */
class IrRemoteControl(
    private val hasEmitter: () -> Boolean,
    /** 发一串时序。返回 null = 已发出（**不是"成功"**），否则是给人看的原因。 */
    private val transmit: (IntArray) -> String?,
    /** 键 → NEC 命令码。地址固定 `0x04`（示例码）。 */
    private val codebook: Map<RemoteKey, Int> = DEFAULT_CODEBOOK,
    /** 记诊断（`IrStatus.attempted`）。 */
    private val onAttempt: (ok: Boolean, note: String?) -> Unit = { _, _ -> },
) : RemoteControl {

    override val label: String get() = "红外"

    override val kind: RemoteControl.Kind get() = RemoteControl.Kind.Ir

    override fun keys(): Set<RemoteKey> =
        // 没有发射器时**一个键都不给** —— 界面据此整块不显示，
        // 而不是画一个按了没反应的遥控器。
        if (hasEmitter()) codebook.keys else emptySet()

    override suspend fun send(key: RemoteKey): RemoteResult {
        if (!hasEmitter()) return RemoteResult.Failed("这台手机没有红外发射器")
        val command = codebook[key] ?: return RemoteResult.Unsupported
        val pattern = NecEncoder.encode(NEC_ADDRESS, command, repeats = 2)
        val error = transmit(pattern)
        onAttempt(error == null, error)
        return if (error == null) RemoteResult.Sent else RemoteResult.Failed(error)
    }

    override fun diagnoseLine(): String = buildString {
        append("红外=")
        append(if (hasEmitter()) "有发射器" else "无发射器")
        append("  可发键=").append(keys().size)
        append("  码表=示例（").append(codebook.size).append(" 键，未接码库）")
        append("  回执=无（红外只有发送）")
    }

    companion object {
        /** NEC 地址。多数字库把地址写在码名里，接库后这个值要按设备换。 */
        const val NEC_ADDRESS = 0x04

        /**
         * 内置示例码表。
         *
         * **这不是码库。** 只有一条探测用的电源码，用途是"试一下有没有反应"。
         * 接 `Flipper-IRDB` 之后这个常量会被替换成按设备查表的实现。
         */
        val DEFAULT_CODEBOOK: Map<RemoteKey, Int> = mapOf(
            RemoteKey.Power to 0x08,
        )

        /** Android 环境下的工厂（唯一碰 `IrBlaster` 的地方）。 */
        fun from(context: android.content.Context): IrRemoteControl {
            val blaster = IrBlaster(context)
            return IrRemoteControl(
                hasEmitter = { blaster.hasEmitter },
                transmit = { pattern -> blaster.transmit(pattern) },
                onAttempt = { ok, note ->
                    io.github.laozhudu.linkplay.console.ir.IrStatus.attempted(ok, note)
                },
            )
        }
    }
}
