# Terminal utilizável (submódulo 3.4)

> Implementado, **não compilado nem executado**. Validar em aparelho físico e na suíte da Fase 3 (3.5).

- **Teclas extras** (`TerminalKeys`): ESC `0x1b`, Tab `0x09`, setas `ESC [ A–D`, Home/End `ESC [ H/F`, Ctrl+C `0x03`, Ctrl+D `0x04`. Com modificadores, setas/Home/End usam o parâmetro xterm (`ESC [ 1 ; m X`, m=3 Alt, 5 Ctrl, 7 ambos); Alt prefixa ESC nas demais. Setas sempre em modo normal (sem rastrear modo de cursor de aplicação).
- **Ctrl/Alt**: botões de armar; valem para uma tecla (da barra ou o primeiro caractere do campo de entrada) e desarmam.
- **Conflação**: snapshot de UI a cada ~33 ms no máximo (`PUBLISH_INTERVAL_MILLIS`); mensagens do app aparecem imediatamente.
- **Contrapressão**: no máximo 512 blocos de saída pendentes por sessão; se a sessão deixa de estar em execução, blocos excedentes são descartados.
- **fd do PTY**: `acquireFd/releaseFd/closeMaster` garantem que o fd só é fechado após leituras/escritas/resize em curso. Uma leitura bloqueada só termina quando o processo filho encerra (ou o PTY fecha).

Suposição: a fila limitada assume que há consumidor de eventos enquanto a sessão roda (o Service sempre consome).
