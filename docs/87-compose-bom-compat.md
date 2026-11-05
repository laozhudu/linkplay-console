# 施工单 · 第二批续：compose-bom 兼容性实测 + 第一批验收补漏

> **依据的代码状态：提交 `7ab3d2d`**（#130～#133 已合入）
> 承接 `docs/86-unified-shortest-path.md`
> 性质：**接单开工文档**。每条写了「做什么 / 做到什么程度 / 收益 / 回退路径」。
> 本文所有版本号结论都是**下载 AAR 读元数据**得出的，不是查文档推断的；复核方法见第四节。

---

## 零、先说结论（三条，按优先级）

| # | 结论 | 依据 |
|---|---|---|
| **1** | **不用升 AGP。** 升到 `compose-bom = "2026.03.01"` 就能拿到 `MotionScheme`，AGP 8.7.3 / Gradle 8.11.1 / compileSdk 35 全部不动 | AAR 元数据实测：foundation 1.10.6 要求 `minAGP 8.6.0` + `minCompileSdk 35` |
| **2** | 之前撞墙是因为**跳到了最头上那一档**（2026.09.00）。分水岭是 compose **1.12.0**，不是 material3 | 1.12.0 / 1.12.1 要求 `minAGP 9.1.0` + `minCompileSdk 37`；1.11.4 及以下只要 8.6.0 / 35 |
| **3** | 触感做对了（放唯一入口），但**按容器分档不按语义分档**，需要补两档 | 见第六节 |

**如果 2026.03.01 编译不过**（有可能，我没编译过），退回「手写动效 + 先做第三批」，**不要升 AGP 9**。

---

## 一、BOM 兼容性实测表（可直接复核）

### 1.1 各档 BOM 里到底是什么版本

`compose-bom-<版本>.pom` 里就是一张版本清单：

| BOM | compose / foundation / ui | material3 | 有 MotionScheme |
|---|---|---|---|
| **2024.12.01**（现状） | 1.7.6 | 1.3.1 | ❌ |
| 2025.12.01 | 1.10.0 | **1.4.0** | ✅ |
| **2026.03.01** | **1.10.6** | **1.4.0** | ✅ |
| 2026.06.01 | 1.11.4 | 1.4.0 | ✅ |
| 2026.08.00 | **1.12.0** | 1.4.0 | ✅ |
| 2026.09.00（之前试的） | **1.12.1** | 1.4.0 | ✅ |

**关键：material3 1.4.0 从 2025.12.01 就进了 BOM。** 不需要追最新 BOM。

### 1.2 各构件的真实门槛（AAR 内 `aar-metadata.properties`）

| 构件 | `minAndroidGradlePluginVersion` | `minCompileSdk` | 我们能过吗（AGP 8.7.3 / compileSdk 35） |
|---|---|---|---|
| foundation 1.10.0 | **8.6.0** | 35 | ✅ |
| foundation 1.10.6 | **8.6.0** | 35 | ✅ |
| foundation 1.11.4 | **8.6.0** | 35 | ✅ |
| foundation **1.12.0** | **9.1.0** | **37** | ❌ |
| foundation **1.12.1** | **9.1.0** | **37** | ❌ |
| ui 1.11.4 | 8.6.0 | 35 | ✅ |
| material3 **1.4.0** | **8.6.0** | 35 | ✅ |
| activity-compose 1.9.3 | — | 34 | ✅ |

### 1.3 kotlin-stdlib 要求（这条决定「能不能不动 Kotlin」）

| compose runtime | 要求 kotlin-stdlib | 我们是 Kotlin 2.1.0 |
|---|---|---|
| 1.9.0 | 2.0.21 | ✅ |
| **1.10.0 / 1.10.6** | **2.0.21** | ✅ |
| 1.11.4 | **2.1.20** | ⚠️ 需要升 Kotlin |
| 1.12.x | —（已被 AGP 门槛挡掉） | — |

**所以 2026.03.01 是唯一一档「AGP 不动 + Kotlin 不动 + compileSdk 不动」的选择。**

### 1.4 MotionScheme 确实在 material3 1.4.0 里

我把 `material3-android-1.4.0.aar` 的 `classes.jar` 打开看了：

```
androidx/compose/material3/MotionScheme.class
androidx/compose/material3/MotionScheme$Companion.class
androidx/compose/material3/MotionScheme$ExpressiveMotionSchemeImpl.class
androidx/compose/material3/MotionScheme$StandardMotionSchemeImpl.class
androidx/compose/material3/MotionSchemeKt.class
androidx/compose/material3/tokens/ExpressiveMotionTokens.class
androidx/compose/material3/tokens/MotionSchemeKeyTokens.class
androidx/compose/material3/tokens/MotionTokens.class
androidx/compose/material3/tokens/StandardMotionTokens.class
```

`MaterialTheme.motionScheme` + 六个 spec（`fastSpatialSpec` / `defaultSpatialSpec` / `slowSpatialSpec` / `fastEffectsSpec` / `defaultEffectsSpec` / `slowEffectsSpec`）都在。

---

## 二、做什么：BOM 2026.03.01（方案 D）

### 2.1 改动

```toml
# gradle/libs.versions.toml
composeBom = "2024.12.01"   →   "2026.03.01"
```

**只改这一行。** AGP / Gradle / Kotlin / compileSdk / media3 / coil 全部不动。

### 2.2 分三步走，每步都要能回退

**第 1 步 · 只改版本号，跑编译**

```bash
export ANDROID_HOME=/opt/android-sdk
./gradlew :app:assembleDebug --no-daemon 2>&1 | tail -40
```

三种结果，对应三种走法：

| 结果 | 走法 |
|---|---|
| **BUILD SUCCESSFUL** | 直接进第 2 步 |
| 报**个别 API 不兼容**（例如 `ExperimentalMaterial3Api` 有签名变更） | 就地改那几处，属于正常升级成本，继续 |
| 报 **compose compiler / Kotlin 版本不匹配** | 升 Kotlin `2.1.0 → 2.1.20`（同 minor，只影响 compose compiler 插件）。仍不行就**停，转方案 C** |
| 报 **AGP 版本不够** | 说明我读错了元数据 —— 立刻停，把完整报错贴出来，我重新核 |

**第 2 步 · 跑全量单测 + 对比度测试**

```bash
./gradlew test :core-protocol:test :core-lx:test :core-radio:test \
  :core-diagnostics:test :core-upnp:test :core-script:test :core-discovery:test
```

⚠️ **`ThemeContrastTest` 必须绿。** 换 BOM 最容易悄悄破坏对比度（`docs/50` 的 WCAG 4.5:1 是唯一防线）。

**第 3 步 · 肉眼过一遍界面**

字号、间距、圆角、颜色有没有意外变化。重点看：
- 底部 Tab 的四个 glyph 有没有对齐变化
- `AppCard` 的圆角（`AppShapesExpressive.squircle`）
- 播放页封面与文字层级

### 2.3 验收标准

- [ ] `:app:assembleDebug` BUILD SUCCESSFUL
- [ ] 全量单测 0 失败（现基线 1,036）
- [ ] `ThemeContrastTest` 绿
- [ ] 界面无肉眼可见的意外变化
- [ ] **新代码里能用 `MaterialTheme.motionScheme` 编译通过**（写一行验证，例如给某个 `animateFloatAsState` 传 `MaterialTheme.motionScheme.defaultSpatialSpec()`）

### 2.4 收益

| 项 | 前 | 后 |
|---|---|---|
| `MotionScheme` | 用不上 | **可用**，动效手感统一有依据 |
| M3 组件 | 1.3.1 | **1.4.0**（`NavigationBar` 选中胶囊、`PullToRefreshBox` 等新组件可用） |
| 构建工具链 | — | **零改动**（AGP / Gradle / Kotlin / compileSdk 全不动） |
| 回退成本 | — | **一行版本号改回去** |

### 2.5 回退路径（写清楚，别到出事才想）

```bash
git checkout gradle/libs.versions.toml
./gradlew :app:assembleDebug    # 确认回到 BUILD SUCCESSFUL
```

**不要**在同一个 PR 里既升 BOM 又改界面。升不上去就关掉 PR，main 一点不受影响。

---

## 三、D 的真实风险（我没编译过，必须说清）

我只验证了**声明门槛**（AAR 元数据里的 `minAGP` / `minCompileSdk`）和**类是否存在**，
**没跑过一次编译**。真实风险在：

| # | 风险 | 概率 | 应对 |
|---|---|:-:|---|
| 1 | **Compose Compiler 版本不匹配**：Kotlin 2.1.0 的 compose 插件对 runtime 1.10.6 可能报「compiler 太旧」 | 中 | 升 Kotlin 到 2.1.20（同 minor） |
| 2 | **跨 3 个 minor 的 API 变更**：foundation 1.7.6 → 1.10.6 中间有弃用/签名变更 | 中 | 就地改，属正常升级成本 |
| 3 | **非 BOM 管理的依赖不匹配**：`activity-compose 1.9.3`（已验 minCompileSdk 34 ✅）、`lifecycle 2.8.7`、`coil 2.7.0` | 低 | 报错就补那一两个号 |
| 4 | **Material3 1.4.0 的组件默认样式变了**（例如 `Card` 的 elevation、`TopAppBar` 的高度） | 中 | 第 3 步肉眼过 |

**结论：D 是「一个版本号 + 可能的两个补丁版本」，B 是「三个大件 + 全部页面肉眼验」。风险量级差一个数量级。**

---

## 四、复核方法（谁都能重跑一遍）

```bash
# 1) 某档 BOM 里各构件是什么版本
curl -s https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/2026.03.01/compose-bom-2026.03.01.pom \
  | grep -A1 -E '<artifactId>(foundation|ui|material3)</artifactId>'

# 2) 某构件的真实门槛（这才是 AGP 报错的来源）
curl -sL -o f.aar https://dl.google.com/dl/android/maven2/androidx/compose/foundation/foundation-android/1.10.6/foundation-android-1.10.6.aar
unzip -p f.aar META-INF/com/android/build/gradle/aar-metadata.properties

# 3) 某个类在不在
unzip -p m3.aar classes.jar > c.jar && unzip -l c.jar | grep MotionScheme
```

---

## 五、第一批验收补漏（#130～#133 已合入，但有四处没到位）

### 5.1 ✅ 已达标

| 项 | 验收 |
|---|---|
| 1.1 删 NAS 三段降级 | `NasViewModel` 不再直接调 `DevicePlaylistGateway.push` / `DeviceQueuePush.push` / `QueuePlaybackService.start` |
| 1.2 本地队列走唯一入口 | `QueueViewModel` 不再直接调 `QueuePlaybackService.start` |
| 1.3 `playQueue` 存在 | `PlaybackRouter.playQueue()` 已实现，NAS / 本地 / 在线 / 歌单 / 洛雪 5 个源都调它 |

### 5.2 🔴 补漏 1：还有第 4 条下发路径

`PlaybackViewModel.play()` 里仍在直接调原语：

```kotlin
// PlaybackViewModel.kt 约 503 行
if (!hasDeviceQueue && localItems.isNotEmpty()) {
    val ok = withContext(Dispatchers.IO) {
        runCatching { DevicePlaylistGateway.push(deviceIp, location, localItems, 0) }.getOrNull()
    }
```

**三个问题**：没有能力位判断（投通用渲染器白撞 401）、没有二段三段降级、和唯一入口不同步。

**做什么**：改成调 `startQueueOnDevice`（或 `PlaybackRouter.playQueue`）。

**验收**

```bash
grep -rn 'DevicePlaylistGateway.push\|DeviceQueuePush.push' app/src/main
# 期望：只在 QueueStarter.kt 里出现
```

- [ ] `PlaybackViewModel` 里零命中
- [ ] 首页/播放页点「全部播放」到 DOSS 音箱，日志出现「设备侧队列 · 原厂 PlayQueue」
- [ ] 点「全部播放」到通用渲染器，日志**不出现**「PlayQueue 未接受」

**收益**：`docs/86` 验收第 1 条才真的成立；下发路径才真的只剩 1 条。

### 5.3 🔴 补漏 2：输出开关没挪，用户还是改不了

`PlaybackOutput` 的**唯一写入点仍在洛雪页**（`LxSearchScreen.kt:88-99`），播放页没有。

后果比修之前更别扭：**5 个源都受这个开关影响了，但开关还锁在一个跟它们无关的页面里。**
用户在 NAS 页想听手机上放 → 得跑去洛雪页切，再回来点播放。

**做什么**：把「从哪出声 本机/音箱」那组 `FilterChip` 从 `LxSearchScreen` 挪到 `PlayerScreen`。

**验收**

- [ ] `grep -rn 'setOutput(' app/src/main` → 只在播放页
- [ ] 在播放页切「本机」，然后从 NAS / 本地 / 在线 / 歌单 / 洛雪 任一处点播放 → 声音从**手机**出来
- [ ] 切「音箱」→ 声音从音箱出来，**不两边一起响**
- [ ] 洛雪页不再有这个开关（避免两处都能改、状态不同步）

**收益**：这是 `docs/86` 1.3 真正的收益项（「6 个源都能本地播」），现在机制通了但用户摸不到。

### 5.4 🟠 补漏 3：「6 个源全通」实际是 5/6

`RadioViewModel.playUrl()` 仍是自己的流程（`switchMode:wifi` → `playUrl` → 6 秒探测首包）。

**这个有客观理由**（电台必须先切输入源，网络流首包慢要探测），**不用改**。
但 PR 标题别写「6 个源全通」，写成「5 个源 + 电台保持专用流程」。

- [ ] 在 `PlaybackRouter` 或 `docs` 里注明电台是例外及原因

### 5.5 🟠 补漏 4：4 个 PR 零测试

1.1 / 1.2 是纯重构，**行为应该完全不变** —— 这种改动最该有测试锁住「改完行为一致」。

**做什么**：给 `startQueueOnDevice` 补一组单测（假 Gateway / 假 Push / 假 Service）。

**验收**（三条，覆盖三段降级）

- [ ] 能力位为 false 时，**不调用** `DevicePlaylistGateway.push`
- [ ] PlayQueue 成功时，**不调用**后两段
- [ ] PlayQueue + m3u 都失败时，走到客户端逐首

**收益**：这类「漏了一个能力位判断」的问题以后进不来。

---

## 六、触感评估（#133）

### 6.1 ✅ 做对的地方

**加在 `AppCard` 这个唯一入口上，不是逐页加。** 这比我 `docs/86` 里写的「三处」更对 ——
`AppCard` 是全 App 唯一的标准卡片（19 处使用），加在这里不会漏。注释也写清了理由。

底部导航单独一处，合理（它不是 `AppCard`）。

### 6.2 ⚠️ 问题：按容器分档，不按语义分档

```kotlin
// AppCard 里
base.clickable {
    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    onClick()
}
```

`AppCard` 是**容器**，不是**动作**。所以点「配置备份」「诊断记录」「六键预设」全都一样震，
语义完全不同。

**这和项目铁律是同一类错误**：能力位决定渲染、不看型号；这里是**动作语义决定反馈、不看容器**。

**做什么**：保留 `AppCard` 的默认档（覆盖率高），补两个语义档：

| 位置 | 档位 | 语义 |
|---|---|---|
| 切歌成功 / 队列项换位 | `Confirm` | 「做成了」 |
| 操作失败（连不上设备 / 投流被拒） | `Reject` | 「没成」 |
| 拖动进度条跨整数秒 | `TextHandleMove` | 已有 |

**验收**

- [ ] 成功和失败**震感不同**（闭眼能分辨）
- [ ] 点普通卡片仍是轻档，不吵

**收益**：现在覆盖率 100% 但语义 0% —— 用户分不出「成功了」和「点了没反应」。

### 6.3 真机差异：不要换更重的档，加一个自检

你提的「有的 ROM 很轻、省电模式下一次都不给」是**真的**。处理方式：

**不要**因为「没感觉」就全局换 `Confirm` —— `Confirm` 的语义是「确认了重要操作」，
用在点卡片上会变成震动提醒，反而吵。

**要做**：设置页加一个「测试触感」按钮，点一下依次触发三档，让用户当场确认自己手机上到底有没有。

- [ ] 设置页有「测试触感」，三档可分辨
- [ ] 省电模式下至少能被告知「本机触感被系统限制」（读 `Vibrator.hasVibrator()` + 是否被禁）

---

## 七、接单顺序（照这个做）

| 顺序 | 做什么 | 依赖 | 预计 |
|---|---|---|---|
| **1** | **方案 D：BOM 改 2026.03.01，跑编译** | 无 | 半小时内知道行不行 |
| 2 | D 成功后：动效 token + 三处落地（Tab / 队列增删 / 播放键） | 1 | — |
| 3 | **补漏 1：堵 `PlaybackViewModel.play()` 第 4 条路径** | 无（可与 1 并行） | — |
| 4 | **补漏 2：输出开关挪到播放页** | 无（可与 1 并行） | — |
| 5 | 触感补 `Confirm` / `Reject` 两档 + 测试按钮 | 无 | — |
| 6 | 补漏 4：`startQueueOnDevice` 三条降级单测 | 3 | — |
| 7 | D 失败 → **转第三批（入口收敛）**，动效手写顶着 | 1 失败 | — |

**第 1 项是决策点**：成了后面全是加法；不成只损失半小时，且 main 一点不动。

---

## 八、明确不做

| 不做 | 理由 |
|---|---|
| **升 AGP 9 + Gradle + Kotlin 三个大件** | 为一个动效 token 推三个大件，收益风险不匹配。D 成了就永远不需要 |
| 追 BOM 2026.06.01 / 2026.08.00 | 2026.06.01 要 Kotlin 2.1.20；2026.08.00 要 AGP 9.1.0。收益和 2026.03.01 完全一样（都是 material3 1.4.0） |
| 因为「触感没感觉」全局换成重档 | 语义错了，会变成震动提醒 |
| 在同一个 PR 里既升 BOM 又改界面 | 升不上去时回退会连带界面改动 |

---

## 九、一句话

**之前撞墙不是「必须升级」，是「跳到了最头上那一档」。把 BOM 停在 2026.03.01，AGP/Gradle/Kotlin/compileSdk 一个都不用动，MotionScheme 一样拿到。先花半小时试这一行，试不过再谈别的。**

---

## 附：本文所有实测数据（可复核）

```
现状（7ab3d2d）：AGP 8.7.3 / Kotlin 2.1.0 / Gradle 8.11.1
                composeBom 2024.12.01 / compileSdk 35 / minSdk 24
                @Test 1,036

AAR 元数据实测：
  foundation 1.10.0   minAGP 8.6.0  minCompileSdk 35
  foundation 1.10.6   minAGP 8.6.0  minCompileSdk 35
  foundation 1.11.4   minAGP 8.6.0  minCompileSdk 35
  foundation 1.12.0   minAGP 9.1.0  minCompileSdk 37
  foundation 1.12.1   minAGP 9.1.0  minCompileSdk 37
  ui 1.11.4           minAGP 8.6.0  minCompileSdk 35
  material3 1.4.0     minAGP 8.6.0  minCompileSdk 35
  activity-compose 1.9.3              minCompileSdk 34

kotlin-stdlib 要求：
  runtime 1.9.0  → 2.0.21
  runtime 1.10.0 → 2.0.21
  runtime 1.10.6 → 2.0.21
  runtime 1.11.4 → 2.1.20

material3 1.4.0 classes.jar 含 MotionScheme 相关类 10 个

APK 0.9.134 校验：versionCode 134 / compileSdk 35 / targetSdk 35 / minSdk 24
                  classes.dex 内 performHapticFeedback 出现 2 次
```
