# 23 · 列表从设备同步 + 框架一致性审计

## A. 播放列表从设备同步（先看原厂怎么做的）

### 原厂的做法（反编译 + 真机对照）

`CurrPlayListActivity` 本身只是壳，真正的数据来自 `getPlayerStatus` 的两个字段：

```
plicount  设备侧列表长度
plicurr   当前第几首（**1 基**，设备播完自动 +1）
```

也就是说**列表在设备上，App 只负责展示与下发**。之前我们完全走客户端排队
（App 自己记队列、逐首投流），所以出现两个现象：
App 退出队列就断、设备的上一首/下一首用不了 —— 这正是用户说的「列表没从设备同步」。

### 关键发现：整列表可以用 m3u 推给设备

之前记的结论是「本固件不支持整列表推送」（基于 UPnP `CreateQueue` 只收空 `<Tracks/>`）。
这次找到了 **httpapi 的路子**，真机实测（2026-11-03，DS-1831 / fw 3.6.5310）：

```
setPlayerCmd:playlist:http://<手机>:<端口>/pl/xxx.m3u:0
  → OK，设备真的来 GET 这个 m3u（服务器日志确认）
  → getPlayerStatus: mode=20 status=play plicount=5 plicurr=1
  → setPlayerCmd:next / prev / pause / resume 全部正常
  → **把 m3u 服务停掉之后，上述操作依然全部正常**
```

最后一条最关键：设备拿到 URL 清单后自己持有，**App 退出也照播** —— 这就是原厂的行为，
也是「重开 App 状态都在」的基础。

### 两个必须知道的边界（真机试出来的）

| 现象 | 结论 |
|---|---|
| `playlist:<url>:1` / `:2` → 回 `OK` 但 `plicount=0` | **只有 index=0 生效**，非 0 静默失败 |
| 用 `next` 连发跳到第 N 首 | 不可靠（5 首里有一次 next 被吞） |
| 播完自动下一首 | 正常，`plicurr` 自动 +1 |

所以实现改成 **旋转列表**：把目标曲排到第一位，再以 `index=0` 下发。
设备侧第 1 首 = 本地列表第 `start+1` 首，映射关系 `(start + plicurr-1) % size`。
实测 5 首、从第 3 首起：`plicount=5 plicurr=1`，映射计算得到"本地第 3 首"，与预期一致。

### 实现

| 文件 | 作用 |
|---|---|
| `media/M3uBuilder` | 生成 `#EXTM3U`（时长未知写 `-1`，标题换行抹平避免截断列表）+ `rotateFrom()` |
| `media/LocalMediaServer` | 新增 `publishText/unpublish`：内存里直接供 m3u（不落临时文件） |
| `player/DeviceQueueState` | `plicount`/`plicurr` → `count`/`index`（1 基转 0 基，越界给 -1） |
| `playback/DeviceQueuePush` | 旋转 → 发布 m3u → `playlist:...:0` → 轮询确认 `plicount == 列表长度`；失败**如实失败**不假装成功 |
| `playback/QueueBus` | 新增 `deviceOwned` / `deviceOffset`，`syncDeviceIndex()` 把设备位置映射到本地列表高亮 |
| NAS / 音乐库 | 先试设备侧整列表；失败才退回客户端逐首投流（原有能力保留） |
| 播放页 | 列表 chip 旁直接写「设备列表 N 首 · 第 i 首」——把设备的真值摆出来 |

## B. 框架一致性审计（80 个 Kotlin 文件）

### 查出并修掉的问题

| # | 问题 | 处理 |
|---|---|---|
| 1 | **两份死代码**：`PlaybackCard.kt`、`PlaybackSummaryRow.kt` 全仓库零引用 | 删除 |
| 2 | **同一控件三份私有实现**：`PillButton` 在 MultiroomCard / LibraryScreen / RadioScreen 各写一遍，参数还不一致（`enabled+filled` vs 只有 `filled`） | 抽到 `ui/components/Buttons.kt` 一份，参数统一 |
| 3 | **`InfoLine` 两份**：`Common.kt` 有公开实现，`DeviceSettingsScreen` 又私有写了一份 | 删私有，用公共 |
| 4 | `CircleIcon` 只在播放页，其它地方手写 `Box+clip+clickable`（PageHeader 里也有一份） | 抽到 `Buttons.kt` |
| 5 | **15 处绕过 `StatusColors` 直接用 `colorScheme.error`** | 全改为 `StatusColors.error`（深底对比度已调到 8.28:1，`colorScheme.error` 没跟上） |
| 6 | **8 处裸 `RoundedCornerShape(x)`** 违反「圆角只用令牌」 | 改用 `AppShapesExpressive.squircle/pill` |
| 7 | **分层反转**：`playback/DeviceQueuePush` 要拿本机 IP，得反向依赖 `library/MediaServerService` | 抽 `util/Net.localIpv4()`，旧函数保留为兼容委托 |

### 审计后仍然统一的项（无需改）

- 裸 `dp` 字面量：**0 处**（全部走 `Spacing`）
- 一级页/二级页的返回规则、页头（`PageHeader`）、底部导航：已统一
- 主题令牌：颜色只用 `colorScheme` / `LocalExtraColors` / `OnDarkPalette` / `StatusColors`
- 状态源唯一性：音量 → `VolumeBus`；队列 → `QueueBus`；播放快照 → `PlaybackKey`

### 故意保留的差异（不是不统一）

| 差异 | 理由 |
|---|---|
| 播放页永远深色底 | 封面派生色按深底设计；换来一套恒定文字色，浅色主题下也不会出现"深字压深底" |
| NAS 用 `rendererIp`，别处用 `deviceIp` | 语义不同：NAS 流程里它是"渲染器地址"（可能不等于控制地址） |
| 客户端排队代码保留 | 设备拒收 m3u 时（非 http 直链、固件差异）需要它兜底 |

## 验证

- `:core-protocol:test` + `:app:testDebugUnitTest` **344 用例 0 失败**
  （新增 `M3uBuilderTest` 9、`LocalMediaServerTextTest` 5、`DeviceQueueStateTest` 7）
- 真机端到端（`.153`）：
  - 3 首 / 5 首两种列表 `plicount` 分别变成 3 / 5，`status=play`
  - `index=2` 静默失败（`plicount=0`）→ 已改为旋转 + `index=0`
  - 旋转方案：5 首从第 3 首起 → `plicount=5 plicurr=1`，映射到本地第 3 首 ✓
  - 停掉 m3u 服务后 `next/prev/pause/resume` 仍正常
- 测完已把设备 `stop`

## 仍未覆盖

- 主机 `.164` 依旧离线，多房间无法复测
- 设备不回填曲目元数据（`Title=Unknown`），所以列表标题只能用本地那份；
  界面因此是「设备的方向盘 + 本地的曲目单」，这一点在播放页那行小字里已明示
