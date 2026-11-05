package io.github.laozhudu.linkplay.console.ir

/**
 * NEC 红外协议的编码器 —— 把「地址 + 命令」变成 `ConsumerIrManager.transmit` 要的时长数组。
 *
 * ## 为什么先只做 NEC
 *
 * 红外有十几种协议（NEC / RC5 / RC6 / SIRC / Samsung / Panasonic…），
 * Flipper-IRDB 里最常见的是 NEC 与 NECext。
 * 这一版的目的**不是**做万能遥控，而是回答一个前置问题：
 * **我这台手机到底有没有红外发射器、发出去有没有反应。**
 * 一条 NEC 电源码就够了。
 *
 * ## NEC 帧结构（记在这里，省得每次都查）
 *
 * ```
 * 头：  mark 9000µs + space 4500µs
 * 位：  mark 560µs + space（0 是 560µs，1 是 1690µs）   共 32 位，LSB 先出
 * 尾：  mark 560µs + space 40000µs（帧间隔）
 * ```
 *
 * 32 位 = 地址 + 反地址 + 命令 + 反命令。反码不是冗余：接收端据此判断收到的是有效帧。
 *
 * Android 的 `transmit` 数组语义：**偶数下标是载波 mark，奇数是静默 space**，
 * 单位微秒。所以数组必须以 space 结尾 —— 这是最容易写错的一处。
 */
object NecEncoder {

    /** NEC 固定 38kHz 载波。 */
    const val CARRIER_HZ = 38_000

    private const val HEAD_MARK = 9_000
    private const val HEAD_SPACE = 4_500
    private const val BIT_MARK = 560
    private const val ZERO_SPACE = 560
    private const val ONE_SPACE = 1_690
    /** 帧间隔。少了它，连发时第二帧会被接收端当成第一帧的一部分。 */
    private const val FRAME_GAP = 40_000

    /**
     * 编一帧。
     *
     * @param address 8 位地址（超出范围按低 8 位取）
     * @param command 8 位命令
     * @param repeats 重复几次（≥1）。每次之间插入帧间隔。
     */
    fun encode(address: Int, command: Int, repeats: Int = 1): IntArray {
        val times = (repeats.coerceAtLeast(1))
        val out = ArrayList<Int>(BUFFER_SIZE * times)
        repeat(times) {
            out += HEAD_MARK
            out += HEAD_SPACE
            val bytes = intArrayOf(
                address and 0xFF,
                address.inv() and 0xFF,
                command and 0xFF,
                command.inv() and 0xFF,
            )
            bytes.forEach { byte ->
                for (bit in 0 until 8) {
                    out += BIT_MARK
                    out += if ((byte shr bit) and 1 == 1) ONE_SPACE else ZERO_SPACE
                }
            }
            // 帧尾：一个 mark + 长间隔。必须以 space 结尾。
            out += BIT_MARK
            out += FRAME_GAP
        }
        return out.toIntArray()
    }

    /** 一帧的数组长度（2 头 + 32 位 × 2 + 尾 2）。 */
    const val BUFFER_SIZE = 2 + 32 * 2 + 2

    /** 探测用的最小码：LG 电视电源（地址 0x04 命令 0x08 是最常见的一组）。 */
    fun probePower(): IntArray = encode(address = 0x04, command = 0x08, repeats = 2)
}
