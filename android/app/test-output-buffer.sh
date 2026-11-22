#!/usr/bin/env bash
set -Eeuo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BUILD_DIR="$(mktemp -d)"
trap 'rm -rf "$BUILD_DIR"' EXIT
cd "$ROOT_DIR"

javac -d "$BUILD_DIR" \
  android/app/src/main/java/com/khalinux/android/TerminalOutputBuffer.java \
  android/app/src/testHost/java/com/khalinux/android/TerminalOutputBufferTest.java
java -cp "$BUILD_DIR" com.khalinux.android.TerminalOutputBufferTest
