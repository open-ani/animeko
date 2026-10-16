#!/usr/bin/env python3
#  Copyright (C) 2024-2026 OpenAni and contributors.
#
#  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
#  Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
#
#  https://github.com/open-ani/ani/blob/main/LICENSE

"""datasource-test-mcp 发行包的冒烟测试: 服务能启动, mpv 能真实播放, CEF 能从播放页拦截视频 URL.

在发行包根目录运行: python smoke-test.py

只访问本机: 测试视频和播放页由脚本自带的 HTTP 服务提供, 不依赖外网.
"""

import http.server
import io
import json
import os
import pathlib
import signal
import socket
import subprocess
import sys
import tempfile
import threading
import time
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parent
IS_WINDOWS = os.name == "nt"
LAUNCHER = ROOT / "bin" / ("datasource-test-mcp.bat" if IS_WINDOWS else "datasource-test-mcp")
READY_TIMEOUT_S = 180

# 3 秒 64x48 的 H.264 视频. mpv 自带的 libav 是只含常见流媒体格式的精简构建, 测试视频要用它支持的编码:
# ffmpeg -f lavfi -i color=c=gray:s=64x48:r=10:d=3 -c:v libx264 -profile:v baseline -pix_fmt yuv420p \
#   -movflags +faststart -an -map_metadata -1 -fflags +bitexact smoke-test.mp4
VIDEO = ROOT / "smoke-test.mp4"
WIDTH, HEIGHT = 64, 48


def serve_files(files):
    """files: path -> (body, content type). 返回已在后台运行的 HTTP 服务."""

    class Handler(http.server.BaseHTTPRequestHandler):
        def do_GET(self):
            body, content_type = files.get(self.path.split("?")[0], (None, None))
            if body is None:
                self.send_error(404)
                return
            try:
                self.send_response(200)
                self.send_header("Content-Type", content_type)
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)
            except ConnectionError:
                pass  # 播放器播够秒数就断开

        def log_message(self, format, *args):
            pass

    class Server(http.server.ThreadingHTTPServer):
        def handle_error(self, request, client_address):
            if not isinstance(sys.exc_info()[1], ConnectionError):  # 播放器断开连接不算错误
                super().handle_error(request, client_address)

    server = Server(("127.0.0.1", 0), Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return server


def collapse_stack_frames(lines, keep=3):
    """异常栈只保留前几帧, 免得一个深栈占满日志尾部, 挤掉异常本身."""
    result, frames = [], 0
    for line in lines:
        if line.lstrip().startswith("at ") and line[:1].isspace():
            frames += 1
            if frames <= keep:
                result.append(line)
            elif frames == keep + 1:
                result.append("\t...")
        else:
            frames = 0
            result.append(line)
    return result


def free_port():
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


class McpServer:
    def __init__(self, log_file):
        port = free_port()
        self.url = f"http://127.0.0.1:{port}/mcp"
        # 独立进程组, 结束时连同 CEF 子进程一起清理
        self.proc = subprocess.Popen(
            [str(LAUNCHER), "--host", "127.0.0.1", "--port", str(port)],
            stdout=log_file, stderr=subprocess.STDOUT,
            start_new_session=not IS_WINDOWS,
            creationflags=getattr(subprocess, "CREATE_NEW_PROCESS_GROUP", 0),
        )
        self.next_id = 0

    def post(self, method, params=None, timeout_s=60):
        self.next_id += 1
        message = {"jsonrpc": "2.0", "id": self.next_id, "method": method}
        if params is not None:
            message["params"] = params
        request = urllib.request.Request(
            self.url, data=json.dumps(message).encode(),
            headers={"Content-Type": "application/json"}, method="POST",
        )
        with urllib.request.urlopen(request, timeout=timeout_s) as response:
            return json.loads(response.read())

    def wait_ready(self):
        deadline = time.time() + READY_TIMEOUT_S
        while True:
            if self.proc.poll() is not None:
                raise RuntimeError(f"服务启动后立即退出 (exit={self.proc.returncode})")
            try:
                self.post("initialize", timeout_s=5)
                return
            except OSError:
                if time.time() > deadline:
                    raise RuntimeError(f"服务 {READY_TIMEOUT_S}s 内未就绪")
                time.sleep(0.5)

    def tool(self, name, arguments, timeout_s):
        response = self.post("tools/call", {"name": name, "arguments": arguments}, timeout_s)
        content = (response.get("result") or {}).get("structuredContent")
        if content is None:
            raise RuntimeError(f"{name} 调用失败: {json.dumps(response, ensure_ascii=False)[:500]}")
        return content

    def stop(self):
        if self.proc.poll() is not None:
            return
        if IS_WINDOWS:
            subprocess.run(["taskkill", "/T", "/F", "/PID", str(self.proc.pid)], capture_output=True)
        else:
            os.killpg(self.proc.pid, signal.SIGKILL)
        self.proc.wait()


def check_tools(mcp, base):
    names = {tool["name"] for tool in mcp.post("tools/list")["result"]["tools"]}
    missing = {"probe_video", "detect_hls_ads", "selector_resolve_episode", "selector_run_step"} - names
    return not missing, f"共 {len(names)} 个工具" + (f", 缺少 {sorted(missing)}" if missing else "")


def check_playback(mcp, base):
    with tempfile.TemporaryDirectory() as frames_dir:
        result = mcp.tool("probe_video", {
            "videoUrl": f"{base}/smoke-test.mp4",
            "showWindow": False,
            "playSeconds": 1,
            "playTimeoutMillis": 30_000,
            "detectAds": False,
            "captureFramesDir": frames_dir,
            "captureAtSeconds": [0],
        }, timeout_s=120)
        frames = [frame for frame in result.get("capturedFrames", []) if pathlib.Path(frame["path"]).is_file()]
    analysis = result.get("mediaAnalysis") or {}
    playback = analysis.get("playback") or {}
    video = analysis.get("video") or {}
    ok = bool(analysis.get("available") and playback.get("ran") and playback.get("ok")) \
        and (video.get("width"), video.get("height")) == (WIDTH, HEIGHT) and bool(frames)
    errors = analysis.get("errors", []) + playback.get("errors", [])
    detail = f"{analysis.get('tool', 'mpv 不可用')}, {video.get('width')}x{video.get('height')}, " \
             f"起播 {playback.get('timeToPlayingMillis')}ms, 截图 {len(frames)} 张"
    return ok, detail + (f", 错误: {'; '.join(errors)}" if errors else "")


def check_webview(mcp, base):
    result = mcp.tool("selector_run_step", {
        "step": "extractVideo",
        "url": f"{base}/play.html",
        "config": {
            "searchUrl": f"{base}/search?q={{keyword}}",
            "matchVideo": {"matchVideoUrl": r"smoke-test\.mp4$"},
        },
        "probeResolvedVideo": False,
    }, timeout_s=240)
    resolved = ((result.get("details") or {}).get("resolvedVideo") or {}).get("url", "")
    ok = bool(result.get("ok")) and resolved == f"{base}/smoke-test.mp4"
    errors = result.get("errors") or []
    return ok, result.get("summary", "") + (f", 错误: {'; '.join(errors)}" if errors else "")


def main():
    if isinstance(sys.stdout, io.TextIOWrapper):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    if not LAUNCHER.exists():
        sys.exit(f"找不到启动脚本: {LAUNCHER}")
    print(f"自带 JBR: {'是' if (ROOT / 'jbr').is_dir() else '否, 使用 JAVA_HOME 或 PATH 中的 java'}")

    page = b'<!doctype html><video src="smoke-test.mp4" autoplay muted></video>'
    http_server = serve_files({
        "/smoke-test.mp4": (VIDEO.read_bytes(), "video/mp4"),
        "/play.html": (page, "text/html; charset=utf-8"),
    })
    base = f"http://127.0.0.1:{http_server.server_address[1]}"

    log_path = pathlib.Path(tempfile.gettempdir()) / f"datasource-test-mcp-smoke-{os.getpid()}.log"
    failed = []
    with open(log_path, "wb") as log_file:
        mcp = McpServer(log_file)
        try:
            mcp.wait_ready()
            print("✓ 服务启动")
            for name, check in [("工具列表", check_tools), ("mpv 实播", check_playback), ("CEF 解析播放页", check_webview)]:
                try:
                    ok, detail = check(mcp, base)
                except Exception as e:
                    ok, detail = False, f"{type(e).__name__}: {e}"
                print(f"{'✓' if ok else '✗'} {name}: {detail}")
                if not ok:
                    failed.append(name)
        except RuntimeError as e:
            print(f"✗ 服务启动: {e}")
            failed.append("服务启动")
        finally:
            mcp.stop()
            http_server.shutdown()

    if failed:
        print(f"\n服务日志 ({log_path}) 最后 100 行, 异常栈只保留前 3 帧:")
        lines = log_path.read_bytes().decode("utf-8", errors="replace").splitlines()
        print("\n".join(collapse_stack_frames(lines)[-100:]))
        sys.exit(1)
    log_path.unlink(missing_ok=True)


if __name__ == "__main__":
    main()
