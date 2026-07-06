#!/usr/bin/env bash
# Android UI verification toolbox for the Animeko client.
# Wraps emulator + adb so an agent can drive the app: launch, screenshot, tap, swipe, inspect UI.
# Run `droid.sh help` for the command list.
set -euo pipefail

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
PKG="${ANI_PKG:-me.him188.ani.debug}"
AVD="${ANI_AVD:-Pixel_8_Pro_API_35}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
OUT_DIR="${ANI_UI_OUT_DIR:-${TMPDIR:-/tmp}/animeko-ui-verify}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

cmd="${1:-help}"
shift || true

dump_ui() { # $1 = output file; retries because uiautomator returns null root while the UI is busy
  local attempt
  for attempt in 1 2 3 4 5; do
    adb shell rm -f /sdcard/ui-dump.xml >/dev/null 2>&1 || true
    adb shell uiautomator dump /sdcard/ui-dump.xml >/dev/null 2>&1 || true
    adb exec-out cat /sdcard/ui-dump.xml > "$1" 2>/dev/null || true
    if [[ -s "$1" ]] && head -c 1 "$1" | grep -q '<'; then
      adb shell rm -f /sdcard/ui-dump.xml >/dev/null 2>&1 || true
      return 0
    fi
    sleep 2
  done
  echo "uiautomator dump kept failing (UI busy or no active window)" >&2
  return 1
}

get_density() { # override density wins over physical
  adb shell wm density | tr -d '\r' | awk -F': ' '/Override/{o=$2} /Physical/{p=$2} END{print (o ? o : p)}'
}

record_size() { # half the display, 16-aligned: the emulator encoder rejects full-size video (err=-22)
  adb shell wm size | tr -d '\r' | awk -F': ' '/Override/{o=$2} /Physical/{p=$2} END{
    s=(o ? o : p); split(s, a, "x");
    w=int(a[1]/32)*16; h=int(a[2]/32)*16;
    if (w<16) w=16; if (h<16) h=16;
    printf "%dx%d", w, h}'
}

case "$cmd" in
  emulator)
    emu="$SDK/emulator/emulator"
    [[ -x "$emu" ]] || { echo "emulator binary not found at $emu" >&2; exit 1; }
    if adb devices | awk 'NR>1 && $2=="device"{f=1} END{exit f?0:1}'; then
      echo "Reusing already-connected device:"
      adb devices
    else
      args=(-avd "$AVD" -no-audio -no-boot-anim -netdelay none -netspeed full)
      if [[ "${HEADED:-0}" != "1" ]]; then
        args+=(-no-window -gpu swiftshader_indirect)
      fi
      echo "Starting AVD $AVD (HEADED=${HEADED:-0}), log: /tmp/animeko-emulator-$AVD.log"
      "$emu" "${args[@]}" >"/tmp/animeko-emulator-$AVD.log" 2>&1 &
      disown
    fi
    adb wait-for-device
    deadline=$((SECONDS + 300))
    until [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do
      (( SECONDS < deadline )) || { echo "Timed out waiting for boot; see /tmp/animeko-emulator-$AVD.log" >&2; exit 1; }
      sleep 2
    done
    adb shell input keyevent 82 >/dev/null 2>&1 || true
    for s in window_animation_scale transition_animation_scale animator_duration_scale; do
      adb shell settings put global "$s" 0 >/dev/null 2>&1 || true
    done
    echo "Emulator ready: Android $(adb shell getprop ro.build.version.release | tr -d '\r'), $(adb shell wm size | tr -d '\r')"
    ;;

  kill-emulator)
    adb emu kill
    ;;

  install)
    apk="${1:-}"
    if [[ -z "$apk" ]]; then
      dir="$REPO_ROOT/app/android/build/outputs/apk/default/debug"
      apk="$(ls -t "$dir/android-default-universal-debug.apk" "$dir/android-default-arm64-v8a-debug.apk" 2>/dev/null | head -1 || true)"
    fi
    [[ -n "$apk" && -f "$apk" ]] || {
      echo "No debug APK found. Build one first:" >&2
      echo "  ./gradlew :app:android:assembleDefaultDebug -Pani.android.abis=arm64-v8a" >&2
      exit 1
    }
    echo "Installing $apk"
    adb install -r -t "$apk"
    ;;

  uninstall)
    adb uninstall "$PKG"
    ;;

  launch)
    adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
    echo "Launched $PKG"
    ;;

  relaunch)
    adb shell am force-stop "$PKG"
    sleep 1
    adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
    echo "Relaunched $PKG"
    ;;

  stop-app)
    adb shell am force-stop "$PKG"
    ;;

  clear)
    adb shell pm clear "$PKG"
    echo "Cleared app data of $PKG (next launch shows first-run onboarding)"
    ;;

  screenshot|shot)
    out="${1:-$OUT_DIR/shot-$(date +%Y%m%d-%H%M%S).png}"
    mkdir -p "$(dirname "$out")"
    adb exec-out screencap -p > "$out"
    echo "$out"
    ;;

  ui)
    out="${1:-$OUT_DIR/ui-$(date +%Y%m%d-%H%M%S).xml}"
    mkdir -p "$(dirname "$out")"
    dump_ui "$out"
    echo "$out"
    ;;

  find)
    q="${1:?usage: droid.sh find <substring of text/content-desc/resource-id>}"
    mkdir -p "$OUT_DIR"
    tmp="$OUT_DIR/ui-find.xml"
    dump_ui "$tmp"
    python3 "$SCRIPT_DIR/ui_tree.py" "$tmp" --density "$(get_density)" --find "$q"
    ;;

  tree)
    mkdir -p "$OUT_DIR"
    tmp="$OUT_DIR/ui-tree.xml"
    dump_ui "$tmp"
    python3 "$SCRIPT_DIR/ui_tree.py" "$tmp" --density "$(get_density)" "$@"
    ;;

  tap)
    adb shell input tap "${1:?usage: droid.sh tap <x> <y>}" "${2:?y}"
    echo "tapped ($1,$2)"
    ;;

  swipe)
    adb shell input swipe "${1:?x1}" "${2:?y1}" "${3:?x2}" "${4:?y2}" "${5:-300}"
    echo "swiped ($1,$2)->($3,$4) in ${5:-300}ms"
    ;;

  text)
    t="${1:?usage: droid.sh text <ascii-text>}"
    adb shell input text "$(printf %s "$t" | sed 's/ /%s/g')"
    echo "typed: $t"
    ;;

  key)
    adb shell input keyevent "${1:?usage: droid.sh key <keycode>}"
    ;;

  back)
    adb shell input keyevent 4
    ;;

  home)
    adb shell input keyevent 3
    ;;

  display)
    preset="${1:-phone}"
    case "$preset" in
      wide)   adb shell wm size 2560x1600; adb shell wm density 320 ;;
      tablet) adb shell wm size 1920x1200; adb shell wm density 240 ;;
      phone)  adb shell wm size 1344x2992; adb shell wm density 480 ;;
      reset)  adb shell wm size reset;     adb shell wm density reset ;;
      *) echo "usage: droid.sh display wide|tablet|phone|reset" >&2; exit 2 ;;
    esac
    echo "Display now: $(adb shell wm size | tr -d '\r'), $(adb shell wm density | tr -d '\r')"
    ;;

  logcat)
    pid="$(adb shell pidof -s "$PKG" 2>/dev/null | tr -d '\r' || true)"
    if [[ -n "$pid" ]]; then
      adb logcat -d --pid="$pid" | tail -n "${1:-100}"
    else
      echo "app process not running; recent crash buffer:" >&2
      adb logcat -d -b crash | tail -n "${1:-100}"
    fi
    ;;

  crashes)
    adb logcat -d -b crash | tail -n "${1:-120}"
    ;;

  record)
    secs="${1:?usage: droid.sh record <seconds> [out.mp4]}"
    out="${2:-$OUT_DIR/rec-$(date +%Y%m%d-%H%M%S).mp4}"
    mkdir -p "$(dirname "$out")"
    (( secs <= 180 )) || { echo "screenrecord caps at 180s" >&2; exit 2; }
    size="$(record_size)"
    echo "Recording screen for ${secs}s at $size..." >&2
    adb shell screenrecord --size "$size" --time-limit "$secs" --bit-rate 8000000 /sdcard/ani-rec.mp4
    sleep 1
    adb pull /sdcard/ani-rec.mp4 "$out" >/dev/null
    adb shell rm -f /sdcard/ani-rec.mp4
    echo "$out"
    ;;

  record-start)
    out="${1:-$OUT_DIR/rec-$(date +%Y%m%d-%H%M%S).mp4}"
    mkdir -p "$(dirname "$out")"
    size="$(record_size)"
    # redirect: the detached recorder must not inherit our stdout, or callers using
    # $(record-start) block until the recorder exits
    adb shell screenrecord --size "$size" --time-limit 180 --bit-rate 8000000 /sdcard/ani-rec.mp4 \
      >"$OUT_DIR/.droid-rec.log" 2>&1 &
    echo "$! $out" > "$OUT_DIR/.droid-rec"
    sleep 1
    echo "recording started (auto-stops at 180s); stop with: droid.sh record-stop" >&2
    echo "$out"
    ;;

  record-stop)
    [[ -f "$OUT_DIR/.droid-rec" ]] || { echo "no recording in progress (use record-start first)" >&2; exit 1; }
    read -r rec_pid out < "$OUT_DIR/.droid-rec"
    rm -f "$OUT_DIR/.droid-rec"
    adb shell pkill -INT screenrecord >/dev/null 2>&1 || true
    for _ in 1 2 3 4 5 6 7 8 9 10; do kill -0 "$rec_pid" 2>/dev/null || break; sleep 0.5; done
    sleep 1 # let the muxer finalize the mp4
    adb pull /sdcard/ani-rec.mp4 "$out" >/dev/null
    adb shell rm -f /sdcard/ani-rec.mp4
    echo "$out"
    ;;

  frames)
    python3 "$SCRIPT_DIR/frame_diff.py" "$@"
    ;;

  devices)
    adb devices -l
    ;;

  help|*)
    cat <<'EOF'
Animeko Android UI verification toolbox. All state-changing commands target $ANI_PKG (default me.him188.ani.debug).
Set ANDROID_SERIAL when multiple devices are connected. Set ANI_AVD to use another AVD. HEADED=1 shows the emulator window.

  emulator                    start (or reuse) the AVD, wait for full boot, disable animations
  kill-emulator               shut the emulator down
  install [apk]               adb-install newest built default-debug APK (or the given one)
  uninstall                   uninstall the app
  launch | relaunch | stop-app
  clear                       wipe app data (back to first-run onboarding)
  screenshot [out.png]        capture screen; prints the absolute PNG path (Read it to see the screen)
  ui [out.xml]                dump raw UI hierarchy XML; prints the file path
  tree [--all]                pretty-print the Compose semantics tree with dp sizes (--all includes plain containers)
  find <substring>            search UI dump by text/content-desc/resource-id; prints tap-ready center + dp size
  tap <x> <y>
  swipe <x1> <y1> <x2> <y2> [ms]
  text <ascii>                type text (ASCII only, no CJK — Android `input text` limitation)
  key <keycode> | back | home
  display wide|tablet|phone|reset
  logcat [n] | crashes [n]    app log tail / crash buffer
  record <secs> [out.mp4]     record the screen for N seconds (max 180), print the video path
  record-start [out.mp4]      start recording in background; interact, then record-stop
  record-stop                 stop recording, pull + print the video path
  frames <video> [args]       analyze a recording for transient glitches (frame_diff.py; --help for options)
  devices
EOF
    ;;
esac
