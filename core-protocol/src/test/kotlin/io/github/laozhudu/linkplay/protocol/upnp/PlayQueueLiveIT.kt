package io.github.laozhudu.linkplay.protocol.upnp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 真机闭环：把整份列表交给设备 → 起播 → 读回。
 *
 * 两条上一次没验证的事在这里一次做实：
 *
 * 1. 歌单/整列表下发（`CreateQueue` → `PlayQueueWithIndex` → `BrowseQueue` 读回）；
 * 2. 空设备点播放回 `704 play not allowed` —— 证明界面必须翻译成人话，
 *    而不是把 `SOAP 500 for "...AVTransport:1#Play"` 摊给用户。
 *
 * 需要显式给环境变量才会跑（默认跳过；CI 上没有设备）：
 *
 * ```
 * LIVE_DEVICE=192.168.99.164 LIVE_DEVICE_EMPTY=192.168.99.164 \
 * LIVE_LIST_URLS=http://192.168.99.223:8200/MediaItems/170.mp3 \
 * ./gradlew :core-protocol:test --tests "*PlayQueueLiveIT*"
 * ```
 *
 * 会覆盖该设备当前的播放队列，只在设备没在放你想听的内容时执行。
 */
class PlayQueueLiveIT {

    private fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }

    @Test
    fun `push a list, play it, read it back`() {
        val ip = env("LIVE_DEVICE") ?: return
        val urls = env("LIVE_LIST_URLS")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
        assumeTrue("未提供 LIVE_LIST_URLS，跳过真机用例", !urls.isNullOrEmpty())

        val desc = UpnpRendererClient.describe("http://$ip:49152/description.xml")
        val pq = PlayQueueClient(desc, HttpUpnpTransport())
        assumeTrue("这台设备没有 PlayQueue 服务", pq.hasService())

        val items = urls!!.mapIndexed { i, u ->
            QueueTrack(url = u, title = "live-$i", artist = "it", durationSec = 5)
        }

        // CreateQueue
        val created = pq.createQueue(QueueNames.OURS, items)
        assertTrue("CreateQueue 应回成功，实际：$created", created.isNotBlank())

        // PlayQueueWithIndex（Index 是 1 基，内部已 +1）
        val played = pq.playWithIndex(QueueNames.OURS, 0)
        assertTrue("PlayQueueWithIndex 应回成功，实际：$played", played.isNotBlank())

        // 设备侧状态必须与列表条数一致
        val (count, cur) = readStatus(ip)
        assertEquals("设备侧 plicount 应等于下发条数", items.size, count)
        assertTrue("设备侧应在播（plicurr=$cur）", cur >= 1)

        // BrowseQueue 读回条数
        val back = pq.browseQueue(QueueNames.OURS)
        assertNotNull("BrowseQueue 应能读回列表", back)
        assertEquals("读回的条数应与下发一致", items.size, back!!.tracks.size)
    }

    @Test
    fun `play on an empty renderer returns 704 which we translate`() {
        val ip = env("LIVE_DEVICE_EMPTY") ?: return
        val client = UpnpRendererClient(UpnpRendererClient.describe("http://$ip:49152/description.xml"))
        val fault = runCatching { client.play() }.exceptionOrNull() as? SoapFaultException
        // 有的设备允许空播（回 200），那就没什么可断言的
        assumeTrue("这台设备允许空播，跳过", fault != null)
        assertEquals("UPnP 标准里 704 就是 play not allowed", 704, fault!!.upnpCode)
        val text = UpnpFault.friendlyOf(fault)
        assertTrue("文案要指向选歌，不是报故障：$text", text.contains("没有加载媒体"))
    }

    /** 读 `getPlayerStatus` 里的 `plicount` / `plicurr`。 */
    private fun readStatus(ip: String): Pair<Int, Int> {
        val text = HttpUpnpTransport().get("http://$ip/httpapi.asp?command=getPlayerStatus")
        val count = Regex("\"plicount\":\"?(\\d+)").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val cur = Regex("\"plicurr\":\"?(\\d+)").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        return count to cur
    }
}
