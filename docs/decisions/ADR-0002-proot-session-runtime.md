# ADR-0002: Launcher PRoot e gerenciamento de sessões

- **Status:** aceito para a Fase 2
- **Data:** 2026-11-22

## Contexto

O aplicativo Android precisa iniciar comandos no RootFS sem root, controlar mais de uma sessão e manter a UI independente dos detalhes de processos.

## Decisão

Criar uma interface `LinuxRuntime`, uma configuração `ProotConfig`, um `ProotLauncher` e um `SessionManager`. O launcher constrói uma lista de argumentos para `ProcessBuilder`; não existe shell intermediário no host. O terminal usa Bash dentro do RootFS, portanto a interpretação de shell é intencional e limitada ao ambiente convidado.

Sessões possuem identificador, estado, timestamps, código de saída, timeout e eventos de stdout/stderr. O limite inicial é quatro sessões por runtime e deve ser ajustado quando houver métricas reais no Android.

## Segurança e limites

Caminhos de RootFS, PRoot e bind mounts são validados antes da execução. Variáveis de ambiente têm nomes validados. `-0` não representa privilégio real. PRoot não é uma fronteira de isolamento forte. O runtime não aceita nem executa comandos no shell do host.

## Consequências

O núcleo pode ser testado sem Compose e integrado posteriormente a Coroutines. Pipes são suficientes para comandos não interativos; um PTY dedicado continua necessário para a experiência de terminal da Fase 3.
