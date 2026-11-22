# KhaLinux

Ambiente Linux ARM64 para Android, executado localmente e sem root, voltado a aprendizado, diagnóstico, auditoria autorizada e prática de cibersegurança.

> **Status:** Fase 1 — build do RootFS Debian ARM64 validado no sandbox por emulação. Ainda não há runtime PRoot, aplicativo Android ou toolkit de ferramentas implementados.

## Visão

O KhaLinux será composto por um **KhaLinux Core**, responsável por um RootFS Debian ARM64 reproduzível, e por um **KhaLinux Android**, aplicativo Kotlin/Jetpack Compose que gerenciará instalação, runtime, terminal, ferramentas, laboratórios e persistência local.

O primeiro runtime planejado é baseado em PRoot. PRoot não é uma fronteira de isolamento forte e o projeto não o apresentará como equivalente a uma máquina virtual.

## Escopo entregue até a Fase 1

- Documentação de produto, arquitetura, segurança e contribuição.
- Estrutura inicial para `distribution/` e `android/`.
- Convenções e decisões arquiteturais iniciais.
- Templates para issues e pull requests.
- Scripts reproduzíveis para Debian Bookworm ARM64 via `debootstrap --foreign` e QEMU.
- Manifesto versionado com SHA-256 canônico do RootFS gerado.
- Verificações estruturais e smoke test em `chroot` ARM64 emulado.

Nenhuma funcionalidade é considerada implementada sem código, testes executados, evidência e documentação correspondente.

## Estrutura

```text
.
├── android/          # Aplicativo Android; scaffold da Fase 0
├── distribution/     # Construção e metadados do Linux; scaffold da Fase 0
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

## Próximo passo

A Fase 2 começa pela implementação do runtime PRoot, PTY e gerenciamento de sessões no Android. A validação em aparelho Android 15+ ARM64 ainda é pendente. Consulte [`ROADMAP.md`](ROADMAP.md), [`ARCHITECTURE.md`](ARCHITECTURE.md), [`SECURITY.md`](SECURITY.md) e [`docs/phase1-validation.md`](docs/phase1-validation.md).

## Licença

O projeto é distribuído sob Apache-2.0. Consulte [`LICENSE`](LICENSE).
