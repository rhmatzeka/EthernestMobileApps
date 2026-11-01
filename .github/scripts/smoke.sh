#!/usr/bin/env bash
# Installs the debug APK on the running emulator, creates a wallet through the
# real onboarding screens, opens every main screen and fails if the app crashed.
# Screenshots and UI dumps of each screen are saved in smoke/.
set -uo pipefail

PKG=id.rahmat.projekakhir
OUT=smoke
HERE="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$OUT"

adb wait-for-device
adb install -r -g app/build/outputs/apk/debug/app-debug.apk
# CI emulators often show "Pixel Launcher isn't responding": we never need the
# launcher, so turn it off and hide system crash/ANR dialogs.
adb shell pm disable-user --user 0 com.google.android.apps.nexuslauncher >/dev/null 2>&1 || true
adb shell settings put global hide_error_dialogs 1 || true
sleep 5
adb logcat -c || true

find_node() { python3 "$HERE/ui_find.py" /tmp/ui.xml "$1"; }
dump() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true
  adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1 || true
}
wait_for() {
  for _ in $(seq 1 "${2:-30}"); do
    dump
    [ -n "$(find_node "$1")" ] && return 0
    sleep 2
  done
  echo "::error::timed out waiting for $1"
  shot "timeout"
  return 1
}
tap() {
  wait_for "$1" || return 1
  # shellcheck disable=SC2046
  adb shell input tap $(find_node "$1")
  sleep "${2:-2}"
}
shot() {
  adb exec-out screencap -p > "$OUT/$1.png" || true
  cp /tmp/ui.xml "$OUT/$1.xml" 2>/dev/null || true
}
texts() {
  # Visible texts of the current screen, for the job log.
  python3 - /tmp/ui.xml <<'PY'
import sys, xml.etree.ElementTree as ET
try:
    root = ET.parse(sys.argv[1]).getroot()
except Exception:
    sys.exit(0)
seen = [n.get("text") for n in root.iter("node") if n.get("text")]
print(" | ".join(seen[:60]))
PY
}
alive() {
  if ! adb shell pidof "$PKG" >/dev/null; then
    echo "::error::app is not running after: $1"
    return 1
  fi
}

failed=0
step() {
  echo "== $1"
  shift
  "$@" || failed=1
}

adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null
step "onboarding: skip"        tap id:textSkip
step "onboarding: create"      tap id:buttonCreateWallet
step "pin: first entry"        tap id:inputPin 1
adb shell input text 123456
step "pin: confirm entry"      tap id:inputConfirmPin 1
adb shell input text 123456
adb shell input keyevent 111   # ESC: close the keyboard
sleep 1
step "pin: continue"           tap id:buttonContinue 4
step "home: opened"            wait_for id:menu_home 30
# Give the balance and price requests time to finish.
sleep 20
dump; shot "1-home"; texts
step "home alive" alive home

step "history" tap id:menu_history 6
dump; shot "2-history"; texts
step "market" tap id:menu_market 10
dump; shot "3-market"; texts
step "settings" tap id:menu_settings 4
dump; shot "4-settings"; texts
step "back home" tap id:menu_home 4
step "send screen" tap id:buttonSend 6
dump; shot "5-send"; texts
adb shell input keyevent 4; sleep 2
step "receive screen" tap id:buttonReceive 4
dump; shot "6-receive"; texts
adb shell input keyevent 4; sleep 2
step "swap screen" tap id:buttonQuickSwap 6
dump; shot "7-swap"; texts
step "after tour alive" alive tour

adb logcat -d > "$OUT/logcat.txt" || true
if grep -q "FATAL EXCEPTION" "$OUT/logcat.txt"; then
  echo "::error::the app crashed"
  grep -A 30 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -80
  failed=1
fi
exit "$failed"
