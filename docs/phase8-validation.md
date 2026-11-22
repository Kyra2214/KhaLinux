# Validação da Fase 8

**Conclusão da implementação (8.1–8.5): em código. Validação: NÃO executada** (sem `kotlinc`/rede; política do Prompt Mestre: testes só na etapa final).

Pendências transferidas para a validação final:
- Compilar e rodar `StorageHygieneTest` (8.1), `TerminalMemoryBudgetTest` (8.2; e `TerminalEmulatorTest` existente após a troca por `TerminalStyle.DEFAULT`/`TerminalCell.BLANK`), `AssetSyncStampTest` (8.3), `MemoryPressureTest` (8.4), `InstallRecoveryTest` (8.5).
- Conferir no CI de `:android:app`: `FilesViewModel`/`FilesScreen` (espaço e limpeza), `KhaLinuxRuntimeService` (storage, warm-up, recuperação, marca das fixtures), `MainActivity.onTrimMemory`, `TerminalViewModel.onTrimMemory`.
- Medições em aparelho (nenhuma feita): memória por aba (estimativa de 72 B/célula), tempo de abertura das telas, consumo com sessão ociosa.
- Não feito: reduzir o tamanho do RootFS/downloads (exige nova build do RootFS no CI e rede; ver ROADMAP).
