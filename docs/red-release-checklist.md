# RED-14 — Checklist de estabilização e entrega

**Estado:** implementação/documentação do roadmap RED/BLUE concluídas (100%); host checks aprovados no workflow `45405105200`; testes unitários Android/APK Debug em execução; validação browser/hardware e release publicável pendentes. Este checklist separa implementação de validação.

## Entregáveis documentais concluídos

- [x] Notas de release preliminares e limitações: [red-blue-release-notes.md](red-blue-release-notes.md).
- [x] Matriz inicial de compatibilidade: [red-blue-compatibility.md](red-blue-compatibility.md).
- [x] Checklist de estabilização e entrega.
- [x] Registro de revisão direcionada e comparação Git: [red-branch-stabilization.md](red-branch-stabilization.md).
- [x] Roadmap, changelog e retomada alinhados ao estado real, sem declarar validação.

## Snapshot Git registrado

- Branch: `recovery/red-portal-integration`.
- Commit observado antes desta atualização documental: `72f612deb08c425fdb1e1418bb9be7a98d1511ab`.
- Base `main`: `a4de5eb7ef3d72404ea1171af5eaa135a48bde0c`.
- Comparação: 520 commits à frente, 0 atrás; 48 arquivos alterados.
- A contagem é um retrato e precisa ser atualizada antes do PR se a branch avançar.

## Portões obrigatórios da etapa final

- [ ] Validar no B a instalação manual da CA pública, conferência do fingerprint, confiança no navegador e handshake TLS; confirmar prompts web reais.
- [ ] Executar RED-11: auditoria final e resolver achados críticos/altos.
- [ ] Executar RED-12: build, lint, testes unitários e integração host/Android.
- [ ] Corrigir falhas e repetir testes afetados.
- [ ] Executar RED-13: testes físicos A+B somente após aprovação do portão RED-12.
- [ ] Atualizar guias, changelog, notas de release e matriz de compatibilidade com resultados reais.
- [ ] Remover logs temporários e artefatos de diagnóstico; preservar evidências sanitizadas exigidas.
- [ ] Revisar integralmente o diff atual contra a base da branch e confirmar que não há alterações do usuário descartadas.
- [ ] Atualizar versionName/versionCode somente após aprovação dos portões.
- [ ] Gerar APK assinado usando segredos fornecidos por variáveis de ambiente, nunca armazenados no Git.
- [ ] Calcular SHA-256 do APK e registrar commit, versão, ambiente e resultados dos testes.
- [ ] Preparar PR de `recovery/red-portal-integration` para revisão; não fazer merge nem alterar `main` sem instrução explícita.

## Revisão já realizada, com escopo limitado

- [x] Confirmar branch de trabalho e base `main` sem alterar `main`.
- [x] Comparar referências Git no snapshot: branch adiantada, sem commits faltantes da base.
- [x] Revisar direcionadamente Manifest, configuração Android e arquivos RED/BLUE relevantes.
- [x] Buscar chamadas de logging de depuração nos arquivos Kotlin RED/BLUE revisados; nenhuma encontrada.
- [ ] Revisar integralmente o diff de todos os arquivos/commits antes do PR.
- [ ] Validar RED-07 no navegador B: CA confiável, SAN IP correto, ausência de alerta e permissões web reais quando suportadas.
- [ ] Validar RED-15 no A/B: hotspot do sistema permanece ativo, B não perde associação ao ativar upstream, heartbeat local continua atualizando, upstream de A é observado e navegação externa de B é confirmada diretamente no B.
- [ ] Executar os portões finais de validação e gerar artefatos.

## Estado atual do pacote

- APK Debug/Release: não gerado.
- Assinatura e SHA-256: não produzidos.
- Testes/build/lint/auditoria final/testes físicos: não executados nesta etapa.
- PR: não aberto.
- ZIP/checkpoints: não gerar; o trabalho permanece diretamente no GitHub conforme instrução do usuário.

**Critério de conclusão do RED-14:** checklist integralmente atendido após os portões anteriores. Até lá, considerar concluída somente a parcela documental/estabilização, não a release.
