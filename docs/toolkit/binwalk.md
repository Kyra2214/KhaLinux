# binwalk

- **Grupo:** forense · **Pacote:** `binwalk` · **Comandos:** `binwalk`
- **Verificação:** `binwalk --help` · **Estado:** não verificado em ARM64/PRoot (sem evidência registrada)

Análise de assinaturas e entropia em firmware e blobs binários próprios ou autorizados.

## Uso autorizado

Não exige confirmação de escopo (análise local de arquivos), mas examine apenas evidências e arquivos que você possui ou tem autorização para analisar; preserve os originais e trabalhe em cópias.

## Limitações sem root

- Evitar a extração automática (-e) em arquivos não confiáveis.
- Utilitários de extração podem estar ausentes; não verificado em ARM64/PRoot.

## Exemplos seguros (arquivos próprios)

```text
binwalk firmware.bin
binwalk -E firmware.bin
```

## Instalação

Pelo gerenciador de pacotes (tela Pacotes) ou `apt-get install binwalk` no RootFS. Versão do catálogo lida em páginas de pacotes do Debian (bookworm); conferir no snapshot APT fixado (5.6).
