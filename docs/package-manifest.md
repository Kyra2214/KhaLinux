# Manifesto de pacote (submódulo 4.1)

Modelo Kotlin em `android/runtime/src/main/kotlin/com/khalinux/runtime/packages/` (`PackageManifest`, `PackageManifestParser`) e esquema JSON em `distribution/packages/schema/package-manifest.schema.json`. Exemplo ilustrativo (fora do catálogo): `distribution/packages/examples/example-apt-package.json`.

## Campos

| Campo | Obrigatório | Regra |
|---|---|---|
| `schema` | sim | Deve ser `1`. |
| `name` / `version` | sim | `name`: `[a-z0-9][a-z0-9+._-]{0,63}`; `version` começa com dígito. |
| `title` / `description` | sim | Até 80 / 500 caracteres. |
| `category` | sim | reconhecimento, rede, web, forense, auditoria, sistema, desenvolvimento, outros. |
| `architecture` | sim | `arm64` ou `all`. Nenhuma compatibilidade é presumida. |
| `source` | sim | `debian-apt` (`apt_name`, sem URL), `github-release` ou `khalinux-repository` (`url` HTTPS, sem credenciais). |
| `download_bytes` / `installed_bytes` | condicional | Positivos; `download_bytes` obrigatório para artefato direto. |
| `verification` | sim | `sha256` obrigatório para artefato direto; `evidence` obrigatória quando `validation = verified`. |
| `validation` | sim | `verified` (evidência), `unverified` (declarado pelo upstream), `known-limited` (exige limitações). |
| `dependencies` | não | Sem duplicatas e sem autodependência; resolução no submódulo 4.3. |
| `limitations` | não | Texto livre; obrigatório para `known-limited`. |
| `requires_network` / `tags` | não | Informativos para pesquisa e UI. |

Campos desconhecidos são rejeitados pelo parser.

## Suposições registradas

- O módulo `runtime` não depende de biblioteca JSON; o parser recebe a estrutura já decodificada (`Map`/`List`). O adaptador do app (`org.json`) decodificará o texto nos submódulos seguintes.
- Pacotes `debian-apt` não carregam SHA-256 no manifesto: a integridade é garantida pelo repositório APT fixado (snapshot do RootFS); o manifesto registra apenas o nome APT.
- O catálogo real ainda não existe; será criado no submódulo 4.2. O exemplo não representa um pacote validado.
- Nenhum teste foi executado ou criado nesta etapa (política por fase); nada foi compilado (sem `kotlinc` no ambiente).
