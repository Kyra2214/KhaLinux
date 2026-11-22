#!/usr/bin/env bash
set -Eeuo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ROOTFS_DIR="${ROOTFS_DIR:-${ROOT_DIR}/distribution/build/output/rootfs-arm64}"
fail() { printf '[khalinux-rootfs-test] erro: %s\n' "$*" >&2; exit 1; }
[[ "${EUID}" -eq 0 ]] || fail 'execute como root para usar chroot'
command -v file >/dev/null || fail 'file ausente'
command -v qemu-aarch64-static >/dev/null || fail 'qemu-aarch64-static ausente'
[[ -d "${ROOTFS_DIR}" ]] || fail "RootFS ausente: ${ROOTFS_DIR}"
file "${ROOTFS_DIR}/bin/bash" | grep -q 'ARM aarch64' || fail '/bin/bash não é ELF ARM64'

QEMU_IN_ROOT="${ROOTFS_DIR}/usr/bin/qemu-aarch64-static"
install -Dm755 "$(command -v qemu-aarch64-static)" "${QEMU_IN_ROOT}"
cleanup() { rm -f "${QEMU_IN_ROOT}"; }
trap cleanup EXIT

chroot "${ROOTFS_DIR}" /usr/bin/qemu-aarch64-static /bin/bash -c '
set -eu
[ "$(dpkg --print-architecture)" = arm64 ]
command -v bash >/dev/null
command -v python3 >/dev/null
command -v git >/dev/null
command -v sqlite3 >/dev/null
command -v apt-get >/dev/null
[ -x /bin/bash ]
id khalinux >/dev/null
apt-get --version >/dev/null
printf "rootfs_smoke_test=ok architecture=arm64 apt=binary-present\\n"
'
