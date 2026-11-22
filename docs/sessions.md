# Múltiplas sessões (7.1)

**Estado: concluído em código; nada compilado/executado nesta sessão** (sem `kotlinc` e sem rede; `:android:app` só é validável no CI).

## Modelo
- `runtime/tabs/TerminalTabs.kt`: `TerminalTabs` (até `DEFAULT_MAX_TABS` = 4, igual ao limite de sessões do `SessionManager`) e `TerminalTab` (emulador próprio, `sessionId`, `interactive`, `starting`, `sendingInput`, `draft`, título de até 24 caracteres sem caracteres de controle).
- Sempre há ao menos uma aba e uma ativa. Fechar a aba ativa seleciona a vizinha à esquerda; fechar devolve a sessão a cancelar (`TabCloseResult`).
- Sem dependência de Android; testado por `TerminalTabsTest`.

## App
- `TerminalViewModel`: os campos de sessão do `TerminalUiState` (`sessionId`, `isInteractive`, `isStarting`, `isSendingInput`, `terminalSnapshot`, `output`) derivam sempre da aba ativa (`refreshLocked`). Cada sessão é iniciada com um observador preso ao id da aba; abas em segundo plano continuam recebendo saída, mas só a aba ativa publica snapshots (~33 ms).
- Fechar uma aba cancela suas tarefas e a sessão; se a aba some durante a inicialização, a sessão recém-criada é cancelada.
- Tamanho (linhas/colunas) e fonte continuam globais: o redimensionamento vale para todos os emuladores e PTYs interativos.
- `KhaLinuxRuntimeService`: conjunto de sessões interativas ativas; o foreground continua enquanto houver sessão ativa, em início ou instalação; a ação "Encerrar sessões" cancela todas.
- UI: `TabsBar` (selecionar; tocar na aba ativa renomeia; "Fechar aba"; "+ Aba").

## Suposições e limites
- Uma sessão (comando ou shell) por aba; o limite do runtime (4) também é consumido por pacotes/laboratórios, e a falha de início aparece na aba.
- Abas e transcrições não persistem após fechar o app; sessões não sobrevivem à morte do processo.
- Entrada interativa continua fora do histórico de comandos (senhas).
