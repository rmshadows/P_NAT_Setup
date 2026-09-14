#!/usr/bin/python3
# Linux VNC：先做 :0 当前屏
#
# - 默认 x11vnc（共享当前 DISPLAY，可反向连出）
# - 可选 TigerVNC x0vncserver（只监听，协助端连过来）
# - 运行时只写 .runtime/vnc/，不碰 ~/.vnc
# - 动词与 Dayon 对齐：无参数启动 / --stop / --keep / --uninstall

import argparse
import os
import signal
import shutil
import subprocess
import sys
import time

_LIB = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "lib"))
if _LIB not in sys.path:
    sys.path.insert(0, _LIB)
from lib_paths import abs_under, ensure_import_path, runtime_dir
import lib_loadConf as loadConf

ROOT = ensure_import_path(os.path.dirname(os.path.abspath(__file__)))
RUNTIME_ROOT = os.path.join(runtime_dir(ROOT), "vnc")
MARKER = os.path.join(RUNTIME_ROOT, ".owned_by_p_nat_setup")
PASSWD_FILE = os.path.join(RUNTIME_ROOT, "passwd")
SSL_PEM = os.path.join(RUNTIME_ROOT, "server.pem")
PID_FILE = os.path.join(RUNTIME_ROOT, "vnc.pid")
LOG_FILE = os.path.join(RUNTIME_ROOT, "vnc.log")

Windows = os.path.sep == "\\"


def abs_under_root(*parts):
    return abs_under(ROOT, *parts)


def read_conf():
    return loadConf.read_app_conf(ROOT)


def ensure_linux():
    if Windows:
        print("当前脚本只支持 Linux。Windows 用 TightVNC。")
        sys.exit(1)


def session_type():
    return (os.environ.get("XDG_SESSION_TYPE") or "").strip().lower()


def current_display():
    d = (os.environ.get("DISPLAY") or "").strip()
    if not d:
        d = ":0"
    return d.split(".")[0]


def parse_display_num(raw):
    v = (raw or "0").strip()
    if v.startswith(":"):
        v = v[1:].strip()
    n = ""
    for ch in v:
        if ch.isdigit():
            n += ch
        else:
            break
    return int(n) if n else 0


def which(name):
    p = shutil.which(name)
    if p:
        return p
    fallback = "/usr/bin/" + name
    return fallback if os.path.isfile(fallback) and os.access(fallback, os.X_OK) else None


def write_marker():
    os.makedirs(abs_under_root(RUNTIME_ROOT), exist_ok=True)
    with open(abs_under_root(MARKER), "w", encoding="utf-8") as f:
        f.write("P_NAT_SETUP vnc runtime\n")


def want_encrypt(conf):
    return str(conf.get("vnc_encrypt", "0")).strip() in ("1", "true", "True", "yes")


def want_reverse(conf):
    v = str(conf.get("vnc_connect", "reverse")).strip().lower()
    if v in ("listen", "wait", "in", "0"):
        return False
    return True


def conf_impl(conf):
    v = str(conf.get("vnc_impl", "auto")).strip().lower()
    if v in ("x11vnc", "tigervnc", "tiger", "x0vncserver"):
        if v in ("tiger", "x0vncserver"):
            return "tigervnc"
        return v
    return "auto"


def resolve_impl(conf):
    want = conf_impl(conf)
    x11 = which("x11vnc")
    tiger = which("x0vncserver")
    reverse = want_reverse(conf)
    if reverse:
        if want == "tigervnc":
            print("TigerVNC 的 x0vncserver 不能反向连出。")
            print("请在高级设置改选 x11vnc，或把连接方式改成「等待连入」。")
            sys.exit(1)
        if not x11:
            print("反向连出需要 x11vnc。安装: sudo apt install x11vnc")
            print("或在 JRA「Linux 依赖」里勾选。")
            sys.exit(1)
        return "x11vnc", x11
    if want == "x11vnc":
        if not x11:
            print("未找到 x11vnc。安装: sudo apt install x11vnc")
            print("或在 JRA「Linux 依赖」里勾选。")
            sys.exit(1)
        return "x11vnc", x11
    if want == "tigervnc":
        if not tiger:
            print("未找到 x0vncserver。安装: sudo apt install tigervnc-scraping-server")
            sys.exit(1)
        return "tigervnc", tiger
    if x11:
        return "x11vnc", x11
    if tiger:
        return "tigervnc", tiger
    print("未找到 VNC 实现。:0 推荐 x11vnc：")
    print("  sudo apt install x11vnc")
    print("备选 TigerVNC 刮屏：")
    print("  sudo apt install tigervnc-scraping-server")
    sys.exit(1)


def store_x11_passwd(password):
    path = abs_under_root(PASSWD_FILE)
    x11 = which("x11vnc")
    if not x11:
        print("写密码文件需要 x11vnc")
        sys.exit(1)
    r = subprocess.run([x11, "-storepasswd", password, path])
    if r.returncode != 0 or not os.path.isfile(path):
        print("x11vnc -storepasswd 失败")
        sys.exit(1)
    os.chmod(path, 0o600)
    return path


def store_tiger_passwd(password):
    path = abs_under_root(PASSWD_FILE)
    vncpasswd = which("vncpasswd")
    if not vncpasswd:
        print("TigerVNC 设密码需要 vncpasswd（包 tigervnc-common 或 tigervnc-scraping-server）")
        sys.exit(1)
    p = subprocess.Popen(
        [vncpasswd, "-f"],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    out, err = p.communicate((password + "\n").encode("utf-8"))
    if p.returncode != 0:
        print("vncpasswd 失败: {}".format(err.decode("utf-8", "replace")))
        sys.exit(1)
    with open(path, "wb") as f:
        f.write(out)
    os.chmod(path, 0o600)
    return path


def ensure_ssl_pem():
    path = abs_under_root(SSL_PEM)
    if os.path.isfile(path):
        return path
    openssl = which("openssl")
    if not openssl:
        print("加密需要 openssl 生成证书，或在高级设置改选「不加密」。")
        sys.exit(1)
    cmd = [
        openssl,
        "req",
        "-new",
        "-x509",
        "-days",
        "3650",
        "-nodes",
        "-subj",
        "/CN=p-nat-vnc",
        "-out",
        path,
        "-keyout",
        path,
    ]
    r = subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
    if r.returncode != 0 or not os.path.isfile(path):
        print("生成 TLS 证书失败")
        if r.stderr:
            print(r.stderr.decode("utf-8", "replace"))
        sys.exit(1)
    os.chmod(path, 0o600)
    print("写入 TLS 证书: {}".format(path))
    return path


def list_our_pids():
    needle = abs_under_root(RUNTIME_ROOT)
    pids = []
    try:
        out = subprocess.check_output(["ps", "-eo", "pid,args"], text=True, errors="ignore")
    except Exception as e:
        print(e)
        return pids
    for line in out.splitlines():
        line = line.strip()
        if not line or needle not in line:
            continue
        if "x11vnc" not in line and "x0vncserver" not in line:
            continue
        if "deploy/vnc/install.py" in line:
            continue
        parts = line.split(None, 1)
        try:
            pids.append(int(parts[0]))
        except ValueError:
            pass
    return pids


def stop_ours():
    pids = list_our_pids()
    pid_path = abs_under_root(PID_FILE)
    if os.path.isfile(pid_path):
        try:
            with open(pid_path, "r", encoding="utf-8") as f:
                extra = int(f.read().strip() or "0")
            if extra and extra not in pids:
                pids.append(extra)
        except ValueError:
            pass
    if not pids:
        print("没有运行中的本项目 VNC 进程")
        return
    print("结束进程: {}".format(pids))
    for pid in pids:
        try:
            os.kill(pid, signal.SIGTERM)
        except ProcessLookupError:
            pass
        except PermissionError:
            print("无权结束 pid {}".format(pid))
    time.sleep(0.8)
    for pid in list_our_pids():
        try:
            os.kill(pid, signal.SIGKILL)
        except (ProcessLookupError, PermissionError):
            pass
    try:
        os.remove(pid_path)
    except OSError:
        pass


def uninstall():
    print("== 卸载 Linux VNC（隔离运行时） ==")
    stop_ours()
    runtime = abs_under_root(RUNTIME_ROOT)
    if os.path.isdir(runtime):
        shutil.rmtree(runtime, ignore_errors=True)
        print("已删除: {}".format(runtime))
    else:
        print("无需删除（不存在）: {}".format(runtime))
    print("未触碰 ~/.vnc")
    print("== 卸载完成 ==")


def spawn(cmd, log_path):
    os.makedirs(os.path.dirname(log_path), exist_ok=True)
    logf = open(log_path, "ab")
    logf.write(("+ " + " ".join(cmd) + "\n").encode("utf-8", "replace"))
    logf.flush()
    proc = subprocess.Popen(
        cmd,
        stdout=logf,
        stderr=subprocess.STDOUT,
        stdin=subprocess.DEVNULL,
        start_new_session=True,
        cwd=abs_under_root(RUNTIME_ROOT),
    )
    time.sleep(0.9)
    if proc.poll() is not None:
        logf.close()
        print("启动失败（进程已退出，code={}）。日志: {}".format(proc.returncode, log_path))
        try:
            with open(log_path, "r", encoding="utf-8", errors="replace") as f:
                tail = f.read()[-1200:]
            print(tail)
        except OSError:
            pass
        sys.exit(1)
    with open(abs_under_root(PID_FILE), "w", encoding="utf-8") as f:
        f.write(str(proc.pid) + "\n")
    print("已在后台运行 pid={}  日志 {}".format(proc.pid, log_path))
    return proc.pid


def build_x11vnc(binpath, conf, display, encrypt, password):
    host = str(conf.get("host", "")).strip()
    port = str(conf.get("port", "")).strip()
    log_path = abs_under_root(LOG_FILE)
    cmd = [
        binpath,
        "-display",
        display,
        "-forever",
        "-shared",
        "-repeat",
        "-rfbport",
        "5900",
        "-o",
        log_path,
    ]
    xa = os.environ.get("XAUTHORITY") or ""
    if xa and os.path.isfile(xa):
        cmd.extend(["-auth", xa])
    else:
        cmd.extend(["-auth", "guess"])
    if password:
        store_x11_passwd(password)
        cmd.extend(["-rfbauth", abs_under_root(PASSWD_FILE)])
    else:
        cmd.append("-nopw")
        print("未设 VNC 密码（vnc_passwd 为空）。局域网/VPN 才建议这样。")
    if encrypt:
        pem = ensure_ssl_pem()
        cmd.extend(["-ssl", pem])
        print("加密: TLS（x11vnc -ssl）。协助端 Viewer 需支持 TLS（TigerVNC Viewer 可以；TightVNC Viewer 往往不行）。")
    else:
        print("不加密: 普通 RFB。TightVNC / TigerVNC / Remmina 都能连。公网请走 VPN/FRP/SSH。")
    if want_reverse(conf):
        if not host:
            print("已选反向连出，但协助端地址为空。请在 VNC 高级设置填写 Viewer 监听地址。")
            sys.exit(1)
        dest = host if not port else "{}:{}".format(host, port)
        cmd.extend(["-connect", dest])
        print("连接: 反向连出 {}".format(dest))
        print("协助端需先开 Viewer 监听（TightVNC 常见 5500；以高级设置端口为准）。")
    else:
        print("连接: 等待连入 本机:5900")
    return cmd


def build_tigervnc(binpath, conf, display, encrypt, password):
    cmd = [
        binpath,
        "-display",
        display,
        "-rfbport",
        "5900",
    ]
    if password:
        store_tiger_passwd(password)
        cmd.extend(["-PasswordFile", abs_under_root(PASSWD_FILE)])
        if encrypt:
            cmd.extend(["-SecurityTypes", "TLSVnc,VeNCrypt"])
            print("加密: VeNCrypt/TLSVnc。协助端用 TigerVNC Viewer。")
        else:
            cmd.extend(["-SecurityTypes", "VncAuth"])
            print("不加密: VncAuth 密码。")
    else:
        if encrypt:
            cmd.extend(["-SecurityTypes", "TLSNone,VeNCrypt"])
            print("加密但无密码（TLSNone）。公网不建议。")
        else:
            cmd.extend(["-SecurityTypes", "None"])
            print("不加密且无密码。仅限本机/VPN。")
    if want_reverse(conf):
        print("TigerVNC x0vncserver 不能反向连出。请改选 x11vnc 或改成等待连入。")
        sys.exit(1)
    print("连接: 等待连入 本机:5900")
    return cmd


def start_vnc(conf):
    st = session_type()
    if st == "wayland":
        print("当前是 Wayland。x11vnc / x0vncserver 不能可靠共享整屏。")
        print("请用 Dayon，或登录 X11 会话后再加载 VNC。")
        sys.exit(1)
    dpy_n = parse_display_num(conf.get("vnc_display", "0"))
    if dpy_n > 0:
        print("本轮只做当前屏（:0）。虚拟桌面 :{} 稍后接 TigerVNC vncserver。".format(dpy_n))
        sys.exit(1)
    display = current_display()
    if not os.environ.get("DISPLAY"):
        print("未检测到 DISPLAY。可先: export DISPLAY=:0")
        sys.exit(1)
    write_marker()
    impl, binpath = resolve_impl(conf)
    encrypt = want_encrypt(conf)
    password = str(conf.get("vnc_passwd", "")).strip()
    print("== Linux VNC :0 当前屏 ==")
    print("实现: {}  ({})".format(impl, binpath))
    print("DISPLAY: {}".format(display))
    print("连接: {}".format("反向连出" if want_reverse(conf) else "等待连入"))
    print("加密: {}".format("是" if encrypt else "否"))
    if list_our_pids():
        print("已有本项目 VNC 在跑，先停再启")
        stop_ours()
    log_path = abs_under_root(LOG_FILE)
    if impl == "x11vnc":
        cmd = build_x11vnc(binpath, conf, display, encrypt, password)
    else:
        cmd = build_tigervnc(binpath, conf, display, encrypt, password)
    spawn(cmd, log_path)


def keep_only():
    conf = read_conf()
    write_marker()
    encrypt = want_encrypt(conf)
    password = str(conf.get("vnc_passwd", "")).strip()
    impl, binpath = resolve_impl(conf)
    if password:
        if impl == "x11vnc":
            store_x11_passwd(password)
        else:
            store_tiger_passwd(password)
    if encrypt and impl == "x11vnc":
        ensure_ssl_pem()
    print("已保留运行时: {}".format(abs_under_root(RUNTIME_ROOT)))
    print("下次: python3 deploy/vnc/install.py")


def main():
    ensure_linux()
    parser = argparse.ArgumentParser(
        description="Linux VNC :0 当前屏（x11vnc 推荐 / TigerVNC 可选）"
    )
    parser.add_argument("--uninstall", action="store_true", help="停进程并删除 .runtime/vnc")
    parser.add_argument("--keep", action="store_true", help="只准备密码/证书，不启动")
    parser.add_argument("--stop", action="store_true", help="只结束本项目 VNC 进程")
    args = parser.parse_args()
    if args.uninstall:
        uninstall()
        return
    if args.stop:
        stop_ours()
        return
    if args.keep:
        keep_only()
        return
    start_vnc(read_conf())


if __name__ == "__main__":
    main()
