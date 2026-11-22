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
