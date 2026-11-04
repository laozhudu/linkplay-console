# 68 · tt-music 与 radiostation-discovery：两个模块的真实状态

> 2026-11-04 · 用户纠正了我的理解，重新调研
> 依据代码状态：`106d1d9`

---

## 零、先纠正我上一轮的错误

`docs/67` 我写的是：

> 在线曲库：代码完善了，但 `.124` 上根本没有音乐……**这是重复投资，不是缺陷**

**这个判断是错的。** 我把 tt-music 当成了"本地音乐库服务器"，
而它的真实定位是 —— **把在线音源（网易云/QQ/酷狗等）转成标准 Subsonic 接口**。

它本来就不需要本地音乐。`scanStatus.count = 0` 是**正常的**，不是问题。

真正的问题在别处，下面说清楚。

---

## 一、tt-music 是什么（实测确认）

```
镜像：penhaijun/lx-music-server:latest
启动：node index.js
日志：
  [Proxy] Music SDK Proxy: Disabled
  [INFO] startup - musicSdk initialized
  [UserApi] 启动源文件监控: /server/data/users/source
  [UserApi] 初始化所有自定义源...
```

它是 **lxserver 系**（洛雪音乐的服务端），`ping` 返回 `"type":"lxserver"`。

### 能力面（查项目文档确认）

| 能力 | 说明 |
|---|---|
| Subsonic 协议 | **全面兼容**（音流 / Feishin / LMP 等客户端可直连） |
| **全网在线搜索** | 支持平台前缀：`wy:`（网易云）/ `tx:`（QQ）/ `kg:`（酷狗）/ `kw:`（酷我）/ `mg:`（咪咕） |
| 范围前缀 | `online:` 只搜在线 / `local:` 只搜本地 |
| 本地资源检索 | 同时支持（`localLibrary.enabled: true`） |
| 音频链接 | **靠"自定义源脚本"解析**，服务端本身不产音频 |

**所以你的理解是对的**：它就是"在线音源 → 标准接口"的转换器。

---

## 二、为什么搜不到（根因）

### 源脚本其实装了，而且有一个已启用

`/vol2/1000/ttmusic/config/users/source/_open/sources.json`：

| 脚本 | 版本 | enabled | 支持的平台 |
|---|---|---|---|
| 六音音源 | v1.2.1 | ❌ **false** | kw / kg / tx / wy / mg |
| **全豆要[聚合音源]** | 9.3 | ✅ **true** | wy / tx / kw / kg / mg / qsvip |

**"全豆要聚合音源"是启用的**，支持五个主流平台。

### 但用户没有访问源的权限

`/vol2/1000/ttmusic/config/users/tt01_65ad5e/token.json`：

```json
{
  "enabled": false,
  "tokens": []
}
```

而 `config.js` 里：

```
user.enablePublicRestriction: true     ← 公共权限受限
```

项目文档原话：

> `user.enablePublicRestriction` 控制**公共权限限制**，默认开启。
> 具体能力还由对应开关决定。

**所以链条是**：

```
源脚本已启用  ✅
    ↓
但 tt01 用户的"源访问令牌"未启用  ❌
    ↓
Subsonic 搜索不代理到在线源
    ↓
search3 返回空（只查了本地库，而本地库是空的）
```

**这就是 `scanStatus.count = 0` + 搜索全空的完整解释。**

### 实测验证

| 请求 | 结果 |
|---|---|
| `search3?query=a` | `song: []` |
| `search3?query=wy:周杰伦` | `song: []` |
| `search3?query=online:周杰伦` | `song: []` |

三种都空 —— 说明不是"前缀写错"，是**整个在线源通道没打开**。

---

## 三、怎么修（两条路，都很短）

### 方案 A · 后台给 tt01 启用源访问（推荐）

1. 打开 `http://192.168.99.124:9527/admin`（密码 `123456`）
2. 用户管理 → `tt01` → 找到"自定义源 / 源访问"相关开关
3. 启用 → 保存
4. 验证：`search3?query=wy:周杰伦` 应该返回歌曲

**为什么推荐**：只放开这一个用户，权限最小。

### 方案 B · 关掉公共限制

`config.js` 里把 `"user.enablePublicRestriction": true` 改成 `false`，重启容器。

**代价**：所有能连到这个服务的客户端都能用源。
但这是**内网自用**（`bindIP: 0.0.0.0` 但在 `.124` 上，外面进不来），风险可接受。

### 修好之后能做什么

- 在 App 的在线曲库页搜 `wy:周杰伦` → 出歌单 → 点一首 → 投给音箱
- 音频直链由源脚本返回（多半是各平台的 CDN 直链，**音箱裸 GET 能拉**）
- 所以**这条路是通的**，只是卡在一个开关

---

## 四、radiostation-discovery：你自己的电台模块

### 它是什么

`laozhudu/radiostation-discovery`（private，2026-11-03 建）

> 纯 Kotlin/JVM 广播电台发现与流媒体 URL 解析模块，可被任何播放器项目接入

| 特性 | 说明 |
|---|---|
| 依赖 | **零 Android 依赖**，纯 Kotlin/JVM |
| 数据源 | **Radio Browser**（radio-browser.info，全球几万个台） |
| 能力 | `search(query)` / `searchByPlace(country, state)` / `getPopular()` |
| 流解析 | `resolveStream()` 跟随 302，把中间页 URL 解析成真实 `.mp3` / `.m3u8` |
| HTTP | 默认 `HttpURLConnection`，零额外依赖，可换 OkHttp |
| 可替换数据源 | `RadioStationSource` 接口，可换成自己的电台目录 |
| 规模 | 11 个 Kotlin 文件，35 KB，MIT |

### 和 App 里现有 radio/ 模块的区别

| | App 里的 `radio/` | `radiostation-discovery` |
|---|---|---|
| 电台来源 | **5 个硬编码** + 用户手填 | **Radio Browser 全库（几万个）** |
| 搜索 | 无 | 按名称 / 按地区 / 热门 |
| 流地址解析 | 无（直接用用户填的） | 跟随 302 解析真实流 |
| 真机验证 | 5 个台全部验证过 | **未接入，未验证** |

**所以这两个不是重复，是两层**：
- 现有 `radio/` = 已验证的精选台（5 个，能播）
- `radiostation-discovery` = 全球可搜（几万个，但能不能播要筛）

---

## 五、接入 radiostation-discovery 的关键约束

### 硬约束：音箱不认 https

实测（`docs/42`，`.164`）：
- `http://` → play
- `https://` → 命令回 OK 但没播

而 Radio Browser 里 **https 占绝大多数**。实测搜"中国"：

```
总 24 个台：http 3 个（12%），https 21 个
```

**只接 http 的话，几万个台里只有一成能用。**

### 但 RelayMediaServer 能解决这个问题

我查了它的实现：

```kotlin
if (method == "GET") up.body?.copyTo(output, 16 * 1024)
```

`copyTo` 是**边读边写**（16KB 缓冲），不是整包读进内存 —— **能转发无限长的音频流**。

它的类注释也写明了设计意图：

> 转发媒体服务：把「设备拉不了的上游链接」变成「设备能拉的本机链接」

**所以**：
```
https://电台流  →  RelayMediaServer  →  http://手机:端口/token/xxx  →  音箱能拉
```

**代价**：手机要一直在线当中继（App 在后台跑着）。

### 所以接入方案分两档

| 方案 | 覆盖 | 手机要求 | 复杂度 |
|---|---|---|---|
| **A. 只接 http 台** | 12% | 不需要 | 低（过滤即可） |
| **B. https 走 Relay 转发** | ~100% | 要一直在线 | 中（已有 RelayMediaServer） |

**建议先做 A**（低风险、能立刻验证），B 作为后续增强。

---

## 六、两个模块对比：互补，不重复

| | tt-music（在线曲库） | radiostation-discovery（电台） |
|---|---|---|
| 内容 | **音乐**（网易云/QQ/酷狗/酷我/咪咕） | **广播电台**（全球） |
| 接入方式 | Subsonic HTTP 接口 | 编译进 App 的 Kotlin 库 |
| 部署 | 服务端（`.124` Docker，已在跑） | 无需部署 |
| 当前状态 | **只差一个权限开关** | **代码在，未接入 App** |
| 手机在线要求 | 不需要（服务端直链） | https 台需要中继 |
| 真机验证 | 未验证（因为搜不到） | 未验证（因为没接入） |

**它们解决的是不同需求**：
- 想听某首歌 → 走 tt-music（搜歌名）
- 想听广播/新闻/音乐电台 → 走 radiostation-discovery

---

## 七、建议的动作顺序

| # | 做什么 | 谁做 | 成本 |
|---|---|---|---|
| 1 | **给 tt01 启用源访问**，然后验证 `wy:周杰伦` 能搜到 | 你（点几下） | 5 分钟 |
| 2 | 验证搜到的歌能不能投给音箱播（关键：源脚本返回的直链音箱能不能拉） | 你 + 我 | 10 分钟 |
| 3 | 把 `radiostation-discovery` 接进 App（先只接 http 台） | 开发对话 | 半天 |
| 4 | 视情况做 https 台走 Relay 转发 | 开发对话 | 1-2 天 |

**第 1、2 步必须先做** —— 因为如果源脚本返回的直链音箱拉不了（可能有防盗链），
那 tt-music 这条路就白搭，得改用 Relay 转发。

**这是整个判断的关键未知**：源脚本给的链接，音箱能不能裸 GET 拉到。

---

## 八、一句话总结

**tt-music：能力完整，源脚本已装并启用，只差给用户开权限。** 我上一轮说"没内容/重复投资"是错的。

**radiostation-discovery：你自己的模块，功能比现有 radio/ 强一个量级**（几万个台 vs 5 个硬编码），
关键约束是"音箱不认 https"，但 `RelayMediaServer` 能解决。

**两个都值得用，而且是互补的**：一个搜歌，一个听广播。

**下一步最关键的不是写代码，是先验证一件事**：
tt-music 源脚本返回的直链，音箱能不能直接拉？
这个答案决定后面所有方案的走向。
