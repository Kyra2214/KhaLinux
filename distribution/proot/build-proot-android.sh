#!/usr/bin/env bash
set -Eeuo pipefail

# Build oficial do PRoot para Android ARM64 usando as receitas e fontes do Termux.
PROOT_VERSION="5.1.107.102"
TALLOC_VERSION="2.5.0"
SHMEM_VERSION="0.7"
PROOT_SHA256="30d12a6e2cc4147123756ff58edea262d3083bbc238c12d0ab9b39e3d1c40852"
TALLOC_SHA256="912afa237510ae542a7733998eb18a12bcda35ab6729c8e2ddb43e8d0ebab007"
SHMEM_SHA256="1e5ff8459bc0a8c229dd8a94b27d119987e09ef3414331c2b5ebfff20b98e867"
RELEASE_TAG="proot-android-${PROOT_VERSION}-v1"
API_LEVEL="${ANDROID_API_LEVEL:-35}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WORK_DIR="${WORK_DIR:-${ROOT_DIR}/distribution/build/proot-work}"
OUTPUT_DIR="${OUTPUT_DIR:-${ROOT_DIR}/distribution/build/proot-output}"
MANIFEST_DIR="${ROOT_DIR}/distribution/manifests"
SOURCE_DATE_EPOCH="${SOURCE_DATE_EPOCH:-1763769600}"
NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"

fail() { printf '[khalinux-proot] erro: %s\n' "$*" >&2; exit 1; }
require_cmd() { command -v "$1" >/dev/null 2>&1 || fail "comando ausente: $1"; }
for cmd in curl sha256sum tar gzip make python3; do require_cmd "$cmd"; done
[[ -n "$NDK" && -d "$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin" ]] || fail 'ANDROID_NDK_HOME/ROOT inválido'
[[ "$API_LEVEL" =~ ^[0-9]+$ ]] || fail 'ANDROID_API_LEVEL inválido'

TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
CC="$TOOLCHAIN/aarch64-linux-android${API_LEVEL}-clang"
AR="$TOOLCHAIN/llvm-ar"
RANLIB="$TOOLCHAIN/llvm-ranlib"
STRIP="$TOOLCHAIN/llvm-strip"
OBJCOPY="$TOOLCHAIN/llvm-objcopy"
OBJDUMP="$TOOLCHAIN/llvm-objdump"
READELF="$TOOLCHAIN/llvm-readelf"
for tool in "$CC" "$AR" "$RANLIB" "$STRIP" "$OBJCOPY" "$OBJDUMP" "$READELF"; do [[ -x "$tool" ]] || fail "ferramenta NDK ausente: $tool"; done

rm -rf "$WORK_DIR" "$OUTPUT_DIR"
mkdir -p "$WORK_DIR/src" "$WORK_DIR/prefix/lib" "$WORK_DIR/prefix/include/sys" "$OUTPUT_DIR" "$MANIFEST_DIR"
cd "$WORK_DIR/src"

download_verified() {
  local url="$1" output="$2" expected="$3"
  curl --fail --location --retry 4 --retry-all-errors --connect-timeout 20 "$url" -o "$output"
  printf '%s  %s\n' "$expected" "$output" | sha256sum --check --status || fail "SHA-256 inválido: $url"
}

download_verified "https://codeload.github.com/termux/proot/zip/refs/tags/v${PROOT_VERSION}" proot.zip "$PROOT_SHA256"
download_verified "https://www.samba.org/ftp/talloc/talloc-${TALLOC_VERSION}.tar.gz" talloc.tar.gz "$TALLOC_SHA256"
download_verified "https://codeload.github.com/termux/libandroid-shmem/tar.gz/refs/tags/v${SHMEM_VERSION}" libandroid-shmem.tar.gz "$SHMEM_SHA256"

unzip -q proot.zip
tar -xzf talloc.tar.gz
tar -xzf libandroid-shmem.tar.gz
PROOT_SRC="$WORK_DIR/src/proot-${PROOT_VERSION}"
TALLOC_SRC="$WORK_DIR/src/talloc-${TALLOC_VERSION}"
SHMEM_SRC="$WORK_DIR/src/libandroid-shmem-${SHMEM_VERSION}"
[[ -d "$PROOT_SRC/src" && -x "$TALLOC_SRC/configure" && -f "$SHMEM_SRC/Makefile" ]] || fail 'layout inesperado nos fontes fixados'

PREFIX="$WORK_DIR/prefix"
export CC AR RANLIB CFLAGS="-fPIC -O2" LDFLAGS=""

# libandroid-shmem é compilada como arquivo estático e ligada ao PRoot.
make -C "$SHMEM_SRC" clean
# O NDK exige declarações explícitas para strcmp/memset usadas por esta extensão.
python3 - "$PROOT_SRC/src/extension/ashmem_memfd/ashmem_memfd.c" <<'PY'
from pathlib import Path
import sys

source = Path(sys.argv[1])
text = source.read_text()
if "#include <string.h>" not in text:
    anchor = "#include <stdlib.h>" + chr(10)
    if text.count(anchor) != 1:
        raise SystemExit("PRoot ashmem_memfd source layout changed; refusing an unreviewed patch")
    text = text.replace(anchor, anchor + "#include <string.h>" + chr(10))
    source.write_text(text)
PY

# Bionic não define _PATH_TMP. O patch usa TMPDIR, que será apontado pelo app
# para um diretório privado gravável antes de iniciar o PRoot.
python3 - "$SHMEM_SRC/shmem.c" <<'PY'
from pathlib import Path
import sys

source = Path(sys.argv[1])
text = source.read_text()
macro = '#define ASHV_KEY_SYMLINK_PATH _PATH_TMP "ashv_key_%d"'
old = 'sprintf(symlink_path, ASHV_KEY_SYMLINK_PATH, key);'
new = """const char *tmpdir = getenv("TMPDIR");
        if (tmpdir == NULL || tmpdir[0] != '/') tmpdir = "/tmp";
        size_t tmpdir_len = strlen(tmpdir);
        int path_len = snprintf(symlink_path, sizeof(symlink_path), "%s%sashv_key_%d",
                                tmpdir, (tmpdir_len > 0 && tmpdir[tmpdir_len - 1] == '/') ? "" : "/", key);
        if (path_len < 0 || (size_t) path_len >= sizeof(symlink_path)) {
            pthread_mutex_unlock(&mutex);
            errno = ENAMETOOLONG;
            return -1;
        }"""
if text.count(macro) != 1 or text.count(old) != 1:
    raise SystemExit("libandroid-shmem source layout changed; refusing an unreviewed patch")
text = text.replace(macro, "/* KhaLinux patch: temporary key paths are resolved from TMPDIR at runtime. */")
text = text.replace(old, new)
text = text.replace("#include <paths.h>\n", "")
source.write_text(text)
PY
make -C "$SHMEM_SRC" libandroid-shmem.a CC="$CC" AR="$AR" CFLAGS="-fPIC -O2 -std=c11 -Wall -Wextra"
"$RANLIB" "$SHMEM_SRC/libandroid-shmem.a"
install -m 0644 "$SHMEM_SRC/libandroid-shmem.a" "$PREFIX/lib/libandroid-shmem.a"
install -m 0644 "$SHMEM_SRC/shm.h" "$PREFIX/include/sys/shm.h"

# Respostas explícitas para os testes de configure que não podem executar binários ARM64 no host.
cat > "$TALLOC_SRC/cross-answers.txt" <<'ANSWERS'
Checking uname sysname type: "Linux"
Checking uname machine type: "dontcare"
Checking uname release type: "dontcare"
Checking uname version type: "dontcare"
Checking simple C program: OK
building library support: OK
Checking for large file support: OK
Checking for -D_FILE_OFFSET_BITS=64: OK
Checking for WORDS_BIGENDIAN: OK
Checking for C99 vsnprintf: OK
Checking for HAVE_SECURE_MKSTEMP: OK
rpath library support: OK
-Wl,--version-script support: FAIL
Checking correct behavior of strtoll: OK
Checking correct behavior of strptime: OK
Checking for HAVE_IFACE_GETIFADDRS: OK
Checking for HAVE_IFACE_IFCONF: OK
Checking for HAVE_IFACE_IFREQ: OK
Checking getconf LFS_CFLAGS: OK
Checking for large file support without additional flags: OK
Checking for working strptime: OK
Checking for HAVE_SHARED_MMAP: OK
Checking for HAVE_MREMAP: OK
Checking for HAVE_INCOHERENT_MMAP: OK
Checking getconf large file support flags work: OK
ANSWERS
(
  cd "$TALLOC_SRC"
  ./configure --prefix="$PREFIX" --disable-rpath --disable-python --cross-compile --cross-answers=cross-answers.txt
  make -j2
  make install
  cd bin/default
  "$AR" rcu libtalloc.a talloc*.o
  "$RANLIB" libtalloc.a
  install -m 0644 libtalloc.a "$PREFIX/lib/libtalloc.a"
)

# Compila o PRoot oficial com loader embutido e dependências auxiliares estáticas.
make -C "$PROOT_SRC/src" -j2 \
  CC="$CC" AR="$AR" STRIP="$STRIP" OBJCOPY="$OBJCOPY" OBJDUMP="$OBJDUMP" \
  CPPFLAGS="-D_FILE_OFFSET_BITS=64 -D_GNU_SOURCE -DARG_MAX=131072 -I. -I$PROOT_SRC/src -I$PREFIX/include" \
  CFLAGS="-Wall -Wextra -O2 -DWITH_LIBANDROID_SHMEM" \
  LDFLAGS="-L$PREFIX/lib -Wl,-Bstatic -ltalloc -landroid-shmem -Wl,-Bdynamic -Wl,-z,noexecstack -llog -landroid"

PROOT_BIN="$PROOT_SRC/src/proot"
[[ -s "$PROOT_BIN" ]] || fail 'binário PRoot não foi gerado'
"$READELF" -h "$PROOT_BIN" | grep -q 'AArch64' || fail 'binário gerado não é ELF ARM64'
DYNAMIC_INFO="$("$READELF" -d "$PROOT_BIN")"
if grep -Eqi 'lib(talloc|android-shmem)\.so' <<<"$DYNAMIC_INFO"; then
  fail 'binário mantém dependência dinâmica de libtalloc/libandroid-shmem'
fi

install -m 0755 "$PROOT_BIN" "$OUTPUT_DIR/proot"
(
  cd "$OUTPUT_DIR"
  tar --sort=name --mtime="@${SOURCE_DATE_EPOCH}" --owner=0 --group=0 --numeric-owner -cf - proot | gzip -n > proot-arm64.tar.gz
)
BINARY_BYTES="$(stat -c %s "$OUTPUT_DIR/proot")"
ARCHIVE_BYTES="$(stat -c %s "$OUTPUT_DIR/proot-arm64.tar.gz")"
BINARY_SHA256="$(sha256sum "$OUTPUT_DIR/proot" | awk '{print $1}')"
ARCHIVE_SHA256="$(sha256sum "$OUTPUT_DIR/proot-arm64.tar.gz" | awk '{print $1}')"
cat > "$MANIFEST_DIR/proot-arm64.json" <<MANIFEST
{
  "schema": 1,
  "project": "KhaLinux",
  "artifact": "proot-android-arm64",
  "architecture": "arm64",
  "runtime": "Android Bionic",
  "proot_version": "${PROOT_VERSION}",
  "proot_source": "https://github.com/termux/proot",
  "proot_source_sha256": "${PROOT_SHA256}",
  "talloc_version": "${TALLOC_VERSION}",
  "talloc_source_sha256": "${TALLOC_SHA256}",
  "libandroid_shmem_version": "${SHMEM_VERSION}",
  "libandroid_shmem_source_sha256": "${SHMEM_SHA256}",
  "android_api": ${API_LEVEL},
  "release_tag": "${RELEASE_TAG}",
  "archive_name": "proot-arm64.tar.gz",
  "archive_url": "https://github.com/Kyra2214/KhaLinux/releases/download/${RELEASE_TAG}/proot-arm64.tar.gz",
  "archive_bytes": ${ARCHIVE_BYTES},
  "archive_sha256": "${ARCHIVE_SHA256}",
  "binary_name": "proot",
  "binary_bytes": ${BINARY_BYTES},
  "binary_sha256": "${BINARY_SHA256}"
}
MANIFEST
printf '[khalinux-proot] PRoot ARM64 construído e validado\n'
printf 'binary_sha256=%s\narchive_sha256=%s\n' "$BINARY_SHA256" "$ARCHIVE_SHA256"
