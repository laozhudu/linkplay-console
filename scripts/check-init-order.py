#!/usr/bin/env python3
"""检查 Kotlin 类里 `init { }` 是否在所有类级属性之后。

## 为什么需要这个检查

Kotlin **按声明顺序**初始化字段与执行 `init` 块。如果 `init` 写在某个属性之前，
而 init 里启动了异步任务（协程、线程、回调），那个任务可能在 init 还没跑完、
后面的字段还是 null / 未初始化时就访问它们。

真实事故（2026-11-04 09:45:34 诊断报告）：

    NullPointerException: Attempt to invoke interface method
      'java.lang.Object kotlin.Lazy.getValue()' on a null object reference

`ConsoleViewModel.init`（第 252 行）里 `probeSaved()` 启动的协程经
`withContext(Dispatchers.IO)` 派发到 IO 线程，抢在主线程跑完 init 之前访问了
`snapshots`（第 572 行，`by lazy`）→ lazy delegate 还是 null → 整个探活协程终止
→ 三台设备全部「状态=未探测」。

编译器不会报这个错，单测也测不到（要真跑 Android ViewModel）。
所以用静态检查兜住。

## 判据

只查 **4 空格缩进**的类级成员（`companion object` 里是 8 空格，不查）：
- `init {` 的行号必须大于该类里所有 `val` / `var` 声明的行号

## 退出码

0 = 通过；1 = 有违规
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "app/src/main"

# 4 空格缩进的类级属性声明
PROP = re.compile(r"^    (?:@\w+\s+)?(?:private |internal |protected |public )?(?:override )?(?:const )?(?:val|var) \w+")
INIT = re.compile(r"^    init\s*\{")


def check_file(path: Path):
    problems = []
    lines = path.read_text(encoding="utf-8").split("\n")
    init_lines = [(i + 1, l) for i, l in enumerate(lines) if INIT.match(l)]
    if not init_lines:
        return problems
    prop_lines = [(i + 1, l) for i, l in enumerate(lines) if PROP.match(l)]
    for init_no, _ in init_lines:
        after = [(n, l) for n, l in prop_lines if n > init_no]
        if after:
            first_no, first_line = after[0]
            problems.append(
                f"{path.relative_to(ROOT)}:{init_no}: init 块出现在类级属性之前\n"
                f"    后面的属性（第 {first_no} 行）在 init 执行时还是未初始化：\n"
                f"      {first_line.strip()}\n"
                f"    修法：把 init 块移到所有属性声明之后（见 ConsoleViewModel 类末尾的启动流程注释）"
            )
    return problems


def main():
    if not SRC.is_dir():
        print(f"跳过：{SRC} 不存在")
        return 0
    problems = []
    for path in sorted(SRC.rglob("*.kt")):
        problems.extend(check_file(path))
    if problems:
        print("发现 init 顺序问题：\n")
        for p in problems:
            print(p)
        print(f"\n共 {len(problems)} 处。")
        return 1
    print("init 顺序检查通过（所有 init 块都在类级属性之后）。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
