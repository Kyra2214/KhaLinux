# Evidências da Fase 2

## Implementação

A Fase 2 adicionou um runtime Kotlin/JVM independente da UI em `android/runtime/`. `ProotConfig` valida o executável, RootFS, diretório de trabalho, bind mounts e ambiente. `ProotLauncher` monta argumentos estruturados e inicia PRoot sem shell intermediário no host. `SessionManager` controla sessões concorrentes, stdout, stderr, estados, código de saída, timeout e cancelamento.

Em host x86_64, o launcher aceita um `qemu-aarch64-static` opcional por meio de `PRoot -q`. Em Android ARM64, esse parâmetro deve ser nulo e os binários ARM64 devem ser executados diretamente.

## Testes executados

A suíte foi compilada com Kotlin 1.3.31 e executada com JUnit 4 contra o RootFS Debian ARM64 da Fase 1, usando PRoot 5.1.0 e QEMU AArch64. Resultado real:

```text
JUnit version 4.13.2
....
OK (4 tests)
```

Os testes cobrem: passagem do comando como argumento único do Bash convidado, rejeição de nomes inválidos de ambiente, captura de stdout e código de saída, timeout e cancelamento de sessão.

Para repetir após instalar as dependências do host:

```bash
./android/runtime/test.sh
```

## Limitações

Esta validação ocorreu em sandbox x86_64 com emulação. Ainda não há projeto Gradle/Android, integração com ciclo de vida de Activity/Service, PTY interativo, grupo de processos específico do Android, persistência Room ou teste em dispositivo Android 15+ ARM64. Portanto, a implementação da Fase 2 está validada como núcleo de processos e sessões, mas a integração de dispositivo permanece pendente.
