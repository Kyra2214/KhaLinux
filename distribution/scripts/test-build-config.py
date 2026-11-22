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
assert source_text.count("Check-Valid-Until: no") == 2, "snapshot precisa desabilitar expiração nos dois repositórios"
assert "snapshot.debian.org/archive/debian/20251122T000000Z" in source_text, "snapshot padrão não está fixado"

builder_text = builder.read_text()
assert '[[ "${DEBIAN_SNAPSHOT}" =~ ^[0-9]{8}T[0-9]{6}Z$ ]]' in builder_text, "snapshot não é validado antes de entrar na URL"
assert 'sed "s/20251122T000000Z/${DEBIAN_SNAPSHOT}/g" "${SOURCES_FILE}" > "${ROOTFS_DIR}/etc/apt/sources.list.d/debian.sources"' in builder_text, "build não aplica o snapshot selecionado às fontes APT"
assert 'rm -f "${ROOTFS_DIR}/etc/apt/sources.list.d/debian.sources"' not in builder_text, "build remove as fontes APT no final"
assert "DEBIAN_SNAPSHOT" in builder_text, "build sem snapshot configurável"
assert "--foreign" in builder_text, "build não está preparado para cross-build"
assert "sha256sum" in builder_text, "build não gera SHA-256"
assert 'ARCHIVE_FILE="${OUTPUT_DIR}/rootfs-arm64.tar.gz"' in builder_text, "build não gera arquivo TAR/GZIP para instalação"
assert "ARCHIVE_SHA256" in builder_text and "archive_sha256" in builder_text, "manifesto não registra hash do arquivo instalável"
assert "ROOTFS_RELEASE_TAG" in builder_text and "archive_url" in builder_text, "manifesto não identifica a release do RootFS"

assert "EXPECTED_SNAPSHOT" in verifier.read_text(), "verificador não compara fontes com o manifesto"
assert "ARCHIVE_SHA256" in verifier.read_text(), "verificador não valida o arquivo RootFS publicado"
assert builder.stat().st_mode & 0o111, "builder sem bit executável"
assert verifier.stat().st_mode & 0o111, "verificador sem bit executável"
assert rootfs_test.stat().st_mode & 0o111, "teste do RootFS sem bit executável"
print(f"config_status=ok packages={len(package_names)}")
