# Evidências da Fase 3

## Entrega

Foi criado um projeto Gradle Android com os módulos `android:runtime` e `android:app`. O `KhaLinuxRuntimeService` hospeda o `SessionManager` e expõe um Binder local; a `MainActivity` faz bind no Service, exibe o estado do runtime, envia comandos por `startShell` e renderiza eventos de stdout, stderr, saída e falha.

A Activity não cria `ProcessBuilder`, não monta argumentos PRoot e não executa comandos diretamente. O Service resolve os caminhos privados `filesDir/runtime/proot` e `filesDir/runtime/rootfs-arm64`, recusando iniciar se o runtime ainda não estiver instalado.

## Validação disponível

O SDK Android não estava instalado no sandbox durante esta implementação. Por isso, o APK não foi declarado compilado. A separação estrutural pode ser validada sem SDK:

```bash
python3 android/app/validate-phase3.py
```

O build Android deverá ser executado em um ambiente com SDK 35, Build Tools compatíveis e Gradle/Android Gradle Plugin disponíveis.

## Limitações

A interface é uma Activity mínima baseada em Views para manter a integração verificável sem adicionar dependências de Compose antes do SDK estar disponível. Navegação completa, Dashboard, Room, WorkManager, instalação do RootFS, PTY interativo, notificações de foreground Service e teste em aparelho Android 15+ continuam pendentes.

A Fase 3, portanto, está implementada como integração inicial Activity/Service e scaffold Gradle, mas ainda não atende ao critério de APK funcional integrado em dispositivo real.
