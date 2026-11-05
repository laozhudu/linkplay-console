#!/usr/bin/env python3
"""不许用全局 opt-in 绕过编译期门禁。

为什么：@RequiresOptIn 挡得住「不小心」，挡不住「加一行 -opt-in= 全局开关」。
        全局开了以后所有文件都不再报错，门禁等于拆掉，而且没人会注意到。
规则：构建脚本里不许出现 -opt-in= / optIn(...)。
      .kt 里的 @file:OptIn 是**允许**的 —— 它是局部的、看得见的、要写注解的。
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TARGETS = []
for dirpath, dirs, files in os.walk(ROOT):
    dirs[:] = [d for d in dirs if d not in (".git", "build", ".gradle", "node_modules")]
    for name in files:
        if name.endswith(".kts") or name == "gradle.properties":
            TARGETS.append(os.path.join(dirpath, name))

BAD = [
    (re.compile(r"-opt-in\s*="), "命令行 -opt-in="),
    (re.compile(r"\boptIn\s*\("), "freeCompilerArgs 里的 optIn("),
    (re.compile(r"\boptIn\s*\+="), "optIn +="),
]

hits = []
for path in TARGETS:
    with open(path, encoding="utf-8", errors="replace") as f:
        for no, line in enumerate(f, 1):
            stripped = line.strip()
            if stripped.startswith("//") or stripped.startswith("#"):
                continue
            for pat, label in BAD:
                if pat.search(line):
                    hits.append(f"{os.path.relpath(path, ROOT)}:{no}  {label}  {stripped[:90]}")

if hits:
    print("发现全局 opt-in（会把编译期门禁整片拆掉）：")
    for h in hits:
        print("  " + h)
    sys.exit(1)

print("全局 opt-in 检查通过（禁用的开关没有被绕过）。")
