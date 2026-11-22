# Evidências da Fase 1

## Build executado

Em 22 de novembro de 2026, o build foi executado no sandbox Ubuntu 24.04 x86_64 com `debootstrap`, `qemu-user-static` e `binfmt-support`. O alvo foi Debian Bookworm ARM64, com `SOURCE_DATE_EPOCH=1763769600`.

O RootFS foi gerado em `distribution/build/output/rootfs-arm64/`. Esse diretório é um artefato local e permanece ignorado pelo Git. O manifesto versionado é `distribution/manifests/rootfs-arm64.json`.

## Verificações executadas

A validação estrutural passou com `distribution/scripts/verify-rootfs.sh`. O arquivo `/bin/bash` foi identificado como ELF 64-bit AArch64. Um smoke test dentro de `chroot` com QEMU confirmou `dpkg --print-architecture=arm64`, disponibilidade de Bash, Python 3, Git e SQLite, existência de `/etc/hostname` e criação do usuário de sistema `khalinux`.

A validação de configuração passou com `python3 distribution/scripts/test-build-config.py`.

## Limitações da evidência

Estas verificações foram feitas por emulação em host x86_64. Ainda não houve teste em aparelho Android 15+ ARM64, execução via PRoot, teste de ciclo de vida do aplicativo ou validação de armazenamento privado. Portanto, a Fase 1 está validada quanto à construção e aos smoke tests do RootFS no sandbox, mas a compatibilidade final no dispositivo permanece pendente para as Fases 2 e 9.
