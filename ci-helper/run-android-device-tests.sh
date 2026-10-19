#!/usr/bin/env bash
#
# 在 android-emulator-runner 启动的模拟器上运行 instrumented test, 并把整机 logcat 保存到 <logcat 文件>.
#
# 用法: run-android-device-tests.sh <logcat 文件> <Gradle 任务与选项...>
#
# API 30 的 ART 偶尔在测试进程的 GC 线程 (HeapTaskDaemon) 中段错误, 崩溃位置在 libart.so 内, 与被测代码无关.
# 失败的任务全部由这种崩溃造成时, 在同一模拟器上重跑这些任务一次.
# 其他失败, 包括被测代码造成的崩溃, 不重试, 以免掩盖不稳定的测试.

set -u

logcat_file=$1
shift

gradle_output=$(mktemp)

run_gradle() {
  ./gradlew "$@" 2>&1 | tee "$gradle_output"
  return "${PIPESTATUS[0]}"
}

# 测试进程在启动阶段崩溃时不会留下按测试拆分的 logcat, 整机 logcat 是唯一线索.
save_logcat() {
  adb logcat -d > "$logcat_file" || true
}

# 输出 crash 缓冲区中本应用进程的 ART 崩溃次数. 有任何其他崩溃 (Java 未捕获异常, 或 libart.so 之外的 native 崩溃) 时输出 0.
count_art_crashes() {
  adb logcat -d -b crash | awk '
    # tombstone 中进程行之后的第一个 #00 帧是崩溃位置.
    / DEBUG *: .*>>> me\.him188\.ani[^ ]* <<</ { in_tombstone = 1; next }
    in_tombstone && / DEBUG *: +#00 pc / {
      if (/\/libart\.so /) art++; else other++
      in_tombstone = 0
    }
    / AndroidRuntime: Process: me\.him188\.ani/ { other++ }
    END { print (other ? 0 : art + 0) }
  '
}

# 输出上一次 Gradle 运行中失败的任务, 仅当它们全部因 ART 崩溃而失败.
tasks_failed_by_art_crash() {
  local failed_count crashed_count
  failed_count=$(grep -cE '^> Task [^ ]+ FAILED$' "$gradle_output")
  [ "$failed_count" -gt 0 ] || return
  # 崩溃信息在 Gradle 输出中不一定紧跟所属任务, 只能比较数量.
  crashed_count=$(grep -c 'Instrumentation run failed due to Process crashed' "$gradle_output")
  [ "$crashed_count" -eq "$failed_count" ] || return
  [ "$(count_art_crashes)" -ge "$failed_count" ] || return
  sed -nE 's/^> Task ([^ ]+) FAILED$/\1/p' "$gradle_output"
}

run_gradle "$@"
status=$?
save_logcat

if [ "$status" -ne 0 ]; then
  retry_tasks=$(tasks_failed_by_art_crash)
  if [ -n "$retry_tasks" ]; then
    echo "::warning::ART crashed in the test process; retrying" $retry_tasks
    # 重跑时只换掉要运行的任务, 选项 (包括 -x 及其参数) 不变.
    options=()
    previous=
    for arg in "$@"; do
      if [[ $arg == -* || $previous == -x ]]; then
        options+=("$arg")
      fi
      previous=$arg
    done
    run_gradle $retry_tasks "${options[@]}"
    status=$?
    save_logcat
  fi
fi

exit "$status"
