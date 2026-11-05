#!/usr/bin/env python3
"""
用途：检查仓库文档里的「引用断链」，防止再出现「文档里引用了一篇根本不存在的文档」。
来源：2026-11-04 发现 docs/64 引用了「综合优化评估」「实机接口方案讨论」两篇，
      但那两篇一直只存在工作区、从没入库 —— 读者顺着引用找不到，等于丢失。
调度：CI（排在 check-init-order / check-diagnostics-labels 之后），也可本地手动跑。
影响范围：只读 docs/，不改任何文件。
依赖：python3（标准库）。
风险：低。规则误报时在该行加 `<!-- docref-ok -->` 豁免。

检查两条：
1. `docs/NN-xxx.md` 形式的引用，文件必须真实存在。
2. `依据：xxx` 这类元引用必须带 `docs/` 路径。**裸名字正是上次漏掉的那两篇的写法** ——
   写成「依据：综合优化评估」时，任何链接检查都抓不到，只有人读到才会发现找不到。
   确实还没入库的内容，要写成「工作区草稿，未入库」。
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DOCS = ROOT / "docs"
EXEMPT = "<!-- docref-ok -->"

# 「依据：」后面出现这些词就算合规（指向仓库内真实存在的东西）
ALLOWED_IN_EVIDENCE = (
    "docs/", "README", "NOTICE", "LICENSE",
    "scripts/", "core-", "app/", "settings.gradle",
    "未入库", "工作区草稿",
)


def doc_files():
    return sorted(p for p in DOCS.rglob("*.md") if p.is_file())


def check_path_refs(text, existing):
    """第 1 条：docs/NN-xxx.md 必须存在。"""
    errs = []
    for i, line in enumerate(text.split("\n"), 1):
        if EXEMPT in line:
            continue
        for m in re.finditer(r'docs/(\d{2}-[A-Za-z0-9_\-]+\.md)', line):
            ref = m.group(1)
            if ref not in existing:
                errs.append((i, "引用了不存在的文档 docs/%s" % ref))
    return errs


def check_bare_evidence_refs(text):
    """第 2 条：「依据：」后面必须带路径或注明未入库。"""
    errs = []
    for i, line in enumerate(text.split("\n"), 1):
        if EXEMPT in line:
            continue
        m = re.search(r'依据\*\*：(.+)|依据：(.+)', line)
        if not m:
            continue
        val = (m.group(1) or m.group(2) or "").strip()
        if not val:
            continue
        if any(a in val for a in ALLOWED_IN_EVIDENCE):
            continue
        errs.append((
            i,
            "「依据：」引用的是裸名字（%s），必须写成 docs/NN-xxx.md，"
            "或注明「工作区草稿，未入库」" % val[:40],
        ))
    return errs


def main():
    if not DOCS.is_dir():
        print("找不到 %s" % DOCS)
        return 1

    existing = set(p.name for p in doc_files())
    total = 0

    for path in doc_files():
        text = path.read_text(encoding="utf-8")
        rel = path.relative_to(ROOT)
        problems = check_path_refs(text, existing) + check_bare_evidence_refs(text)
        for line_no, msg in problems:
            print("%s:%d: %s" % (rel, line_no, msg))
            total += 1

    if total:
        print("\n共 %d 处引用问题。" % total)
        print("规则：仓库文档只能引用 docs/NN-xxx.md（且必须存在）；")
        print("      内容还没入库时写「工作区草稿，未入库」，不要写裸名字。")
        return 1

    print("文档引用检查通过（%d 篇）。" % len(existing))
    return 0


if __name__ == "__main__":
    sys.exit(main())
