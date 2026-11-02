# 19 · 网络电台：真机跑通 + 独立页

用户这一轮两条：

> 电台无法添加链接，此功能需要你使用网上可用电台链接测试，使用过的电台链接自动保存。
> 网络电台要放在媒体库下一级目录，与手机本地音乐同级。

## 1. 「无法添加链接」的真实原因（两个，叠在一起）

| # | 原因 | 证据 |
|---|---|---|
| 1 | 地址缺 `http://` 时校验直接判非法 | `validate` 只认 `http://` / `https://` 开头，粘 `ice1.somafm.com/xxx` 必然失败 |
| 2 | 提交按钮此时是**禁用**的 | 按钮 `enabled = valid`，文案写着「填个有效地址」，点不动 → 看起来就是"没法添加" |
| 3 | （更关键）就算加进去了也**放不出声** | 见下 |

**放不出声的根因（真机查出来的）**：旧固件（DS-1831 / fw 3.6.5310）直接
`setPlayerCmd:play:<网络流>` 会回 `OK`，但 `getPlayerStatus` 永远 `status=stop`。
必须先切输入源：

```
setPlayerCmd:switchmode:wifi   → OK   (mode 变 0)
setPlayerCmd:play:<url>        → OK
getPlayerStatus                → status=play，curpos 持续递增
```

## 2. 真机逐条实测（2026-11-02，主机 `.164`）

| 电台 | 地址 | 结果 |
|---|---|---|
| Radio Paradise | `http://stream.radioparadise.com/mp3-192` | ✅ play，curpos 3184→8385 |
| NPR News | `http://npr-ice.streamguys1.com/live.mp3` | ✅ play |
| FIP（法国） | `http://icecast.radiofrance.fr/fip-hifi.aac` | ✅ play |
| CNR 中国之声 | `http://ngcdn001.cnr.cn/live/zgzs/index.m3u8` | ✅ play，curpos 递增 |
| CNR 音乐之声 | `http://ngcdn002.cnr.cn/live/yyzs/index.m3u8` | ✅ play，curpos 递增 |
| SomaFM Groove Salad | `http://ice1.somafm.com/groovesalad-128-mp3` | ❌ 回 OK 但 status 一直 stop |
| BBC Radio 1 | `https://stream.live.vc.bbcmedia.co.uk/bbc_radio_one` | ❌ 400（**https 在这台老固件上不可靠**）|

**结论**：内置列表只用**明文 http + 真机验证过**的地址（`VerifiedRadioCatalog`），
每条都带 `deviceVerifiedAt` 备注，方便以后复核。https 一律不收 —— 旧固件 TLS 栈太弱。

## 3. 用过的地址自动保存

`RadioViewModel.playUrl()` 播成功后调 `autoSave()`：库里没有就入库，名字取域名；
已有就复用它的 id。所以「直接播一条地址」试过的链接不会丢，下次打开就在列表里。

同地址不会重复入库（用户常连点两次添加）。

## 4. 位置：媒体库 → 与手机本地音乐同级

之前电台是音乐库里的一个 **tab**，用户根本找不到入口。现在：

```
媒体库（底部第二个 Tab）
 ├ NAS 音乐
 ├ 手机本地音乐
 ├ 网络电台      ← 新增，独立页
 └ 音乐库（搜索 / 收藏 / 最近，已去掉电台 tab）
```

`SubPage.Radio` 与 `SubPage.LocalQueue` 同级，页头同样用 `PageHeader`，顶部留白由外层统一提供。

## 5. 页面结构（三层，打开就懂）

1. **状态行**：在播什么 / 空闲，在播时右侧有「停止」；
2. **我的电台**：点一行即播，行尾 ✕ 二次确认删除；播不起来的会在副标题标红说明；
3. **内置可用电台**：「全部添加」一键入库，也可以单条点「＋」；
4. **直接播一条地址**：粘上就播，播成功的自动入库。

添加对话框：**提交按钮永远可点**（不再因为格式灰掉），地址边输边显示「将使用：http://…」，
真不合法才报具体原因。对话框里还列了内置电台，点一下自动填好。

## 验证

- `:core-protocol:test` + `:app:testDebugUnitTest` **291 用例 0 失败**（新增 `RadioUrlTest` 9 个）
- 真机逐条实测（上表），`switchmode:wifi` 这个前置步骤是实测出来的
- `:app:assembleDebug` 通过
- 测完已把设备恢复：`setPlayerCmd:stop`，主机与从机音量都回到 60，组内 1 台从机仍在
