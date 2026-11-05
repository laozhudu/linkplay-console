#!/usr/bin/env python3
"""Screen 层不许判断设备型号。

现状是 warn：还有 1 处（S2.3 能力位补齐后才转 error）。
判据见 docs/86 不变量 I3：界面渲染看 DeviceCapabilities，不看 DeviceProfile。
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "app", "src", "main")
PAT = re.compile(r"\bDeviceProfile\s*\.")

hits = []
for dirpath, _dirs, files in os.walk(SRC):
    for name in files:
        if not name.endswith("Screen.kt"):
            continue
        path = os.path.join(dirpath, name)
        with open(path, encoding="utf-8") as f:
            for no, line in enumerate(f, 1):
                if line.strip().startswith("//"):
                    continue
                if PAT.search(line):
                    hits.append(f"{os.path.relpath(path, ROOT)}:{no}  {line.strip()[:90]}")

if hits:
    print(f"[warn] 界面里还有 {len(hits)} 处按型号判断（S2.3 会改成能力位）：")
    for h in hits:
        print("  " + h)
else:
    print("界面型号判断检查通过（零处）。")
sys.exit(0)
