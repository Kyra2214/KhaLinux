# RED-12 — Plano de validação automatizada (execução adiada)

**Estado:** plano e integração de código registrados; nenhum build, lint, teste unitário, instrumentado ou de integração foi executado. Toda a execução está adiada até a conclusão integral dos submódulos do roadmap.

## Ambiente a registrar na fase final

- Commit exato e branch.
- Versão do JDK, Gradle wrapper, Android Gradle Plugin e Kotlin.
- Versões instaladas do Android SDK/Build Tools.
- Sistema operacional e arquitetura do host.
- Saída integral dos comandos, resultado e duração.

## Sequência de validação final

1. Confirmar tarefas disponíveis no wrapper Gradle e dependências resolvidas.
2. Executar compilação Debug do app e módulos dependentes.
3. Executar testes JVM/unitários de validadores, modelos, migração do histórico e regras de sessão.
4. Executar testes de integração do servidor HTTP com requests válidos, inválidos, limites, sessão/cookie, origem, expiração e encerramento.
5. Executar lint/checagens estáticas e revisar Manifest, assets e dependências.
6. Corrigir falhas e repetir as suítes afetadas.
7. Gerar APK Debug, calcular SHA-256 e registrar commit, versão e artefato.

## Comandos candidatos (não executados)

```bash
./gradlew tasks
./gradlew :android:app:assembleDebug
./gradlew test
./gradlew :android:app:testDebugUnitTest
./gradlew :android:app:lintDebug
sha256sum android/app/build/outputs/apk/debug/app-debug.apk
```

Confirmar nomes de tarefas disponíveis no wrapper antes de executá-las; a lista acima é roteiro, não evidência de execução.

## Matriz automatizada obrigatória

- Validação de formulário sintético: campos permitidos, formato e tamanho.
- Consentimento: aceitar todos, recusar opcionais, configuração customizada e campos extras/inválidos.
- Permissão web: allowlist de recursos/estados, payload extra, mídia/coordenadas rejeitados.
- HTTP: Host, query, path, Content-Length, Transfer-Encoding, Content-Type, headers duplicados/controle, limites e timeout.
- Sessão: cookie ausente/inválido, IP diferente, Origin/Referer externo, rate limit, expiração e stop.
- Persistência: schema v1→v2, schema atual, limites de 100 sessões/300 eventos, exclusão e sanitização.
- Relatório: versões, metadados, estados e ausência de conteúdo sensível.
- Ciclo de vida: callbacks tardios, stop repetido, perda de interface, segundo plano e recuperação interrompida.
- RED/BLUE: mesmos IDs/eventos, fatos separados de interpretações, sem alertas não observados.

## Critério de saída

Build, testes, lint e integração sem falhas; APK identificado por SHA-256 e commit. Nenhum teste físico começa antes deste portão. Esta etapa permanece pendente e não foi aprovada.