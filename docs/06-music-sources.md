# 音源方案调研：能否参考洛雪（LX Music）这类项目

> 只做调研，不写代码。所有结论标注依据；法务判断部分不是法律意见，落地前请自行确认。

## 0. 结论

| 问题 | 答案 |
|---|---|
| 洛雪的做法能照抄吗？ | **架构可以抄，音源不能自带**。LX 现在的形态（软件不含任何音源 + 用户自行导入 JS 脚本）本身就是「被律师函之后的自保形态」。 |
| 我们应该接 QQ 音乐吗？ | **不接**。理由见 §3：Listen1 就是被 QQ 音乐 DMCA 打掉的先例。 |
| 最优音源路线 | 本地文件（已有）→ 网络电台 URL（合规、老音箱最实用）→ Subsonic/Navidrome（合法自建）→ 可选「用户自备插件」壳子。 |
| 需要 JS 引擎吗？ | 只有走「洛雪式脚本」才需要（QuickJS/Rhino）。**建议先不做**，它把合规风险直接搬到自己身上。 |

## 1. 洛雪音乐（LX Music）现在的真实形态

依据：官方文档站 `lxmusic.cn/docs/music-source/`（抓取于本文档写作时）

> 「洛雪音乐本身**不内置任何音乐源**，软件通过自定义源功能来获取音乐数据。」
> 「本站**不提供任何音乐源脚本**。音乐源脚本需用户自行编写或从第三方获取，使用时请遵守所在地法律法规。」

背景（社区记录）：项目曾内置各平台接口，收到律师函后移除了内置源，只保留自定义源机制。

### 音源脚本机制（技术形态）

- 文件：`.js`，UTF-8，ES6+
- 头部注释声明元信息：`@name` / `@description` / `@version` / `@author` / `@homepage`
- 事件驱动：加载后发 `inited` 事件，声明支持的源与音质（`128k` / `320k` / `flac` / `flac24bit`）
- 注册 `request` 事件处理「取 URL / 取歌词 / 取封面」
- 网络请求走宿主注入的 `globalThis.lx.request`（绕过跨域）
- 「换源」：某个源取不到就自动换下一个

**架构本质**：宿主提供播放器 + 脚本沙箱 + HTTP 代理；音源逻辑完全外置。
这是把「分发侵权内容」的责任从软件转移到用户侧的标准做法（类似 yt-dlp 的 extractor、浏览器 userscript）。

## 2. 同类项目参照

| 项目 | 模式 | 许可/状态 | 对我们的意义 |
|---|---|---|---|
| **MusicFree** | 插件化，App 本身不含任何平台音源，搜索/播放/歌单全靠插件 | AGPL-3.0，Android/HarmonyOS | 与 LX 同构；**AGPL 传染性**，不能抄代码只能用思路 |
| **Listen1** | 聚合网易/QQ/酷狗等 | 浏览器插件/桌面版；**收到 QQ 音乐 DMCA，主仓库曾关闭** | **反面教材**：正面聚合会被平台打击 |
| **Navidrome / Gonic** | 自建库 + Subsonic/OpenSubsonic API | 开源 | **最合规的「在线音乐」**：自己的文件，自己的服务器 |
| **Music Assistant** | 聚合 provider（含 Subsonic 源、DLNA 播放器） | 开源 | 我们已被它覆盖的场景，不必重复实现 |

## 3. 为什么「接 QQ 音乐」这条路不能走

1. **技术面**：QQ 音乐没有对第三方开放的播放取流 API；能做到的都是逆向私有接口或抓取直链，接口随时变、需要持续对抗。
2. **法务面**：Listen1 收到过 QQ 音乐的 DMCA Takedown（其仓库 README 里有原话），主仓库因此关闭。这是公开可查的先例。
3. **产品面**：**用户根本不需要我们转一手** —— 音箱是标准 DLNA 渲染器，QQ 音乐 App 自带 DLNA/QPlay 投屏，直接把音推给音箱即可。我们做一个中间层只会增加一层失败点。
4. **我们的定位**：这个 App 的价值是「设备控制 + 配网 + 老固件适配」，不是音乐内容分发。

## 4. 推荐音源分层（由易到难、由合规到灰色）

### 第 1 层：本地文件（已实现）

MediaStore + 手机 HTTP 服务 + PlayQueue 整列表推送。零合规风险。

### 第 2 层：网络电台（建议下一步）

- 用户手动粘贴流地址（`http(s)://...mp3|m3u8|aac`），存本地（对应原 App 的 `tb_net_radio`：`soundName/soundUrl/isKept/deviceName`）
- 也可内置少量**明确允许转载**的公共电台目录（如 Radio Browser 的公开索引）
- 合规、稳定、老音箱也吃不消复杂协议，正好匹配

### 第 3 层：自建音乐服务（Subsonic / OpenSubsonic）

- 用户跑 Navidrome/Gonic，App 当客户端：`getAlbumList2` / `search3` / `stream`
- 我们只负责把 `stream` 给的 URL 投给音箱（**注意**：多数 Subsonic 流需要认证头或 token，而音箱拉流带不了自定义头 →
  实际做法是 App 内置反向代理，把 `http://手机/t/<id>` 转成带认证的上游请求。这正好复用我们已有的本地 HTTP 服务）
- 完全合规；一次实现同时覆盖 Navidrome / Gonic / Airsonic 等

### 第 4 层（可选、最后再考虑）：用户自备「音源插件」壳子

若真要做洛雪式插件：

- 需要 JS 运行时：QuickJS（`quickjs-android`）或 Rhino；LX 的 API 是 `globalThis.lx.request` + 事件模型，可对齐
- 必须做到：**不自带、不推荐、不内置任何源**；插件由用户自行导入；错误提示里不出现任何平台名
- 风险仍在：AGPL 项目（MusicFree）的代码不能抄；一旦我们在文档里「指导如何获取某平台音源」，就失去了「工具中立」的立场
- **建议**：先做 1–3 层；第 4 层等有明确用户需求再做，且单独评估

## 5. 明确不做的

- 逆向 QQ 音乐 / 网易云 / 酷狗 / 酷我 / 咪咕 的取流接口
- 内置任何「音源脚本」或提供下载入口
- 复用原 App 里那批硬编码凭据（蜻蜓 `client_secret`、讯飞 appid、iHeart apikey）——它们是签发给原 App 的，见 `docs/01` §5

## 6. 待确认

- MusicFree 与 LX 的插件/脚本 API 是否有公开规范文档（会影响第 4 层的兼容成本）
- Radio Browser 目录的许可与稳定性（第 2 层若内置目录需确认）
- 第 3 层反向代理方案在「音箱不支持认证头」这一约束下的实际兼容性（需真机验证）

## 7. 依据清单

- LX Music 音源机制与「不提供任何音乐源脚本」声明：`https://www.lxmusic.cn/docs/music-source/`
- LX Music 仓库：`https://github.com/lyswhut/lx-music-desktop`、`https://github.com/lyswhut/lx-music-mobile`
- MusicFree：`https://github.com/maotoumao/MusicFree`（AGPL-3.0）、`https://musicfree.catcat.work/`
- Listen1 收到 QQ 音乐 DMCA 的说明：`https://github.com/listen1/listen1_chrome_extension`（README）
- 生态接入（Music Assistant / Lyrion / HA WiiM 等）：见 `docs/04-ecosystem-integration.md`
