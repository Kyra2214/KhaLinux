#!/usr/bin/env bash
set -euo pipefail

# Release aprovada (pinada no app). Atualizar junto com o PR que atualiza os hashes do app.
ROOTFS_RELEASE_TAG="${KHALINUX_APPROVED_ROOTFS_TAG:-rootfs-bookworm-20261122-v1}"
ROOTFS_ARCHIVE_SHA256="bcd272a77f6f081408e1b60d9e9ed65ad9c23678b8892d334b5e051bf61613e8"
ARCHIVE_URL="https://github.com/Kyra2214/KhaLinux/releases/download/${ROOTFS_RELEASE_TAG}/rootfs-arm64.tar.gz"
WORK_DIR="$(mktemp -d)"
ROOTFS="${WORK_DIR}/rootfs"
ARCHIVE="${WORK_DIR}/rootfs-arm64.tar.gz"

cleanup() {
    sudo rm -rf "${WORK_DIR}"
}
trap cleanup EXIT

for command in curl sha256sum tar proot qemu-aarch64-static timeout; do
    command -v "${command}" >/dev/null 2>&1 || {
        echo "Dependência ausente: ${command}" >&2
        exit 1
    }
done

echo "[proot-smoke] baixando RootFS ARM64 fixado"
curl --fail --location --retry 3 --retry-all-errors --silent --show-error \
    "${ARCHIVE_URL}" --output "${ARCHIVE}"
printf '%s  %s\n' "${ROOTFS_ARCHIVE_SHA256}" "${ARCHIVE}" | sha256sum --check --status

mkdir -p "${ROOTFS}"
sudo tar -xzf "${ARCHIVE}" --numeric-owner -C "${ROOTFS}"
sudo chown -R "$(id -u):$(id -g)" "${ROOTFS}"

# O RootfsInstaller não cria nós de dispositivo; prepara placeholders equivalentes.
if [[ -L "${ROOTFS}/dev" || -L "${ROOTFS}/dev/pts" ]]; then
    echo "Diretório de dispositivo inesperadamente simbólico no RootFS" >&2
    exit 1
fi
mkdir -p "${ROOTFS}/dev/pts"
for node in null zero random urandom tty; do
    target="${ROOTFS}/dev/${node}"
    if [[ -L "${target}" ]]; then
        echo "Ponto de montagem inesperado como symlink: ${target}" >&2
        exit 1
    fi
    if [[ ! -e "${target}" ]]; then
        : > "${target}"
    fi
done

QEMU="$(command -v qemu-aarch64-static)"
PROOT="$(command -v proot)"
echo "[proot-smoke] host PRoot: $("${PROOT}" --version 2>&1 | head -n 1)"

timeout 90s "${PROOT}" -0 -q "${QEMU}" -r "${ROOTFS}" \
    -b /dev/null:/dev/null \
    -b /dev/zero:/dev/zero \
    -b /dev/random:/dev/random \
    -b /dev/urandom:/dev/urandom \
    -b /dev/tty:/dev/tty \
    -b /dev/pts:/dev/pts \
    -w /home/khalinux \
    /usr/bin/env -i \
        HOME=/home/khalinux \
        LANG=C.UTF-8 \
        LC_ALL=C.UTF-8 \
        PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin \
        /bin/bash --noprofile --norc -lc \
        'set -eu; test "$(id -u)" = 0; test -c /dev/null; test -d /dev/pts; printf "shell_smoke=ok arch=%s\\n" "$(uname -m)"; printf smoke > /dev/null; head -c 1 /dev/urandom > /dev/null'

echo "[proot-smoke] RootFS ARM64 + host PRoot + allowlisted device binds: ok"
