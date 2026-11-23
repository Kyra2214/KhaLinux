# Lynis

- **Grupo:** auditoria · **Pacote:** `lynis` · **Comandos:** `lynis`
- **Verificação:** `lynis --version` · **Estado:** não verificado em ARM64/PRoot (sem evidência registrada); catálogo: `known-limited`

Auditoria de segurança e hardening do RootFS do KhaLinux.

## Uso autorizado

Não exige confirmação de escopo: audita apenas o próprio RootFS local. Não use para avaliar sistemas de terceiros sem autorização.

## Limitações sem root

- /proc do Android montado (ADR-0005 revisada), mas sem acesso ao kernel: alguns testes falham ou não se aplicam.
- Audita só o RootFS, não o Android; não verificado em ARM64/PRoot.

## Exemplos seguros

```text
lynis audit system --quick
lynis --version
```

## Instalação

Pelo gerenciador de pacotes (tela Pacotes) ou `apt-get install lynis` no RootFS. Versão do catálogo lida em páginas do Debian (bookworm); conferir no snapshot APT fixado (5.6).
