# 73 · core-lx 落地：洛雪脚本引擎 + musicSdk 打包

> 2026-11-04 · 依据 `lyswhut/lx-music-mobile` v1.9.2（Apache-2.0）
> 承接 `docs/70`（可行性）、`docs/71`（模块设计）

---

## 零、这一版做了什么

按 `docs/71` 的方案，把 `core-lx` 模块真正写出来了：

```
core-lx  Android 模块，1354 行主代码 + 1137 行单测，80 个用例
├── assets/
│   ├── lx-preload.js              官方 594 行，**一字未改**
│   └── lx-music-sdk.bundle.js     构建产物 595KB，五平台搜索可用
├── js/                            打包输入（不打进 APK）
│   ├── bridge.js                  原生桥的 JS 侧
│   ├── entry.js                   musicSdk 入口，回调式 API
│   └── shim/                      6 个 RN 替身
└── src/main/kotlin/.../lx/
    ├── LxProtocol.kt              报文解析 + 错误翻译（纯 JVM 可测）
    ├── LxScriptInfo.kt            脚本头解析（纯 JVM 可测）
    ├── LxCrypto.kt                AES/RSA/MD5/SHA1（纯 JVM 可测）
    ├── LxHttp.kt                  替脚本发 HTTP（纯 JVM 可测）
    ├── LxEngine.kt                引擎接口（为了可测）
    ├── QuickJsEngine.kt           QuickJS 绑定（Android）
    ├── LxUserApi.kt               用户脚本宿主
    ├── LxMusicSdk.kt              musicSdk 宿主
    └── LxAssets.kt                assets 读取
```

配套脚本：

| 脚本 | 作用 |
|---|---|
| `scripts/build-lx-bundle.sh` | 从上游源码重新打包 bundle（手动跑，CI 不跑） |
| `scripts/verify-lx-bundle.js` | 用 Node 假原生桥驱动 bundle，验证产物没坏 |

---

## 一、两个宿主，各管一段

| | `LxUserApi`（用户脚本） | `LxMusicSdk`（musicSdk） |
|---|---|---|
| 干什么 | songmid → 播放直链 | 搜索 / 热搜 / 歌单 / 歌词 / 封面 |
| 有没有搜索 | ❌ 脚本只给直链 | ✅ 五个平台 |
| 跑什么 | 官方 preload + 用户脚本 | 打包好的 bundle |
| 上下文 | 独立 QuickJSContext | 独立 QuickJSContext |

**必须两个上下文**：官方 preload 会冻结 `globalThis` 并禁用 `eval`（安全加固），
如果和 SDK 共用，SDK 一加载就废。

**为什么必须两个一起用**：洛雪 2.x 把内置取流关掉了（`api-source-info.ts` 是空数组），
但内置搜索还在。所以「搜歌靠 SDK、出直链靠脚本」。

---

## 二、bundle 是真的能用（不是"编译过了"）

`scripts/verify-lx-bundle.js` 用 Node 搭了个假原生桥，驱动**提交进仓库的那个 bundle**：

```
=== bundle 自检 ===
支持的平台: wy, kw, tx, mg, kg
  wy: 3 条 / total 277  例: 想你就写信 (Live) - 周杰伦、李硕、张鑫
  kw: 3 条 / total 3292  例: 发如雪 - 周杰伦
  tx: 3 条 / total 999   例: 发如雪 - 周杰伦
  mg: 4 条 / total 150   例: 圣诞星（feat. 杨瑞代） - 周杰伦
  kg: 3 条 / total 480   例: 发如雪 - 周杰伦
  热搜(wy): 20 条
  歌单(wy): 2 个
```

**这一步不能省**：bundle 是构建产物，光"打包成功"说明不了它能跑。
用假桥跑一遍才知道 shim 有没有漏。

---

## 三、单测抓出来的 4 个真 bug

这些都不是"代码风格问题"，是**上线就会坏**的东西。

### 1. 协议方向搞反了（最严重）

洛雪有两个方向的报文都叫 `response`，形状完全不同：

| 方向 | 形状 |
|---|---|
| 原生 → JS | `{requestKey, error, response:{statusCode, headers, body}}` |
| **JS → 原生** | `{requestKey, status, result}` 或 `{requestKey, status:false, errorMessage}` |

我第一版按第一个形状去解析脚本的答复，结果**永远拿不到结果**——
`requestKey` 能对上，但 `status`/`result` 全读不到，最后表现为"脚本没返回"。

单测 `LxUserApiTest.取直链成功` 直接挂掉才暴露。现在分成两个方法：
`parseScriptResult()`（JS→原生）和构造用的 `requestPayload()`。

### 2. `AES/CBC/PKCS7Padding` 在标准 JDK 上不存在

官方 Android 版用这个名字（Conscrypt 认），但**标准 JDK 没有这个 transformation**，
只有 BouncyCastle 才提供。对 16 字节块来说 `PKCS5Padding` 与 `PKCS7Padding` **完全等价**，
改用后者，这样单测能在 JVM 上跑、Android 上也一样。

### 3. `toBodyJson` 用宽松 Json 解析，把裸文本当成 JSON

`Json { isLenient = true }` 会把裸文本 `hello` 成功解析成 JSON 字符串值，
于是 body 变成裸值而不是字符串，JS 侧 `body.xxx` 取不到东西。

修法：先用**严格** Json 判断"这段文本像不像 JSON"（看首字符），
像才按 JSON 解析，否则用 `JsonPrimitive(text)` 包成字符串字面量（自动转义引号换行）。

### 4. 单行注释形式的脚本头解析不了

`/*! @name 只有名字 */` 这种单行写法，我的解析器只去掉了行首的 `*`，
没去掉 `/*` 和 `!`，于是 `@name` 那行不认。

修法：每行循环剥掉 `/*` / `*` / `!` 前缀。

---

## 四、诚实说明：真机没验过

| 验过的 | 怎么验的 |
|---|---|
| bundle 能搜五个平台 | Node 假桥实跑（上面那串输出） |
| 协议报文、超时、错误翻译 | 80 个 JVM 单测（假引擎） |
| 加密与 JDK 标准实现一致 | 交叉验证 |
| HTTP 收发 | 本地假服务器 |
| 编译 + 打包 | `:core-lx:testDebugUnitTest` + `:app:assembleDebug` 全绿 |

| **没验的** | 为什么 |
|---|---|
| **QuickJS 里真的跑起来** | 这台机器没有 Android 设备也没有模拟器；`wrapper-android` 的 so 只有 Android ABI，JVM 跑不了 |
| 与音箱联调（搜到歌 → 投送 → 出声） | 同上，而且 `app` 还没接 `core-lx` |

**这是本轮最大的缺口，不能当成"已经能用了"。**
下一步必须在真机上跑一次 `LxMusicSdk.search("wy", "周杰伦")`，
确认 QuickJS 里的行为和在 Node 里一致（尤其是 `pako`、`buffer` 这些 polyfill 在 QuickJS 里是否正常）。

---

## 五、怎么重建 bundle

```bash
# 用已有检出（快）
LX_SRC=/path/to/lx-music-mobile scripts/build-lx-bundle.sh

# 或者自动 clone 指定 tag
LX_TAG=v1.9.2 scripts/build-lx-bundle.sh

# 重建后务必自检
node scripts/verify-lx-bundle.js core-lx/src/main/assets/lx-music-sdk.bundle.js
```

**上游改了怎么办**：如果 lx 重构了目录结构，脚本会在第 1 步就报错停下
（找不到 `src/utils/musicSdk/wy/index.js`），不会产出坏包。

---

## 六、还没做的事

| 事项 | 状态 |
|---|---|
| `app` 依赖 `core-lx` | ❌ 未接 |
| 界面上"导入脚本 / 发现脚本"入口 | ❌ 未做 |
| 在线搜索页接 `LxMusicSdk` | ❌ 未做 |
| 诊断段（脚本状态/搜索耗时/失败原因） | ❌ 未做 |
| 真机验证 | ❌ **缺** |
| 打包脚本进 CI | ❌ 故意不进（避免每次构建 clone 上游 + npm install） |

---

## 七、许可

- `lx-music-mobile`：**Apache-2.0**，`lx-preload.js` 与 bundle 均来自该项目，保留原文件头
- `wang.harlon.quickjs:wrapper-android`：**Apache-2.0**
- **用户脚本不打包不内置**，用户自己导入；取流仍靠用户脚本，不内置任何平台凭据

`NOTICE` 里需要写明：本项目包含来自 `lyswhut/lx-music-mobile`（Apache-2.0，已修改）的代码。
