package io.github.laozhudu.linkplay.console


/**
 * 三个一级页面（底部导航），对应通用播放器的「设备 / 播放 / 媒体库」。
 *
 * 为什么改成底部导航：原来所有页面靠「返回上一级」串起来，从播放页去看 NAS 要
 * 「进 → 出 → 进」，返回键还得数点几次。底部导航是一级入口，切换不需要任何返回操作
 * —— 这也是市面播放器（Spotify / Apple Music / Auxio / Gramophone）的共识结构。
 */
internal enum class Tab(val label: String, val glyph: String) {
    Devices("设备", "◍"),
    Player("播放", "▮▮"),
    Sources("媒体库", "≡"),
}

/** 二级页面：从一级页推进去，返回即回一级。 */
internal enum class SubPage { Nas, LocalQueue, Library, Radio, Settings, Provisioning, DeviceContent, Online, Wps, Diagnostics }
