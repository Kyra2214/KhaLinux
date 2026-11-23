# RESUME_SESSION

- Último ZIP: KhaLinux_CHECKPOINT_049.zip (F-01 apt com sufixo +bN; ver docs/plano-de-correcao.md) — anteriores: 048 (M-01 copiar entrada/saída), KhaLinux_CHECKPOINT_047.zip (corrige extração de symlinks do RootFS + UI nova; NÃO compilado nesta sessão — rodar `:android:runtime` testes e `assembleDebug` no CI)
- Concluídos: **implementação 47/47 (Fases 0–9 em código)**. Validação **parcial** (`docs/final-validation.md`): runtime 166/166 testes no host, auditoria 0 erros, PTY nativo OK.
- Próximo: no CI rodar `:android:app:testDebugUnitTest`/`assembleDebug`, commitar `android/app/schemas`, gerar o release assinado (`docs/release.md`, R8), conferir `kotlin-test` real; no aparelho: Diagnóstico (PRoot em `filesDir`), instalação limpa, atualização, recuperação, toolkit, labs e medições. Só então promover `1.0.0-rc.1` → `1.0.0`.
- Como reproduzir a suíte local: compilador embutido do Gradle (`kotlin-compiler-embeddable` 2.0.21, `-language-version 1.9`) + JUnit/commons do `lib/` do Gradle + shim de `kotlin.test` (descrito em `docs/final-validation.md`); ou, com kotlinc 1.9.24, `SKIP_NATIVE_PTY=1 bash android/runtime/test.sh`.
- Suposições: nameservers 1.1.1.1/8.8.8.8; percentual de extração aproximado; plano B (`libproot.so`) não empacotado; `/proc` montado, `/sys` não (ADR-0005 revisada); hashes pinados nas releases `-v1`; fixtures dos labs em `/opt/khalinux-labs`; serviços de lab iniciados manualmente; no máximo 4 abas/sessões; tutor sem modelo (ADR-0006).
- Pendências abertas: nenhuma ferramenta do toolkit verificada em aparelho; commitar esquemas Room; testes de ViewModel/Room (precisam de Android); teste físico via Diagnóstico; revisão de `native_pty.c`; reduzir RootFS/downloads exige nova build no CI.
- Bloqueios: PRoot em filesDir (targetSdk 35) só em aparelho físico; `kotlinc`/rede ausentes; o ZIP de entrada não contém `.git` (sem histórico Git nesta sessão).


## Área RED — checkpoint 2026-11-22

- Branch: `plan/area-red-wifi-publico`; não mesclada em `main`.
- Roadmap mestre: `docs/roadmap-area-red-completa.md`.
- Operação 01: `docs/roadmap-area-red-estagio-01-wifi-publico.md`.
- Implementação em código: navegação RED, catálogo asset/parser, ficha/tutorial, ViewModel, sessões/eventos, persistência isolada, histórico limitado, recuperação de sessão interrompida, hotspot Android LocalOnlyHotspot, portal HTTP local vinculado à interface Wi-Fi, QR Code/endereço, formulário sintético com envio fixo sem campos, fluxo genérico de rede social, permissões simuladas sem acesso ao aparelho, relatório Markdown e exportação manual.
- Arquivos principais: `android/app/src/main/kotlin/com/khalinux/android/red/`; catálogo em `distribution/red/operations.json`.
- Documentação: `docs/red-area.md`, `docs/red-operation-01.md`; arquitetura, guia e changelog atualizados.
- **Validação pendente:** nenhuma compilação, teste automatizado, verificação de CI ou teste físico foi executado nesta implementação, conforme a regra do projeto. Validar integração Compose, parser, persistência, estados/eventos, permissões/hotspot, vínculo de interface, servidor HTTP, QR, segundo aparelho e exportação somente no fechamento.
- Limitações conhecidas: LocalOnlyHotspot e interface IPv4 variam por Android/fabricante; abertura automática do portal não é garantida; se não for possível identificar a interface Wi-Fi, o portal não abre em todas as interfaces como fallback.
- Próxima ação: executar a validação final acumulada da Área RED, corrigir achados, gerar checkpoint completo e só então solicitar autorização separada para eventual merge em `main`.


## Área RED — revisão de escopo 2026-11-23

- Roadmap mestre revisado: `docs/roadmap-area-red-completa.md`.
- Operação 01 revisada: `docs/roadmap-area-red-estagio-01-wifi-publico.md`.
- Documentação operacional atualizada: `docs/red-area.md`, `docs/red-operation-01.md`, `ARCHITECTURE.md`, `CHANGELOG.md`.
- Direção: laboratório pessoal com dois aparelhos Android dedicados/controlados pelo instrutor. Aparelho A = operador, hotspot, servidor e painel; aparelho B = participante.
- Portal HTTP local previsto na porta TCP 8080, com cenário de autenticação local fictícia e cenário de acesso direto sem login.
- A aula deve demonstrar também navegação, links e redirecionamentos depois da conexão; o painel deve mostrar requisições reais e campos sintéticos efetivamente recebidos durante a sessão.
- Interface do participante natural, sem avisos grotescos. Identificação do laboratório e observabilidade no painel do instrutor. Vídeos mostram comportamento e resultados, não código.
- Permissões reais devem ser solicitadas no próprio aparelho B pelo modo participante Android. HTTP em IP de LAN não concede APIs nativas de câmera/microfone/localização; o navegador tem limitações de contexto seguro. Login e Wi-Fi não concedem permissões.
- RED e BLUE devem compartilhar cenário/evidências e manter perspectivas pedagógicas separadas.
- **Lacuna atual:** a implementação existente ainda envia apenas evento fixo no formulário e usa permissões simuladas. A revisão feita nesta sessão foi documental; os novos fluxos ainda não foram implementados.
- Próxima etapa: iniciar RED-0/RED-1 do roadmap revisado e avançar sequencialmente até RED-12. Não executar testes, builds, validações ou testes físicos durante a implementação. Reservar RED-13 para validação final.
- Branch permanece `plan/area-red-wifi-publico`; não mesclar em `main` sem autorização explícita.

## Execução contínua RED — atualização 2026-11-23

- Branch obrigatória: `plan/area-red-wifi-publico`; não usar outra branch e não mesclar em `main`.
- Instrução atual: executar roadmap continuamente, sem parar entre submódulos; **ZIP/checkpoints desativados** por instrução explícita do usuário.
- Roadmap RED: `docs/roadmap-area-red-completa.md`; Operação 01: `docs/roadmap-area-red-estagio-01-wifi-publico.md`.
- RED-0 (escopo/arquitetura): concluído documentalmente.
- RED-1 (fundação/catálogo): concluído em código e documentação. Arquivos alterados: `RedModels.kt`, `RedSessionStore.kt`, `RedViewModel.kt`, `distribution/red/operations.json`, roadmap e changelog.
- Progresso RED: 2/14 submódulos (14%); próximo: RED-2 — hotspot e conexão de dois aparelhos.
- RED-1 criou estados de rede, eventos tipados, observação HTTP, sequência de eventos e metadados HTTP persistíveis; nenhum valor de formulário é persistido.
- Ainda não implementado: bind robusto da rede hotspot, porta 8080, portal com login local/acesso direto, submissão HTTP de campos sintéticos, links/navegação, modo participante Android, painel de observabilidade completo e integração BLUE.
- **Não executar** testes unitários, integração, instrumentados, builds, validações ou testes físicos durante a implementação. Transferir tudo para RED-13.
- Sem ZIP. Não fazer merge. Continuar automaticamente no RED-2.

## Execução contínua RED — checkpoint de implementação

- Branch: `plan/area-red-wifi-publico`. Não trocar de branch e não fazer merge.
- ZIP/checkpoints: **ignorados/desativados** conforme instrução atual do usuário.
- RED-0 a RED-6 concluídos em documentação/código; progresso: **50% (7/14 submódulos)**.
- RED-1: modelos de estado/eventos, observação HTTP e catálogo atualizados.
- RED-2: LocalOnlyHotspot, estado e dados expostos pelo Android integrados; participante fica ativo após primeira requisição HTTP. Android não expõe lista universal de clientes.
- RED-3: servidor HTTP porta 8080, bind em IPv4 Wi-Fi/AP, limites e timeout; painel mostra metadados de requisição.
- RED-4: portal fictício natural com modo login local e modo acesso direto, páginas internas e redirect.
- RED-5: POST recebe somente `demo_id=aluno-...` e `demo_code=LAB-0000`; valores ficam apenas em memória no painel, sem persistência.
- RED-6: navegação/links locais e redirect 302 registrados como eventos.
- Próximo submódulo: RED-7 — modo participante Android e permissões reais.
- Arquivos principais alterados: `RedModels.kt`, `RedSessionStore.kt`, `RedViewModel.kt`, `RedHotspotController.kt`, `RedPortalServer.kt`, `RedScreen.kt`, `distribution/red/operations.json`, documentos RED/arquitetura/changelog.
- Pendências: modo participante no aparelho B; câmera/microfone/localização por diálogos reais e prévia local; seletor SAF; integração BLUE; tutorial final.
- Não executar testes, builds, validações ou testes físicos durante implementação. Transferir tudo para RED-13.

## Execução contínua RED — atualização após RED-7

- Branch: `plan/area-red-wifi-publico`; sem ZIP e sem merge.
- RED-0 a RED-7 concluídos em código/documentação: **57% (8/14)**.
- RED-7 adicionou `RedParticipantScreen.kt`, CameraX, permissões CAMERA/RECORD_AUDIO/ACCESS_COARSE_LOCATION, câmera em prévia local, medidor de áudio sem gravação, localização exibida localmente e seletor SAF.
- Pareamento: código temporário gerado ao iniciar sessão no aparelho A; participante informa URL local e token e pode registrar estado no endpoint `/participant/event`. O endpoint aceita apenas kind/result allowlist.
- Acesso à câmera/microfone/localização não é concedido pelo portal web ou login; as solicitações acontecem no app participante no aparelho B.
- Próximo: RED-8 — painel de observabilidade completo e linha do tempo unificada.
- Validação (incluindo CameraX, permissões e dois aparelhos) continua adiada para RED-13; não executar testes/builds agora.

## Execução contínua RED — atualização após RED-8

- RED-0 a RED-8 concluídos em código/documentação: **64% (9/14)**.
- RED-8 ampliou painel: status hotspot/servidor/participante, interface/porta, lista de requisições, headers allowlist, valores sintéticos efêmeros, permissões e timeline.
- RedRequestObservation.safeHeaders mantém somente Host, User-Agent, Accept, Accept-Language, Content-Type e Referer sem query. Cookie e Authorization não entram no modelo.
- Próximo submódulo: RED-9 — histórico e relatórios.
- Sem ZIP, sem testes, sem builds e sem merge.

## Execução contínua RED — atualização após RED-9

- RED-0 a RED-9 concluídos em código/documentação: **71% (10/14)**.
- RED-9 registra o modo do portal na sessão, mantém histórico limitado (100 sessões/300 eventos), marca sessões órfãs como interrompidas e gera relatório com timeline e metadados sem valores sensíveis.
- Próximo submódulo: RED-10 — integração pedagógica RED ↔ BLUE.
- Busca de código no estado padrão não encontrou uma tela BLUE existente; verificar branch/estrutura e criar módulo BLUE integrado sem presumir que já existe.
- Sem ZIP, sem testes/builds, sem merge.

## Execução contínua RED — atualização após RED-10

- RED-0 a RED-10 concluídos em código/documentação: **79% (11/14)**.
- Área BLUE criada em android/app/src/main/kotlin/com/khalinux/android/blue/BlueViewModel.kt e BlueScreen.kt.
- Integração Compose em MainActivity.kt, KhaLinuxTerminalScreen.kt e HomeScreen.kt; menu Mais contém BLUE.
- RedLiveEvidenceStore compartilha eventos/requisições/campos sintéticos temporários. Alternância RED → BLUE não encerra hotspot nem sessão; retorno reabre a tela da operação.
- Documentação: docs/blue-area.md, docs/red-area.md, docs/red-operation-01.md, docs/user-guide.md e ARCHITECTURE.md.
- Próximo: RED-11 — tutorial e documentação de aula.
- Sem ZIP, sem testes/builds, sem merge.

## Execução contínua RED — atualização após RED-11

- RED-0 a RED-11 concluídos em código/documentação: **86% (12/14)**.
- RED-11: guia do instrutor em docs/red-instructor-guide.md, roteiro rápido dentro da tela RED, user guide e documentos RED/BLUE atualizados.
- Próximo: RED-12 — fechamento da implementação e congelamento.
- Ainda pendente após RED-12: RED-13 validação final (testes, builds e testes físicos). Não antecipar.
- Sem ZIP, sem testes/builds, sem merge.

## Execução contínua RED — fechamento RED-12

- Branch: plan/area-red-wifi-publico.
- RED-0 a RED-12 concluídos em código/documentação: **93% (13/14)**.
- RED-12 congelou implementação e consolidou a lista de validação em docs/red-implementation-freeze.md.
- Próxima e única fase: RED-13 — validação final. Implementação não deve ser reaberta salvo correção de falha identificada nessa fase.
- Testes/builds/validações não foram executados durante a implementação. Agora a fase final está autorizada pelo Prompt Mestre.
- ZIP/checkpoints desativados; branch atual é a única branch autorizada; sem merge para main.

## RED-13 — estado da validação final

- Implementação RED-0 a RED-12 concluída em código/documentação.
- CI automatizado aprovado: workflow 45099843828, commit cf1c16d68d6706f912ee856d3753efd935077a26.
- Passaram: host checks, auditoria de segurança, JNI PTY, PRoot/ARM64, testes unitários runtime/app, assembleDebug e upload do APK de inspeção.
- Corrigido durante a validação: allowlist de permissões Android e expressão condicional do servidor HTTP.
- Pendência obrigatória: teste físico com dois aparelhos Android, incluindo hotspot, conexão, portal 8080, login sintético/acesso direto, permissões, relay e alternância RED/BLUE.
- Pendência adicional: compatibilidade por fabricante/versão e regressão física.
- Não marcar 100% enquanto a validação física não for concluída.
- Branch permanece plan/area-red-wifi-publico; sem ZIP e sem merge.
