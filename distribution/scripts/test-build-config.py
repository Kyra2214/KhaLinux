#!/usr/bin/env python3
"""Valida a configuração versionada sem exigir privilégios ou um RootFS pronto."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
packages = ROOT / "distribution/configuration/base-packages.txt"
sources = ROOT / "distribution/repositories/debian-bookworm.sources"
builder = ROOT / "distribution/scripts/build-rootfs.sh"
verifier = ROOT / "distribution/scripts/verify-rootfs.sh"

raw_packages = [line.split("#", 1)[0].strip() for line in packages.read_text().splitlines()]
package_names = [line for line in raw_packages if line]
assert len(package_names) == len(set(package_names)), "pacotes duplicados"
required = {"apt", "bash", "coreutils", "ca-certificates", "python3", "git", "sqlite3"}
assert required <= set(package_names), "pacote base obrigatório ausente"
assert "Architectures: arm64" in sources.read_text(), "fonte não restringe a arm64"
assert "--foreign" in builder.read_text(), "build não está preparado para cross-build"
assert "sha256sum" in builder.read_text(), "build não gera SHA-256"
assert "path traversal" not in verifier.read_text().lower()  # verificação não extrai arquivos
assert builder.stat().st_mode & 0o111, "builder sem bit executável"
assert verifier.stat().st_mode & 0o111, "verificador sem bit executável"
print(f"config_status=ok packages={len(package_names)}")
