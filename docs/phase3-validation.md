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

## Próximas etapas

O PTY interativo e a entrada por linhas foram integrados depois desta validação inicial. Foi adicionada uma base de instalador RootFS com download HTTPS, SHA-256 esperado, limites de download/extração, extração TAR/GZIP com bloqueio de path traversal e links que escapem do RootFS, e troca por diretório temporário com recuperação da instalação anterior. O Service expõe a operação em executor de background. Testes unitários de caminhos TAR foram adicionados, mas ainda aguardam execução CI.\n\nAinda faltam a publicação/seleção dos artefatos RootFS e PRoot, UI de instalação e progresso, validação da extração com um RootFS Debian real, emulador VT/ANSI, Room/histórico, configurações e notificações de foreground Service. O ciclo de vida do Service e de processos durante mudanças de configuração também precisa de testes e refinamento.

A Fase 3 continua aberta. O build Android integrado e os testes do instalador devem passar no CI antes de considerar a integração pronta. Testes em aparelho físico permanecem reservados para a etapa final, depois da implementação, auditoria, regressão e geração do APK.
