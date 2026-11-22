# Runtime PRoot — Fase 2

Este módulo contém o núcleo Kotlin/JVM do runtime Linux. Ele não depende de Compose. `LinuxRuntime` expõe sessões, eventos, entrada interativa e redimensionamento sem acoplar a UI aos detalhes de processo.

## Launchers e sessões

`ProotConfig` valida o executável, o RootFS, diretórios de trabalho, bind mounts e nomes de variáveis de ambiente. `ProotLauncher` monta uma lista de argumentos estruturados e nunca concatena comandos para um shell do host. Comandos individuais usam `SystemProcessLauncher` e pipes. Solicitações interativas usam um `TerminalProcessLauncher` independente.

No Android, `AndroidPtyProcessLauncher` usa a ponte JNI em `src/main/cpp/native_pty.c`. O código C prepara argumentos e ambiente no processo pai, chama `forkpty` e executa o vetor sem shell intermediário via `execve`. O master do PTY transporta stdin/stdout/stderr combinados; resize aplica `TIOCSWINSZ`. O cancelamento sinaliza o grupo de processos da sessão. A biblioteca nativa só é carregada quando o launcher interativo é usado.

`SessionManager` captura stdout/stderr como bytes, expõe estado e eventos, aplica timeout, encaminha entrada e aguarda o esvaziamento das saídas antes do evento final. A tela atual oferece entrada de linhas para um Bash interativo e mantém os comandos não interativos em pipes.

## Limitações explícitas

PRoot não é isolamento forte. A opção `-0` apenas apresenta identidade root dentro do ambiente PRoot e não concede privilégios ao processo no Android.

O PTY implementado é o transporte de terminal, não um emulador VT/ANSI. o ambiente convidado usa `TERM=ansi` (subconjunto VT/ANSI do emulador do runtime, não xterm completo); teclas especiais chegam pela barra extra (`TerminalKeys`). As dimensões iniciais são 24x80; a UI ainda não calcula e envia o tamanho real da área visível. Validação com NDK, aparelho Android ARM64, instalação do runtime e ciclo de vida Android continuam pendentes.

## Testes host-side

`test-pty-native.sh` compila a JNI com GCC/JDK e exercita spawn, entrada/saída, UTF-8, resize e sinalização em Linux. Isso valida o contrato POSIX/JNI no host, não substitui o build Android NDK. `test.sh` compila todo o runtime (inclui `install/` e `diagnostics/`) com `kotlinc` e executa todas as classes `*Test` com JUnit (`SKIP_NATIVE_PTY=1` pula a JNI). Dependências: kotlinc 1.9.24+, JDK 17+, junit4, hamcrest-core, commons-compress/io/lang3. A cobertura inclui `ProotLocator`, `RuntimeDiagnostics`, `TerminalKeys`, download retomável e a fila limitada.
