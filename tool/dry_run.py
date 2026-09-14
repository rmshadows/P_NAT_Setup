#!/usr/bin/python3
# 模拟运行：只打印「会跑什么」，不真正执行。
# 在 Linux 上也能预览 Windows 路径，用来理清入口。
#
# 用法：
#   python3 tool/dry_run.py
#   python3 tool/dry_run.py --all
#   python3 tool/dry_run.py --os windows --desktop tightvnc --action load
#   python3 tool/dry_run.py --os linux --desktop dayon --action stop
#   PNAT_DRY_RUN=1  时各 deploy 脚本也应只打印（逐步接入）

from __future__ import print_function

import argparse
import os
import sys

_LIB = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "lib"))
if _LIB not in sys.path:
    sys.path.insert(0, _LIB)
from lib_paths import ensure_import_path, res_dir, runtime_dir
import lib_loadConf as loadConf

ROOT = ensure_import_path(os.path.dirname(os.path.abspath(__file__)))


def resolve_desktop(os_name, raw):
    """auto → windows=tightvnc，linux=dayon。vnc 在 Windows 视为 tightvnc。"""
    d = (raw or "auto").strip().lower()
    if d in ("", "auto"):
        return "tightvnc" if os_name == "windows" else "dayon"
    if d == "vnc":
        return "tightvnc" if os_name == "windows" else "vnc"
    return d


def exists_note(path):
    ok = os.path.exists(path)
    return "存在" if ok else "缺失"


def plan(os_name, desktop, action, conf):
    """返回 (标题, 行列表)。"""
    desktop = resolve_desktop(os_name, desktop)
    role = conf.get("role", "assisted")
    host = conf.get("host", "127.0.0.1")
    port = conf.get("port", "8080")
    res = res_dir(ROOT)
    rt = runtime_dir(ROOT)
    lines = []
    lines.append("平台={}  引擎={}  动作={}".format(os_name, desktop, action))
    lines.append("conf: role={} host={} port={}".format(role, host, port))
    lines.append("PNAT_ROOT={}".format(ROOT))

    if action == "entries":
        lines.append("")
        lines.append("【GUI 入口】")
        lines.append("  左键「加载远程桌面」 → 普通启动（读 desktop=，无感）")
        lines.append("  加载按钮右箭头 → Linux：Dayon / VNC；Windows 另有 RDP")
        lines.append("  右击「加载远程桌面」 → 高级设置（角色 / 地址 / 选屏；Linux VNC 含 :0/:N）")
        lines.append("  「JRA设置」页 → 体验版 / 托盘 / AHK")
        lines.append("  设置页·开发者预演 → 控制台文案（主界面以真实体验为准）")
        lines.append("  「停止远程桌面」 → 停当前引擎")
        lines.append("")
        lines.append("【CLI 入口】")
        lines.append("  python3 deploy/dayon/install.py [assisted|assistant] [--keep|--stop|--uninstall]")
        lines.append("  deploy/tightvnc/install.bat")
        lines.append("  python3 tool/dry_run.py --all")
        return "入口说明", lines

    if desktop == "dayon":
        script = os.path.join(ROOT, "deploy", "dayon", "install.py")
        if os_name == "linux":
            archive = os.path.join(res, "linux", "Dayon", "dayon.sh")
        else:
            archive = os.path.join(res, "windows", "Dayon",
                                   "assisted.exe" if role == "assisted" else "assistant.exe")
        lines.append("脚本: {} [{}]".format(script, exists_note(script)))
        lines.append("资源: {} [{}]".format(archive, exists_note(archive)))
        lines.append("运行时: {}".format(os.path.join(rt, "dayon")))
        if action == "load":
            cmd = "python3 deploy/dayon/install.py"
            if role == "assistant":
                cmd += " assistant"
            lines.append("将执行: {}".format(cmd))
            lines.append("效果: 解压到 .runtime/dayon，{} 连 {}:{}".format(
                "受助端" if role == "assisted" else "协助端", host, port))
        elif action == "stop":
            lines.append("将执行: python3 deploy/dayon/install.py --stop")
        elif action == "advanced":
            lines.append("将执行: （设置页）可选 keep / uninstall / 改 role·host·port")
            lines.append("  python3 deploy/dayon/install.py --keep")
            lines.append("  python3 deploy/dayon/install.py --uninstall")
            lines.append("  编辑 conf/app.conf 后再次 load")
        elif action == "monitor":
            lines.append("多显示器选屏: Dayon 当前跟物理会话走，选屏选项=说明/预留（设置页）")
        else:
            lines.append("未知动作")

    elif desktop == "tightvnc":
        if os_name != "windows":
            lines.append("注意: TightVNC 仅 Windows。Linux 模拟仍打印 Win 命令，不会真跑。")
        exe = os.path.join(res, "windows", "TightVNC", "RAServer", "start_server.exe")
        kill = os.path.join(res, "windows", "TightVNC", "RAServer", "kill_server.bat")
        msi = os.path.join(res, "windows", "TightVNC", "tightvnc.msi")
        bat = os.path.join(ROOT, "deploy", "tightvnc", "install.bat")
        tvn_conf = os.path.join(ROOT, "conf", "tightvnc.conf")
        lines.append("便携端: {} [{}]".format(exe, exists_note(exe)))
        lines.append("MSI: {} [{}]".format(msi, exists_note(msi)))
        lines.append("高级脚本: {} [{}]".format(bat, exists_note(bat)))
        lines.append("高级配置: {} [{}]".format(tvn_conf, exists_note(tvn_conf)))
        if action == "load":
            lines.append("将执行:（JRA 普通）")
            lines.append("  {} -reinstall / -start".format(exe))
            lines.append("  {} -controlservice -connect {}:{}".format(exe, host, port))
            lines.append("效果: 便携服务 + 逆向连接，镜像物理屏（可在设置页选显示器）")
        elif action == "stop":
            lines.append("将执行: {} -stop/-remove ; {}".format(exe, kill))
        elif action == "advanced":
            lines.append("将执行: deploy/tightvnc/install.bat")
            lines.append("  读 conf/tightvnc.conf → MSI 装成服务 / 密码 / 托盘")
            lines.append("可选: python deploy/tightvnc/HideRegedit.py")
        elif action == "monitor":
            lines.append("多显示器选屏: 设置页选项（不装虚拟二屏）")
            lines.append("  全部显示器 | 主屏 | 指定屏号（TightVNC Server 显示设置）")
        else:
            lines.append("未知动作")

    elif desktop == "vnc":
        raw = str(conf.get("vnc_display", "0")).strip()
        if raw.startswith(":"):
            raw = raw[1:]
        digits = ""
        for ch in raw:
            if ch.isdigit():
                digits += ch
            else:
                break
        dpy = int(digits) if digits else 0
        kind = "当前屏幕" if dpy <= 0 else "虚拟桌面"
        lines.append("Linux VNC 显示: :{}（{}）".format(dpy, kind))
        conn = str(conf.get("vnc_connect", "reverse")).strip().lower()
        reverse = conn not in ("listen", "wait", "in", "0")
        lines.append("实现: {}  连接: {}  加密: {}".format(
            conf.get("vnc_impl", "auto"),
            "反向" if reverse else "等待",
            "TLS" if str(conf.get("vnc_encrypt", "0")) == "1" else "否"))
        script = os.path.join(ROOT, "deploy", "vnc", "install.py")
        if dpy > 0:
            lines.append("状态: 本轮只做 :0，加载会被拒绝")
        elif action == "stop":
            lines.append("将执行: python3 deploy/vnc/install.py --stop")
        elif action == "advanced":
            lines.append("状态: 无系统服务档；右击改连接方式/实现/加密/密码")
        else:
            lines.append("将执行: python3 deploy/vnc/install.py")
            if reverse:
                lines.append("  反向连出协助端 Viewer {}:{}".format(host, port))
            else:
                lines.append("  等待连入 本机:5900")
        lines.append("脚本: {}".format(script))

    elif desktop == "rdp":
        if os_name != "windows":
            lines.append("状态: Linux 已去掉 RDP，请改选 Dayon 或 VNC")
        else:
            lines.append("状态: 规划中，尚未实现脚本")
            if action == "load":
                lines.append("将执行:（预告）启用 Remote Desktop + 防火墙；协助端 mstsc")
            elif action == "advanced":
                lines.append("将执行:（预告）端口 / 仅安全连接 / 用户组")
            else:
                lines.append("将执行:（预告）停用或恢复 RDP 策略")

    elif desktop == "x11vnc":
        lines.append("状态: 已并入 Linux VNC 的 :0（当前屏）")
        lines.append("将执行:（预告）共享 $DISPLAY / :0")

    else:
        lines.append("未知引擎: {}".format(desktop))

    return "{} / {} / {}".format(os_name, desktop, action), lines


def print_plan(title, lines):
    print("")
    print("=" * 60)
    print(title)
    print("=" * 60)
    for line in lines:
        print(line)


def main():
    conf = loadConf.read_app_conf(ROOT)
    ap = argparse.ArgumentParser(description="P_NAT 桌面层模拟运行（只打印）")
    ap.add_argument("--os", choices=["windows", "linux"],
                    help="模拟目标平台（默认=本机）")
    ap.add_argument("--desktop",
                    help="dayon|tightvnc|rdp|x11vnc|auto（默认读 conf）")
    ap.add_argument("--action",
                    choices=["load", "stop", "advanced", "monitor", "entries"],
                    default="load",
                    help="模拟动作")
    ap.add_argument("--all", action="store_true",
                    help="打印各平台×引擎×动作矩阵 + 入口说明")
    args = ap.parse_args()

    if args.all:
        print_plan(*plan("linux", "auto", "entries", conf))
        for os_name in ("linux", "windows"):
            for desk in ("auto", "dayon", "vnc", "tightvnc", "rdp"):
                if os_name == "linux" and desk == "tightvnc":
                    continue
                if os_name == "windows" and desk == "vnc":
                    continue
                if os_name == "windows" and desk == "dayon":
                    # Windows Dayon 可选，要打印
                    pass
                for action in ("load", "stop", "advanced", "monitor"):
                    if desk == "rdp" and action == "monitor":
                        continue
                    title, lines = plan(os_name, desk, action, conf)
                    print_plan(title, lines)
        print("")
        print("提示: 以上均为 DRY-RUN，未启动任何进程。")
        return

    os_name = args.os
    if not os_name:
        os_name = "windows" if os.path.sep == "\\" else "linux"
    desktop = args.desktop or conf.get("desktop", "auto")
    title, lines = plan(os_name, desktop, args.action, conf)
    print_plan(title, lines)
    print("")
    print("DRY-RUN 结束（未执行）。看全表: python3 tool/dry_run.py --all")


if __name__ == "__main__":
    main()
