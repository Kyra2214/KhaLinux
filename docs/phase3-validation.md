# Evidências da Fase 3

## Entrega

O aplicativo agora usa uma Activity hospedeira baseada em `ComponentActivity`, uma tela Jetpack Compose e `TerminalViewModel`. A Activity limita-se ao binding do `KhaLinuxRuntimeService` e entrega ao ViewModel um adaptador da camada de dados. O ViewModel publica estado em `StateFlow` e chama casos de uso de domínio por meio da porta `TerminalRuntimeGateway`; o adaptador converte essas operações para o Binder e os eventos do Service.

A tela coleta o estado com `collectAsStateWithLifecycle`, permite editar e executar um comando, cancelar uma sessão ativa, mostra o estado de conexão/runtime e rola para a saída mais recente. Activity e composables não criam `ProcessBuilder`, não montam argumentos PRoot nem executam comandos diretamente.

O transcript do terminal usa `TerminalOutputBuffer`, limitado a 64 Ki caracteres em memória. Quando o limite é ultrapassado, o trecho mais antigo é descartado e um marcador informa a truncagem. O buffer é volátil: não adiciona histórico em disco nem persiste comandos ou tokens.

## Validação executada

O validador estrutural confere Activity/Compose/ViewModel/domínio/dados/Service e executa seis testes host-side do buffer: transcript curto, truncagem incremental, chunk maior que o limite, limite padrão, limpeza e configuração inválida. Os testes compilam o buffer com `javac`, sem depender do Android SDK:

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

O PRoot 5.1.107.102 foi compilado para Android/Bionic ARM64 a partir de versões e hashes fixados de PRoot, talloc e libandroid-shmem, validado como ELF AArch64 com linker `/system/bin/linker64` e publicado em [release PRoot v1](https://github.com/Kyra2214/KhaLinux/releases/tag/proot-android-5.1.107.102-v1). O workflow de build passou no CI. O instalador dentro do app agora valida o hash fixado do manifesto, hashes do pacote/binário, ELF ARM64/Bionic e extração de arquivo único; sua integração e testes CI ainda estão em andamento.

Ainda faltam concluir e validar a instalação PRoot pelo app, validar execução e bind mounts, implementar emulador VT/ANSI, persistência Room/histórico, configurações e refinar notificações/ciclo de vida do foreground Service.

A Fase 3 continua aberta. Testes em aparelho físico permanecem reservados para a etapa final, depois da implementação, auditoria, regressão e geração do APK.
