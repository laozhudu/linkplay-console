#!/usr/bin/env python3
"""下发原语只允许出现在 QueueStarter.kt。

来源：docs/86 不变量 I1；4 个 PR（#130 #131 #132 #139）修的都是这一件事。
规则：DevicePlaylistGateway.push / DeviceQueuePush.push / QueuePlaybackService.start
      只允许出现在 app/src/main/**/QueueStarter.kt。
注意：DevicePlaylistGateway 的 read / playIndex / toQueueItems 不算下发路径，不查。
风险：低，纯只读。
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "app", "src", "main")
ALLOWED = {"QueueStarter.kt"}

PRIMITIVES = [
    re.compile(r"\bDevicePlaylistGateway\s*\.\s*push\s*\("),
    re.compile(r"\bDeviceQueuePush\s*\.\s*push\s*\("),
    re.compile(r"\bQueuePlaybackService\s*\.\s*start\s*\("),
]

violations = []
for dirpath, _dirs, files in os.walk(SRC):
    for name in files:
        if not name.endswith(".kt"):
            continue
        path = os.path.join(dirpath, name)
        # 原语自身的定义文件不是调用点
        if name in ("DevicePlaylistGateway.kt", "DeviceQueuePush.kt", "QueuePlaybackService.kt"):
            continue
        with open(path, encoding="utf-8") as f:
            for no, line in enumerate(f, 1):
                stripped = line.strip()
                if stripped.startswith("*") or stripped.startswith("//"):
                    continue
                for pat in PRIMITIVES:
                    if pat.search(line):
                        rel = os.path.relpath(path, ROOT)
                        if name in ALLOWED:
                            continue
                        violations.append(f"{rel}:{no}  {stripped[:100]}")

if violations:
    print("下发原语出现在唯一入口之外：")
    for v in violations:
        print("  " + v)
    print()
    print("下发只有一条实现（docs/86 不变量 I1）。请调 startQueueOnDevice。")
    sys.exit(1)

print("下发原语检查通过（只在 QueueStarter.kt）。")
