# KhaLinux Core — Fase 1

A Fase 1 constrói uma base Debian Bookworm ARM64 minimal. O build usa `debootstrap --foreign` para permitir host x86_64 e finaliza o segundo estágio com `qemu-aarch64-static`.

## Dependências do host

Em Ubuntu/Debian, instale `debootstrap`, `qemu-user-static`, `binfmt-support`, `git`, `python3` e `ca-certificates`. O build exige root porque cria um sistema de arquivos e executa o segundo estágio em `chroot`.

## Build

Na raiz do repositório:

```bash
sudo apt-get update
sudo apt-get install debootstrap qemu-user-static binfmt-support
sudo SOURCE_DATE_EPOCH=1763769600 KHALINUX_ALLOW_CLEAN=1 \
  ./distribution/scripts/build-rootfs.sh
```

`SOURCE_DATE_EPOCH` deve ser fixado em builds reproduzíveis. O script gera o RootFS em `distribution/build/output/rootfs-arm64/` e o manifesto em `distribution/manifests/rootfs-arm64.json`. O RootFS não deve ser commitado; apenas scripts, configuração e manifestos devem ser versionados.

## Verificação

A configuração pode ser validada sem root:

```bash
python3 distribution/scripts/test-build-config.py
```

Após um build real:

```bash
sudo ./distribution/scripts/verify-rootfs.sh
```

A verificação confirma JSON válido, caminhos essenciais e presença dos componentes mínimos. Ela não substitui testes em aparelho ARM64 nem prova isolamento de segurança.

## Limitações conhecidas

O host atual pode ser x86_64, portanto o resultado é cross-built e finalizado por emulação. A compatibilidade final deve ser validada em Android ARM64 real. A lista inicial é deliberadamente pequena; ferramentas de cibersegurança serão avaliadas em fases posteriores.
