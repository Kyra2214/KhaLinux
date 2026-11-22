#!/usr/bin/env bash
set -Eeuo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ROOTFS_DIR="${ROOTFS_DIR:-${ROOT_DIR}/distribution/build/output/rootfs-arm64}"
MANIFEST_FILE="${ROOT_DIR}/distribution/manifests/rootfs-arm64.json"
fail() { printf '[khalinux-verify] erro: %s\n' "$*" >&2; exit 1; }
command -v sha256sum >/dev/null || fail 'sha256sum ausente'
command -v python3 >/dev/null || fail 'python3 ausente'
command -v tar >/dev/null || fail 'tar ausente'
[[ -d "${ROOTFS_DIR}" ]] || fail "RootFS não encontrado: ${ROOTFS_DIR}"
[[ -s "${MANIFEST_FILE}" ]] || fail "manifesto não encontrado: ${MANIFEST_FILE}"
APT_SOURCES_FILE="${ROOTFS_DIR}/etc/apt/sources.list.d/debian.sources"
[[ -s "${APT_SOURCES_FILE}" ]] || fail "fontes APT ausentes no RootFS: ${APT_SOURCES_FILE}"
grep -q 'snapshot.debian.org/archive/debian/20251122T000000Z' "${APT_SOURCES_FILE}" || fail 'snapshot Debian fixo ausente nas fontes APT'
grep -q '^Check-Valid-Until: no
python3 -m json.tool "${MANIFEST_FILE}" >/dev/null || fail 'manifesto JSON inválido'
SOURCE_DATE_EPOCH="$(python3 - "${MANIFEST_FILE}" <<'PY'
import json, sys
print(json.load(open(sys.argv[1], encoding="utf-8"))["source_date_epoch"])
PY
)"
EXPECTED_SHA256="$(python3 - "${MANIFEST_FILE}" <<'PY'
import json, sys
print(json.load(open(sys.argv[1], encoding="utf-8"))["canonical_tar_sha256"])
PY
)"
ACTUAL_SHA256="$(tar --sort=name --mtime="@${SOURCE_DATE_EPOCH}" --owner=0 --group=0 --numeric-owner -C "${ROOTFS_DIR}" -cf - . | sha256sum | awk '{print $1}')"
[[ "${ACTUAL_SHA256}" == "${EXPECTED_SHA256}" ]] || fail "SHA-256 divergente: esperado ${EXPECTED_SHA256}, obtido ${ACTUAL_SHA256}"
printf 'canonical_tar_sha256=%s\n' "${ACTUAL_SHA256}"
python3 - "${ROOTFS_DIR}" <<'PY'
import os, sys
root = os.path.realpath(sys.argv[1])
required = ["bin/bash", "bin/sh", "usr/bin/python3", "usr/bin/git", "usr/bin/sqlite3", "etc/hostname"]
for rel in required:
    path = os.path.join(root, rel)
    if not os.path.exists(path):
        raise SystemExit(f"ausente: {rel}")
if os.path.islink(os.path.join(root, "etc/hostname")):
    raise SystemExit("hostname não deve ser symlink")
print("required_paths=ok")
PY
printf 'rootfs_directory=%s\n' "${ROOTFS_DIR}"
printf 'manifest=%s\n' "${MANIFEST_FILE}"
printf 'status=ok\n'
 "${APT_SOURCES_FILE}" || fail 'fontes snapshot precisam desabilitar expiração de metadados'
python3 -m json.tool "${MANIFEST_FILE}" >/dev/null || fail 'manifesto JSON inválido'
SOURCE_DATE_EPOCH="$(python3 - "${MANIFEST_FILE}" <<'PY'
import json, sys
print(json.load(open(sys.argv[1], encoding="utf-8"))["source_date_epoch"])
PY
)"
EXPECTED_SHA256="$(python3 - "${MANIFEST_FILE}" <<'PY'
import json, sys
print(json.load(open(sys.argv[1], encoding="utf-8"))["canonical_tar_sha256"])
PY
)"
ACTUAL_SHA256="$(tar --sort=name --mtime="@${SOURCE_DATE_EPOCH}" --owner=0 --group=0 --numeric-owner -C "${ROOTFS_DIR}" -cf - . | sha256sum | awk '{print $1}')"
[[ "${ACTUAL_SHA256}" == "${EXPECTED_SHA256}" ]] || fail "SHA-256 divergente: esperado ${EXPECTED_SHA256}, obtido ${ACTUAL_SHA256}"
printf 'canonical_tar_sha256=%s\n' "${ACTUAL_SHA256}"
python3 - "${ROOTFS_DIR}" <<'PY'
import os, sys
root = os.path.realpath(sys.argv[1])
required = ["bin/bash", "bin/sh", "usr/bin/python3", "usr/bin/git", "usr/bin/sqlite3", "etc/hostname"]
for rel in required:
    path = os.path.join(root, rel)
    if not os.path.exists(path):
        raise SystemExit(f"ausente: {rel}")
if os.path.islink(os.path.join(root, "etc/hostname")):
    raise SystemExit("hostname não deve ser symlink")
print("required_paths=ok")
PY
printf 'rootfs_directory=%s\n' "${ROOTFS_DIR}"
printf 'manifest=%s\n' "${MANIFEST_FILE}"
printf 'status=ok\n'
