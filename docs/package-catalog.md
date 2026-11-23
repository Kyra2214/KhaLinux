# Catálogo de pacotes (submódulo 4.2)

- Dados: `distribution/packages/catalog/catalog.json` (schema 1, `catalog_version`, `packages` = manifestos do `docs/package-manifest.md`).
- Código: `PackageCatalog` e `PackageCatalogParser` em `android/runtime/.../packages/`.
- Pesquisa: texto livre (todos os termos devem casar; sem caixa/acentos) + filtros opcionais de categoria, tag, arquitetura e validação. Relevância: nome exato > prefixo > nome/título > tag > descrição.
- Invariantes: sem nomes duplicados; toda dependência deve existir no catálogo.

## Atualização 5.3

Catálogo `2026.11.22-3` inclui whatweb, sqlmap e wfuzz (categoria web). Todos `unverified`, com limitações documentadas; sem `dependencies` explícitas, pois o APT resolve Ruby/Python.

## Atualização 5.4

Catálogo `2026.11.22-4` inclui sleuthkit, binwalk, hashdeep e libimage-exiftool-perl (categoria forense), todos `unverified`.

## Atualização 5.5

Catálogo `2026.11.22-5` inclui lynis, chkrootkit e rkhunter (categoria auditoria, `known-limited`).

## Suposições

- Catálogo inicial limitado aos pacotes do RootFS base (versões do `base-package-versions-arm64.txt`), todos `unverified`; `iputils-ping` e `iproute2` registram limitações prováveis sem root, ainda não confirmadas em aparelho.
- Uma versão por pacote por vez; múltiplas versões ficam para o submódulo 4.6 se necessário.
- Decodificação do JSON (org.json) fica no adaptador do app, no submódulo 4.7.
- Nenhum teste executado ou criado; nada compilado.


## Atualização 5.6 — ADB ARM64

- Incluído `adb` 1:34.0.5-12~bpo12+1 para ARM64, fornecido pelo repositório oficial Debian bookworm-backports no snapshot fixado.
- A fonte `bookworm-backports` foi adicionada a `distribution/repositories/debian-bookworm.sources`; a versão fica instalada sob demanda pelo gerenciador de pacotes.
- A versão 34.0.5 atende ao requisito de pareamento sem fio introduzido no Platform Tools 30.0.0. O pacote permanece `unverified` até a validação final no RootFS ARM64/PRoot e nos dois aparelhos.
- A integração do KhaLinux usa o transporte sem fio; não presume acesso USB nem root no Android.

## Atualização 5.7 — provisionamento de repositórios APT adicionais

- Manifestos APT podem declarar `apt_suite` e `apt_uri` HTTPS fixados.
- Antes de atualizar o índice, o instalador provisiona a suite declarada no RootFS e só então resolve a versão exata.
- O pacote `adb` declara `bookworm-backports` no snapshot `20251122T000000Z`; isso também habilita instalação em RootFS já existente, sem exigir reinstalação.
- O RootFS novo inclui ADB na base. O manifesto de versões do RootFS só será atualizado quando o artefato for reconstruído na etapa final.
