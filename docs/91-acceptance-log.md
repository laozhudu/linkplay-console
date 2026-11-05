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

---

## 下一道

S+1：设备页按「呈现使用」重做（分组列表 + 行内调音量 + 只留三条状态）
