# 安装与覆盖升级

## 为什么之前每装一次都要先卸载

Android 允许「覆盖安装」有两个硬条件，缺一即失败：

| 条件 | 之前的状态 |
|---|---|
| `applicationId` 相同 | ✅ `io.github.laozhudu.linkplay.console` |
| **签名证书相同** | ❌ 每次 CI 构建都换了新钥匙 |
| `versionCode` 不低于已装的 | ⚠️ 一直是 `1`（同值不阻塞，但不利于判断新旧） |

签名为什么会变：Android 的 debug 构建默认用 `~/.android/debug.keystore`，**文件不存在就现造一把**。
GitHub Actions 的 runner 每次都是干净机器，于是每个包都带着一把全新的自签名证书。
系统发现「同包名、不同签名」，只能拒绝覆盖，报 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`。

实测证据（本仓库 0.7.0 与 0.8.0 两个产物）：

```
0.7.0  Signer #1 SHA-256: 896ddf62…d1fbd
0.8.0  Signer #1 SHA-256: d1073693…85cf9a   ← 不是同一个证书
```

## 现在的做法

### 1. 一把固定的签名密钥

- 仓库里**不放** keystore（公开仓库，密钥进 Git 等于把「用同一身份签发任意 APK」的能力公开）。
- CI 从 GitHub Secrets 取：`LINKPLAY_KEYSTORE_B64` / `LINKPLAY_STORE_PASSWORD` / `LINKPLAY_KEY_ALIAS` / `LINKPLAY_KEY_PASSWORD`。
- 本地从三处之一取，优先级：环境变量 → `local.properties` → `~/.linkplay/linkplay-release.jks`。
- **debug 与 release 用同一把** —— 交付走 debug 产物，若两者签名不同，装过 release 后就再也覆盖不了 debug。

当前证书指纹（可用于核对，不该再变）：

```
DN:      CN=linkplay-console, OU=personal, O=laozhudu, L=CN, C=CN
SHA-256: DC:3F:1F:24:40:E7:3E:EE:F5:1A:93:8A:79:4A:34:13:AD:D2:88:0C:F9:02:10:3E:74:87:A1:87:CD:2E:95:07
有效期:  10000 天
```

### 2. `versionCode` 随提交递增

`versionCode = git 提交数`，`versionName = 0.9.<提交数>`。这样本地构建与 CI 构建算出来的数字天然一致。

> CI 的 checkout 必须 `fetch-depth: 0`。浅克隆下 `git rev-list --count HEAD` 永远返回 1，
> 版本号不再递增，覆盖安装会重新失效——这是最容易踩回去的坑。

## 一次性操作（只做这一次）

已装在手机上的包（0.7.0 / 0.8.0）用的是**随机历史签名**，与新密钥对不上，所以：

1. **卸载**手机上的旧版本（桌面长按 → 卸载，或 `adb uninstall io.github.laozhudu.linkplay.console`）；
2. 安装新版 APK（CI 产出的 `linkplay-console-debug`）。

此后所有新版都能直接覆盖安装，**不用再卸载**。数据会保留（同一个 `applicationId`、签名不再变）。

## 自己核对两个包能不能互相覆盖

```bash
BT=$ANDROID_HOME/build-tools/35.0.0

# 1) 版本号必须递增
$BT/aapt2 dump badging new.apk | head -1 | grep -o "versionCode='[0-9]*'"
$BT/aapt2 dump badging old.apk | head -1 | grep -o "versionCode='[0-9]*'"

# 2) 签名指纹必须完全一致
$BT/apksigner verify --print-certs new.apk | grep "SHA-256 digest:"
$BT/apksigner verify --print-certs old.apk | grep "SHA-256 digest:"
```

两条都满足，`adb install -r new.apk` 或手机上点安装都能覆盖。

## 密钥备份（重要）

keystore 只存在于这台机器的 `/root/.hermes/keys/linkplay-release.jks`（密码在同目录 `pass.txt`）
和 GitHub Secrets 里。**两处都丢，就再也无法升级已装的应用**（只能卸载重装，数据丢失）。

备份建议：把 `linkplay-release.jks` + 密码存进你的密码管理器或加密云盘，别只留在一台机器上。

## 常见报错对照

| 报错 | 原因 | 处理 |
|---|---|---|
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | 签名不一致 | 核对指纹；不一致就卸载重装（一次性） |
| `INSTALL_FAILED_VERSION_DOWNGRADE` | 新包 `versionCode` 更低 | 确认 checkout 用了 `fetch-depth: 0` |
| `INSTALL_FAILED_OLDER_SDK` | 设备 Android 低于 minSdk 24 | 该设备不支持 |
| `App not installed`（无细节） | 系统里有同名不同签名的残留 | `adb uninstall <包名>` 后重装 |
