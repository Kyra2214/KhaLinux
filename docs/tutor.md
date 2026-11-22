# Tutor didático local (7.8)

**Estado: concluído em código; nada compilado/executado** (sem `kotlinc`/rede nesta sessão). Decisão de viabilidade: ADR-0006.

- **O que é:** tutor offline **baseado em regras**. Sem modelo de linguagem embarcado, sem rede, sem telemetria. Responde só com `distribution/tutor/knowledge.tsv` (32 tópicos: Linux, permissões, texto, rede, web, forense, limitações do PRoot) e com os descritores do toolkit (`tools.json`).
- **Busca:** normaliza texto (minúsculas, sem acentos), pontua palavras-chave (frase 3, palavra exata 2, mesmo radical 1) e exige pontuação mínima 2; palavras genéricas ("ajuda", "arquivo") não são palavras-chave. Sem correspondência, o tutor diz que não sabe e sugere tópicos — nunca inventa.
- **Ferramentas:** perguntar por `nmap`, `curl` etc. mostra resumo, comandos, aviso de escopo (se exige autorização), limitações sem root e se foi verificada em aparelho.
- **Exercícios:** "Perguntar ao tutor" em cada exercício envia só título e enunciado públicos. O tutor **recusa** pedidos de resposta/gabarito e indica tópicos relacionados e as dicas do exercício. A base não contém respostas de exercícios (regra do arquivo).
- **Escopo/segurança:** pedidos de invasão, DDoS ou uso sem autorização são recusados (lista conservadora de frases; não substitui o gate de escopo do toolkit, que continua sendo a barreira real).
- **Privacidade:** a conversa fica só em memória e é descartada ao fechar; nada é gravado nem enviado.
- **Código:** `runtime/.../tutor/TutorKnowledge.kt` (modelo, parser TSV), `TutorEngine.kt`; app: `tutor/TutorViewModel.kt`, `TutorScreen.kt`, botão "Tutor" e "Perguntar ao tutor"; asset via `assets.srcDir("../../distribution/tutor")`.
- **Limitações:** não entende perguntas fora do vocabulário da base, não mantém contexto entre perguntas e responde só em português. Um conceito explicado pode, naturalmente, ajudar a resolver o exercício — o que se evita é a resposta pronta.
- **Validação pendente:** `TutorEngineTest` (escrito, não executado) entra na suíte final da Fase 7.
