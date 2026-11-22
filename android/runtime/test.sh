#!/usr/bin/env bash
set -Eeuo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT_DIR}"
BUILD_DIR="${ROOT_DIR}/android/runtime/build"
KOTLINC_BIN="$(command -v kotlinc || true)"
[[ -n "${KOTLINC_BIN}" ]] || { echo 'kotlinc ausente (necessário Kotlin 1.9.24+)' >&2; exit 1; }
KOTLINC_REAL="$(readlink -f "${KOTLINC_BIN}")"
KOTLIN_HOME="$(cd "$(dirname "$(dirname "${KOTLINC_REAL}")")" && pwd)"
KOTLIN_LIB="${KOTLIN_HOME}/lib"
KOTLIN_TEST_JAR="${KOTLIN_TEST_JAR:-${KOTLIN_LIB}/kotlin-test.jar}"
KOTLIN_STDLIB="${KOTLIN_STDLIB:-${KOTLIN_LIB}/kotlin-stdlib.jar}"
JUNIT_JAR="${JUNIT_JAR:-/usr/share/java/junit4.jar}"
HAMCREST_JAR="${HAMCREST_JAR:-/usr/share/java/hamcrest-core.jar}"

for file in "${KOTLIN_TEST_JAR}" "${KOTLIN_STDLIB}" "${JUNIT_JAR}" "${HAMCREST_JAR}"; do
  [[ -s "${file}" ]] || { echo "dependência ausente: ${file}" >&2; exit 1; }
done
command -v proot >/dev/null || { echo 'proot ausente' >&2; exit 1; }
command -v qemu-aarch64-static >/dev/null || { echo 'qemu-aarch64-static ausente' >&2; exit 1; }
command -v javac >/dev/null || { echo 'javac ausente' >&2; exit 1; }
command -v sort >/dev/null || { echo 'sort ausente' >&2; exit 1; }

KOTLIN_VERSION="$(kotlinc -version 2>&1 | sed -n 's/.*kotlinc-jvm \\([^ ]*\\).*/\\1/p')"
[[ -n "${KOTLIN_VERSION}" ]] || { echo 'não foi possível identificar a versão do Kotlin' >&2; exit 1; }
MIN_KOTLIN_VERSION="1.9.24"
OLDEST_VERSION="$(printf '%s\\n' "${MIN_KOTLIN_VERSION}" "${KOTLIN_VERSION}" | sort -V | head -n 1)"
[[ "${OLDEST_VERSION}" == "${MIN_KOTLIN_VERSION}" ]] || {
  echo "Kotlin ${KOTLIN_VERSION} incompatível; use ${MIN_KOTLIN_VERSION} ou superior" >&2
  exit 1
}

rm -rf "${BUILD_DIR}"
mkdir -p "${BUILD_DIR}/classes" "${BUILD_DIR}/test-classes"
javac -d "${BUILD_DIR}/classes" android/runtime/src/main/java/com/khalinux/runtime/NativePty.java
kotlinc android/runtime/src/main/kotlin/com/khalinux/runtime/*.kt \\
  -jvm-target 17 -cp "${BUILD_DIR}/classes" -d "${BUILD_DIR}/classes"
kotlinc -jvm-target 17 -cp "${BUILD_DIR}/classes:${KOTLIN_STDLIB}:${KOTLIN_TEST_JAR}:${JUNIT_JAR}:${HAMCREST_JAR}" \\
  android/runtime/src/test/kotlin/com/khalinux/runtime/*.kt -d "${BUILD_DIR}/test-classes"
java -cp "${BUILD_DIR}/classes:${BUILD_DIR}/test-classes:${KOTLIN_STDLIB}:${KOTLIN_TEST_JAR}:${JUNIT_JAR}:${HAMCREST_JAR}" \\
  org.junit.runner.JUnitCore com.khalinux.runtime.SessionManagerTest
bash android/runtime/test-pty-native.sh
