# ExifTool

- **Grupo:** forense · **Pacote:** `libimage-exiftool-perl` · **Comandos:** `exiftool`
- **Verificação:** `exiftool -ver` · **Estado:** não verificado em ARM64/PRoot (sem evidência registrada)

Leitura e edição de metadados de arquivos de imagem, documento e mídia.

## Uso autorizado

Não exige confirmação de escopo (análise local de arquivos), mas examine apenas evidências e arquivos que você possui ou tem autorização para analisar; preserve os originais e trabalhe em cópias.

## Limitações sem root

- Versão do bookworm antiga (12.57); não verificado em ARM64/PRoot.

## Exemplos seguros (arquivos próprios)

```text
exiftool foto.jpg
exiftool -ver
```

## Instalação

Pelo gerenciador de pacotes (tela Pacotes) ou `apt-get install libimage-exiftool-perl` no RootFS. Versão do catálogo lida em páginas de pacotes do Debian (bookworm); conferir no snapshot APT fixado (5.6).
