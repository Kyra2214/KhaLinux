# Arquitetura

## Objetivo

O KhaLinux será um ambiente Linux ARM64 real, executado localmente em Android 15+ sem root. A interface Android não simulará um terminal: ela orquestrará um runtime Linux verificável.

## Componentes

### KhaLinux Core

Responsável pelo sistema Linux e pelo RootFS reproduzível:

```text
distribution/
├── bootstrap/       # preparação do ambiente de build
├── base/            # configuração da base Debian ARM64
├── packages/        # seleção e metadados de pacotes
├── repositories/    # origens verificadas
├── configuration/   # configurações do ambiente
├── services/        # serviços suportados
├── scripts/         # scripts reprodutíveis
├── build/           # artefatos locais; não versionar binários grandes
└── manifests/       # manifestos e hashes versionados
```

A base inicial implementada é Debian Bookworm ARM64 minimal, construída por `debootstrap --foreign` e finalizada por QEMU AArch64 quando o host não é ARM64. A seleção versionada inclui Bash, coreutils, utilitários de rede, OpenSSH client, Python 3, Git, SQLite e APT, sempre validando necessidade e compatibilidade.

### KhaLinux Android

Aplicativo Kotlin + Jetpack Compose, organizado por responsabilidades:

```text
android/
├── app/ core/ domain/ data/ database/
├── runtime/ terminal/ installer/
├── tools/ labs/ settings/ ui/
```

A camada de UI não executará comandos diretamente. Casos de uso e interfaces de domínio conversarão com o runtime, persistência e catálogo.

## Runtime

A primeira implementação utiliza PRoot para execução sem root, com a abstração `LinuxRuntime`, `ProotLauncher` e `SessionManager` em `android/runtime/`. O núcleo separa launcher, sessões, processos, stdout/stderr, timeout e cancelamento. Pipes e PTY (JNI) estão implementados, com fila de saída limitada e fechamento seguro do fd. `ProotLocator` abstrai a origem do PRoot (plano A `filesDir`, plano B `libproot.so` em jniLibs) e `RuntimeDiagnostics` verifica PRoot, shell, DNS, rede e PTY no aparelho (tela Diagnóstico). A persistência local usa Room (histórico e configurações).

PRoot não fornece isolamento forte. O produto deverá comunicar essa limitação e não prometer segurança equivalente a VM ou contêiner privilegiado.

## Área RED

A Área RED é uma camada Android independente do runtime Linux, com catálogo versionado em `distribution/red/operations.json`, parser próprio, ViewModel/StateFlow e persistência local isolada. A Operação 01 usa o adaptador Android `LocalOnlyHotspot` e um servidor HTTP local vinculado à interface Wi-Fi do laboratório. O portal só registra eventos sintéticos; o corpo dos formulários é descartado e a página envia apenas um evento fixo de conclusão. A UI não executa lógica de rede diretamente.

A documentação operacional está em `docs/red-area.md` e `docs/red-operation-01.md`. A abertura automática do portal não é garantida pelo Android; endereço local e QR Code são as alternativas assistidas.

## Persistência

Room/SQLite deverá armazenar, com migrações versionadas: ferramentas instaladas, catálogo, histórico de comandos, progresso e exercícios, configurações, versões do RootFS, histórico de instalação e preferências. Segredos e tokens não podem entrar no histórico.

## Fluxo de instalação previsto

1. Verificar requisitos do dispositivo e espaço disponível.
2. Baixar somente por HTTPS a partir de origem permitida.
3. Validar manifesto, versão, arquitetura e SHA-256.
4. Extrair com proteção contra path traversal.
5. Inicializar diretórios e estado persistente.
6. Registrar operação e evidências de validação.

## Decisões pendentes

- Estratégia final de build e distribuição do RootFS.
- Biblioteca PTY e integração JNI, se necessária.
- Modelo de catálogo e política de repositórios.
- Matriz de dispositivos Android 15+ para testes reais.

Esses pontos serão decididos por ADRs antes de bloquear APIs públicas. Consulte [`docs/decisions/ADR-0001-foundation.md`](docs/decisions/ADR-0001-foundation.md).
