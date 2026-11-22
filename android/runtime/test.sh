#!/usr/bin/env bash
set -Eeuo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT_DIR}"
BUILD_DIR="${ROOT_DIR}/android/runtime/build"
KOTLIN_TEST_JAR="/usr/share/java/kotlin-test-1.3.31.jar"
JUNIT_JAR="/usr/share/java/junit4.jar"
HAMCREST_JAR="/usr/share/java/hamcrest-core.jar"
for file in "$KOTLIN_TEST_JAR" "$JUNIT_JAR" "$HAMCREST_JAR"; do
  [[ -s "$file" ]] || { echo "dependência ausente: $file" >&2; exit 1; }
done
command -v kotlinc >/dev/null || { echo 'kotlinc ausente' >&2; exit 1; }
command -v proot >/dev/null || { echo 'proot ausente' >&2; exit 1; }
command -v qemu-aarch64-static >/dev/null || { echo 'qemu-aarch64-static ausente' >&2; exit 1; }
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/classes" "$BUILD_DIR/test-classes"
kotlinc android/runtime/src/main/kotlin/com/khalinux/runtime/*.kt -d "$BUILD_DIR/classes"
kotlinc -cp "$BUILD_DIR/classes:$KOTLIN_TEST_JAR:$JUNIT_JAR:$HAMCREST_JAR" \
  android/runtime/src/test/kotlin/com/khalinux/runtime/*.kt -d "$BUILD_DIR/test-classes"
KOTLIN_STDLIB="$(find /usr/share/java -maxdepth 1 -name 'kotlin-stdlib*.jar' | head -1)"
java -cp "$BUILD_DIR/classes:$BUILD_DIR/test-classes:$KOTLIN_STDLIB:$KOTLIN_TEST_JAR:$JUNIT_JAR:$HAMCREST_JAR" \
  org.junit.runner.JUnitCore com.khalinux.runtime.SessionManagerTest
