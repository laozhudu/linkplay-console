# 36 · 定位定案：遥控器 + 自己的检索层（接 tt-music）

用户拍板选 **B**：不做成完整音乐客户端，而是「遥控器 + 检索层」——
内容由 `.124` 上的 tt-music 提供，播放仍然走我们自己的设备链路。

## 一、为什么是 B

### 现状对比（实测，不是估计）

| 维度 | TT-Music（`.124:9527`） | 我们 | 判断 |
|---|---|---|---|
| 内容 | 曲库/榜单/下载/补全，五平台自定义源 | NAS 目录 + 手机本地 + 电台 | 它有源，我们没 |
| 检索 | `search3` 全文搜 | 只有本机拼音搜 | 它有，我们没 |
| 批量操作 | 批量收藏/下载/删除 | 只有单个 | 它有 |
| 播放体验 | 均衡器/混响/歌词卡片/五套主题 | 进度/音量/循环/内嵌歌词 | 部分差距 |
| **投送到设备** | 「正在投屏到」（丢直链） | **设备侧 PlayQueue + 多房间 + 声道/屏蔽/预设/固件** | **我们强** |
| **设备私有能力** | 无（不认识 LinkPlay） | 24 条 httpapi + 私有 PlayQueue 全打通 | 我们强 |

结论：**它强在有内容，我们强在有设备**。这两层是上下关系，不是替代关系。
重写成完整客户端 = 用我们的短板去拼它的长板，还会把精力从唯一护城河上挪开。

## 二、tt-music 的实际协议形态（这轮实测）

```
容器  penhaijun/lx-music-server:latest   端口 9527（host 网络）
配置  /vol2/1000/ttmusic/config/config.js
      "subsonic.enable": true
      "subsonic.path": "/rest"
      users: tt01 / 123456
```

ping 实测：

```
GET http://192.168.99.124:9527/rest/ping.view?u=tt01&p=123456&v=1.16.1&c=linkplay&f=json
→ {"subsonic-response":{"status":"ok","version":"1.16.1","type":"lxserver","openSubsonic":true}}
```

**它是 Navidrome 分支**，洛雪那层"脚本去第三方取流"全关在 NAS 里，App 侧只面对标准协议。
这就是为什么不再需要内嵌 QuickJS —— 之前担心的「脚本 API 长期维护」成本直接消失。

当前曲库是空的（`getAlbumList2 → album: []`、`search3 → song: []`），
需要在 tt-music 自己的界面里扫/下媒体，App 才会有内容。

### 直链不需要转发

`/rest/stream.view?id=…&u=…&t=…&s=…` 凭据在 query 里，
音箱裸 GET（真机 UA `curl/7.36.0`，无 Referer）也能直接拉。
所以 Subsonic 这类源**不走** `RelayMediaServer` —— 只有防盗链的源才需要转发。

## 三、这轮落地了什么

### core-protocol（纯 JVM，零运行时依赖）

- `mediasource/MediaProvider.kt` —— 音源抽象：`search` / `resolve` / `coverUrl` / `configured`。
  加一个源 = 加一个实现类，播放链路一行不动。
- `mediasource/SubsonicClient.kt` —— `search3` / `stream.view` / `getCoverArt`；
  **token 认证**（`t=md5(password+salt)&s=salt`），明文密码不进 URL。
- `mediasource/SubsonicConfig` —— JSON 持久化；`baseUrl` 缺 `http://` 自动补
  （电台那次因为"缺协议判非法"卡过一次，不再重复）。

### app

- `playback/QueueStarter.kt` —— `startQueueOnDevice(...)`：**全 App 唯一的整单下发入口**。
  三段降级（PlayQueue → m3u → 客户端逐首投流）只此一份；`LibraryViewModel.startQueue` 已改为委托。
  这是"重开 App 列表不同步"那一类 bug 的根治办法：多写一份就多一处漂移。
- `online/OnlineViewModel.kt` + `online/OnlineScreen.kt` —— 搜索框优先；
  未配置时**直接给配置表单**（就地填、就地 ping），不做"先去设置页"的跳转。
  点一首 = 从这首开始播整份搜索结果，与音乐库点歌行为一致。
- 媒体库 Tab 增加「在线曲库」入口。

## 四、验证

| 项 | 结果 |
|---|---|
| 真机 ping `.124:9527` | `status: ok`（`SubsonicLiveIT`，环境变量驱动，CI 跳过） |
| 真机 search3（空库） | 可解析、返回 0 条，不抛异常 |
| 单测 | 新增 `SubsonicClientTest` 13 条；全量 **463 用例 0 失败** |
| 未验证 | 有歌之后的直链实播（曲库为空）；跨公网/HTTPS 场景 |

## 五、下一步（已派给子智能体，各自独立 worktree）

| 任务 | 内容 | 验收 |
|---|---|---|
| voice | 中文命令词 + 系统语音识别（`IntentRouter` 纯函数） | 单测 ≥20 + 编译 |
| mymusic | 「我的音乐」四行入口 + 真实数量（`MyMusicSummary` 纯函数） | 单测 ≥8 + 编译 |
| dmr | 通用 DLNA 渲染器支持（电视盒子 `.175` AirReceiver） | 单测 ≥10 + 真机只读探测 |
| floating | 悬浮窗控制 + WPS 配网引导 | 单测 ≥14 + 编译 |

合并顺序：四条分支各自 PR → 我逐个 rebase/解冲突 → 全量单测 → 出一个 APK。
