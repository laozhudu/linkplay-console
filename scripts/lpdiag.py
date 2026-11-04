#!/usr/bin/env python3
"""
用途：声控台（linkplay-console）实机测试台 —— 让 agent 亲手跑整个 app 并拿到一手证据。
来源：docs/76-field-test-harness.md
调度：手动 / agent 按需调用；不进程驻留，每次一条命令。
影响范围：只读设备（device/scan/ui/log/shot）；写操作仅 tap/input/start/stop/clear/audio record。
         **不改 app 代码、不改音箱配置**。
依赖：python3（标准库）+ adb（/opt/android-sdk/platform-tools/adb，37.0.1）+ ffmpeg（录音分析）。
风险：低。但 `clear` 会清 app 数据、`input`/`tap` 会真的操作手机 —— 需显式指定。

核心思想：**三路对账**
  A 界面  截图 + uiautomator 树         → 用户看到什么
  B 自述  adb logcat -s LinkPlayDiag     → app 认为自己干了什么
  C 真值  直连音箱 httpapi               → 设备实际是什么状态

断言一律对 C 路（真值），不对 A/B。否则 app 有 bug 时测试也会"通过"。
"""

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import time
import urllib.request
from pathlib import Path
from typing import NoReturn

ADB = "/opt/android-sdk/platform-tools/adb"
PKG = "io.github.laozhudu.linkplay.console"
ACTIVITY = f"{PKG}/.MainActivity"
LOG_TAG = "LinkPlayDiag"
HERE = Path(__file__).resolve().parent
RUNS = HERE.parent / "runs"


# ─────────────────────────── 通用 ───────────────────────────

def sh(cmd, timeout=60, check=False):
    """跑命令，返回 (rc, stdout, stderr)。永不抛异常 —— 测试台自己崩了就没法定位问题。"""
    try:
        p = subprocess.run(cmd, shell=isinstance(cmd, str), capture_output=True,
                           text=True, timeout=timeout)
        return p.returncode, p.stdout, p.stderr
    except subprocess.TimeoutExpired:
        return 124, "", f"超时 {timeout}s: {cmd}"
    except FileNotFoundError as e:
        return 127, "", f"命令不存在: {e}"


def die(msg, code=1) -> NoReturn:
    print(f"✗ {msg}", file=sys.stderr)
    sys.exit(code)


def ok(msg):
    print(f"✓ {msg}")


# ─────────────────────────── C 路：设备真值 ───────────────────────────

def httpapi(ip, command, timeout=4):
    """直连音箱 httpapi。返回 dict / 原文 / None。

    这是**唯一可信的一手资料**：app 显示什么都可能是错的，设备回包不会。
    """
    url = f"http://{ip}/httpapi.asp?command={command}"
    try:
        with urllib.request.urlopen(url, timeout=timeout) as r:
            raw = r.read().decode("utf-8", "replace").strip()
    except Exception as e:
        return {"_error": str(e)}
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        return {"_raw": raw}


def hexname(v):
    """设备把中文名/曲名存成大写 hex，解回来。解不出就原样返回。"""
    if not isinstance(v, str) or not v:
        return v
    try:
        return bytes.fromhex(v).decode("utf-8", "replace")
    except ValueError:
        return v


def device_truth(ip):
    """一台设备的完整真值快照。三个命令合并，各自失败不影响其它。"""
    ps = httpapi(ip, "getPlayerStatus")
    ex = httpapi(ip, "getStatusEx")
    sl = httpapi(ip, "multiroom:getSlaveList")

    def g(d, k, default=None):
        return d.get(k, default) if isinstance(d, dict) else default

    return {
        "ip": ip,
        "reachable": "_error" not in ps,
        "error": g(ps, "_error"),
        "name": g(ex, "DeviceName"),
        "firmware": g(ex, "firmware"),
        "group": g(ex, "GroupName"),
        "securemode": g(ex, "securemode"),
        "wifi_channel": g(ex, "WifiChannel"),
        "battery_charging": g(ex, "battery") == "1",
        "battery_percent": int(g(ex, "battery_percent", -1)) if str(g(ex, "battery_percent", "")).isdigit() else None,
        "status": g(ps, "status"),
        "mode": g(ps, "mode"),
        "loop": g(ps, "loop"),
        "channel": g(ps, "ch"),
        "vol": int(g(ps, "vol", -1)) if str(g(ps, "vol", "")).isdigit() else None,
        "mute": g(ps, "mute") == "1",
        "curpos": int(g(ps, "curpos", 0)) if str(g(ps, "curpos", "")).isdigit() else None,
        "totlen": int(g(ps, "totlen", 0)) if str(g(ps, "totlen", "")).isdigit() else None,
        "title": hexname(g(ps, "Title")),
        "artist": hexname(g(ps, "Artist")),
        "slaves": g(sl, "slaves"),
        "slave_list": g(sl, "slave_list"),
    }


def cmd_device(args):
    ips = args.ip or []
    if args.all:
        ips = [d["ip"] for d in load_devices()]
    if not ips:
        die("要给 --ip 或 --all（见 scripts/devices.json）")
    out = [device_truth(ip) for ip in ips]
    if args.json:
        print(json.dumps(out, ensure_ascii=False, indent=2))
        return 0
    for d in out:
        if not d["reachable"]:
            print(f"✗ {d['ip']:16s} 不可达：{d['error']}")
            continue
        print(f"✓ {d['ip']:16s} {d['name'] or '?':10s} fw={d['firmware']} "
              f"vol={d['vol']} ch={d['channel']} {d['status']} "
              f"{d['curpos']}/{d['totlen']}ms battery={d['battery_percent']}% "
              f"slaves={d['slaves']}  《{d['title']}》")
    return 0


def load_devices():
    p = HERE / "devices.json"
    if not p.exists():
        return []
    return json.loads(p.read_text(encoding="utf-8")).get("devices", [])


# ─────────────────────────── adb 通道 ───────────────────────────

def adb(*args, serial=None, timeout=60):
    cmd = [ADB] + (["-s", serial] if serial else []) + list(args)
    return sh(cmd, timeout=timeout)


def adb_ready(serial=None):
    rc, out, err = adb("devices", serial=None)
    if rc != 0:
        die(f"adb 不可用：{err.strip()}\n  期望 {ADB}（37.x，支持无线配对）")
    lines = [l for l in out.splitlines()[1:] if l.strip()]
    if not lines:
        die("没有已连接的设备。先 `lpdiag.py connect <手机IP:端口>`（手机开无线调试）")
    if serial is None and len(lines) > 1:
        die(f"有多台设备，用 --serial 指定：{[l.split()[0] for l in lines]}")
    state = lines[0].split()[1] if len(lines[0].split()) > 1 else "?"
    if state != "device":
        die(f"设备状态是 {state}（不是 device）。手机上看一眼授权弹窗")
    return lines[0].split()[0]


def cmd_pair(args):
    """首次配对。注意：配对端口 ≠ 连接端口，两个都在手机「无线调试」页面上。"""
    rc, out, err = adb("pair", args.host, args.code, timeout=90)
    print(out.strip() or err.strip())
    return rc


def cmd_connect(args):
    rc, out, err = adb("connect", args.host, timeout=30)
    print((out or err).strip())
    if "connected" in (out or ""):
        ok(f"已连接 {args.host}")
        return 0
    return rc or 1


def cmd_devices(args):
    rc, out, err = adb("devices", "-l")
    print(out.strip() or err.strip())
    return rc


def cmd_shot(args):
    serial = adb_ready(args.serial)
    path = Path(args.path or f"/tmp/lpdiag-{int(time.time())}.png")
    path.parent.mkdir(parents=True, exist_ok=True)
    # exec-out 走二进制直传，避免 shell 层 CRLF 破坏 PNG
    with open(path, "wb") as f:
        p = subprocess.run([ADB] + (["-s", serial] if serial else []) +
                           ["exec-out", "screencap", "-p"], stdout=f, timeout=60)
    if p.returncode != 0 or not path.exists() or path.stat().st_size < 1000:
        die("截图失败（文件太小或没生成）")
    ok(f"{path}  ({path.stat().st_size} B)")
    return 0


NODE_RE = re.compile(r'<node[^>]*?text="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', re.S)


def dump_ui(serial):
    """uiautomator dump → 紧凑文本。

    原始 XML 比紧凑文本大 10-50 倍，直接喂模型是灾难。这里只留 text + 中心点。
    """
    adb("shell", "uiautomator", "dump", "/sdcard/lpdiag-ui.xml", serial=serial)
    rc, out, err = adb("shell", "cat", "/sdcard/lpdiag-ui.xml", serial=serial)
    if rc != 0 or not out.strip():
        return None, (err or "dump 为空")
    nodes = []
    for m in NODE_RE.finditer(out):
        text = m.group(1).strip()
        if not text:
            continue
        x1, y1, x2, y2 = (int(m.group(i)) for i in range(2, 6))
        nodes.append({"text": text, "cx": (x1 + x2) // 2, "cy": (y1 + y2) // 2,
                      "w": x2 - x1, "h": y2 - y1})
    return nodes, None


def cmd_ui(args):
    serial = adb_ready(args.serial)
    nodes, err = dump_ui(serial)
    if nodes is None:
        die(f"取 UI 树失败：{err}")
    if args.json:
        print(json.dumps(nodes, ensure_ascii=False, indent=2))
    else:
        for n in nodes:
            print(f"  ({n['cx']:4d},{n['cy']:4d})  {n['text']}")
    print(f"\n共 {len(nodes)} 个可读文本节点", file=sys.stderr)
    if not nodes:
        print("⚠ 一个文本节点都没读到 —— Compose 语义树可能没导出（见 docs/76 第四节）",
              file=sys.stderr)
    return 0


def cmd_tap(args):
    serial = adb_ready(args.serial)
    if args.xy:
        x, y = args.xy
    else:
        if not args.text:
            die("要给 --text 或 --xy X Y")
        nodes, err = dump_ui(serial)
        if nodes is None:
            die(f"取 UI 树失败：{err}")
        hit = [n for n in nodes if args.text in n["text"]]
        if not hit:
            die(f"界面上找不到「{args.text}」。当前可见：{[n['text'] for n in nodes][:20]}")
        x, y = hit[0]["cx"], hit[0]["cy"]
        ok(f"命中「{hit[0]['text']}」@ ({x},{y})")
    rc, out, err = adb("shell", "input", "tap", str(x), str(y), serial=serial)
    return rc


def cmd_input(args):
    serial = adb_ready(args.serial)
    # 空格要转义成 %s，这是 `input text` 的既有约定
    rc, out, err = adb("shell", "input", "text", args.text.replace(" ", "%s"), serial=serial)
    return rc


def cmd_app(args):
    serial = adb_ready(args.serial)
    if args.action == "start":
        rc, out, err = adb("shell", "am", "start", "-W", "-n", ACTIVITY, serial=serial)
        print((out or err).strip()[-400:])
    elif args.action == "stop":
        rc, out, err = adb("shell", "am", "force-stop", PKG, serial=serial)
    else:  # clear
        rc, out, err = adb("shell", "pm", "clear", PKG, serial=serial)
        print((out or err).strip())
    return rc


def cmd_log(args):
    serial = adb_ready(args.serial)
    # 环形缓冲默认 256KB，很容易被别的日志冲掉 —— 先放大到 8M
    adb("logcat", "-G", "8M", serial=serial)
    cmd = ["logcat", "-d", "-s", LOG_TAG]
    if args.follow:
        cmd = ["logcat", "-s", LOG_TAG]
    if args.since:
        cmd = ["logcat", "-d", "-s", LOG_TAG]  # -T 时间戳过滤，格式要求严，交给 --since-ts
    rc, out, err = adb(*cmd, serial=serial, timeout=120)
    print(out or err)
    return rc


REPORT_CHUNK = re.compile(r"\[报告 (\d+)\]\n", re.S)


def collect_report(serial):
    """从 logcat 把「[报告 N]」分块按 N 顺序拼回完整报告。

    坑：logcat 是按时间到达的，不保证 N 连续递增（多线程/缓冲刷新）。
    必须按 N 排序，不能按到达顺序。
    """
    rc, out, err = adb("logcat", "-d", "-s", LOG_TAG, serial=serial, timeout=120)
    if rc != 0:
        return None, err
    marks = list(REPORT_CHUNK.finditer(out))
    if not marks:
        return None, "logcat 里没有 [报告 N] —— 说明诊断页没被打开过（或缓冲被冲掉）"
    parts = {}
    for i, m in enumerate(marks):
        end = marks[i + 1].start() if i + 1 < len(marks) else len(out)
        parts[int(m.group(1))] = out[m.end():end]
    ordered = "".join(parts[k] for k in sorted(parts))
    return ordered, None


def cmd_report(args):
    serial = adb_ready(args.serial)
    text, err = collect_report(serial)
    if text is None:
        die(err)
    if args.save:
        p = Path(args.save)
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(text, encoding="utf-8")
        ok(f"{p}  ({len(text)} 字符)")
    else:
        print(text)
    return 0


# ─────────────────────────── 声音（真响没响） ───────────────────────────

def cmd_audio(args):
    # analyze 是纯本地分析（ffmpeg），不需要手机 —— 别让它被 adb 卡住
    if args.audio_action == "analyze":
        return analyze_audio(args.file)
    serial = adb_ready(args.serial)
    if args.audio_action == "record":
        # 用系统录音机录环境音 —— 音箱在响，手机麦克风能听见。这是唯一能证明"真的出声"的办法
        remote = "/sdcard/lpdiag-rec.m4a"
        adb("shell", "rm", "-f", remote, serial=serial)
        adb("shell", "am", "start", "-a", "android.provider.MediaStore.RECORD_SOUND",
            serial=serial)
        ok(f"已拉起录音机，录 {args.seconds}s ...")
        time.sleep(args.seconds)
        adb("shell", "input", "keyevent", "KEYCODE_BACK", serial=serial)
        time.sleep(2)
        local = Path(args.path or f"/tmp/lpdiag-rec-{int(time.time())}.m4a")
        adb("pull", remote, str(local), serial=serial)
        if not local.exists():
            die("录音没拉回来 —— 录音机可能弹了权限或没保存")
        ok(f"{local}")
        return 0

    return 0


def analyze_audio(file_path):
    """ffmpeg 测平均音量。静音 ≈ -91dB，正常播放 -30 ~ -10dB。

    这是"音箱真的响了没有"唯一客观的判据 —— 设备的 curpos 递增只能证明"在解码"。
    """
    f = Path(file_path or "")
    if not f.exists():
        die(f"文件不存在：{f}")
    rc, out, err = sh(["ffmpeg", "-hide_banner", "-i", str(f), "-af", "volumedetect",
                       "-f", "null", "-"], timeout=180)
    m = re.search(r"mean_volume:\s*(-?[\d.]+) dB", err or "")
    mx = re.search(r"max_volume:\s*(-?[\d.]+) dB", err or "")
    if not m:
        die(f"分析失败：{(err or '')[-300:]}")
    mean, peak = float(m.group(1)), float(mx.group(1)) if mx else None
    verdict = "静音（没出声）" if mean < -60 else "有声音" if mean > -40 else "很轻"
    print(f"平均 {mean} dB / 峰值 {peak} dB → {verdict}")
    return 0 if mean > -60 else 2


# ─────────────────────────── 证据包 ───────────────────────────

def new_run(name):
    ts = time.strftime("%Y-%m-%dT%H-%M-%S")
    d = RUNS / f"{ts}-{name}" if name else RUNS / ts
    (d / "screenshots").mkdir(parents=True, exist_ok=True)
    (d / "audio").mkdir(parents=True, exist_ok=True)
    return d


def cmd_evidence(args):
    """一次性抓全套现场：设备真值 + 报告 + 日志 + 崩溃 + 截图。

    这是"出问题时最少要留下的东西"，缺一样后面就得猜。
    """
    serial = adb_ready(args.serial)
    d = new_run(args.name)
    summary = {"at": time.strftime("%Y-%m-%d %H:%M:%S"), "serial": serial, "steps": []}

    truth = [device_truth(x["ip"]) for x in load_devices()]
    (d / "device-truth.json").write_text(
        json.dumps(truth, ensure_ascii=False, indent=2), encoding="utf-8")
    summary["devices"] = [{"ip": t["ip"], "reachable": t["reachable"], "status": t["status"],
                           "vol": t["vol"]} for t in truth]

    text, err = collect_report(serial)
    if text:
        (d / "report.txt").write_text(text, encoding="utf-8")
        summary["report_chars"] = len(text)
    else:
        summary["report_error"] = err

    _, logs, _ = adb("logcat", "-d", "-s", LOG_TAG, serial=serial, timeout=120)
    (d / "logcat.txt").write_text(logs or "", encoding="utf-8")

    _, crash, _ = adb("logcat", "-d", "-b", "crash", serial=serial, timeout=60)
    if crash.strip():
        (d / "crash.txt").write_text(crash, encoding="utf-8")
        summary["crash_lines"] = len(crash.splitlines())

    adb("exec-out", "screencap", "-p", serial=serial)
    with open(d / "screenshots" / "00-final.png", "wb") as f:
        subprocess.run([ADB, "-s", serial, "exec-out", "screencap", "-p"], stdout=f, timeout=60)

    (d / "00-摘要.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2),
                                    encoding="utf-8")
    ok(f"证据包：{d}")
    for k, v in summary.items():
        if k != "steps":
            print(f"  {k}: {v}")
    return 0


# ─────────────────────────── 流程编排 ───────────────────────────

def cmd_run(args):
    """跑一个 JSON 流程。步骤见 docs/76 第五节。

    设计约束：
    - 断言只对 C 路（设备真值），不对界面文字
    - 不用 sleep，用 wait 轮询到条件成立
    - 任何断言失败：立刻截图 + 存证据包 + 退出码 2
    """
    flow = json.loads(Path(args.flow).read_text(encoding="utf-8"))
    serial = adb_ready(args.serial)
    d = new_run(flow.get("name", Path(args.flow).stem))
    results = []
    for i, step in enumerate(flow.get("steps", []), 1):
        t0 = time.time()
        rec = {"n": i, "step": step, "ok": True, "ms": 0}
        try:
            exec_step(step, serial, d)
        except AssertionError as e:
            rec["ok"] = False
            rec["error"] = str(e)
            shot_fail(d, i, serial)
        except SystemExit:
            raise
        except Exception as e:
            rec["ok"] = False
            rec["error"] = f"{type(e).__name__}: {e}"
            shot_fail(d, i, serial)
        rec["ms"] = int((time.time() - t0) * 1000)
        results.append(rec)
        mark = "✓" if rec["ok"] else "✗"
        print(f"{mark} {i:2d}. {json.dumps(step, ensure_ascii=False)[:100]}"
              + (f"  ← {rec['error']}" if not rec["ok"] else f"  ({rec['ms']}ms)"))
        if not rec["ok"] and not flow.get("continue_on_fail"):
            break
    (d / "steps.json").write_text(json.dumps(results, ensure_ascii=False, indent=2),
                                  encoding="utf-8")
    failed = [r for r in results if not r["ok"]]
    print(f"\n{'✓ 全部通过' if not failed else f'✗ {len(failed)} 步失败'}  ({len(results)} 步)")
    print(f"证据：{d}")
    return 0 if not failed else 2


def shot_fail(d, i, serial):
    p = d / "screenshots" / f"fail-{i:02d}.png"
    with open(p, "wb") as f:
        subprocess.run([ADB, "-s", serial, "exec-out", "screencap", "-p"], stdout=f, timeout=60)


def exec_step(step, serial, d):
    do = step.get("do")
    if do == "clear":
        adb("shell", "pm", "clear", PKG, serial=serial)
    elif do == "start":
        adb("shell", "am", "start", "-W", "-n", ACTIVITY, serial=serial)
    elif do == "stop":
        adb("shell", "am", "force-stop", PKG, serial=serial)
    elif do == "tap":
        nodes, err = dump_ui(serial)
        if nodes is None:
            raise AssertionError(f"取 UI 树失败：{err}")
        hit = [n for n in nodes if step["text"] in n["text"]]
        if not hit:
            raise AssertionError(f"找不到「{step['text']}」，可见：{[n['text'] for n in nodes][:15]}")
        adb("shell", "input", "tap", str(hit[0]["cx"]), str(hit[0]["cy"]), serial=serial)
    elif do == "shot":
        p = d / "screenshots" / f"{step.get('as', 'shot')}.png"
        with open(p, "wb") as f:
            subprocess.run([ADB, "-s", serial, "exec-out", "screencap", "-p"],
                           stdout=f, timeout=60)
    elif do == "wait":
        deadline = time.time() + step.get("timeout", 15)
        while time.time() < deadline:
            if check_condition(step["until"], serial):
                return
            time.sleep(0.5)
        raise AssertionError(f"等不到 {step['until']}（{step.get('timeout', 15)}s）")
    elif do == "assert":
        if not check_condition(step["that"], serial):
            raise AssertionError(f"断言失败：{step['that']}")
    elif do == "audio":
        p = d / "audio" / f"step-{int(time.time())}.m4a"
        subprocess.run([sys.executable, __file__, "audio", "record",
                        "--seconds", str(step.get("seconds", 8)), "--path", str(p)],
                       capture_output=True, timeout=step.get("seconds", 8) + 60)
        rc, out, err = sh(["ffmpeg", "-hide_banner", "-i", str(p), "-af", "volumedetect",
                           "-f", "null", "-"], timeout=120)
        m = re.search(r"mean_volume:\s*(-?[\d.]+) dB", err or "")
        if not m:
            raise AssertionError("录音分析失败")
        mean = float(m.group(1))
        want = step.get("expect", "sound")
        if want == "sound" and mean < -60:
            raise AssertionError(f"期望出声，实测 {mean} dB（静音）")
        if want == "silence" and mean > -40:
            raise AssertionError(f"期望静音，实测 {mean} dB（有声音）")
    else:
        raise AssertionError(f"未知步骤：{do}")


def check_condition(cond, serial):
    """条件语法：`device:<ip>:<field>=<value>` / `ui:<文字>` / `log:<正则>`"""
    kind, _, rest = cond.partition(":")
    if kind == "device":
        ip, _, expr = rest.partition(":")
        field, _, want = expr.partition("=")
        t = device_truth(ip)
        return str(t.get(field)).lower() == want.lower()
    if kind == "ui":
        nodes, _ = dump_ui(serial)
        return any(rest in n["text"] for n in (nodes or []))
    if kind == "log":
        _, out, _ = adb("logcat", "-d", "-s", LOG_TAG, serial=serial, timeout=60)
        return re.search(rest, out or "") is not None
    raise AssertionError(f"未知条件：{cond}")


# ─────────────────────────── 入口 ───────────────────────────

def main():
    ap = argparse.ArgumentParser(prog="lpdiag", description="声控台实机测试台")
    ap.add_argument("--serial", help="多设备时指定序列号")
    sub = ap.add_subparsers(dest="cmd", required=True)

    p = sub.add_parser("device", help="[C路] 直连音箱取真值（不需要手机）")
    p.add_argument("--ip", action="append", help="可多次给")
    p.add_argument("--all", action="store_true", help="读 scripts/devices.json")
    p.add_argument("--json", action="store_true")
    p.set_defaults(fn=cmd_device)

    p = sub.add_parser("pair", help="首次无线配对（手机「无线调试」页）")
    p.add_argument("host"); p.add_argument("code")
    p.set_defaults(fn=cmd_pair)

    p = sub.add_parser("connect", help="连接（配对之后每次）")
    p.add_argument("host"); p.set_defaults(fn=cmd_connect)

    sub.add_parser("devices", help="列设备").set_defaults(fn=cmd_devices)

    p = sub.add_parser("shot", help="[A路] 截图")
    p.add_argument("path", nargs="?"); p.set_defaults(fn=cmd_shot)

    p = sub.add_parser("ui", help="[A路] UI 树（紧凑文本）")
    p.add_argument("--json", action="store_true"); p.set_defaults(fn=cmd_ui)

    p = sub.add_parser("tap", help="[A路] 按文字点击")
    p.add_argument("text", nargs="?")
    p.add_argument("--xy", nargs=2, type=int)
    p.set_defaults(fn=cmd_tap)

    p = sub.add_parser("input", help="[A路] 输入文字")
    p.add_argument("text"); p.set_defaults(fn=cmd_input)

    p = sub.add_parser("app", help="启动/停止/清数据")
    p.add_argument("action", choices=["start", "stop", "clear"])
    p.set_defaults(fn=cmd_app)

    p = sub.add_parser("log", help="[B路] 诊断日志")
    p.add_argument("-f", "--follow", action="store_true")
    p.add_argument("--since", help="（预留）")
    p.set_defaults(fn=cmd_log)

    p = sub.add_parser("report", help="[B路] 从 logcat 拼回完整诊断报告")
    p.add_argument("--save"); p.set_defaults(fn=cmd_report)

    p = sub.add_parser("audio", help="声音：record 录环境音 / analyze 判有没有出声")
    p.add_argument("audio_action", choices=["record", "analyze"])
    p.add_argument("--seconds", type=int, default=8)
    p.add_argument("--path"); p.add_argument("--file")
    p.set_defaults(fn=cmd_audio)

    p = sub.add_parser("evidence", help="一键抓全套现场（证据包）")
    p.add_argument("--name"); p.set_defaults(fn=cmd_evidence)

    p = sub.add_parser("run", help="跑 JSON 流程")
    p.add_argument("flow"); p.set_defaults(fn=cmd_run)

    args = ap.parse_args()
    sys.exit(args.fn(args))


if __name__ == "__main__":
    main()
