# Evidências da Fase 1

## Build executado

Em 22 de novembro de 2026, às `2026-11-22T13:31:30Z`, o build foi executado no sandbox Ubuntu 24.04 x86_64 com `debootstrap`, `qemu-user-static`, `binfmt-support` e o keyring Debian. O alvo foi Debian Bookworm ARM64.

Há duas datas deliberadamente distintas: `build_invocation_utc` registra quando o comando foi executado; `source_date_epoch_utc` registra a época fixa usada para normalizar metadados, que neste artefato é `2025-11-22T00:00:00Z` (`SOURCE_DATE_EPOCH=1763769600`). A segunda data não é a data de geração física do artefato.

O RootFS foi gerado em `distribution/build/output/rootfs-arm64/`. Esse diretório é um artefato local e permanece ignorado pelo Git. O manifesto versionado é `distribution/manifests/rootfs-arm64.json`.

## Verificações executadas

A validação estrutural passou com `distribution/scripts/verify-rootfs.sh`, incluindo comparação do SHA-256 canônico `98d12425f3852441a6ea9957bb97177e3bf3502d56cac2c06e18a9b055844b85`. O arquivo `/bin/bash` foi identificado como ELF 64-bit AArch64. O smoke test reproduzível `sudo distribution/scripts/test-rootfs.sh` confirmou `dpkg --print-architecture=arm64`, Bash, Python 3, Git, SQLite, APT, `/etc/hostname` e o usuário `khalinux`.

A validação de configuração passou com `python3 distribution/scripts/test-build-config.py`. O build agora usa o snapshot fixo `https://snapshot.debian.org/archive/debian/20251122T000000Z`, verifica assinaturas com o keyring Debian e gera `distribution/manifests/base-package-versions-arm64.txt` com as versões dos pacotes solicitados.

## Limitações da evidência

Estas verificações foram feitas por emulação em host x86_64. O snapshot fixa o conjunto de índices e versões acessado pelo build, mas uma prova formal de bit a bit ainda exige duas execuções limpas comparadas; o script já normaliza metadados do tar e registra o hash. Ainda não houve teste em aparelho Android 15+ ARM64, execução via PRoot no dispositivo, teste de ciclo de vida do aplicativo ou validação de armazenamento privado.
