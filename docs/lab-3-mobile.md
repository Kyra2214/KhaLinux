# LAB-3 — RED Mobile

**Estado:** em implementação. LAB-3.1 concluído; próximo submódulo LAB-3.2.  
**Branch:** `plan/laboratorio-operacional`.

## LAB-3.1 — Inventário de pacotes

### Operação real

O catálogo agora inclui `android.adb.package-inventory`, que executa no dispositivo selecionado:

```sh
adb -s <serial> shell pm list packages -f -i -U --show-versioncode
```

A saída vem do Package Manager Android, não de catálogo estático. O motor comum registra stdout/stderr, exit code, duração e evidência SHA-256.

### Parser

`AndroidPackageInventoryParser` transforma as linhas em registros com:

- Nome do pacote.
- Caminho de instalação reportado pelo Android.
- Instalador, quando disponível.
- UID.
- versionCode, quando fornecido pela versão do Package Manager.
- Partição de origem: `SYSTEM_PARTITION`, `DATA_PARTITION`, `APEX` ou `UNKNOWN`.

O caminho em `/data/app` é classificado como partição de dados, não como prova de que o app é de terceiros: atualizações de apps de sistema também podem residir ali. A origem do instalador e o caminho são campos independentes.

### Integração

- `AdbClient.devicePackageInventory` fornece o comando ADB.
- `RedViewModel.loadPackageInventory(assetId)` cria sessão associada ao ativo, executa a operação, lê a evidência persistida e popula `RedUiState.packageInventory`.
- O painel Android em `RedScreen` permite atualizar inventário, descobrir ADB, parear/conectar por mDNS ou endpoint manual e consultar pacotes do dispositivo.
- O painel de avisos repetitivos de autorização foi removido; estados técnicos e erros operacionais continuam visíveis.
- A lista completa permanece na evidência; a tela apresenta uma amostra de até 30 entradas para manter o painel leve.

### Pendências e validação

- LAB-3.2 implementará permissões solicitadas/concedidas/negadas, flags e AppOps por pacote.
- A interpretação do formato de `pm list packages` pode variar entre fabricantes/versões; campos opcionais permanecem nulos quando não retornados.
- Não foram executados testes, builds, compilações ou validações. Compatibilidade Android e coleta real serão verificadas somente no LAB-10.
