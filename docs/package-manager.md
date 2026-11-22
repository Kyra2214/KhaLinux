# Histórico e fachada do gerenciador (submódulo 4.7)

- `PackageHistoryStore`: `<epoch>\t<ação>\t<pacote>\t<versão|->\tok|fail\t<detalhe>`; só acrescenta linhas (compactação atômica descarta as mais antigas acima de `maxEntries`); detalhes sem TAB/quebra de linha, até 300 caracteres.
- `PackageManager`: ponto único de uso pela UI. Cada `install`/`update`/`remove` grava uma entrada por pacote concluído e uma entrada de falha quando a operação falha.
- `LinuxRuntimePackageCommandRunner`: adaptador `LinuxRuntime` → `PackageCommandRunner` (stdout+stderr, últimos 8000 caracteres; timeout → código -1).
- `PackageCatalogJson` (módulo app): converte `org.json` em `Map/List` para `PackageCatalogParser`.

## Suposições

- Chamadas do gerenciador bloqueiam; o app deve usá-las em thread de trabalho (wiring em 4.8).
- O histórico não guarda a saída completa dos comandos, apenas o final da mensagem de erro, para evitar vazar dados sensíveis.
- Nenhum teste executado ou criado; nada compilado (módulo app só no CI).
