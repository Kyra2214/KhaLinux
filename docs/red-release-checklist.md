# RED-14 — Checklist de estabilização e entrega

**Estado:** parcial. Documentação de release preparada; APK, assinatura, SHA-256, revisão final do diff e PR aguardam a fase de validação. Não publicar nem declarar release pronta antes dos portões finais.

## Entregáveis documentais

- [x] Notas preliminares de release: red-blue-release-notes.md.
- [x] Matriz de compatibilidade inicial: red-blue-compatibility.md.
- [x] Checklist de estabilização e entrega.
- [x] Histórico, changelog, roadmap e retomada atualizados.

## Portões obrigatórios após 100% da implementação

- [ ] Resolver dependências externas registradas, inclusive HTTPS confiável para RED-07, se fornecido.
- [ ] Executar RED-11: auditoria final e resolver achados críticos/altos.
- [ ] Executar RED-12: build, lint, testes unitários e integração host/Android.
- [ ] Executar RED-13: testes físicos A+B após aprovação do portão RED-12.
- [ ] Corrigir falhas e repetir testes afetados.
- [ ] Atualizar guias, changelog, notas de release e matriz de compatibilidade com resultados reais.
- [ ] Remover logs temporários e artefatos de diagnóstico; preservar evidências sanitizadas exigidas.
- [ ] Revisar diff completo contra a base da branch e confirmar que não há alterações do usuário descartadas.
- [ ] Atualizar versionName/versionCode somente após aprovação dos portões.
- [ ] Gerar APK assinado usando segredos fornecidos por variáveis de ambiente, nunca armazenados no Git.
- [ ] Calcular SHA-256 do APK e registrar commit, versão, ambiente e resultado dos testes.
- [ ] Preparar PR da branch recovery/red-portal-integration para revisão; não fazer merge nem alterar main sem instrução explícita.

## Estado atual do pacote

- Nenhum APK Debug/Release foi gerado nesta execução.
- Nenhum hash ou assinatura é declarado.
- Nenhum PR foi aberto nesta etapa.
- Não gerar ZIP/checkpoints; o trabalho permanece diretamente no GitHub conforme instrução do usuário.