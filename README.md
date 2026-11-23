# KhaLinux

Ambiente Linux ARM64 para Android, executado localmente e sem root, cuja finalidade principal é executar um laboratório operacional de cibersegurança RED/BLUE/PURPLE. O Linux, o runtime Android, o terminal e o toolkit formam a infraestrutura do laboratório.

> **Status:** LAB-0 concluído; LAB-1 parcialmente implementado; LAB-2 com implementação concluída e validação final pendente. O próximo módulo é RED Mobile (LAB-3). APK Release e testes físicos continuam reservados à etapa final (ver [`ROADMAP.md`](ROADMAP.md) e [`docs/roadmap-area-red-completa.md`](docs/roadmap-area-red-completa.md)).

## Visão

O KhaLinux será composto por um **KhaLinux Core**, responsável por um RootFS Debian ARM64 reproduzível, e por um **KhaLinux Android**, aplicativo Kotlin/Jetpack Compose que gerenciará instalação, runtime, terminal, ferramentas, laboratórios e persistência local.

O primeiro runtime planejado é baseado em PRoot. PRoot não é uma fronteira de isolamento forte e o projeto não o apresentará como equivalente a uma máquina virtual.

## Implementado no repositório

- Documentação de produto, arquitetura, segurança e contribuição.
- Estrutura inicial para `distribution/` e `android/`.
- Convenções e decisões arquiteturais iniciais.
- Templates para issues e pull requests.
- Scripts reproduzíveis para Debian Bookworm ARM64 via `debootstrap --foreign` e QEMU.
- Manifesto versionado com SHA-256 canônico do RootFS gerado.
- Verificações estruturais e smoke test em `chroot` ARM64 emulado.
- Runtime Kotlin com launcher PRoot, sessões, stdout/stderr, timeout e cancelamento.
- Motor operacional com fila, sessões, eventos persistidos, evidências SHA-256 e CLI `khalab`.
- Inventário Android local/remoto, ADB Wireless debugging, descoberta mDNS, pareamento/conexão e operações ADB integradas ao motor.
- `KhaLinuxRuntimeService` com Binder local e separação UI/runtime.
- Activity Compose, ViewModel com `StateFlow`, casos de uso de terminal, porta de domínio/adaptador do Service e transcript limitado em memória.

Implementação e validação são estados separados: o código pode estar implementado enquanto os testes e a evidência final permanecem pendentes para LAB-10.

## Estrutura

```text
.
├── android/          # Aplicativo Android e runtime Kotlin
├── distribution/     # Construção e metadados do Linux
├── docs/decisions/   # Registros de decisões arquiteturais
├── .github/          # Templates e automações futuras
├── ARCHITECTURE.md
├── CONTRIBUTING.md
├── LICENSE
├── ROADMAP.md
└── SECURITY.md
```

## Princípios

1. Compatibilidade ARM64 e execução sem root como requisitos de primeira classe.
2. Segurança verificável: downloads HTTPS, SHA-256, manifestos versionados e extração protegida.
3. Laboratórios vulneráveis somente em localhost por padrão e com escopo autorizado.
4. Commits pequenos, documentação honesta e testes realmente executados.
5. Nenhuma credencial no APK ou no repositório.

## Instalação e uso

- [`docs/install-guide.md`](docs/install-guide.md): requisitos (Android 15+, ARM64, sem root), instalação, primeira abertura, atualização e problemas comuns.
- [`docs/user-guide.md`](docs/user-guide.md): terminal, abas, scripts, arquivos, pacotes, ferramentas, laboratórios, tutor e diagnóstico.
- Na primeira abertura o app baixa e instala PRoot e Debian ARM64 sozinho, com barra de progresso (0–100%) e download retomável; releases são imutáveis e os hashes são pinados no app ([`docs/installation-flow.md`](docs/installation-flow.md)). O build usa `./gradlew`.

## Estado atual

Fases 0–9 implementadas em código (candidata `1.0.0-rc.1`, ver [`RELEASE_NOTES.md`](RELEASE_NOTES.md)). Validação parcial: o runtime foi compilado e testado no host (166 testes, 0 falhas) e o PTY nativo passou; o módulo `:android:app`, o build release e o app em aparelho físico (use **Diagnóstico**) ainda não foram validados ([`docs/final-validation.md`](docs/final-validation.md)). Auditoria estática em [`docs/security-audit.md`](docs/security-audit.md). Documentos de validação: [`docs/phase1-validation.md`](docs/phase1-validation.md) a [`docs/phase8-validation.md`](docs/phase8-validation.md). Consulte também [`ARCHITECTURE.md`](ARCHITECTURE.md) e [`SECURITY.md`](SECURITY.md).

## Licença

O projeto é distribuído sob Apache-2.0. Consulte [`LICENSE`](LICENSE).
