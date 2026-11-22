#!/usr/bin/env bash
set -Eeuo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUTPUT_DIR="${OUTPUT_DIR:-${ROOT_DIR}/distribution/build/proot-output}"
MANIFEST="${ROOT_DIR}/distribution/manifests/proot-arm64.json"
fail() { printf '[khalinux-proot-verify] erro: %s\n' "$*" >&2; exit 1; }
for cmd in python3 sha256sum tar readelf; do command -v "$cmd" >/dev/null || fail "comando ausente: $cmd"; done
[[ -s "$OUTPUT_DIR/proot" && -x "$OUTPUT_DIR/proot" ]] || fail 'binário PRoot ausente ou não executável'
[[ -s "$OUTPUT_DIR/proot-arm64.tar.gz" ]] || fail 'arquivo PRoot ausente'
[[ -s "$MANIFEST" ]] || fail 'manifesto PRoot ausente'
python3 -m json.tool "$MANIFEST" >/dev/null || fail 'manifesto JSON inválido'
python3 - "$MANIFEST" <<'PY'
import json, os, re, sys
m = json.load(open(sys.argv[1], encoding="utf-8"))
assert m.get("schema") == 1
assert m.get("architecture") == "arm64"
assert m.get("runtime") == "Android Bionic"
assert m.get("proot_source") == "https://github.com/termux/proot"
assert m.get("release_tag") == os.environ.get("PROOT_RELEASE_TAG", "proot-android-5.1.107.102-v1")
assert m.get("archive_name") == "proot-arm64.tar.gz"
assert m.get("archive_url") == "https://github.com/Kyra2214/KhaLinux/releases/download/" + m["release_tag"] + "/" + m["archive_name"]
for field in ("archive_sha256", "binary_sha256", "proot_source_sha256", "talloc_source_sha256", "libandroid_shmem_source_sha256"):
    assert re.fullmatch(r"[a-f0-9]{64}", m.get(field, "")), f"{field} inválido"
PY
EXPECTED_BINARY="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["binary_sha256"])' "$MANIFEST")"
EXPECTED_ARCHIVE="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["archive_sha256"])' "$MANIFEST")"
printf '%s  %s\n' "$EXPECTED_BINARY" "$OUTPUT_DIR/proot" | sha256sum --check --status || fail 'SHA-256 do binário divergente'
printf '%s  %s\n' "$EXPECTED_ARCHIVE" "$OUTPUT_DIR/proot-arm64.tar.gz" | sha256sum --check --status || fail 'SHA-256 do arquivo divergente'
readelf -h "$OUTPUT_DIR/proot" | grep -q 'AArch64' || fail 'binário não é ELF ARM64'
DYNAMIC_INFO="$(readelf -d "$OUTPUT_DIR/proot")"
if grep -Eqi 'lib(talloc|android-shmem)\.so' <<<"$DYNAMIC_INFO"; then
  fail 'dependência auxiliar dinâmica não foi incorporada estaticamente'
fi
[[ "$(tar -tzf "$OUTPUT_DIR/proot-arm64.tar.gz")" == "proot" ]] || fail 'arquivo deve conter somente o executável PRoot'
printf 'proot_arm64=ok\n'
printf 'binary_sha256=%s\narchive_sha256=%s\n' "$EXPECTED_BINARY" "$EXPECTED_ARCHIVE"
