# 架构与重写路线

## 模块边界

```
app/             Compose UI + ViewModel，只做「状态 → 屏幕」和「事件 → 用例」
core-discovery/  SSDP 发现（Android library：需要 MulticastLock，但库内不持 Context）
core-protocol/   协议客户端（纯 JVM：命令拼装、JSON 解析、状态模型、HTTP 传输）
```

依赖方向单向：`app → core-discovery → core-protocol`，`core-protocol` 不依赖 Android。

这样切的好处：协议层可以用纯 JUnit 覆盖（当前 22 个用例，秒级），
上真机、进 CI、改成 AAR 给别人用都不需要改代码。

## 为什么 core-protocol 是纯 JVM

- 命令拼装与字段解析是纯字符串/字节逻辑，**不需要 Android**；
- 纯 JVM 模块的测试不用 Robolectric，CI 里 100% 可跑；
- 未来想给自动化脚本（Kotlin/Java/Kotlin Multiplatform）复用，不用背 Android 依赖。

## 现代技术栈取舍

| 关注点 | 选择 | 理由 |
|---|---|---|
| UI | Jetpack Compose + Material3 | 原 1478 个 view 类全部重写，Compose 最省代码 |
| 异步 | Coroutines/Flow + `StateFlow` | 无回调地狱；设备请求天然是 IO 边界 |
| HTTP | `HttpURLConnection`（当前）→ 可换 OkHttp | 设备只提供明文 HTTP，短连接足够；不引依赖以保构建轻 |
| 播放 | Media3 `MediaSessionService` + ExoPlayer | 补齐锁屏/通知/蓝牙控制（原版完全没有） |
| UPnP | jUPnP（Cling 后继） | Cling 2014 停更，且原版用的还是魔改分支 |
| 存储 | app-specific 目录 + SAF | 替代 `/sdcard/wiimu/` 直写 |
| 日志 | 内存 ring buffer（当前）→ 可接 Timber | 先保证「能看到设备原始返回」，排障核心 |

## 本轮已实现 / 未实现

已实现（可在真机验证）：
- SSDP 发现（含厂商 ST 与 `ssdp:all` 兜底）
- 手动 IP 添加
- `getStatusEx` 解析与展示、`multiroom:getSlaveList` 解析与展示
- 改名、输入源切换、从机音量 +、恢复出厂（二次确认）
- 原始命令控制台（可执行任意 `httpapi.asp?command=`）
- 22 个协议层单元测试

未实现（路线图）：
- UPnP AVTransport/RenderingControl 接入（播放、音量、进度、事件订阅）
- AP 直连配网的完整 UI 流程（协议层已备好 `ConnectMasterAp`/`wlanConnectApEx`）
- MCU 灯效通道（TCP 长连接 + 帧解析）
- 音箱固件 OTA 轮询
- Media3 本地播放与 DLNA 推流

## CI

`.github/workflows/android.yml`：JDK 17 + Gradle 8.11.1，跑
`./gradlew :core-protocol:test`（协议单测）与 `:app:assembleDebug`（真实产出 APK），
APK 作为 artifact 上传。CI 绿即代表「代码可编译 + 协议逻辑自洽」，不代表真机可用
（真机验证需要实际设备在局域网内）。
