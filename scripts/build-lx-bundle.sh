#!/usr/bin/env bash
#
# 用途：把洛雪（lx-music-mobile）的 musicSdk 打包成 core-lx 的 assets 资源。
# 来源：lyswhut/lx-music-mobile（Apache-2.0），版本见 LX_TAG。
# 调度：手动。产物 core-lx/src/main/assets/lx-music-sdk.bundle.js 提交进仓库，
#       CI 不重新打包（避免每次构建都 clone 上游 + npm install）。
# 影响范围：只写 core-lx/src/main/assets/lx-music-sdk.bundle.js。
# 依赖：git、node、npm、网络（拉上游源码与 npm 包）。
# 风险：上游重构会导致替换点失效（脚本会报错停下，不会产出坏包）。
#
# 用法：
#   scripts/build-lx-bundle.sh              # 自动 clone 到临时目录
#   LX_SRC=/path/to/lx-music-mobile scripts/build-lx-bundle.sh   # 用已有检出
#
set -euo pipefail

LX_REPO="${LX_REPO:-https://github.com/lyswhut/lx-music-mobile.git}"
LX_TAG="${LX_TAG:-v1.9.2}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT/core-lx/src/main/assets/lx-music-sdk.bundle.js"
WORK="${WORK:-/tmp/lx-bundle-work}"

echo "== 1/6 准备上游源码 =="
if [ -n "${LX_SRC:-}" ]; then
  SRC="$LX_SRC"
  echo "   使用已有检出: $SRC"
else
  rm -rf "$WORK/src"
  mkdir -p "$WORK"
  git clone --depth 1 --branch "$LX_TAG" -q "$LX_REPO" "$WORK/src"
  SRC="$WORK/src"
fi
[ -f "$SRC/src/utils/musicSdk/wy/index.js" ] || { echo "!! 找不到 $SRC/src/utils/musicSdk，上游结构变了"; exit 1; }

echo "== 2/6 拷贝源码到工作区 =="
rm -rf "$WORK/build"
mkdir -p "$WORK/build"
cp -r "$SRC/src" "$WORK/build/src"
cp "$ROOT/core-lx/js/entry.js" "$WORK/build/entry.js"
cp "$ROOT/core-lx/js/bridge.js" "$WORK/build/src/bridge.js"
cp -r "$ROOT/core-lx/js/shim/." "$WORK/build/shim/"

echo "== 3/6 替换 RN 专属实现 =="
# request.js：原版用 RN 网络栈，换成走原生桥
cp "$WORK/build/shim/request.js" "$WORK/build/src/utils/request.js"
# crypto：原版转调 RN 原生模块，换成同步桥
cp "$WORK/build/shim/crypto.ts" "$WORK/build/src/utils/nativeModules/crypto.ts"
# setting state：原版是 RN 的 store，换成静态默认值
cp "$WORK/build/shim/setting-state.ts" "$WORK/build/src/store/setting/state.ts"

echo "== 4/6 装依赖（pako/he 要用 v1，v2 是 ESM 且没有 default 导出）=="
cd "$WORK/build"
[ -f package.json ] || echo '{"name":"lx-bundle","private":true}' > package.json
npm i --silent --no-audit --no-fund esbuild@0.28.2 pako@1.0.11 he@1.2.0 crypto-js buffer >/dev/null

echo "== 5/6 打桩 RN 模块 =="
mkdir -p node_modules/react-native-quick-base64 node_modules/react-native-quick-md5 node_modules/react-native-background-timer
printf '{"name":"react-native-quick-base64","version":"0.0.0","main":"index.js"}' > node_modules/react-native-quick-base64/package.json
printf '{"name":"react-native-quick-md5","version":"0.0.0","main":"index.js"}' > node_modules/react-native-quick-md5/package.json
printf '{"name":"react-native-background-timer","version":"0.0.0","main":"index.js"}' > node_modules/react-native-background-timer/package.json
# 用 esbuild 的 alias 指到我们的 shim，index.js 只是占位
echo 'module.exports = {}' > node_modules/react-native-quick-base64/index.js
echo 'module.exports = {}' > node_modules/react-native-quick-md5/index.js
echo 'module.exports = {}' > node_modules/react-native-background-timer/index.js

echo "== 6/6 打包 =="
./node_modules/.bin/esbuild entry.js \
  --bundle --platform=browser --format=iife --target=es2020 \
  --alias:@=./src \
  --alias:react-native-quick-base64=./shim/quick-base64.js \
  --alias:react-native-quick-md5=./shim/quick-md5.js \
  --alias:react-native-background-timer=./shim/bg-timer.js \
  --define:process.env.NODE_ENV='"production"' \
  --outfile="$OUT" \
  --log-level=warning

SIZE=$(stat -c %s "$OUT")
echo
echo "产物: $OUT"
echo "大小: $SIZE 字节"
grep -q "lxSdkCall" "$OUT" || { echo "!! 产物里没有 lxSdkCall，打包失败"; exit 1; }
echo "自检: 产物包含 lxSdkCall ✓"

# 把上游 tag 写进 assets，让 App 能在诊断里说出"现在跑的是哪一版"。
# 为什么要这一步：官方升级后，如果没有这个文件，App 里看到的和实际跑的无法核对，
# "要不要升级"就永远靠猜。
VER_FILE="$ROOT/core-lx/src/main/assets/lx-bundle-version.txt"
printf '%s\n' "$LX_TAG" > "$VER_FILE"
echo "   bundle 版本已写入 ${VER_FILE#$ROOT/} → $LX_TAG"
