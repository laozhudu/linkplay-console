#!/usr/bin/env python3
"""输出选择（本机 / 音箱）只许从播放页改。

S1.4 已把开关从洛雪页搬到播放页，所以这里从 warn 转成 error。
为什么要有这条：这个开关管着全部 6 个源，但它藏在跟源无关的页面里半年多 ——
用户想在 NAS 页用手机放，得跑去洛雪页切完再回来。
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
    print(f"输出开关出现在播放页之外（{len(hits)} 处）：")
    for h in hits:
        print("  " + h)
else:
    print("输出开关检查通过（只在播放页）。")
sys.exit(1 if hits else 0)
