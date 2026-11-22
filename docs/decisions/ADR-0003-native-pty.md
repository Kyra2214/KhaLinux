# ADR-0003: PTY nativo por JNI para sessões interativas

- **Status:** aceito para a Fase 2/3
- **Data:** 2026-11-22

## Contexto

O runtime existente usa `ProcessBuilder` e pipes. Isso atende a comandos não interativos, mas não fornece terminal controlador, entrada contínua, eco de linha nem redimensionamento de janela. `forkpty` e `openpty` constam no Bionic desde a API 23; o `minSdk` do projeto é 35, portanto ambos estão disponíveis na faixa suportada [1]. `forkpty` combina a criação do pseudo-terminal, o fork e a preparação do terminal controlador e entrega o descritor master ao processo pai [2]. O NDK documenta que chamadas de libc compatíveis com o `minSdk` podem ser usadas diretamente [3].

O Termux confirma a viabilidade do padrão Android com uma ponte JNI que cria subprocessos associados a PTY e expõe descritor, espera, redimensionamento e fechamento [4]. O `pty4j` declara suporte a Linux, macOS, Windows e FreeBSD, sem Android [5].

## Decisão

Adicionar um backend C/JNI pequeno, próprio do KhaLinux, que usa `forkpty` e executa o vetor de argumentos recebido com `execve`. O backend aceita apenas argumentos estruturados; não introduz shell no host. A implementação prepara os vetores de argumentos e ambiente antes do fork, liga stdin/stdout/stderr do processo filho ao lado escravo e oferece operações para ler, escrever, redimensionar, sinalizar e aguardar o processo. Antes de `execve`, o filho restaura handlers padrão e desbloqueia sinais para não herdar o estado de sinais do ART/JVM [6].

O `ProotLauncher` mantém `SystemProcessLauncher` com pipes para solicitações comuns e recebe um launcher PTY separado para sessões marcadas como interativas. Assim, testes host-side e comandos não interativos preservam o comportamento atual. O Service Android seleciona o backend PTY apenas para abrir o Bash interativo. A saída do PTY é um fluxo combinado, conforme a semântica de terminal.

A primeira entrega cobre transporte de bytes e entrada de linha pelo PTY. Não é ainda um emulador de terminal VT/ANSI: aplicativos de tela cheia, movimentação de cursor, cores e teclas especiais dependem de uma etapa posterior de emulação/renderização. Até lá o ambiente convidado mantém `TERM=dumb` para evitar anunciar recursos de terminal não implementados.

## Consequências e validação

O código nativo permanece restrito ao módulo `android/runtime`, carregado somente ao selecionar o launcher interativo. `minSdk 35` excede a disponibilidade mínima da API 23. A validação host-side compila a camada JNI contra os headers do JDK e testa spawn, entrada/saída, tamanho da janela e encerramento; isso não substitui compilação com NDK nem execução em Android ARM64, que continuam pendentes.

## Referências

[1]: https://android.googlesource.com/platform/bionic/+/HEAD/docs/status.md "Android Bionic status: forkpty e openpty na API 23"
[2]: https://man.archlinux.org/man/forkpty.3.en "forkpty: criação do processo com pseudo-terminal"
[3]: https://developer.android.com/ndk/guides/stable_apis "Android NDK stable APIs"
[4]: https://github.com/termux/termux-app/blob/master/terminal-emulator/src/main/java/com/termux/terminal/JNI.java "Termux JNI para subprocessos PTY"
[5]: https://github.com/JetBrains/pty4j "Pty4J: plataformas suportadas"
[6]: https://android.googlesource.com/platform/bionic/+/refs/heads/main/libc/include/bits/signal_types.h "Definições de sinais do Bionic"
