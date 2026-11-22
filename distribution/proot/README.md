# PRoot para Android ARM64

O KhaLinux compila o PRoot para Android/Bionic usando o código-fonte oficial mantido pelo projeto Termux. O binário não é baixado de um repositório de terceiros.

## Fontes fixadas

| Componente | Versão | Origem | SHA-256 do fonte |
|---|---:|---|---|
| PRoot | 5.1.107.102 | [termux/proot](https://github.com/termux/proot) | `30d12a6e2cc4147123756ff58edea262d3083bbc238c12d0ab9b39e3d1c40852` |
| talloc | 2.5.0 | [Samba](https://www.samba.org/ftp/talloc/) | `912afa237510ae542a7733998eb18a12bcda35ab6729c8e2ddb43e8d0ebab007` |
| libandroid-shmem | 0.7 | [termux/libandroid-shmem](https://github.com/termux/libandroid-shmem) | `1e5ff8459bc0a8c229dd8a94b27d119987e09ef3414331c2b5ebfff20b98e867` |

As versões e hashes seguem as receitas de pacote do Termux. O PRoot é GPL-2.0-or-later; talloc é GPL-3.0; libandroid-shmem é BSD-3-Clause. A combinação é distribuída sob os termos GPL-3.0-or-later aplicáveis ao trabalho combinado, preservando os avisos de copyright e as licenças de cada componente.

## Build

O workflow `.github/workflows/proot-release.yml` usa Android NDK 27.0.12077973 e API 35. O script:

1. baixa somente as versões acima e confere SHA-256 antes de extrair;
2. compila talloc e libandroid-shmem como arquivos estáticos;
3. compila o PRoot ARM64 com o loader incorporado ao executável;
4. verifica ELF AArch64 e ausência de dependências dinâmicas para talloc/libandroid-shmem;
5. gera pacote e manifesto com hashes do binário e do arquivo.

O executável continua dinamicamente ligado às bibliotecas de sistema do Android/Bionic. Não é um binário Linux/glibc.

## Limites

- O build ainda precisa passar no CI antes de ser considerado artefato utilizável.
- Compilação cruzada não comprova execução em aparelho Android.
- PRoot não é VM nem sandbox de segurança; processos continuam sujeitos às permissões do UID do app.
- O RootFS Debian é independente do PRoot e já é distribuído em release própria.
