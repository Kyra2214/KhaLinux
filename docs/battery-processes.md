# Bateria e processos (8.4)

**Estado: concluído em código; nada compilado/executado** (sem `kotlinc`/rede). Sem medição de consumo em aparelho.

Auditoria (código lido):
- O Service só vira foreground durante instalação ou sessão interativa e sai de foreground (`stopForeground` + `stopSelf`) quando a última sessão termina; `onDestroy` fecha o `SessionManager`, que cancela todas as sessões (grupos de processos via `ProcessGroupManagedProcess`) e encerra os pools. Nada disso foi alterado.
- A saída do PTY tem contrapressão (512 blocos) e conflação de atualização de tela (3.4); o `scheduler` do `SessionManager` só existe para timeouts. Não há wakelock, polling nem alarmes no app.
- **Decisão (sem encerramento automático por ociosidade):** um shell parado não consome CPU, e fechar sozinho uma sessão do usuário perderia trabalho em andamento. A notificação persistente continua oferecendo "encerrar sessão". Se medições em aparelho mostrarem custo, um tempo-limite opcional pode ser avaliado.

Implementado:
- `MemoryPressure.shouldTrimBackgroundTabs(level)`: só `RUNNING_CRITICAL` (15) e `MODERATE`/`COMPLETE` (60/80) disparam; `MainActivity.onTrimMemory` → `TerminalViewModel.onTrimMemory`.
- `TerminalTabs.trimBackground()` reduz ao mínimo (100 linhas) o histórico das abas **em segundo plano**, descartando as linhas mais antigas delas (a aba ativa e a saída em andamento não mudam); `select()` devolve à aba o limite normal (o que foi descartado não volta). Trade-off: sob pressão crítica o sistema pode encerrar o processo inteiro, o que perderia tudo.
- Validação pendente: `MemoryPressureTest` (escrito, não executado).
