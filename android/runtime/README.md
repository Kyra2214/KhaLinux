# Runtime PRoot — Fase 2

Este módulo contém o núcleo Kotlin/JVM do runtime Linux. Ele não depende de Compose e não acessa Android UI. A futura camada Android poderá adaptar `LinuxRuntime` para Coroutines, WorkManager e ciclo de vida.

## Responsabilidades implementadas

`ProotConfig` valida o executável, o RootFS, diretórios de trabalho, bind mounts e nomes de variáveis de ambiente. `ProotLauncher` monta uma lista de argumentos estruturados e inicia `ProcessBuilder` sem concatenar um comando de host. A string de terminal é passada como um único argumento para o Bash **dentro** do RootFS.

`SessionManager` limita sessões concorrentes, captura stdout e stderr em bytes, expõe eventos, registra estado, aplica timeout e permite cancelamento. O cancelamento usa `destroy()` e fallback para `destroyForcibly()`; a implementação Android deverá complementar isso com um grupo de processos quando a API disponível permitir.

## Limitações explícitas

PRoot não é isolamento forte. O runtime não promete equivalência a máquina virtual ou contêiner. A opção `-0` apenas apresenta identidade root dentro do ambiente PRoot e não concede privilégios ao processo no Android.

O módulo ainda não implementa PTY interativo; `ProcessBuilder` fornece streams pipe. O suporte a terminal interativo, teclado auxiliar, múltiplas sessões persistidas e integração com ciclo de vida Android será desenvolvido nas próximas etapas.

## Execução local

Os testes usam `/usr/bin/proot`, `/usr/bin/qemu-aarch64-static` e o RootFS gerado na Fase 1 porque o sandbox é x86_64. Em Android ARM64, `qemuExecutable` deve ser nulo: o PRoot executará diretamente os binários ARM64 do RootFS. Em um projeto Gradle Android, estes arquivos devem ser incluídos como source set Kotlin e testados com JUnit.
