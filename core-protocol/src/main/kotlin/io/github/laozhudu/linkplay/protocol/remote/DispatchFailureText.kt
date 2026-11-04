package io.github.laozhudu.linkplay.protocol.remote

/**
 * 把 [DispatchFailure] 翻译成**用户能看懂的一句话**。
 *
 * ## 为什么必须在协议层翻译，而不是界面层
 *
 * 界面拿到的只有 `DispatchFailure` 这个 sealed 类型。如果让它自己写文案，
 * 五个界面就会有五套说法，而且新增一种失败必然有地方漏掉（`when` 不写 else 会编译报错，
 * 但很多人会图省事写 else -> "失败"）。
 *
 * 放在这里还有一层理由：**错误码的含义是协议知识**。
 * `704` 对 LinkPlay 是"设备里没有媒体"，对 DLNA 是 `Transition not available` ——
 * 界面不该知道这件事（docs/40 的 `SoapFault` 翻译层同理）。
 *
 * ## 文案原则
 *
 * 1. **说人话，不说协议**：不出现 `704`、`IOException`、`Unparsable`
 * 2. **给出下一步**：不只说"失败"，要说"该怎么办"（先去点一首 / 检查网络）
 * 3. **不假装成功**：宁可说"没响应"，也不说"已发送"
 */
fun DispatchFailure.describe(): String = when (this) {
    DispatchFailure.Unreachable ->
        "设备没有响应（可能休眠或不在同一网络）"

    DispatchFailure.Unsupported ->
        "这台设备不支持该操作"

    is DispatchFailure.Rejected -> when (code) {
        // LinkPlay 的 704：设备手里没有媒体，需要先给它一首歌/一个列表
        704 -> "设备里没有可播放的内容，先去「我的音乐」点一首"
        // LinkPlay 的 401：动作不在该固件的白名单里（实测 getAlarmList 等老固件不支持）
        401 -> "这台设备的固件不支持该操作"
        0 -> text.ifBlank { "设备拒绝了该操作" }
        else -> "设备拒绝了该操作（$code）"
    }

    is DispatchFailure.Unparsable ->
        "设备回包无法识别，可能是固件版本差异"
}

/** 成功时的日志文本。区分"对端确认"和未来的"单向发出"。 */
fun DispatchResult.describeForLog(): String = when (this) {
    is DispatchResult.Acked -> "OK"
    is DispatchResult.Failed -> "失败：" + failure.describe()
}
