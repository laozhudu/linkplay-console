package io.github.laozhudu.linkplay.protocol

/**
 * 固件升级的三段状态（原厂 `pagesdevconfig/DeviceUpgradeActivity`）。
 *
 * 原厂流程是四条命令：
 * ```
 * getMvRemoteUpdateStart    → 让设备去服务器查（无返回体，回 OK）
 * getMvRemoteUpdateStatus   → 0 = 没有新版本 / 其它值 = 有可用版本（真机见过 22）
 * getMvRomDownloadStatus    → {"status","progress","name","ver","new_ver","tsize","dsize",…}
 * getMvRomBurnPrecent       → {"status","progress"}   烧写进度
 * ```
 *
 * 真机原文（`.164`，2026-11-03）：
 * ```
 * getMvRemoteUpdateStatus → 0
 * getMvRomDownloadStatus  → {"status":"0","count":"0","progress":"0","name":"","ver":"0","new_ver":"0",…}
 * getMvRomBurnPrecent     → {"status":"0","progress":"0"}
 * ```
 *
 * 这里只做**解析与阶段判定**，纯 JVM 可测；网络那一层在 `ConsoleViewModel`。
 */
data class FirmwareUpdate(
    /** 有没有新版本（`getMvRemoteUpdateStatus` 非 0 即视为有）。 */
    val hasUpdate: Boolean = false,
    /** 设备自报的原始状态值，界面上如实显示，别自己编。 */
    val statusRaw: Int? = null,
    /** 当前版本 / 新版本（来自 download status，可能为空）。 */
    val currentVersion: String? = null,
    val newVersion: String? = null,
    val romName: String? = null,
    /** 下载进度 0..100。 */
    val downloadPercent: Int = 0,
    /** 烧写进度 0..100。 */
    val burnPercent: Int = 0,
    /** 下载状态原始码：0 = 空闲/完成（真机见到），其它值见设备文档。 */
    val downloadStatus: Int? = null,
    val burnStatus: Int? = null,
) {
    /**
     * 一句话阶段描述。
     *
     * 判定顺序按原厂的语义：先看有没有新版本，再看是在下载还是在烧写。
     * 两个 `status` 都为 0 且进度为 0 时不假装"正在升级" —— 真机上这就是"什么都没发生"。
     */
    val phase: String
        get() = when {
            !hasUpdate && downloadPercent == 0 && burnPercent == 0 -> "没有可用更新"
            burnPercent in 1..99 -> "正在写入固件 $burnPercent%"
            burnPercent >= 100 -> "固件写入完成"
            downloadPercent in 1..99 -> "正在下载固件 $downloadPercent%"
            downloadPercent >= 100 -> "下载完成，等待写入"
            hasUpdate -> "发现新版本，等待开始下载"
            else -> "状态未知（设备未报告进度）"
        }

    /** 是否可以开始升级：有新版本且还没在下载/烧写。 */
    val canStart: Boolean
        get() = hasUpdate && downloadPercent == 0 && burnPercent == 0

    companion object {
        fun of(
            remoteStatus: String?,
            downloadJson: String? = null,
            burnJson: String? = null,
        ): FirmwareUpdate {
            val remote = remoteStatus?.trim()?.toIntOrNull()
            val dl = downloadJson?.let { runCatching { Json.parse(it) }.getOrNull() } as? JsonValue.Obj
            val burn = burnJson?.let { runCatching { Json.parse(it) }.getOrNull() } as? JsonValue.Obj
            fun JsonValue.Obj?.str(k: String): String? = this?.string(k)?.takeIf { it.isNotBlank() && it != "0" }
            fun JsonValue.Obj?.num(k: String): Int? =
                this?.string(k)?.toIntOrNull() ?: this?.int(k)
            return FirmwareUpdate(
                hasUpdate = remote != null && remote != 0,
                statusRaw = remote,
                currentVersion = dl.str("ver")?.takeIf { it != "0" },
                newVersion = dl.str("new_ver"),
                romName = dl.str("name"),
                downloadPercent = (dl.num("progress") ?: 0).coerceIn(0, 100),
                burnPercent = (burn.num("progress") ?: 0).coerceIn(0, 100),
                downloadStatus = dl.num("status"),
                burnStatus = burn.num("status"),
            )
        }
    }
}
