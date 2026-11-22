# hashdeep (hashdeep, md5deep, sha256deep)

- **Grupo:** forense · **Pacote:** `hashdeep` · **Comandos:** `hashdeep`, `md5deep`, `sha256deep`
- **Verificação:** `hashdeep -V` · **Estado:** não verificado em ARM64/PRoot (sem evidência registrada)

Hashes recursivos e auditoria de integridade contra listas de hashes conhecidos.

## Uso autorizado

Não exige confirmação de escopo (análise local de arquivos), mas examine apenas evidências e arquivos que você possui ou tem autorização para analisar; preserve os originais e trabalhe em cópias.

## Limitações sem root

- Não verificado em ARM64/PRoot.

## Exemplos seguros (arquivos próprios)

```text
hashdeep -r -c sha256 ./pasta > hashes.txt
hashdeep -r -a -k hashes.txt ./pasta
```

## Instalação

Pelo gerenciador de pacotes (tela Pacotes) ou `apt-get install hashdeep` no RootFS. Versão do catálogo lida em páginas de pacotes do Debian (bookworm); conferir no snapshot APT fixado (5.6).
