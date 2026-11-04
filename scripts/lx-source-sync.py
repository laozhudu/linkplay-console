#!/usr/bin/env python3
"""
用途：从 pdone/lx-music-source 拉全套洛雪音源，实测哪支真能取到可播链接，输出推荐清单。
来源：docs/78-lx-source-integration.md
调度：手动（音源天天变，建议每周跑一次；也可挂 cron）
影响范围：只写 ./lx-sources/（下载的脚本）和 ./lx-sources.recommended.json，不动仓库其它文件。
依赖：python3 标准库 + node（/opt/node-26.13.0）+ scripts/lx-source-probe.js
风险：低。只读网络。

为什么不用仓库自带的健康检测：
  pdone 的 probe.py 明确写着「不执行任何音源脚本代码」—— 它只静态提取上游主机名再探活。
  所以它报「可用」只代表**上游主机活着**，不代表脚本能取到 URL。
  实测 2026-11-05：它报 11 支可用，真能五个平台全过的只有 3 支。
  这里执行脚本、真取 URL、真去拉，才是"能不能用"。
"""

import argparse
import json
import os
import re
import subprocess
import sys
import time
from pathlib import Path

REPO = "pdone/lx-music-source"
RAW = f"https://raw.githubusercontent.com/{REPO}/main"
HEALTH = f"https://raw.githubusercontent.com/{REPO}/health-status/health-state.json"
NODE = "/opt/node-26.13.0/bin/node"
PLATFORMS = ["kw", "kg", "tx", "wy", "mg"]

HERE = Path(__file__).resolve().parent
PROBE = HERE / "lx-source-probe.js"


def curl(url, dest, timeout=30):
    p = subprocess.run(["curl", "-sL", "--max-time", str(timeout), "-o", str(dest), url],
                       capture_output=True, text=True)
    if p.returncode != 0:
        return False, p.stderr.strip()[:100]
    if not dest.exists() or dest.stat().st_size < 100:
        return False, f"太小（{dest.stat().st_size if dest.exists() else 0}B）"
    return True, None


def list_sources():
    """从 README 抓音源目录名（README 是唯一稳定的清单来源）。"""
    p = subprocess.run(["curl", "-sL", "--max-time", "20", f"{RAW}/README.md"],
                       capture_output=True, text=True)
    names = re.findall(rf"{re.escape(RAW)}/([a-z0-9_\-]+)/latest\.js", p.stdout)
    # 保序去重
    seen, out = set(), []
    for n in names:
        if n not in seen:
            seen.add(n)
            out.append(n)
    return out


def health_status():
    """仓库自带的每日检测（只探主机，仅作参考，不作为判据）。"""
    p = subprocess.run(["curl", "-sL", "--max-time", "20", HEALTH],
                       capture_output=True, text=True)
    try:
        d = json.loads(p.stdout)
        return d.get("sources", {}), d.get("updated_at")
    except Exception:
        return {}, None


def probe(script_path, name, timeout=90):
    p = subprocess.run([NODE, str(PROBE), "--script", str(script_path), "--json",
                        "--name", name],
                       capture_output=True, text=True, timeout=timeout, cwd=str(HERE.parent))
    try:
        return json.loads(p.stdout)[0]
    except Exception:
        return {"error": f"探针输出无法解析（rc={p.returncode}）"}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dir", default="lx-sources", help="下载目录（默认 ./lx-sources）")
    ap.add_argument("--song", default="周杰伦", help="用来试取的歌名")
    ap.add_argument("--keep", action="store_true", help="保留上一轮的目录")
    args = ap.parse_args()

    outdir = Path(args.dir).resolve()
    outdir.mkdir(parents=True, exist_ok=True)

    health, health_at = health_status()
    names = list_sources()
    if not names:
        print("✗ 抓不到音源清单（README 变了？）", file=sys.stderr)
        return 1
    print(f"音源清单 {len(names)} 支；仓库自检更新于 {health_at or '未知'}")

    rows = []
    for n in names:
        d = outdir / n
        d.mkdir(parents=True, exist_ok=True)
        f = d / "latest.js"
        got, err = curl(f"{RAW}/{n}/latest.js", f)
        if not got:
            print(f"  ✗ {n:12s} 下载失败：{err}")
            rows.append({"name": n, "download": False, "error": err})
            continue
        r = probe(f, args.song)
        # 注意：r["name"] 是脚本头部的 @name（中文名），别拿目录名覆盖它
        r["dir"] = n
        r["health"] = health.get(n, "未知")
        r["bytes"] = f.stat().st_size
        rows.append(r)
        if r.get("error"):
            print(f"  ✗ {n:12s} {str(r['error'])[:60]}")
            continue
        ok = [s for s in PLATFORMS if (r.get("sources", {}).get(s) or {}).get("playable")]
        print(f"  {'✅' if len(ok) == 5 else '⚠️'} {n:12s} "
              f"{len(ok)}/5  {' '.join(ok)}  （仓库自检：{r['health']}）")

    # 排序：五平台全过优先，再按通过数
    def score(r):
        return len([s for s in PLATFORMS if (r.get("sources", {}).get(s) or {}).get("playable")])

    usable = [r for r in rows if score(r) > 0]
    usable.sort(key=lambda r: (-score(r), r["dir"]))

    best = [r["dir"] for r in usable if score(r) == 5]
    rec = {
        "generated_at": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
        "repo": REPO,
        "tested_song": args.song,
        "platforms": PLATFORMS,
        "all_five": best,
        "ranking": [
            {
                "dir": r["dir"],
                "title": r.get("name"),
                "version": r.get("version"),
                "pass": score(r),
                "platforms": {s: bool((r.get("sources", {}).get(s) or {}).get("playable"))
                              for s in PLATFORMS},
                "url": f"{RAW}/{r['dir']}/latest.js",
                "health_by_repo": r.get("health"),
            }
            for r in usable
        ],
        "failed": [r.get("dir") or r.get("name") for r in rows if score(r) == 0],
    }
    dest = Path("lx-sources.recommended.json")
    dest.write_text(json.dumps(rec, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"\n五个平台全过：{', '.join(best) if best else '（无）'}")
    print(f"推荐清单 → {dest}")
    if best:
        print(f"\n最简单用法：在洛雪/声控台的「导入脚本」里贴这一行 ——")
        print(f"  {RAW}/{best[0]}/latest.js")
    return 0 if best else 2


if __name__ == "__main__":
    sys.exit(main())
