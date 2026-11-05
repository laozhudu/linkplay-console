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

---

## 下一道

S2.2 通知栏接音箱（`UpnpPlayer` + MediaSession）→ **U1/U2/U3 见 `docs/92`**
