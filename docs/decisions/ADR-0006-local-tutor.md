# ADR-0006 — Assistente didático local

**Status:** aceita (7.8).

**Contexto:** o Roadmap pede um assistente didático local "se viável e seguro". O app não tem rede garantida, o aparelho é limitado e não há modelo de linguagem validado para ARM64 sem root nem para o tamanho de download aceitável (Fase 8).

**Decisão:** não embarcar modelo generativo. Implementar um tutor offline determinístico, baseado em regras e em conteúdo versionado e revisável (`distribution/tutor/knowledge.tsv` + descritores do toolkit).

**Consequências:**
- Seguro: respostas previsíveis, auditáveis, sem alucinação; não entrega respostas de exercícios; recusa ataque a terceiros.
- Limitado: cobre só o que está na base; diz "não encontrei" quando não sabe.
- Reversível: um modelo local poderia ser avaliado numa fase futura atrás da mesma interface (`TutorEngine.ask`), sem mudar a UI.
