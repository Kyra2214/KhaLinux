# RED-12 — Plano de validação automatizada (execução adiada)

**Estado:** implementação do roadmap concluída (100%) e fase final de validação iniciada. Workflow `45405105200` (commit `0c0ca2e`) aprovou os checks de host, mas a etapa Android falhou na compilação Kotlin: import Compose `weight` inacessível e literal de tab inválido no parser HTTP. Correções aplicadas nos commits `36945e3` e `fd82386`. A execução `45405903861` revelou testes antigos que ainda referenciavam `validateParticipantEvent`/`RedParticipantEvent` removidos e um retorno nullable de IPv4; testes foram reescritos para as APIs atuais e o endereço foi estreitado. Workflow `45406501672` está executando novamente testes Android, `assembleDebug` e `lintDebug`. Testes específicos do portal e testes físicos A/B ainda não foram executados.

## Execução registrada — 24/11/2026

- **Workflow final: PASS** — [45408044336](https://github.com/Kyra2214/KhaLinux/actions/runs/45408044336), commit `c9f289a`.
- **Host checks: PASS** — configuração RootFS, sintaxe shell, auditoria estática, teste nativo JNI PTY e smoke PRoot ARM64 com binds.
- **Android unit tests: PASS** — `:android:runtime:testDebugUnitTest` e `:android:app:testDebugUnitTest`, incluindo testes do seletor de interface, cookie de heartbeat e validador atual.
- **APK Debug: PASS** — `:android:app:assembleDebug`; artefato não foi publicado nem anexado.
- **Android lint: PASS** — `:android:app:lintDebug`; incompatibilidade API 33 de `InputStream.readNBytes` corrigida com leitura limitada compatível com minSdk 28.
- **Testes físicos A/B: NOT STARTED** — continuam reservados para a última etapa.
- **Validação no navegador real: PENDING** — confiança da CA, SAN IP, prompts de câmera/microfone/localização, heartbeat e continuidade durante ativação do upstream precisam ser confirmados no aparelho B.
- **Integração dedicada do portal em hardware: PENDING** — o CI compila e testa unidades, mas não substitui a sessão real A/B.

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
4. Executar testes de integração do portal HTTPS (sem fallback) e do endpoint HTTP restrito de preparação, cobrindo requests válidos/inválidos, limites, sessão/cookie, origem, expiração e encerramento.
5. Executar lint/checagens estáticas e revisar Manifest, assets e dependências.
6. Corrigir falhas e repetir as suítes afetadas.
7. Gerar APK Debug, calcular SHA-256 e registrar commit, versão e artefato.

## Comandos complementares pendentes

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
- HTTP/HTTPS: Host, query, path, Content-Length, Transfer-Encoding, Content-Type, headers duplicados/controle, limites e timeout; confirmar que login, consentimento e permissões nunca são servidos em HTTP/8080.
- Sessão: cookie ausente/inválido, IP diferente, Origin/Referer externo, rate limit, expiração e stop.
- TLS local: geração/reutilização da CA, SAN IP exato, validade, assinatura X.509, seleção de protocolos/cipher suites, cadeia do servidor, cookie Secure, Origin HTTPS e ausência de fallback HTTP.
- Preparação CA: somente GET `/`, `/ca.cer` e `/health`; limite de cabeçalho/clientes; fingerprint consistente com DER; nenhuma rota de portal, credencial ou permissão exposta em HTTP.
- Ciclo de chave TLS: chave privada da CA permanece no Android Keystore; chave efêmera do servidor é removida em stop/falha; novo IP produz certificado SAN correspondente.
- RED-15: abrir Configurações de tethering com hotspot inicialmente desligado, detectar um IPv4 novo/inequívoco, confirmar que candidatos ambíguos falham fechados, observar mudanças de upstream validado em A e verificar que o portal continua local quando o upstream desaparece; no teste físico, confirmar que B permanece associado e recebe internet após ativação do upstream. Testes JVM `RedSystemHotspotControllerTest` cobrem seleção fora do endereço default, exclusão de candidatos do upstream, fallback AP-named, ambiguidade e ausência de candidato. `RedPortalServerHeartbeatTest` cobre cookie de sessão válido, token incorreto, cookie ausente/vazio, token ausente/vazio e cookies duplicados ambíguos.
- Heartbeat: `GET /heartbeat` exige IP de sessão e cookie `kl_lab_session`, responde 204 sem payload, atualiza o último contato e não cria evento de navegação; chamadas sem cookie/IP correto são rejeitadas. Validar também pausa com página oculta e retomada ao voltar para visível; ausência de heartbeat não deve ser interpretada automaticamente como desconexão.
- Persistência: schema v1→v2, schema atual, limites de 100 sessões/300 eventos, exclusão e sanitização.
- Relatório: versões, metadados, estados e ausência de conteúdo sensível.
- Ciclo de vida: callbacks tardios, stop repetido, perda de interface, segundo plano e recuperação interrompida.
- RED/BLUE: mesmos IDs/eventos, fatos separados de interpretações, sem alertas não observados.

## Critério de saída

Build, testes, lint e integração sem falhas; APK identificado por SHA-256 e commit. Nenhum teste físico começa antes deste portão. Esta etapa permanece pendente e não foi aprovada.