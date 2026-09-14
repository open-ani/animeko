# 终端 TV 遥控器

没有实体遥控器时，用它驱动 Android TV variant（`me.him188.ani.tv*`）：终端里画一个遥控器面板，
键盘按键或鼠标点击都会通过 `adb shell input keyevent` 发到设备。

```bash
python3 tools/tv-remote/tv-remote.py                    # 自动选设备 + 自动认包
python3 tools/tv-remote/tv-remote.py -s <设备号>         # 多设备时指定
python3 tools/tv-remote/tv-remote.py -p me.him188.ani.tv # 驱动参考版而非本地 debug 包
python3 tools/tv-remote/tv-remote.py --selftest         # 只校验面板布局并打印预览, 不连设备
```

只依赖系统 Python 3 与 `adb`（`PATH` 里没有时回落到 `~/Library/Android/sdk/platform-tools/adb`）。
终端至少 31 行 × 72 列。

## 键位

| 键 | 作用 | keycode |
|---|---|---|
| `↑` `↓` `←` `→` | D-pad 方向 | 19/20/21/22 |
| `Enter` / `Space` | 确认 | 23 |
| `L` | **长按确认**（收藏菜单等长按语义） | 23 + `--longpress` |
| `Backspace` / `Esc` | 返回 | 4 |
| `h` / `m` | 主页 / 菜单 | 3 / 82 |
| `p` `n` `b` | 播放暂停 / 下一集 / 上一集 | 85 / 87 / 88 |
| `[` `]` | 快退 / 快进 | 89 / 90 |
| `+` `-` | 音量 | 24 / 25 |
| `w` | 唤醒屏幕（锁屏后遥控器无反应时先按它） | 224 |
| `s` | 截图并用系统预览打开 | — |
| `t` | 输入文本（`adb input text`，**仅 ASCII**，中文需用设备输入法） | — |
| `o` / `r` | 启动 / 重启应用 | — |
| `q` | 退出 | — |

鼠标：直接点面板上的按钮，效果与热键相同。

## 说明

- 每次按键在后台线程发送，面板不会卡住；按钮会闪一下，底部状态栏显示最近按键与耗时（`adb` 往返通常 50–250ms）。
- 面板只在有输入或状态变化时重绘，空闲时不刷屏。
- 包名自动探测顺序：本地 debug 包 `me.him188.ani.tv.debug2` → 参考版 `me.him188.ani.tv`。
