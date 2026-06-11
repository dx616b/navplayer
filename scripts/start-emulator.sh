#!/usr/bin/env bash
# Start the NavPlayer emulator, install APK, and launch the app (no rebuild).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}"
AVD_NAME="navplayer_land"
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
LOG=/tmp/navplayer-emulator.log

log() { printf '==> %s\n' "$*"; }
die() { printf '!! %s\n' "$*" >&2; exit 1; }

[ -f "$HOME/.navplayer-android-env.sh" ] && . "$HOME/.navplayer-android-env.sh"
export PATH="$SDK_ROOT/emulator:$SDK_ROOT/platform-tools:$SDK_ROOT/cmdline-tools/latest/bin:$PATH"

command -v emulator >/dev/null || die "Emulator not installed. Run: ./scripts/setup-android-env.sh --emulator"
[ -f "$APK" ] || die "APK missing. Run: ./gradlew :app:assembleDebug"

if ! dpkg -s libpulse0 >/dev/null 2>&1; then
  die "Missing libpulse0. Run: sudo apt-get install -y libpulse0"
fi

in_kvm_group() {
  getent group kvm | grep -qE ":($USER)$|:($USER),|,($USER)$|,($USER),"
}

ensure_kvm() {
  if [[ -r /dev/kvm ]]; then
    return 0
  fi
  if in_kvm_group; then
    log "KVM group set but not active in this shell — using sg kvm"
    return 0
  fi
  cat >&2 <<EOF
!! x86_64 emulator needs KVM (/dev/kvm).

Run once:
  sudo gpasswd -a $USER kvm

Then run this script again (no WSL logout needed):
  ./scripts/start-emulator.sh
EOF
  exit 1
}

launch_emulator() {
  local avd=$1
  if [[ -r /dev/kvm ]]; then
    nohup emulator -avd "$avd" -no-snapshot -gpu swiftshader_indirect >"$LOG" 2>&1 &
  else
    nohup sg kvm -c "emulator -avd $avd -no-snapshot -gpu swiftshader_indirect" >"$LOG" 2>&1 &
  fi
  echo $!
}

if adb devices | grep -q 'emulator-'; then
  log "Emulator already connected"
else
  avdmanager list avd 2>/dev/null | grep -q "Name: $AVD_NAME" || \
    die "AVD $AVD_NAME not found. Run: ./scripts/run-local.sh once"
  ensure_kvm
  log "Starting emulator..."
  : >"$LOG"
  EMU_PID=$(launch_emulator "$AVD_NAME")
  sleep 5
  kill -0 "$EMU_PID" 2>/dev/null || {
    tail -20 "$LOG" >&2 || true
    die "Emulator failed to start (see $LOG)"
  }
  timeout 180 adb wait-for-device || die "Timed out waiting for emulator (see $LOG)"
  for _ in $(seq 1 90); do
    [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]] && break
    sleep 2
  done
fi

adb shell settings put system user_rotation 1 || true
log "Installing APK..."
adb install -r "$APK"
log "Launching NavPlayer..."
adb shell am start -n com.dean.navplayer/.ui.MainActivity
log "Done. Navidrome on this PC: https://10.0.2.2:4533"
log "Emulator log: $LOG"
