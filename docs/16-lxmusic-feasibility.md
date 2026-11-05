# 洛雪（LX Music）能不能搬过来接入 — 结论与实测

## 一句话结论

**UI 搬不了；但它的「自定义音源宿主」是真的能搬**（Apache-2.0 + 443 行 Java + 594 行 JS）。
搬完还差一道坎：**音箱是裸 GET 拉流，不带 Referer/Cookie**，所以音源直链必须过一层代理转发。

## 1. 核实过的事实（不是推断）

| 项目 | 事实 | 来源 |
|---|---|---|
| 许可证 | `lx-music-mobile` / `lx-music-desktop` 都是 **Apache-2.0**（+ 项目补充协议） | GitHub API `license.spdx_id` |
| 移动端技术栈 | **React Native**：TypeScript 70% / JavaScript 23% / Java 5% | GitHub `languages` |
| 桌面端 | Electron + **Vue** 28% | 同上 |
| 有没有 DLNA / 投屏 | **没有**。751 个文件里 `dlna` / `cast` / `upnp` / `airplay` 命中 0 | `git/trees?recursive=1` |
| 音源宿主 | Android 端 = **QuickJS**（`wang.harlon.quickjs:wrapper-android:2.4.0`）+ 预加载 shim | `android/app/build.gradle`、`assets/script/user-api-preload.js` |
| 宿主代码量 | `QuickJS.java` 220 行、`JavaScriptThread.java` 70、`JsHandler.java` 64、`UserApiModule.java` 89 = **443 行** + `user-api-preload.js` **594 行** | 逐文件统计 |
| 官方对音源的态度 | README 原文：「本项目本身没有获取某个音频数据的能力，本项目使用的在线音频数据来源来自软件设置内『自定义源』设置所选择的『源』返回的在线链接」 | mobile README |

「没有 DLNA」这一条决定了：**洛雪自己永远投不到我们的音箱上**，它只能手机/蓝牙出声。

## 2. 真机实测：音箱拉流时发什么头

起了一个会打印完整请求头的本地服务，投流给 `192.168.99.153`，抓到的原文：

```
GET /tone1.mp3
Host: 192.168.99.226:8765
User-Agent: curl/7.36.0
Icy-MetaData: 1
Connection: close
```

失败重试时还会换一套身份：

```
GET /gated.mp3
Accept: */*
User-Agent: NSPlayer/4.1.0.3856
Host: 192.168.99.226:8765
Pragma: xClientGUID={c77e7400-...}
Pragma: no-cache,rate=1.000000,...
Connection: Close
```

**没有 `Referer`，没有 `Cookie`，没有任何自定义头。**

再做一个「必须带 Referer 才回 200」的链接（`/gated.mp3`）：

```
设备请求 → 403 → getPlayerStatus 立刻变成 status=stop，播放失败
```

### 这条实测意味着什么

洛雪音源脚本返回的链接常见两类：

| 链接类型 | 能不能直投 |
|---|---|
| 裸 CDN 直链（不校验来源） | ✅ 能，`setPlayerCmd:play:<url>` 直接播 |
| 需要 `Referer` / `Cookie` / 特定 UA 的（大多数平台音源） | ❌ 必然 403 |
| m3u8 / 分片 HLS | ~~❌ 设备端 curl 7.36 不解析播放列表~~ **⚠️ 已推翻**：音箱能播 m3u8 —— `docs/19` 实测 CNR 两台的 `index.m3u8` 均 `play` 且 `curpos` 递增，`docs/42` 也确认「音箱能播 m3u8」。当时那次失败是个别主机/协议问题，不是设备能力问题 |

所以「洛雪音源 → 音箱」**必须中间有一层代理**：我们替设备补头拉流，再把裸字节以 Range 响应吐给设备。

## 3. 三个可选的落点

| 方案 | 做什么 | 工作量 | 值不值 |
|---|---|---|---|
| **A. 代理转发**（推荐先做） | 手机/NAS 上跑一个「补头 + 透传 Range」的小服务；输入任意受保护直链 → 输出设备能拉的本地 URL | **约 1 天**（`LocalMediaServer` 的 Range 骨架已写好，只加上游拉流） | 高。不依赖洛雪，任何链接都能投；把「投流」能力从「只能播裸直链」扩展成「什么都能播」 |
| **B. 搬洛雪音源宿主** | 接 `wang.harlon.quickjs`，移植 443 行 Java 宿主 + 594 行 preload.js，跑用户的音源脚本，拿 URL | 约 3 天（含脚本兼容性调试） | 中。技术上完全可行、许可证允许（Apache-2.0，保留版权声明即可），但要长期跟着社区脚本更新 |
| **C. 直接用洛雪 App** | 手机装洛雪，蓝牙放给音箱 | 0 | 低。没有 DLNA，音质与操作都差一档；只适合「偶尔听听」 |

A 与 B 不冲突：A 是投流的底座，B 是在 A 之上加「音源自带搜索」。

## 4. 合规边界（沿用前面几轮的判断，未松动）

- **搬宿主代码 ≠ 内置音源**。洛雪官方本身不提供音源，音源是用户自己导入的脚本。
  我们移植「脚本宿主」在许可证和设计上都干净；真正灰色的是**第三方音源脚本**。
- 所以做法是：**宿主由我们做，脚本由你导入**，仓库里不放任何音源脚本、不放任何平台凭据。
- 洛雪的 `client_secret` / 平台 cookie 一律不碰（换了包名签名也必然失效）。

## 4.5 A 已落地：`RelayMediaServer`（真机验证通过）

`core-protocol/media/RelayMediaServer.kt` —— 把「设备拉不了的上游」变成「设备能拉的本机地址」。

真机联调（`RelayLiveIT`，日志来自实际运行）：

```
① 设备直连防盗链链接 → status 不是 play（播不了，符合预期）
② 登记到转发服务 → 设备改为拉 http://192.168.99.226:34549/s/2aba25f5da3af445.mp3
   → status=play，curpos 678 → 2512 递增，totlen=6000
   → 上游侧确认收到了带 Referer 的请求
```

（`uri` 字段 HEX 解码正是那条 `/s/...mp3`，说明设备确实在拉我们的转发地址。）

四条设计约束，都有它防的具体问题：

| 约束 | 防的是什么 |
|---|---|
| **不是开放代理**：只服务 `publish()` 登记过的 token，token 随机 + TTL | 否则局域网里任何设备都能借它访问任意地址，等于把内网当跳板 |
| **上游忽略 Range 时自己切片并回 206** | 很多 CDN 对 Range 仍回 200 全长；直接透传会让设备拖进度条时从头播 |
| **每个连接 `Connection: close`** | 嵌入式设备不复用连接，长连接反而挂住（与 `LocalMediaServer` 同一结论） |
| **上游非 2xx 回 502 并写明「多半是防盗链」** | 否则用户只看到「播不了」，不知道是缺 Referer |

App 侧：播放页「投流地址」区新增「这条链接需要 Referer（自动转发）」开关 + Referer 输入框。
勾选后 `cast()` 会起转发服务、把本机地址给音箱；`onCleared` 时释放端口。

## 5. 建议的下一步

1. 先做 **A（代理转发）**，1 天，立刻让「任何直链都能投给音箱」成立；
2. 若你确实要「在 App 里搜歌 → 投给音箱」，再做 **B**，工程量主要花在 JS 宿主与新编译产物的联调；
3. **UI 不搬洛雪**（React Native，与我方 Compose 不兼容），继续用 Rhythm 那套视觉。

## 6. 附：本文件的技术来源

- `api.github.com/repos/lyswhut/lx-music-mobile`（license / languages）
- `api.github.com/repos/lyswhut/lx-music-mobile/git/trees/master?recursive=1`（751 文件全量路径，用于确认无 cast/dlna）
- `raw.githubusercontent.com/.../android/app/build.gradle`（QuickJS 依赖）
- 真机：`192.168.99.153` 对 `192.168.99.226:8765` 的拉流请求头，日志见本轮探测
