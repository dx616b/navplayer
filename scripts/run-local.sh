#!/usr/bin/env bash
# Build NavPlayer and run it on a local Android emulator (WSL2 / Linux).
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
exec "$ROOT/scripts/setup-android-env.sh" --run
