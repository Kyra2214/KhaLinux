#!/usr/bin/env bash
set -Eeuo pipefail

# Build reproduzível do RootFS Debian ARM64. Executar em host x86_64/arm64 com root.
# Exemplo: sudo SOURCE_DATE_EPOCH=1763769600 ./build-rootfs.sh

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUTPUT_DIR="${OUTPUT_DIR:-${ROOT_DIR}/distribution/build/output}"
ROOTFS_DIR="${ROOTFS_DIR:-${OUTPUT_DIR}/rootfs-arm64}"
DEBIAN_SUITE="${DEBIAN_SUITE:-bookworm}"
DEBIAN_SNAPSHOT="${DEBIAN_SNAPSHOT:-20251122T000000Z}"
ROOTFS_RELEASE_TAG="${ROOTFS_RELEASE_TAG:-rootfs-bookworm-20261122-v1}"
DEBIAN_MIRROR="${DEBIAN_MIRROR:-https://snapshot.debian.org/archive/debian/${DEBIAN_SNAPSHOT}}"
SOURCE_DATE_EPOCH="${SOURCE_DATE_EPOCH:-$(git -C "${ROOT_DIR}" log -1 --format=%ct 2>/dev/null || date +%s)}"
BUILD_INVOCATION_UTC="${BUILD_INVOCATION_UTC:-$(date -u +%Y-%m-%dT%H:%M:%SZ)}"
PACKAGES_FILE="${ROOT_DIR}/distribution/configuration/base-packages.txt"
SOURCES_FILE="${ROOT_DIR}/distribution/repositories/debian-bookworm.sources"
MANIFEST_DIR="${ROOT_DIR}/distribution/manifests"
MANIFEST_FILE="${MANIFEST_DIR}/rootfs-arm64.json"
QEMU_BIN="${QEMU_BIN:-$(command -v qemu-aarch64-static || true)}"

log() { printf '[khalinux-build] %s\n' "$*"; }
fail() { printf '[khalinux-build] erro: %s\n' "$*" >&2; exit 1; }
require_cmd() { command -v "$1" >/dev/null 2>&1 || fail "comando ausente: $1"; }

[[ "${EUID}" -eq 0 ]] || fail 'execute como root (por exemplo, sudo ./build-rootfs.sh)'
require_cmd debootstrap
require_cmd dpkg
require_cmd sha256sum
require_cmd git
require_cmd tar
require_cmd gzip
require_cmd stat
require_cmd awk
require_cmd sed
[[ -n "${QEMU_BIN}" && -x "${QEMU_BIN}" ]] || fail 'qemu-aarch64-static é necessário para finalizar ARM64 em host não-ARM64'
[[ -s "${PACKAGES_FILE}" ]] || fail "lista de pacotes ausente: ${PACKAGES_FILE}"
[[ -s "${SOURCES_FILE}" ]] || fail "fontes Debian ausentes: ${SOURCES_FILE}"
[[ "${DEBIAN_SNAPSHOT}" =~ ^[0-9]{8}T[0-9]{6}Z$ ]] || fail "snapshot Debian inválido: ${DEBIAN_SNAPSHOT}"
[[ "${ROOTFS_RELEASE_TAG}" =~ ^[A-Za-z0-9._-]+$ ]] || fail "tag de release inválida: ${ROOTFS_RELEASE_TAG}"

HOST_ARCH="$(dpkg --print-architecture)"
[[ "${HOST_ARCH}" == amd64 || "${HOST_ARCH}" == arm64 ]] || fail "arquitetura de host não suportada: ${HOST_ARCH}"

mkdir -p "${OUTPUT_DIR}" "${MANIFEST_DIR}"
if [[ -e "${ROOTFS_DIR}" ]]; then
  [[ "${KHALINUX_ALLOW_CLEAN:-}" == 1 ]] || fail "saída já existe; defina KHALINUX_ALLOW_CLEAN=1 para recriar: ${ROOTFS_DIR}"
  rm -rf --one-file-system "${ROOTFS_DIR}"
fi
mkdir -p "${ROOTFS_DIR}"

mapfile -t PACKAGES < <(sed -e 's/#.*//' -e '/^[[:space:]]*$/d' "${PACKAGES_FILE}")
INCLUDE="$(IFS=,; echo "${PACKAGES[*]}")"

log "criando Debian ${DEBIAN_SUITE} ARM64 com debootstrap"
# shellcheck disable=SC2086
debootstrap --arch=arm64 --foreign --variant=minbase --include="${INCLUDE}" \
  --force-check-gpg \
  "${DEBIAN_SUITE}" "${ROOTFS_DIR}" "${DEBIAN_MIRROR}"

install -Dm755 "${QEMU_BIN}" "${ROOTFS_DIR}/usr/bin/qemu-aarch64-static"
install -d "${ROOTFS_DIR}/etc/apt/sources.list.d"
sed "s/20251122T000000Z/${DEBIAN_SNAPSHOT}/g" "${SOURCES_FILE}" > "${ROOTFS_DIR}/etc/apt/sources.list.d/debian.sources"
chmod 0644 "${ROOTFS_DIR}/etc/apt/sources.list.d/debian.sources"

cat > "${ROOTFS_DIR}/root/khalinux-finalize.sh" <<'FINALIZE'
#!/bin/sh
set -eu
/debootstrap/debootstrap --second-stage
printf 'khalinux\n' > /etc/hostname
install -d -m 0755 /etc/khalinux /var/lib/khalinux
if ! getent group khalinux >/dev/null 2>&1; then groupadd --system khalinux; fi
if ! id khalinux >/dev/null 2>&1; then useradd --system --create-home --gid khalinux --shell /bin/bash khalinux; fi
passwd --lock khalinux >/dev/null 2>&1 || true
cat > /etc/profile.d/khalinux.sh <<'PROFILE'
export KHALINUX_ROOTFS=1
export LANG=C.UTF-8
export LC_ALL=C.UTF-8
PROFILE
rm -f /etc/apt/sources.list
rm -f /root/khalinux-finalize.sh /usr/bin/qemu-aarch64-static
apt-get clean
rm -rf /var/lib/apt/lists/* /var/cache/apt/*
FINALIZE
chmod 0755 "${ROOTFS_DIR}/root/khalinux-finalize.sh"

log 'finalizando configuração dentro do RootFS ARM64'
chroot "${ROOTFS_DIR}" /root/khalinux-finalize.sh

# Keep the pinned .sources file so APT remains usable after installation.
# Only remove editor/backup artifacts; the repository definition is part of the RootFS.
rm -f "${ROOTFS_DIR}/etc/apt/sources.list.d/debian.sources.save"

ROOTFS_BYTES="$(du -sb "${ROOTFS_DIR}" | awk '{print $1}')"
ROOTFS_SHA256="$(tar --sort=name --mtime="@${SOURCE_DATE_EPOCH}" --owner=0 --group=0 --numeric-owner -C "${ROOTFS_DIR}" -cf - . | sha256sum | awk '{print $1}')"
ARCHIVE_FILE="${OUTPUT_DIR}/rootfs-arm64.tar.gz"
tar --sort=name --mtime="@${SOURCE_DATE_EPOCH}" --owner=0 --group=0 --numeric-owner -C "${ROOTFS_DIR}" -cf - . | gzip -n > "${ARCHIVE_FILE}"
ARCHIVE_BYTES="$(stat -c %s "${ARCHIVE_FILE}")"
ARCHIVE_SHA256="$(sha256sum "${ARCHIVE_FILE}" | awk '{print $1}')"
GIT_COMMIT="$(git -C "${ROOT_DIR}" rev-parse HEAD 2>/dev/null || printf 'uncommitted')"
PACKAGE_LOCK_FILE="${MANIFEST_DIR}/base-package-versions-arm64.txt"
: > "${PACKAGE_LOCK_FILE}"
for package in "${PACKAGES[@]}"; do
  dpkg_version="$(chroot "${ROOTFS_DIR}" dpkg-query -W -f='${Version}' "${package}" 2>/dev/null || true)"
  [[ -n "${dpkg_version}" ]] && printf '%s=%s\n' "${package}" "${dpkg_version}" >> "${PACKAGE_LOCK_FILE}"
done
cat > "${MANIFEST_FILE}" <<MANIFEST
{
  "schema": 1,
  "project": "KhaLinux",
  "artifact": "rootfs-arm64",
  "distribution": "Debian",
  "suite": "${DEBIAN_SUITE}",
  "architecture": "arm64",
  "mirror": "${DEBIAN_MIRROR}",
  "snapshot": "${DEBIAN_SNAPSHOT}",
  "source_date_epoch": ${SOURCE_DATE_EPOCH},
  "source_date_epoch_utc": "$(date -u -d "@${SOURCE_DATE_EPOCH}" +%Y-%m-%dT%H:%M:%SZ)",
  "build_invocation_utc": "${BUILD_INVOCATION_UTC}",
  "git_commit": "${GIT_COMMIT}",
  "package_list": "distribution/configuration/base-packages.txt",
  "package_lock": "distribution/manifests/base-package-versions-arm64.txt",
  "rootfs_bytes": ${ROOTFS_BYTES},
  "canonical_tar_sha256": "${ROOTFS_SHA256}",
  "archive_name": "rootfs-arm64.tar.gz",
  "release_tag": "${ROOTFS_RELEASE_TAG}",
  "archive_url": "https://github.com/Kyra2214/KhaLinux/releases/download/${ROOTFS_RELEASE_TAG}/rootfs-arm64.tar.gz",
  "archive_bytes": ${ARCHIVE_BYTES},
  "archive_sha256": "${ARCHIVE_SHA256}"
}
MANIFEST

log "RootFS concluído: ${ROOTFS_DIR}"
log "manifesto: ${MANIFEST_FILE}"
log "SHA-256 do tar canônico: ${ROOTFS_SHA256}"
log "arquivo instalável: ${ARCHIVE_FILE} (${ARCHIVE_BYTES} bytes)"
log "SHA-256 do arquivo: ${ARCHIVE_SHA256}"
