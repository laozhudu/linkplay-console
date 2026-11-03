# 22 · 状态恢复 / 音量浮层 / 应用图标 / 输入源

## 1. 重开 App 恢复上次的播放状态（含列表）

### 找到的真问题：三处存盘键不一致

| 位置 | 原来用的键 |
|---|---|
| NAS 流程 | `uuid ?: ip` |
| 队列服务 | `uuid.ifBlank { ip }` |
| 音乐库 | **SSID**（`deviceLabel`） |

存的和读的不是一个键，所以"恢复上次播放"注定读不到。现在统一到 `PlaybackKey`：

- `of(uuid, ip)` = uuid 优先，否则 `ip:<地址>`
- `candidates(uuid, ip)` = 恢复时依次尝试的两个键

### 恢复流程（`PlaybackViewModel.restoreLastSession`）

两路信息合起来才是完整状态：

| 来源 | 提供什么 |
|---|---|
| **设备侧** `getPlayerStatus` | 真的在放什么、音量、静音、进度、**输入源 mode**（权威） |
| **本地快照** `SessionStore.loadPlayback` | 队列列表与上次播到第几首（本固件不支持整列表推送，队列只存在我们这边） |

另外 `ConsoleViewModel.restoreLastDevice()` 会在启动时按 uuid（其次 IP）找回**上次那台设备**并静默选中 ——
否则播放页是空的、列表无处可挂。

`QueueBus.restore()` 把快照读回来后 **running 保持 false**（服务没在跑），
界面如实显示"已恢复、未接管"，点某一首才真正投流接管。

## 2. 音量键：系统样式浮层 + 更跟手

- 新增 `VolumeOverlay`（自绘，样式对齐 Android 原生音量面板：圆角卡片、喇叭、细进度条、百分比、设备名），
  因为系统那根条被我们吞掉了，不自己画用户就没有反馈。
- 由进程级 `VolumeOsd`（StateFlow）驱动，`Activity.dispatchKeyEvent` 写入，浮层 1.4 秒自动收起，
  连按会刷新计时（`nonce` 比对，避免上一轮定时器提前关掉这一轮）。
- **更跟手**：音量键这条路**不走去抖通道**，按一下立刻发一次 HTTP（按键本来就是离散事件，
  多等 60ms 只会显得钝；合并通道留给拖动音量条）。

## 3. 应用图标

原来 Manifest 里**没有 `android:icon`**，用的是系统默认图标 —— 所以看起来像"没做图标"。

新图标：深底 + 浅蓝**音箱箱体（高音在上、低音在下）+ 右侧竖向音量滑轨带白色滑块**，
一眼读作"音箱 + 音量控制"，符合遥控器定位。

- `mipmap-anydpi-v26/ic_launcher.xml` 自适应图标（background 取 `@color/ic_launcher_background`，
  含 Android 13+ 的 `<monochrome>` 主题图标）
- `mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher.png` + `_round.png` + `_foreground.png` 传统兜底（minSdk 24）
- 迭代过程：v1 播放三角+声波（被判"像播放器不像音箱"）→ v3 音箱+声波（声波糊成"耳朵"）
  → v4 音量条 → **v5 定稿**；48px 下专门检查过（滑轨加粗、滑块改白色提对比）

## 4. 输入源：可读、可控、有标识

`PlayerSource`（core，纯函数）：
- 可切换源：`wifi` / `line-in` / `bluetooth` / `udisk` / `optical`（逐字取自原厂资源串）
- 反向映射：按官方 mode 表把设备回报的数字翻成"现在在哪一路"
  （1/2/31/32 → wifi，11/16/42 → udisk，40 → line-in，41 → bluetooth，43 → optical）
- `describe(mode)` → `网络播放 · 本地播放列表` 这样的一句话，播放页顶栏直接显示

`PlaybackSnapshot` 新增 `sourceId` 与 `mode`；播放页加一行可横滚的来源 chip，当前源高亮且不可点。

### 两条真机教训（都写进了代码注释）

1. **切输入源会打断当前播放**：`.153` 上 `mode 2`（DLNA 在播）执行 `switchmode:wifi` 后变成 `mode 0 / status stop`。
   所以只要设备在播（PLAYING/PAUSED），切源必须**原地二次确认**，不用弹窗（浮层是明确讨厌的）。
2. **切走 Wi-Fi 的源有失控风险**：蓝牙/线路/U 盘/光纤在部分固件上会让手机控制不到设备，
   提示语写明"要回机身上切回来"。
3. 命令回 `OK` 不算成功：切完回读 `mode`，与目标不符就把设备真实回报写进错误提示。

## 验证

- `:core-protocol:test` + `:app:testDebugUnitTest` **323 用例 0 失败**
  （新增 `PlayerSourceTest` 6、`PlaybackKeyTest` 5）
- APK 内已确认打包 `mipmap-anydpi-v26/ic_launcher.xml` 与各密度 PNG
- 真机：`.153` 回报 `mode=2 / pause / vol=17`，与 `describe()` 预测一致；`switchmode:wifi` 后 `mode=0`
- ⚠️ 主机 `.164` 目前**离线**（curl 超时），组已解散（`.153` 的 `slaves=0`、`apcli0` 回到 `192.168.99.153`），
  所以本轮无法在主机上复测多房间
