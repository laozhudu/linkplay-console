# 71 · 把洛雪整个做成一个模块：实测可行，代价是「造一个最小 RN 运行时」

> 2026-11-04 · 依据 `lyswhut/lx-music-mobile` v1.9.2（Apache-2.0）+ 官方 APK 实测
> 本文是**可行性论证 + 实测数据**，不含代码改动

---

## 零、结论

**能做。但性质不是「抄逻辑」也不是「手搓接口」，而是：把洛雪的 JS 核心原样跑起来，
外面套一个最小运行时。**

| 说法 | 对不对 |
|---|---|
| 「抄洛雪的算法用 Kotlin 重写」 | ❌ 那是 1 万行 JS，重写要两三个月，而且平台一改就废 |
| 「手搓一个 lx 兼容接口」 | ❌ 没必要，官方 preload 直接抄 |
| 「把洛雪的 JS 当模块跑」 | ✅ **这条路对，而且我已经跑通了** |

**实测结果（1 小时内做到的 PoC）**：把洛雪 `musicSdk` 的源码原样打包，
在**完全脱离洛雪 App** 的宿主里跑，**五个平台搜索全通**，还带热搜、歌单、封面：

```
### wy
  搜索: 2 条 / total 277        热搜: 20 条   歌单搜索: 2 个   封面: OK https://p1.music.126.net/...
### kw
  搜索: 2 条 / total 3292       热搜: 20 条   歌单搜索: 2 个   封面: OK http://img1.kwcdn.kuwo.cn/...
### tx
  搜索: 2 条 / total 999
### mg
  搜索: 4 条 / total 150
### kg
  搜索: 3 条 / total 480
```

---

## 一、先说官方 APK 里是什么（你说得对，我查了）

`lx-music-mobile-v1.9.2-arm64-v8a.apk`（13.9MB）解开：

| 内容 | 是什么 | 能不能用 |
|---|---|---|
| `assets/index.android.bundle`（2.45MB） | **Hermes 字节码**（魔数 `c61fbc03`） | ❌ **取不出源码**，也不能跨 App 复用 |
| `assets/script/user-api-preload.js`（21KB） | 用户脚本注入层 | ✅ 源码仓库里有同一份，直接抄 |
| `lib/arm64-v8a/libquickjs-android-wrapper.so` | QuickJS 引擎 | ✅ 说明官方就用这个 |
| `libquickbase64.so` / `libquickmd5.so` | RN 原生模块 | ✅ 对应两个 shim |

**关键结论**：APK 里的 JS 是 Hermes 字节码，**抄不出来**。
所以「从 APK 抄」这条路走不通 —— 但**源码仓库是 Apache-2.0，直接抄源码就行**，
而且我已经证明源码能打包、能跑。

---

## 二、洛雪 2.x 到底还剩什么（重要）

很多人以为洛雪是「内置五大平台音源」。**2.x 已经主动关掉了内置取流**：

```js
// src/utils/musicSdk/api-source-info.ts
const sources: Array<{...}> = []        // ← 空数组
```

```js
// api-source.js
const getAPI = source => apiList[`${apiSetting}_api_${source}`]
const apis = source => {
  if (/^user_api/.test(settingState.setting['common.apiSource'])) return global.lx.apis[source]
  const api = getAPI(source)            // → undefined
  if (api) return api
  throw new Error('Api is not found')   // ← 内置取流直接抛错
}
```

**所以现在的分工是**：

| 能力 | 谁提供 |
|---|---|
| **搜索 / 热搜 / 歌单 / 专辑 / 歌手 / 歌词 / 评论 / 排行榜** | ✅ **内置 musicSdk**（10,379 行 JS，能跑） |
| **播放直链** | ❌ 只能靠**用户自装脚本**（`user_api*`） |

**这正好和我们的需求对齐**：我们缺的是「搜索」，而搜索恰恰是内置的那部分。

---

## 三、实测：要补哪些东西（这是最有价值的部分）

我把 `musicSdk` 用 esbuild 打包（522KB），逐个补齐缺口。**实际缺口比想象小得多**：

### 静态依赖（全量扫描出来的）

| 类型 | 清单 |
|---|---|
| **RN 包（3 个）** | `react-native-quick-base64`、`react-native-quick-md5`、`react-native-background-timer` |
| **npm 包（3 个）** | `pako`、`crypto-js`、`buffer` |
| **项目别名（3 个）** | `@/utils/nativeModules/crypto`、`@/utils/request`、`@/store/setting/state` |
| **RN 全局** | 只有 `global.lx`（2 处） |

### 运行期才暴露的全局（实测踩出来的）

| 全局 | 谁用 | 后果 |
|---|---|---|
| `fetch` | 部分平台 | Node 不认 RN 的 `cache:false` 等选项 → 报错 |
| `FileReader` | tx 的 blob 处理 | `global.FileReader is not a constructor` |
| `btoa` / `atob` | quick-base64 | 一开始我 shim 只给了 `toByteArray`，直接崩 |

**总共约 10 个 shim。** 我 1 小时写完了 8 个，5 个平台搜索全通。

### 加密桥（和用户脚本**同一套**）

洛雪的 `musicSdk` 要这些：

```
aesEncryptSync / aesDecryptSync / rsaEncryptSync / rsaDecryptSync / hashSHA1 / md5
```

**和用户脚本要的 `utils.crypto` 是同一批原语**（AES/RSA/MD5）。
所以一份 Kotlin 实现能同时服务两边 —— 这是把两件事合成一个模块的物理基础。

---

## 四、模块设计

### 一个模块，两个 JS 载荷

```
core-lx   ★新   Android 模块
├── assets/
│   ├── lx-preload.js          官方 594 行原样拷（用户脚本注入层）
│   └── lx-music-sdk.bundle.js  洛雪 musicSdk 打包产物（约 520KB）
├── JsRuntime.kt               QuickJS 生命周期 + 线程模型
├── NativeBridge.kt            桥：request / crypto / buffer / base64 / md5 / timer
├── Crypto.kt                  AES / RSA / MD5 / SHA1（照抄官方 AES.java、RSA.java）
├── ScriptHost.kt              用户脚本：加载/卸载/发事件/收结果
├── SdkHost.kt                 musicSdk：搜索/热搜/歌单/歌词/封面
└── LxModels.kt                两个宿主共用的数据结构
```

依赖：

```
core-lx → core-protocol（MediaTrack / RadioStation 那种纯数据）
core-lx → core-diagnostics
core-lx → wang.harlon.quickjs:wrapper-android:2.4.0
```

**为什么是一个模块而不是两个**：用户脚本和 musicSdk 共用
「JS 引擎 + request 桥 + crypto 桥 + buffer 桥」，拆两个就要把这些复制两遍。

**为什么不进 `core-protocol`**：QuickJS 是 JNI + `.so`，底座必须保持纯 JVM（`docs/69` 判据 1）。

### 上层怎么用

```
core-protocol/mediasource/LxSourceProvider.kt   实现 MediaProvider
   ├── search()  → SdkHost 调 musicSdk（五个平台）
   └── resolve() → ScriptHost 调用户脚本（拿直链）
```

对界面完全透明，`docs/57` 的验收判据（加新源只改一个文件）依然成立。

---

## 五、两条路的真实代价

| | **A. Kotlin 重写** | **B. 跑原 JS（本文）** |
|---|---|---|
| 工作量 | 10,379 行 JS 重写，**2-3 个月** | 打包 + 10 个 shim + 桥，**2-4 周** |
| 平台改了怎么办 | 自己改代码 | `git pull` 上游，重新打包 |
| 会不会过期 | 会，而且改起来慢 | 跟着上游走 |
| 能不能带歌词/评论/排行榜 | 要一个个重写 | **免费全拿到** |
| 风险 | 重写引入 bug | 上游架构变了要跟着改 |

**B 明显划算**：用两周换三个月的活，而且跟着上游升级。

---

## 六、具体施工顺序

| 步 | 做什么 | 验收 |
|---|---|---|
| 1 | 建 `core-lx` 空壳 + QuickJS 依赖 + CI 模块依赖检查 | `./gradlew projects` 5 个模块 |
| 2 | 移植 `user-api-preload.js` + 6 个桥，跑通用户脚本 | 导入一个脚本能列出它支持的平台 |
| 3 | 打包 `musicSdk` 进 assets，QuickJS 里跑搜索 | 五个平台能搜出结果（真机） |
| 4 | 补 `fetch`/`FileReader`/`Blob` 等全局 | tx 也能搜 |
| 5 | 接 `LxSourceProvider`，界面出在线搜索 | 搜一首 → 投给音箱出声 |
| 6 | 诊断段：脚本状态/搜索耗时/失败原因 | 报告里能看出是脚本挂了还是网络挂了 |

**第 3 步是分水岭**：在那之前都是准备，那一步过了就证明整条路成立。

---

## 七、许可与合规

| 项 | 结论 |
|---|---|
| lx-music-mobile | **Apache-2.0**，可抄可改可闭源；义务：保留 LICENSE/NOTICE + 标注修改 |
| QuickJS wrapper | Apache-2.0 |
| 打包进 APK 的 `musicSdk` | 属于 Apache-2.0 代码，**必须保留版权声明** |
| 用户脚本 | **不打包、不内置**，用户自己导入（和洛雪一致） |
| 内置搜索算不算侵权 | 洛雪自己都还留着，风险与洛雪同级；**取流仍靠用户脚本，不内置凭据** |

**必须写进 `NOTICE`**：本项目包含来自 `lyswhut/lx-music-mobile` 的代码（Apache-2.0，已修改）。

---

## 八、风险（诚实列）

| 风险 | 影响 | 缓解 |
|---|---|---|
| **上游重构** | musicSdk 目录结构变了，打包脚本要改 | 打包脚本进仓库，CI 里能发现 |
| **平台改 API** | 某个平台搜不到 | 上游修好后重新打包 |
| **RN 全局越用越多** | 某个功能突然报 `XXX is not a constructor` | 每加一个就补一个 shim，诊断里记清楚 |
| **QuickJS 与 Node 差异** | Node 能跑的 QuickJS 不一定能（无 `zlib`、无 `Buffer`） | `pako` 已打进 bundle；`buffer` 要一起打 |
| **APK 体积** | bundle 520KB + QuickJS 1.3MB ≈ **+1.8MB** | 只打 arm64 |
| **法律** | 内置搜索依赖平台未授权接口 | 与洛雪同级；不内置取流凭据 |

---

## 九、和之前几份文档的关系

| 文档 | 说了什么 | 本文怎么接 |
|---|---|---|
| `docs/68` | tt-music 能搜能放，但只有网易云可用 | tt-music 是**外部服务**方案，本文是**本地模块**方案 |
| `docs/69` | 本地播放器要开 `core-playback`（Media3） | `core-lx` 和它同级，都是「拖第三方依赖所以开模块」 |
| `docs/70` | 用户脚本引擎可行（照抄 preload） | 本文把它扩成「连 musicSdk 一起跑」，**一个模块两个载荷** |

**三条路线可以共存**：

```
在线音乐
├── tt-music（外部服务，有搜索，1 天能上）
├── core-lx（本地模块，有搜索，2-4 周，不依赖任何服务器）
└── 用户脚本（core-lx 内，只出直链）
```

共用同一个 `MediaProvider` 接口，谁先上都不影响别人。

---

## 十、一句话

**能做，而且比想象的省 —— 洛雪的搜索核心只有 10 个 shim 的距离，我已经跑通了五个平台。**
真正的工作量不在「理解洛雪」，在「造一个够用的 RN 最小运行时」：桥要稳、全局要补全、
QuickJS 里要能跑（Node 能跑不等于 QuickJS 能跑）。

**建议**：先做 `docs/70` 的用户脚本（一周内见效），再做本文的 musicSdk（两到四周），
tt-music 作为过渡保留。
