# Evidências da Fase 3

## Entrega

O aplicativo agora usa uma Activity hospedeira baseada em `ComponentActivity`, uma tela Jetpack Compose e `TerminalViewModel`. A Activity limita-se ao binding do `KhaLinuxRuntimeService` e entrega ao ViewModel um adaptador da camada de dados. O ViewModel publica estado em `StateFlow` e chama casos de uso de domínio por meio da porta `TerminalRuntimeGateway`; o adaptador converte essas operações para o Binder e os eventos do Service.

A tela coleta o estado com `collectAsStateWithLifecycle`, permite editar e executar um comando, cancelar uma sessão ativa, mostra o estado de conexão/runtime e rola para a saída mais recente. Activity e composables não criam `ProcessBuilder`, não montam argumentos PRoot nem executam comandos diretamente.

A tela usa o emulador VT/ANSI do runtime, com parser incremental UTF-8, cursor, SGR, cores ANSI 16/256/RGB, erase, scrollback limitado e renderização de estilos Compose. O subconjunto implementado não é um emulador xterm completo. O histórico de comandos é persistido localmente em Room, limitado a 100 entradas; a tela oferece os 12 mais recentes para reutilização. A saída do terminal não é persistida.

## Validação executada

O validador estrutural confere Activity/Compose/ViewModel/domínio/dados/Service, emulador VT/ANSI, Room, migração de configurações e controles de histórico. Os testes unitários do runtime exercitam o parser; os testes unitários do app cobrem o repositório de histórico, limite de 100 comandos, consulta dos 12 mais recentes e descarte de entradas vazias. Os testes compilam o buffer com `javac`, sem depender do Android SDK:

```text
terminal_output_buffer=ok cases=6
phase3_structure=ok compose_mvvm_domain_data_service_separated=true transcript_bounded=true
```

Para repetir:

```bash
python3 android/app/validate-phase3.py
```

Essa validação estrutural não compila nem executa a UI Compose. O código da Activity, tela e ViewModel ainda precisa de validação em toolchain Android.

## Próximas etapas e estado atualizado

O instalador RootFS usa manifesto de release fixada, download HTTPS com redirecionamentos restritos a hosts GitHub, SHA-256, limites de tamanho, extração TAR/GZIP protegida contra path traversal e links que escapem do diretório, preservação de permissões POSIX e troca por diretório temporário com recuperação da instalação anterior. O Service e a tela Compose expõem instalação assíncrona e progresso.

A primeira construção real do RootFS ARM64 passou no GitHub Actions: debootstrap com verificação GPG, geração do arquivo, SHA-256 canônico, verificação do arquivo e smoke test ARM64 via QEMU/chroot. Os artefatos foram publicados em [KhaLinux Debian Bookworm ARM64 RootFS v1](https://github.com/Kyra2214/KhaLinux/releases/tag/rootfs-bookworm-20261122-v1). O arquivo publicado tem aproximadamente 126 MB compactados.

O PRoot 5.1.107.102 foi compilado para Android/Bionic ARM64 a partir de versões e hashes fixados de PRoot, talloc e libandroid-shmem, validado como ELF AArch64 com linker `/system/bin/linker64` e publicado em [release PRoot v1](https://github.com/Kyra2214/KhaLinux/releases/tag/proot-android-5.1.107.102-v1). O workflow de build passou no CI. O instalador dentro do app valida o hash fixado do manifesto, hashes do pacote/binário, ELF ARM64/Bionic e extração de arquivo único. A UI oferece instalação separada de Debian e PRoot, com progresso. O runtime prepara binds mínimos e allowlisted para nós de dispositivo, sem expor /dev inteiro, /proc ou /sys.

O smoke host executa Bash ARM64 pelo PRoot/QEMU com os binds mínimos e confirma UID simulado, arquitetura e dispositivos. O emulador ANSI/VT básico agora processa UTF-8 incremental, SGR/cores, cursor, erase e scrollback, e a UI renderiza atributos. Ele anuncia TERM=ansi e não promete xterm completo. O shell interativo promove o Service a foreground service `specialUse`, com canal de notificação de baixa prioridade, ação para encerrar a sessão e remoção da notificação ao receber saída/falha. A Activity pode ser destruída sem encerrar a sessão enquanto o foreground service estiver ativo. CI valida as declarações do manifest e compila o APK Debug. O ciclo de vida ainda precisa de validação em aparelho real; o teste físico continua reservado para o final. Os binds PRoot não são read-only nem uma fronteira de isolamento.

A Fase 3 continua aberta. Testes em aparelho físico permanecem reservados para a etapa final, depois da implementação, auditoria, regressão e geração do APK.
