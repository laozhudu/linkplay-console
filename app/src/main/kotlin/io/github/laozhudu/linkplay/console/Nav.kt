package io.github.laozhudu.linkplay.console


/**
 * 三个一级页面（底部导航），对应通用播放器的「设备 / 播放 / 媒体库」。
 *
 * 为什么改成底部导航：原来所有页面靠「返回上一级」串起来，从播放页去看 NAS 要
 * 「进 → 出 → 进」，返回键还得数点几次。底部导航是一级入口，切换不需要任何返回操作
 * —— 这也是市面播放器（Spotify / Apple Music / Auxio / Gramophone）的共识结构。
 */
/**
 * 底部一级页面。
 *
 * **已按 `docs/93` 定稿改为五格**（2026-11-05）：播放 · 内容 · 设备 · 遥控 · 我。
 *
 * 改之前的四项是「设备 / 播放 / 洛雪 / 媒体库」，问题：洛雪是内容源却和媒体库并列、
 * 媒体库里挤了七件事、遥控一个入口都没有。
 *
 * 判据与逐页去向见 `docs/93-nav-redesign-options.md`；铁律见 `docs/52` 铁律四。
 * **改这里之前先读那两份，不要再凭感觉拍一遍。**
 */
internal enum class Tab(val label: String, val glyph: String) {

    /**
     * 播放 —— 定稿第 1 格。
     *
     * 刻意**偏离**大厂（Spotify / Sonos 都把播放收成常驻条）：
     * 播放页信息密度高（封面 / 进度 / 音量 / 输出口 / 播放列表），
     * 常驻条只是摘要，摘要替代不了这一页。
     */
    Player("播放", "▶"),

    /**
     * 内容 —— 定稿第 2 格。
     *
     * 大厂一致把「找内容」独立成一格（Spotify 的 Search、Sonos 的 Browse）。
     * 原来这里是「洛雪」和「媒体库」两个并列格，都属于「有什么可放」，
     * 按使用场景应该合成一格，**按源筛选而不是按模块分页**。
     */
    Content("内容", "≡"),

    /** 设备 —— 定稿第 3 格。控设备是主体，不是设置（Sonos / Google Home 同理）。 */
    Devices("设备", "◍"),

    /** 遥控 —— 定稿第 4 格。红外 / 蓝牙 / WiFi 三种遥控共用这一格。 */
    Remote("遥控", "⌁"),

    /** 我 —— 定稿第 5 格。诊断台列为第一项。 */
    Me("我", "◔"),
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
