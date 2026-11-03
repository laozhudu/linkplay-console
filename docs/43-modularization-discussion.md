# 43 · 「一切皆模块」可行性讨论（含实测数据）

结论先说：**方向对，但「一切皆模块」要拆成两件事，其中一件你现在缺、另一件你不需要。**

| 你要的东西 | 靠什么实现 | 现状 |
|---|---|---|
| **一致性**（同一个概念只有一个入口） | 单一入口 + Kotlin `internal` + 编译期强制 | ⚠️ 缺（docs/41 那 7 条全是这个病） |
| **可拼装**（加功能不改老代码） | 接口 + 注册表 + 装配层 | ⚠️ 半成品（`MediaProvider` 接口写了，app 里 **0 处使用**） |
| **编译速度 / 依赖方向可见** | 拆 Gradle 模块 | ✅ 已经够用（3 模块，CI 1m45s） |

下面每一条都基于我在这份代码里实测的数字，不是架构教科书。

---

## 一、先看现状：你的项目其实没那么"不模块化"

### core-protocol 已经是低耦合的

我统计了 `core-protocol` 里 67 个文件、26 个包之间的 import 关系：

```
被依赖最多（入度）= 真正的地基
  ROOT（Json/LinkPlayStatus/LinkPlayCommands/DeviceHttpClient）  10
  upnp                                                            8
  queue                                                           4
  player                                                          3
  playqueue / session                                             1 / 1

依赖别人最多（出度）= 耦合重灾区
  session   5      snapshot  4      playback 3      player 3
```

**67 个文件，跨包 import 一共只有约 30 条。** 这个数字非常低 ——
说明协议层内部各包基本互不认识，只是共享一个很小的地基（`ROOT`）。

换句话说：**你最担心的"一致性没统一"，在协议层已经不是模块边界的问题了。**

### 真正的巨石是两个类，不是模块

```
app/src/main/.../ConsoleViewModel.kt     2027 行   76 个函数
app/src/main/.../MainActivity.kt         1402 行
app/src/main/.../playback/PlayerScreen.kt 842 行
app/src/main/.../playback/PlaybackViewModel.kt 841 行
```

`ConsoleViewModel` 的 76 个函数按关注点分：

| 关注点 | 函数数 | 例子 |
|---|---|---|
| 设备发现/探活/记录 | ~15 | `scan` `probeSaved` `identify` `add` `forget` `touch` |
| 设备信息快照 | ~6 | `applySnapshot` `rendererByIp` `probeGenericDmr` |
| 多房间 | ~12 | `joinSlave` `kickSlave` `setSlaveVolume` `renameSlave` `toggleMask` |
| 音量 | ~5 | `setCardVolume` `setGroupVolume` `adjustVolumeByKey` |
| 播放控制 | ~4 | `playAll` `pauseAll` `togglePlayAll` `toggleDevicePlay` |
| 预设/固件 | ~3 | `pressPreset` `checkFirmware` `startFirmwareUpdate` |
| 诊断 | ~5 | `registerDiagnostics` `diagnosticsReport` `lastCrash` |
| 会话恢复 | ~3 | `restoreLastDevice` `restoreLastQueue` `reloadSaved` |

**这是一个 god object，8 个关注点挤在一个类里。**
拆成 Gradle 模块解决不了它 —— 它需要的是**拆类**，不是拆模块。

---

## 二、关键发现：docs/41 那 7 个一致性问题，一个 Gradle 模块都不用加就能修

我把上一轮审计的 7 条逐条对照"拆模块能不能防住"：

| # | 问题 | 修法 | 需要 Gradle 模块吗 |
|---|---|---|---|
| 1 | uuid 存储侧 3 套归一化 | `SavedDevice` 构造函数私有 + 只有 `DeviceIdentity.of()` 能造 | ❌ 不需要 |
| 2 | 队列 owner 只覆盖 3/13 处 | `QueueBus.restore(deviceIp, …)` 强制传参 | ❌ 不需要 |
| 3 | 音量 3 条路 | `DeviceVolumeControl` 唯一入口 + `internal` | ❌ 不需要 |
| 4 | 能力档两份 | 并进 `DeviceSnapshot` | ❌ 不需要 |
| 5 | 错误文案直出异常 | `UserMessage.of(t)` 唯一翻译点 | ❌ 不需要 |
| 6 | 时长格式化 4 套 | 唯一实现 + 删掉私有副本 | ❌ 不需要 |
| 7 | "未知"语义不统一 | 类型上区分（`Int?` vs `0`） | ❌ 不需要 |

**全部靠"单一入口 + 编译期强制"就够了。**

这是这次讨论最重要的一条：
**你要的「一致性」，来源是「同一个概念只有一个出口」，不是「拆成很多模块」。**
拆模块只是把这条规则**物理上强制**得更彻底 —— 但如果代码里根本只有一个入口，
单模块一样一致；反之，拆成 20 个模块而每层都能自己造 uuid，照样不一致。

---

## 三、你已经有正确的直觉，但没接上：`MediaProvider` 是个空转的接口

`core-protocol/mediasource/MediaProvider.kt` 的注释写得非常准：

> 之前"歌从哪来"是写死在几个入口里的（NAS / 手机本地 / 电台 / 歌单），
> 于是每加一个源就要改一处播放逻辑。把"取列表"和"取直链"抽出来之后，
> **加源 = 加一个实现类，播放链路（PlayQueue / m3u / RelayMediaServer）一行不动。**

这就是你说的"模块化"的标准定义。但实测结果：

```
grep -rn 'MediaProvider' --include=*.kt app/src/main
→ 0 处命中
```

**接口在，实现类也在（`SubsonicClient : MediaProvider`），但 app 层从没用过这个接口。**
`OnlineViewModel` 直接 `import SubsonicClient` + `SubsonicConfig`，硬编码。

所以现在的真实状态是：**抽象写了，装配没做。**
这不是模块化过度，是模块化**没做完** —— 差的就是"注册表 + 装配"这一层。

而你刚刚在诊断页上已经把这一层做对了：

```kotlin
// DiagnosticsHub：谁有状态谁注册，app 不认识具体实现
DiagnosticsHub.register("playback", "播放") { … }
```

**同一套模式搬到内容源，就是你要的「一切皆模块」。**

---

## 四、官方指南的判据（拿来砍掉不该拆的）

Android 官方 *Common modularization patterns* 给出「API 与实现要不要分开」的四条判据，
我逐条对照你的项目：

| 官方判据 | 你的项目 | 结论 |
|---|---|---|
| **Diverse capabilities**（同一件事有多种实现） | 内容源有：Subsonic / NAS / 本地 / 电台 / 未来的在线搜索 | ✅ **该分** |
| **Multiple applications**（多端共用） | 只有 Android 一个端（core-protocol 已可发布 JAR，够了） | ❌ 不必 |
| **Independent teams**（多人并行） | 单人开发 | ❌ 不必 |
| **Large codebase**（大到需要切块理解） | 23K 行，`ConsoleViewModel` 2027 行确实超了 | ⚠️ 该拆的是类 |

官方还有两句直接相关的：

> **"Android modules come with overhead, preferably, you'd want to use the Kotlin or Java kind as much as possible."**
> （Android 模块有额外开销，尽量用纯 Kotlin/Java 模块。）

> **"There is no single modularization strategy that fits all projects."**

所以按官方口径，你的项目**够不上"一切皆模块"的规模**，
但**内容源这一块正好命中"多种实现"** —— 这是唯一该做成插件的地方。

---

## 五、我的建议：不追"一切皆模块"，追"六个模块 + 三个强制规则"

### 模块地图（从现在的 3 个到 7 个，不是 20 个）

```
:core-model          ← 纯 Kotlin 数据 + 契约（MediaTrack / DeviceSnapshot / QueueItem / 各接口）
   ↑
:core-protocol       ← 已有，协议实现（LinkPlay httpapi / UPnP / PlayQueue）
   ↑
:cap-subsonic        ← 内容源实现（现在硬编码在 OnlineViewModel 里）
:cap-radio           ← 电台（接入 radiostation-discovery）
:cap-localmedia      ← 本地曲库 + NAS
   ↑
:app                 ← 只做装配 + UI（不出现任何具体实现类名）
```

**关键不在数量，在于 `:app` 里不出现具体实现**。
现在 `OnlineViewModel` 里 `import SubsonicClient` 就是违规 —— 拆完模块后
它只能 `import MediaProvider`，实现由 `:app` 的装配层注入。

### 三条强制规则（比模块数量重要得多）

**规则 1：每个概念只有一个构造入口**

```kotlin
// 现在（3 处各自造 uuid，所以会重影）
SavedDevice(uuid = found.udn?.removePrefix("uuid:") ?: ip, …)

// 改成（构造函数 private，只有工厂能造）
data class SavedDevice private constructor(val uuid: String, …) {
    companion object {
        fun of(udn: String?, ip: String) = SavedDevice(UuidMatch.key(udn) ?: ip, …)
    }
}
```

编译期就堵死，不靠自觉 —— 这才是"模块化"真正的收益。

**规则 2：写状态的入口必须带归属**

```kotlin
// 现在：restore() 内部读 currentOwner（可能是上一台设备）
QueueBus.restore(items, index, title)

// 改成：编译期强制传
QueueBus.restore(deviceIp, items, index, title)
```

**规则 3：能力通过注册表发现，不通过 import**

```kotlin
// 内容源
ProviderRegistry.register(SubsonicProvider(config))   // 在 :app 装配层
// 界面侧
ProviderRegistry.all().filter { it.configured }       // 只认识接口

// 诊断（已经这么做对了）
DiagnosticsHub.register("playback", "播放") { … }
```

### "底座也是模块" —— 这条我建议分开看

| 理解 | 判断 |
|---|---|
| 底座做成**独立 Gradle 模块**（可独立编译、独立测试、可发布 JAR） | ✅ **已经是了**，而且做得对：core-protocol 是纯 JVM，609 单测跑得飞快，`publishMavenPublication` 能出 JAR |
| 底座做成**可替换的插件**（每样东西都有接口 + 多实现） | ❌ **不建议**。按官方四条判据，你不会有第二个 `Json` 实现、第二个 `LinkPlayCommands`。为"可替换"付出的代价是每个类型都要多一层接口，收益为零 |

**底座的正确目标是「可独立测试」，不是「可替换」。** 你现在已经做到了。

---

## 六、成本估算（诚实标注：这是我按经验估的，没实测）

| 方案 | 模块数 | CI 构建（估） | 主要代价 |
|---|---|---|---|
| 现状 | 3 | 1m45s（实测） | — |
| 我的建议 | 7 | 2m~2m30s（估） | 装配层多一个文件；跨模块重构要改 2~3 处 |
| 一切皆模块 | 20+ | 4m~6m（估） | 每个新功能都要动 `settings.gradle` + 建目录 + 写 build 脚本；Compose 预览变慢；改一个字段要跨 3 个模块 |

`gradle.properties` 里已有 `parallel=true` + `caching=true` + `nonTransitiveRClass=true`，
所以多模块不会像裸配那么惨，但**每加一个 Android library 模块仍有固定开销**。

对单人项目，20 个模块的日常成本（改字段要开 3 个文件）大概率**大于**收益。

---

## 七、真正该拆的是 `ConsoleViewModel`（2027 行 / 76 函数）

这比拆 Gradle 模块收益大得多，而且**不需要动构建**。按关注点拆成 4 个类：

```
ConsoleViewModel（保留 ~200 行）
├── 只做状态聚合 + 委托，界面无感
├── DeviceRegistry    设备发现/探活/记录（~400 行）
├── GroupController   多房间/组网/声道/屏蔽（~500 行）
├── DeviceControl     音量/播放/预设/固件（~400 行）
└── SessionRestore    会话恢复（~150 行）
```

为什么值得先做这个：

- 现在改一个"组网"的小逻辑，要在 2027 行里找，而且**容易碰到无关代码**（这几轮已经出过几次）
- 拆完每个类 300~500 行，`ConsoleViewModel` 变成纯装配 —— 和你想要的"底座 + 模块"是同一件事，只是发生在**类**层面而不是模块层面
- 不动 `settings.gradle`、不动 CI、不动依赖图，风险最低

---

## 八、落地顺序建议

### P0（先做，不需要你决策）

1. **一致性审计 P1**（uuid 单一入口 + 队列 owner 强制）—— 这是真 bug，且正好是规则 1/2 的样板
2. **拆 `ConsoleViewModel`** —— 2027 行拆成 4 个类，界面零改动

### P1（需要你点头）

3. **内容源插件化**：`:cap-subsonic` 抽出 + `ProviderRegistry` + `OnlineViewModel` 只依赖接口
   顺带把电台模块接进来 —— 这样"加电台"就是"加一个 provider"，不改任何播放代码
4. **诊断加请求追踪 + 一键自检**（上一轮留的坑）

### P2（等有第二个端/第二个人再说）

5. 拆更多 Gradle 模块、API/impl 分离、convention plugins

---

## 九、需要你定的三件事

1. **模块数量**：接受 7 个（我建议），还是要试 20 个"一切皆模块"？
   —— 我倾向 7 个，理由见第六节的成本对比；但如果你想亲自体感一下多模块的日常成本，
   我可以先拿 `:cap-radio` 做一个试点，一周后再判断要不要推广。

2. **拆类先做还是模块先做**：我建议**先拆 `ConsoleViewModel`**（P0），
   因为它是当前所有"改 A 碰 B"的根源；模块化放到内容源插件化时一起做。

3. **底座要不要动**：我建议**不动**。core-protocol 的耦合度（67 文件 / ~30 条跨包 import）
   已经很低，609 单测纯 JVM 跑得很快，可发布 JAR —— 它已经是合格的底座。

---

## 附：这次讨论用到的实测命令

```bash
# 模块规模
find core-protocol/src/main -name '*.kt' | wc -l          # 67
find app/src/main -name '*.kt' | wc -l                    # 52

# 包间耦合（67 文件，跨包 import 约 30 条）
grep -rh '^import io.github.laozhudu.linkplay.protocol' core-protocol/src/main | sort | uniq -c | sort -rn

# 巨石
wc -l app/src/main/.../ConsoleViewModel.kt                # 2027
wc -l app/src/main/.../MainActivity.kt                    # 1402

# 空转的接口
grep -rn 'MediaProvider' --include=*.kt app/src/main      # 0 命中
grep -n 'Subsonic' app/src/main/.../online/OnlineViewModel.kt | head   # 硬编码
```
