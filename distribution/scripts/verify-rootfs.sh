#!/usr/bin/env bash
set -Eeuo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ROOTFS_DIR="${ROOTFS_DIR:-${ROOT_DIR}/distribution/build/output/rootfs-arm64}"
MANIFEST_FILE="${ROOT_DIR}/distribution/manifests/rootfs-arm64.json"
fail() { printf '[khalinux-verify] erro: %s\n' "$*" >&2; exit 1; }
command -v sha256sum >/dev/null || fail 'sha256sum ausente'
command -v python3 >/dev/null || fail 'python3 ausente'
command -v tar >/dev/null || fail 'tar ausente'
command -v grep >/dev/null || fail 'grep ausente'
[[ -d "${ROOTFS_DIR}" ]] || fail "RootFS não encontrado: ${ROOTFS_DIR}"
[[ -s "${MANIFEST_FILE}" ]] || fail "manifesto não encontrado: ${MANIFEST_FILE}"
APT_SOURCES_FILE="${ROOTFS_DIR}/etc/apt/sources.list.d/debian.sources"
[[ -s "${APT_SOURCES_FILE}" ]] || fail "fontes APT ausentes no RootFS: ${APT_SOURCES_FILE}"
EXPECTED_SNAPSHOT="$(python3 - "${MANIFEST_FILE}" <<'PY'
import json, sys
print(json.load(open(sys.argv[1], encoding="utf-8"))["snapshot"])
PY
)"
grep -Fq "snapshot.debian.org/archive/debian/${EXPECTED_SNAPSHOT}" "${APT_SOURCES_FILE}" || fail "snapshot ${EXPECTED_SNAPSHOT} ausente nas fontes APT"
[[ "$(grep -Fc "${EXPECTED_SNAPSHOT}" "${APT_SOURCES_FILE}")" -eq 2 ]] || fail 'as fontes APT precisam usar o mesmo snapshot do manifesto nos dois repositórios'
[[ "$(grep -c '^Check-Valid-Until: no$' "${APT_SOURCES_FILE}")" -eq 2 ]] || fail 'fontes snapshot precisam desabilitar expiração nos dois repositórios'
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
ARCHIVE_NAME="$(python3 - "${MANIFEST_FILE}" <<'PY'
import json, sys
print(json.load(open(sys.argv[1], encoding="utf-8")).get("archive_name", ""))
PY
)"
ARCHIVE_SHA256="$(python3 - "${MANIFEST_FILE}" <<'PY'
import json, sys
print(json.load(open(sys.argv[1], encoding="utf-8")).get("archive_sha256", ""))
PY
)"
RELEASE_TAG="$(python3 - "${MANIFEST_FILE}" <<'PY'
import json, sys
print(json.load(open(sys.argv[1], encoding="utf-8")).get("release_tag", ""))
PY
)"
ARCHIVE_URL="$(python3 - "${MANIFEST_FILE}" <<'PY'
import json, sys
print(json.load(open(sys.argv[1], encoding="utf-8")).get("archive_url", ""))
PY
)"
[[ "${ARCHIVE_NAME}" == "rootfs-arm64.tar.gz" ]] || fail 'nome do arquivo RootFS inválido no manifesto'
[[ "${ARCHIVE_SHA256}" =~ ^[a-fA-F0-9]{64}$ ]] || fail 'SHA-256 do arquivo RootFS inválido no manifesto'
[[ "${RELEASE_TAG}" =~ ^[A-Za-z0-9._-]+$ ]] || fail 'tag de release inválida no manifesto'
EXPECTED_ARCHIVE_URL="https://github.com/Kyra2214/KhaLinux/releases/download/${RELEASE_TAG}/${ARCHIVE_NAME}"
[[ "${ARCHIVE_URL}" == "${EXPECTED_ARCHIVE_URL}" ]] || fail 'URL do arquivo não corresponde à release declarada'
ARCHIVE_FILE="$(dirname "${ROOTFS_DIR}")/${ARCHIVE_NAME}"
[[ -s "${ARCHIVE_FILE}" ]] || fail "arquivo RootFS não encontrado: ${ARCHIVE_FILE}"
ACTUAL_ARCHIVE_SHA256="$(sha256sum "${ARCHIVE_FILE}" | awk '{print $1}')"
[[ "${ACTUAL_ARCHIVE_SHA256}" == "${ARCHIVE_SHA256}" ]] || fail 'SHA-256 do arquivo RootFS divergente'
printf 'archive_sha256=%s\\n' "${ACTUAL_ARCHIVE_SHA256}"
printf 'rootfs_directory=%s\n' "${ROOTFS_DIR}"
printf 'manifest=%s\n' "${MANIFEST_FILE}"
printf 'status=ok\n'
