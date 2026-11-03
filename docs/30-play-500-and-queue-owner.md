# 30 · 切到新音箱点播放报 SOAP 500：两个独立真因

用户现象（截图）：切到 `doss1`（`.164`）后按播放，页面右下出现红字

```
SOAP 500 for "urn:schemas-upnp-org:service:AVTransport:1#Play" ...
```

而且播放列表显示「设备列表 122 首 · 第 3 首」—— 122 是**另一台** `.153` 的队列长度。
原厂不会这样。查下来是**两个独立的 bug 撞在一起**。

## 真因一：设备没有媒体时 `AVTransport.Play` 必然 500

真机实测（`.164`）：

```
GET http://192.168.99.164/httpapi.asp?command=getPlayerStatus
→ status=stop, mode=10, PlayMedium=UNKNOWN（GetMediaInfo 里也是 UNKNOWN，CurrentURI 为空）

POST /upnp/control/rendertransport1  SOAPACTION: "...AVTransport:1#Play"
→ HTTP 500
   <errorCode>704</errorCode>
   <errorDescription>play not allowed</errorDescription>
```

UPnP 标准里 **704 = "Play not allowed"**，含义就是"现在没有可播放的媒体"。

原厂为什么不报这个？因为它点播放之前一定先下发过 URI / 队列（设备列表页那个播放键走的是
「取列表 → CreateQueue → PlayQueueWithIndex」）。我们是"用户想按播放就直接 Play"，
在一台空设备上必然撞 704。

更糟的是我们把 `IOException` 的 message 原样贴到界面上 —— 用户看到的是 UPnP 的 XML 报文。

### 修法

1. **结构化异常** `SoapFaultException(httpCode, upnpCode, description, action)`，
   把 `errorCode` / `errorDescription` 带出来（原来只是拼进字符串）；
2. **`UpnpFault` 翻译层**：704/702 → 「这台音箱还没加载媒体。先去「我的音乐」点一首，
   或按「全部播放」把列表交给它。」；701 → 提示切回网络播放；401 → 「这台固件不支持这个操作（Action）」。
   **任何情况下都不把 SOAP/urn 报文给用户看**；
3. **`play()` 按原厂顺序走**：
   - 设备有队列（`plicount>0`）→ `AVTransport.Play`；被拒且码可重试 → 换 httpapi `setPlayerCmd:play`；
   - 设备没队列、本地有队列 → 先把队列交给设备（`DevicePlaylistGateway.push`）再播，等价于原厂的「全部播放」；
   - 都没有 → 中性色提示"该选歌了"，不再红字报错。
4. **界面分层**：新增 `PlaybackUiState.notice`（中性色）与 `error`（红色）分开，
   `error` 只放真故障。

## 真因二：队列没按设备归属，切设备后显示别人 122 首

`QueueBus` 是全局单例，`switchTo` 时没有被清。于是切到 `.164` 后：

- 播放页「播放列表 122」= `.153` 的列表；
- 「设备列表 122 首 · 第 3 首」= `.153` 的 `plicount/plicurr`（上一轮 `switchTo` 已重置 `_state`，
  但这两项来自 `refreshOnce` 写入的 `deviceQueueCount`，只要有一次旧设备的回读就会被填回来）。

用户对着别人的列表按播放，还会顺带触发真因一。

### 修法

`QueueRunState.ownerIp` + `QueueBus.setOwner(ip)`：

- 换设备 → 整份丢掉上一台的列表与日志（`QueueRunState(ownerIp = 新IP)`）；
- `begin/restore/clear` 内部自动带上当前 owner，调用方不用记（漏一处就会再串台）；
- 同一台设备重复 `setOwner` 不清空（首屏组合时会调两次，不能把刚恢复的列表清掉）；
- `switchTo` 里同时 `QueuePlaybackService.stop()`：客户端投流服务绑定的是**一台设备**的地址，
  留着它会让旧设备继续被逐首投流，与新设备界面不一致。

## 真机验证（本轮实测，不是推断）

**空设备 704**：

```
Play → 500 / 704 play not allowed    （直接 curl 复现，与截图一致）
```

**整列表下发闭环**（`.164`，此前是空设备）：

```
CreateQueue(3 首)         → 200
PlayQueueWithIndex(看 1)  → 200
getPlayerStatus           → status=play, mode=10, plicount=3, plicurr=1,
                            Title=6C6976652D30（"live-0"）, uri=…/MediaItems/170.mp3
BrowseQueue               → 读回 3 首
```

这条用 `PlayQueueLiveIT` 跑通（`LIVE_DEVICE=192.168.99.164 LIVE_LIST_URLS=…`），
**也顺手补上了上一轮"歌单下发未真机验证"的缺口**。

## 新增测试

- `SoapFaultTest`（7）：704 分类为"没媒体"、文案指向选歌、不泄漏原始报文、
  未知码也不泄漏、`friendlyOf` 解包、日志里保留技术细节、401 指出具体动作名
- `QueueBusOwnershipTest`（5）：换设备丢列表、restore 保留归属、重复 setOwner 不清空、
  begin 带上 owner、未知归属不认领
- `PlayQueueLiveIT`（2，需环境变量，CI 自动跳过）

全量 **412 用例 0 失败**。
