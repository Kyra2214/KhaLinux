#!/usr/bin/env bash
set -Eeuo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RUNTIME_DIR="$ROOT_DIR/android/runtime"
BUILD_DIR="$RUNTIME_DIR/build/pty-host-test"
JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"

command -v gcc >/dev/null || { echo 'gcc ausente' >&2; exit 1; }
command -v javac >/dev/null || { echo 'javac ausente' >&2; exit 1; }
[[ -f "$JAVA_HOME/include/jni.h" ]] || { echo "jni.h ausente em $JAVA_HOME" >&2; exit 1; }

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/classes"
gcc -std=c11 -Wall -Wextra -Werror -fPIC -shared \
  -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" \
  "$RUNTIME_DIR/src/main/cpp/native_pty.c" \
  -o "$BUILD_DIR/libkhalinux_pty.so" -lutil
javac -d "$BUILD_DIR/classes" \
  "$RUNTIME_DIR/src/main/java/com/khalinux/runtime/NativePty.java" \
  "$RUNTIME_DIR/src/testHost/java/com/khalinux/runtime/NativePtyIntegrationTest.java"
java -Djava.library.path="$BUILD_DIR" -cp "$BUILD_DIR/classes" \
  com.khalinux.runtime.NativePtyIntegrationTest
