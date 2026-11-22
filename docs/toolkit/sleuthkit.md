# The Sleuth Kit (mmls, fls, icat, fsstat, istat)

- **Grupo:** forense · **Pacote:** `sleuthkit` · **Comandos:** `mmls`, `fls`, `icat`, `fsstat`, `istat`
- **Verificação:** `fls -V` · **Estado:** não verificado em ARM64/PRoot (sem evidência registrada)

Análise de partições e sistemas de arquivos em imagens de disco de evidências autorizadas.

## Uso autorizado

Não exige confirmação de escopo (análise local de arquivos), mas examine apenas evidências e arquivos que você possui ou tem autorização para analisar; preserve os originais e trabalhe em cópias.

## Limitações sem root

- Trabalha com arquivos de imagem; sem root não há acesso a dispositivos de bloco.
- Não verificado em ARM64/PRoot.

## Exemplos seguros (arquivos próprios)

```text
mmls imagem.dd
fls -r -o <offset> imagem.dd
fls -V
```

## Instalação

Pelo gerenciador de pacotes (tela Pacotes) ou `apt-get install sleuthkit` no RootFS. Versão do catálogo lida em páginas de pacotes do Debian (bookworm); conferir no snapshot APT fixado (5.6).
