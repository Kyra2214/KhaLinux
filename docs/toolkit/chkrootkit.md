# chkrootkit

- **Grupo:** auditoria · **Pacote:** `chkrootkit` · **Comandos:** `chkrootkit`
- **Verificação:** `chkrootkit -V` · **Estado:** não verificado em ARM64/PRoot (sem evidência registrada); catálogo: `known-limited`

Verificação local de sinais de rootkits conhecidos no RootFS.

## Uso autorizado

Não exige confirmação de escopo: audita apenas o próprio RootFS local. Não use para avaliar sistemas de terceiros sem autorização.

## Limitações sem root

- /proc do Android montado (ADR-0005 revisada), mas sem acesso ao kernel: alguns testes não se aplicam.
- Resultado não prova ausência de comprometimento; não verificado em ARM64/PRoot.

## Exemplos seguros

```text
chkrootkit -q
chkrootkit -V
```

## Instalação

Pelo gerenciador de pacotes (tela Pacotes) ou `apt-get install chkrootkit` no RootFS. Versão do catálogo lida em páginas do Debian (bookworm); conferir no snapshot APT fixado (5.6).
