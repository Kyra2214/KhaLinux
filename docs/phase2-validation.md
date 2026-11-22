# Evidências da Fase 2

## Implementação

A Fase 2 adicionou um runtime Kotlin/JVM independente da UI em `android/runtime/`. `ProotConfig` valida o executável, RootFS, diretório de trabalho, bind mounts e ambiente. `ProotLauncher` monta argumentos estruturados e inicia PRoot sem shell intermediário no host. `SessionManager` controla sessões concorrentes, stdout, stderr, estados, código de saída, timeout e cancelamento.

Em host x86_64, o launcher aceita um `qemu-aarch64-static` opcional por meio de `PRoot -q`. Em Android ARM64, esse parâmetro deve ser nulo e os binários ARM64 devem ser executados diretamente.

## Testes executados

A evidência abaixo registra a execução histórica de quatro testes com Kotlin 1.3.31, JUnit 4, PRoot 5.1.0 e QEMU AArch64 no RootFS Debian ARM64. A suíte atual foi ampliada para oito testes e usa um launcher de processo simulado nos testes unitários, sem depender do RootFS ou de QEMU.

```text
JUnit version 4.13.2
....
OK (4 tests)
```

A suíte atual cobre montagem de argumentos estruturados, validação de ambiente, shell interativa conservadora, captura de stdout/código de saída, limite de sessões simultâneas, reutilização de capacidade após conclusão, falha de inicialização e timeout/cancelamento. O runner compila com Kotlin 1.9.24+ e JDK 17.

Para repetir após instalar Kotlin 1.9.24 ou superior, JDK 17, JUnit 4 e Hamcrest no host:

```bash
./android/runtime/test.sh
```

## Limitações

No registro original da Fase 2, a validação ocorreu em sandbox x86_64 com emulação e ainda não existiam integração Android, PTY interativo ou ciclo de vida de Service. Essas lacunas foram parcialmente tratadas nas fases posteriores: o projeto Gradle, o Service e o PTY nativo foram adicionados na Fase 3. Permanecem pendentes a validação da compilação Android, o instalador do RootFS/PRoot, a emulação VT/ANSI, o gerenciamento robusto de processos descendentes, a persistência Room e os testes físicos finais em Android ARM64.
