package io.github.laozhudu.linkplay.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * SSDP 多次应答的合并 —— **防的是"扫到了又被自己扔掉"**。
 *
 * ## 这份测试在挡什么（真机现象）
 *
 * 用户：「通用 upnp 呢？怎么没有扫出来」。
 *
 * `ssdp:all` 会让一台设备把 rootdevice / 设备类型 / 每个服务类型各答一遍。
 * 这些应答里**有的带 LOCATION、有的不带**。原来代码是 `found[ip] = 新应答`，
 * 于是一条不带 LOCATION 的应答就能把前面带了 LOCATION 的那条覆盖掉，
 * 后面 `filter { location != null }` 一过，**这台设备就消失了**。
 *
 * 从用户视角看是"这台设备扫不出来"；从代码视角看是"扫到了，然后自己删了"。
 * 这种 bug 没有任何日志，只能靠知道这个坑才能防住。
 */
class SsdpMergeAnswerTest {

    private val discovery = SsdpDiscovery()

    private fun device(location: String?, st: String? = null) = DiscoveredDevice(
        ip = "192.168.99.175",
        location = location,
        usn = "uuid:81adb0b2::urn:schemas-upnp-org:device:MediaRenderer:1",
        server = "Linux/3.x UPnP/1.0 AirReceiver/1.0",
        searchTarget = st,
    )

    @Test
    fun `不带 LOCATION 的应答不会抹掉已有的 LOCATION`() {
        val withLocation = device("http://192.168.99.175:3500/", "urn:schemas-upnp-org:device:MediaRenderer:1")

        // 后一条是 rootdevice 的应答，某些固件这条不带 LOCATION
        val merged = discovery.mergeAnswer(
            previous = withLocation,
            ip = "192.168.99.175",
            location = null,
            usn = "uuid:81adb0b2::upnp:rootdevice",
            server = null,
            searchTarget = "upnp:rootdevice",
        )

        assertNotNull("LOCATION 必须还在 —— 丢了这台设备就从结果里消失了", merged.location)
        assertEquals("http://192.168.99.175:3500/", merged.location)
        // ST 也不该把"更像设备"的那个盖掉
        assertEquals("urn:schemas-upnp-org:device:MediaRenderer:1", merged.searchTarget)
    }

    @Test
    fun `带 LOCATION 的后到应答可以覆盖`() {
        val first = device(null, "upnp:rootdevice")

        val merged = discovery.mergeAnswer(
            previous = first,
            ip = "192.168.99.175",
            location = "http://192.168.99.175:3500/",
            usn = "uuid:81adb0b2::urn:schemas-upnp-org:device:MediaRenderer:1",
            server = "AirReceiver",
            searchTarget = "urn:schemas-upnp-org:device:MediaRenderer:1",
        )

        assertEquals("http://192.168.99.175:3500/", merged.location)
        assertEquals("urn:schemas-upnp-org:device:MediaRenderer:1", merged.searchTarget)
    }

    @Test
    fun `第一次应答就带 LOCATION 时直接收下`() {
        val merged = discovery.mergeAnswer(
            previous = null,
            ip = "192.168.99.175",
            location = "http://192.168.99.175:3500/",
            usn = "uuid:81adb0b2",
            server = "AirReceiver",
            searchTarget = "urn:schemas-upnp-org:device:MediaRenderer:1",
        )

        assertEquals("http://192.168.99.175:3500/", merged.location)
    }
}
