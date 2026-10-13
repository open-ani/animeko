#!/usr/bin/env bash
#
# 在 android-emulator-runner 启动模拟器之后、运行 instrumented test 之前执行.

set -u

# 系统错误对话框 ("应用无响应"、"应用已停止") 是盖在测试窗口之上的系统窗口:
# 它拿走窗口焦点, 之后注入给测试窗口的输入全部被丢弃, 此后依赖焦点或输入的 UI 测试全部失败.
# 关闭对话框后, 系统直接结束无响应的应用, 不再等待用户选择.
adb shell settings put global hide_error_dialogs 1

# runner 在开机完成后立即发送解锁按键. 此时 launcher 可能还没有获得焦点的窗口, 按键分发超时即触发 ANR.
# 上面的设置生效之前已经弹出的对话框, 随对应进程一起关闭.
anr_packages=$(adb shell dumpsys window windows | grep -o 'Application Not Responding: [A-Za-z0-9_.]*' | sed 's/.*: //' | sort -u)
for package in $anr_packages; do
  echo "Stopping $package, which is showing an ANR dialog"
  adb shell am force-stop "$package"
done

exit 0
