package io.github.laozhudu.linkplay.protocol.snapshot

import io.github.laozhudu.linkplay.protocol.DeviceHttpClient
import io.github.laozhudu.linkplay.protocol.upnp.UpnpRendererClient
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 真机闭环：最短路径到底几次请求、电量能不能拿到。
 *
 * 用法（不需要真机时自动跳过）：
 * ```
 * LIVE_DEVICES=192.168.99.153,192.168.99.164 \
 *   ./gradlew :core-protocol:test --tests '*DeviceSnapshotLiveIT*' -i
 * ```
 *
 * 为什么要这个 IT：单测只能证明"解析对"，证明不了"设备真的这么答"。
 * 这个项目反复吃过"命令回 OK 但功能没生效"的亏，所以每次改取数路径都要跑一遍真机。
 */
class DeviceSnapshotLiveIT {

    private class CountingHttp : DeviceHttpClient() {
        val asked = ArrayList<String>()
        override fun get(deviceIp: String, command: String, path: String, readTimeoutMs: Int): String {
            asked.add(command)
            return super.get(deviceIp, command, path, readTimeoutMs)
        }
    }

    @Test
    fun `two requests give battery channel and playback state`() {
        val ips = (System.getenv("LIVE_DEVICES") ?: "")
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        assumeTrue("未设置 LIVE_DEVICES，跳过真机用例", ips.isNotEmpty())

        for (ip in ips) {
            val http = CountingHttp()
            val reader = DeviceSnapshotReader(
                http = http,
                controlInfo = { target ->
                    runCatching { UpnpRendererClient.ofDevice(target).controlDeviceInfo() }.getOrNull()
                },
            )

            val snap = reader.read(ip)
            assertNotNull("$ip 无响应", snap)
            snap!!

            println("[$ip] 通道=${snap.answered} 请求=${http.asked}")
            println("[$ip] 电量=${snap.batteryPercent} 充电态=${snap.onBattery} 声道=${snap.channel} 音量=${snap.volume} 名字=${snap.deviceName}")

            // 电量必须拿到 —— 这是用户报的问题，缺了就是没修好
            assertNotNull("$ip 没拿到电量（answered=${snap.answered}）", snap.batteryPercent)
            // 最短路径：最多 3 次（CONTROL_INFO 成功时只应有 getPlayerStatus 一次 httpapi 请求）
            assertTrue("$ip 请求过多：${http.asked}", http.asked.size <= 2)
            assertTrue("$ip 没走主路径", DeviceSnapshot.Channel.CONTROL_INFO in snap.answered)
        }
    }
}
