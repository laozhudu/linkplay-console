#!/usr/bin/env python3
"""输出选择（本机 / 音箱）只许从播放页改。

现状是 warn：开关还在洛雪页（S1.4 搬到播放页后才转 error）。
做法：exit 0 但打印清单 —— 清单先摆出来，清一处划一处。
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "app", "src", "main")
ALLOWED = {"PlayerScreen.kt", "PlaybackRouter.kt"}

PAT = re.compile(r"\bsetOutput\s*\(")
hits = []
for dirpath, _dirs, files in os.walk(SRC):
    for name in files:
        if not name.endswith(".kt") or name in ALLOWED:
            continue
        path = os.path.join(dirpath, name)
        with open(path, encoding="utf-8") as f:
            for no, line in enumerate(f, 1):
                if line.strip().startswith("//"):
                    continue
                if PAT.search(line):
                    hits.append(f"{os.path.relpath(path, ROOT)}:{no}  {line.strip()[:90]}")

if hits:
    print(f"[warn] 输出开关还有 {len(hits)} 处在播放页之外（S1.4 会搬走）：")
    for h in hits:
        print("  " + h)
else:
    print("输出开关检查通过（只在播放页）。")
sys.exit(0)
