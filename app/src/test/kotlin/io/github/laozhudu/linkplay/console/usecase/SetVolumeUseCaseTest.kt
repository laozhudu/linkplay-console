package io.github.laozhudu.linkplay.console.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 音量唯一入口的三条规则。
 *
 * 锁的是**顺序和通道选择** —— 这两件事写错了，表现就是
 * "界面显示 60、设备实际 40"（真机撞过一次）。
 */
class SetVolumeUseCaseTest {

    private class Recorder {
        val events = mutableListOf<String>()
        fun usecase() = SetVolumeUseCase(
            publish = { ip, v -> events += "publish:$ip:$v" },
            sendMerged = { ip, v -> events += "merged:$ip:$v" },
            sendNow = { ip, v -> events += "now:$ip:$v" },
        )
    }

    @Test
    fun `drag publishes first then uses the merged channel`() {
        val r = Recorder()
        r.usecase().duringDrag("10.0.0.1", 47)
        // 顺序要紧：先写 VolumeBus 界面才跟手，再下发
        assertEquals(listOf("publish:10.0.0.1:47", "merged:10.0.0.1:47"), r.events)
    }

    @Test
    fun `discrete publishes first then sends immediately`() {
        val r = Recorder()
        r.usecase().discrete("10.0.0.1", 52)
        assertEquals(listOf("publish:10.0.0.1:52", "now:10.0.0.1:52"), r.events)
    }

    @Test
    fun `drag never uses the immediate channel`() {
        // 拖动如果走直发，一秒几十个 HTTP 会把老固件压垮
        val r = Recorder()
        r.usecase().duringDrag("10.0.0.1", 30)
        assertEquals(0, r.events.count { it.startsWith("now:") })
    }

    @Test
    fun `discrete never uses the merged channel`() {
        // 按键如果走合并，会多等 60ms —— 按下去像卡了一下
        val r = Recorder()
        r.usecase().discrete("10.0.0.1", 30)
        assertEquals(0, r.events.count { it.startsWith("merged:") })
    }

    @Test
    fun `out of range values are clamped`() {
        val r = Recorder()
        r.usecase().duringDrag("ip", 140)
        r.usecase().discrete("ip", -20)
        assertEquals(
            listOf("publish:ip:100", "merged:ip:100", "publish:ip:0", "now:ip:0"),
            r.events,
        )
    }

    @Test
    fun `display sync never sends anything`() {
        // 读到设备新音量时只更新界面 —— 绝不能再发一条命令回去
        val r = Recorder()
        r.usecase().syncDisplayOnly("ip", 66)
        assertEquals(listOf("publish:ip:66"), r.events)
    }
}
