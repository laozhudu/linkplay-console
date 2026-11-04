#!/usr/bin/env python3
"""检查「动作耗时」的动作名是否统一、是否真的接上了。

## 为什么需要它

耗时账本按**动作名**聚合。名字一漂移，报告就会变成：

    refreshDevices 平均 240ms
    refresh        平均 610ms

两条其实是同一件事，"平均"和"最慢"全失真，而看报告的人会以为发现了一个新的慢动作。
本项目因为同类问题吃过一次亏：4 个心跳协程被批量替换成同一个标签，
报告看起来像"锁获取炸了"，实际是卡片心跳。

动作名现在是枚举（`ActionTiming.Action`），**写错名字编译就过不了**。
所以这里只查类型系统管不到的三件事：

1. 枚举 `id` 必须唯一、驼峰小写开头（`setVolume`，不是 `SetVolume` / `set_volume`）；
2. 每个枚举值在 `app/src/main` 里**至少有一处使用** —— 声明了没接上，等于对报告撒谎；
3. `id` 不能和 diagnostics 的其它命名冲突（如等于某个 ViewModel 名）。
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "app" / "src" / "main"
DECL = SRC / "kotlin" / "io" / "github" / "laozhudu" / "linkplay" / "console" / "diagnostics" / "ActionTiming.kt"

ENUM_RE = re.compile(r'^\s{8}([A-Z][A-Z0-9_]*)\("([a-zA-Z0-9]+)"\)', re.M)


def main() -> int:
    problems = []

    if not DECL.exists():
        print("找不到 " + str(DECL.relative_to(ROOT)))
        return 1

    text = DECL.read_text(encoding="utf-8")
    enum_start = text.find("enum class Action")
    if enum_start < 0:
        print("ActionTiming.kt 里找不到 enum class Action")
        return 1
    enum_body = text[enum_start: text.find("}", enum_start)]
    entries = ENUM_RE.findall(enum_body)
    if not entries:
        print("enum class Action 里没有值")
        return 1

    seen = {}
    for key, ident in entries:
        if ident in seen:
            problems.append("动作名 '" + ident + "' 被 " + seen[ident] + " 和 " + key + " 重复使用")
        seen[ident] = key
        if not re.fullmatch(r"[a-z][a-zA-Z0-9]*", ident):
            problems.append("动作名 '" + ident + "' 不是驼峰小写开头（示例：setVolume / refreshDevices）")
        if ident.endswith(("ViewModel", "Activity", "Service", "Fragment")):
            problems.append("动作名 '" + ident + "' 是类名，不是动作")

    # 使用次数：除声明文件外，Action.XXX 出现在 app/src/main 的其它文件里
    used = {key: 0 for key, _ in entries}
    for path in sorted(SRC.rglob("*.kt")):
        if path == DECL:
            continue
        body = path.read_text(encoding="utf-8")
        for key in used:
            used[key] += len(re.findall(r"\bAction\." + key + r"\b", body))

    for key, ident in entries:
        if used[key] == 0:
            problems.append("动作 " + ident + "（" + key + "）声明了但没有任何地方使用 —— 报告里永远不会出现它")

    if problems:
        print("动作名检查未通过：")
        for p in problems:
            print("  " + p)
        print()
        print("改法：动作名一律从 enum Action 取；新动作先加一行，再在调用点用 begin/measure 包住。")
        return 1

    wired = sum(1 for v in used.values() if v > 0)
    print("动作名检查通过（" + str(len(entries)) + " 个动作 / " + str(wired) + " 个已接上）。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
