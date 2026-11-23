# Roadmap do KhaLinux

> **Correção de direção — 2026-11-23:** o produto principal é o laboratório operacional RED/BLUE/PURPLE. O Debian ARM64, PRoot, terminal, APT, toolkit e integração Android são a infraestrutura que prepara e executa esse laboratório. O motor de exercícios e os conteúdos guiados da antiga Fase 6 são complementares; não substituem operações reais.
>
> Roadmap operacional vigente: [docs/roadmap-area-red-completa.md](docs/roadmap-area-red-completa.md). A operação Wi-Fi foi reclassificada como protótipo e seu plano operacional está em [docs/roadmap-area-red-estagio-01-wifi-publico.md](docs/roadmap-area-red-estagio-01-wifi-publico.md).
>
> Ordem: LAB-0 auditoria e inventário → LAB-1 motor de execução → LAB-2 conexão/inventário Android → LAB-3 RED Mobile → LAB-4 RED Network → LAB-5 RED Wi-Fi → LAB-6 RED Web → LAB-7 BLUE operacional → LAB-8 PURPLE → LAB-9 interface/relatórios → LAB-10 validação integrada, testes físicos e release.

## Progresso de execução

**Infraestrutura e módulos-base:** implementados em código em grande parte; validação integrada e física ainda pendentes. **Laboratório operacional:** replanejado em 2026-11-23; não está concluído. **Validação final: PARCIAL** — CI nº 284 passou no commit `49648c9a7bed05fddce2053aae28ebab0aa9902c`: testes unitários Android, compilação APK Debug, verificações host, auditoria estática, JNI/PTy e smoke test PRoot. APK Release assinado e testes físicos ainda pendentes; detalhes em `docs/final-validation.md`.

**Branch da revisão operacional:** `plan/laboratorio-operacional` · **Laboratório operacional:** 23% (LAB-0 concluído; LAB-1 parcial com duas pendências técnicas; LAB-2 implementação concluída, validação final pendente). **Próximo submódulo:** LAB-3 — RED Mobile. Não gerar ZIP/checkpoints.

| # | Submódulo | Estado |
|---|---|---|
| 0 | Fase 0 — Fundação | [x] concluído |
| 1 | Fase 1 — Linux Base (RootFS) | [x] concluído |
| 2 | Fase 2 — Runtime Android (código) | [x] concluído (execução em aparelho pendente) |
| 3.1 | Fase 3 — Instalação automática com porcentagem | [x] concluído (sem testes executados; ver `docs/installation-flow.md`) |
| 3.2 | Fase 3 — Correções de bugs sem aparelho (rotação, histórico, concorrência, home, UI) | [x] concluído; itens remanescentes (corrida de fd no PTY, strings.xml, bind de /proc) movidos para 3.4/3.5 |
| 3.3 | Fase 3 — Tela de Diagnóstico (ponto de teste físico) | [x] concluído (sem compilar/executar; ver `docs/diagnostics.md`) |
| 3.4 | Fase 3 — Terminal utilizável (teclas, conflação de saída) | [x] concluído (sem compilar/executar; ver `docs/terminal-usability.md`) |
| 3.5 | Fase 3 — Release imutável, CI, documentação e testes | [x] concluído (59 testes do runtime + PTY nativo executados no host; módulo `:android:app` só validável no CI; ver `docs/phase3-validation.md`) |
| 4.1 | Fase 4 — Modelo de manifesto de pacote (arquitetura, origem, versão, tamanho, verificação, limitações) | [x] concluído (sem compilar/executar; ver `docs/package-manifest.md`) |
| 4.2 | Fase 4 — Catálogo, pesquisa e categorias | [x] concluído (sem compilar/executar; ver `docs/package-catalog.md`) |
| 4.3 | Fase 4 — Resolução de dependências | [x] concluído (sem compilar/executar; ver `docs/package-dependencies.md`) |
| 4.4 | Fase 4 — Instalação (download verificado, registro de estado) | [x] concluído (sem compilar/executar; ver `docs/package-install.md`) |
| 4.5 | Fase 4 — Remoção | [x] concluído (sem compilar/executar; ver `docs/package-remove.md`) |
| 4.6 | Fase 4 — Atualização | [x] concluído (sem compilar/executar; ver `docs/package-update.md`) |
| 4.7 | Fase 4 — Histórico auditável, fachada `PackageManager` e adaptadores (org.json, LinuxRuntime) | [x] concluído (sem compilar/executar; ver `docs/package-manager.md`) |
| 4.8 | Fase 4 — UI do gerenciador e validação da fase (suíte de testes) | [x] concluído em código (testes escritos, **não executados**: sem `kotlinc`/rede nesta sessão; CI cobre via Gradle; ver `docs/phase4-validation.md`) |
| 5.1 | Fase 5 — Infra do toolkit: descritores de ferramenta, grupos, política de uso autorizado, docs por ferramenta | [x] concluído (sem compilar/executar; ver `docs/toolkit.md`) |
| 5.2 | Fase 5 — Reconhecimento e rede (pacotes no catálogo, descritores, docs) | [x] concluído (nmap, whois, traceroute; versões conferidas só em packages.debian.org; sem compilar/executar) |
| 5.3 | Fase 5 — Web | [x] concluído (whatweb, sqlmap, wfuzz; nikto fora por estar em non-free; versões conferidas só em páginas do Debian; sem compilar/executar) |
| 5.4 | Fase 5 — Forense | [x] concluído (sleuthkit, binwalk, hashdeep, exiftool; foremost fora: não confirmado; versões conferidas só em páginas do Debian; sem compilar/executar) |
| 5.5 | Fase 5 — Auditoria | [x] concluído (lynis, chkrootkit, rkhunter; `known-limited`; john/hydra fora de escopo; versões conferidas só em páginas do Debian; sem compilar/executar) |
| 5.6 | Fase 5 — Verificação ARM64 por ferramenta, wiring no app (asset, guarda, tela) e validação da fase | [x] concluído (88 testes do runtime executados no host; `:android:app` não compilado aqui; nenhuma ferramenta verificada em aparelho; ver `docs/phase5-validation.md`) |
| 6.1 | Fase 6 — Modelo de laboratório/exercício, catálogo e parser (escopo só loopback) | [x] concluído (compilado no host; sem testes por política; ver `docs/labs.md`) |
| 6.2 | Fase 6 — Motor de exercícios (sessão, verificação de resposta por hash, dicas, tentativas) | [x] concluído (compilado no host; sem testes por política; ver `docs/labs.md`) |
| 6.3 | Fase 6 — Pontuação e progresso persistido | [x] concluído (**não compilado**: sem `kotlinc`/rede nesta sessão; sem testes por política; ver `docs/labs.md`) |
| 6.4 | Fase 6 — Exercícios Linux complementares (conteúdo) | [x] concluído (3 laboratórios, 13 exercícios em `labs.json`; hashes e respostas conferidos no host com os próprios comandos; **não compilado/testado no Kotlin**; ver `docs/labs.md`) |
| 6.5 | Fase 6 — Exercícios de rede complementares (localhost) | [x] concluído (3 laboratórios, 14 exercícios; só loopback; 404 do `http.server` conferido no host; **não compilado/testado no Kotlin**; ver `docs/labs.md`) |
| 6.6 | Fase 6 — Exercícios Web complementares (serviços vulneráveis só em loopback) | [x] concluído (3 laboratórios, 12 exercícios; fixtures Python em `distribution/labs/fixtures/web/` executadas e conferidas com curl no host; **não compilado/testado no Kotlin**; ver `docs/labs.md`) |
| 6.7 | Fase 6 — Exercícios de forense complementares (conteúdo) | [x] concluído (3 laboratórios, 12 exercícios; evidências determinísticas geradas por script e respostas conferidas no host; **não compilado/testado no Kotlin**; ver `docs/labs.md`) |
| 6.8 | Fase 6 — Desafios progressivos/trilhas, UI, asset `labs.json` e validação da fase | [x] concluído em código (testes escritos, **não executados**: sem `kotlinc`/rede; `:android:app` não compilado; ver `docs/phase6-validation.md`) |
| 7.1 | Fase 7 — Múltiplas sessões (abas de terminal: criar, trocar, renomear, fechar; sessão, saída e rascunho por aba; foreground com várias sessões) | [x] concluído em código (`TerminalTabs` + teste escrito, **não compilado/executado**: sem `kotlinc`/rede; `:android:app` só validável no CI; ver `docs/sessions.md`) |
| 7.2 | Fase 7 — Personalização (5 temas de cores do terminal, fonte e tamanho já existentes; persistência Room v3 com migração 2→3) | [x] concluído em código (`TerminalThemesTest` escrito, **não compilado/executado**; esquema Room v3 só gerado no CI; ver `docs/customization.md`) |
| 7.3 | Fase 7 — Favoritos (comandos e ferramentas favoritas, persistidos em `favorites.tsv`) | [x] concluído em código (`FavoritesStoreTest` escrito, **não compilado/executado**; ver `docs/favorites.md`) |
| 7.4 | Fase 7 — Scripts (biblioteca em `/home/khalinux/scripts`: criar, editar, apagar, executar na aba ativa) | [x] concluído em código (`ScriptStoreTest` escrito, **não compilado/executado**; ver `docs/scripts.md`) |
| 7.5 | Fase 7 — Gerenciador de arquivos (navegar, ver/editar texto, criar pasta/arquivo, renomear, apagar em `/home/khalinux`, confinado à raiz) | [x] concluído em código (`HomeFileManagerTest` escrito, **não compilado/executado**; ver `docs/file-manager.md`) |
| 7.6 | Fase 7 — Relatórios (relatório Markdown de laboratórios e ferramentas, salvo em `/home/khalinux/reports`) | [x] concluído em código (`ReportBuilderTest` escrito, **não compilado/executado**; ver `docs/reports.md`) |
| 7.7 | Fase 7 — Integração entre ferramentas (saída do terminal → nota Markdown em `reports/`; "Usar no terminal" no toolkit respeitando o gate de escopo) | [x] concluído em código (`OutputNoteTest` escrito, **não compilado/executado**; ver `docs/tool-integration.md`) |
| 7.8 | Fase 7 — Assistente didático local (tutor offline baseado em regras, ADR-0006) e validação da fase | [x] concluído em código (`TutorEngineTest` escrito, **não compilado/executado**; a suíte das Fases 6 e 7 foi transferida para a validação final; ver `docs/tutor.md`) |
| 8.1 | Fase 8 — Higiene de armazenamento (limpeza de resíduos de instalação, relatório de espaço) | [x] concluído em código (`StorageHygieneTest` escrito, **não executado**; ver `docs/storage.md`) |
| 8.2 | Fase 8 — Memória e saída do terminal (auditar e limitar scrollback, buffers e histórico) | [x] concluído em código (`TerminalMemoryBudgetTest` escrito, **não executado**; sem medição em aparelho; ver `docs/memory.md`) |
| 8.3 | Fase 8 — Inicialização (carregamento preguiçoso/em segundo plano de catálogos e assets, cache de parse) | [x] concluído em código (`AssetSyncStampTest` escrito, **não executado**; sem medição em aparelho; ver `docs/startup.md`) |
| 8.4 | Fase 8 — Bateria e processos (ociosidade, foreground service, limpeza de grupos de processos) | [x] concluído em código (auditoria: ciclo de foreground/processos já correto; adicionada redução de histórico em pressão crítica de memória; `MemoryPressureTest` escrito, **não executado**; sem medição de bateria; ver `docs/battery-processes.md`) |
| 8.5 | Fase 8 — Recuperação (instalação interrompida, restauração de `.previous`, sessões órfãs) e validação da fase | [x] concluído em código (`InstallRecoveryTest` escrito, **não executado**; validação da fase transferida para a final; ver `docs/recovery.md`, `docs/phase8-validation.md`) |
| 9.1 | Fase 9 — Auditoria de segurança estática (script `audit-security.py`, revisão manual, endurecimento do manifesto, passo no CI) | [x] concluído em código (script **não executado**; ver `docs/security-audit.md`) |
| 9.2 | Fase 9 — Documentação de instalação e uso (guia de instalação, guia do usuário, README) | [x] concluído (`docs/install-guide.md`, `docs/user-guide.md`; descreve comportamento em código, não validado em aparelho) |
| 9.3 | Fase 9 — APK release e assinatura (signingConfig por variáveis de ambiente, R8/ProGuard, workflow de release) | [x] concluído em código (nenhum build de release executado; ver `docs/release.md`) |
| 9.4 | Fase 9 — Notas de versão e preparação da primeira versão (1.0.0-rc.1 candidata; 1.0.0 estável só após a validação final) | [x] concluído (`RELEASE_NOTES.md`; `versionName` padrão `1.0.0-rc.1`, `versionCode` 2) |

O detalhamento de 3.1–3.5 está em [`docs/plano-de-correcao.md`](docs/plano-de-correcao.md).

Este roadmap é a referência oficial de evolução do projeto. Uma fase só pode ser considerada concluída quando seus critérios de aceite forem atendidos com implementação, testes executados, evidência e documentação.

## Fase 0 — Fundação

**Estado:** em preparação/concluída como scaffold documental.

- Repositório público e documentação inicial.
- Estruturas `distribution/`, `android/` e `docs/decisions/`.
- Convenções de contribuição, segurança e decisões técnicas.
- Templates de issues e pull requests.

**Critério:** repositório organizado e sem arquivos fictícios apresentados como funcionais.

## Fase 1 — Linux Base

**Estado:** primeira reconstrução real no GitHub Actions concluída com sucesso. Debian Bookworm ARM64 passou pela verificação de hash canônico, verificação do arquivo e smoke test ARM64 via QEMU/chroot. O pacote, manifesto e lock de pacotes foram publicados em [rootfs-bookworm-20261122-v1](https://github.com/Kyra2214/KhaLinux/releases/tag/rootfs-bookworm-20261122-v1). A base está entregue; reprodutibilidade bit-a-bit entre duas reconstruções independentes ainda não foi comprovada.

- Preparar build ARM64.
- Construir Debian ARM64 minimal e gerar RootFS.
- Configurar Bash, coreutils, utilitários de rede, APT, usuários e diretórios.
- Criar scripts de inicialização e manifesto versionado.
- Gerar e validar SHA-256.
- Testar comandos básicos e integridade da imagem.

**Critério:** RootFS reproduzível, inicializável e identificado corretamente.

## Fase 2 — Runtime Android

**Estado:** launcher Kotlin, sessões, PTY JNI, entrada/resize/cancelamento e grupos de processos implementados. Testes host, JNI/PTy e APK Debug passaram no CI. O RootFS ARM64 agora está publicado. PRoot 5.1.107.102 foi compilado com Android NDK 27/API 35, validado como ELF AArch64/Bionic e publicado em [release v1](https://github.com/Kyra2214/KhaLinux/releases/tag/proot-android-5.1.107.102-v1). O instalador no app foi integrado ao Service/UI, valida manifesto e hashes fixados, extrai um único ELF ARM64/Bionic e passou nos testes unitários e no build APK Debug do CI. A execução no Android ainda não foi validada.

- Implementar launcher PRoot, PTY, sessões e gerenciamento de processos.
- Tratar stdout/stderr, timeout, cancelamento e erros.
- Validar Android 15+, ARM64, armazenamento privado e ciclo de vida.

**Critério:** executar Linux real no Android sem root, dentro das limitações documentadas.

## Fase 3 — Aplicativo Android

**Estado:** Activity/Service, Compose/MVVM, PTY JNI, instaladores RootFS/PRoot, binds mínimos, emulador VT/ANSI básico, histórico local Room e configurações persistentes do terminal implementados. O emulador cobre UTF-8 incremental, cursor, SGR, cores 16/256/RGB, erase e scrollback, sem alegar compatibilidade xterm completa. Histórico mantém até 100 comandos e expõe os 12 mais recentes; fonte e dimensões do terminal são persistidas com migração Room. CI executa validação host, smoke PRoot/QEMU, testes unitários do runtime e do repositório de histórico, além da compilação APK Debug. O shell interativo agora promove o Service a foreground service special-use, exibe notificação persistente com ação para encerrar a sessão e remove a notificação quando o processo termina. CI valida as declarações e compila o APK Debug. Fase 3 concluída em código (3.1–3.5): instalação automática, terminal com teclas extras, tela de Diagnóstico, releases imutáveis e Gradle wrapper. Ainda faltam a conferência do CI do módulo `:android:app`, testes de integração no dispositivo e testes físicos finais (Diagnóstico).

- Concluir projeto Kotlin com Jetpack Compose, MVVM, Clean Architecture e Coroutines.
- Implementar navegação, dashboard, instalação, terminal, configurações, Room e histórico.

**Critério:** APK funcional integrado ao ambiente Linux.

## Fase 4 — Gerenciador de pacotes

- Catálogo, pesquisa, categorias, dependências, instalação, remoção, atualização e histórico.
- Manifestos com arquitetura, origem, versão, tamanho, verificação e limitações.

**Critério:** instalar e remover ferramentas de maneira confiável e auditável.

## Fase 5 — Cyber Toolkit

**Estado:** concluída em código (5.1–5.6); suíte do runtime executada no host (88 testes); ferramentas não verificadas em aparelho (ver `docs/phase5-validation.md`).

- Integrar ferramentas de reconhecimento, rede, Web, forense e auditoria.
- Validar individualmente ARM64 e limitações sem root.
- Criar testes automatizados e documentação por ferramenta.

**Critério:** ferramentas funcionais e verificadas; nenhuma compatibilidade presumida.

## Fase 6 — Conteúdo de exercícios complementares

**Estado:** concluída em código (6.1–6.8); suíte não executada nesta sessão (ver `docs/phase6-validation.md`).

- Motor de exercícios, laboratórios Linux/rede/Web/forense, desafios progressivos, pontuação e progresso persistido.
- Vulnerabilidades operando em localhost por padrão.

**Critério:** exercícios executáveis localmente e com escopo controlado.

## Fase 7 — Experiência avançada

**Estado:** concluída em código; 7.1–7.8 (sessões, personalização, favoritos, scripts, arquivos, relatórios, integração, tutor offline) concluídos em código (ver `docs/sessions.md`, `customization.md`, `favorites.md`, `scripts.md`, `file-manager.md`, `reports.md`, `tool-integration.md`, `tutor.md`); suíte da fase transferida para a validação final.

- Múltiplas sessões, personalização, favoritos, scripts, gerenciamento de arquivos, relatórios e integração entre ferramentas.
- Assistente didático local, se viável e seguro.

## Fase 8 — Otimização

**Estado:** concluída em código (8.1–8.5; ver `docs/storage.md`, `docs/memory.md`, `docs/startup.md`, `docs/battery-processes.md`, `docs/recovery.md`, `docs/phase8-validation.md`); sem medição em aparelho. Subdivisão: 8.1 armazenamento, 8.2 memória/saída, 8.3 inicialização, 8.4 bateria/processos, 8.5 recuperação. Reduzir o tamanho do RootFS/downloads depende de nova build do RootFS (CI, rede) e fica registrado como pendência externa.

- Reduzir RootFS e downloads; otimizar memória, inicialização, bateria, processos e recuperação.

**Critério:** estabilidade em dispositivos compatíveis.

## Fase 9 — Auditoria e publicação

**Subdivisão:** 9.1 auditoria de segurança estática, 9.2 documentação de instalação e uso, 9.3 APK release e assinatura, 9.4 notas de versão. Testes unitários, integração, E2E, aparelho real, instalação limpa, atualização e recuperação pertencem à **validação final** (após 9.4) e dependem de `kotlinc`/rede/aparelho.

- Auditorias de segurança, testes unitários, integração, E2E, aparelho real, instalação limpa, atualização e recuperação.
- APK release, documentação de instalação e primeira versão estável.

## Regras transversais

Não implementar todas as fases de uma vez; não marcar testes sem execução real; não publicar binários gigantes no Git; manter commits pequenos; documentar limitações do Android sem root; preservar dados do usuário; e nunca executar avaliações contra terceiros sem autorização e escopo explícito.
