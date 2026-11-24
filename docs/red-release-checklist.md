# RED-14 — Checklist de estabilização e entrega

## Integração na main — 2026-11-24

- PR [#19](https://github.com/Kyra2214/KhaLinux/pull/19) integrada por merge normal.
- Merge commit: `5bffe1467f71b5a1fb96a757bb4d584cfa132e44`.
- Branch de origem: `recovery/red-portal-integration`; comparação imediatamente antes do merge: 589 commits à frente, 0 atrás; 52 arquivos alterados.
- Home Security incluído: formulário e-mail/senha somente demonstrativo, senha descartada, e-mail mascarado no painel A; Facebook/Instagram apenas elementos visuais.
- CI principal para o novo HEAD e APK Release assinado ainda aguardam disparo/execução. O workflow depende dos secrets de assinatura configurados no repositório.
- Pendente validação física A/B de TLS, permissões reais do navegador, hotspot OEM e encaminhamento externo; não declarar esses testes concluídos.

**Estado:** roadmap-base RED/BLUE implementado (17/17); workflow `45408044336` aprovou host checks, auditoria estática, testes unitários Android, APK Debug e lint antes do ajuste complementar Home Security. O novo formulário aguarda CI. Validação browser/hardware e release assinada continuam pendentes.

## Entregáveis documentais concluídos

- [x] Notas de release preliminares e limitações: [red-blue-release-notes.md](red-blue-release-notes.md).
- [x] Matriz inicial de compatibilidade: [red-blue-compatibility.md](red-blue-compatibility.md).
- [x] Checklist de estabilização e entrega.
- [x] Registro de revisão direcionada e comparação Git: [red-branch-stabilization.md](red-branch-stabilization.md).
- [x] Roadmap, changelog e retomada alinhados ao estado real, sem declarar validação.

## Snapshot Git registrado

- Branch: `recovery/red-portal-integration`.
- HEAD antes da abertura do PR: `545e5d89352503aa8c1bb7afee84ad55bcc6a91f`.
- Base `main`: `a4de5eb7ef3d72404ea1171af5eaa135a48bde0c`.
- Comparação atual: 586 commits à frente, 0 atrás; 52 arquivos alterados. Base `main`: `a4de5eb7ef3d72404ea1171af5eaa135a48bde0c`.
- A contagem é um retrato e precisa ser atualizada antes do PR se a branch avançar.

## Portões obrigatórios da etapa final

- [ ] Validar no B a instalação manual da CA pública, conferência do fingerprint, confiança no navegador e handshake TLS; confirmar prompts web reais.
- [ ] Executar RED-11: auditoria final e resolver achados críticos/altos.
- [ ] Executar RED-12 novamente após Home Security: build, lint, testes unitários e integração host/Android. O workflow `45408044336` cobre apenas o estado anterior.
- [ ] Corrigir falhas e repetir testes afetados.
- [ ] Executar RED-13: testes físicos A+B somente após aprovação do portão RED-12.
- [ ] Atualizar guias, changelog, notas de release e matriz de compatibilidade com resultados reais.
- [ ] Remover logs temporários e artefatos de diagnóstico; preservar evidências sanitizadas exigidas.
- [ ] Revisar integralmente o diff atual contra a base da branch e confirmar que não há alterações do usuário descartadas.
- [ ] Atualizar versionName/versionCode somente após aprovação dos portões.
- [ ] Gerar APK assinado usando segredos fornecidos por variáveis de ambiente, nunca armazenados no Git.
- [ ] Calcular SHA-256 do APK e registrar commit, versão, ambiente e resultados dos testes.
- [ ] Abrir PR de `recovery/red-portal-integration` para `main`, aguardar CI e integrar após aprovação dos checks. O usuário autorizou explicitamente a integração nesta etapa.

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

- APK Debug: gerado no CI anterior à alteração Home Security; novo build pendente. APK Release assinado: ainda não gerado.
- Assinatura e SHA-256: não produzidos.
- CI anterior aprovado no workflow `45408044336`; CI do ajuste Home Security pendente. Testes físicos A/B continuam pendentes.
- PR: não aberto.
- ZIP/checkpoints: não gerar; o trabalho permanece diretamente no GitHub conforme instrução do usuário.

**Critério de conclusão do RED-14:** checklist integralmente atendido após os portões anteriores. Até lá, considerar concluída somente a parcela documental/estabilização, não a release.
