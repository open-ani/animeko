#!/usr/bin/env python3
# Copyright (C) 2024-2026 OpenAni and contributors.
#
# 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
# Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
#
# https://github.com/open-ani/ani/blob/main/LICENSE
"""
终端 Android TV 遥控器.

没有实体遥控器时用它驱动 TV variant: 键盘按键或鼠标点击面板上的按钮, 通过 adb 发送
keyevent 到设备/模拟器. 长按、文本输入、截图预览都在面板上.

    python3 tools/tv-remote/tv-remote.py            # 自动选设备, 自动认出已装的 TV 包
    python3 tools/tv-remote/tv-remote.py -s <设备号> -p me.him188.ani.tv.debug2

键位见面板底部; q 退出.
"""

from __future__ import annotations

import argparse
import curses
import shutil
import subprocess
import sys
import threading
import time
import unicodedata
from dataclasses import dataclass, field


def disp_width(text: str) -> int:
    """终端显示列宽: 东亚全宽字符占 2 列, 其余占 1 列."""
    return sum(2 if unicodedata.east_asian_width(ch) in ("W", "F") else 1 for ch in text)

# TV 包候选: 本地 debug 构建优先, 其次参考版
DEFAULT_PACKAGES = [
    ("me.him188.ani.tv.debug2", "me.him188.ani.tv.MainActivity"),
    ("me.him188.ani.tv", "me.him188.ani.android.activity.MainActivity"),
]

ADB = shutil.which("adb") or f"{__import__('os').path.expanduser('~')}/Library/Android/sdk/platform-tools/adb"


@dataclass
class Button:
    """面板上的一个按钮; row/col 为左上角, 宽高含边框."""

    label: str
    keycode: int | None
    row: int
    col: int
    width: int = 9
    height: int = 3
    hotkey: str = ""
    action: str = ""  # 非 keyevent 的特殊动作
    long_press: bool = False
    flash_until: float = field(default=0.0, compare=False)

    def hit(self, y: int, x: int) -> bool:
        return self.row <= y < self.row + self.height and self.col <= x < self.col + self.width


def build_buttons() -> list[Button]:
    """遥控器布局: D-pad 十字 + 返回/主页 + 媒体键 + 工具键."""
    b: list[Button] = []
    # D-pad (十字, 居中于 col 20)
    b.append(Button("▲", 19, row=3, col=20, hotkey="↑"))
    b.append(Button("◀", 21, row=6, col=11, hotkey="←"))
    b.append(Button("OK", 23, row=6, col=20, hotkey="⏎"))
    b.append(Button("▶", 22, row=6, col=29, hotkey="→"))
    b.append(Button("▼", 20, row=9, col=20, hotkey="↓"))
    # 长按确认 (TV 上常用于收藏菜单)
    b.append(Button("长按OK", 23, row=6, col=39, width=10, hotkey="L", long_press=True))
    # 导航
    b.append(Button("返回", 4, row=13, col=11, hotkey="⌫"))
    b.append(Button("主页", 3, row=13, col=29, hotkey="h"))
    b.append(Button("菜单", 82, row=13, col=20, hotkey="m"))
    # 媒体
    b.append(Button("上一集", 88, row=17, col=11, width=11, hotkey="b"))
    b.append(Button("播/停", 85, row=17, col=23, width=11, hotkey="p"))
    b.append(Button("下一集", 87, row=17, col=35, width=11, hotkey="n"))
    b.append(Button("快退", 89, row=17, col=47, width=9, hotkey="["))
    b.append(Button("快进", 90, row=17, col=57, width=9, hotkey="]"))
    # 音量 / 电源
    b.append(Button("音量-", 25, row=3, col=39, width=10, hotkey="-"))
    b.append(Button("音量+", 24, row=3, col=50, width=10, hotkey="+"))
    b.append(Button("唤醒", 224, row=9, col=39, width=10, hotkey="w"))
    # 工具
    b.append(Button("截图", None, row=21, col=11, width=10, hotkey="s", action="screenshot"))
    b.append(Button("输入文本", None, row=21, col=22, width=12, hotkey="t", action="text"))
    b.append(Button("启动应用", None, row=21, col=35, width=12, hotkey="o", action="launch"))
    b.append(Button("重启应用", None, row=21, col=48, width=12, hotkey="r", action="relaunch"))
    return b


class Remote:
    def __init__(self, serial: str | None, package: str | None, activity: str | None):
        self.serial = serial
        self.package = package
        self.activity = activity
        self.status = "就绪"
        self.last_key = ""
        self.last_ms = 0
        self.buttons = build_buttons()
        self.lock = threading.Lock()

    # ---- adb ----
    def adb(self, *args: str, capture: bool = False, timeout: float = 15) -> str:
        cmd = [ADB]
        if self.serial:
            cmd += ["-s", self.serial]
        cmd += list(args)
        if capture:
            out = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
            return out.stdout.strip()
        subprocess.run(cmd, capture_output=True, timeout=timeout)
        return ""

    def detect_device(self) -> bool:
        out = self.adb("devices", capture=True)
        devices = [
            line.split()[0]
            for line in out.splitlines()[1:]
            if line.strip() and line.split()[-1] == "device"
        ]
        if not devices:
            return False
        if self.serial not in devices:
            self.serial = devices[0]
        return True

    def detect_package(self) -> None:
        if self.package:
            return
        installed = self.adb("shell", "pm", "list", "packages", capture=True)
        for pkg, act in DEFAULT_PACKAGES:
            if f"package:{pkg}" in installed:
                self.package, self.activity = pkg, act
                return
        self.package, self.activity = DEFAULT_PACKAGES[0]

    # ---- 动作 (后台线程, 不卡 UI) ----
    def send_key(self, keycode: int, long_press: bool = False, label: str = "") -> None:
        def run() -> None:
            start = time.time()
            args = ["shell", "input", "keyevent"]
            if long_press:
                args.append("--longpress")
            args.append(str(keycode))
            self.adb(*args)
            with self.lock:
                self.last_key = f"{label or keycode}{' (长按)' if long_press else ''}"
                self.last_ms = int((time.time() - start) * 1000)

        threading.Thread(target=run, daemon=True).start()

    def screenshot(self) -> None:
        def run() -> None:
            path = f"/tmp/tv-remote-{int(time.time())}.png"
            cmd = [ADB]
            if self.serial:
                cmd += ["-s", self.serial]
            cmd += ["exec-out", "screencap", "-p"]
            with open(path, "wb") as f:
                subprocess.run(cmd, stdout=f, timeout=30)
            subprocess.run(["open", path], capture_output=True)
            with self.lock:
                self.status = f"截图 → {path}"

        threading.Thread(target=run, daemon=True).start()

    def send_text(self, text: str) -> None:
        def run() -> None:
            # adb input text 不支持空格与中文: 空格转 %s, 非 ASCII 直接提示
            if any(ord(c) > 127 for c in text):
                with self.lock:
                    self.status = "adb 无法输入非 ASCII 字符 (中文请用设备输入法)"
                return
            self.adb("shell", "input", "text", text.replace(" ", "%s"))
            with self.lock:
                self.status = f"已输入: {text}"

        threading.Thread(target=run, daemon=True).start()

    def launch(self, force_stop: bool = False) -> None:
        def run() -> None:
            args = ["shell", "am", "start"]
            if force_stop:
                args.append("-S")
            args += ["-n", f"{self.package}/{self.activity}"]
            self.adb(*args)
            with self.lock:
                self.status = ("重启" if force_stop else "启动") + f" {self.package}"

        threading.Thread(target=run, daemon=True).start()

    def trigger(self, btn: Button) -> None:
        btn.flash_until = time.time() + 0.16
        if btn.action == "screenshot":
            self.screenshot()
        elif btn.action == "launch":
            self.launch(force_stop=False)
        elif btn.action == "relaunch":
            self.launch(force_stop=True)
        elif btn.action == "text":
            pass  # 由主循环处理 (需要弹输入行)
        elif btn.keycode is not None:
            self.send_key(btn.keycode, btn.long_press, btn.label)


HELP_LINES = [
    "方向键/⏎ = D-pad·确认   ⌫/Esc = 返回   L = 长按确认   h 主页   m 菜单",
    "p 播/停  n 下一集  b 上一集  [ ] 快退/快进   +/- 音量   w 唤醒屏幕",
    "s 截图并预览   t 输入文本(仅 ASCII)   o 启动应用   r 重启应用   q 退出",
]


def draw(stdscr, remote: Remote) -> None:
    stdscr.erase()
    h, w = stdscr.getmaxyx()
    now = time.time()

    title = " Animeko TV 遥控器 "
    stdscr.attron(curses.A_BOLD)
    stdscr.addstr(0, 2, title[: max(0, w - 4)])
    stdscr.attroff(curses.A_BOLD)

    with remote.lock:
        dev = remote.serial or "(无设备)"
        pkg = remote.package or "-"
        status = remote.status
        last = remote.last_key
        ms = remote.last_ms

    info = f"设备 {dev}   应用 {pkg}"
    if len(info) < w - 4:
        stdscr.addstr(1, 2, info, curses.A_DIM)

    for btn in remote.buttons:
        if btn.row + btn.height >= h or btn.col + btn.width >= w:
            continue
        flashing = now < btn.flash_until
        attr = curses.A_REVERSE if flashing else curses.A_NORMAL
        # 边框
        stdscr.addstr(btn.row, btn.col, "┌" + "─" * (btn.width - 2) + "┐", attr)
        stdscr.addstr(btn.row + 1, btn.col, "│" + " " * (btn.width - 2) + "│", attr)
        stdscr.addstr(btn.row + 2, btn.col, "└" + "─" * (btn.width - 2) + "┘", attr)
        # 标签居中 (按显示宽度粗略计: 非 ASCII 记 2 列)
        disp = disp_width(btn.label)
        pad = max(0, (btn.width - 2 - disp) // 2)
        stdscr.addstr(btn.row + 1, btn.col + 1 + pad, btn.label, attr | curses.A_BOLD)
        # 热键角标
        if btn.hotkey:
            hk = btn.hotkey
            hk_disp = disp_width(hk)
            pos = btn.col + btn.width - 1 - hk_disp
            if pos > btn.col:
                stdscr.addstr(btn.row + 2, pos, hk, curses.A_DIM)

    footer = h - len(HELP_LINES) - 2
    if footer > 0:
        line = f"最近: {last or '-'}"
        if last and ms:
            line += f"  ({ms}ms)"
        line += f"   {status}"
        stdscr.addstr(footer, 2, line[: max(0, w - 4)], curses.A_BOLD)
        for i, text in enumerate(HELP_LINES):
            row = footer + 1 + i
            if row < h - 1:
                stdscr.addstr(row, 2, text[: max(0, w - 4)], curses.A_DIM)

    stdscr.refresh()


def prompt_text(stdscr, remote: Remote) -> str | None:
    h, w = stdscr.getmaxyx()
    row = h - 1
    stdscr.move(row, 0)
    stdscr.clrtoeol()
    stdscr.addstr(row, 2, "输入文本 (回车发送, 空则取消): ")
    curses.echo()
    curses.curs_set(1)
    try:
        raw = stdscr.getstr(row, 34, 120)
    finally:
        curses.noecho()
        curses.curs_set(0)
    return raw.decode("utf-8", errors="ignore").strip() or None


KEY_MAP: dict[int | str, tuple[int, bool, str]] = {
    curses.KEY_UP: (19, False, "▲"),
    curses.KEY_DOWN: (20, False, "▼"),
    curses.KEY_LEFT: (21, False, "◀"),
    curses.KEY_RIGHT: (22, False, "▶"),
    "\n": (23, False, "OK"),
    "\r": (23, False, "OK"),
    " ": (23, False, "OK"),
    "L": (23, True, "OK"),
    "l": (23, True, "OK"),
    "\x7f": (4, False, "返回"),
    curses.KEY_BACKSPACE: (4, False, "返回"),
    "\x1b": (4, False, "返回"),
    "h": (3, False, "主页"),
    "m": (82, False, "菜单"),
    "p": (85, False, "播/停"),
    "n": (87, False, "下一集"),
    "b": (88, False, "上一集"),
    "[": (89, False, "快退"),
    "]": (90, False, "快进"),
    "+": (24, False, "音量+"),
    "=": (24, False, "音量+"),
    "-": (25, False, "音量-"),
    "w": (224, False, "唤醒"),
}


def find_button(remote: Remote, keycode: int, long_press: bool) -> Button | None:
    for btn in remote.buttons:
        if btn.keycode == keycode and btn.long_press == long_press:
            return btn
    return None


def main(stdscr, remote: Remote) -> None:
    curses.curs_set(0)
    stdscr.keypad(True)
    stdscr.timeout(120)  # get_wch 最多阻塞 120ms; 不用 nodelay+sleep, 避免空转刷屏
    curses.mousemask(curses.BUTTON1_PRESSED | curses.BUTTON1_RELEASED | curses.BUTTON1_CLICKED)

    try:
        dirty = True
        last_snapshot: tuple = ()
        while True:
            # 仅在有变化时重绘: 按键反馈闪烁中 / 状态更新 / 尺寸变化
            flashing = any(time.time() < b.flash_until for b in remote.buttons)
            with remote.lock:
                snapshot = (remote.status, remote.last_key, remote.last_ms, remote.serial, remote.package)
            if dirty or flashing or snapshot != last_snapshot:
                draw(stdscr, remote)
                last_snapshot = snapshot
                dirty = False

            try:
                ch = stdscr.get_wch()
            except curses.error:
                continue  # 超时无输入, 回到循环顶部按需重绘
            dirty = True

            if ch == curses.KEY_MOUSE:
                try:
                    _, mx, my, _, state = curses.getmouse()
                except curses.error:
                    continue
                if state & (curses.BUTTON1_PRESSED | curses.BUTTON1_CLICKED):
                    for btn in remote.buttons:
                        if btn.hit(my, mx):
                            remote.trigger(btn)
                            if btn.action == "text":
                                text = prompt_text(stdscr, remote)
                                if text:
                                    remote.send_text(text)
                            break
                continue

            if ch in ("q", "Q"):
                break
            if ch in ("s", "S"):
                btn = next((b for b in remote.buttons if b.action == "screenshot"), None)
                if btn:
                    remote.trigger(btn)
                continue
            if ch in ("t", "T"):
                btn = next((b for b in remote.buttons if b.action == "text"), None)
                if btn:
                    btn.flash_until = time.time() + 0.16
                text = prompt_text(stdscr, remote)
                if text:
                    remote.send_text(text)
                continue
            if ch in ("o", "O"):
                remote.launch(force_stop=False)
                continue
            if ch in ("r", "R"):
                remote.launch(force_stop=True)
                continue

            mapped = KEY_MAP.get(ch)
            if mapped:
                keycode, long_press, label = mapped
                btn = find_button(remote, keycode, long_press)
                if btn:
                    btn.flash_until = time.time() + 0.16
                remote.send_key(keycode, long_press, label)
    finally:
        curses.mousemask(0)


def selftest() -> int:
    """不进 curses 的自检: 校验布局并按同一套坐标打印 ASCII 预览."""
    buttons = build_buttons()
    rows = max(b.row + b.height for b in buttons) + 1
    cols = max(b.col + b.width for b in buttons) + 2
    canvas = [[" "] * cols for _ in range(rows)]

    problems: list[str] = []
    for i, a in enumerate(buttons):
        for b in buttons[i + 1 :]:
            if not (
                a.row + a.height <= b.row
                or b.row + b.height <= a.row
                or a.col + a.width <= b.col
                or b.col + b.width <= a.col
            ):
                problems.append(f"按钮重叠: {a.label} / {b.label}")
    hotkeys = [b.hotkey for b in buttons if b.hotkey]
    if len(hotkeys) != len(set(hotkeys)):
        problems.append(f"热键冲突: {sorted(hotkeys)}")
    for b in buttons:
        disp = disp_width(b.label)
        if disp > b.width - 2:
            problems.append(f"标签超出按钮宽度: {b.label} ({disp} > {b.width - 2})")

    def put(r: int, c: int, text: str) -> None:
        # 终端按显示宽度排版: CJK 占两列, 预览也照此占两个槽位, 否则边框会看起来是歪的
        col = c
        for ch in text:
            if not (0 <= r < rows and 0 <= col < cols):
                break
            canvas[r][col] = ch
            col += 1
            if disp_width(ch) == 2:  # 全宽字符占两个槽位, 第二格标记为跳过
                if 0 <= col < cols:
                    canvas[r][col] = None
                col += 1

    for b in buttons:
        put(b.row, b.col, "┌" + "─" * (b.width - 2) + "┐")
        put(b.row + 1, b.col, "│" + " " * (b.width - 2) + "│")
        put(b.row + 2, b.col, "└" + "─" * (b.width - 2) + "┘")
        disp = disp_width(b.label)
        put(b.row + 1, b.col + 1 + max(0, (b.width - 2 - disp) // 2), b.label)

    print("=== 面板布局预览 ===")
    for row in canvas:
        line = "".join(c for c in row if c is not None).rstrip()
        if line:
            print(line)
    print()
    for line in HELP_LINES:
        print("  " + line)
    print()
    print(f"按钮 {len(buttons)} 个, 最小终端尺寸约 {rows + len(HELP_LINES) + 3} 行 × {cols + 4} 列")
    if problems:
        print("\n自检失败:")
        for p in problems:
            print("  - " + p)
        return 1
    print("自检通过: 无重叠 / 热键唯一 / 标签不溢出")
    return 0


def cli() -> int:
    parser = argparse.ArgumentParser(description="终端 Android TV 遥控器 (adb keyevent)")
    parser.add_argument("-s", "--serial", help="adb 设备号; 省略则自动选第一台")
    parser.add_argument("-p", "--package", help="应用包名; 省略则自动探测已安装的 TV 包")
    parser.add_argument("-a", "--activity", help="启动 Activity (配合 --package)")
    parser.add_argument("--selftest", action="store_true", help="校验面板布局并打印预览, 不连设备")
    args = parser.parse_args()

    if args.selftest:
        return selftest()

    remote = Remote(args.serial, args.package, args.activity)
    if not remote.detect_device():
        print("找不到已连接的 adb 设备. 先插上手机/开模拟器, 再运行本工具.", file=sys.stderr)
        return 1
    remote.detect_package()
    if args.package and not args.activity:
        remote.activity = next(
            (act for pkg, act in DEFAULT_PACKAGES if pkg == args.package),
            f"{args.package}.MainActivity",
        )

    curses.wrapper(main, remote)
    return 0


if __name__ == "__main__":
    sys.exit(cli())
