# Validação final

A conclusão da **implementação** (47/47 submódulos) e a da **validação** são registradas separadamente. **A validação NÃO está completa**: faltam itens que dependem do Android SDK/CI e de aparelho físico.

## Executado nesta sessão (ambiente de autoria, host Linux)

| Verificação | Resultado |
|---|---|
| Compilação de **todo** o Kotlin de `android/runtime` (Fases 2–8) | OK (só avisos de depreciação). Usou o compilador embutido no Gradle local (Kotlin 2.0.21, `-language-version 1.9 -api-version 1.9 -jvm-target 17`), **não** o 1.9.24 oficial. |
| Suíte JUnit do runtime (35 classes) | **166 testes, 0 falhas** após as correções abaixo. Primeira execução: 165 testes, 12 falhas. |
| PTY nativo no host (`test-pty-native.sh`) | OK: spawn, input, resize, UTF-8, sinais. |
| `audit-security.py` (9.1) | 15 grupos, 0 erros, 0 avisos. Controle negativo (manifesto com `allowBackup=true` + senha literal em cópia) detectou 2 erros. |
| `test-build-config.py` | `config_status=ok packages=12`. |
| `bash -n` nos scripts de build/teste/PRoot | OK. |
| Parse YAML dos 4 workflows | OK (só sintaxe; nunca rodaram). |

### Falhas encontradas e corrigidas

1. **Tutor — referências de documentação** (11 testes): a regex `TutorTopic.DOC` recusava maiúsculas e rejeitava `docs/decisions/ADR-0002-…md`, derrubando o carregamento do `knowledge.tsv` real. Corrigida; a nova regex também **recusa `..`** (a antiga aceitava `docs/../x.md`). Teste de regressão `docReferenceCannotEscapeDocsDirectory` adicionado.
2. **`LabsDataTest.docsAndFixturesExist`** (1 teste): o teste supunha que todo serviço usa uma fixture em `/opt/khalinux-labs`, mas `rede-sockets` usa `python3 -m http.server 8080 --bind 127.0.0.1`. Erro do teste, não do produto; ajustado para exigir `--bind` nesses casos.
3. A auditoria passou a exigir `--bind` loopback em comandos `http.server` do `labs.json`.

## Shim de teste (transparência)

O ambiente não tem `kotlinc` nem `kotlin-test` e não há rede. Os testes usam `kotlin.test`; para rodá-los foi escrito um **shim local** (`assertEquals/True/False/Null/NotNull/Same/FailsWith`, com contratos) fora do projeto. Semântica equivalente à real, mas **não é o artefato oficial**: repetir no CI com `kotlin-test` 1.9.24 real.

## NÃO executado (pendências finais)

| Pendência | Motivo |
|---|---|
| Compilar `:android:app` (Compose, ViewModels, Service multi-sessão, `TabsBar`, `LabsScreen`, `TutorScreen`, `FilesScreen`…) e rodar `:android:app:testDebugUnitTest` | Sem Android SDK/NDK nem rede; só no CI. |
| Migração Room 2→3 e testes de DAO/ViewModel | Exigem Android; commitar `android/app/schemas` gerado no CI. |
| Build `assembleRelease` com R8 e assinatura; `apksigner verify` | Nunca gerado; regras do R8 não exercitadas (`docs/release.md`). |
| Workflows (`ci.yml`, `release-apk.yml`) | Nunca executados. |
| Revisão/sanitizers do `native_pty.c` | Só o teste funcional do host foi executado. |
| Smoke PRoot/QEMU (`test-proot-runtime.sh`) | Precisa de `proot` e `qemu-user-static`, ausentes. |
| Teste físico (Android 15+, ARM64): PRoot em `filesDir` com `targetSdk 35`, Diagnóstico, rotação, segundo plano, instalação limpa, atualização, recuperação, toolkit, laboratórios | Exige aparelho do usuário. |
| Medições de memória, inicialização e bateria (Fase 8) | Exigem aparelho. |
| Verificar ferramentas do toolkit em ARM64/PRoot | Exigem aparelho. |
| Reduzir RootFS/downloads | Exige nova build do RootFS no CI. |
| Versões do catálogo contra o snapshot APT fixo | Sem rede. |

## Conclusão

Implementação: **100% (47/47 em código)**. Validação: **parcial** — runtime validado no host (166/166), `:android:app`, release e aparelho físico pendentes. Por isso o projeto **não** é declarado 100% concluído nem estável: o artefato é a candidata `1.0.0-rc.1`.
