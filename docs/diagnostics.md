# Tela de Diagnóstico (submódulo 3.3)

Ponto de teste físico: o app roda sozinho uma sequência de verificações e mostra OK/FALHA por item.
Abrir: botão **Diagnóstico** na tela principal. O botão "Copiar relatório" copia o resultado em texto.

> Estado: implementado, **não compilado nem executado** (nenhum teste roda durante os submódulos; ver ROADMAP).
> O resultado real só existe em aparelho físico (Android 15+, ARM64).

## Itens

| Item | Como é verificado | Observação |
|---|---|---|
| PRoot executa | `proot --version` no host, com `PROOT_TMP_DIR`/`TMPDIR` | Teste decisivo do possível bloqueio de execução em `filesDir` (targetSdk 35). Falha → sugere o plano B. |
| Shell no RootFS | `uname -m; id -u` | Espera `aarch64`. |
| DNS | `getent hosts debian.org` (mostra `/etc/resolv.conf` se falhar) | |
| Rede | `curl` em `http://deb.debian.org/` (HTTP simples, sem depender de CA) | Ignorado se `curl` não existir no RootFS. |
| PTY | shell interativo: `stty size` (24 80) → resize → `stty size` (30 100) → `sleep` + byte `0x03` + eco de controle | Confirma TIOCSWINSZ e Ctrl+C reais. |
| Rotação | **Manual** | O usuário gira o aparelho com a sessão aberta e marca Funcionou/Falhou. |
| Segundo plano | **Manual** | `sleep 600` com o app em segundo plano; marcar ao voltar. |

Se o PRoot não executa, os quatro itens dependentes ficam "ignorado" com o motivo.

## ProotLocator (plano B)

`ProotLocator` abstrai a origem do executável:

- `FilesDirProotLocator`: plano A, `filesDir/runtime/proot` (baixado e verificado).
- `NativeLibProotLocator`: plano B, `libproot.so` em `nativeLibraryDir` (jniLibs); define `PROOT_LOADER` se existir `libproot-loader.so`.
- `FallbackProotLocator`: tenta na ordem A → B. É o usado pelo Service.

Para adotar o plano B bastará empacotar `libproot.so` (com `useLegacyPackaging = true`) e fazer o Service preferi-lo; o restante do runtime não muda.
`ProotConfig` ganhou `tempDirectory` e `prootEnvironment` (valores padrão preservam o comportamento anterior).

## Suposições registradas

- O plano B **não** está empacotado; o localizador só passa a funcionar quando a biblioteca existir. Se o plano A existir mas falhar ao executar, a troca para o B é uma decisão a tomar após o resultado físico.
- O diagnóstico de PTY observa a saída do próprio shell (`stty size`), não o estado interno do runtime.
- Itens manuais não bloqueiam `automaticChecksPassed`.

## Código

- Runtime: `ProotLocator.kt`, `diagnostics/DiagnosticModel.kt`, `diagnostics/RuntimeDiagnostics.kt`.
- App: `DiagnosticsScreen.kt`, `TerminalViewModel` (`openDiagnostics`, `runDiagnostics`, `confirmManualDiagnostic`), `KhaLinuxRuntimeService.runDiagnostics`, porta `TerminalRuntimeGateway.runDiagnostics`.
