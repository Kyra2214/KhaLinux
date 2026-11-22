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
COMPRESS_JAR="${COMPRESS_JAR:-/usr/share/java/commons-compress.jar}"
COMMONS_IO_JAR="${COMMONS_IO_JAR:-/usr/share/java/commons-io.jar}"
COMMONS_LANG3_JAR="${COMMONS_LANG3_JAR:-/usr/share/java/commons-lang3.jar}"

for file in "${KOTLIN_TEST_JAR}" "${KOTLIN_STDLIB}" "${JUNIT_JAR}" "${HAMCREST_JAR}" "${COMPRESS_JAR}" "${COMMONS_IO_JAR}" "${COMMONS_LANG3_JAR}"; do
  [[ -s "${file}" ]] || { echo "dependência ausente: ${file}" >&2; exit 1; }
done
command -v javac >/dev/null || { echo 'javac ausente' >&2; exit 1; }
command -v sort >/dev/null || { echo 'sort ausente' >&2; exit 1; }

KOTLIN_VERSION="$(kotlinc -version 2>&1 | sed -n 's/.*kotlinc-jvm \([^ ]*\).*/\1/p')"
[[ -n "${KOTLIN_VERSION}" ]] || { echo 'não foi possível identificar a versão do Kotlin' >&2; exit 1; }
MIN_KOTLIN_VERSION="1.9.24"
OLDEST_VERSION="$(printf '%s\n' "${MIN_KOTLIN_VERSION}" "${KOTLIN_VERSION}" | sort -V | head -n 1)"
[[ "${OLDEST_VERSION}" == "${MIN_KOTLIN_VERSION}" ]] || {
  echo "Kotlin ${KOTLIN_VERSION} incompatível; use ${MIN_KOTLIN_VERSION} ou superior" >&2
  exit 1
}

rm -rf "${BUILD_DIR}"
mkdir -p "${BUILD_DIR}/classes" "${BUILD_DIR}/test-classes"
LIBS="${KOTLIN_STDLIB}:${COMPRESS_JAR}:${COMMONS_IO_JAR}:${COMMONS_LANG3_JAR}"
TEST_LIBS="${KOTLIN_TEST_JAR}:${JUNIT_JAR}:${HAMCREST_JAR}"
javac -d "${BUILD_DIR}/classes" android/runtime/src/main/java/com/khalinux/runtime/NativePty.java
# Todo o código Kotlin do runtime (inclui install/ e diagnostics/); não depende do Android SDK.
find android/runtime/src/main/kotlin -name '*.kt' > "${BUILD_DIR}/main-sources.txt"
kotlinc @"${BUILD_DIR}/main-sources.txt" \
  -jvm-target 17 -cp "${BUILD_DIR}/classes:${LIBS}" -d "${BUILD_DIR}/classes"
find android/runtime/src/test/kotlin -name '*.kt' > "${BUILD_DIR}/test-sources.txt"
kotlinc @"${BUILD_DIR}/test-sources.txt" -jvm-target 17 -Xfriend-paths="${BUILD_DIR}/classes" \
  -cp "${BUILD_DIR}/classes:${LIBS}:${TEST_LIBS}" -d "${BUILD_DIR}/test-classes"
TEST_CLASSES="$(cd "${BUILD_DIR}/test-classes" && find . -name '*Test.class' | sed 's#^\./##; s#\.class$##; s#/#.#g' | sort | tr '\n' ' ')"
echo "Classes de teste: ${TEST_CLASSES}"
# shellcheck disable=SC2086
java -cp "${BUILD_DIR}/classes:${BUILD_DIR}/test-classes:${LIBS}:${TEST_LIBS}" \
  org.junit.runner.JUnitCore ${TEST_CLASSES}
if [[ "${SKIP_NATIVE_PTY:-0}" != "1" ]]; then bash android/runtime/test-pty-native.sh; fi
