#!/usr/bin/python3
# -*- coding: utf-8 -*-
"""
抓取一组热键（X11 GrabKeyboard），打印一行 xdotool 风格组合键后退出。
用法：python3 tool/capture_hotkey.py
成功：stdout 一行如 ctrl+alt+Right，exit 0
取消/失败：stderr 说明，exit 非 0

依赖：python3-xlib（apt）或 pip install python-xlib
"""
from __future__ import print_function

import sys
import time

MOD_CTRL = 1 << 2
MOD_ALT = 1 << 3
MOD_SHIFT = 1 << 0
MOD_SUPER = 1 << 6

SPECIAL = {
    "Escape": "Escape",
    "Return": "Return",
    "space": "space",
    "Tab": "Tab",
    "BackSpace": "BackSpace",
    "Delete": "Delete",
    "Left": "Left",
    "Right": "Right",
    "Up": "Up",
    "Down": "Down",
    "Page_Up": "Page_Up",
    "Page_Down": "Page_Down",
    "Home": "Home",
    "End": "End",
    "Insert": "Insert",
}

MODIFIER_NAMES = frozenset(
    [
        "Shift_L",
        "Shift_R",
        "Control_L",
        "Control_R",
        "Alt_L",
        "Alt_R",
        "Meta_L",
        "Meta_R",
        "Super_L",
        "Super_R",
        "Hyper_L",
        "Hyper_R",
        "ISO_Level3_Shift",
        "Mode_switch",
        "Caps_Lock",
        "Num_Lock",
    ]
)


def _fail(msg):
    sys.stderr.write(msg + "\n")
    sys.exit(2)


def _key_label(dpy, keycode):
    from Xlib import XK

    keysym = dpy.keycode_to_keysym(keycode, 0)
    if keysym == 0:
        return None
    name = XK.keysym_to_string(keysym)
    if name:
        return name
    # 方向键等：扫 XK_* 常量
    for attr in dir(XK):
        if attr.startswith("XK_") and getattr(XK, attr) == keysym:
            return attr[3:]
    return None


def _to_xdo_key(label):
    if label in SPECIAL:
        return SPECIAL[label]
    if len(label) == 1:
        return label.lower()
    if label.startswith("KP_"):
        return label
    if label.startswith("F") and label[1:].isdigit():
        return label
    return label


def main():
    try:
        from Xlib import X, display
    except ImportError:
        _fail("缺少 python3-xlib。请先: python3 init/install.py  或  sudo apt install python3-xlib")

    if not sys.platform.startswith("linux"):
        _fail("仅支持 Linux/X11 抓取")

    d = display.Display()
    root = d.screen().root
    grab = root.grab_keyboard(True, X.GrabModeAsync, X.GrabModeAsync, X.CurrentTime)
    if grab != X.GrabSuccess:
        d.close()
        _fail("无法 GrabKeyboard（可能已有程序占用）")

    sys.stderr.write("已抓取键盘：请按下目标组合键（Esc 取消，20 秒超时）…\n")
    sys.stderr.flush()

    deadline = time.time() + 20.0
    result = None
    try:
        while time.time() < deadline and result is None:
            ev = d.next_event()
            if ev.type != X.KeyPress:
                continue
            label = _key_label(d, ev.detail)
            if not label:
                continue
            if label in MODIFIER_NAMES:
                continue
            if label == "Escape":
                _fail("已取消")
            parts = []
            state = ev.state
            if state & MOD_CTRL:
                parts.append("ctrl")
            if state & MOD_ALT:
                parts.append("alt")
            if state & MOD_SHIFT:
                parts.append("shift")
            if state & MOD_SUPER:
                parts.append("super")
            parts.append(_to_xdo_key(label))
            result = "+".join(parts)
    finally:
        try:
            d.ungrab_keyboard(X.CurrentTime)
            d.flush()
            d.close()
        except Exception:
            pass

    if not result:
        _fail("超时未捕获到按键")
    print(result)
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main() or 0)
    except SystemExit:
        raise
    except Exception as e:
        _fail(str(e))
