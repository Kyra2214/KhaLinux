# Memória e saída do terminal (8.2)

**Estado: concluído em código; nada compilado/executado** (sem `kotlinc`/rede). Sem medição em aparelho.

Auditoria (código lido, não medido): o emulador já limitava o histórico (1000 linhas + tela), o `SessionManager` já limita eventos de dados pendentes (512) e sessões/abas (4). Problemas encontrados e tratados:

- **Custo por coluna ignorado:** o histórico fixo de 1000 linhas custava dezenas de MB por aba em 400 colunas. `TerminalMemoryBudget` calcula o histórico por aba para o pior caso (todas as abas) caber em ~24 MB; `TerminalTabs` aplica na criação da aba e em cada `resizeAll`, e `TerminalEmulator.setScrollbackLimit` descarta linhas excedentes na hora. Em 24×80 o histórico continua 1000.
- **Alocação por célula:** cada `TerminalCell()` criava um `TerminalStyle` novo e `snapshot().plainText()`/`displayRange()` criavam um por célula examinada a cada atualização de tela. Agora há `TerminalStyle.DEFAULT` e `TerminalCell.BLANK` compartilhados (imutáveis), reduzindo memória e coleta de lixo a cada atualização.
- **Suposição:** `BYTES_PER_CELL = 72` é estimativa conservadora, não medida. Orçamento de 24 MB também é um valor inicial.
- **Não alterado:** o texto plano completo (`plainText`) continua sendo gerado a cada atualização para a UI; reduzi-lo exigiria mudar a UI e fica registrado como possível melhoria após medição em aparelho.
- Validação pendente: `TerminalMemoryBudgetTest` (escrito, não executado); `TerminalEmulatorTest` existente deve continuar passando.
