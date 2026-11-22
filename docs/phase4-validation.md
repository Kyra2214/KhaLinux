# Validação da Fase 4 — Gerenciador de pacotes

## Entregue (código)

| Submódulo | Documento |
|---|---|
| 4.1 Manifesto | `docs/package-manifest.md` |
| 4.2 Catálogo e pesquisa | `docs/package-catalog.md` |
| 4.3 Dependências | `docs/package-dependencies.md` |
| 4.4 Instalação | `docs/package-install.md` |
| 4.5 Remoção | `docs/package-remove.md` |
| 4.6 Atualização | `docs/package-update.md` |
| 4.7 Histórico e fachada | `docs/package-manager.md` |
| 4.8 UI | `PackagesViewModel`, `PackagesScreen`, `KhaLinuxRuntimeService.packageManager()` |

## Testes

Escritos em `android/runtime/src/test/kotlin/com/khalinux/runtime/packages/` (`PackageManifestTest`, `PackageCatalogTest`, `PackageVersionTest`, `DependencyResolverTest`, `PackageManagerFlowTest`) e descobertos automaticamente por `android/runtime/test.sh` e pelo Gradle.

**Status: NÃO EXECUTADOS.** A sessão em que foram escritos não tinha `kotlinc` nem acesso à rede. Foi feita apenas checagem de balanceamento de sintaxe. Nenhum código da fase foi compilado; o módulo `:android:app` só compila no CI.

Lacunas conhecidas:
- Sem teste automatizado do download direto HTTPS no `PackageInstaller` (`ResumableDownloader` não é injetável nele).
- Sem teste de ViewModel/Compose (exigem Android).
- Sem validação em aparelho: `apt-get` dentro do PRoot, existência das versões exatas no snapshot e comportamento das ferramentas continuam não verificados; todo o catálogo está `unverified`.

## Próximas ações

1. `bash android/runtime/test.sh` num ambiente com Kotlin 1.9.24 e corrigir falhas.
2. Conferir o CI (`:android:app:assembleDebug`, testes unitários).
3. Teste físico: instalar/remover um pacote APT pela tela Pacotes.
