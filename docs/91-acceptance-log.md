# 验收台账（累计）

> **给验收方看的。** 每一轮施工结束追加一条，不删不改历史。
> 每条都写清：改了什么 / 怎么复核 / 没验什么 / 遗留。
>
> 复核方式：下面每条命令都可以直接跑。**只有跑过的才算验过。**
>
> 维护规则：
> 1. 一轮一条，倒序追加在文末（新的在下）
> 2. 「没验」栏必须写，空着等于假装验过
> 3. 与施工图有偏差的，就地写出来，不悄悄改图纸

---

## 复核基线（2026-11-05，提交 `49ce277` / 版本 `0.9.143`）

```bash
cd /root/.hermes/workspace/linkplay-console
git log -1 --format='%h %s'
grep -rho '@Test' app/src/test core-*/src/test | wc -l          # 期望 1036
python3 scripts/check-init-order.py                              # 通过
python3 scripts/check-diagnostics-labels.py                      # 通过（66 处）
python3 scripts/check-timing-names.py                            # 通过（13/13）
python3 scripts/check-single-dispatch.py                         # 通过
python3 scripts/check-no-global-optin.py                         # 通过
python3 scripts/check-output-switch.py                           # warn：2 处
python3 scripts/check-profile-in-ui.py                           # warn：1 处
```

**护栏三道门现状**

| 门 | 位置 | 状态 |
|---|---|---|
| 编译期 `@SingleEntryPoint` | `playback/guards/SingleEntryPoint.kt` | ERROR 级，已反向验证 |
| `check-single-dispatch.py` | CI | error，已反向验证 |
| `check-no-global-optin.py` | CI | error，已反向验证 |

---

## 第二轮 · docs/86 第一批（1.1 / 1.2 / 1.3）

| PR | 内容 | 验收 |
|---|---|---|
| #130 | 删 NAS 三段降级 | `grep` 下发点 -1 |
| #131 | 本地队列走唯一入口 | 同上 |
| #132 | `PlaybackRouter` 唯一分派（`playQueue`） | 全仓直调只剩 Router 内部 2 处 |

**偏差**：#130 净减只有 13 行（删 57 加 25）—— 收益在"只有一处实现"，不在行数。
**行为变化**：NAS 起播循环模式固定为 `LOOP_ALL`（原来跟随 NAS 页设置）。
**没验**：真机行为差异（`docs/86` 只要求结构收敛）。

## 第三轮 · compose-bom（#134 / #135）

| PR | 内容 | 验收 |
|---|---|---|
| #134 | docs/87 BOM 兼容性实测 | 文档 |
| #135 | `composeBom 2024.12.01 → 2026.03.01` | 见下 |

**结果**：compose 1.7.6 → **1.10.6**，material3 1.3.1 → **1.4.0**，AGP / Kotlin / compileSdk **全未动**。

**偏差（重要）**：我第一轮直接跳 `2026.09.00`（compose 1.12.1）撞墙报 `requires AGP 9.1.0`，
误判成"必须升 AGP"。docs/87 查 AAR 元数据找到分水岭在 **compose 1.12.0**，
1.11.4 及以下都只要 AGP 8.6.0。**下次遇到依赖升级撞墙，先读 AAR 元数据，不要往上试。**

**没验**：界面肉眼（字号/间距/圆角/颜色）—— 需真机，用户未回传。

## 第四轮 · 触感 + 下发路径（#133 / #139）

| PR | 内容 | 验收 |
|---|---|---|
| #133 | 触感加在 `AppCard`（唯一入口，19 处覆盖）+ 底部导航 | 编译 + 1036 |
| #139 | 堵第 4 条下发路径（`PlaybackViewModel:501`） | `grep` 下发只剩 `QueueStarter.kt` |

**#133 的设计点**：触感放 `AppCard` 而不是逐页加 —— 逐页加必漏，漏的那几处会显得"点了没反应"。

**没做（如实）**：docs/88 要求的三条降级单测（T6）。
**原因**：`startQueueOnDevice` 内部直连 6 个静态依赖，其中 `SessionStore.of(context)` /
`QueuePlaybackService.start` 要 Android 环境，纯 JVM 测不了；要测必须加注入点 + 引入
Mockito/Robolectric。**引测试框架是独立决定，没顺手加。**
**结论**：硬塞一个测不到真实路径的测试，锁的是错的实现 —— 比没有测试更坏。

**没验**：投 `.175` 电视盒子日志是否不再有「PlayQueue 未接受」（需真机）。

## 第五轮 · S0 护栏（#141 / #142）

| PR | 工序 | 内容 |
|---|---|---|
| #141 | S0.1–S0.3 | `@SingleEntryPoint` / `@OutputSwitch` + 三处打标记 + 反向验证 |
| #142 | S0.4–S0.5 | 四个 CI 检查（2 error + 2 warn） |

**反向验证记录（三次，全部做真）**

| 验证 | 做法 | 结果 |
|---|---|---|
| 编译期门禁 | NasViewModel 加一行 `DevicePlaylistGateway.push` | `e: ...这是唯一入口的内部原语...` ✅ |
| 下发检查脚本 | NasViewModel 加一行 `DeviceQueuePush.push` | exit 1，定位 `:573` ✅ |
| 全局开关检查 | `app/build.gradle.kts` 加 `-opt-in=...` | exit 1，定位 `:161` ✅ |

**偏差**：docs/90 写「`*Screen.kt` 里 `DeviceProfile.` 有 15 处」，**实测 1 处**
（`HomeScreen.kt:303`，且是 `?: UNKNOWN` 取默认值，不是按型号分支）。脚本按实测写。

**零行为改动**：两轮都不改运行时逻辑，1036 测试一条没改。

---

## 累计真机欠账（**都没验，别当验过**）

| 版本 | 要验什么 | 状态 |
|---|---|---|
| 0.9.125 | 通知栏 / 耳机键 / 锁屏 | 未验 |
| 0.9.122 | 红外有没有发射器 | 未验 |
| 0.9.133 | 六源本机播 + 两边不一起响 | 未验 |
| 0.9.139 | 投 `.175` 下发路径 | 未验 |
| 0.9.136 | BOM 升级后界面肉眼 | 未验 |
| 0.9.133 | 触感手感 | 未验 |

## 设备观察

| 设备 | 项 | 状态 |
|---|---|---|
| doss3 `.153` | 电量 60% → 50% → 30%（插着电） | 持续下滑，**别当主音箱长时间用** |
| doss2 `.181` | 电量 20% → 10% → 100% | 反复，未下结论 |
| `.175` 机顶盒 | Get 类全通，**Set 类没测过** | 能否出声未知 |

---

## 第六轮 · S1.1 意图层（`PlayIntent`）

**工序**：docs/90 S1.1。**改动**：1 新 + 4 改。

| 文件 | 动作 |
|---|---|
| `core-protocol/.../playback/PlayIntent.kt` | **新建**，13 个意图分支 |
| `core-protocol/.../voice/IntentRouter.kt` | `sealed interface VoiceIntent` → `typealias VoiceIntent = PlayIntent` |
| `core-protocol/.../voice/IntentRouterTest.kt` | 引用改 `PlayIntent.Xxx`（断言逻辑一字未改） |
| `app/.../playback/PlayerScreen.kt` | 两个 `when` 补 `SetVolume` / `SeekTo` 分支 |
| `app/.../voice/VoiceSourceStatus.kt` | 引用改 `PlayIntent.Xxx` |

**为什么改这条**：「下一首」在界面 / 悬浮窗 / 语音各走各的，共 4 条路径 —— 因为意图层只给语音开了口。
**意图不关心它是说出来的还是点出来的。**

### 与施工图的一处偏差（technical，重要）

docs/90 §3.2 说「用 `typealias` 过渡是为了让 613 个单测不用改」。

**实测不行**：Kotlin 的 `typealias` 不能用来访问嵌套成员 ——
`VoiceIntent.Next` 经别名访问会报 `Unresolved reference 'Next'`。

所以改了 4 个文件的引用（`VoiceIntent.` → `PlayIntent.`）。**但断言逻辑一个字没改**，
613 个用例全绿，`IntentRouterTest` 的规则判断一条没动 —— 这才是那条要求的本意。

### 编译期强制生效了一次（这工序的真正价值）

`PlayIntent` 比原 `VoiceIntent` 多 `SetVolume` / `SeekTo`，加上去立刻报：

```
e: PlayerScreen.kt:866:85 'when' expression must be exhaustive.
   Add the 'is SeekTo', 'is SetVolume' branches or an 'else' branch.
```

**这就是 sealed interface 的意义**：新增意图时编译器逼你处理，而不是运行时静默落到 else 变成「点了没反应」。

两个分支现在只做回执（语音说不出"音量到 40"这种定值），执行落地在 S1.2 的 dispatch ——
代码里写明了"等统一执行点"，没有假装接上。

### 验证

```bash
./gradlew :core-protocol:test          # 613 用例 0 失败
./gradlew :app:assembleDebug           # BUILD SUCCESSFUL
python3 scripts/check-init-order.py    # 通过
python3 scripts/check-single-dispatch.py # 通过
# 全量 1036 用例 0 失败
```

### 没验

- 语音说「下一首」真机行为（**本轮零行为改动**：意图类型改名，路由规则与执行都没变）
- `SetVolume` / `SeekTo` 无法执行 —— 预期，等 S1.2

### 下一道

S1.2 `PlaybackDispatcher`（唯一执行点）。前置已就绪：意图层统一了。

## 第七轮 · S1.2 唯一执行点（`PlaybackDispatcher`）

**改动**：1 新 + 1 改。

| 文件 | 动作 |
|---|---|
| `app/.../playback/PlaybackDispatcher.kt` | **新建**，14 个意图的 dispatch + `can()` + `PlaybackViewModels` 持有器 |
| `app/.../playback/PlayerScreen.kt` | `DisposableEffect` 登记 / 注销 VM |

**两条硬规矩写进类注释**：① 能力位判断在这里做，不在界面做；② 不实现播放逻辑，只做分派。

### 收进这一层的那个真 bug

`FloatingControlService` 直发 `LinkPlayCommands.nextTrack()` —— 那是 LinkPlay 私有 httpapi，
投通用 DLNA 渲染器时设备不认，而 `supportsNextPrev` **从来没被读过**。
表现：电视盒子上照样画着「下一首」，按了毫无反应。S1.3 接入后由 `can()` 决定画不画。

### 一个自己撞出来的坑（记下来免得重踩）

ExoPlayer 的 `volume` 是 **0f..1f**，设备侧是 **0..100**。第一版直接赋值 Int，编译报：

```
e: Argument type mismatch: actual type is 'kotlin.Int', but 'kotlin.Float' was expected.
```

换算是这一层的责任 —— 上层记两套刻度会出现「显示 60 实际 0.6」这种看不出来的错。

### 验证

```bash
./gradlew :app:assembleDebug            # 通过
# 全量 1036 用例 0 失败
python3 scripts/check-init-order.py     # 通过
python3 scripts/check-single-dispatch.py # 通过
grep -rn 'PlaybackRouter.of(' app/src/main | grep -v Dispatcher | wc -l   # 8
```

**那 8 处是 S1.3 的活**：界面还在直连 router，本轮只把 dispatcher 建起来并让播放页登记 VM。

### 没验

- **零行为改动**：没有任何调用点改走 dispatcher（那是 S1.3）。dispatcher 现在还没有调用者。
- 真机「电视盒子上不显示下一首」要 S1.3 接入后才验得了。

## 第八轮 · S1.3 悬浮窗接入 dispatch（真 bug 修复）

**改动**：1 文件。

| 位置 | 前 | 后 |
|---|---|---|
| `FloatingControlService:204-206` | `send(LinkPlayCommands.previousTrack()/togglePause()/nextTrack())` | `dispatch(PlayIntent.Previous/TogglePlay/Next)` |
| 新增 `dispatch(intent)` | — | `can()` 判断 + 不支持时 Toast |

### 修的是什么（这是 S1.3 的全部意义）

那三行直发 **LinkPlay 私有 httpapi**。投通用 DLNA 渲染器（电视盒子 `.175`）时设备不认，
但按钮照样画着 —— **按了什么都不发生，也没有提示**。

而「这台设备支不支持切歌」（`DeviceCapabilities.supportsNextPrev`）**从来没被读过一次**。

现在 `can()` 先判断：做不到就 Toast「这台设备不支持这个操作」，而不是静默失败。

### 验证

```bash
grep -rn 'LinkPlayCommands' app/src/main/kotlin/.../floating/   # 0 处 ✅（docs/90 验收第 4 条）
./gradlew :app:assembleDebug                                     # 通过
```

### 没做完的部分（如实记录）

docs/90 的 S1.3 要求**三个入口**都接：界面 / 悬浮窗 / 语音。
本轮只做了**悬浮窗**（真 bug 所在 + 验收硬指标）。

| 入口 | 状态 | 为什么 |
|---|---|---|
| 悬浮窗 | ✅ | 真 bug，`grep` 可验 |
| 界面 `PlayerScreen` 按钮 | ⏸ | 十几处调用要改成 suspend 上下文（`dispatch` 是 suspend），属于机械改动但面广 |
| 语音 `handleVoiceIntent` | ⏸ | 同上，且它现在是普通函数，要改成跑在 scope 里 |

**不接的两个入口目前仍走 `vm.xxx()` 直连** —— 它们本来就工作正常（不走私有 httpapi），
没有那个 bug，所以「没接」不影响正确性，只影响"唯一入口"的完整性。

### 没验

- 真机：投电视盒子看悬浮窗「下一首」是否变灰/提示（需装机）
- 真机：投音箱时悬浮窗三个按钮是否照常工作

## 第九轮 · S1.4 输出开关搬回播放页（**用户可感**）

**改动**：2 改 + 1 脚本转 error + CI。

| 文件 | 动作 |
|---|---|
| `PlayerScreen.kt` | 顶栏下方加 `OutputSwitchRow()`（内联一行，**不用浮层**，docs/37） |
| `LxSearchScreen.kt` | 删掉那组 chip（948 字符） |
| `scripts/check-output-switch.py` | **warn → error** |
| `.github/workflows/android.yml` | 去掉 `\|\| true` |

### 收益（这是 docs/86 1.3 真正的兑现）

| 项 | 前 | 后 |
|---|---|---|
| 能手机本地播的源 | 机制 5/6，**用户实际只能用 1/6** | **5/6** |
| 通知栏 / 锁屏 / 耳机键 | 大部分场景用不上 | 切「本机」就活 |
| 找开关要去的页面 | 洛雪页（跟源无关） | 播放页（本来就在放声音的地方） |

### 验证

```bash
python3 scripts/check-output-switch.py     # 通过（0 处）
grep -rn 'setOutput(' app/src/main          # 只剩 PlayerScreen + PlaybackRouter 定义
./gradlew :app:assembleDebug                # 通过
```

### 一个刻意的设计点

`OutputSwitchRow` 读的是 `PlaybackRouter.output()`（全站唯一真相），
**不是 `PlaybackViewModel` 的状态** —— 后者是"当前设备"的，本机播放时它没有意义。

### 没验（全都要真机）

- 播放页顶部多了一行，**封面和标题会不会被挤变形**
- 切「本机」后从 NAS / 本地 / 在线 / 歌单 / 洛雪 任一处播放 → 声音从手机出
- 切「音箱」→ 从音箱出，**两边不一起响**
- 洛雪页确实不再有那个开关

## 第十轮 · S1.4 输出开关搬回播放页（**用户可感**）

| 文件 | 动作 |
|---|---|
| `PlayerScreen.kt` | 顶栏下加 `OutputSwitchRow()`（内联一行，**不浮层**，docs/37） |
| `LxSearchScreen.kt` | 删掉那组 chip（948 字符） |
| `scripts/check-output-switch.py` | warn → **error** |

```bash
python3 scripts/check-output-switch.py   # 通过
grep -rn 'setOutput(' app/src/main        # 只剩 PlayerScreen + PlaybackRouter
```

**没验**：真机（封面是否被挤变形 / 六个源本机播 / 两边不一起响）。

## 第十一轮 · 输出口改成「本机 + 每台设备一个」（用户纠正）

**用户原话**：「我要一个设备一个输出口，即多少设备就多少输出口，这个说了很多次」。

原来 `enum class PlaybackOutput { Remote, Local }` —— 两个值。家里 4 台设备，
「投给谁」由**上一个页面选中的设备**隐式决定，用户在这里的显式选择被覆盖。

现在：

```kotlin
sealed interface PlaybackOutput {
    data object Local
    data class Device(val ip: String, val label: String = ip)
}
```

存储 `local` / `device:<ip>`；老数据 `remote` → `Device("")` = 跟随当前设备（**绝不回退本机**，否则手机突然外放）。

`PlaybackRouter.outputIp()`：**播放页选的设备覆盖调用方传的 ip** —— 这是行为实质改变。

```bash
./gradlew :app:assembleDebug                 # 通过
./gradlew :app:testDebugUnitTest             # 通过（输出选择测试 3 → 5 用例）
```

**没验**：真机（chip 数量 / 切到某台是否真去那台）。

**我的失职**：`docs/86`/`docs/90` 写的是"本机/音箱"两个口，我照着做，**没有回到用户原话核一遍**。
那两张图不是用户写的。

## 第十二轮 · UI 待办入档（用户要求「不许忘」）

用户 2026-11-05 指出三件事，明确「允许延后，不允许遗忘」：

| # | 待办 | 状态 |
|---|---|---|
| U1 | 底部导航重排（现在四项已不成立） | 待做，前置：S1–S3 完成 |
| U2 | 首页设备按类型隔离（DOSS 与通用 UPnP 混在一起） | 待做，前置：S2.3 能力位 |
| U3 | 红外 / 蓝牙 / WiFi 遥控的 UI 入口 | 待做，前置：功能可用 |

**载体**：`docs/92-ui-restructure-backlog.md`（含判据与前置条件）+ `docs/90` 第 9 章引用
+ `Nav.kt` 的 `Tab` 枚举锚点注释。**每轮汇报都会提到，做完一项划一项。**

## 第十三轮 · 整套 UI 重设计授权入档

**用户追加授权**：「整套 ui 都需要根据项目实际情况后再重新设计，**并且允许删除和修改**」。

这条把范围从"三件待办"扩到**整套 UI**，并明确**可以删页面、可以改交互**。

### 盘点数据（重设计的输入）

```
4 个底部格子 / 13 个二级路由 / 17 个页面 / 5,470 行
最大：PlayerScreen 964 · HomeScreen 623 · LibraryScreen 599 · NasScreen 502
最小：SourcesScreen 46（纯入口列表）
```

### 三条已确认的结构病

| 病 | 证据 |
|---|---|
| **中转页** | `SourcesScreen`(46) 和 `MyMusicScreen`(232) 点进去只是再选一次 |
| **职责重叠** | `Library`(599)/`MyMusic`(232)/`Queue`(345)/`Sources`(46) 都在回答"我有什么音乐" |
| **维度混用** | 设备=对象、播放=动作、洛雪=内容源(品牌)、媒体库=集合 —— **四格三种维度，这是"不对了"的根因** |

### 已写进去的"候选删除清单"（不是决定）

`SourcesScreen` / `MyMusicScreen` 倾向删；`QueueScreen` / `DeviceContentScreen` 倾向并入播放页；
`ProvisioningScreen` + `WpsGuideScreen` 倾向合并；`DiagnosticsScreen` 要提到可见位置。

**文档里明确标注是"候选，不是决定"** —— 真正动手前要重核，因为底层架构还会动。

### 重设计的第一步（不是画界面）

文档里定了三问：① 这页回答什么问题 ② 这答案还有别处给吗 ③ 删掉会迷路吗。
按三问过 17 页 → 得到合并/删除清单 → 再谈布局。

### 没验

本轮纯文档，零代码改动（`Nav.kt` 上一轮加的是注释）。

## 第十四轮 · S1.3 语音入口接入 dispatch

**用户原话**：「继续按施工图做」。

| 文件 | 动作 |
|---|---|
| `PlayerScreen.kt` | 语音回调改 `voiceScope.launch`；`handleVoiceIntent` 改 suspend，走 `PlaybackDispatcher` |
| `PlaybackDispatcher.kt` | 构造参数 `Application` → `Context`（调用方不必再 `as Application`） |

### 顺带接上了两个之前"占位"的意图

`SetVolume` / `SeekTo` 在 S1.1 只做回执（写的是"等统一执行点"），现在执行点存在了，**接上了**。

### 三个入口现状

| 入口 | 走 dispatch | 说明 |
|---|---|---|
| 悬浮窗 | ✅ | 上一轮，顺带修掉私有 httpapi bug |
| 语音 | ✅ | 本轮 |
| 界面按钮 | ⏸ | 十几处调用，均为 `vm.xxx()` 正常通道，无 bug |

**界面按钮暂未改**，如实记着 —— 它不走私有 httpapi，没有那个 bug，只是"唯一入口"不完整。

### 验证

```bash
./gradlew :app:assembleDebug          # 通过
./gradlew :app:testDebugUnitTest      # 通过
./gradlew :core-protocol:test         # 通过
check-init-order / check-single-dispatch  # 通过
```

### 没验

真机语音（说「下一首」是否切歌）—— 需要麦克风与真机。
之前那句回执文案全部保留，用户看到的提示不变。

## 第十五轮 · S2.1 统一播放状态（**用户可感：本机播放有进度条**）

| 文件 | 动作 |
|---|---|
| `ui/player/PlayerUiState.kt` | 补 4 个字段：`positionMs` / `durationMs` / `isLive` / `volume` |
| `playback/local/LocalPlayer.kt` | 加 **500ms ticker** + `publish()` 填齐位置/时长/音量 |
| `playback/PlayerScreen.kt` | 进度条与音量条**按输出选源**（本机读 ExoPlayer / 音箱读设备快照） |

### 修的毛病（用户能感觉到）

切到「本机」播放时，播放页的进度条和音量条是**空的** —— 因为界面只读设备状态，
而手机上放的时候没有人往那里填值。

### 两个必要的技术点

**① 必须自己轮询（500ms）**
Media3 **不会**每次位置变化都回调，只在状态变化时给一次。不轮询，进度条就停在开始那一秒不动。

**② 时长不能用 0 表示"未知"**
Media3 用 `C.TIME_UNSET`（负数）表示还没加载完。直接当 0 用会让进度条显示 `0:00/0:00`，
看起来像卡住。所以分成 `durationMs`（真时长）和 `isLive`（直播，不该画进度条）。

### 验证

```bash
./gradlew :app:assembleDebug          # 通过
./gradlew :app:testDebugUnitTest      # 通过
check-init-order.py                   # 通过
```

### 没验（真机）

- 切「本机」→ 播放页**进度条真的在走**
- 本机播放时**音量条能拖且真改音量**
- 切回音箱 → 进度/音量立刻变成设备的值，**不串**
- 播放页**仍无任何浮层**（docs/37）

### 没做的部分

`MiniPlayerBar`（常驻播放条）仍读旧来源。它每页都在，改动面比播放页大，
下一轮单独做（`docs/90` S2.1 也列了它）。

## 第十六轮 · S2.1 收尾：常驻播放条按输出选源

| 文件 | 动作 |
|---|---|
| `home/MiniPlayerBar.kt` | 本机输出时读 `LocalPlayer` 状态；文案改成 `m:ss / m:ss` |

**修的毛病**：切到「本机」后，底部那条常驻播放条仍显示**音箱的曲名** —— 因为这里只读 `QueueBus`。

### 一个设计点

本机播放没有"第 3/100 首"这种概念（本地播放器目前只播单首），
所以本机时文案换成进度时间，而不是把 `queue.total` 生硬套上去。

### 验证

```bash
./gradlew :app:assembleDebug / testDebugUnitTest   # 通过
```

### 没验

真机（本机播放时那条是否显示进度 / 切回音箱是否立刻换回"第几首"）。

### 交付路径的教训

用户反馈 APK「文件不存在」。文件确实在 `/root/.hermes/workspace/`（21MB），
但**那个目录在会话工作区之外**，App 取不到。

**从本轮起 APK 复制到会话工作区内再给链接。** 这是第二次因为这个路径问题让用户拿不到文件。

## 第十七轮 · S2.3 前半：两个根位 + 首页分组判据（为 U2 铺路）

**为什么不按施工图顺序先做 S2.2**：S2.2 要给 Media3 实现 `Player` 契约
（`SimpleBasePlayer` + `getState` + 五个 handle*），错误面大、需要整轮空间。
本轮先做它的前置分类判据 —— 施工图第 6 章也写了「S2.3 的根位与 S2.2 可并行」。

| 文件 | 动作 |
|---|---|
| `core-protocol/.../dlna/DeviceProfile.kt` | `DeviceCapabilities` 加 `canPlayAudio` / `canBeRemoted` / `group`；新增 `enum DeviceGroup` |
| `core-protocol/.../dlna/DeviceGroupTest.kt` | **新建**，5 个用例 |

### 三档分组（按能力，不按品牌）

```
MySpeaker    有 PlayQueue + 能组网      → 三台 DOSS
OtherPlayer  能放声音但无队列/无组网    → 电视盒子
Other        连声音都放不了 / 没探全
```

**为什么按能力**：型号标签会随固件变（`.153` 旧固件与 `.164` 新固件已实测出字段差异），
能力是探测出来的、可以随时修正。新加一台设备只要探到能力就自动落组，代码一行不用改。

### 验证

```bash
./gradlew :core-protocol:test    # 618 用例 0 失败（新增 5）
```

### 没做

**首页还没按 `group` 渲染** —— 那是 U2 本身（用户定的时机是 S2.3 之后整体做）。
本轮只把判据准备好，且用 5 个用例锁住"什么能力落哪组"。

**S2.2 留到下一轮**，理由如实写在这里。

## 第十八轮 · S2.2 通知栏接音箱（`UpnpPlayer` + MediaSession 换挂）

| 文件 | 动作 |
|---|---|
| `playback/UpnpPlayer.kt` | **新建**，`SimpleBasePlayer` 实现（约 190 行） |
| `playback/PlaybackService.kt` | `currentPlayer()` 按输出换挂 + `restart()` |
| `playback/PlaybackViewModel.kt` | 加 `toUiState()`（设备态 → 统一态） |
| `playback/PlayerScreen.kt` | 切输出后 `PlaybackService.restart()` |

### 修的是什么

`MediaSession` 原来直接挂 `ExoPlayer`（本机播放器）。所以**投音箱时通知栏/锁屏/耳机键
控制的是手机上那个空播放器** —— 全是死的。

### 三个关键实现点

**① 先改状态再发命令**
官方硬契约：「update the visible state immediately after each method call」。
等设备回话（真机 30~80ms，断网更久）会让通知栏按钮按下去半天不变，用户重复按三次。

**② 按钮按能力位给**
电视盒子没有设备侧队列 → 不给"下一首"。给了就是"按钮在那儿、按下去没反应"
（悬浮窗踩过同一个坑）。

**③ `SimpleBasePlayer` 没有 `handleSeekToNext`**
编译报 `overrides nothing`。下一首/上一首都走 `handleSeek`，
用目标索引和当前索引比较来判断方向 —— 实测签名是这样。

### 已知取舍（写进代码注释）

`MediaSession` 建立后**不容易换 player 实例**，所以切输出时用"停服务再起"重建会话。
通知栏会闪一下，但不会出现"控件还在、声音没了"。

### 验证

```bash
:app:compileDebugKotlin / assembleDebug / testDebugUnitTest   # 全通过
```

### 没验（必须真机）

- 投音箱时**通知栏出现媒体控件**，标题/进度正确
- 通知栏按暂停 → 音箱真的暂停
- 耳机键能切歌（投音箱时）
- 本机播放行为不回归
- 切输出后通知栏**不会卡在旧状态**

## 第十九轮 · S3.1 第一项：`ToQueueItemsUseCase`（收掉 2 处逐字重复）

| 文件 | 动作 |
|---|---|
| `usecase/ToQueueItemsUseCase.kt` | **新建**：`of()` 单条构造 + `resolveAll()` 批量解析 |
| `library/LibraryViewModel.kt` | **两处逐字重复**合并（`:206` 播放列表 / `:274` 加入歌单） |
| `test/.../ToQueueItemsUseCaseTest.kt` | **新建**，5 个用例 |

### 为什么不引入 `SourceTrack` 抽象（与施工图的一处偏差）

施工图 §3.6 写的是 `ToQueueItemsUseCase(resolveUrl: (SourceTrack) -> String?)`。
但**这个类型不存在**，而且要造它就得让五个源（`BrowseEntry` / `LocalTrack` /
`SubsonicSong` / `RadioStation` / `LxTrack`）都去实现一个公共父类 ——
那是牵动五个模块的改动，收益只是"少写一个字段名"。

改用泛型 + 两个 lambda：**不新增类型，只统一流程和字段落点**。

### 收的真实收益（不是行数）

`resolveAll` 统一了"解析失败怎么办"：**直接丢弃，不占位、不崩整批**。

五处手写循环各自处理不同：
- 有的 `getOrNull()?.let{}`（对）
- 有的 `!!`（一条失败整批崩）
- 有的保留空 url 占位（设备回 400，表现是"随机有几首放不了"）

### 验证

```bash
./gradlew :app:compileDebugKotlin / testDebugUnitTest   # 通过，5 个用例 0 失败
grep -rn 'QueueItem(' app/src/main | wc -l              # 9 → 6（剩 6 处待收）
```

### 没做完（如实记）

施工图 S3.1 要求收 **8 处**，本轮收了 **2 处**（音乐库里那对逐字重复，价值最高）。
还剩 6 处：NAS / 队列 / 电台 / PlaybackRouter / PlaybackViewModel / PlaylistStore / DevicePlaylistGateway。

另外两个 UseCase（`SetVolumeUseCase` / `PlayFromSourceUseCase`）**本轮没动** ——
它们要动 4 套音量实现和 5 个 ViewModel 的播放路径，需要整轮。

## 第二十轮 · U2 首页设备按类型隔离（用户第一条 UI 意见兑现）

**用户原话**：「首页设备，通用 upnp 设备根本就没考虑过与 doss 隔离，反而混在一起」。

| 文件 | 动作 |
|---|---|
| `home/HomeScreen.kt` | 设备列表按 `DeviceCapabilities.group` 分组渲染 + `DeviceGroupHeader` |

### 现在长这样

```
我的音箱                     ← 有 PlayQueue + 能组网
  能收整份列表、能组网
  [doss1] [doss2] [doss3]

其它播放设备                 ← 能放声音，但没队列没组网
  能放声音，但没有队列也不能组网
  [E900V22C-175]
```

判据用上一轮建的 `DeviceCapabilities.group`（`docs/90` S2.3 前置，5 个用例锁着）。
**按能力分不按品牌分** —— 型号标签会随固件变，能力是探测出来的。

### 两个刻意的处理

1. **空组不显示** —— 没有电视盒子的人不该看到一个空标题
2. **文案在 UI 层** —— `core-protocol` 的枚举不带中文，协议模块不该管文案

### 验证

```bash
./gradlew :app:assembleDebug / :app:testDebugUnitTest   # 通过
check-init-order.py                                      # 通过
check-profile-in-ui.py                                   # 仍 warn 1 处（见下）
```

### 剩下那 1 处（如实记）

`HomeScreen:320` 还有一处 `?: DeviceProfile.UNKNOWN` —— 那是**取默认值**给卡片渲染用，
不是按型号分支。要彻底清掉得让 `DeviceCardRow` 的 `profile` 参数变成非空，
改动面在卡片组件里，下一轮一起做。所以 `check-profile-in-ui.py` 还不能转 error。

### 没验（真机）

- 首页是否真的分成两组
- 电视盒子那台是否落在「其它播放设备」
- 只有音箱时是否**不显示**空组标题

## 第二十一轮 · U1 方案对比（只出方案，不动代码）

`docs/93-nav-redesign-options.md`。三个方案 + 13 个子页去向 + 工作量 + 风险。

| 方案 | 形态 | 主要代价 |
|---|---|---|
| **A 按对象** | 出声 / 找歌 / 设备 / 我 | 「找歌」要重做（6 个源收一屏），NAS 目录树可能挤 |
| B 按场景 | 首页 / 在放 / 找歌 / 设备 | 「首页」与「在放」信息重叠；「我」进齿轮 |
| C 精简 3 格 | 在放 / 找到 / 设备 | **不推荐**：17 页装 3 格 → 中转页从一层变两层 |

**我的建议：A + 吸收 B 的首页摘要**（「设备」格顶部放一条"在放什么"）。
理由：这个 App 会一直加能力（红外/蓝牙/B站/下载），**只有"按对象分"在加东西时不用重排**。

### 需要用户定的三件事

1. 选哪个方案
2. **洛雪降级**（从底部格子变成「找歌」里的一个源）—— 用户搜得多的活要确认
3. 诊断台放哪（建议「我」首屏第一项，现在要点三层）

### 没做

零代码改动。等用户选完再写施工单。

## 第二十二轮 · UI 定稿（三核心：播放器 / 内容源 / 遥控器）

**用户定位原话**：「这是一个以播放器，内容源，遥控器为核心的 app，至于其他，
根据常规大厂 ai 进行安排，该拆分的拆分，该删的删，该整合的整合，其他都为以上三个核心内容服务」。

`docs/93` 从"三个方案对比"改成**定稿**：

```
播放    内容    遥控    我
```

### 三个核心各占一格

| 格 | 回答什么 | 第一屏 |
|---|---|---|
| 播放 | 现在放什么、怎么放 | 封面/进度/音量/**从哪出声**/队列 |
| 内容 | 有什么可放 | **搜索框** + 最近播放 + 源筛选（NAS/本地/在线/洛雪/电台/歌单） |
| 遥控 | 这些机器怎么控 | 分组设备 + 音量/组网/声道 + **红外/蓝牙/WiFi**（U3 的位置在这里） |
| 我 | App 自己怎么配 | **诊断台第一项** + 配网 + 脚本 + 备份 |

### 大厂参照（写进文档）

Spotify（Home/Search/Library）→ **搜索必须独立成格**；
Sonos（房间/浏览/搜索/系统）、Google Home（设备/自动化/我）→ **控设备是主体，不是设置**。

**一处刻意的偏离**：大厂都把"在放"做成常驻条不做 tab。
我们保留「播放」格，因为播放页 964 行信息密度高（封面/进度/音量/队列/日志/预设键），
常驻条点开是全屏页；两者不冲突（条是摘要，格是全操作面）。

### 删 / 并 / 移（13 页全部有去向）

- **删 2**：`SourcesScreen`(46)、`MyMusicScreen`(232)
- **并入 5**：洛雪(147)→内容、电台(273)→内容、队列(345)→播放、设备内容(183)→播放、在线(283)→内容
- **移 4**：NAS(502)→内容全屏浏览、音乐库(599)→内容视图、脚本(119)→我、**诊断(135)→我首屏第一项**
- **合并**：配网(342) + WPS(161) → 合成一页

### 6 步施工，每步可回退

第 1 步 = 新建「内容」页（搜索 + 最近 + 源筛选），做完就能感知。

### 没做

**零代码改动**。定稿先给用户过一遍 —— 上一轮输出口那个错，
就是因为我在没跟用户确认的情况下按施工图做了。

### 下一轮

第 1 步：新建「内容」页。

## 第二十三轮 · 设备模型澄清 + 删掉界面上的"设备信息"

**用户两次纠正**：

1. 「用户不需要知道全部设备信息，用户只需要能用，好用，简单状态就够」
2. 「设备是需要添加的，现在没有并不意味着永远没有……就是红外设备，蓝牙设备」

| 改动 | 内容 |
|---|---|
| `HomeScreen.kt` | **删掉分组标题下的那行说明**（"能收整份列表、能组网"） |
| `docs/93` 第九节 | 记录设备模型：三类、用户添加、"输出口"= 播放设备的子集 |

### 我错在哪

上一轮做 U2 分组时，我在分组标题下面加了一行能力说明 —— 那是**给开发看的**
（它解释的是代码里怎么分组），放在界面上只是噪音。

用户要的是"能不能用"，不是"为什么这么分类"。

### 设备模型（记进文档，后续据此做）

| 类型 | 添加方式 | 默认显示 |
|---|---|---|
| 播放设备 | SSDP / 填 IP | 在放什么 · 音量 · 在线 |
| 红外设备 | 选品牌型号 | 最近按过的键 |
| 蓝牙设备 | 扫描配对 | 连没连上 |

**「输出口」= 播放设备里能出声的那些**，不是独立概念。

### 验证

```bash
./gradlew :app:compileDebugKotlin   # 通过
```

## 第二十四轮 · 设计语言定为 Apple HIG（只落文档）

**用户原话**：「苹果的 ui 设计就是最好的 ui」。

### 先确认的一个冲突（写进文档）

现在这个 App 的视觉是 **Google Material 3**：

```
Theme.kt:44   AppShapes = Shapes(RoundedCornerShape(8/12/18/24/32.dp))   ← Material 形状阶梯
Theme.kt:174/197  darkColorScheme / lightColorScheme                     ← Material 色彩系统
```

差别不在圆角，在**结构**：Material 用"卡片堆叠"，苹果用"分组列表"。
全 App 有 **19 处 `AppCard`**。

### 定了八条落地规则 + 现状违反清单

最关键三条：
1. 分组列表优先，卡片极少用（苹果的设置/健康/音乐全是 inset grouped list）
2. 灰阶层级，颜色只给"状态"和"主操作"（现在每处都在用 primary 彩色）
3. 行内不放解释文字（已删设备分组那处，其余还有）

### 落地顺序：先建地基

**S**：建 `InsetGroup` / `ListRow` / `LargeTitle` 三个基础件 —— **不做这一步就改页面，等于每页各写一套**。
之后 S+1 设备页、S+2 「我」页、S+3 播放页、S+4 底部图标。

### 没做

零代码改动。样式是地基，先定规则再动手。

## 第二十五轮 · 建 Apple 风格三个基础件（`docs/93` 第十节 S 步）

| 文件 | 动作 |
|---|---|
| `ui/apple/AppleUi.kt` | **新建**：`InsetGroup` / `ListRow` / `LargeTitle` |

### 为什么先建设施，不改页面

现有 `AppCard` 是 Material 的"卡片堆叠"，全 App **19 处**。
一次性全换会同时动 10 个页面，出问题无法定位。
所以先把苹果那套的**模子**建好，页面一个个搬 —— 搬一个测一个。

### 三条 HIG 硬规则写进代码

1. **行高 ≥ 44dp**（`heightIn(min = 44.dp)`）—— 苹果全系统的触控底线
2. **层级靠明度不靠色相**：三档灰全部从 `onSurface` 降透明度派生
3. **分隔线从文字左边缘起**（不是整行通到底）—— 苹果的缩进分隔线

### 一个我自己抓出来的坑

第一版我把 iOS 的固定灰写死了（`Color(0xFF1C1C1E)` / 底色 `Color.White`）。

编译通过、亮色下也对，但**暗色主题下分组块会变成一块白底** —— 装上去是刺眼的白屏。

改成从 `MaterialTheme.colorScheme` 派生后两种主题自动都对。
这类错**编译和单测都抓不到**，只有装上手机才看得见。

### 验证

```bash
./gradlew :app:compileDebugKotlin   # 通过
```

### 没做

**一个页面都还没搬**。本轮只建组件，不改任何现有页面 —— 纯新增，零回归风险。

## 第二十六轮 · 设计判据定为「呈现使用」

**用户原话**：「之前开发是挖掘功能，ui 设计是呈现使用」。

这句话解释了之前多轮返工的根因：**我一直在"呈现功能"**。

| 我之前做的 | 属于 |
|---|---|
| 设备分组标题下写「能收整份列表、能组网」 | 功能说明 |
| 播放页状态栏 `输入源 DLNA 渲染器` | 协议名 |
| 分组按能力位 | 功能分类 |

这些是**挖掘阶段的产物**（那时需要它们才知道通没通），呈现阶段就是噪音。

### 落成判据

> **用户打开这个界面，是为了完成哪一个动作？**

答不出来 = 功能罗列，不是使用路径。四个界面的答案写进文档：

播放=让声音从我想的地方出来；内容=找到现在想听的；遥控=调某一台的音量；
我=出问题时看哪里。

### 顺手定了上一轮悬着的问题（行内数字 vs 滑块）

按判据推：**本质是遥控器，调音量最高频 → 不能要求"点进去再调"**，选行内可拖。
但加两条：拖动区在行右侧一块（不是整行，否则难点进详情）、松手才发命令（连续发会让音箱卡）。

**苹果不给行内滑块，是因为 iOS 设备列表不是遥控器；我们这个是。**

### 状态显示的尺寸

前三条进列表（在不在 / 在放什么 / 音量），后四条进详情（电量 / 固件 / 能不能组网 / 支持什么协议）。

### 没做

零代码。判据先落文档，下一轮按它改设备页。

## 第二十七轮 · 交互层：触觉分级（借鉴苹果 haptic）

| 文件 | 动作 |
|---|---|
| `docs/93` 第十二节 | **新增**：苹果六条交互原则 → 本 App 的映射 + 现状差距 |
| `ui/apple/AppHaptics.kt` | **新建**：按语义分五档（tap / impact / tick / success / error） |
| `ui/components/Common.kt` | `AppCard` 触感改走封装 |
| `ConsoleApp.kt` | 底部导航触感改走封装 |

### 修的是什么

原来**全站用同一档触感**（`HapticFeedbackType.TextHandleMove`，最轻那档）。
删除设备、组网成功、点一行 —— 手感完全一样。**反馈一样等于没有反馈**（HIG 专门讲过）。

### 为什么不用 Compose 的 `LocalHapticFeedback`

它的枚举只有两档（`LongPress` / `TextHandleMove`），给不出"成功/失败"。
改用 `View.performHapticFeedback` + 系统常量：
`CONFIRM`（成功）/ `REJECT`（失败）/ `CLOCK_TICK`（拖动刻度）/ `LONG_PRESS`（影响）。

**而且它会自动尊重用户的"关闭触感"设置** —— 系统内部判断，不用我们操心。

### 文档里记下的最重要一条（还没做）

**苹果的列表页几乎从不给每行摆三四个按钮**，因为摆出来会让人犹豫：
"这一行有五个动作，我该点哪个？"

现在设备卡片正是这样：音量、声道、组网、预设、重命名、删除全在明面上。

按「呈现使用」判据，这一行只有一个动作 —— **控制这台设备**：
- 点一下 → 进控制面板
- 长按 → 快捷菜单（重命名 / 取消组网 / 删除 / 详情）

### 验证

```bash
./gradlew :app:compileDebugKotlin   # 通过
```

### 没验

触感各档强度**只能真机感觉**。而且各家 ROM 对 `CONFIRM` / `REJECT` 的实现差异较大
（有的很轻、省电模式下不给）。

## 第二十八轮 · 全功能 × 全配置矩阵（用户要求"停一下"）

`docs/94-feature-config-matrix.md`。**全部数据从代码 grep 出来，不是凭记忆。**

### 规模

```
10 个 ViewModel / 147 个公开动作 / 17 个页面
7 处持久化（3 SQLite + 1 文本 + 3 目录）
```

### 三个最有用的发现

**① 65 个内容源动作，用户视角只有 3 个**

NAS(21) + 本地(15) + 在线(7) + 电台(11) + 洛雪(6) + 本地队列(10) + 统一搜索(1) = 65 个动作，
摊在 **6 个页面**上。而用户要做的事只有：**找 → 选 → 放**。

**源是筛选维度，不是页面。**

**② 42 个遥控动作，用户只需要 4 个入口**

发现/状态/音量/组网/声道/机身键/遮罩/固件/恢复出厂/睡眠/原始命令/诊断 —— 12 组，
用户视角只有：看成在不在、调音量、组网、出问题看诊断。

**剩下绝大多数是内部编排**（`scan`/`openDir`/`toggleEntry`/`selectAllPlayable`…），
它们不该有独立入口，只该是某个动作的中间步骤。

**③ 配置把"每台"和"全局"混在一起**

7 处持久化里，最严重的错：**把"每台设备"的配置**（音量/声道/固件/配网）
**全塞在一台设备的设置页** —— 换个设备要重新找一遍。

正确分四类：每台 / 每源 / 全局一次 / 系统权限。

### 顺手扫出的重复和缺口

| 类型 | 内容 |
|---|---|
| 重复 | `renameCard`/`renameDevice`、`setVolume`/`setVolumeLive`、`pressPreset` 两处、循环模式三份、`QueueItem(` 6 处 |
| 有功能无入口 | `sendCommand`（原始命令）、红外（只有"试发一个键"）、蓝牙/WiFi（零代码） |
| **有页面无用户动作** | `SourcesScreen`(46)、`MyMusicScreen`(232)、`DeviceContentScreen`(183)、`WpsGuideScreen`(161) |

### 结论

**不是 UI 不好看，是"147 个动作被摊在 17 个页面里，没有按用户动作归并"。**

### 没做

零代码。等用户看完矩阵再定 UI 顺序。

## 第二十九轮 · 矩阵补到七维（用户问「还能更完整吗」）

第一版只有"功能 + 配置"两维。补了六维，`docs/94` 现在 12.4KB。

| 新增维度 | 扫出来的数 |
|---|---|
| **协议能力** | LinkPlay 私有 httpapi **55 条命令** + UPnP 4 个服务 |
| **后台服务** | 4 个前台服务（2 个 dataSync / 1 specialUse / 1 mediaPlayback） |
| **时间周期** | **5 个不同周期**同时在跑（500ms / 1s / 3s / 6s / 手动） |
| **权限** | 14 个（其中 **4 个要用户手动处理**） |
| **诊断段** | 20 段 = 系统状态的全部维度 |
| **UI 组件** | 11 个，但**新旧两套并存，新那套用了 0 处** |

### 补完后暴露的五个新问题（第一版看不到）

1. **55 条 httpapi 命令只用了约 20 条** —— 剩下 30 多条里可能有用户想要的功能
   （LED 开关、闹钟、EQ），**从没人盘过**
2. **5 个轮询周期并存** —— "有时跟手有时不跟手"的根源（最短 500ms，最长 6s，没统一）
3. **4 个前台服务、各自的通知渠道** —— 用户可能在通知栏看到 4 条本 App 通知
4. **4 个权限要手动给**，但**分散在"第一次用到时"才申请** —— 用户不知道还有哪些没给
5. **新旧两套 UI 组件并存**（Material 卡片 + Apple 分组列表），迁移完成前界面会同时有两种视觉

### 总矩阵（16 项能力 × 7 维）

结论：**16 项能力里只有 7 项在界面上"能直接摸到"**，8 项"深/找不到"，2 项完全没有。

**UI 重排的真正目标不是好看，是让这 16 项能力的状态在界面上各就各位。**

### 没做

零代码。

## 第三十轮 · UI 设计图定稿 + 设备行组件

### 设计图（四屏）

`ui-mock.html`（用真实像素画，chromium 截图）。四屏：设备 / 播放 / 内容 / 我。

用户逐条反馈后改了三版：

| 轮 | 用户说的 | 改了什么 |
|---|---|---|
| v1 | —— | 四屏初版 |
| v2 | 「在线和洛雪本来就是同个源」 | 源筛选 6 → 5，洛雪并进「在线」 |
| v3 | 「在线曲库可以删除用洛雪代替 / B站没做 / 缺蓝牙红外 / 播放器要有播放列表」 | 加 B站筛选位、加「遥控设备」组、播放页加播放列表行、删在线曲库配置 |
| v4 | 「在线还是保留吧」 | 「我」里恢复「在线曲库（可选）」 |

**最终决策**：界面上不增加入口 —— 「在线」背后问两路（洛雪 + Subsonic），结果标来源。
因为用户认知里它们同个源，但能力上 Subsonic 能检索**自己服务器**的库，洛雪不能。

### 组件 `DeviceRowApple`

一行三件事：在不在 / 在放什么 / 音量。电量、固件、能否组网、声道、预设**全部进详情**。

**音量用手势不用滑块**：54dp 宽的滑块在 390dp 屏上拖不准，还会把状态文字挤成四个字。
改成整行左右拖（苹果在列表里的做法），拖动时数字放大，**松手才发命令**。

三种手势共存：轻点进面板 / 长按出菜单 / 左右拖调音量
（`combinedClickable` + `detectHorizontalDragGestures`，不互相吞）。

### 验证

```bash
./gradlew :app:compileDebugKotlin   # 通过
```

### 没做（重要，如实记）

**没有接进 `HomeScreen`。** 因为现在的设备卡片上有 6 个功能（组网 / 声道 / 预设 / 遮罩 /
改名 / 移除），直接换掉新行会**丢功能**。

接入时必须同时决定这 6 个去哪（长按菜单里 4 个 + 详情页 2 个），那是完整一轮的事。
硬接进去的风险比收益大。

---

## 下一道

设备页接入（新行组件 + 6 个原功能的去处分派）

## 第三十一轮 · 补漏提交（main 编译修复）

**事故**：第二十七轮（触觉分级）整轮**没提交** —— 写了文件、本地编译通过，直接进下一轮。

因为那轮没推送，CI 没跑，一直没暴露。直到 `DeviceRowApple` 引用 `rememberAppHaptics`
合入后，CI 报 `Unresolved reference`，**main 变成编译不过**。

| 补的内容 | 文件 |
|---|---|
| 触觉分级封装 | `ui/apple/AppHaptics.kt`（新增） |
| 两处接入 | `Common.kt` / `ConsoleApp.kt` |
| 交互原则文档 | `docs/93` 第十二节 |

顺带删掉误留在 `app/ui-mock.html` 的设计图。

**新增踩坑条目**：**「本地编译通过」≠「已经提交」**。
每轮结束前必须 `git status --short` 确认为空。

## 第三十二轮 · 补两屏：设备发现 + 遥控器面板

**用户指出**：「设备发现有没有设计进去，遥控器没有在 ui 上体现」。

**两条都是真漏**：之前只画了"已添加的设备列表"，没画"怎么加"，也没画"遥控器长什么样"。

### 第五屏 · 扫描发现

```
扫描
[正在扫描局域网… 8s]
发现 2 台新设备
  DOSS DS-1831     192.168.99.190 · 音箱   [添加]
  E900V22C-175     192.168.99.175 · 播放器 [添加]
已添加
  doss1 / doss2（灰显"已添加"）
扫不到？确认手机和音箱在同一个 WiFi，或直接填 IP。
```

入口在设备页底部一行「扫描附近设备 · 发现 2 台」。

**关键点**：已添加的设备**在同一屏显示为灰显**，不是消失 ——
否则用户会以为"扫描怎么找不到我加过的设备"。

### 第六屏 · 红外遥控器

```
← 设备                      编辑
电视
[⏻ 电源]
[▲] [▼]
    ┌─────────┐
    │    ▲    │
    │ ◀ OK ▶ │
    │    ▼    │
    └─────────┘
[返回] [菜单]
[音量−] [音量+]
更多按键 · 18 个  ›
手机需有红外发射器。没有的话，可用 WiFi / 蓝牙遥控。
```

**这是"遥控器"该有的样子** —— 不是设备列表里一行，是点进去一个按键面板。

### 一个截图环境的已知问题

播放页曲名在图上显示双层。**这是 headless chromium 缺中文字体导致的**
（系统只有 Noto Serif CJK，没有 Sans），试了三种修法都不解决：
改字号对齐 / 去 line-clamp / 关 antialiased + 字间距。

真机上（Android 自带中文字体）不会这样。设计意图以其余五屏为准。

### 没做

零代码。这两屏都还没实现（红外码库没接，扫描页没建）。

## 第三十三轮 · 管线矩阵（最短路径 + 扩展性）

用户：「按这个定稿，内部管线同样做个矩阵，**找最短路径和保留扩展性**」。

`docs/95-pipeline-matrix.md`。七层管线 + 五屏逐动作跳数 + 绕路清单 + 扩展性矩阵。

### 最短路径（已经压到底）

| 动作 | 跳数 | 说明 |
|---|---:|---|
| 本机暂停/切歌 | **3** | 全 App 最短 |
| 电台搜索 / 本地搜索 | **3** | 无中间层 |
| 看设备状态 / 拖音量 / 扫描 | **4** | 已并发、已合并两次取数 |
| 红外按键 | **4** | 其中 2 跳本地计算，<5ms |

**最长的 7 跳**：投送播放（VM→Dispatcher→Router→QueueStarter→Gateway→HTTP）
和洛雪取直链（多一层 QuickJS）。

**能不能再减？** 唯一候选是"Dispatcher + Router 合并"，但那样能力位判断会浸进路由层，
将来本机播放器也要懂设备能力 —— **不减更干净**。

### 绕路清单（5 条真问题）

音量两套实现 / 循环模式三份存储 / 界面按钮仍直连 VM / `QueueItem` 6 处手写 /
`pressPreset` 两个 VM 各一份。

### 扩展性（这是矩阵最重要的产出）

| 加什么 | 现在改几处 | 目标 |
|---|---:|---:|
| 标准播放设备 | **0** | 0 ✅ 已达标 |
| 私有协议设备 | 2~3 | 2 |
| **内容源** | **4** | 2（定稿 UI 能解决） |
| **遥控类型** | **4×N（每种各散一次）** | 1 + 实现 |

### 结论：先修路再跑车

**定稿 UI 落地前必须先建两个接口**：

1. `RemoteControl`（label / send(key) / keys()）—— 红外、蓝牙、WiFi 各自实现，
   UI 只认接口，**加新类型时 UI 零改动**
2. `ContentSource`（搜索 / 播放 / 诊断一行）—— 6 个源实现它，内容页只画一次

不建这两个接口，遥控页和内容页就会变成两坨各自硬编码的界面，下次加东西还得拆。

## 第三十四轮 · 基础设施第一步：`RemoteControl` 接口 + 红外实现

用户：「基础设施先完善吧」→ 按 `docs/95` 第四节顺序，先建接口再跑车。

| 文件 | 动作 |
|---|---|
| `remote/RemoteKey.kt` | **新建**：14 个语义键 + `Custom(name)` |
| `remote/RemoteResult.kt` | **新建**：`Sent` / `Unsupported` / `Failed(reason)` |
| `remote/RemoteControl.kt` | **新建**：接口 + 三种传输方式枚举 |
| `remote/IrRemoteControl.kt` | **新建**：红外实现（NEC） |
| `test/.../IrRemoteControlTest.kt` | **新建**，**8 个用例** |

### 三个关键设计

**① 界面只认语义键，不认码值**

红外是 NEC 码、蓝牙是 HID usage、WiFi 是各家 JSON —— 同一个"电源键"三种传输完全不同。
界面存码值的话，**红外遥控器就永远是红外遥控器**，换蓝牙要重画一遍。

**② `keys()` 决定界面画什么**

没有发射器时返回**空集** → 界面整块不显示，而不是画一个按了没反应的遥控器。
这条由单测锁着（`no emitter means zero keys`）。

**③ `Sent` 不等于 `Success`**

红外 `transmit()` 是 void，**没有回执**。系统只保证"波形交给驱动了"，
不保证电视收到了。所以结果类型里**没有 Success**，界面文案只能说"已发送"。

### 现在的真实能力（诚实标注）

内置码表**只有一条探测码**（NEC 地址 `0x04` / 命令 `0x08`），
所以 `keys()` 只返回 Power，遥控页现在只会有一个电源键。

**这是真实能力，不是设计偷懒。** 接 `Flipper-IRDB`（CC0，30 万条）时只换 `codebook`，
界面和接口都不用动。诊断行里明确写着「码表=示例（1 键，未接码库）」。

### 一个刻意的可测性取舍

`IrRemoteControl` 的依赖是**三个 lambda**（hasEmitter / transmit / onAttempt），
不是直接 `new IrBlaster` —— 因为 `IrBlaster` 要 Android 的 `ConsumerIrManager`，
直接依赖它这个类就跑不了纯 JVM 单测，而"没有发射器时必须不画键"恰恰最该被锁住。

Android 工厂在 `companion fun from(context)`，是唯一碰 `IrBlaster` 的地方。

### 验证

```bash
./gradlew :app:testDebugUnitTest   # 全量 1056 用例 0 失败（新增 8）
```

### 没做

- 码库（Flipper-IRDB）没接
- **还没接进 UI**（遥控页还没建）—— 本轮只建接口 + 实现 + 测试
- 蓝牙 / WiFi 实现（接口已就绪，加实现时 UI 零改动）

## 第三十五轮 · 基础设施第二步：`ContentSource` 接口 + 两个实现

用户：「同意」→ 按 `docs/95` 第四节第 2 项。

| 文件 | 动作 |
|---|---|
| `content/ContentModels.kt` | **新建**：`SourceId` / `ContentItem` / `Resolved` / `Readiness` |
| `content/ContentSource.kt` | **新建**：接口 |
| `content/LocalContentSource.kt` | **新建**：本地曲库 |
| `content/RadioContentSource.kt` | **新建**：网络电台 |
| `test/.../ContentSourceTest.kt` | **新建**，**11 个用例** |

### 只实现两个源（刻意）

没一次搬 6 个，而是挑了**差异最大**的两个：本地（数据库、要权限）
和电台（网络、必须二次解析）。

理由：接口只经过"想"没经过"实现"，八成会返工 ——
`ToQueueItemsUseCase` 里的 `SourceTrack` 就是这么挂的（造了个不存在的类型）。

两个差异最大的源都装得下，剩下四个才有把握。

### 三处与直觉相反的取舍

**① `browse()` 不进接口**

NAS 是唯一需要逐层下钻的源。塞进接口等于逼本地/电台/洛雪都实现一个用不上的方法。

**NAS 的目录树留在它自己的页面** —— 这不违反"内容统一"，因为"浏览目录"和"搜索"本来就是两件事。

**② `Resolved.NeedsLocalServer(path)` 而不是给地址**

本地文件不能被音箱直接取，要先由 `LocalMediaServer` 转成 HTTP。
早期版本在这里直接拼 `http://127.0.0.1:PORT/t/ID` 是错的 —— **端口那时还没起**。

所以接口只交路径，由播放编排决定怎么服务它。

**③ 不支持搜索时返回空列表，不抛异常**

一个源不支持搜索，不该让"内容"整屏报错。原因写在 `diagnoseLine()` 里。

### 一个真实的字段修正

我原以为电台条目没有稳定 id，打算用 url 当 id。查了 `RadioStation` 才知道
`id` 就是 `stationuuid`、还有 `favicon` / `tags` / `country`。

**看了类型定义才改对** —— 又是"没查就写"的教训。

### 验证

```bash
./gradlew :app:testDebugUnitTest   # 全量 1067 用例 0 失败（新增 11）
```

### 没做

- 剩下 4 个源（NAS / 在线 / 洛雪 / B站）**还没实现接口**
- 内容页还没建（本轮只建接口 + 两个实现）

## 第三十六轮 · 基础设施第三步：`SetVolumeUseCase`（收共同步骤，**不合并语义**）

### 先纠正我自己在 `docs/95` 里的一个错误判断

管线矩阵里我写的是「**音量两套实现**，两条下发链，该收成一个」。

**查了代码之后发现判断错了**，两条通道的语义本来就不同，且都该留：

| 场景 | 通道 | 为什么不能合并 |
|---|---|---|
| 卡片/滑块拖动 | 合并通道（60ms 取最新） | 拖动一秒几十个值，每个发 HTTP 会把老固件压垮 |
| 音量键 | 直发 | 离散事件，等 60ms 会显得钝 |

**合并它们会让按键变钝** —— 那正是 `ToQueueItemsUseCase` 那种"为统一而统一"的坑。

### 真正的隐患是什么

现在有 **4 个地方**想改音量，每个都要自己记得做三步：
① 写 `VolumeBus`（界面立刻跟手）② 选下发通道 ③ 通道判断不能各写一遍。

**漏掉第 ①　步的表现就是「界面显示 60、设备实际 40」** —— 真机上撞过。

所以这次收的是**共同步骤**，不是语义。

### 改动

| 文件 | 动作 |
|---|---|
| `usecase/SetVolumeUseCase.kt` | **新建**：`duringDrag` / `discrete` / `syncDisplayOnly` |
| `DeviceControl.kt` | 卡片拖动 + 音量键两个入口改走它 |
| `docs/95` | **修正错误的判断**（"两套实现"→"缺共同入口"） |

`syncDisplayOnly` 是刻意加的：读到设备新音量时只更新界面，**绝不能再发一条命令回去**。
（有这个入口，调用方就不会自己去调 `publish` 当前置步骤，从而不重复。）

### 验证

```bash
./gradlew :app:testDebugUnitTest   # 全量 1073 用例 0 失败（新增 6）
```

6 个用例锁的是**顺序和通道选择**：
拖动必须先 publish 再 merged、按键必须先 publish 再 now、
**两者不许串道**（拖动走直发会压垮固件、按键走合并会变钝）、越界钳制、同步显示不发命令。

### 一个环境问题的记录（这轮反复撞）

**新增测试文件后，第一次 `compileDebugUnitTestKotlin` 必然失败且不给错误信息，重跑一次就成功。**

这是 Kotlin 增量编译在 `--no-daemon` 下的缓存问题。以后遇到"构建失败但没有 `e:` 行"，
直接重跑一次，不要以为代码错了。

### 没做

播放页那两个音量方法（`setVolume` / `setVolumeLive`）走的是 `PlaybackEngine`，
和设备侧的 `RemoteTransport` 是两条不同的链路 —— **本轮没动**，需要单独一轮（风险评估不同）。

## 第三十七轮 · 基础设施第四步：界面按钮走唯一执行点（**用户可感**）

| 位置 | 前 | 后 |
|---|---|---|
| 上一首 / 下一首 | `vm::previous` / `vm::next` | `dispatcher.dispatch(...)` + **能力位判断** |
| 播放/暂停大按钮 | `if (playing) vm.pause() else vm.play()` | `dispatch(PlayIntent.TogglePlay)` |
| 停止 | `vm::stop` | `dispatch(PlayIntent.Stop)` |
| 静音 | `vm::toggleMute` | `dispatch(PlayIntent.Mute)` |
| 循环模式 | `vm::cycleLoopMode` | `dispatch(PlayIntent.LoopNext)` |

**这两个手势保留直连**（刻意的）：`seekTo`（拖进度）和 `setVolumeLive`（拖音量）。
它们是连续手势，每次拖动都走一次 `dispatch` 会创建几十个协程，没必要。

### 用户能看到的变化

投通用 DLNA 渲染器（电视盒子）时，**上一首/下一首变成灰的**，
点一下会提示「这台设备没有「下一首」（它不持有播放列表）」。

以前是画出来、按下去毫无反应 —— 用户以为 App 坏了。

### 一处文案的讲究

提示不是"操作失败"，是"**这台设备没有这个能力**"，而且带了原因（不持有播放列表）。

说"失败"会让用户去查网络；说清是设备限制，他才会明白要换台设备。

### 顺带改名

`voiceScope` → `actionScope`。它现在服务语音、按钮、切歌三个来源，旧名字会误导后来人。

### 验证

```bash
./gradlew :app:assembleDebug / :app:testDebugUnitTest   # 全量 1073 用例 0 失败
```

### 没做的

- 投流那 4 个（`setCastUrl` / `setCastNeedsHeaders` / `setCastReferer` / `cast`）
  **没走 dispatch** —— `PlayIntent` 里没有 `Cast`。要不要加是下一轮的决定
  （加了会让"投任意 URL"进意图层，不一定合适）。
- 本机播放时 `can(Next)` 恒为 true —— 本地播放器目前只播单首，那个"下一首"
  点了会走到 `ExoPlayer.seekToNextMediaItem()`，但队列里没有下一首。**已知限制，未改。**

## 第三十八轮 · 循环模式「收一份」= **删掉两个假入口**

### 查出来的真相（比"三份存储"严重）

我原以为问题是"三份存储、改一处另两处不知道"。查完代码发现是**两个按钮点了根本没用**：

| 位置 | 状态 | 实际行为 |
|---|---|---|
| 播放页 | `LoopMode`（设备真实状态） | ✅ 真发 `setPlayerCmd:loopmode:<n>` |
| NAS 页 | `Int` = `ClientSideQueue.LOOP_*` | ❌ **只改本地 state**，下发时被 `startQueueOnDevice` 内部的 `LOOP_ALL` 覆盖 |
| 队列页 | `Int` = `QueueLoopMode.*` | ❌ **没有任何读取点**（纯死状态） |

**用户点 NAS 页的循环按钮：界面文字变了、设备没变。**

这是「命令回 OK ≠ 功能生效」的一个更糟的变体 —— **连命令都没发**。

### 三套编号其实是**刻意不同**的（`docs/01` §312/§366 有实测记录）

| | httpapi `setPlayerCmd:loopmode` | 队列服务 `SetQueueLoopMode` |
|---|---|---|
| 不循环 | 0 | 0 |
| 单曲 | 1 | 1 |
| 列表 | **-1** | **2** |
| 随机 | **2** | **3** |

`LoopMode` 枚举的 `wire` 与 httpapi 对齐 ✓，`QueueLoopMode` 与队列服务对齐 ✓。
**它们不是同一件事，不该合并**（`docs/65` 那条"名字一样 ≠ 重复"）。

### 所以这轮"收一份"的正确做法是**删**

| 文件 | 动作 |
|---|---|
| `NasScreen.kt` | 删循环按钮 + `nextLoop` / `loopLabel` 两个死函数 |
| `NasViewModel.kt` | 删 `loopMode` state + `setLoopMode`；`savePlayback` 去掉 `loopMode` 参数（它只写不读） |
| `QueueScreen.kt` | 删「循环模式」整块 + `nextLoop` |
| `QueueViewModel.kt` | 删 `loopMode` state + `setLoopMode` |

### 用户能感觉到的变化

**NAS 页和队列页的循环按钮消失了。**

这不是功能减少 —— 那个按钮本来就没用。循环模式只能在播放页改，那里是真的。

### 验证

```bash
./gradlew :app:assembleDebug / :app:testDebugUnitTest   # 全量 1073 用例 0 失败
grep setLoopMode / state.loopMode                       # 只剩播放页的设备状态
```

### 教训

**"三份存储"不一定是"三份都在生效"** —— 可能两份是死的。
先查清哪份真在用，再决定是合并还是删。这次的答案是**删**。

## 第三十九轮 · 轮询周期收一处（`PollCadence`）

| 文件 | 动作 |
|---|---|
| `util/PollCadence.kt` | **新建**：7 个周期 + 每个的**理由** |
| `LocalPlayer` / `PlaybackViewModel` / `ConsoleViewModel` ×4 / `WpsGuideScreen` | 全部改成引用它 |

### 收之前散成这样

| 周期 | 用途 |
|---:|---|
| 500ms | 本机播放进度 |
| **1000ms** | 播放页设备状态 |
| 2500ms | 组主机音量 |
| 3000ms | 组拓扑 |
| 3000ms | 首页卡片（逐台） |
| 1000ms | 睡眠定时时钟 |

**注意一个之前记错的数**：播放页是 **1 秒**，不是 3 秒（`POLL_INTERVAL_MS = 1_000L`）。
所以同一台设备，**播放页 1 秒刷、首页 3 秒刷** —— 切来切去会觉得"有时快有时慢"。

### 刻意的选择：收"定义"，不改"值"

五个用途不同，强行统一会伤害其中一些：

| 用途 | 如果改成 1s |
|---|---|
| 本机进度 | 500ms 才不看出格；1s 会一跳一跳 |
| 首页卡片 | 每秒问每台设备，`.153` 是 2018 年固件，会被打爆 |
| 组拓扑 | `slave_list` 请求量翻三倍，而组结构很少变 |

**所以 `PollCadence` 里每个值都写了"为什么是这个数"。写不出理由的，说明该统一。**

### 验证

```bash
grep 'while (true)' -A 3 | grep delay    # 周期循环里已经没有裸数字
./gradlew :app:assembleDebug / testDebugUnitTest   # 全量 1073 用例 0 失败
```

### 零行为改动

所有值保持原样，只是搬到一处。**要调手感时改这一个文件即可，不会漏。**

## 第四十轮 · 设备页按定稿重排（**用户可感，第一步 UI**）

| 文件 | 动作 |
|---|---|
| `HomeScreen.kt` | 设备列表换成 `DeviceRowApple`（一行一台）；长按菜单补 4 项；加 3 个辅助函数 |
| 复用 | `DeviceRowApple`（第三十轮建的，带手势调音量） |

### 之前 vs 现在

```
之前：每台一张大卡片，里面有 音量滑块 / 播放键 / 遮罩 / 声道 / 预设键 / 组网 / 改名 / 删除
现在：● doss1   在放《034 诸子百家》   47  ›
      点一行 → 进这台的控制面板
      长按   → 菜单（9 项）
```

**一行只放三件事**：在不在（圆点）/ 在放什么（文字）/ 音量（数字，可左右拖）。

### 6 个功能搬去哪了（都保留，没丢）

| 原按钮 | 新位置 |
|---|---|
| 音量滑块 | 行内左右拖 |
| 播放/暂停 | 长按菜单「播放这台 / 暂停这台」 |
| 声道 | 长按菜单「声道：立体声 / 仅左 / 仅右」（点一下切下一个） |
| 遮罩 | 长按菜单「遮罩（本机静音，不改设备）」 |
| 退出同步 | 长按菜单（只在组里时才出现） |
| 预设键 / 固件 | 长按菜单「设备内容」（原有） |
| 改名 / 删除 / 设置 | 长按菜单（原有） |

### 状态文案只有三档

```
离线 / 同步中 / 在放《…》/ 暂停 · … / 待播放 / 待播放 · 电量 18%
```

电量**只在 ≤20% 时出现在行里**，其余时候进详情页 ——
这是「简单状态就够」的具体尺寸。

### 验证

```bash
./gradlew :app:assembleDebug / :app:testDebugUnitTest   # 全量 1073 用例 0 失败
grep 'DeviceCardRow(' app/src/main                      # 已无调用点
```

### 没做（如实记）

1. **`DeviceCardRow`（380 行）成了死代码**，本轮没删 —— 它里面的声道三档、预设键布局还有参考价值，
   下轮确认无遗漏后再归档删除。
2. **「发现」分组没按定稿重排** —— 现有扫描结果区在首页下方，样式还是旧的 Material 卡片。
   定稿要求把它收成一个「发现」分组（新设备各带"添加"）。留下一轮。

## 第四十一轮 · 对齐定稿：发现改常驻 + 归档死代码

**用户追问**：「你有没有按前面讨论定稿的 ui 设计执行」——**自查后确实有三处偏差**。

| 定稿（`docs/93` 屏 1） | 上一轮实际 | 本轮 |
|---|---|---|
| 我的音箱 / 其它播放设备分组 | ✅ | ✅ |
| 一行一台（点/长按/左右拖） | ✅ | ✅ |
| **发现分组常驻** | ❌ 要点「添加音箱」才展开 | ✅ **改成常驻** |
| **遥控设备分组**（红外/蓝牙） | ❌ 完全没做 | ❌ 仍未做（见下） |
| 底部五格 | ❌ 没做（排最后） | ❌ 仍未做 |

### 本轮改动

| 文件 | 动作 |
|---|---|
| `HomeScreen.kt` | 「发现」提为常驻分组（扫描入口 + 已发现的设备，最多 3 条） |
| `DeviceCardRow.kt` | **归档**到 `docs/archive/DeviceCardRow.kt.archived`（380 行死代码） |

「手动填 IP」「AP 配网」仍在「添加音箱」展开区 —— 它们是低频操作，占常驻位置不值得。

### 「遥控设备」分组为什么没做（要用户定）

定稿图里有：

```
遥控设备
  🔴 电视（红外 · 可用）        ›
  🔵 添加蓝牙设备              ›
```

但现实是：**红外码库没接**（`IrRemoteControl` 只有一条探测码）、**蓝牙零代码**。

画出来的话，点进去是空的或按不动 —— 违反本项目「功能不存在的入口不画」。
但**不画**又和定稿不一致。

**所以这里需要用户决定**：先占位（明确标注"未接入"）还是等红外能用再加。

## 第四十二轮 · 定稿写入铁律 + 遥控设备分组（按定稿推进）

用户的第 1、2、3 条指令本轮落地前两条。

### ① 定稿 UI 写进铁律（`docs/52` 新增「铁律四」）

| 小节 | 内容 |
|---|---|
| 4.1 | **五屏结构不可自行增删**（播放/内容/设备/遥控/我），每格回答什么问题 |
| 4.2 | **视觉铁律**（Apple HIG 七条）：不再新增 `AppCard`、大标题、灰阶层级、线性图标、行高 ≥44dp、行内不放解释文字、每屏一个主动作 |
| 4.3 | **「呈现使用」判据**：答不出"用户要完成什么动作"的界面，**该删就删**；具体到"三条进列表、四条进详情" |
| 4.4 | **配置必须分四类**（每台 / 每源 / 全局 / 权限），禁止把"每台"塞进全局设置页 |
| 4.5 | **设备模型**：用户添加、三类（播放/红外/蓝牙）、「输出口」是播放设备的子集 |
| 4.6 | **不累积问题**（用户明确授权）：现在就在误导用户的东西属于 bug，当轮必须处理 |

4.6 里列了已按此执行的三例：删掉没用循环按钮、归档 380 行死代码、界面按钮改走执行点。

### ② 补「遥控设备」分组（甲：占位 + 明确标注）

```
遥控设备
  ⌁ 添加红外设备   码库未接入（电视 / 空调）  ›
  ᛒ 添加蓝牙设备   未实现（遥控 / 耳机）      ›
```

**点了给一句实话**，不是静默：

- 红外：「码库（Flipper-IRDB，30 万条）还没接进来。现在只有一个探测键，用来试这台手机能不能发红外。接好之后这里会变成『选品牌 → 选型号』。」
- 蓝牙：「还是零代码（`docs/84` 只有调研）。接口层已经建好（`RemoteControl`），加实现时遥控页不用改。」

**底线是不骗人**：副标题直写「未接入」，而不是画一个按下去没反应的遥控器。

### 现在的设备页分组顺序（与定稿一致）

```
我的音箱（按能力分组）
其它播放设备
遥控设备          ← 本轮补上
发现（常驻）
或（手动 IP / 配网，展开区）
```

### 验证

```bash
./gradlew :app:assembleDebug / :app:testDebugUnitTest   # 全量 1073 用例 0 失败
python3 scripts/check-doc-refs.py                       # 通过（96 篇）
```

## 第四十三轮 · 遥控页 + 「我」页 + 底部五格（定稿结构一次到位）

用户「继续做」。本轮把**第 4、5 屏和底部结构一起做完** —— 不拆开做的理由写在本节末尾。

### ① 遥控页 `remote/RemoteScreen.kt`（新建）

**画哪些键完全由 `RemoteControl.keys()` 决定**，不是照设计图画。

当前 `IrRemoteControl` 只有一条探测码，所以这页只有一个「电源」圆键。这不是没做完，
是 `DEFAULT_CODEBOOK` 里真的只有一条。接 `Flipper-IRDB` 后 `keys()` 一变，
网格自动长出方向盘 / 音量± / 信号源 —— **这个文件一行都不用改**。

无发射器时 `keys()` 返回空集 → 整块不画，换成一句实话（`EmptyState`）。

**文案底线**：只说「已发送」，不说「已打开电视」—— 红外 `transmit()` 是 void，没有回执。
页面底部常驻一行诊断（`control.diagnoseLine()`），由实现自己给，诊断台不用按类型 switch。

### ② 「我」页 `me/MeScreen.kt`（新建）

三组：**出问题了先看这里**（诊断台，第一项）/ 配置（音箱设置、音源脚本、配网）/ 关于（版本）。

诊断台从「设备设置 → 第四层」提到一级页第一项。这正好对应用户说的
「用户不需要知道全部设备信息」——那是设备页；出问题时需要的恰恰是全量信息。

### ③ 底部五格 `Nav.kt` + `ConsoleApp.kt`

```
播放 · 内容 · 设备 · 遥控 · 我
```

| 改动 | 说明 |
|---|---|
| 删 `Tab.Lx` / `Tab.Sources` | 两者都属于「有什么可放」，合成 `Tab.Content` |
| 新增 `Tab.Remote` / `Tab.Me` | 定稿第 4、5 格 |
| 顺序改为 播放→内容→设备→遥控→我 | 与定稿一致 |

`Tab.Content` 本轮**只做"合并入口"这一层**，复用 `SourcesScreen` 当骨架
（它本来就是"六件事的入口列表"，形状是对的）；真正的内容页（搜索框 + 按源筛选 + 最近播放）下一轮。

`grep 'Tab.Lx\|Tab.Sources'` 归零。

### ④ 设备页接上跳转

「遥控设备 → 添加红外设备」从「弹一段说明」改成**直接进遥控页** —— 有地方可去比弹窗好。

### 为什么这轮把三件事一起做（而不是拆三轮）

定稿里遥控是**独立一格**。如果先做成二级页（从设备页进），过两周改成一级格，
等于把 `Nav` / `ConsoleApp` / 所有路由重写一遍 —— 那正是用户说的「累积问题」。

**结构性的东西一次做完，页面内部再逐轮细化。**

### 验证

```bash
./gradlew :app:compileDebugKotlin / :app:assembleDebug / :app:testDebugUnitTest
# 全量 1073 用例 0 失败
python3 scripts/check-*.py     # 8 个全 PASS
grep -rn 'Tab.Lx\|Tab.Sources' app/src/main   # 0 处
```

### 没做的（如实记）

| 项 | 状态 |
|---|---|
| 内容页内部（搜索框 + 源筛选 + 最近播放） | **未做**，本轮只合并了入口 |
| 底部图标用矢量不用字符 | **未做** —— 项目没引 `material-icons` 依赖，一次全换是单独一轮 |
| 遥控页真机（有没有发射器 / 发出去电视响不响） | **未验** |

## 第四十四轮 · 内容页按定稿重做（第三核心落地）

用户「继续做」。本轮做第 2 屏 —— **三核心里的「内容源」，之前最空的一页**。

### ① 新建 `content/ContentScreen.kt`

```
内容                              3 台在线

[🔍 歌名、歌手，或「周杰伦」              ]

全部  NAS  本地  在线  电台  B站

我的音乐
  手机本地      1,204 首          ›
  家庭音乐共享  2 个               ›
  全部歌单      8 个               ›
  最近播放      45 首             ›

音源
  网络电台      12 个             ›
  在线曲库      已连接             ›
  洛雪音乐      搜歌取直链（脚本）  ›
  哔哩哔哩      未接入             ›
```

顺序是**搜索 → 筛选 → 可用内容**。原来那七个并列入口（本地/NAS/在线/电台/歌单/脚本/搜索中心）
是按代码模块分的，用户脑子里没有这些模块。

### ② 三处设计取舍

| 决定 | 理由 |
|---|---|
| **源筛选而不是分页** | 分页要求用户先知道"歌在哪个源里"，他通常不知道也不关心；筛选的语义是"全给你，想窄再点" |
| **洛雪没有独立筛选项** | 用户原话「在线和洛雪本来就是同个源」，两者都归「在线」 |
| **B 站画出来但直说「未接入」** | 定稿图有筛选位、用户明确说过要体现；但零实现，所以点了弹一句实话，不是死的 |

### ③ 自己抓到并修掉的一个坑（重要）

第一版 `buildRows()` 里八行全是 `onClick = {}`。**编译能过、界面好看、点了一点反应都没有** ——
这正是本项目最忌的失败模式（"命令回 OK ≠ 功能生效"的界面版）。

修法：把回调从参数传进来，并写进注释：

> 上一版这里先写成 `onClick = {}`，编译能过、界面能看，但点了什么都不会发生。

### ④ 第二处自查：差点绕过已有测试

第一版把这八行的数量文案（单位、「已连接/未配置」、空状态提示）**重写了一遍**。
但那些是**产品规则**，`core-protocol` 的 `MyMusicSummary` 已经给了，还有单测钉着。

重写等于绕过测试，以后改文案要改两处 → 改成复用 `MyMusicSummary`，
本页只补三件它不知道的事：**归哪组 / 算哪个源 / 点了去哪**。

### ⑤ 删掉两个被取代的页面（归档，不是直接删）

| 文件 | 行数 | 为什么能删 |
|---|---|---|
| `SourcesScreen.kt` | 46 | 纯中转（转发到 MyMusicScreen），docs/93 明确列为"删" |
| `library/MyMusicScreen.kt` | 232 | 被 `ContentScreen` 取代 |

两文件移到 `docs/archive/`。`grep 'MyMusicScreen\|SourcesScreen' app/src/main` 只剩注释（已清）。

### ⑥ 搜索框接上统一搜索

`SearchHubScreen` 新增 `initialKeyword` 参数：内容页输入 → 回车 → 带着词进统一搜索页，
开页即搜（`LaunchedEffect`）。**不在内容页出结果** —— 搜索结果有自己的排序、来源标注、
失败说明，挤一页会让"浏览"和"搜索"两种状态互相污染。

### 验证

```bash
./gradlew :app:compileDebugKotlin / :app:testDebugUnitTest / :app:assembleDebug
# 全量 1073 用例 0 失败
python3 scripts/check-*.py     # 8 个全 PASS
grep 'MyMusicScreen|SourcesScreen' app/src/main   # 仅注释
```

### 没做的（如实记）

| 项 | 状态 |
|---|---|
| **定稿里「接着听 / 最近加入」两个分组** | **未做** —— 现在只有 `recentCount`（数量），没有条目列表。要先建"最近播放"存储 |
| 「接着听」按设备分组（doss1 / doss3） | 同上 |
| 内容页真机（筛选手感 / 搜索跳转） | **未验** |

## 第四十五轮 · 「接着听」+「最近加入」（严格对齐定稿）

用户「继续严格按 ui 定稿做」。

### ① 先自查出一处**上一轮我自己加的、定稿里没有的分组**

上一轮内容页我排成「我的音乐 + 音源」两组。回去逐字比对定稿图（`docs/mock/ui-mock-v5.html` 179–212 行），
定稿内容页只有四块：

```
内容
[🔍 歌名、歌手，或「周杰伦」   ]
全部  NAS  本地  在线  电台  B站
接着听
  034 诸子百家   7:45   doss1
  晴天           洛雪   在线
最近加入
  叶惠美（整张）  11 首
  中国之声        电台
```

**没有「我的音乐 / 音源」。** 那是我按"六源入口"的老习惯自己加的。

本轮的处置：
- 按定稿顺序排前四块（搜索 → 筛选 → 接着听 → 最近加入）
- 源入口**保留**但压到最后一组、标题统一成「全部来源」

为什么没直接删：**筛选条只能筛列表，不能当导航** —— 删掉就再也进不去 NAS / 本地曲库 / 歌单。
这是功能可用性的下限。**这条偏差如实记在这里**，不假装定稿有。

### ② 「接着听」怎么来的

数据本来就有，但缺两块：

| 缺什么 | 怎么补 |
|---|---|
| 写入点不全 | `PlayJournal` 已是统一入口（NAS / 队列 / 本机都走它） |
| **不知道投给了哪台设备** | `tb_last_played` 加 `device_ip` 列（DB v2 → v3） |

**加列不重建**：`alter table tb_last_played add column device_ip text`。
`tb_last_played` 里是用户的历史，drop 掉等于清空他的记忆。旧行这一列是 null，
读的时候降级成"不显示设备名"，不会崩。

### ③ 一个刻意的架构决定：设备不放进 `TrackRecord`

`TrackRecord` 的契约是「**与设备无关**的曲目记录」。而"这首投给了谁"是**播放事件**的属性。

所以新开 `RecentPlay(track, deviceIp, playedAt)`：
- 曲目是曲目，播放事件是播放事件
- 以后加"投给音箱还是本机"不用再往 `TrackRecord` 里塞字段

`RecentStore` 上是 **default method**（`recentWithDevice` / `recordWithDevice`），
降级成旧方法 —— 纯内存实现和任何旧实现不用改就能编译。

### ④ 顺带补上一个缺口：本机播放原来不进「最近」

`PlaybackRouter.playOne` 的 `Local` 分支之前只 `local.play()`，不记最近。
后果是**用手机听这件事从不进历史**，「接着听」里永远只有投给音箱的那几首。

已补，`deviceIp = null`（本机不属于任何一台设备），界面第三列降级显示来源标签。

### ⑤ 「最近加入」怎么拼的

定稿这一组混了电台和歌单，`core-protocol` 里没有（也不该有）这种混合类型 →
在 app 层加 `RecentAdd(title, kind, detail)`，取「最近收藏的电台 2 条 + 最近改的歌单 2 条」，
合起来截 3 条。

电台表没有时间戳（原厂表结构就没有），按 id 倒序近似"最近"—— 这一点在代码注释里写明了。

### ⑥ 测试抓出的一个真实缺陷

给 `RecentStore` 的默认实现写测试时，第一版断言 `playedAt == 1000L` **失败**。

原因不是默认实现写错了，是**我写的那个测试替身不合契约**：`record(track, playedAt)`
只存了 pair，没有把 `playedAt` 回填进 `track.firstPlayedAt`。
而 `recent()` 返回的记录里 `firstPlayedAt` 有值是 `RecentStore` 契约的一部分
（`TrackRecord` 里那个字段存在的唯一理由就是给最近播放排序）。

修的是测试替身，不是生产代码 —— 但**这个失败说明默认实现依赖的实现方契约是隐性的**，
已在测试注释里写明。

### 验证

```bash
./gradlew :core-protocol:test / :app:testDebugUnitTest / :app:assembleDebug
# 1078 用例 0 失败（+5：RecentStore 默认实现 2 + 时长格式化 3）
python3 scripts/check-*.py     # 8 个全 PASS
```

### 没做的（如实记）

| 项 | 状态 |
|---|---|
| 「接着听」真机（点了真的会再投一次吗） | **未验** |
| 老数据的 `device_ip` 是 null | 旧记录显示的第三列是来源标签（"在线"），不是设备名 |
| 「最近加入」的电台没有真实时间 | 按 id 倒序近似 |

## 第四十六轮 · 设备页逐条对齐定稿（用户截图指出）

用户发来真机截图问「与定稿设计图有没有不一致」。逐字比对后**有 9 处**，本轮全改。

### 对照表

| # | 截图现状 | 定稿 | 处置 |
|---|---|---|---|
| 1 | 大标题「我的音箱」+「2 台在线」，下面分组标题**又一次「我的音箱」** | 大标题「设备」，分组才是「我的音箱」 | 大标题改 **「设备」** |
| 2 | 顶上一个**聚合播放条**（歌名 + E900V22C + ▶ + ⧉） | 无 | **删** |
| 3 | 聚合条下「点 ⧉ 勾选要同步的音箱」 | 无 | **删**（行内解释文字） |
| 4 | 右上角 `+` `↻` `☾` 三个圆按钮 | 无 | **删**（`+` 与下面「添加音箱」重复；`↻` 刷新并入「扫描」） |
| 5 | 中间一个「添加音箱」大按钮 | 无 | **删**，改成「发现」组最后一行「扫不到？手动填 IP / 配网」 |
| 6 | 底部「▶ 全部播放」大按钮 | 无 | **删** |
| 7 | 设备名截成 `DOSS…` `E900…` | `doss1` `doss2` `doss3` 完整 | 名字宽度写死 76dp → 改按比例 **weight(0.45f)** |
| 8 | 离线设备右侧显示 `0`，而且**还能左右拖** | 在线才显示音量 | 离线显示 **`—`**、**不响应拖动** |
| 9 | `doss1  暂停 · 046【秦】揭手而…` | `doss2  暂停` | 暂停时**只写「暂停」**，不拼歌名 |

### 三处「删了之后没有入口」的补救

删掉的东西里有三个是有功能的，删之前先找好了去处：

| 删掉了什么 | 功能去了哪 |
|---|---|
| 聚合条里的 **⧉ 组网** | 长按菜单新增「**组合同步（勾选其它音箱）**」 |
| 右上角 **↻ 刷新** | 「发现 → 扫描附近设备」本来就会重新探活 |
| **添加音箱** 大按钮 | 「发现」组最后一行「扫不到？手动填 IP / 配网」（`showAdd` 展开区仍在） |

组网这一条最关键：**三台一起响是核心功能**，聚合条按定稿删掉之后如果不在别处补，这个功能就没入口了。
按 `docs/93` 第十二节的苹果交互原则（常驻按钮收进长按菜单），补在菜单里是对的。

### 顺带清掉的死代码

| 文件 | 清掉 |
|---|---|
| `HomeScreen.kt` | `CircleIcon` / `SectionTitle` 两个 import（改大标题后不用了）、`onOpenPlayer` 参数（聚合条删了，没人调它） |
| `ConsoleApp.kt` | `onOpenPlayer = { tab = Tab.Player }` 传参 |

### 为什么设备页现在没有播放控制了

删完后设备页只剩：**分组设备列表 + 遥控设备 + 发现**。

这是刻意的：原来这一页有**四个**地方能播/停（聚合条、全部播放大按钮、底部常驻条、长按菜单），
三个入口干同一件事，用户会犹豫点哪个。定稿的划分是：

| 页 | 管什么 |
|---|---|
| 设备 | 有哪些设备、各自什么状态、加设备、组网（长按） |
| 播放 | 放什么、怎么放 |
| 常驻条 | 一眼看到在放什么、一键停 |

### 验证

```bash
./gradlew :app:compileDebugKotlin / :app:testDebugUnitTest / :app:assembleDebug
# 全量 1078 用例 0 失败
python3 scripts/check-*.py     # 5 个关键的全 PASS
```

### 仍不一致的（如实记）

| 项 | 状态 |
|---|---|
| `⌁` `ᛒ` `◎` 等**字符当图标** | 未改 —— 项目没引 `material-icons` 依赖，一次全换是单独一轮 |
| 定稿「遥控设备」组里有一台真设备（`电视 · 红外 · 可用`） | 只有占位两行（码库没接） |
| 设备行右侧 chevron `›` | 定稿有，保留 |
| **改完的效果未真机验收** | 待用户装机确认 |

## 第四十七轮 · 状态栏图标随主题自动变深/变浅（用户截图指出的白底白字）

用户指出：白色背景下最上方的时间/电量被盖住了，问有没有通用办法。

### 根因是**两件事**凑在一起

| # | 问题 | 位置 |
|---|---|---|
| 1 | 系统主题写死 `android:Theme.Material.Light.NoActionBar` —— 不随深色模式变 | `res/values/themes.xml` |
| 2 | 代码里**从来没设过** `isAppearanceLightStatusBars`，系统按主题默认给**浅色图标** | 全项目零处 |

后果：App 自己是白底（`#F7F9FC`），顶上那排也是白的 —— 白底白字。

另外还发现一个没人提但真实存在的：**没有 `values-night`**，所以深色模式下
系统自己的东西（键盘、权限弹窗、启动白闪）仍然是浅色。

### 修法：三处，全是标准做法

#### ① `LinkPlayTheme` 里按主题设图标明暗

```kotlin
SideEffect {
    val window = view.context.findActivity()?.window ?: return@SideEffect
    val controller = WindowCompat.getInsetsController(window, view)
    controller.isAppearanceLightStatusBars = !darkTheme      // 浅色背景 → 深色图标
    controller.isAppearanceLightNavigationBars = !darkTheme
    window.statusBarColor = scheme.background.toArgb()
    window.navigationBarColor = scheme.background.toArgb()
}
```

**这就是业界通用做法**，不是我自己想的：Android 从 API 23 起给
`SYSTEM_UI_FLAG_LIGHT_STATUS_BAR`，现在统一走 AndroidX 的
`WindowInsetsControllerCompat.isAppearanceLightStatusBars`（true = 背景浅、图标深）。
所有正规 App 都是这么做的 —— 不需要按厂商/机型写分支。

放在**主题层**而不是 `MainActivity`：它是主题的属性。以后加"浅色 / 深色 / 跟随系统"
手动切换时，这里一行不用改就会跟着变。

#### ② 加 `values-night/` 一套

`themes.xml` + `colors.xml` 各有 night 版本，父主题换成 `android:Theme.Material.NoActionBar`。
解决深色模式下系统组件仍是浅色、启动闪白的问题。

#### ③ 背景色三处对齐

| 位置 | 浅色 | 深色 |
|---|---|---|
| `LightColors/DarkColors.background`（Compose） | `#F7F9FC` | `#0A0C11` |
| `values/colors.xml` → `app_background` | `#F7F9FC` | `#0A0C11` |

**为什么必须对齐**：`targetSdk = 35`，Android 15 起**强制 edge-to-edge**，
状态栏透明，露出来的就是 window background。不同色的话顶上会有一条色差带
（用户截图里那条浅灰就是这么来的），启动瞬间也能看到。

### 一个顺带修掉的坑

`findActivity()` 用 `tailrec` 沿 `baseContext` 往上找，而不是 `view.context as? Activity`。

Compose 的 View 上下文常被 `ContextThemeWrapper` 包一层，直接强转会拿到 null，
于是状态栏设置**静默失效** —— 界面看着正常、就是颜色不对，很难查。

### 我自己踩的一个坑（如实记）

写 `values/colors.xml` 时**直接覆盖了整个文件**，把原有的
`ic_launcher_background` 弄丢了，`processDebugResources` 报：

```
AAPT: error: resource color/ic_launcher_background not found
（mipmap-anydpi-v26/ic_launcher.xml / ic_launcher_round.xml）
```

教训：**改 XML 资源前先看原文件内容**，哪怕只加一行。

### 验证：用 aapt2 确认资源真的进了包

```bash
aapt2 dump resources app-debug.apk | grep -A6 'style/Theme.LinkPlayConsole'
```

```
()      (style) size=3 parent=0x01030241   ← Material.Light.NoActionBar
(night) (style) size=3 parent=0x0103022e   ← Material.NoActionBar
        0x01010054 = @color/app_background

color/app_background
()      #fff7f9fc
(night) #ff0a0c11
```

两条配置都在、父主题按 night 正确切换、windowBackground 指向我们的颜色。

### 没验的

| 项 | 状态 |
|---|---|
| 真机浅色模式（图标是否真的变黑） | **未验** |
| 真机深色模式（图标是否变浅、系统组件是否跟着深） | **未验** |
| Android 15 上 edge-to-edge 的实际观感 | **未验**（targetSdk 35 会强制） |

## 第四十八轮 · 「我」页加主题切换（跟随系统 / 浅色 / 深色）

用户：「在设置里就按通用软件加个主题切换」。

### ① `ui/theme/ThemePrefs.kt`（新建）

```kotlin
enum class ThemeMode(val id: String, val label: String) {
    System("system", "跟随系统"),
    Light("light", "浅色"),
    Dark("dark", "深色"),
}
```

默认 **跟随系统**。理由：绝大多数人只在系统里设一次深浅，App 跟着走就对了；
给一个固定默认值等于让用户在两处各设一次，还可能互相矛盾。

用 `SharedPreferences` 而不是数据库：这是一行配置，读写频率低，
而且**首帧就要用**（`setContent` 之前），数据库首次打开可能慢到能看见白屏。

暴露 `StateFlow<ThemeMode>`：切主题要**立刻生效**（包括状态栏图标），
改一处整棵树重绘，不需要任何页面自己去通知别处刷新。

### ② 「我」页加「外观」组

```
外观
  主题    跟随系统   ›
```

点开是三选一（选中项前面打勾，不用 RadioButton —— 一行文字比一行单选圈更接近苹果的写法）。

### ③ 一个必须处理的边角：手动主题 vs `values-night`

`targetSdk = 35` 起强制 edge-to-edge，状态栏透明，**露出来的是 window background**。
而 `values/` 和 `values-night/` 里的那份是**按系统深色**选的。

于是会出现：用户手动选「浅色」、但系统是深色 → 状态栏那条仍是深色，和 App 的浅色背景撞出边界。

修法是在 `LinkPlayTheme` 的 `SideEffect` 里覆盖掉：

```kotlin
window.setBackgroundDrawable(ColorDrawable(scheme.background.toArgb()))
```

**顺手把上一轮的隐患补严**：现在手动主题也保证三处同色（Compose 背景 / 系统栏 / 窗口底色）。

### ④ 测试锁住的两条规则

| 规则 | 写反了会怎样 |
|---|---|
| 「跟随系统」真跟随 | 系统切深色 App 不变 |
| 手动选择能压过系统 | "我明明选了深色还是白的" |

第二条**只在用户系统设置正好相反时才会暴露**，极难复现 —— 所以必须有测试。

### 验证

```bash
./gradlew :app:testDebugUnitTest / :app:assembleDebug
# 全量 1081 用例 0 失败（+3）
```

### 没验的

| 项 | 状态 |
|---|---|
| 真机切主题（浅色 ↔ 深色 ↔ 跟随系统，状态栏图标是否跟着变） | **未验** |
| 手动浅色 + 系统深色时状态栏那条是否真同色 | **未验** |

## 第四十九轮 · 播放页按定稿重排（列表收成入口行 + 主控三键）

用户：「保留音量条，继续做」。其余三条按定稿改。

### 改了什么

| # | 定稿 | 改前 | 改后 |
|---|---|---|---|
| 1 | `doss1 · 投送中` 一行 | 没有（只有小字 IP） | **新增**投送状态行 |
| 2 | 播放列表 = **一行入口** | 页面中部常驻，占满 weight | 一行入口 → 点开**全屏列表** |
| 3 | 主控 = **三个键** | 五个（循环 / 上一首 / 播放 / 下一首 / 停止） | 三个；循环与停止搬进列表页底部 |
| 4 | 顺序 | 封面→标题→列表→进度→音量→控制 | 封面→标题→**状态**→进度→音量→**三控制**→**列表入口** |

**音量条按用户指示保留**（定稿里没有）—— 这是本轮唯一一处刻意偏差，理由：调音量是播放页最高频的动作，删了要靠设备页拖或音量键。

### 三处判断的理由

**① 投送状态行为什么提到标题正下方**

用户最常见的困惑不是"没有播放按钮"，是"按了播放却没声音"——因为声音投到了另一台设备。
原来这个信息藏在顶栏小字里。定稿把它放在标题下第二行，是对的。

**② 播放列表为什么该收起来**

定稿这一页是**遥控器**，不是文件管理器。一眼要看到的是封面、进度、三个键；
选第几首是低频动作，值得多一次点击。原来列表占掉中部整块 weight，
结果是封面被压小、控制区被挤到最底 —— 三件事互相抢空间。

**③ 循环与停止为什么搬走**

- **循环**要连点三下才回到"不循环"，而且状态只在图标上体现（用户看不出来现在是哪种循环）
- **停止**在没有外部输入的场景下和"暂停"几乎同义

主控一排摆五个平级按钮，每次都要想一下点哪个。搬到列表页底部后，
循环还顺便从"图标"改成了**文字**（`循环：列表`），状态一眼可读。

### 全屏列表（`PlaylistSheet`）

- 全屏 `Dialog` + `usePlatformDefaultWidth = false`
- 保留原有的「播放列表 / 投流与日志」切换（调试面板没删）
- 保留设备队列同步状态（`设备 100 首 · 第 8 首`）
- 底部：循环（文字）+ 停止

### 验证

```bash
./gradlew :app:compileDebugKotlin / :app:testDebugUnitTest / :app:assembleDebug
# 全量 1081 用例 0 失败
python3 scripts/check-*.py     # 5 个关键全 PASS
```

### 没验的

| 项 | 状态 |
|---|---|
| 真机播放页布局（封面大小 / 疏密） | **未验** |
| 全屏列表打开/关闭手感 | **未验** |
| 循环按钮搬走后状态是否还正确下发 | 逻辑未动，但**未真机验** |

## 第五十轮 · 模型 A 落地：单一播放目标（本机 + 设备）

用户拍板「按 A 做」。`docs/96` 的调研结论变成代码。

### ① 唯一真值

`PlaybackOutputStore` 本来只有"读文件"，现在多了一条流：

```kotlin
private val _current = MutableStateFlow<PlaybackOutput?>(null)
val current: StateFlow<PlaybackOutput?>
fun prime(context)      // 进程启动读一次
fun write(...)          // 写文件 + 更新流（值相同时 StateFlow 不发射，所以不会循环）
```

任何一处改目标，所有观察者立刻拿到，不用轮询文件。

### ② 双向同步（这是拆掉"两套状态"的关键）

| 方向 | 谁做 | 做法 |
|---|---|---|
| 点设备 → 设为目标 | `open()` / `select()` | 顺带 `write(Device(ip, name))` |
| 目标改成设备 → 切过去 | `ConsoleViewModel.init` | collect `current`，IP 变了就 `open(那台)` |

两个方向收敛到同一个值，**不会再出现「选中 doss2 但投给 doss1」**。

`_state.value.selectedIp == ip` 时直接 return —— 这是防循环的那道闸。

### ③ 设备页加「本机」

大厂都把本机当成目标列表里**平等的一项**（Spotify "This phone"、AirPlay "This iPhone"）。

```
本机        这台手机自己放                     ← 插在第一个非空分组之前
我的音箱
  ● doss1  暂停  正在用  56  ›
```

点「本机」= 设目标为本机 + 进播放页。

`DeviceRowApple` 新增 `inUse` 参数：**当前目标那行右侧显示「正在用」**。

### ④ 内容页加「投给」

```
内容
投给   [本机] [doss1] [doss2] [doss3]     ← 新增，横向可滚
[🔍 歌名、歌手，或「周杰伦」]
```

和播放页的「从哪出声」是**同一个真值**。内容页原来是"点了就投，投给谁不显示"——那正是"投错设备"的根源。

### 与定稿的偏差（如实记）

`docs/93` 定稿的**内容页没有「投给」这一行**（设备页也没有「本机」行）。
这两处是本轮按 `docs/96` 调研新增的，用户已明确要求按模型 A 做。

已记入本节，定稿图未同步更新。

### 验证

```bash
./gradlew :app:compileDebugKotlin / :app:testDebugUnitTest / :app:assembleDebug
# 全量 1081 用例 0 失败
python3 scripts/check-*.py     # 5 个关键全 PASS
```

### 没做的 / 没验的

| 项 | 状态 |
|---|---|
| **播放页按大厂重排（A4）** | **未做**，下一轮 |
| 目标离线时的拦截 | **未做** —— 现在照投，失败靠现有错误提示 |
| 老数据 `remote`（跟随选中）迁移 | **未做** —— 仍保留"跟随"中间态 |
| 真机（本机行 / 正在用标记 / 投给 chips） | **未验** |

## 第五十一轮 · 播放页对齐大厂：目标选择挪到底部

用户：「播放器页面 ui 也可以抄大厂的」。

### 改了什么

「从哪出声」从**顶栏**挪到**最底部**（列表入口下面）。

```
[封面]
034 诸子百家 · 儒家孔子
doss1 · 投送中
━━━━━━━━━
7:45        10:41
音量 ━━━━━ 56

   ⏮     ⏸     ⏭

播放列表 · 100 首 · 第 8 首  ›

从哪出声  本机  doss1  doss2  doss3     ← 挪到这里
```

### 为什么挪

| 依据 | 说明 |
|---|---|
| **Spotify** | Connect 图标就在底部播放条里 |
| **Sonos** | 当前房间在视觉底部 |
| **定稿**（`docs/93` 屏 2） | 「从哪出声」本来就画在播放列表入口下面 |

三方一致。而且语义上：顶栏是"这页是什么"，目标选择是"东西往哪去" —— 不同层，不该放一起。

### 现在的目标一致性

| 位置 | 显示 | 改它等于 |
|---|---|---|
| 设备页 | 每行「正在用」 | 点那台 = 设目标 + 切过去 |
| 内容页 | `投给 [本机][doss1]…` | 改目标 |
| 播放页 | `从哪出声 [本机][doss1]…` | 改目标 |

三处读写的是**同一个 `PlaybackOutputStore.current`**。

### 验证

```bash
./gradlew :app:testDebugUnitTest / :app:assembleDebug   # 1081 用例 0 失败
python3 scripts/check-output-switch.py                  # 通过（只在播放页）
```

### 没验的

真机：目标切换后，设备页「正在用」、内容页「投给」、播放页「从哪出声」三处是否**同时**变。

## 第五十二轮 · 三个导航 bug（`docs/97` P1 全部修完）

用户：「同意做，先把 ui 理顺」，并指出底部播放条没处理干净（附截图：内容页底部挂着「未在播放 · 第 1/1 首」，还压住最后一行）。

### ① 底部播放条：没在放就不显示（用户截图那条）

原条件是 `queue.nowPlaying != null || queue.running`。

**占位队列**（`QueueRestore` 记着"上次放到第几首"，但内容已失效）两条都满足 ——
于是没在放的时候底部挂着一条「未在播放」，还挡住内容。

改成三个条件同时成立：

| 条件 | 判据 |
|---|---|
| 有当前曲目且**不是占位** | `queue.nowPlaying != null && !QueueRestore.isPlaceholder(current)` |
| **本机播放也算** | `localUi.hasContent`（投本机时队列是空的，只看 queue 会漏） |
| 真的在放或暂停中 | `running / deviceOwned / playingNow / hasLocalTrack` |

**大厂不会出现"未在播放"的播放条 —— 没有在放，就没有这条。**

### ② 诊断台返回落到错页（`docs/97` 问题 2）

`onBack` 原来写死 `sub = SubPage.Settings`，但诊断台有**三个入口**。
从「我」页进去，返回落到「设置」页。

改成记录来路：`diagReturnTo`，三处入口各自传来源，返回时恢复。

### ③ 任何 Tab 按返回都跳设备页（`docs/97` 问题 3）

```kotlin
// 旧
BackHandler(enabled = sub != null || tab != Tab.Devices) { ... else -> tab = Tab.Devices }
// 新
BackHandler(enabled = sub != null) { sub = null }
```

Tab 上不再拦截 → 系统默认行为（退出 App）。

| 平台 | Tab 根上按返回 |
|---|---|
| Android 标准 / Spotify / Gmail / YouTube | 退出 App |
| iOS | 无效果 |
| **我们（改前）** | **跳到设备页** ← 独一无二的反例 |

顺手也不再丢掉当前 Tab 的滚动位置。

### 验证

```bash
./gradlew :app:compileDebugKotlin / :app:testDebugUnitTest / :app:assembleDebug
# 全量 1081 用例 0 失败
python3 scripts/check-*.py     # 全 PASS
```

### 没验的

| 项 | 状态 |
|---|---|
| 真机：内容页底部条是否真的消失 | **未验**（截图反馈的就是这条） |
| 真机：我 → 诊断台 → 返回是否回「我」 | **未验** |
| 真机：播放页按返回是否退出 App | **未验** |
| `docs/97` 的 P2（每 Tab 一个栈） | **未做** |

## 第五十三轮 · 播放页「从哪出声」改成左下角投屏图标

用户：「播放器界面输入源和从哪出声重复，我倾向于从哪发声，并且以左下角类似于大厂投屏的符号，点开可选才合理」。

### ① 顶栏不再念"输入源"

原来顶栏固定写 `输入源 网络播放 · 播放中` —— 和底部的「从哪出声」看起来像同一件事说两遍。

改成**只在不是网络播放时才提**：

| 音箱当前 mode | 顶栏显示 |
|---|---|
| 网络播放（日常） | `正在播放` / `已暂停` |
| 线路输入 / 蓝牙 / U盘 / 光纤 | `输入源 线路输入 · 播放中` |

为什么保留后者：音箱切到 AUX 时，用户在 App 里点播放没声音，**必须有人告诉他"声音在别的路上"**。
但日常那一路不用说 —— 说了就是噪音。

### ② 底部一整行 chips → 左下角一个投屏图标

```
   ⧉       ⏮     ⏸     ⏭
  本机
```

- 图标是 **Canvas 画的 cast 形状**（屏幕 + 同心弧），项目没引图标库
- 图标下面显示**当前目标名**（`本机` / `doss1`），一眼知道投给谁
- 点开是 `ModalBottomSheet`：本机 + 各设备，当前项打勾

大厂对照：Spotify 底部 cast 图标、Apple Music 的 AirPlay 图标、YouTube 的 cast 按钮 ——
都是**一个图标 + 点开一层列表**，不占常驻空间。

### ③ 一处约束被有意放宽（已写进注释）

`docs/37` 原来写「播放页不许任何浮层」。

原意是**不许把控制面板藏进浮层**（那样每次调音量都要开关一次）。而"选目标"是低频、
且必须弹出一层才看得全的动作 —— `docs/97` 把它归为 sheet 类（临时任务层），不算违反。

选中后**立刻生效并重建 MediaSession**（`PlaybackService.restart`），
因为 MediaSession 建好之后不能动态换 player。

### ④ 顺手删掉死代码

`OutputSwitchRow()`（约 90 行，含大段注释）已经没人调用 —— 整段删除。
`grep OutputSwitchRow` 归零。

### 验证

```bash
./gradlew :app:compileDebugKotlin / :app:testDebugUnitTest / :app:assembleDebug
# 全量 1081 用例 0 失败
python3 scripts/check-*.py     # 全 PASS（check-output-switch 仍通过）
```

### 没验的

| 项 | 状态 |
|---|---|
| 真机：cast 图标的形状是否认得出 | **未验**（Canvas 手绘，可能需要调比例） |
| 真机：sheet 弹出/选择/关闭手感 | **未验** |
| 真机：选完是否真的换了输出（声音去了新设备） | **未验** |

## 第五十四轮 · 修「顶栏是 E900V22C、左下角却是本机」

用户截图：顶栏 `E900V22C-175[DLNA]`，左下角 cast 写着「本机」，并说「应该是本机换不出，或者其他问题」。

### 根因：模型 A 的同步漏了**启动恢复**这条路

`selectedIp` 有三个写入点，我只在两个上面挂了同步：

| 写入点 | 同步目标了吗 |
|---|---|
| `open(device)`（设备页点一台） | ✅ |
| `select(ip)` | ✅ |
| **`SessionRestore.restore()`（启动恢复上次那台）** | ❌ **漏了** |

于是：上次手动选了「本机」→ 关 App → 重开 → 恢复出 `selectedIp = E900V22C`，
但目标还停在文件里的 `local`。**顶栏读 selectedIp，左下角读目标 —— 两边对不上。**

修法：`restore()` 之后调 `syncTargetToSelected()`，并把它抽成统一方法写清
「什么时候不该调」（用户显式选本机时不能覆盖回去）。

### 顺带修的四处

| # | 问题 | 修法 |
|---|---|---|
| 1 | 顶栏名字来自 `state.friendlyName`，cast 来自 target —— 两个来源 | 抽 `targetLabel`，**两处都用它** |
| 2 | 输入源显示「DLNA 渲染器」（协议名当输入源） | 只在**拿得到 LinkPlay 模式**时才提输入源；通用渲染器没有 mode → 不提 |
| 3 | 曲名下「E900V22C-175[DLNA] · 待播放」重复设备名 | 状态行只写状态 |
| 4 | `TargetSheet` 内部自己读设备列表，读不到就只剩「本机」→ **"换不出"** | 改成从外面传已读好的列表 |

第 4 条很可能就是用户说的"换不出"——sheet 里只有「本机」一项，自然选不了别的。

### 验证

```bash
./gradlew :app:compileDebugKotlin / :app:testDebugUnitTest / :app:assembleDebug
# 全量 1081 用例 0 失败
python3 scripts/check-*.py     # 全 PASS
```

### 仍没解决的（如实记）

| 项 | 说明 |
|---|---|
| 真机验证 | **未验** —— 这轮的判断全是读代码推出来的 |
| 顶栏仍显示 `E900V22C-175[DLNA]` 这种长名字 | 那是设备自报的 friendlyName，不是我们起的名字 |
| 曲名「设备上的第 1 首」 | 设备侧队列的占位标题，不是 bug |
| `docs/96` 说的"老数据 remote 迁移" | 仍未做，`Device("")` 那种跟随态还在 |

## 第五十五轮 · 播放列表「为什么全是序号」+ 功能接通审计

用户：「播放列表没有跟进，而且我需要功能的跟进，而不只是 ui 上有，但是实际没用」。

### ① 查清了：那份 100 首不是我们推的

`QueueRestore.plan()` 的三条分支：

| 条件 | 结果 |
|---|---|
| 设备列表长度 == 本地快照长度 | 用**快照**（有真实歌名） |
| 设备有队列但长度对不上 / 没快照 | **占位**（"设备上的第 N 首"） |
| 设备没队列 | 用快照（上次的列表） |

截图那台 E900V22C 是**设备自己握着 100 首**（原厂 App 或之前留下的），
我们从来没推过这份清单 → 走第二条 → 100 行全是"设备上的第 N 首"。

**而且 LinkPlay 协议里没有"读取播放列表内容"的命令**（`docs/01` 只有
`setPlayerCmd:playlist` 写入，没有对应查询）—— 这是协议层的硬限制，不是没写。

### ② 真正的 bug：我把说明弄丢了

`queue.sourceNote` 本来会告诉用户「设备上有 N 首正在播，但本机没存到这份曲目单」。
上一轮把列表搬进全屏 sheet 时，**这行说明没跟过去** —— 于是用户看到 100 个同名条目，
没有任何解释。这正是"UI 有了但看起来没用"。

已补回，并加了一句可执行的建议：

> 音箱自己握着这份列表，但它不告诉我们每首是什么。
> 想看到歌名，从 NAS / 曲库 / 洛雪那边自己投一次 —— 那样列表就是我们的。

### ③ 功能接通审计（每个可点元素背后的真实方法）

| 界面动作 | 背后实现 | 命中文件数 |
|---|---|---|
| 播放 / 暂停 | `PlayIntent.TogglePlay` → dispatcher → 设备命令 | 3 |
| 上一首 / 下一首 | `PlayIntent.Next/Previous` + 能力位判断 | 4 |
| 进度拖动 | `seekTo` | 3 |
| 音量拖动 | `setVolumeLive` → `SetVolumeUseCase`（60ms 合并） | 3 |
| 列表点歌 | `playQueueItem`（占位时发 next/prev，真实时 PlayQueueWithIndex） | 3 |
| 接着听 | `onReplay` → `PlaybackRouter.playOne` | 2 |
| 设备行拖动音量 | `setCardVolume` | 3 |
| 切输出目标 | `PlaybackOutputStore.write` + `PlaybackService.restart` | 4 |

**八条链路代码层面都是接通的**，没有"画了不接"的。

### ④ 但"没用"的真实原因是另一件事：**大部分没在真机上验过**

`docs/91` 里累计的真机欠账已经十几项。UI 改得再多，只要有一条链在真机上不通，
观感就是"点了没用"。

### 验证

```bash
./gradlew :app:testDebugUnitTest / :app:assembleDebug   # 1081 用例 0 失败
```

### 没验的

| 项 | 状态 |
|---|---|
| 播放列表 sheet 里的说明是否显示 | **未验** |
| 占位列表点第 N 首是否真的让音箱跳过去 | **未验** |

## 第五十六轮 · 修真机崩溃：PlaybackService 创建失败

用户贴了 0.9.193 的诊断报告，第一条就是崩溃：

```
java.lang.RuntimeException: Unable to create service PlaybackService:
  java.lang.IllegalArgumentException: Empty playlist only allowed in STATE_IDLE or STATE_ENDED
线程：main
```

### 根因

`MediaSession.Builder(this, player).build()` —— media3 在构建会话时会去读 player 的当前项。
**处在 READY / PAUSED 但媒体列表为空的 player 会直接抛这个异常。**

这是在 `Service.onCreate` 里抛的 → 没人接 → Android 报 `Unable to create service` → **整个 App 崩**。

一个"通知栏控件建不起来"的小问题，被放大成了闪退。

### 两层修法

**① 前置判断（从根上不进那条路）**

```kotlin
PlaybackOutput.Local -> {
    val p = router.local.raw()
    if (p.mediaItemCount > 0 || p.playbackState == STATE_IDLE) p else null
}
```

列表空且不在 IDLE → 不交出 player → 上层 `player == null` 分支走 `stopSelf()`，不建会话。

**② 兜底 catch（防同类问题再升级成闪退）**

```kotlin
session = runCatching { MediaSession.Builder(this, player).build() }.getOrNull()
if (session == null) { diagnostics("..."); stopSelf() }
```

建不出会话只影响通知栏控件，**本地播放照样出声**，不该闪退。

失败路径按 `docs/52` 铁律一记一条（进 logcat `LinkPlayDiag`）。

### 报告里另外两个值得注意的

| 观察 | 说明 |
|---|---|
| 5 台设备里 4 台「未探测 / 全部未应答」 | 报告只跑了 2 秒，`probeSaved()` 还没跑完就出报告了 —— **不是设备离线** |
| `[启动恢复] 来源=本地快照` 但 `[队列] 来源=设备上有 100 首…占位` | **两处不一致，说明恢复出的快照后来被轮询覆盖成了占位** —— 单独一轮查 |

第二条是新发现的问题：既然本地有 100 首的快照（长度和设备报的 100 一致），
`QueueRestore.plan` 的 `snapshotMatches` 就该成立、用真实歌名。现在却退化成占位，
说明第二次调用时 `snapshot` 传的是 null。**下一轮处理。**

### 验证

```bash
./gradlew :app:compileDebugKotlin / :app:testDebugUnitTest / :app:assembleDebug
# 1081 用例 0 失败
```

## 第五十七轮 · 「播放列表没跟进」的真根因：switchTo 早退吞掉了 uuid

`docs/91` 第五十六轮留下的矛盾，本轮定位并修掉。

### 症状（真机报告）

```
[启动恢复]  队列=100 首 · 第 30 首 · 来源=本地快照
[队列]      来源=设备上有 100 首正在播，但本机没存到这份曲目单（只显示顺序）
```

**同一份 100 首，一处说"有快照"，一处说"没存到"。**

### 根因在 `PlaybackViewModel.switchTo` 的第一行

```kotlin
// 旧
if (ip == deviceIp && (newLocation == null || newLocation == location)) return
```

启动时序：

1. VM 构造时 `selectedUuid` 还没就绪（启动瞬间是 null）→ `deviceIp = .164`, `deviceUuid = null`
2. `SessionRestore` 恢复完后 `LaunchedEffect` 调 `switchTo(相同 ip, null, 真实 uuid)`
3. **ip 相同、location 是 null → 直接 return，uuid 永远没写进来**

于是后面读快照时：

```kotlin
PlaybackKey.candidates(null, "192.168.99.164")  →  ["ip:192.168.99.164"]
```

而推送队列时存的是 **uuid** 键 → **读不到快照** → `QueueRestore.plan` 退化成
「设备上的第 N 首」占位。

**这就是「播放列表没跟进」的真身**：数据一直都在，只是读的键算错了。

### 修法

地址没变时**不重连，但补 uuid**：

```kotlin
if (ip == deviceIp && (newLocation == null || newLocation == location)) {
    if (uuid != null && uuid != deviceUuid) deviceUuid = uuid   // ← 补上
    return
}
```

为什么补 uuid 不用 bump generation / 重开轮询：**uuid 只影响"读哪份快照"，
不影响连接本身**。补完之后下一轮轮询（每秒一次）就会用正确的键读到快照，
列表自动从占位变回真实歌名。

### 这一轮的教训

「提前 return」是最容易吞掉附加逻辑的写法。原来那句注释写的是"什么都不做"，
但**实际上地址相同不代表什么都相同** —— 这次吞掉的是 uuid，
上次（`docs/91` 第四十轮）吞掉的是 5 个字段。

### 验证

```bash
./gradlew :app:testDebugUnitTest / :app:assembleDebug
# 1081 用例 0 失败
```

### 没验的

| 项 | 状态 |
|---|---|
| 真机：重开 App 后播放列表是否显示真实歌名而不是"设备上的第 N 首" | **未验** |
| 真机：`[队列]` 段的「来源」是否变成「设备列表 N 首 · 与本地快照一致」 | **未验** |

## 第五十八轮 · 设备页错误归属 + 「我」页改全局设置（用户四条中的 1、2）

用户：「以上问题解决，我们逐个 tab 解决问题」。

### ① 设备页那条红色报错，现在跟着设备走

**现象**（截图）：页顶挂着

```
ConnectException: Failed to connect to /192.168.99.175:80
```

而当前选中的是 **doss1**。那条错误是**切去 E900V22C 时**探活失败留下的，
一直挂到切回来都没清。

一条属于某台设备的错误挂在页面顶部 = 告诉用户"整个 App 坏了"。

修法：

| 改动 | 说明 |
|---|---|
| `ConsoleUiState` 加 `errorIp` | 记录这条错误是哪台设备产生的 |
| `run()` 捕获时写 `errorIp = selectedIp` | **记当时在操作哪台**，之后切设备也不会跟错行 |
| `DeviceRowApple` 加 `errorText` | 有错的那台，副标题变红显示原因 |
| 页顶横幅删掉 | 只保留**无归属**的错误（如"名字不能为空"）当一行小字 |

### ② 「我」页改成全局设置

用户原话：「这个 tab 应该改为全局通用设置，里面的项目也应该是全局通用设置才对」。

| 项 | 处置 |
|---|---|
| **音箱设置**（当前选中那台的网络 / 名字 / 固件） | **删除** —— 这是**每台设备**的，去设备页点那台进详情 |
| 诊断台 / 主题 / 音源脚本 / 配网 / 版本 | 保留（都是全局一次的东西） |

标题也从「我」改成「设置」，副标题写明 **「这里的每一项都对所有设备生效」**。

`docs/52` 铁律四 §4.4 本来就是这么规定的（配置分四类），
这条「音箱设置」是当初放错了位置，本轮按用户要求纠正。

### 验证

```bash
./gradlew :app:compileDebugKotlin   # 通过
```

### 还没做的（用户四条中的 3、4）

| # | 要求 | 状态 |
|---|---|---|
| 3 | 内容页搜索**直接出结果**，不要点进去再搜一遍 | **未做**，下一轮 |
| 4 | 内容页「投给」与播放页 cast **同一形态、直通**；内容页作为首页 | **未做**，下一轮 |

## 第五十九轮 · 默认进内容页 + 通用 UPnP 设备不再报错

用户：「通用 upnp 的 80 端口没开放，所以永远报错。默认进内容页。而且内容页里 b 站源我做好模块没接进来」。

### ① 默认进「内容」页

`var tab by remember { mutableStateOf(Tab.Content) }`

理由：打开 App 第一件事通常是"我想听点什么"，不是"我先看看设备"。
设备页是排查/管理用的低频页，内容页才是入口。

### ② 通用 UPnP 设备探活失败不再甩错误

用户已经诊断出原因：**那台设备的 80 端口没开**，所以每次探活必然 ConnectException。

对这种设备，**离线状态本身就是完整信息** —— 再甩一条
`ConnectException: Failed to connect to /192.168.99.175:80` 只是噪音，
而且会让人以为整个 App 坏了。

修法：错误归属判断里，`DeviceGroup.OtherPlayer`（通用渲染器）**不显示错误文本**，只显示「离线」。

> 顺带核实了一下 URL 拼接：`DeviceDescription.resolve()` 用的是
> `java.net.URI.resolve()`，会正确保留 base 的端口，**没有硬编码 80**。
> 所以那个 `:80` 是设备的 LOCATION 本身（或手动添加时没带端口）导致的，
> 不是我们拼错。真要修得去查 SSDP 通告里它报的 LOCATION 是什么。

### ③ B 站：仓库里只有文档，没有代码

查了全仓库：

| 位置 | 内容 |
|---|---|
| `docs/85-bilibili-source.md`（分支 `docs/bilibili-source`） | 319 行施工文档 |
| 代码模块 | **没有** —— `settings.gradle.kts` 里没有 `core-bili` 之类的模块 |
| 任何 `.kt` 里 | 只有 `DeviceCapabilities` 等词的误匹配 |

也就是说：**方案写了，模块没做**。

要继续的话有两个选择：
1. 按 `docs/85` 从零实现一个 `core-bili` 模块（工作量：单模块 + 取流 + 搜索 + 接线）
2. 先在内容页把 B 站那一行接通"能搜到、能播"，再补细节

### 验证

```bash
./gradlew :app:compileDebugKotlin / :app:testDebugUnitTest / :app:assembleDebug
# 1081 用例 0 失败
```

## 第六十轮 · 内容页搜索直接出结果、能直接点（用户四条中的 3）

用户：「内容 tab 搜索不合理，搜索的结果应直接出现全部结果。能直接点，而不是点了进去以后还要再重新搜索一遍」。

### 根因：搜索结果只留数量，条目被丢掉

```kotlin
// 旧
data class Found(val count: Int, val ms: Long)
```

`SearchHubViewModel.probe()` 明明拿到了条目（`value.tracks` / `list` / `matched`），
却**只留了 size**。所以搜索页只能显示「洛雪 5 条」，用户点进去还得再搜一遍。

### 改法：数据层带条目出来

```kotlin
data class SearchHit(
    val title: String,
    val subtitle: String?,
    val source: SourceId,
    val url: String? = null,        // 电台现成
    val lxTrack: LxTrack? = null,   // 洛雪要拿脚本现解析
    val localId: Long? = null,      // 本地要走本机 HTTP 服务
    val durationSec: Long = 0,
)

data class Found(val count: Int, val ms: Long, val items: List<SearchHit>)
```

三个源各取前 5 条填进去。

### 内容页：搜完就地出结果

```
内容
投给  [本机][doss1]…
[🔍 周杰伦          ]

洛雪音乐
  晴天    周杰伦        ← 点一下就播
  稻香    周杰伦
  …

网络电台
  …
手机本地
  …
```

- 有搜索词时，**「接着听 / 最近加入 / 全部来源」全部让位**（避免两套内容抢屏）
- 按源分组，不合并排序（NAS 的《晴天》和洛雪的《晴天》不是同一份东西）
- 没结果的源也给一行「没有结果 + 原因 + 去那边」，不是空白

### 三类的播放方式收敛在一个地方

| 源 | 怎么播 |
|---|---|
| 电台 | 地址现成，直接 `playOne(url)` |
| 本地 | `LocalMediaRegistry.baseUrl() + "/t/{id}"`（要走本机 HTTP 服务） |
| 洛雪 | 拿启用脚本 → `LxSession.userApi` → `musicUrl()` 现解析 → `playOne` |

界面只调 `onPlayHit(hit)`，**不判断源** —— 和"输出目标"一样，分派只在一个地方。

### 验证

```bash
./gradlew :app:compileDebugKotlin / :app:testDebugUnitTest / :app:assembleDebug
# 1081 用例 0 失败
```

### 没做的

| 项 | 状态 |
|---|---|
| B 站源 | **仓库里只有 `docs/85` 施工文档 + 真机验证结论，没有代码** |
| 「投给」统一成播放页那个 cast 形态（用户第 4 条） | 未做 |
| 真机：搜一首点一下是否真的出声 | **未验** |

## 第六十一轮 · B 站取流（第一块地基）

用户：「b 站源帮我做好并集成进去」。

按 `docs/85` 施工，本轮完成**取流**这一块。

### 新增 `core-protocol/.../bili/BiliClient.kt`

三件事：

| 方法 | 做什么 |
|---|---|
| `parseBvid(input)` | 从 BV 号 / 视频链接里认出 BV 号 |
| `fetchStream(bvid)` | `/x/web-interface/view` 取 cid + 标题 + 时长 |
| 同上 | `/x/player/playurl?fnval=16` 取 DASH 音轨，**选带宽最大那条** |

依赖是**一个 lambda**（`fetchJson`），所以整个类能跑纯 JVM 单测。

### 一个关键能力边界（`docs/85` §2.4 实测）

**B 站搜索接口有风控，不能搜关键词**：

```
GET /x/web-interface/search/type?keyword=test  → 返回 HTML 风控页
GET /x/web-interface/wbi/search/type?...       → {"code":0,"data":{"v_voucher":"…"}} 没有结果
```

所以这个源在界面上是「**粘 BV 号或链接**」，**不是搜索框**。
补 buvid + cookie + wbi 签名是另一套工作量，且 ToS 更敏感 —— 不做。

### 测试抓到的第一个坑

第一版把 `cid` / `bandwidth` / `id` 当**字符串**取（`string("cid")`），
测试立刻抛 `IllegalStateException` —— B 站这几处给的是**数字**。

修法：`JsonValue.Obj.long()` 辅助函数，数字和字符串两边都认
（B 站不同接口确实两种都出现过）。

### 四条例测试

```
parseBvid: BV 号 / 完整链接 / 带空格       全过
parseBvid: 空串 / 中文 / b23 短链          → null（不瞎猜）
音轨选择: 43k / 102k / 203k 三条            → 选中 203k（30280）
没有 dash                                → "没有 dash（可能该视频无音轨）"
```

第二条尤其重要：选错拿到 43kbps 那条，听感会像"网络差"，真机上极难归因。

```bash
:core-protocol:test       622 用例（+4）
:app:testDebugUnitTest    1081 用例
───────────────────────────────
                          全绿
```

### 下一步（remux + 接线）

`docs/85` §2.3 实测：B 站给的是 **fMP4 分片**，DOSS 一律 `stop`。所以还差：

| 项 | 内容 |
|---|---|
| `Fmp4Remuxer` | Media3 Transformer transmux（要加 `media3-transformer` 依赖，Apache-2.0） |
| 下载 + 本机 HTTP 提供 | 转完的文件走 `LocalMediaServer`，不直投外网直链 |
| 内容页接线 | B 站那一行从「未接入」变成「粘 BV 号」输入 |

### 与用户的确认项

用户在上一轮说「b 站一定有，前面有测试」——**测试是真的**（`docs/85` 记了真机验证：
`ffmpeg -c copy` 重封装后投 doss1，`totlen=212309`、`curpos` 稳定递增）。
但那验证是**手工用 ffmpeg 做的**，不是代码。本轮从零把取流写成代码。

## 第六十二轮 · B 站 fMP4 remux（第二块地基）

用户：「继续」（做 remux）。

### 加了依赖

`media3-transformer:1.5.1`（Apache-2.0，与现有 media3 同版本）。
**先验证能拉到再动代码** —— 用 `:app:dependencies` 确认 `1.5.1` 进图、无冲突，
因为 compose-bom 那次就是没先验证撞了墙。

### 新增 `app/.../bili/Fmp4Remuxer.kt`

| 步 | 做什么 | 为什么 |
|---|---|---|
| 1 | 下载直链到 `cacheDir/bili_remux/{key}.fmp4` | B 站直链有效期 120 分钟且是分片流，直接喂 Transformer 任何一次读超时都会让整个转换失败，且失败点难归因 |
| 2 | `Transformer` transmux → `.mp4` | 音轨格式不变时官方自动走 transmux（copy samples，不重编码） |
| 3 | 删掉中间 `.fmp4` | 每首 5MB 上下，留着只占空间 |

几个具体决定：

- **已转好的直接复用**：同一首反复点不重复下载；直链过期也不影响已转好的文件
- **`suspendCancellableCoroutine` 包回调 API**：调用方能顺序写「取流 → 下载 → 转换 → 投送」
- **`Transformer` 必须在主线程驱动**（内部要 Looper），所以 `withContext(Dispatchers.Main)`
- **取消时 `transformer.cancel()`**：用户切走不该留一个后台转换在跑
- **`clear()`** 给设置页留了「清空 B 站缓存」

### 编译验证

```bash
./gradlew :app:compileDebugKotlin   # 通过（2m35s，首次拉依赖）
./gradlew :app:testDebugUnitTest / :app:assembleDebug   # 1081 用例 0 失败
```

### 还差最后一环

| 项 | 内容 |
|---|---|
| **本地文件服务** | `LocalMediaServer.publishText` 只支持**内存文本**；5MB 音频要加 `publishFile`（流式读），不能整份塞内存 |
| **内容页接线** | B 站那行从「未接入」变成「粘 BV 号 → 取流 → remux → 投」 |

### 进度小结

```
BiliClient   取流（认 BV 号 / 取 cid / 选最大带宽音轨）   ✅ 622 用例
Fmp4Remuxer  下载 + transmux + 复用                    ✅ 编译通过
文件服务     把 .mp4 提供给音箱                          ⬜
界面接线     粘 BV 号就能播                              ⬜
```

前两块（最容易写错、最需要测试的部分）已经落地；剩下的两块是接线，风险低但必须一次打通才谈得上"能用"。

## 第六十三轮 · B 站链路打通（第三块：文件服务 + 界面接线）

用户：「继续」。这是 `docs/85` 的最后两步。

### ① `LocalMediaServer.publishFile`

原来的 `publishText` 把内容**整个放内存**（适合几百字节的 m3u）。
B 站转出来的音频有 5MB 上下，整份塞内存既浪费也可能 OOM。

新增：

```kotlin
fun publishFile(path: String, file: File): String
```

只记路径，响应时**复用下面那条带 Range 的文件流式分支** —— 顺带白拿了断点续传
（音箱请求时会带 `Range`，这条分支本来就支持）。

一行改动把它接进去：

```kotlin
val file = extraFiles[path] ?: resolver.resolve(path)
```

⚠️ `path` 的后缀决定 `Content-Type`（`.mp4` → `audio/mp4`），所以对外路径要带正确扩展名。

### ② 内容页 B 站：从「未接入」变成「粘 BV 号」

```
全部来源
  ...
  哔哩哔哩    粘 BV 号播放   ›
```

点开是输入框，说明里直接写明限制：

> B 站的搜索接口有风控，搜不了关键词 —— 只能按 BV 号取。
> 例如 BV1GJ411x7h7，或视频页链接。
> 音频是 fMP4 分片，音箱播不了，会先在手机上转一遍（几秒）。

### ③ 完整链路（`ConsoleApp` 里 30 行）

```
粘 BV 号
  → BiliClient.parseBvid        认出 BV
  → BiliClient.fetchStream      取 cid + 选最大带宽音轨
  → Fmp4Remuxer.fetchAndRemux   下载 → transmux → .mp4（已转好的直接复用）
  → LocalMediaRegistry.ensureServer + publishFile
  
  → http://<手机IP>:<port>/bili/<bvid>.mp4
  → PlaybackRouter.playOne      投给当前目标
```

失败一律 `Toast` 说原因（不静默）。

### 一个刻意的设计：内容页不碰网络

`ContentScreen` 只负责"收集用户输入 + 调 `onPlayBili(input)`"。
取流、下载、转换、发布全在 `ConsoleApp` 那一层 —— 和"输出目标分派"同一个原则：
**界面不判断流程，只表达意图。**

### 验证

```bash
./gradlew :app:compileDebugKotlin / :app:testDebugUnitTest / :app:assembleDebug
# 1081 用例 0 失败，文档引用 98 篇通过
```

### 没验的（诚实说）

| 项 | 状态 |
|---|---|
| **真机：粘一个真 BV 号，能不能真的出声** | **未验** |
| Transformer transmux 在真机上的耗时 | **未验**（文档说 0.5 秒级，那是 ffmpeg；Media3 可能不同） |
| 音箱能不能播转出来的 mp4 | **未验**（`docs/85` 验的是 ffmpeg 产物，不是 Media3 产物） |
| b23 短链 | **不支持**（`parseBvid` 只认 BV 号，短链要展开，需要额外一次 HTTP） |

第 3 条是真实风险：ffmpeg 和 Media3 都是 transmux，但输出封装细节可能有差异。
如果真机播不了，第一件事是把转出来的文件拉下来 `ffprobe` 看。

## 第六十四轮 · B 站改走 durl 直投（照官方投屏做法）

用户：「b 站官方本身就可以推直链给任何 upnp 设备，模仿官方推直链即可」。

### 这条纠正了 `docs/85` 的一个默认假设

原文只测了 `fnval=16`（DASH），结论是「fMP4 必须 remux」。
但官方投屏走的是**另一条**：

| 参数 | 返回 | 能不能直投 |
|---|---|---|
| `fnval=16`（DASH） | 音视频分离的 **fMP4 分片** | ❌ DOSS 一律 stop |
| **`fnval=0`（durl）** | **完整 MP4**（已封装） | ✅ 和官方投屏一致 |

`docs/85` §2.5 其实记过 durl 的存在，但当时判断「带 Referer 也 403」就放下了。
用户的实际经验是**官方能推** —— 所以优先试 durl 才是对的顺序。

### 改法：先要 durl，拿不到才退 DASH + remux

`BiliClient.fetchStream` 现在：

```
① fnval=0 → durl[0].url 有值？
     是 → needsRemux = false，**直接投**（一条命令，不下载不转码）
     否 → ②
② fnval=16 → DASH 选最大带宽音轨 → needsRemux = true → Fmp4Remuxer
```

`Stream` 新增 `needsRemux`，`ConsoleApp` 按它分派：

| needsRemux | 做什么 |
|---|---|
| `false` | 直接把地址交给 `playOne` —— 和官方投屏同一条路 |
| `true` | 下载 → transmux → 本机 HTTP → 投（兜底路径，保留） |

### 为什么保留 remux 那条

有些稿件（尤其音频分离的）只给 DASH。那条路留着，用户点同一个 BV 号不会因为
"这个稿子没有 durl" 就没法听。**代价是多 5MB 流量和几秒转换，但保住了可用性。**

### 验证

```bash
:core-protocol:test / :app:compileDebugKotlin / :app:testDebugUnitTest / :app:assembleDebug
# 全绿
```

### 没验的

| 项 | 状态 |
|---|---|
| **真机：durl 直链投 DOSS 能不能播** | **未验** |
| durl 需不需要 Referer（`docs/85` 记过 403 的情况） | **未验**；本实现不带任何 Header |

第二条是关键风险：如果 B 站对 durl 要求 Referer，而我们不带，设备那边会 403。
真出这个现象的话，下一步是「先在本机用带 Referer 的请求把文件拉下来，
再经本机 HTTP 提供」（比 remux 便宜得多，只是多一次下载）。

## 第六十五轮 · 「投给」统一成一个组件（用户指出两页样式不一致）

用户截图：「这个页面投给我说过与播放页面的投屏保持样式和功能一致，你没做到。页面杂乱」。

### 问题

| 页面 | 「投给 / 从哪出声」长什么样 |
|---|---|
| 播放页 | 左下角 cast 图标 + 点开弹层 |
| **内容页** | **一排横向 chips**（本机 / doss1 / DOSS_CloudBook_56C…） |

同一个概念两种形态。而且内容页那排：

- 设备名一长（`DOSS_CloudBook_56C4`）就撑出屏幕
- 和下面的**筛选 chips** 挤成两排横向滚动条，视觉上重复又杂乱

### 改法：抽成一个公共组件

新建 `ui/components/TargetPicker.kt`：

| 组件 | 用途 |
|---|---|
| `CastButton(label, isLocal, onClick)` | cast 形状图标 + 目标名，贴在界面上 |
| `TargetSheet(devices, onDismiss, onPick)` | 点开后从底部弹出的选择层 |
| `TargetSheetRow` | 弹层里的一行（当前项打勾） |

**播放页和内容页都用这一套**，`grep CastButton` 现在两处都是它。

内容页那排 chips 和它背后的 `TargetRow` 组件**整段删除**。

### 一个编译错误值得记

`CastButton` 里画图标的 `Canvas`：

```kotlin
Canvas(...) {
    val c = MaterialTheme.colorScheme.onSurface   // ❌ DrawScope 不是 @Composable
}
```

`DrawScope` 不在 Compose 组合上下文里，取主题色必须**提到 Canvas 外面**。
这是从播放页搬代码时暴露的 —— 播放页那版用的是常量 `OnDarkPalette.text`（硬编码深色），
搬成主题色才触发这个错。

顺带说明为什么共用组件能抓到这类问题：**组件一旦要同时服务深色页和浅色页，
就把"硬编码颜色"这个隐患逼出来了。**

### 验证

```bash
./gradlew :app:compileDebugKotlin / :app:testDebugUnitTest / :app:assembleDebug
# 1081 用例 0 失败
```

### 还没整理的

| 项 | 说明 |
|---|---|
| 搜索框样式 | 现在是带边框 + 右侧「搜索」字样，和苹果风的"无边框灰底"不一致 |
| 大标题下的空白 | 搜索态时「接着听/最近加入/全部来源」都隐藏，中间会空一段 |
| 真机 | **未验** |

## 第六十六轮 · 修播放页 cast 图标消失（浅色主题下黑图标落在深色背景上）

用户截图：播放页左下角**只剩一行几乎看不见的 "doss1"，图标没了**。

### 根因：抽公共组件时把"页面背景"这个前提丢了

上一轮把 `CastButton` 从 `PlayerScreen` 抽到 `ui/components/TargetPicker.kt`，
顺手把颜色从硬编码的 `OnDarkPalette.text` 改成了 `MaterialTheme.colorScheme.onSurface`。

问题是：

| 页面 | 背景 | `onSurface` 在浅色主题下 | 结果 |
|---|---|---|---|
| 内容页 | 跟随主题（浅） | 黑 | 正常 |
| **播放页** | **固定深色**（那页不看主题） | **黑** | **黑图标落在深色底上 = 看不见** |

不是图标没画，是画了看不见。

### 修法：颜色变成必填参数

```kotlin
fun CastButton(
    label: String,
    isLocal: Boolean,
    onClick: () -> Unit,
    contentColor: Color,   // ← 必填，没有默认值
)
```

- 播放页传 `OnDarkPalette.text`（深色页专用前景色）
- 内容页传 `MaterialTheme.colorScheme.onSurface`

**故意不给默认值**：默认值会让下一个调用方又踩同一个坑。
显式要求调用方回答"你的背景是什么色"。

### 顺带修掉同屏另一处

曲名下面原来显示 `192.168.99.164` —— 那是 `artist` 为空时的兜底值。

它既不是作品信息、也不是用户关心的东西，却占着标题正下方最值钱的一行。
设备是谁，顶栏已经写了。改成空串（不占高度）。

### 验证

```bash
./gradlew :app:compileDebugKotlin / :app:testDebugUnitTest / :app:assembleDebug
# 1081 用例 0 失败
```

### 没验的

| 项 | 状态 |
|---|---|
| 真机：cast 图标在深色页上是否清晰 | **未验** |
| 真机：内容页 cast 在浅色/深色主题下是否都对 | **未验** |

## 第六十七轮 · 切到「本机」时整页数据跟着换（真机 bug）

用户：「播放器页面，切换本机时，没有根据本机的情况切换页面信息，播放列表也没换，
其他音箱设备之间反而切换正常」。

### 为什么只有"本机"这一路是坏的

S2.1 那一轮只给**进度和音量**做了"按输出选源"：

```kotlin
val duration = if (isLocalOutput) localUi... else state...
val volumeNow = if (isLocalOutput) localUi.volume else state.volume
```

但**曲名 / 歌手 / 播放状态 / 投送文案 / 播放列表**仍然直接读 `state`（那是**设备**的状态）。

于是切到本机后：

| 元素 | 显示的是 |
|---|---|
| 顶栏 | 本机（对） |
| 曲名 | 音箱那首歌（错） |
| 歌手 | 音箱回报的 IP（错） |
| 播放列表 | 音箱的 122 首（错） |
| 进度 / 音量 | 本机的（对） |

一半一半，所以看着像"没切"。

**"其他音箱之间切换正常"也解释了原因**：那几台都走 `state`，切过去自然全对。
只有"本机"是另一条数据源（ExoPlayer），没接上。

### 改法

| 元素 | 本机 | 设备 |
|---|---|---|
| `playing` | `localUi.playing` | `state.state == PLAYING` |
| `title` | `localUi.title` | `state.title ?: queue.current?.title` |
| `artist` | **不显示**（那是另一路的内容） | 同上 |
| 投送文案 | 「本机播放 / 已暂停」 | 「投送中 / 已暂停 / 待播放」 |
| 播放列表入口 | 「**本机播放 · 单曲**」且**不可点** | 「122 首 · 第 85 首」可点 |

最后一条是关键：本机播放器只播单曲，**假装有队列比不显示更糟**；
点进去也只会看到音箱的列表，所以干脆不给点。

### 顺带：过滤"IP 型歌手"

真机截图里曲名下面是 `192.168.99.164` —— 有些设备把 IP 塞进 `artist` 字段回报。
加了一个 `IP_LIKE` 正则，长得像 IPv4 的一律当"没有歌手"。

### 一个 Kotlin 顺序坑

第一版把 `playing` 的 `if (isLocalOutput)` 写在了 `isLocalOutput` 声明**之前**，
编译报 `Unresolved reference`。Compose 里变量顺序必须严格自顶向下 ——
把那一整块移到 `isLocalOutput` 之后就好。

### 验证

```bash
./gradlew :app:compileDebugKotlin / :app:testDebugUnitTest / :app:assembleDebug
# 1081 用例 0 失败
```

### 没验的

| 项 | 状态 |
|---|---|
| 真机：切本机后曲名/列表是否真空 | **未验** |
| 真机：本机播一首，进度和音量是否实时 | **未验** |
| 真机：从本机切回音箱，是否恢复音箱的数据 | **未验** |

## 第六十八轮 · 音量控制跟输出走（本机调手机、设备调设备）

用户：「音量控制应该也与对应设备直接相关」。

### 问题：读的时候分岔了，写的时候没分

上一轮只让**显示值**按输出切换：

```kotlin
val volumeNow = if (isLocalOutput) localUi.volume else state.volume   // 读 ✓
```

但 `onValueChange` 里还是：

```kotlin
vm.setVolumeLive(...)   // ← 永远调设备
```

结果：切到本机拖音量条，**改的是音箱的音量**，手机这边一点没变；
音量数字还显示 0（因为 `localUi.volume` 的初值是 0，而 ExoPlayer 实际是 1.0 = 100）。

### 改法三处

| 处 | 本机 | 设备 |
|---|---|---|
| 拖动音量 | `LocalPlayer.setVolume()`（ExoPlayer 0f..1f） | `vm.setVolumeLive()` |
| 静音按钮 | 音量归零 / 恢复 60 | `PlayIntent.Mute` |
| 初始值 | `volume = (player.volume * 100f)` | —— |

**换算只放在 `LocalPlayer` 这一层**（0..100 ↔ 0f..1f），和 `PlayerUiState.volume`
的读出方向对称 —— 和 `docs/91` 第 365 条记的那个坑（ExoPlayer 是 0f..1f、
设备是 0..100）同一个处理原则。

### 静音为什么不发命令

本机没有"静音"这个设备侧概念，只有音量。所以把 ExoPlayer 音量归零，
再点一次恢复成 60 —— 不恢复成"上一次的值"，因为那需要额外记状态，
而 60 是一个用户不会觉得"怎么突然这么大"的中间值。

### 验证

```bash
:app:compileDebugKotlin / :app:testDebugUnitTest / :app:assembleDebug
# 1081 用例 0 失败
```

### 没验的

| 项 | 状态 |
|---|---|
| 真机：本机拖音量条手机声音是否真的变 | **未验** |
| 真机：切回音箱后音量条是否显示那台的音量 | **未验** |

## 第六十九轮 · 音量「一一对应」的三层问题（用户要求研究深一点）

用户：「关于音量你需要研究深一点，还没实现设备音量对应的完全一一对应」。

深挖之后发现，音量这条链有**三层**，前两轮只修好了前两层。

### 第 1 层 · 读：按输出选源（第六十七轮已修）

```kotlin
val volumeNow = if (isLocalOutput) localUi.volume else state.volume
```

### 第 2 层 · 写：按输出分派（第六十八轮已修）

```kotlin
if (isLocalOutput) LocalPlayer.setVolume(...) else vm.setVolumeLive(...)
```

### 第 3 层 · 对应关系（本轮，两个真 bug）

#### bug A · 「拖动后自己弹回去」

实际时序：

```
t=0    用户拖到 50 → 界面显示 50（乐观值），命令进 60ms 合并通道
t=80   setPlayerCmd:vol:50 发出
t=120  轮询恰好命中，设备回的却是**命令之前**的 20（命令还在设备队列里）
t=120  界面从 50 跳回 20        ← 用户看到"拖了没用"
t=400  下一次轮询回 50          ← 又弹回来
```

用户说的"没有一一对应"就是这个**来回弹**。

修法：命令发出后 `VOLUME_SETTLE_MS = 900ms` 内**不采信**设备回报
（那段时间的回读必然是旧值）；但**如果设备已经等于目标值就采信**（说明它跟上来了）。
超过 900ms 还没跟上就采信 —— 宁可显示真值，也不假装成功。

#### bug B · 「用原厂 App 改音量，这边不变」

轮询回报只写了 `VolumeBus`，**没写回 `_state.volume`**。
而播放页读的是 `state.volume` → 设备侧改了音量，界面纹丝不动。

修法两处：

1. 回报时**同时写** `VolumeBus` 和 `_state.volume`（保证"界面显示 = 设备实际"这条不变量）
2. 播放页改成**优先读 `VolumeBus`**：

```kotlin
val busVolume = VolumeBus.volumeOf(deviceIp)?.volume
val volumeNow = if (isLocalOutput) localUi.volume else (busVolume ?: state.volume)
```

理由：全站有四个地方能改音量（播放页拖 / 首页行内拖 / 音量键 / 原厂 App），
它们都汇到 `VolumeBus`。读 `state.volume` 等于读它的一份副本，
要求两条更新路径**永远同步** —— 而历史上它们正是先后漏掉过。
`VolumeBus` 是唯一真相，`state.volume` 只做兜底。

### 现在的对应关系表

| 场景 | 谁改 | 界面怎么知道 |
|---|---|---|
| 播放页拖 | `setVolumeLive` → 立刻写两处 | 同一帧 |
| 首页行内拖 | `SetVolumeUseCase` → VolumeBus | 读 VolumeBus，立即 |
| 手机音量键 | `adjustVolumeByKey` → VolumeBus + 直接发 | 读 VolumeBus，立即 |
| **原厂 App / 遥控器改** | 设备自己变 → 轮询回报 | 读 VolumeBus，≤1s（受在途保护约束） |
| 切设备 | 各自的 VolumeBus 条目 | 立即 |
| 本机 | ExoPlayer 自己的音量 | `localUi`（500ms ticker） |

### 验证

```bash
:app:compileDebugKotlin / :app:testDebugUnitTest / :app:assembleDebug
# 1081 用例 0 失败
```

### 仍不等价的地方（如实记）

| 项 | 说明 |
|---|---|
| 本机音量 ≠ 手机音量键 | 现在改的是 **App 内播放器音量**（0-100），不是系统媒体音量。要改成后者是另一种实现（`AudioManager`），大厂两种都有人做 |
| 设备不支持音量 | 通用渲染器（电视盒子）能 SetVolume，但有些设备失败静默 |
| 900ms 这个数 | 按 `docs/01` 实测的指令往返 40~80ms + 60ms 合并窗口估的，**真机可能需要调** |

## 第七十轮 · 音量键按「当前播放目标」决定调谁

用户明确规则：

> 「当前设备是本机按键调的音量是本机，设备为 doss1 按键调的音量为 doss1，
>  如果是通用 upnp 设备音量键调这个设备的音量」

### 原来错在哪

`canHandleVolumeKey()` 只看**「选中的设备」在不在线**，完全不看播放目标。

于是目标切到本机之后，按音量键还在调音箱：手机上放歌，按音量键一点反应都没有
（因为命令发给了客厅那台）。

### 改法：三条规则写进代码

| 播放目标 | 音量键 |
|---|---|
| **本机** | **不拦** → 交给系统，调手机媒体音量 |
| doss1 / doss2 / doss3 | 拦 → 调那台音箱 |
| 通用 UPnP（电视盒子） | 拦 → 调那台设备 |

`canHandleVolumeKey()` 和 `adjustVolumeByKey()` 用同一条判断
（新增 `targetDeviceIp()`：目标指向的设备 IP，目标不是设备时返回 null）。

音量浮层上写的设备名也改成**目标那台** —— 切了目标还写"选中的设备"比不写更糟。

### 顺带：本机音量改成真正的系统媒体音量

用户要"一一对应"。如果本机音量改的是 ExoPlayer 的 `player.volume`（App 内缩放），
那就是**两个旋钮**：

| 操作 | 改的是 |
|---|---|
| 按音量键 | 系统媒体音量 |
| 拖播放页音量条 | App 内音量 |

数字永远对不上。

改成 `AudioManager.setStreamVolume(STREAM_MUSIC, ...)`，读也是读系统的：

- 按音量键 → 系统音量变 → 500ms ticker 读到 → 音量条跟着变
- 拖音量条 → 写系统音量 → 和按键完全等价

需要 `MODIFY_AUDIO_SETTINGS`（普通权限，不用弹窗）。已加进 manifest。

后台播放时 App 的音量滑块会和系统音量一致，这是用户直觉里的"音量"。

### 验证

```bash
:app:compileDebugKotlin / :app:testDebugUnitTest / :app:assembleDebug
# 1081 用例 0 失败
```

### 没验的

| 项 | 状态 |
|---|---|
| 真机：目标是本机时按音量键，系统音量条是否出来、声音是否变 | **未验** |
| 真机：目标是 doss1 时按音量键，是否只调 doss1 | **未验** |
| 真机：目标是电视盒子时按音量键 | **未验**（有些通用渲染器 SetVolume 失败会静默）|
| 系统音量步数换算 | 用 `max * percent / 100` 四舍五入，各机型 max 不同（通常 15~25 档），**未验** |

## 第七十一轮 · 本机音量同步 + 通用 UPnP 音量（两个真 bug）

用户：「本机界面音量条与按键音量条不同步，通用 upnp 设备无法控制音量」。

### ① 通用 UPnP 音量：**通道从来没接上**

`DeviceControl` 里写死：

```kotlin
private val transport: RemoteTransport = LinkPlayTransport(host.http())
```

**不管设备是什么类型，都发 LinkPlay 私有 httpapi。** 给电视盒子发这些命令，
设备不认 → 静默失败（连接是通的，只是它不认这个命令），用户按音量键**没有任何反应也没有提示**。

而 `GenericRenderer` 那边**早就实现了** `setVolume` / `setMute`（走 SOAP `RenderingControl`），
只是从来没人从这条路上调它。

修法：

| 设备类型 | 通道 |
|---|---|
| LINKPLAY（DOSS） | `LinkPlayTransport` |
| **其它（通用渲染器）** | **新增 `UpnpRemoteTransport`** → SOAP RenderingControl |

新增 `app/.../device/UpnpRemoteTransport.kt`（约 80 行）：

- `supports()` 只报通用渲染器真有的能力（音量/静音/播放/暂停/停止/进度），
  上一首下一首、预设键、组网一律 false —— 界面据此不画按不动的按钮
- 失败按 `DispatchFailure` 分三类：连不上 / 不支持 / 设备拒绝

分派依据是**探测出来的 `DeviceCapabilities.group`**，不是品牌名
（型号标签会随固件变）。

### ② 本机音量不同步：只有 ticker 在刷，而它只在播放时跑

改成读写系统音量之后，读值只在 500ms ticker 里刷新，而那个 ticker
**只在播放时跑**（暂停不空转，省电）。于是：

```
没在播放 → 按音量键 → 系统音量 30 → 60
ticker 没跑 → localUi.volume 还是 30 → 音量条不动
```

修法：用 `ContentObserver` 监听 `Settings.System` 里的 `volume_music`。
系统音量一变立刻 `publish()`，比轮询实时得多也不费电。

**必须在 `release()` 里注销** —— 不注销会在界面走掉后继续持有这个对象。

### 验证

```bash
./gradlew :app:compileDebugKotlin / :app:testDebugUnitTest / :app:assembleDebug
# 1081 用例 0 失败
python3 scripts/check-*.py   # 8 个全 PASS
```

### 没验的

| 项 | 状态 |
|---|---|
| 真机：电视盒子音量键能不能调它 | **未验**（`GenericRenderer.setVolume` 之前只测过 GetVolume 11ms 通，**Set 类从来没测过**）|
| 真机：本机按键后音量条是否立刻跟 | **未验** |
| 电视盒子若不响应 SetVolume | 会显示"设备拒绝了该操作"（不再是静默）|

