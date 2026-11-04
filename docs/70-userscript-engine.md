# 70 · 洛雪自定义源机制抽象成独立模块：可行性（照抄官方）

> 2026-11-04 · 依据 `lyswhut/lx-music-mobile` 官方源码（Apache-2.0）
> 本文是**可行性论证 + 照抄清单**，不含代码改动

---

## 零、结论

**可行，而且不用手搓 —— 官方就是这么做的，照着搬就行。**

| 问题 | 答案 |
|---|---|
| 能脱离 tt-music 服务器吗？ | ✅ **能**。tt-music 只是这套机制的**一个宿主**，不是机制本身 |
| 需要自己设计接口吗？ | ❌ 不用。官方 preload 已把契约定死，**原样拷** |
| 需要自己实现加密吗？ | ❌ 不用。官方 6 个 native 桥，Kotlin 照写（`javax.crypto`） |
| 需要自研 JS 引擎吗？ | ❌ 不用。官方用 `wang.harlon.quickjs:wrapper-android:2.4.0` |
| 工作量 | 3～5 天（不是从零造，是搬运 + 接线） |

**我实测验证过**：把官方 `user-api-preload.js` 原样拿来 + 一个真实源脚本，
在一个**完全没有 tt-music** 的宿主里跑，初始化成功、注册 5 个源、真的发出了 HTTP 请求。
（细节见第四节）

---

## 一、官方到底怎么做的

`lyswhut/lx-music-mobile`（Apache-2.0）里，自定义源只有 **3 个文件、903 行**：

| 文件 | 行数 | 作用 |
|---|---|---|
| `android/app/src/main/assets/script/user-api-preload.js` | **594** | 定义 `globalThis.lx` 全套 API，跑在 JS 引擎里 |
| `.../userApi/QuickJS.java` | 220 | 引擎封装 + 6 个 native 桥 + 生命周期 |
| `.../userApi/UserApiModule.java` | 89 | RN 桥（我们不需要，直接 Kotlin 调） |
| 附：`crypto/AES.java`、`crypto/RSA.java` | ~110 | 加解密实现 |

**引擎是 QuickJS（JNI）**，`JavaScriptThread.java` 里明确：

```java
import com.whl.quickjs.wrapper.QuickJSContext;
private QuickJS javaScriptExecutor;
```

依赖一行：

```gradle
implementation 'wang.harlon.quickjs:wrapper-android:2.4.0'   // Apache-2.0
```

**为什么是 QuickJS 而不是 Rhino**：脚本里大量用 `?.`（六音 16 处、全豆要 114 处）、
`??`、`async`、模板字符串 —— 是 ES2020 语法。Rhino 不支持可选链，直接出局。

---

## 二、完整接口契约（照抄这一节就够）

官方 preload 最终暴露 `globalThis.lx`，形状如下：

### 事件常量

```js
lx.EVENT_NAMES = { request: 'request', inited: 'inited', updateAlert: 'updateAlert' }
```

### 三个方法

| 方法 | 签名 | 说明 |
|---|---|---|
| `lx.on(name, handler)` | `handler({source, action, info}) => Promise` | 只能注册 `request`；**必须返回 Promise** |
| `lx.send(name, data)` | `inited` / `updateAlert` | `inited` 只能发一次 |
| `lx.request(url, opts, cb)` | `opts = {method, headers, body, form, formData, binary, timeout}` | 返回一个 `abort()` 函数 |

### 元信息

```js
lx.version            // '2.0.0'（API 版本）
lx.env                // 'mobile' | 'desktop'
lx.currentScriptInfo  // { name, description, version, author, homepage, rawScript }
```

### 工具方法（全部要 native 桥）

| API | 签名 | Android 侧实现 |
|---|---|---|
| `utils.crypto.md5(str)` | → hex 字符串 | `MessageDigest("MD5")` |
| `utils.crypto.aesEncrypt(buf, mode, key, iv)` | mode = `aes-128-cbc` / `aes-128-ecb` | `Cipher` + `PKCS7Padding` / `NoPadding` |
| `utils.crypto.rsaEncrypt(buf, key)` | key 是 PEM body（去头尾） | `Cipher` + `RSA/ECB/NoPadding` |
| `utils.crypto.randomBytes(n)` | → `Uint8Array` | **纯 JS 实现**（官方就是 `Math.random`） |
| `utils.buffer.from(input, enc)` | enc = `base64`/`hex`/`utf8` | 桥 + JS |
| `utils.buffer.bufToString(buf, fmt)` | fmt = `base64`/`hex`/`utf8` | 桥 + JS |

### 源注册（`send(inited)` 的 payload）

```js
send(EVENT_NAMES.inited, {
  sources: {
    wy: { name:'网易云音乐', type:'music', actions:['musicUrl'], qualitys:['128k','320k','flac','flac24bit'] }
  }
})
```

平台 key 只允许：`kw / kg / tx / wy / mg / xm / local`。
**官方会再过滤一遍**（`supportQualitys` + `supportActions` 白名单），脚本声明的超集会被裁掉。

### 请求/响应（最关键的循环）

```
原生 → JS：__lx_native__(key, 'request', JSON{requestKey, data:{source, action, info}})
JS   → 原生：__lx_native_call__(key, 'response', JSON{requestKey, status, result|errorMessage})
```

**回调返回值校验（官方写死的，照抄能挡掉脏数据）**：

| action | 校验 |
|---|---|
| `musicUrl` | 必须是 string、≤2048、`^https?:` |
| `pic` | 同上 |
| `lyric` | 必须是 `{lyric, tlyric?, rlyric?, lxlyric?}`，lyric ≤51200 |

---

## 三、官方做过的安全加固（必须一起抄）

preload 里有一整套防脚本乱来的措施，**这些是踩过坑才有的**：

| 措施 | 位置 | 为什么 |
|---|---|---|
| 禁用 `eval` | `globalThis.eval = () => { throw }` | 防动态执行 |
| 禁用 `Function` 构造器 | Proxy 拦 apply/construct | 同上 |
| 冻结 `globalThis.lx` | `freezeObject` | 防脚本改 API |
| 冻结全部内置属性 | `freezeObjectProperty(globalThis)` | 防原型污染（`Array.prototype.push = ...`） |
| 保留三个例外 | `Function.prototype.toString` / `toLocaleString` / `Object.prototype.toString` | 否则脚本自己会坏 |
| 参数长度上限 | `checkLength(str, 1MB)` / `checkLength(data, 2MB)` | 防内存炸弹 |
| `Function.prototype.toString` 改写 | 返回 `[native code]` | 防脚本探测宿主 |

**抄的时候注意一个坑**（我实测踩到了）：`freezeObjectProperty` 会递归冻结 `globalThis` 上的**所有**属性。
如果宿主把自家的 `Error` / `Promise` 注进同一个 realm，会被一起冻住，导致宿主自己的 HTTP 库崩。
**正确做法：只注入原生桥函数，其余内置对象交给 JS 引擎自己的 realm。**

---

## 四、实测证据（脱离 tt-music 跑通）

我把官方的 `user-api-preload.js` **原样**拿来，配一套 Kotlin 等价的原生桥（Node 版），
在 `.226` 上用纯 Node 跑：

```
########## 全豆要[聚合音源] ##########
=== 1. 脚本初始化 ===
  status: true
  注册源: kw, kg, tx, wy, mg
    wy: actions=[musicUrl] qualitys=[128k,320k,flac]
=== 3. 脚本发出的 HTTP 请求 ===
  GET https://music-api.gdstudio.xyz/api.php?...
  GET https://api.huibq.com/api/url/wy/509781655/320k
  GET https://oiapi.net/api/Music_163?id=509781655
```

**全程没有 tt-music 参与。** 说明：

1. 机制 = 一个 JS 脚本 + 一个约 600 行的注入层，**与服务器无关**
2. 官方 preload 是自包含的（只依赖那 6 个 native 函数）
3. 我们自己的宿主能完整替代 tt-music 的角色

### 另一条证据：脚本只需要 13 个函数

我用 Proxy 包住注入对象，记录脚本实际碰过的每一个属性：

| 脚本 | 实际用到的能力 |
|---|---|
| **全豆要**（31KB） | 只要 `EVENT_NAMES` / `on` / `send` / `request` —— **4 个** |
| **六音**（333KB，混淆） | 上面 4 个 + `utils.buffer.from/bufToString` + `utils.crypto.aesEncrypt/md5/randomBytes/rsaEncrypt` + `env`/`version`/`currentScriptInfo` —— **13 个** |

**没有任何一个脚本用 `require` / `process` / `Buffer` / `fetch` / DOM。**
所以「必须跑在 Node 里」是个误会。

---

## 五、模块设计

### 新模块 `core-userscript`

```
core-userscript   ★新   Android 模块
├── assets/lx-preload.js          官方 594 行原样拷（Apache-2.0，保留文件头）
├── LxScriptEngine.kt             引擎生命周期：create / load / call / destroy
├── NativeBridge.kt               6 个桥：b64 / b642buf / md5 / aes / rsa / setTimeout
├── Crypto.kt                     AES / RSA / MD5（照抄官方 AES.java / RSA.java）
├── ScriptHttp.kt                 替脚本发 HTTP（要透传 headers/cookie）
├── ScriptRuntime.kt              事件循环：request 超时、abort、并发
└── ScriptInfo.kt                 头部注释解析（@name/@version/...）
```

依赖：

```
core-userscript → core-protocol（要 MediaTrack）
core-userscript → core-diagnostics（脚本加载/调用失败要进诊断）
core-userscript → wang.harlon.quickjs:wrapper-android:2.4.0
```

**为什么不塞进 `core-protocol`**：QuickJS 是 Android 原生库（JNI + .so），
`core-protocol` 必须保持纯 JVM 可 publish。这和 `docs/69` 定的判据 1 一致。

### 接进内容源

```
core-protocol/mediasource/LxSourceProvider.kt   实现 MediaProvider
        ↓ 内部调用
core-userscript/LxScriptEngine
```

这样对上层完全透明：`docs/57` 的验收判据（加新源只改一个文件）依然成立。

**注意**：`LxSourceProvider` 只能拿 `musicUrl`（解析直链），**搜不到歌**。
搜索要么另接一个接口，要么用脚本的 `musicSearch` action（只有 `qsvip`/`local` 支持）。
所以「按歌名搜」这件事，脚本机制本身不提供 —— 这是**和 tt-music 的本质差别**。

---

## 六、照抄清单（可直接施工）

| # | 抄什么 | 从哪 | 改什么 |
|---|---|---|---|
| 1 | `user-api-preload.js` | 官方 assets | **原样拷**，只改文件头加来源声明 |
| 2 | `AES.java` / `RSA.java` | 官方 `crypto/` | 翻成 Kotlin（`javax.crypto` 一字不差） |
| 3 | 6 个 native 桥 | `QuickJS.createEnvObj()` | 翻成 Kotlin |
| 4 | 引擎生命周期 | `QuickJS.java` | 去掉 RN 依赖，改 Handler → Coroutine |
| 5 | 头部注释解析 | 官方 `parseScriptInfo` | 照写正则 |
| 6 | 返回值校验 | preload `handleRequest` | **原样搬**（已经写好了） |
| 7 | 安全加固 | preload 尾部 | **原样搬**（别省） |
| 8 | 引擎依赖 | `build.gradle` | `wang.harlon.quickjs:wrapper-android:2.4.0` |

**不抄的**：`UserApiModule.java`（RN 桥）、`JsHandler`/`UtilsEvent`（RN 事件）、
`JavaScriptThread`（RN 的 HandlerThread 模型，我们用自己的协程）。

---

## 七、License 与合规

| 项 | 结论 |
|---|---|
| lx-music-mobile 许可 | **Apache-2.0**（可商用、可修改、可闭源分发） |
| 义务 | 保留 LICENSE + NOTICE，修改过的文件标注「已修改」 |
| 源脚本本身 | **不打包、不内置** —— 用户自己导入（和洛雪一样） |
| 合规边界 | 抽象的是**执行机制**，不是音源；没有内置任何平台凭据或破解逻辑 ✅ |

**这条守住了项目原有的红线**（`docs/06`：不内置平台音源、不绕过付费授权）。
我们做的是「一个能跑用户自备脚本的引擎」，和 lx-music 的定位一致。

---

## 八、工作量与取舍

| 方案 | 工期 | 依赖 | 能否搜索 | 多平台 |
|---|---|---|---|---|
| **A. 接 tt-music 客户端** | 0.5～1 天 | `.124` 那台机器（单点） | ✅ 五个平台 | 只有网易云能用 |
| **B. 自建脚本引擎**（本文） | **3～5 天** | **无外部依赖** | ❌ 只能解析直链 | 取决于用户装什么脚本 |

**A 和 B 不冲突，可以并存** —— 两者都实现同一个 `MediaProvider` 接口。

### 建议路线（最稳）

```
第 1 步：先接 tt-music（1 天）        ← 快速验证产品形态、界面、投送链路
第 2 步：再上脚本引擎（3-5 天）      ← 摆脱 .124 单点
第 3 步：两者共存，界面上让用户选
```

**先 A 后 B 的理由**：A 一天就能让「在线曲库」真的可用（而且有搜索）；
B 要三五天，且**没有搜索能力**（脚本只给直链）。先上 A 才能尽快验证产品是否成立。

### 但如果你只想要 B

也可以，代价是：
- 界面上**不能有搜索框**（脚本给不了搜索结果）
- 得靠「粘贴分享链接/歌单」或者别的搜索源来拿 `songmid`
- 三五天后才能第一次看到在线音乐真的播起来

---

## 九、风险（诚实列）

| 风险 | 影响 | 缓解 |
|---|---|---|
| **脚本会过期** | 平台改加密 → `Unknown cipher`（六音现在就是这样） | 和洛雪一样，靠用户换脚本；界面要能显示「脚本失效」 |
| **APK 变大** | AAR 2.27MB；只留 arm64 约 **+1.3MB** | 用 ABI split / `abiFilters` 只打 arm64 |
| **脚本是任意 JS** | 安全面 | 官方那套加固必须全抄（eval/Function 禁用 + 全局冻结） |
| **混淆脚本难调试** | 出问题看不出原因 | 诊断段要记：脚本名/版本/action/耗时/错误原文 |
| **QuickJS 无 JS 调试器** | 开发慢 | 官方 `openDevTools` 在移动端是禁用的，我们也没有 |
| **没有搜索** | 产品形态受限 | 见第八节，或另配搜索源 |

---

## 十、一句话回答用户

**能，而且官方已经把这条路走通了 —— 594 行 preload + 6 个原生桥 + 一个 QuickJS 依赖，
Apache-2.0 随便抄。** 我实测把官方 preload 原样跑在脱离 tt-music 的宿主里，
脚本初始化、注册源、发 HTTP 全部正常。

**唯一要想清楚的是**：这条路**没有搜索能力**（脚本只负责把 songmid 变成直链）。
所以更现实的是「先接 tt-music 拿搜索，再上脚本引擎拿自主性」，两者共用同一个接口。
