# 80 · 洛雪音源整合：pdone/lx-music-source 实测与接入方案

> 写于 2026-11-05 · 依据 main `e2fa6e5`
> 用户：「这是音源，帮我想个办法整合或者找最简单方法弄到洛雪里面」

---

## 零、结论

**最简单的方法：贴一行链接。** 声控台的「导入脚本」已经支持从链接导入（PR #109），
官方洛雪 App 的自定义源也支持在线导入。链接是：

```
https://raw.githubusercontent.com/pdone/lx-music-source/main/huibq/latest.js
```

**为什么是 `huibq`**：我实测了全部 16 支，只有 3 支能同时打通五个平台，`huibq` 是其中最稳的一支。
**一支就够用** —— 这正好绕开「声控台目前只支持同时启用一支脚本」的限制。

---

## 一、这个仓库是什么

`pdone/lx-music-source`（**10,758★**，无许可证声明，今天还在推）是一个**音源聚合仓库**：

- 16 个目录，每个目录一支洛雪自定义源，产物统一是 `<名字>/latest.js`
- README 给每支两个「在线导入」链接：GitHub 原始链接 + 一个加速镜像
- 自带每日 01:00（北京时间）的可用性检测，结果放在 `health-status` 分支

它聚合的上游就是圈内常见那几支：SixYin、Huibq、LX、HYWmusic、JieXiang、长青、幻音、星海、fish、HelloWorld、玉宁熙……
我们之前测过的 `qdy`（全豆要）和 `sixyin`（六音）也在里面。

---

## 二、实测：仓库说 11 支可用，实际只有 3 支能打

`health-state.json`（2026-11-05 01:09）报：**11 支可用 / 3 支失效 / 1 支异常**。

我写了个探针真跑了一遍（真执行脚本、真取 URL、真去拉），用「周杰伦」试取 128k，结果差得远：

| 音源 | kw | kg | tx | wy | mg | 通过 | 仓库自检 |
|---|:-:|:-:|:-:|:-:|:-:|:-:|---|
| **huibq** | ✅ | ✅ | ✅ | ✅ | ✅ | **5/5** | 可用 |
| **jiexiang**（杰翔聚合） | ✅ | ✅ | ✅ | ✅ | ✅ | **5/5** | 可用 |
| **yuningxi**（玉宁熙 Pro） | ✅ | ✅ | ✅ | ✅ | ✅ | **5/5** | 可用 |
| helloworld | ✅ | ❌ | ✅ | ✅ | ✅ | 4/5 | 可用 |
| fish | ✅ | ✅ | ✅ | ❌ | ❌ | 3/5 | 可用 |
| qdy（全豆要） | ✅ | ❌ | ❌ | ✅ | ❌ | 2/5 | 可用 |
| xinghai（星海） | ✅ | ❌ | ❌ | ✅ | ❌ | 2/5 | 可用 |
| changqing（长青） | ❌ | ❌ | ❌ | ❌ | ❌ | 0/5 | 可用 |
| huanyin（幻音） | ❌ | ❌ | ❌ | ❌ | ❌ | 0/5 | 可用 |
| hyw | ❌ | ❌ | ❌ | ❌ | ❌ | 0/5 | 可用 |
| lx（独家） | ❌ | ❌ | ❌ | ❌ | ❌ | 0/5 | 可用 |
| ikun | ❌ | ❌ | ❌ | ❌ | ❌ | 0/5 | 失效 |
| flower / grass / juhe | init 都没回来 | | | | | — | 失效/异常 |
| sixyin（六音） | 脚本自身 JSON 解析崩 | | | | | — | 可用 |

**「仓库说可用」和「真能播」差 8 支。** 这不是仓库的错，是它的检测方法决定的（见下一节）。

具体坏法各不相同，值得记：

| 症状 | 例子 | 含义 |
|---|---|---|
| 返回的 URL 404 + `text/html` | changqing | 拿到的不是音频地址 |
| `block ip` | lx | 按出口 IP 拉黑，我们这台机器被拒 |
| 返回 200 但 `application/json` | qdy 的 kg | 上游给的是错误体，喂给音箱只会静音 |
| 返回 403 | qdy 的 tx | 防盗链 |
| init 都不回来 | flower / grass / juhe | 脚本要请求服务端配置，那个服务已经挂了 |
| 脚本自身 JSON 崩 | sixyin | 和我们 11-04 测六音时的结论一致 |

---

## 三、为什么仓库的自检会骗人

`probe.py` 文件头的原文：

> 约定：
> - 仅使用 Python 标准库；
> - **不执行任何音源脚本代码**（安全边界）；

它做的是：从脚本源码里**静态提取**上游主机名（明文 URL、十六进制转义解码、`SERVER_SCRIPT_CONFIG` 配置块），
然后对这些主机发一个 GET 探活。

所以「可用」= **上游主机活着**。脚本逻辑变了、密钥失效了、上游改了鉴权，它都看不出来。
**这是方法论的差别，不是谁做错了。** 只是我们要的判据不一样：我们要的是「能不能取到一段能播的音频」。

---

## 四、最简单的方法（三条，按场景选）

### 4.1 官方洛雪 App（手机或桌面）

设置 → 自定义源 → 添加 → **在线导入** → 粘贴：

```
https://raw.githubusercontent.com/pdone/lx-music-source/main/huibq/latest.js
```

打不开 GitHub 就换成镜像版（README 里给的，把域名换掉即可）：

```
https://ghproxy.net/raw.githubusercontent.com/pdone/lx-music-source/main/huibq/latest.js
```

### 4.2 声控台（我们自己的 App）

**这个已经有了** —— PR #109 加的「从链接导入」：导入框里贴 `https://…` → 下载 → 填进文本框 → 检查后点导入。

贴同一行 `huibq` 的链接即可。

### 4.3 离线兜底（网络不通时）

```bash
curl -sL -o huibq.js https://raw.githubusercontent.com/pdone/lx-music-source/main/huibq/latest.js
# 然后把 huibq.js 传到手机，用「导入脚本」选文件
```

---

## 五、真正的整合（要做的改动）

### 5.1 现状卡点：只支持同时启用一支脚本

PR #109 的说明里写得很清楚：

> 现在的「启用」是单值 `_active`，要改成"按源挑脚本"。

`core-lx` 解析脚本 `init` 时**已经拿到了**每支脚本支持哪些平台
（`LxProtocol.kt` 的 `sources: Map<String, LxSourceInfo>`），但三处没打通：

1. `LxUserApi` 的 `LxInitResult` 只保留 ok/errorMessage，没把 `sources` 带出来
2. `core-script` 的 `ScriptEntry` 没有 `sources` 字段
3. 「启用」是单值 `_active`，不是按平台挑

### 5.2 但**现在不需要动它**

因为 `huibq` / `jiexiang` / `yuningxi` **任何一支都覆盖五个平台**。

所以第一版整合只需要：

- 默认带一支（或让用户贴链接）
- 三支互为备份，一支挂了换另一支

### 5.3 值得做的三件小事（都不动契约）

| 事 | 为什么 |
|---|---|
| 诊断里显示当前脚本的 `@name` / `@version` / 覆盖平台 | 现在只知道「已加载」，不知道跑的是哪支、能播哪些平台 |
| 「换一支」按钮，内置三支推荐链接 | 音源天天变，用户不该自己去 GitHub 找 |
| 导入时提示「该脚本声明支持：kw kg tx wy mg」 | 声明和实际是两回事，但至少能看出明显不匹配 |

### 5.4 什么时候才需要「按源挑脚本」

当你想**不同平台用不同脚本**时（比如 kg 用 A、wy 用 B）。判断标准：
**三支里有一支开始单平台退化**（比如 huibq 的 mg 挂了而 jiexiang 的还活着）。

现在还没到那一步 —— 三支都是全通的。

---

## 六、工具（都已实测）

### 6.1 `scripts/lx-source-probe.js`

真执行脚本、真取 URL、真拉一次。判据严格：

- 必须 HTTP 200/206
- **不能是 `text/html` 或 `application/json`** —— 酷狗会返回 200 + JSON 错误体，那种"成功"喂给音箱只会静音
- 跟 3 次重定向（音源常给中转地址）

```bash
node scripts/lx-source-probe.js --script /tmp/huibq.js
node scripts/lx-source-probe.js --all-scripts /tmp/lxsrc --json
```

输出示例（实测）：

```
=== huibq/latest.js 「Huibq_lxmusic源」 v1.2.0
  声明的平台: kw, kg, tx, wy, mg
  ✅ kw: url  《发如雪 - 周杰伦》  2606ms  HTTP 206 audio/mpeg
  ✅ kg: url  《发如雪 - 周杰伦》   901ms  HTTP 206 audio/mpeg
  ✅ tx: url  《发如雪 - 周杰伦》   301ms  HTTP 206 audio/mpeg
  ✅ wy: url  《想你就写信 (Live)》  701ms  HTTP 206 audio/mpeg
  ✅ mg: url  《圣诞星（feat. 杨瑞代）》 401ms  HTTP 206 audio/mpeg
```

### 6.2 `scripts/lx-source-sync.py`

一键：抓清单 → 下载 16 支 → 逐支探测 → 输出排名与推荐链接。

```bash
python3 scripts/lx-source-sync.py            # 下载到 ./lx-sources/
python3 scripts/lx-source-sync.py --song 稻香
```

产物 `lx-sources.recommended.json` 含每支的逐平台通过情况、版本、链接。

**建议每周跑一次**（音源变化以天计）。

---

## 七、坑（实测踩到的）

| 坑 | 说明 |
|---|---|
| **仓库自检不等于能用** | 它不执行脚本，只探主机。11 支"可用"里 8 支实际不能播 |
| **`application/json` 假成功** | 酷狗返回 200 + JSON 错误体。只看状态码会误判成"能用" |
| **3xx 要跟** | 音源经常给中转地址，不跟跳转就报 302 |
| **链接有时效** | `vkey` / `ts` / 路径里的时间戳都会过期，所以必须现取现用，不能缓存直链 |
| **按 IP 拉黑** | `lx` 那支对我们这台机器的出口 IP 直接返回 `block ip` |
| **异步抛异常** | `flower` 在异步回调里抛「服务器异常」，不接住会带崩整个探测进程 |
| **平台串台** | huibq 的 `mg` 结果指向 kuwo 的盘（和 `kg` 同一个地址），能播但平台不对 |
| **明文 http** | 三支给的都是 `http://` 直链。我们 11-04 已实测 DOSS 能播 http 和 https，不是问题 |

---

## 八、合规边界

- 仓库**没有许可证声明**，且明确写着「内容源于网络」「禁止批量下载」（huibq 那支的脚本头部）
- 我们只**个人使用、不内置、不分发**，也不把这些脚本打包进仓库产物
- 脚本从上游 URL 现拉现用，仓库里只放**探测工具**，不放音源脚本本体
- 这与项目一直守的线一致：不内置平台音源、不破解付费授权

---

## 九、一句话

**贴 `huibq` 那一行链接就能用；要更稳就三支互为备份。真正该改的不是"支持多支"，是"让用户知道现在跑的是哪支、能播哪些平台"。**
