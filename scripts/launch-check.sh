#!/usr/bin/env bash
# Installs each APK on the emulator that is already running, opens the app, waits, and says whether it is still alive.
# Usage: scripts/launch-check.sh <out-dir> <apk> [<apk> ...]   (the APK's file name, minus .apk, is its label)
# Writes <out-dir>/<label>.install.txt, <label>.logcat.txt and <label>.result.txt, and one line per APK to $GITHUB_STEP_SUMMARY.
set -u
OUT="$1"; shift
PKG=com.mangotv.app
ACT=com.mangotv.app/com.mangotv.app.MainActivity
WAIT_SECONDS="${WAIT_SECONDS:-25}"
mkdir -p "$OUT"
status=0
for apk in "$@"; do
  label="$(basename "$apk" .apk)"
  adb shell am force-stop "$PKG" >/dev/null 2>&1
  adb uninstall "$PKG" >/dev/null 2>&1
  if ! adb install -r -d "$apk" >"$OUT/$label.install.txt" 2>&1 || ! grep -q "Success" "$OUT/$label.install.txt"; then
    echo "INSTALL FAILED: $(tr '\n' ' ' <"$OUT/$label.install.txt")" >"$OUT/$label.result.txt"
    status=1
  else
    adb logcat -c
    adb shell am start -W -n "$ACT" >"$OUT/$label.start.txt" 2>&1
    sleep "$WAIT_SECONDS"
    adb logcat -d >"$OUT/$label.logcat.txt" 2>&1
    pid="$(adb shell pidof "$PKG" | tr -d '\r')"
    if grep -q "FATAL EXCEPTION" "$OUT/$label.logcat.txt" || [ -z "$pid" ]; then
      {
        echo "CRASHED (process $( [ -z "$pid" ] && echo gone || echo alive ))"
        grep -m1 -A25 "FATAL EXCEPTION" "$OUT/$label.logcat.txt"
      } >"$OUT/$label.result.txt"
      status=1
    else
      echo "OK: still running after ${WAIT_SECONDS}s" >"$OUT/$label.result.txt"
    fi
  fi
  line="$(head -1 "$OUT/$label.result.txt")"
  echo "$label: $line"
  if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
    echo "- **${DEVICE_LABEL:-emulator} / $label**: $line" >>"$GITHUB_STEP_SUMMARY"
    if [ "$(wc -l <"$OUT/$label.result.txt")" -gt 1 ]; then
      { echo '```'; head -30 "$OUT/$label.result.txt"; echo '```'; } >>"$GITHUB_STEP_SUMMARY"
    fi
  fi
done
exit $status
