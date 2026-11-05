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

## 下一道工序

`docs/90` S1.1（`PlayIntent` 落地）→ S1.2（`dispatch` 唯一执行点）→ S1.3（三入口接入）
→ **S1.4（输出开关搬到播放页，用户可感）**
