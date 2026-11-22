#!/usr/bin/env python3
"""Valida a configuração versionada sem exigir privilégios ou um RootFS pronto."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
packages = ROOT / "distribution/configuration/base-packages.txt"
sources = ROOT / "distribution/repositories/debian-bookworm.sources"
builder = ROOT / "distribution/scripts/build-rootfs.sh"
verifier = ROOT / "distribution/scripts/verify-rootfs.sh"
rootfs_test = ROOT / "distribution/scripts/test-rootfs.sh"

raw_packages = [line.split("#", 1)[0].strip() for line in packages.read_text().splitlines()]
package_names = [line for line in raw_packages if line]
assert len(package_names) == len(set(package_names)), "pacotes duplicados"
required = {"apt", "bash", "coreutils", "ca-certificates", "python3", "git", "sqlite3"}
assert required <= set(package_names), "pacote base obrigatório ausente"
source_text = sources.read_text()
assert "Architectures: arm64" in source_text, "fonte não restringe a arm64"
assert source_text.count("Check-Valid-Until: no") == 2, "snapshot antigo precisa desabilitar expiração nos dois repositórios"
builder_text = builder.read_text()
assert 'install -Dm644 "${SOURCES_FILE}" "${ROOTFS_DIR}/etc/apt/sources.list.d/debian.sources"' in builder_text, "build não instala as fontes APT"
assert 'rm -f "${ROOTFS_DIR}/etc/apt/sources.list.d/debian.sources"' not in builder_text, "build remove as fontes APT no final"
assert "snapshot.debian.org/archive/debian/20251122T000000Z" in sources.read_text(), "snapshot Debian não fixado"
assert "DEBIAN_SNAPSHOT" in builder.read_text(), "build sem snapshot configurável"
assert "--foreign" in builder.read_text(), "build não está preparado para cross-build"
assert "sha256sum" in builder.read_text(), "build não gera SHA-256"
assert "path traversal" not in verifier.read_text().lower()  # verificação não extrai arquivos
assert builder.stat().st_mode & 0o111, "builder sem bit executável"
assert verifier.stat().st_mode & 0o111, "verificador sem bit executável"
assert rootfs_test.stat().st_mode & 0o111, "teste do RootFS sem bit executável"
print(f"config_status=ok packages={len(package_names)}")
