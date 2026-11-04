#!/usr/bin/env python3
"""检查诊断标签是否"能对上用户说的话"。

## 为什么需要它

`safeLaunch(source) { ... }` 的 source 是诊断报告里唯一能定位"是哪条路崩的"的信息。
用户报问题时说的是"点播放没反应"、"首页设备不显示电量"。

如果标签写成 `ConsoleViewModel`（类名），那只是把行号换了个地方写，对定位没有帮助；
如果写成 `log` / `run` / `act` 这种占位词，等于没写。

2026-11 一次批量替换把 4 个心跳协程全写成了 `acquireMulticastLock`（那是另一个函数名），
诊断报告里看起来像"锁的获取炸了"，实际是卡片心跳 —— 这类错**人眼扫代码发现不了**，
只有在看报告时才会困惑。所以用脚本在 CI 挡住。

## 判据

1. 必须是字符串字面量（不能是类名拼出来的）
2. 不能以 ViewModel / Activity / Service / Fragment 结尾
3. 不能是纯占位词
4. 不能为空
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "app" / "src" / "main"

# 占位词：写了等于没写
BANNED = {
    "log", "run", "act", "block", "tmp", "todo", "x", "test",
    "httpAct", "doIt", "call", "invoke", "launch", "work", "task",
}

# 类名式标签：把行号换了个地方写
CLASS_SUFFIXES = ("ViewModel", "Activity", "Service", "Fragment", "Adapter")

# safeLaunch("literal") 或 host.launch("literal")
PATTERN = re.compile(r'\b(?:safeLaunch|\.launch)\(\s*"([^"]*)"')


def main() -> int:
    problems = []
    checked = 0

    for path in sorted(SRC.rglob("*.kt")):
        text = path.read_text(encoding="utf-8")
        for match in PATTERN.finditer(text):
            label = match.group(1)
            checked += 1
            line = text[: match.start()].count("\n") + 1
            where = f"{path.relative_to(ROOT)}:{line}"

            if not label.strip():
                problems.append(f"{where}: 标签为空")
            elif label in BANNED:
                problems.append(f"{where}: 标签 '{label}' 是占位词，写不出「用户点了什么」")
            elif label.endswith(CLASS_SUFFIXES):
                problems.append(f"{where}: 标签 '{label}' 是类名，不是动作名")

    if problems:
        print("诊断标签检查未通过：")
        for p in problems:
            print("  " + p)
        print()
        print("改法：用「用户做了什么」命名 —— probeSaved / playQueueItem / cardHeartbeat。")
        return 1

    print(f"诊断标签检查通过（{checked} 处）。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
