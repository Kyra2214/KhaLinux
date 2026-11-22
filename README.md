# KhaLinux

Ambiente Linux ARM64 para Android, executado localmente e sem root, voltado a aprendizado, diagnóstico, auditoria autorizada e prática de cibersegurança.

> **Status:** Fase 0 — fundação do projeto. Este repositório contém documentação e estrutura inicial; ainda não há RootFS, aplicativo Android ou ferramentas funcionais implementados.

## Visão

O KhaLinux será composto por um **KhaLinux Core**, responsável por um RootFS Debian ARM64 reproduzível, e por um **KhaLinux Android**, aplicativo Kotlin/Jetpack Compose que gerenciará instalação, runtime, terminal, ferramentas, laboratórios e persistência local.

O primeiro runtime planejado é baseado em PRoot. PRoot não é uma fronteira de isolamento forte e o projeto não o apresentará como equivalente a uma máquina virtual.

## Escopo da Fase 0

- Documentação de produto, arquitetura, segurança e contribuição.
- Estrutura inicial para `distribution/` e `android/`.
- Convenções e decisões arquiteturais iniciais.
- Templates para issues e pull requests.

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

A Fase 1 começa pela definição do ambiente de build ARM64 e por um pipeline reproduzível para um RootFS Debian ARM64 minimal. Consulte [`ROADMAP.md`](ROADMAP.md), [`ARCHITECTURE.md`](ARCHITECTURE.md) e [`SECURITY.md`](SECURITY.md) antes de implementar.

## Licença

O projeto é distribuído sob Apache-2.0. Consulte [`LICENSE`](LICENSE).
