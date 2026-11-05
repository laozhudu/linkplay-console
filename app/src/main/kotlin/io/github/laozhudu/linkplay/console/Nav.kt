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

    /**
     * 洛雪音乐 —— 一级页面，不藏在"媒体库 → 其它入口"里。
     *
     * 理由就是用户的抱怨：搜一首歌要"进媒体库 → 其它入口 → 洛雪 → 搜 → 点"，
     * 五次操作。一级入口之后是"点洛雪 → 搜 → 点"，而且搜完还能直接切播放页。
     */
    Lx("洛雪", "♪"),

    Sources("媒体库", "≡"),
}

/** 二级页面：从一级页推进去，返回即回一级。 */
internal enum class SubPage {
    Nas, LocalQueue, Library, Radio, Settings, Provisioning, DeviceContent, Online, Wps, Diagnostics,

    /** 洛雪音源脚本（装了什么 / 哪支在用 / 导入新的）。 */
    Scripts,

    /** 洛雪音乐搜索（搜一首 → 取直链 → 投到音箱）。 */
    LxSearch,

    /** 统一入口：一个输入框同时问四个源。 */
    SearchHub,
}
