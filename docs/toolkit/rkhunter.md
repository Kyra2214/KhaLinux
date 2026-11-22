# Rootkit Hunter

- **Grupo:** auditoria · **Pacote:** `rkhunter` · **Comandos:** `rkhunter`
- **Verificação:** `rkhunter --version` · **Estado:** não verificado em ARM64/PRoot (sem evidência registrada); catálogo: `known-limited`

Verificação local de rootkits e integridade de arquivos do RootFS.

## Uso autorizado

Não exige confirmação de escopo: audita apenas o próprio RootFS local. Não use para avaliar sistemas de terceiros sem autorização.

## Limitações sem root

- /proc não montado (ADR-0005): vários testes falham ou não se aplicam.
- Resultado não prova ausência de comprometimento; não verificado em ARM64/PRoot.

## Exemplos seguros

```text
rkhunter --check --sk
rkhunter --version
```

## Instalação

Pelo gerenciador de pacotes (tela Pacotes) ou `apt-get install rkhunter` no RootFS. Versão do catálogo lida em páginas do Debian (bookworm); conferir no snapshot APT fixado (5.6).
